package com.zakiev.spatialdashboard.data

import com.zakiev.spatialdashboard.model.ChartRange
import com.zakiev.spatialdashboard.model.MetricPoint
import com.zakiev.spatialdashboard.model.MetricSeries
import com.zakiev.spatialdashboard.model.MetricsSample
import kotlin.math.max
import kotlin.math.sin

// Generates plausible node_exporter style metrics so the UI looks the same
// with or without network access. Values are derived from the timestamp,
// which keeps the chart continuous between refreshes.
class MockDataSource(
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : MetricsDataSource {

    override suspend fun fetch(range: ChartRange): MetricsSample {
        val now = clock()
        val timestamps = (0..60).map { i -> now - (60 - i) * range.stepSec }

        val memTotal = 16.0 * GIB
        val memAvailable = memTotal * (0.35 + 0.12 * sin(now / 310.0) + noise(now) * 0.04)
        val fsSize = 480.0 * GIB
        val fsAvail = fsSize * (0.27 + 0.02 * sin(now / 2400.0))

        return MetricsSample(
            load1 = MetricSeries("node_load1", timestamps.map { MetricPoint(it, load1At(it)) }),
            netReceive = MetricSeries("net_receive", timestamps.map { MetricPoint(it, netReceiveAt(it)) }),
            memAvailableBytes = memAvailable,
            memTotalBytes = memTotal,
            fsAvailBytes = fsAvail,
            fsSizeBytes = fsSize,
        )
    }

    // For user-defined charts the shape is derived from the query text, so a
    // given query always gets the same believable-looking curve
    override suspend fun fetchSeries(promql: String, range: ChartRange): MetricSeries {
        val now = clock()
        val h = promql.hashCode().toLong()
        val base = 20.0 + (h and 0xFF)
        val amp = 5.0 + (h shr 8 and 0x3F)
        val period = 90.0 + (h shr 14 and 0xFF)
        val points = (0..60).map { i ->
            val t = now - (60 - i) * range.stepSec
            MetricPoint(t, max(0.0, base + amp * sin(t / period) + noise(t + h) * amp * 0.4))
        }
        return MetricSeries(promql, points)
    }

    // Slow sine keeps load drifting through the 2.0 "degraded" threshold now
    // and then, plus rare tall spikes above 4.0 so alerting has something to show
    private fun load1At(t: Long): Double {
        val base = 1.3 + 0.9 * sin(t / 220.0)
        val ripple = 0.25 * sin(t / 37.0)
        val spike = if (noise(t / 75) > 0.42) 3.4 else 0.0
        return max(0.05, base + ripple + spike + noise(t) * 0.3)
    }

    // Bursty traffic shape in bytes/sec: a slow baseline plus occasional spikes
    private fun netReceiveAt(t: Long): Double {
        val base = 4.0 + 3.0 * sin(t / 130.0)
        val burst = if (noise(t / 3) > 0.35 ) 6.0 else 0.0
        return max(0.2, base + burst + noise(t + 7) * 1.5) * 1_000_000.0
    }

    // Deterministic pseudo-noise in [-0.5, 0.5]
    private fun noise(t: Long): Double {
        val h = (t * 2654435761L) ushr 16 and 0xFFFF
        return h.toDouble() / 0xFFFF - 0.5
    }

    private companion object {
        const val STEP_SEC = 15L
        const val GIB = 1024.0 * 1024.0 * 1024.0
    }
}
