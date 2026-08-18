package com.zakiev.spatialdashboard.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zakiev.spatialdashboard.model.SourceKind

@Composable
fun ControlPanel(
    source: SourceKind?,
    refreshing: Boolean,
    mockOnly: Boolean,
    onRefresh: () -> Unit,
    onMockOnlyChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onAddChart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Controls", style = MaterialTheme.typography.titleLarge)

            Button(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth()) {
                Text(if (refreshing) "Refreshing..." else "Refresh now")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Mock data only", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            mockOnly -> "generator forced on"
                            source == SourceKind.LIVE -> "live Prometheus data"
                            source == SourceKind.MOCK -> "live unreachable, using generator"
                            else -> "connecting..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = mockOnly, onCheckedChange = onMockOnlyChange)
            }

            OutlinedButton(onClick = onAddChart, modifier = Modifier.fillMaxWidth()) {
                Text("Add chart")
            }

            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Settings")
            }
        }
    }
}
