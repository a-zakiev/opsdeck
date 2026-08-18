package com.zakiev.spatialdashboard.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zakiev.spatialdashboard.model.ChartConfig
import com.zakiev.spatialdashboard.model.ChartType
import com.zakiev.spatialdashboard.model.MetricSeries
import com.zakiev.spatialdashboard.model.formatCompact
import com.zakiev.spatialdashboard.ui.theme.Sky400
import com.zakiev.spatialdashboard.ui.theme.Teal300

// One user-defined chart in its own panel
@Composable
fun ChartPanel(
    config: ChartConfig,
    series: MetricSeries?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (config.type == ChartType.LINE) Sky400 else Teal300

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        config.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        config.promql,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onEdit) { Text("Edit") }
            }

            Text(
                text = series?.latest?.let { formatCompact(it) } ?: "--",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (series != null && series.points.size > 1) {
                val chartModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                when (config.type) {
                    ChartType.LINE -> LineChart(series, accent, chartModifier)
                    ChartType.BARS -> BarChart(series, accent, chartModifier)
                }
            } else {
                Text(
                    "waiting for data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
