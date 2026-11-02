package com.a3322505a.guitarlearning.practice

import android.content.SharedPreferences

/** Global display preference, independent of song/practice settings. Positive = later. */
class CursorSyncSettings(private val prefs: SharedPreferences) {
    fun load(): Int = runCatching { prefs.getInt(KEY, 0) }.getOrDefault(0).coerceIn(-500, 500)
    fun save(milliseconds: Int) { prefs.edit().putInt(KEY, milliseconds.coerceIn(-500, 500)).apply() }
    companion object { private const val KEY = "cursor-sync-offset-ms" }
}
