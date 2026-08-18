package com.zakiev.spatialdashboard.data

import android.content.Context
import com.zakiev.spatialdashboard.model.ChartConfig
import kotlinx.serialization.json.Json

// User-defined charts stored as one JSON blob in SharedPreferences
class ChartsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("charts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<ChartConfig> = prefs.getString(KEY_CHARTS, null)
        ?.let { runCatching { json.decodeFromString<List<ChartConfig>>(it) }.getOrNull() }
        ?: emptyList()

    fun save(charts: List<ChartConfig>) {
        prefs.edit().putString(KEY_CHARTS, json.encodeToString(charts)).apply()
    }

    private companion object {
        const val KEY_CHARTS = "charts_json"
    }
}
