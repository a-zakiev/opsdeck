package com.zakiev.spatialdashboard.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.xr.compose.spatial.SpatialDialog
import com.zakiev.spatialdashboard.model.ChartRange
import com.zakiev.spatialdashboard.model.DashboardSnapshot
import com.zakiev.spatialdashboard.model.SourceKind
import com.zakiev.spatialdashboard.model.StatusItem
import com.zakiev.spatialdashboard.model.formatBytesPerSec
import com.zakiev.spatialdashboard.ui.theme.HealthRed
import com.zakiev.spatialdashboard.ui.theme.HealthGreen
import com.zakiev.spatialdashboard.ui.theme.HealthYellow
import com.zakiev.spatialdashboard.ui.theme.Sky400
import com.zakiev.spatialdashboard.ui.theme.Slate400
import com.zakiev.spatialdashboard.ui.theme.Teal300
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MetricsPanel(
    snapshot: DashboardSnapshot?,
    range: ChartRange,
    onRangeChange: (ChartRange) -> Unit,
    alert: StatusItem?,
    onDismissAlert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (alert != null) {
        AlertDialogContent(alert, onDismissAlert)
    }

    // Surface (not a plain background) so text picks up onSurface as content color
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Node metrics", style = MaterialTheme.typography.titleLarge)
                Column(horizontalAlignment = Alignment.End) {
                    SourceBadge(snapshot?.source)
                    if (snapshot != null) {
                        Text(
                            "updated ${timeFormat.format(Date(snapshot.updatedAtMs))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { onRangeChange(r) },
                        label = { Text(r.label) },
                    )
                }
            }

            if (snapshot == null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "waiting for data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            ChartHeader(
                title = "node_load1",
                value = snapshot.load1.latest?.let { "%.2f".format(it) } ?: "--",
                valueColor = Sky400,
            )
            LineChart(
                series = snapshot.load1,
                color = Sky400,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                thresholds = listOf(2.0 to HealthYellow, 4.0 to HealthRed),
            )

            Spacer(Modifier.height(20.dp))
            ChartHeader(
                title = "network in",
                value = snapshot.netReceive.latest?.let { formatBytesPerSec(it) } ?: "--",
                valueColor = Teal300,
            )
            BarChart(
                series = snapshot.netReceive,
                color = Teal300,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f),
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                UsageGauge("Memory used", snapshot.memUsedFraction, Modifier.weight(1f))
                UsageGauge("Disk used", snapshot.fsUsedFraction, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChartHeader(title: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

// Pops out in front of the panel when a status goes red; falls back to a
// regular dialog outside of spatial mode
@Composable
private fun AlertDialogContent(alert: StatusItem, onDismiss: () -> Unit) {
    SpatialDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Alert", style = MaterialTheme.typography.titleLarge, color = HealthRed, fontWeight = FontWeight.Bold)
                Text("${alert.title}: ${alert.detail}", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
fun SourceBadge(source: SourceKind?) {
    val (label, color) = when (source) {
        SourceKind.LIVE -> "LIVE" to HealthGreen
        SourceKind.MOCK -> "MOCK" to HealthYellow
        null -> "..." to Slate400
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
