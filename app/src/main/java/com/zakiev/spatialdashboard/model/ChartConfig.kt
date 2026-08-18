package com.zakiev.spatialdashboard.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChartType { LINE, BARS }

// A user-defined chart: one PromQL range query rendered as a line or bars
@Serializable
data class ChartConfig(
    val id: String,
    val title: String,
    val promql: String,
    val type: ChartType,
)

fun formatCompact(value: Double): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 1e9 -> "%.1fG".format(value / 1e9)
        abs >= 1e6 -> "%.1fM".format(value / 1e6)
        abs >= 1e3 -> "%.1fk".format(value / 1e3)
        abs >= 100 -> "%.0f".format(value)
        else -> "%.2f".format(value)
    }
}
