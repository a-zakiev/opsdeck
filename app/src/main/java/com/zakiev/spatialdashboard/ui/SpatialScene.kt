package com.zakiev.spatialdashboard.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterAnchorPoint
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialColumn
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.layout.MovePolicy
import androidx.xr.compose.subspace.layout.ResizePolicy
import androidx.xr.compose.subspace.layout.SpatialMoveEventType
import androidx.xr.compose.subspace.layout.SpatialResizeEventType
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.rotate
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.unit.DpVolumeOffset
import androidx.xr.compose.unit.DpVolumeSize
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.scene
import com.zakiev.spatialdashboard.data.PanelPlacement
import com.zakiev.spatialdashboard.model.MetricSeries
import com.zakiev.spatialdashboard.ui.panels.ChartEditorPanel
import com.zakiev.spatialdashboard.ui.panels.ChartPanel
import com.zakiev.spatialdashboard.ui.panels.ControlPanel
import com.zakiev.spatialdashboard.ui.panels.MetricsPanel
import com.zakiev.spatialdashboard.ui.panels.SettingsPanel
import com.zakiev.spatialdashboard.ui.panels.StatusPanel
import com.zakiev.spatialdashboard.util.BarsGlb
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@Composable
fun DashboardApp(viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
        SpatialScene(state, viewModel)
    } else {
        FlatScene(state, viewModel)
    }
}

@Composable
private fun SpatialScene(state: DashboardViewModel.UiState, vm: DashboardViewModel) {
    val snapshot = state.snapshot

    Subspace {
        SpatialColumn {
            // user charts live in their own row above the main dashboard, so
            // the main row (and the panels spawned next to it) stays in view
            if (state.customCharts.isNotEmpty() || snapshot != null) {
                SpatialRow {
                    Bars3DChart(series = snapshot?.netReceive)
                    state.customCharts.forEach { chart ->
                        key(chart.config.id) {
                            PersistentPanel(
                                id = chart.config.id,
                                defaultWidth = 560.dp,
                                defaultHeight = 420.dp,
                                state = state,
                                vm = vm,
                            ) {
                                ChartSlot(chart, state, vm)
                            }
                        }
                    }
                }
            }

            SpatialRow {
                PersistentPanel(
                    id = "metrics",
                    defaultWidth = 700.dp,
                    defaultHeight = 720.dp,
                    state = state,
                    vm = vm,
                ) {
                    MetricsPanel(
                        snapshot = snapshot,
                        range = state.range,
                        onRangeChange = vm::setRange,
                        alert = state.alert,
                        onDismissAlert = vm::dismissAlert,
                    )
                    Orbiter(anchorPoint = OrbiterAnchorPoint.Bottom, offset = DpVolumeOffset(y = 32.dp)) {
                        Button(onClick = vm::refresh, enabled = !state.refreshing) {
                            Text(if (state.refreshing) "Refreshing..." else "Refresh")
                        }
                    }
                }

                PersistentPanel(
                    id = "status",
                    defaultWidth = 440.dp,
                    defaultHeight = 520.dp,
                    state = state,
                    vm = vm,
                ) {
                    StatusPanel(statuses = snapshot?.statuses.orEmpty())
                }

                // one always-present utility panel: swapping its content is
                // reliable, dynamically inserted panels can end up out of view
                PersistentPanel(
                    id = "utility",
                    defaultWidth = 480.dp,
                    defaultHeight = 680.dp,
                    state = state,
                    vm = vm,
                ) {
                    UtilityPanel(state, vm)
                }
            }
        }
    }
}

