package com.zakiev.spatialdashboard

import com.zakiev.spatialdashboard.data.MockDataSource
import com.zakiev.spatialdashboard.model.ChartConfig
import com.zakiev.spatialdashboard.model.ChartRange
import com.zakiev.spatialdashboard.model.ChartType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartConfigTest {

    @Test
    fun `config list survives json round trip`() {
        val charts = listOf(
            ChartConfig("id1", "CPU busy", """100 - avg(rate(node_cpu_seconds_total{mode="idle"}[1m]))""", ChartType.LINE),
            ChartConfig("id2", "Net out", "sum(rate(node_network_transmit_bytes_total[1m]))", ChartType.BARS),
        )
        val restored = Json.decodeFromString<List<ChartConfig>>(Json.encodeToString(charts))
        assertEquals(charts, restored)
    }

    @Test
    fun `mock series for a custom query is deterministic and sane`() = runTest {
        val source = MockDataSource { 1_700_000_000L }
        val first = source.fetchSeries("node_load5", ChartRange.M15)
        val second = source.fetchSeries("node_load5", ChartRange.M15)
        val other = source.fetchSeries("sum(rate(node_disk_read_bytes_total[1m]))", ChartRange.M15)

        assertEquals(61, first.points.size)
        assertEquals(first, second)
        assertTrue(first.points.all { it.value >= 0.0 })
        assertTrue(first.points != other.points)
    }
}
