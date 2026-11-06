package com.kevin.arclauncher.data

import android.content.Context
import kotlinx.serialization.json.Json

/** Persists [LauncherLayout] as one JSON string in SharedPreferences. */
class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): LauncherLayout =
        prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString<LauncherLayout>(it) }.getOrNull() }
            ?.migrated()?.normalized() ?: LauncherLayout()

    fun save(layout: LauncherLayout) {
        prefs.edit().putString(KEY, json.encodeToString(LauncherLayout.serializer(), layout)).apply()
    }

    private companion object {
        const val KEY = "layout_v1"
    }
}
