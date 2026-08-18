package com.zakiev.spatialdashboard

import com.zakiev.spatialdashboard.data.MockDataSource
import com.zakiev.spatialdashboard.model.ChartRange
import com.zakiev.spatialdashboard.data.buildStatuses
import com.zakiev.spatialdashboard.model.Health
import com.zakiev.spatialdashboard.model.MetricPoint
import com.zakiev.spatialdashboard.model.MetricSeries
import com.zakiev.spatialdashboard.model.MetricsSample
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockDataSourceTest {

    private val fixedClock = { 1_700_000_000L }

    @Test
    fun `generates a full chart series with sane values`() = runTest {
        val sample = MockDataSource(fixedClock).fetch(ChartRange.M15)

        assertEquals(61, sample.load1.points.size)
        assertEquals(61, sample.netReceive.points.size)
        assertTrue(sample.load1.points.zipWithNext().all { (a, b) -> a.timestampSec < b.timestampSec })
        assertTrue(sample.load1.points.all { it.value > 0.0 })
        assertTrue(sample.netReceive.points.all { it.value > 0.0 })
        assertTrue(sample.memAvailableBytes in 0.0..sample.memTotalBytes)
        assertTrue(sample.fsAvailBytes in 0.0..sample.fsSizeBytes)
    }

    @Test
    fun `same clock gives same series so the chart stays continuous`() = runTest {
        val first = MockDataSource(fixedClock).fetch(ChartRange.M15)
        val second = MockDataSource(fixedClock).fetch(ChartRange.M15)
        assertEquals(first.load1, second.load1)
    }
}

class StatusThresholdsTest {

    private fun sample(load: Double = 1.0, memFree: Double = 0.5, diskFree: Double = 0.5, netMbps: Double = 5.0) =
        MetricsSample(
            load1 = MetricSeries("node_load1", listOf(MetricPoint(0, load))),
            netReceive = MetricSeries("net_receive", listOf(MetricPoint(0, netMbps * 1e6))),
            memAvailableBytes = memFree * 16e9,
            memTotalBytes = 16e9,
            fsAvailBytes = diskFree * 500e9,
            fsSizeBytes = 500e9,
        )

    private fun healthOf(sample: MetricsSample, title: String): Health =
        buildStatuses(sample).first { it.title.startsWith(title) }.health

    @Test
    fun `load thresholds`() {
        assertEquals(Health.OK, healthOf(sample(load = 1.2), "CPU"))
        assertEquals(Health.DEGRADED, healthOf(sample(load = 2.5), "CPU"))
        assertEquals(Health.ERROR, healthOf(sample(load = 6.0), "CPU"))
    }

    @Test
    fun `memory thresholds`() {
        assertEquals(Health.OK, healthOf(sample(memFree = 0.5), "Memory"))
        assertEquals(Health.DEGRADED, healthOf(sample(memFree = 0.15), "Memory"))
        assertEquals(Health.ERROR, healthOf(sample(memFree = 0.05), "Memory"))
    }

    @Test
    fun `disk thresholds`() {
        assertEquals(Health.OK, healthOf(sample(diskFree = 0.3), "Disk"))
        assertEquals(Health.DEGRADED, healthOf(sample(diskFree = 0.15), "Disk"))
        assertEquals(Health.ERROR, healthOf(sample(diskFree = 0.05), "Disk"))
    }
}
