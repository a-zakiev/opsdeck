package com.zakiev.spatialdashboard.data

import com.zakiev.spatialdashboard.model.ChartRange
import com.zakiev.spatialdashboard.model.MetricPoint
import com.zakiev.spatialdashboard.model.MetricSeries
import com.zakiev.spatialdashboard.model.MetricsSample
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

// Talks to a Prometheus server over its standard HTTP API. The server address
// and optional bearer token come from settings on every fetch, so changes
// apply without recreating anything. Timeouts are short and any failure just
// bubbles up to the repository, which falls back to mock data.
class PrometheusRemoteDataSource(
    private val config: () -> ServerConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build(),
) : MetricsDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(range: ChartRange): MetricsSample = withContext(Dispatchers.IO) {
        val cfg = config().let { it.copy(baseUrl = it.baseUrl.trimEnd('/')) }
        val now = System.currentTimeMillis() / 1000
        MetricsSample(
            load1 = queryRange(cfg, "node_load1", range, end = now),
            netReceive = queryRange(cfg, "sum(rate(node_network_receive_bytes_total[1m]))", range, end = now),
            memAvailableBytes = queryScalar(cfg, "node_memory_MemAvailable_bytes"),
            memTotalBytes = queryScalar(cfg, "node_memory_MemTotal_bytes"),
            fsAvailBytes = queryScalar(cfg, """node_filesystem_avail_bytes{mountpoint="/"}"""),
            fsSizeBytes = queryScalar(cfg, """node_filesystem_size_bytes{mountpoint="/"}"""),
        )
    }

    override suspend fun fetchSeries(promql: String, range: ChartRange): MetricSeries =
        withContext(Dispatchers.IO) {
            val cfg = config().let { it.copy(baseUrl = it.baseUrl.trimEnd('/')) }
            queryRange(cfg, promql, range, end = System.currentTimeMillis() / 1000)
        }

    override suspend fun fetchMetricNames(): List<String> = withContext(Dispatchers.IO) {
        val cfg = config().let { it.copy(baseUrl = it.baseUrl.trimEnd('/')) }
        val url = cfg.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/label/__name__/values")
            .build()
        get(url.toString(), cfg.authToken)["data"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?: emptyList()
    }

    private fun queryScalar(cfg: ServerConfig, promql: String): Double {
        val url = cfg.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/query")
            .addQueryParameter("query", promql)
            .build()
        val value = firstResult(get(url.toString(), cfg.authToken))["value"]?.jsonArray
            ?: throw IOException("no value for $promql")
        return value[1].jsonPrimitive.content.toDouble()
    }

    private fun queryRange(cfg: ServerConfig, promql: String, range: ChartRange, end: Long): MetricSeries {
        val url = cfg.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/query_range")
            .addQueryParameter("query", promql)
            .addQueryParameter("start", (end - range.rangeSec).toString())
            .addQueryParameter("end", end.toString())
            .addQueryParameter("step", "${range.stepSec}s")
            .build()
        val values = firstResult(get(url.toString(), cfg.authToken))["values"]?.jsonArray
            ?: throw IOException("no range values for $promql")
        val points = values.map { entry ->
            val pair = entry.jsonArray
            MetricPoint(
                timestampSec = pair[0].jsonPrimitive.content.toDouble().toLong(),
                value = pair[1].jsonPrimitive.content.toDouble(),
            )
        }
        return MetricSeries(promql, points)
    }

    private fun get(url: String, token: String): JsonObject {
        val request = Request.Builder().url(url).apply {
            if (token.isNotBlank()) header("Authorization", "Bearer $token")
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from $url")
            val body = response.body?.string() ?: throw IOException("empty body from $url")
            val root = json.parseToJsonElement(body).jsonObject
            if (root["status"]?.jsonPrimitive?.content != "success") {
                throw IOException("prometheus returned error for $url")
            }
            return root
        }
    }

    // Servers often scrape several targets; the first series is enough here
    private fun firstResult(root: JsonObject) =
        root["data"]?.jsonObject?.get("result")?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IOException("empty result set")

}
