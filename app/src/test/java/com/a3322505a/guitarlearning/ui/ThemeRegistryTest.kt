package com.a3322505a.guitarlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class ThemeRegistryTest {
    @Test fun studioIsDefaultAndForestValuesAreExact() {
        assertEquals("studio_dark", ThemeRegistry.default.id)
        assertTrue(ThemeRegistry.default.isDark)
        assertEquals(Color(0xFF0F1216), ThemeRegistry.default.colors.background)
        val c = ThemeRegistry.find("forest_daylight").colors
        assertEquals(Color(0xFFF4F7F5), c.background)
        assertEquals(Color(0xFFE9EFEA), c.surfaceLow)
        assertEquals(Color(0xFFDCE5DF), c.surfaceHigh)
        assertEquals(Color(0xFFD0ECE3), c.surfaceSelected)
        assertEquals(Color(0x80111C16), c.scrim)
        assertEquals(Color(0xFF0D9488), c.primary)
        assertEquals(Color(0xFFEA580C), c.secondary)
        assertEquals(Color(0x1F000000), c.borderSubtle)
        assertEquals(Color(0xFFEA580C), c.noteRoot)
        assertEquals(Color(0xFF0284C7), c.noteChordTone)
        assertEquals(Color(0xFF7C3AED), c.noteCharacteristic)
        assertEquals(Color(0xFF64748B), c.noteScalePassing)
        assertEquals(Color(0xFF231F1D), c.fretboardWood)
        assertEquals(StudioWidth.Compact, studioWidth(599.dp))
        assertEquals(StudioWidth.Medium, studioWidth(600.dp))
        assertEquals(StudioWidth.Expanded, studioWidth(840.dp))
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
