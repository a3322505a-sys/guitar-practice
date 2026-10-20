package com.a3322505a.guitarlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Adding a local skin only requires registering its palette here. */
object ThemeRegistry {
    val all: List<AppThemeSpec> = listOf(
        studio("studio_dark", "黑金录音室", "深灰蓝 × 琥珀金 × 电光青", true,
            0xFF0F1216, 0xFF171B21, 0xFF212730, 0xFF2A2418, 0xB3000000,
            0xFFF59E0B, 0xFF38BDF8, 0x26FFFFFF, 0xFFF97316, 0xFF38BDF8, 0xFFA855F7, 0xFF475569, 0xFF181411),
        studio("luthier_warm", "暖木工坊", "羊皮纸 × 玫瑰木 × 琥珀金", false,
            0xFFF7F3EB, 0xFFEFE9DE, 0xFFE5DEC9, 0xFFF3E3C8, 0x801A1815,
            0xFFB45309, 0xFF0369A1, 0x1F000000, 0xFFDC2626, 0xFF0284C7, 0xFF7E22CE, 0xFF64748B, 0xFF2D221B),
        studio("forest_daylight", "森林日间", "冷灰白 × 松石绿 × 暖橙", false,
            0xFFF4F7F5, 0xFFE9EFEA, 0xFFDCE5DF, 0xFFD0ECE3, 0x80111C16,
            0xFF0D9488, 0xFFEA580C, 0x1F000000, 0xFFEA580C, 0xFF0284C7, 0xFF7C3AED, 0xFF64748B, 0xFF231F1D),
    )

    val default: AppThemeSpec get() = all.first()
    fun find(id: String?): AppThemeSpec = all.firstOrNull { it.id == id } ?: default

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
