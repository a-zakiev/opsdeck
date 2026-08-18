package com.zakiev.spatialdashboard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zakiev.spatialdashboard.data.ChartsRepository
import com.zakiev.spatialdashboard.data.DataRepository
import com.zakiev.spatialdashboard.data.LayoutRepository
import com.zakiev.spatialdashboard.data.PanelPlacement
import com.zakiev.spatialdashboard.data.SettingsRepository
import com.zakiev.spatialdashboard.model.ChartConfig
import com.zakiev.spatialdashboard.model.ChartRange
import com.zakiev.spatialdashboard.model.ChartType
import com.zakiev.spatialdashboard.model.DashboardSnapshot
import com.zakiev.spatialdashboard.model.Health
import com.zakiev.spatialdashboard.model.MetricSeries
import com.zakiev.spatialdashboard.model.StatusItem
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val chartsRepo = ChartsRepository(app)
    private val layoutRepo = LayoutRepository(app)
    private val repository = DataRepository(settings)

    data class CustomChart(val config: ChartConfig, val series: MetricSeries? = null)

    data class UiState(
        val snapshot: DashboardSnapshot? = null,
        val refreshing: Boolean = false,
        val mockOnly: Boolean = false,
        val serverUrl: String = "",
        val authToken: String = "",
        val serverError: String? = null,
        val settingsOpen: Boolean = false,
        val customCharts: List<CustomChart> = emptyList(),
        val editing: ChartConfig? = null,
        val editorError: String? = null,
        val placements: Map<String, PanelPlacement> = emptyMap(),
        val range: ChartRange = ChartRange.M15,
        val metricNames: List<String> = emptyList(),
        val alert: StatusItem? = null,
        val dismissedAlert: String? = null,
    )

    private val _state = MutableStateFlow(
        UiState(
            serverUrl = settings.serverUrl,
            authToken = settings.authToken,
            customCharts = chartsRepo.load().map { CustomChart(it) },
            placements = layoutRepo.load(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refreshNow()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    fun setMockOnly(value: Boolean) {
        _state.update { it.copy(mockOnly = value) }
        refresh()
    }

    fun setSettingsOpen(open: Boolean) {
        _state.update { it.copy(settingsOpen = open) }
    }

    fun setRange(range: ChartRange) {
        _state.update { it.copy(range = range) }
        refresh()
    }

    fun dismissAlert() {
        _state.update { it.copy(dismissedAlert = it.alert?.title, alert = null) }
    }

    fun applyServerSettings(url: String, token: String) {
        val normalized = url.trim().trimEnd('/')
        if (normalized.toHttpUrlOrNull()?.scheme != "https") {
            _state.update { it.copy(serverError = "enter a valid https:// URL") }
            return
        }
        settings.serverUrl = normalized
        settings.authToken = token.trim()
        _state.update {
            it.copy(serverUrl = normalized, authToken = token.trim(), serverError = null)
        }
        refresh()
    }

    fun addChart() {
        _state.update {
            it.copy(
                editing = ChartConfig(
                    id = UUID.randomUUID().toString(),
                    title = "",
                    promql = "",
                    type = ChartType.LINE,
                ),
                editorError = null,
            )
        }
        loadMetricNames()
    }

    fun editChart(config: ChartConfig) {
        _state.update { it.copy(editing = config, editorError = null) }
        loadMetricNames()
    }

    private fun loadMetricNames() {
        if (_state.value.metricNames.isNotEmpty()) return
        viewModelScope.launch {
            val names = repository.fetchMetricNames()
            if (names.isNotEmpty()) _state.update { it.copy(metricNames = names) }
        }
    }

    fun closeEditor() {
        _state.update { it.copy(editing = null, editorError = null) }
    }

    fun saveChart(config: ChartConfig) {
        if (config.promql.isBlank()) {
            _state.update { it.copy(editorError = "query is empty") }
            return
        }
        val saved = config.copy(
            title = config.title.ifBlank { config.promql.take(30) },
            promql = config.promql.trim(),
        )
        val charts = _state.value.customCharts
            .filter { it.config.id != saved.id }
            .plus(CustomChart(saved))
        chartsRepo.save(charts.map { it.config })
        _state.update { it.copy(customCharts = charts, editing = null, editorError = null) }
        refresh()
    }

    fun savePlacement(id: String, placement: PanelPlacement) {
        val placements = _state.value.placements + (id to placement)
        layoutRepo.save(placements)
        _state.update { it.copy(placements = placements) }
    }

    fun resetLayout() {
        layoutRepo.save(emptyMap())
        _state.update { it.copy(placements = emptyMap()) }
    }

    fun deleteChart(id: String) {
        val charts = _state.value.customCharts.filter { it.config.id != id }
        chartsRepo.save(charts.map { it.config })
        val placements = _state.value.placements - id
        layoutRepo.save(placements)
        _state.update {
            it.copy(customCharts = charts, editing = null, editorError = null, placements = placements)
        }
    }

    private suspend fun refreshNow() {
        _state.update { it.copy(refreshing = true) }
        val preferMock = _state.value.mockOnly
        val range = _state.value.range
        val snapshot = repository.fetch(preferMock = preferMock, range = range)
        val seriesById = _state.value.customCharts.associate { chart ->
            chart.config.id to repository.fetchSeries(chart.config.promql, preferMock, range)
        }
        val error = snapshot.statuses.firstOrNull { it.health == Health.ERROR }
        // merge by id: the chart list may have changed while we were fetching
        _state.update { st ->
            st.copy(
                snapshot = snapshot,
                customCharts = st.customCharts.map { c ->
                    c.copy(series = seriesById[c.config.id] ?: c.series)
                },
                refreshing = false,
                alert = error?.takeIf { it.title != st.dismissedAlert },
                dismissedAlert = if (error == null) null else st.dismissedAlert,
            )
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 15_000L
    }
}
