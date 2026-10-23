package com.a3322505a.guitarlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class ThemeRegistryTest {
    @Test fun mintIsDefaultAndRetiredIdsMapToTheirCorrespondingPalettes() {
        assertEquals("mint_light", ThemeRegistry.default.id)
        assertFalse(ThemeRegistry.default.isDark)
        assertEquals(Color(0xFFEFF8F7), ThemeRegistry.default.colors.background)
        assertEquals("mint_light", ThemeRegistry.find("forest_daylight").id)
        assertEquals("apricot_light", ThemeRegistry.find("luthier_warm").id)
        assertEquals("indigo_night", ThemeRegistry.find("studio_dark").id)
        assertTrue(ThemeRegistry.find("indigo_night").isDark)
    }

    @Test fun everyPresetHasReadableTextAndMusicalMarkers() {
        assertTrue(ThemeRegistry.all.size == 3)
        assertEquals(ThemeRegistry.all.size, ThemeRegistry.all.map { it.id }.distinct().size)
        ThemeRegistry.all.forEach { theme ->
            val c = theme.colors
            listOf(
                c.textPrimary to c.background,
                c.textPrimary to c.surfaceSecondary,
                c.textSecondary to c.surface,
                c.textTertiary to c.surfaceSecondary,
                c.primaryText to c.background,
                c.primaryText to c.surface,
                c.primaryText to c.primaryContainer,
                c.primaryText to c.diagramSurface,
                c.onPrimary to c.primary,
                c.toggleActiveContent to c.toggleActiveBackground,
                c.onAccent to c.accent,
                c.onFretboardNoteNormal to c.fretboardNoteNormal,
                c.onFretboardNoteActive to c.fretboardNoteActive,
                c.onFretboardRoot to c.fretboardRoot,
                c.onChordDot to c.chordDot,
                c.notationInk to c.diagramSurface,
                c.notationSecondary to c.diagramSurface,
            ).forEach { (foreground, background) ->
                assertTrue("${theme.id}: contrast ${contrast(foreground, background)}", contrast(foreground, background) >= 4.5f)
            }
        }
    }

    @Test fun enabledTransportSwitchesStandOutFromDisabledBackgroundsInEveryTheme() {
        ThemeRegistry.all.forEach { theme ->
            assertTrue(theme.id, contrast(theme.colors.toggleActiveBackground, theme.colors.surfaceHigh) >= 2.5f)
        }
        assertEquals(3, ThemeRegistry.all.map { it.colors.toggleActiveBackground }.distinct().size)
    }

    @Test fun visualGeometryIsSharedAndInvalidIdsFallBack() {
        assertEquals(ThemeRegistry.default, ThemeRegistry.find(null))
        assertEquals(ThemeRegistry.default, ThemeRegistry.find("retired-skin"))
        ThemeRegistry.all.forEach {
            assertEquals(20.dp, it.metrics.cornerLarge)
            assertEquals(14.dp, it.metrics.cornerMedium)
            assertEquals(12.dp, it.metrics.cornerSmall)
            assertEquals(1.dp, it.metrics.borderWidth)
            assertEquals(48.dp, it.metrics.segmentStyle.height)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance(); val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
}
