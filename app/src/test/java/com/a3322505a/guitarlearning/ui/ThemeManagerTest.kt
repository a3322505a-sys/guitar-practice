package com.a3322505a.guitarlearning.ui

import android.content.Context
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeManagerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val managers = mutableListOf<ThemeManager>()
    private fun manager() = ThemeManager(context).also { managers += it }

    @Before fun clearAppearancePreference() {
        context.getSharedPreferences(ThemeManager.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun releaseListeners() { managers.forEach { it.close() } }

    @Test fun selectionSurvivesManagerRecreationAndResetRestoresForest() {
        val first = manager()
        first.select("dusty_pink")
        assertEquals("dusty_pink", first.current.id)
        val restarted = manager()
        assertEquals("dusty_pink", restarted.current.id)
        restarted.reset()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ThemeRegistry.default, first.current)
        assertEquals(ThemeRegistry.default, manager().current)
    }

    @Test fun separateActivitiesReceiveTheSamePreferenceChange() {
        val main = manager()
        val gp = manager()
        gp.select("deep_sea")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("deep_sea", main.current.id)
        main.select("sakura")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("sakura", gp.current.id)
    }

    @Test fun unknownStoredThemeAndUnknownSelectionUseTheDefault() {
        context.getSharedPreferences(ThemeManager.PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(ThemeManager.THEME_ID, "missing-custom-theme").commit()
        val themes = manager()
        assertEquals(ThemeRegistry.default, themes.current)
        themes.select("not-registered")
        assertEquals(ThemeRegistry.default, themes.current)
        assertEquals(ThemeRegistry.default.id,
            context.getSharedPreferences(ThemeManager.PREFERENCES, Context.MODE_PRIVATE).getString(ThemeManager.THEME_ID, null))
    }
}
