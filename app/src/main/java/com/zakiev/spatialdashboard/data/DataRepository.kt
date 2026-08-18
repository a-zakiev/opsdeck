package com.zakiev.spatialdashboard.data

import com.zakiev.spatialdashboard.model.ChartRange
import com.zakiev.spatialdashboard.model.DashboardSnapshot
import com.zakiev.spatialdashboard.model.Health
import com.zakiev.spatialdashboard.model.MetricSeries
import com.zakiev.spatialdashboard.model.MetricsSample
import com.zakiev.spatialdashboard.model.SourceKind
import com.zakiev.spatialdashboard.model.StatusItem
import com.zakiev.spatialdashboard.model.formatBytesPerSec
import kotlin.coroutines.cancellation.CancellationException

interface MetricsDataSource {
    suspend fun fetch(range: ChartRange): MetricsSample
    suspend fun fetchSeries(promql: String, range: ChartRange): MetricSeries
    suspend fun fetchMetricNames(): List<String> = emptyList()
}

class DataRepository(
    settings: SettingsRepository,
    private val remote: MetricsDataSource = PrometheusRemoteDataSource({ settings.serverConfig }),
    private val mock: MetricsDataSource = MockDataSource(),
) {

    suspend fun fetch(preferMock: Boolean, range: ChartRange): DashboardSnapshot {
        if (preferMock) return mock.fetch(range).toSnapshot(SourceKind.MOCK)
        return try {
            remote.fetch(range).toSnapshot(SourceKind.LIVE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mock.fetch(range).toSnapshot(SourceKind.MOCK)
        }
    }

    suspend fun fetchSeries(promql: String, preferMock: Boolean, range: ChartRange): MetricSeries {
        if (preferMock) return mock.fetchSeries(promql, range)
        return try {
            remote.fetchSeries(promql, range)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mock.fetchSeries(promql, range)
        }
    }

    suspend fun fetchMetricNames(): List<String> = try {
        remote.fetchMetricNames()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private fun MetricsSample.toSnapshot(source: SourceKind) = DashboardSnapshot(
        load1 = load1,
        netReceive = netReceive,
        memUsedFraction = 1.0 - memAvailableBytes / memTotalBytes,
        fsUsedFraction = 1.0 - fsAvailBytes / fsSizeBytes,
        statuses = buildStatuses(this),
        source = source,
        updatedAtMs = System.currentTimeMillis(),
    )
}

internal fun buildStatuses(sample: MetricsSample): List<StatusItem> {
    val load = sample.load1.latest ?: 0.0
    val memFraction = sample.memAvailableBytes / sample.memTotalBytes
    val fsFraction = sample.fsAvailBytes / sample.fsSizeBytes
    val netMbps = (sample.netReceive.latest ?: 0.0) / 1_000_000.0

    return listOf(
        StatusItem(
            title = "CPU load (1m)",
            detail = "load average %.2f".format(load),
            health = when {
                load < 2.0 -> Health.OK
                load < 4.0 -> Health.DEGRADED
                else -> Health.ERROR
            },
        ),
        StatusItem(
            title = "Memory",
            detail = "%.1f of %.1f GiB free".format(sample.memAvailableBytes.toGib(), sample.memTotalBytes.toGib()),
            health = when {
                memFraction > 0.25 -> Health.OK
                memFraction > 0.10 -> Health.DEGRADED
                else -> Health.ERROR
            },
        ),
        StatusItem(
            title = "Disk /",
            detail = "%.0f GiB free (%.0f%%)".format(sample.fsAvailBytes.toGib(), fsFraction * 100),
            health = when {
                fsFraction > 0.20 -> Health.OK
                fsFraction > 0.10 -> Health.DEGRADED
                else -> Health.ERROR
            },
        ),
        StatusItem(
            title = "Network in",
            detail = formatBytesPerSec(sample.netReceive.latest ?: 0.0),
            health = when {
                netMbps < 80 -> Health.OK
                netMbps < 200 -> Health.DEGRADED
                else -> Health.ERROR
            },
        ),
    )
}

private fun Double.toGib() = this / (1024.0 * 1024.0 * 1024.0)
