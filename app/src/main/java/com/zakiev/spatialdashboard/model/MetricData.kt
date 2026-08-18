package com.zakiev.spatialdashboard.model

data class MetricPoint(val timestampSec: Long, val value: Double)

data class MetricSeries(val name: String, val points: List<MetricPoint>) {
    val latest: Double? get() = points.lastOrNull()?.value
}

// One fetch worth of raw metrics, same shape for live and mock sources
data class MetricsSample(
    val load1: MetricSeries,
    val netReceive: MetricSeries, // bytes per second
    val memAvailableBytes: Double,
    val memTotalBytes: Double,
    val fsAvailBytes: Double,
    val fsSizeBytes: Double,
)

enum class Health { OK, DEGRADED, ERROR }

// Chart time window; steps keep every range at ~60 points
enum class ChartRange(val label: String, val rangeSec: Long, val stepSec: Long) {
    M15("15m", 900, 15),
    H1("1h", 3_600, 60),
    H6("6h", 21_600, 360),
}

data class StatusItem(val title: String, val detail: String, val health: Health)

enum class SourceKind { LIVE, MOCK }

fun formatBytesPerSec(value: Double): String = when {
    value >= 1_000_000 -> "%.1f MB/s".format(value / 1_000_000)
    value >= 1_000 -> "%.0f kB/s".format(value / 1_000)
    else -> "%.0f B/s".format(value)
}

data class DashboardSnapshot(
    val load1: MetricSeries,
    val netReceive: MetricSeries,
    val memUsedFraction: Double,
    val fsUsedFraction: Double,
    val statuses: List<StatusItem>,
    val source: SourceKind,
    val updatedAtMs: Long,
)
