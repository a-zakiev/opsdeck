package com.zakiev.spatialdashboard.data

import android.content.Context

data class ServerConfig(val baseUrl: String, val authToken: String)

// Plain SharedPreferences is enough here: one URL and an optional token
// for a demo app, no need for DataStore
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var authToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    val serverConfig: ServerConfig
        get() = ServerConfig(serverUrl, authToken)

    companion object {
        const val DEFAULT_URL = "https://prometheus.demo.prometheus.io"
        private const val KEY_URL = "server_url"
        private const val KEY_TOKEN = "auth_token"
    }
}
