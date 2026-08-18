package com.zakiev.spatialdashboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Fixed dark scheme: dashboards read better on dark panels and the look
// stays consistent across emulator, headset and screenshots.
private val DashboardColorScheme = darkColorScheme(
    primary = Sky400,
    secondary = Teal300,
    background = Slate950,
    surface = Slate900,
    surfaceVariant = Slate800,
    onPrimary = Slate950,
    onBackground = Slate200,
    onSurface = Slate200,
    onSurfaceVariant = Slate400,
    outline = Slate700,
)

@Composable
fun SpatialDashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DashboardColorScheme, typography = Typography, content = content)
}
