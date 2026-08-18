package com.zakiev.spatialdashboard.ui.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.zakiev.spatialdashboard.model.MetricSeries
import com.zakiev.spatialdashboard.model.formatCompact
import com.zakiev.spatialdashboard.ui.theme.HealthGreen
import com.zakiev.spatialdashboard.ui.theme.HealthRed
import com.zakiev.spatialdashboard.ui.theme.HealthYellow
import kotlin.math.roundToInt

@Composable
fun LineChart(
    series: MetricSeries,
    color: Color,
    modifier: Modifier = Modifier,
    thresholds: List<Pair<Double, Color>> = emptyList(),
) {
    val gridColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    Canvas(modifier = modifier) {
        val points = series.points
        if (points.size < 2) return@Canvas
        val min = points.minOf { it.value }
        val max = points.maxOf { it.value }
        // pad the range so a flat line doesn't sit on the edge
        val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val lo = min - span * 0.1
        val hi = max + span * 0.1

        fun x(i: Int) = size.width * i / (points.size - 1)
        fun y(v: Double) = size.height * (1f - ((v - lo) / (hi - lo)).toFloat())

        for (frac in listOf(0.25f, 0.5f, 0.75f)) {
            val gy = size.height * frac
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        thresholds.forEach { (value, thresholdColor) ->
            if (value > lo && value < hi) {
                val ty = y(value)
                drawLine(
                    thresholdColor.copy(alpha = 0.7f),
                    Offset(0f, ty),
                    Offset(size.width, ty),
                    strokeWidth = 2f,
                    pathEffect = dash,
                )
            }
        }

        drawText(textMeasurer, formatCompact(max), topLeft = Offset(4f, 2f), style = labelStyle)
        val minLabel = textMeasurer.measure(formatCompact(min), labelStyle)
        drawText(minLabel, topLeft = Offset(4f, size.height - minLabel.size.height - 2f))

        val line = Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(x(i), y(p.value)) else lineTo(x(i), y(p.value))
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            fill,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)),
            ),
        )
        drawPath(line, color = color, style = Stroke(width = 4f))
    }
}

@Composable
fun BarChart(series: MetricSeries, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val points = series.points
        if (points.isEmpty()) return@Canvas
        val max = points.maxOf { it.value }.takeIf { it > 1e-9 } ?: 1.0
        val slot = size.width / points.size
        val barWidth = slot * 0.6f

        points.forEachIndexed { i, p ->
            val h = ((p.value / max).toFloat() * size.height).coerceAtLeast(2f)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * slot + (slot - barWidth) / 2f, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 3f),
            )
        }
    }
}

// Radial gauge for a 0..1 usage value, colored by the same thresholds as statuses
@Composable
fun UsageGauge(label: String, fraction: Double, modifier: Modifier = Modifier) {
    val value = fraction.coerceIn(0.0, 1.0).toFloat()
    val color = when {
        value < 0.75f -> HealthGreen
        value < 0.90f -> HealthYellow
        else -> HealthRed
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Round)
                val inset = stroke.width / 2f
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                // 270 degree dial, open at the bottom
                drawArc(trackColor, 135f, 270f, false, Offset(inset, inset), arcSize, style = stroke)
                drawArc(color, 135f, 270f * value, false, Offset(inset, inset), arcSize, style = stroke)
            }
            Text(
                "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
