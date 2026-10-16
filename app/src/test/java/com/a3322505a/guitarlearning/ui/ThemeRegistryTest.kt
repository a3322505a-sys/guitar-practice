package com.a3322505a.guitarlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class ThemeRegistryTest {
    @Test fun defaultPaletteMatchesTheRequestedForestColors() {
        val c = ThemeRegistry.default.colors
        assertEquals(Color(0xFFF4F6F8), c.background)
        assertEquals(Color(0xFFFFFFFF), c.surface)
        assertEquals(Color(0xFF057A55), c.primary)
        assertEquals(Color(0xFFF59E0B), c.accent)
    }

    @Test fun everyPresetHasReadableTextAndMusicalMarkers() {
        assertTrue(ThemeRegistry.all.size >= 6)
        assertEquals(ThemeRegistry.all.size, ThemeRegistry.all.map { it.id }.distinct().size)
        ThemeRegistry.all.forEach { theme ->
            val c = theme.colors
            listOf(
                c.textPrimary to c.background,
                c.textSecondary to c.surface,
                c.textTertiary to c.surfaceSecondary,
                c.primary to c.surface,
                c.onPrimary to c.primary,
                c.onAccent to c.accent,
                c.onFretboardNoteNormal to c.fretboardNoteNormal,
                c.onFretboardNoteActive to c.fretboardNoteActive,
                c.onFretboardRoot to c.fretboardRoot,
                c.onChordDot to c.chordDot,
            ).forEach { (foreground, background) ->
                assertTrue("${theme.id}: contrast ${contrast(foreground, background)}", contrast(foreground, background) >= 4.5f)
            }
        }
    }

    @Test fun visualGeometryIsSharedAndInvalidIdsFallBack() {
        assertEquals(ThemeRegistry.default, ThemeRegistry.find(null))
        assertEquals(ThemeRegistry.default, ThemeRegistry.find("retired-skin"))
        ThemeRegistry.all.forEach {
            assertEquals(20.dp, it.metrics.cornerLarge)
            assertEquals(14.dp, it.metrics.cornerMedium)
            assertEquals(12.dp, it.metrics.cornerSmall)
            assertEquals(1.dp, it.metrics.borderWidth)
            assertEquals(44.dp, it.metrics.segmentStyle.height)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance(); val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
}
