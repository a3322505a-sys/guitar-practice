package com.a3322505a.guitarlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Adding a local skin only requires registering its palette here. */
object ThemeRegistry {
    val all: List<AppThemeSpec> = listOf(
        studio("mint_light", "青绿晨光", "薄荷白 × 青绿 × 暖橙", false,
            0xFFEFF8F7, 0xFFF9FDFC, 0xFFE2F1EF, 0xFFCEEAE7, 0x80152927,
            0xFF008C89, 0xFFD86B2E, 0x19274C49, 0xFFF07D32, 0xFF129DD1, 0xFF8666C5, 0xFF708C96, 0xFF252A2B),
        studio("apricot_light", "暖杏木色", "奶油白 × 陶土棕 × 蜂蜜金", false,
            0xFFFFF9F2, 0xFFFFFEFB, 0xFFFFF0E3, 0xFFF6DEC8, 0x802D211A,
            0xFFA55335, 0xFFB46E20, 0x202D211A, 0xFFD86125, 0xFF287FA8, 0xFF9E69B1, 0xFF897965, 0xFF31231E),
        studio("indigo_night", "靛蓝夜练", "深靛蓝 × 冰青 × 柔金", true,
            0xFF111726, 0xFF1A2336, 0xFF222E45, 0xFF263C53, 0xB3000000,
            0xFF64D6D3, 0xFFF2B577, 0x26FFFFFF, 0xFFFFB16C, 0xFF6EBEEB, 0xFFBD9AFF, 0xFF8493A9, 0xFF111923),
    )

    val default: AppThemeSpec get() = all.first()
    fun find(id: String?): AppThemeSpec {
        val mapped = when (id) {
            "forest_daylight" -> "mint_light"
            "luthier_warm" -> "apricot_light"
            "studio_dark" -> "indigo_night"
            else -> id
        }
        return all.firstOrNull { it.id == mapped } ?: default
    }

    private fun studio(
        id: String, name: String, description: String, dark: Boolean,
        background: Long, low: Long, high: Long, selected: Long, scrim: Long,
        primary: Long, secondary: Long, border: Long, root: Long, chord: Long,
        characteristic: Long, passing: Long, wood: Long,
    ): AppThemeSpec {
        val page = Color(background); val surface = Color(low); val raised = Color(high)
        val selection = Color(selected); val main = Color(primary); val accent = Color(secondary)
        val ink = Color(if (dark) 0xFFF3F4F6 else 0xFF1E272D)
        val muted = Color(if (dark) 0xFFB8C1CC else 0xFF47535D)
        val rootColor = Color(root); val chordColor = Color(chord)
        return AppThemeSpec(id, name, description, ThemeTokens(
            background = page, surfaceLow = surface, surfaceHigh = raised, surfaceSelected = selection,
            secondary = accent, borderSubtle = Color(border), noteRoot = rootColor,
            noteChordTone = chordColor, noteCharacteristic = Color(characteristic),
            noteScalePassing = Color(passing), fretboardWood = Color(wood),
            // Legacy fields remain mapped until their consumers migrate to semantic tokens.
            surface = surface, surfaceSecondary = raised, primary = main, primaryContainer = selection,
            accent = accent, accentContainer = raised, onPrimary = readableOn(main, Color.Black),
            onAccent = readableOn(accent, Color.Black), onSurface = ink, textPrimary = ink,
            textSecondary = muted, textTertiary = muted, border = Color(border),
            success = Color(if (dark) 0xFF6EE7B7 else 0xFF197449),
            warning = Color(if (dark) 0xFFFCD34D else 0xFF855400),
            danger = Color(if (dark) 0xFFFDA4AF else 0xFFB23B42), disabled = muted, scrim = Color(scrim),
            fretboardNoteNormal = chordColor, fretboardNoteActive = accent, fretboardRoot = rootColor,
            onFretboardNoteNormal = readableOn(chordColor, Color.Black),
            onFretboardNoteActive = readableOn(accent, Color.Black), onFretboardRoot = readableOn(rootColor, Color.Black),
            chordDot = main, onChordDot = readableOn(main, Color.Black),
            progressComplete = main, progressPending = muted,
            notationInk = ink, notationStaff = muted, notationHighlight = selection,
            notationActive = raised, notationSecondary = muted, diagramSurface = surface, diagramGrid = muted,
            primaryText = readablePrimary(main, ink, listOf(page, surface, raised, selection)),
            fretboardNoteStroke = chordColor,
        ), isDark = dark)
    }

    // Filled controls keep the exact palette; small labels use readable ink on either light or dark containers.
    private fun readablePrimary(primary: Color, ink: Color, backgrounds: List<Color>): Color {
        var candidate = primary
        repeat(20) {
            if (backgrounds.all {
                val a = it.luminance(); val b = candidate.luminance()
                (maxOf(a, b) + .05f) / (minOf(a, b) + .05f) >= 4.5f
            }) return candidate
            candidate = lerp(candidate, ink, .1f)
        }
        return ink
    }

    private fun readableOn(background: Color, dark: Color): Color {
        val lightContrast = 1.05f / (background.luminance() + .05f)
        val darkContrast = (background.luminance() + .05f) / (dark.luminance() + .05f)
        return when {
            maxOf(lightContrast, darkContrast) < 4.5f -> Color.Black
            lightContrast >= darkContrast -> Color.White
            else -> dark
        }
    }
}
