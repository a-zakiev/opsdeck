package com.zakiev.spatialdashboard.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Where the user left a panel: offset from its layout slot plus rotation
// (both in the parent's coordinate space, translation in pixels) and size.
// Zero size means "use the default".
@Serializable
data class PanelPlacement(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
    val rotW: Float = 1f,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
)

class LayoutRepository(context: Context) {

    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Map<String, PanelPlacement> = prefs.getString(KEY_LAYOUT, null)
        ?.let { runCatching { json.decodeFromString<Map<String, PanelPlacement>>(it) }.getOrNull() }
        ?: emptyMap()

    fun save(placements: Map<String, PanelPlacement>) {
        prefs.edit().putString(KEY_LAYOUT, json.encodeToString(placements)).apply()
    }

    private companion object {
        const val KEY_LAYOUT = "layout_json"
    }
}
