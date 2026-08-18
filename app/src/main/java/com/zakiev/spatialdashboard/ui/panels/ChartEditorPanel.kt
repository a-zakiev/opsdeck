package com.zakiev.spatialdashboard.ui.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zakiev.spatialdashboard.model.ChartConfig
import com.zakiev.spatialdashboard.model.ChartType

// Typing PromQL in a headset is painful, so the editor leads with presets:
// one tap fills the title, query and chart type. Free-text stays available.
private data class ChartPreset(val title: String, val promql: String, val type: ChartType)

private val presets = listOf(
    ChartPreset("CPU busy %", """100 - avg(rate(node_cpu_seconds_total{mode="idle"}[1m])) * 100""", ChartType.LINE),
    ChartPreset("Load average (5m)", "node_load5", ChartType.LINE),
    ChartPreset("Memory used", "node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes", ChartType.LINE),
    ChartPreset("Network out", "sum(rate(node_network_transmit_bytes_total[1m]))", ChartType.BARS),
    ChartPreset("Disk read", "sum(rate(node_disk_read_bytes_total[1m]))", ChartType.BARS),
    ChartPreset("Disk write", "sum(rate(node_disk_written_bytes_total[1m]))", ChartType.BARS),
)

@Composable
fun ChartEditorPanel(
    config: ChartConfig,
    isNew: Boolean,
    error: String?,
    metricNames: List<String>,
    onSave: (ChartConfig) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember(config.id) { mutableStateOf(config.title) }
    var promql by remember(config.id) { mutableStateOf(config.promql) }
    var type by remember(config.id) { mutableStateOf(config.type) }
    var search by remember(config.id) { mutableStateOf("") }
    var confirmDelete by remember(config.id) { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isNew) "New chart" else "Edit chart",
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onClose) { Text("Close") }
            }

            Text("Presets", style = MaterialTheme.typography.titleMedium)
            presets.forEach { preset ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            title = preset.title
                            promql = preset.promql
                            type = preset.type
                        },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(preset.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            preset.promql,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (metricNames.isNotEmpty()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search metrics on the server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (search.length >= 2) {
                    val matches = metricNames.filter { it.contains(search, ignoreCase = true) }
                    matches.take(6).forEach { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    promql = name
                                    if (title.isBlank()) title = name
                                    search = ""
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    if (matches.isEmpty()) {
                        Text(
                            "no matches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = promql,
                onValueChange = { promql = it },
                label = { Text("PromQL query") },
                minLines = 2,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == ChartType.LINE,
                    onClick = { type = ChartType.LINE },
                    label = { Text("Line") },
                )
                FilterChip(
                    selected = type == ChartType.BARS,
                    onClick = { type = ChartType.BARS },
                    label = { Text("Bars") },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onSave(config.copy(title = title, promql = promql, type = type)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
                if (!isNew) {
                    OutlinedButton(
                        onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (confirmDelete) "Really delete?" else "Delete",
                            color = if (confirmDelete) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                    }
                }
            }
        }
    }
}