// Real 3D bars built from SceneCore entities. The scene graph is created once
// (a caption panel as the movable root, a base plate and one cube entity per
// bar); data refreshes only adjust entity scales. Entities are never recreated
// mid-session: disposing one while the user is dragging it crashes the runtime.
@Composable
private fun Bars3DChart(series: MetricSeries?) {
    if (series == null || series.points.size < 2) return
    val session = LocalSession.current ?: return
    val context = LocalContext.current

    // quantized to a few height levels so scales only change when visible
    val buckets = remember(series) {
        val values = series.points.map { it.value }
        val bucketSize = (values.size + BARS_3D - 1) / BARS_3D
        val raw = values.chunked(bucketSize).map { it.average() }
        val max = raw.maxOrNull()?.takeIf { it > 1e-9 } ?: 1.0
        raw.map { (it / max * HEIGHT_LEVELS).roundToInt().toDouble() / HEIGHT_LEVELS }
    }
    val holder = remember { Bars3DHolder() }

    LaunchedEffect(buckets) {
        try {
            if (holder.root == null) {
                val barModel = GltfModel.create(session, BarsGlb.unitCube(0.37f, 0.92f, 0.83f, 0.45f), "bar-cube.glb")
                val plateModel = GltfModel.create(session, BarsGlb.unitCube(0.12f, 0.17f, 0.28f, 0.15f), "plate-cube.glb")
                holder.models = listOf(barModel, plateModel)

                val caption = TextView(context).apply {
                    text = "network in, last 15m"
                    setTextColor(0xFFE2E8F0.toInt())
                    setBackgroundColor(0xE60F172A.toInt())
                    textSize = 16f
                    gravity = Gravity.CENTER
                }
                val root = PanelEntity.create(
                    session,
                    caption,
                    FloatSize2d(0.6f, 0.06f),
                    "bars3d",
                    Pose(Vector3(-0.4f, 0.35f, -1.2f), Quaternion.Identity),
                    session.scene.activitySpace,
                )
                holder.root = root
                root.addComponent(MovableComponent.createSystemMovable(session))

                val plate = GltfModelEntity.create(session, plateModel, Pose(Vector3(0f, 0.05f, 0f)), root)
                plate.setScale(Vector3(BARS_TOTAL_W + 0.06f, 0.012f, 0.16f))
                holder.plate = plate

                holder.bars = (0 until BARS_3D).map { i ->
                    val x = -BARS_TOTAL_W / 2 + BAR_STEP / 2 + i * BAR_STEP
                    GltfModelEntity.create(session, barModel, Pose(Vector3(x, 0.065f, 0f)), root)
                }
            }
            holder.bars.forEachIndexed { i, bar ->
                val h = (buckets.getOrNull(i) ?: 0.0).toFloat().coerceAtLeast(0.05f) * BARS_MAX_H
                bar.setScale(Vector3(BAR_STEP * 0.7f, h, 0.05f))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("OpsDeck", "3d bars failed", e)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            holder.bars.forEach { it.dispose() }
            holder.plate?.dispose()
            holder.root?.dispose()
            holder.models.forEach { it.close() }
            holder.bars = emptyList()
            holder.plate = null
            holder.root = null
            holder.models = emptyList()
        }
    }
}

private class Bars3DHolder {
    var root: PanelEntity? = null
    var plate: GltfModelEntity? = null
    var bars: List<GltfModelEntity> = emptyList()
    var models: List<GltfModel> = emptyList()
}

private const val BARS_3D = 14
private const val HEIGHT_LEVELS = 24
private const val BARS_TOTAL_W = 0.9f
private const val BAR_STEP = BARS_TOTAL_W / BARS_3D
private const val BARS_MAX_H = 0.3f

// A SpatialPanel that owns its pose and size (custom move/resize policies)
// and restores them from saved placements between launches
@Composable
private fun PersistentPanel(
    id: String,
    defaultWidth: Dp,
    defaultHeight: Dp,
    state: DashboardViewModel.UiState,
    vm: DashboardViewModel,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val saved = state.placements[id]
    // key on presence, not value: re-init only on "Reset panel layout",
    // not after every persisted gesture
    val hasSaved = saved != null

    var offset by remember(hasSaved) {
        mutableStateOf(Vector3(saved?.offsetX ?: 0f, saved?.offsetY ?: 0f, saved?.offsetZ ?: 0f))
    }
    var rotation by remember(hasSaved) {
        mutableStateOf(Quaternion(saved?.rotX ?: 0f, saved?.rotY ?: 0f, saved?.rotZ ?: 0f, saved?.rotW ?: 1f))
    }
    // layout size is fixed for the session; live resizing is handled visually
    // by the system so sibling panels don't get pushed around mid-gesture
    val layoutSize = remember(hasSaved) {
        with(density) {
            IntSize(
                saved?.widthPx?.takeIf { it > 0 } ?: defaultWidth.roundToPx(),
                saved?.heightPx?.takeIf { it > 0 } ?: defaultHeight.roundToPx(),
            )
        }
    }
    val gesture = remember { MoveGesture() }

    fun persist() = vm.savePlacement(
        id,
        PanelPlacement(
            offsetX = offset.x, offsetY = offset.y, offsetZ = offset.z,
            rotX = rotation.x, rotY = rotation.y, rotZ = rotation.z, rotW = rotation.w,
            widthPx = (gesture.resizedPx ?: layoutSize).width,
            heightPx = (gesture.resizedPx ?: layoutSize).height,
        ),
    )

    SpatialPanel(
        SubspaceModifier
            .width(with(density) { layoutSize.width.toDp() })
            .height(with(density) { layoutSize.height.toDp() })
            .offset(
                x = with(density) { offset.x.toDp() },
                y = with(density) { offset.y.toDp() },
                z = with(density) { offset.z.toDp() },
            )
            .rotate(rotation)
            .movable(
                movePolicy = MovePolicy.custom { event ->
                    if (event.type == SpatialMoveEventType.Start) {
                        gesture.baseOffset = offset
                        gesture.startPose = event.pose
                    } else {
                        val start = gesture.startPose ?: event.pose
                        offset = Vector3(
                            gesture.baseOffset.x + (event.pose.translation.x - start.translation.x),
                            gesture.baseOffset.y + (event.pose.translation.y - start.translation.y),
                            gesture.baseOffset.z + (event.pose.translation.z - start.translation.z),
                        )
                        rotation = event.pose.rotation
                        if (event.type == SpatialMoveEventType.End) persist()
                    }
                },
            )
            .resizable(
                minimumSize = DpVolumeSize(240.dp, 200.dp, 0.dp),
                resizePolicy = ResizePolicy.system { event ->
                    gesture.resizedPx = IntSize(event.size.width, event.size.height)
                    if (event.type == SpatialResizeEventType.End) persist()
                },
            ),
    ) {
        content()
    }
}

private class MoveGesture {
    var baseOffset: Vector3 = Vector3(0f, 0f, 0f)
    var startPose: Pose? = null
    var resizedPx: IntSize? = null
}

// An existing chart edits in place: its own panel swaps to the editor
@Composable
private fun ChartSlot(
    chart: DashboardViewModel.CustomChart,
    state: DashboardViewModel.UiState,
    vm: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val editing = state.editing
    if (editing != null && editing.id == chart.config.id) {
        ChartEditorPanel(
            config = editing,
            isNew = false,
            error = state.editorError,
            metricNames = state.metricNames,
            onSave = vm::saveChart,
            onDelete = { vm.deleteChart(editing.id) },
            onClose = vm::closeEditor,
            modifier = modifier,
        )
    } else {
        ChartPanel(
            config = chart.config,
            series = chart.series,
            onEdit = { vm.editChart(chart.config) },
            modifier = modifier,
        )
    }
}

// Controls, settings and the new-chart editor share one panel slot
@Composable
private fun UtilityPanel(
    state: DashboardViewModel.UiState,
    vm: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val editing = state.editing
    val editingNew = editing != null && state.customCharts.none { it.config.id == editing.id }
    when {
        editingNew -> ChartEditorPanel(
            config = editing!!,
            isNew = true,
            error = state.editorError,
            metricNames = state.metricNames,
            onSave = vm::saveChart,
            onDelete = {},
            onClose = vm::closeEditor,
            modifier = modifier,
        )
        state.settingsOpen -> SettingsPanel(
            serverUrl = state.serverUrl,
            authToken = state.authToken,
            serverError = state.serverError,
            onApply = vm::applyServerSettings,
            onClose = { vm.setSettingsOpen(false) },
            onResetLayout = vm::resetLayout,
            modifier = modifier,
        )
        else -> ControlPanel(
            source = state.snapshot?.source,
            refreshing = state.refreshing,
            mockOnly = state.mockOnly,
            onRefresh = vm::refresh,
            onMockOnlyChange = vm::setMockOnly,
            onOpenSettings = { vm.setSettingsOpen(true) },
            onAddChart = vm::addChart,
            modifier = modifier,
        )
    }
}

// Regular 2D layout for Home Space and non-XR devices
@Composable
private fun FlatScene(state: DashboardViewModel.UiState, vm: DashboardViewModel) {
    val snapshot = state.snapshot

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val panelModifier = Modifier.fillMaxHeight()

            MetricsPanel(
                snapshot = snapshot,
                range = state.range,
                onRangeChange = vm::setRange,
                alert = state.alert,
                onDismissAlert = vm::dismissAlert,
                modifier = panelModifier.width(480.dp),
            )
            StatusPanel(statuses = snapshot?.statuses.orEmpty(), modifier = panelModifier.width(320.dp))
            UtilityPanel(state, vm, modifier = panelModifier.width(360.dp))
            state.customCharts.forEach { chart ->
                key(chart.config.id) {
                    ChartSlot(chart, state, vm, modifier = panelModifier.width(420.dp))
                }
            }
        }
    }
}
