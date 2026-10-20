package com.a3322505a.guitarlearning.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Presentation preferences are independent of the practice engine and its saved state. */
class ThemeManager(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    var current: AppThemeSpec by mutableStateOf(ThemeRegistry.find(preferences.getString(THEME_ID, null)))
        private set

    init {
        // Persist the fallback once so retired theme IDs do not survive recreation.
        if (preferences.getString(THEME_ID, null) != current.id) {
            preferences.edit().putString(THEME_ID, current.id).apply()
        }
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun select(id: String) {
        val selected = ThemeRegistry.find(id)
        current = selected
        preferences.edit().putString(THEME_ID, selected.id).apply()
    }

    fun reset() = select(ThemeRegistry.default.id)

    fun refresh() {
        current = ThemeRegistry.find(preferences.getString(THEME_ID, null))
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == THEME_ID || key == null) refresh()
    }

    fun close() { preferences.unregisterOnSharedPreferenceChangeListener(this) }

    companion object {
        internal const val PREFERENCES = "practice_appearance"
        internal const val THEME_ID = "theme_id"
    }
}
