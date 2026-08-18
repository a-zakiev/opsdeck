package com.zakiev.spatialdashboard.ui.panels

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zakiev.spatialdashboard.model.Health
import com.zakiev.spatialdashboard.model.StatusItem
import com.zakiev.spatialdashboard.ui.theme.HealthGreen
import com.zakiev.spatialdashboard.ui.theme.HealthRed
import com.zakiev.spatialdashboard.ui.theme.HealthYellow

@Composable
fun StatusPanel(statuses: List<StatusItem>, modifier: Modifier = Modifier) {
    val alerting = statuses.any { it.health == Health.ERROR }
    val borderModifier = if (alerting) {
        val pulse by rememberInfiniteTransition(label = "alert")
            .animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "alertAlpha",
            )
        Modifier.border(3.dp, HealthRed.copy(alpha = pulse))
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .then(borderModifier),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Node status", style = MaterialTheme.typography.titleLarge)
            if (statuses.isEmpty()) {
                Text(
                    "waiting for data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            statuses.forEach { StatusCard(it) }
        }
    }
}

@Composable
private fun StatusCard(item: StatusItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(item.health.color(), CircleShape),
        )
        Column {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                item.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Health.color(): Color = when (this) {
    Health.OK -> HealthGreen
    Health.DEGRADED -> HealthYellow
    Health.ERROR -> HealthRed
}
