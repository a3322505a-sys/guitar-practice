package com.a3322505a.guitarlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Adding a local skin only requires registering its palette here. */
object ThemeRegistry {
    val all: List<AppThemeSpec> = listOf(
        skin("forest", "森林绿 · Forest Default", "冷灰白 × 森林绿 × 琥珀金", 0xFFF4F6F8, 0xFF057A55, 0xFFF59E0B),
        skin("warm_neutral", "暖米白 · Warm Neutral", "暖米白 × 焦糖棕 × 杏橙", 0xFFF8F4EE, 0xFF8A573A, 0xFFD79960),
        skin("cool_minimal", "极简灰 · Cool Minimal", "冷白灰 × 石墨灰 × 冰蓝", 0xFFF4F6F8, 0xFF414C57, 0xFF86AEC5),
        skin("deep_sea", "深海蓝 · Deep Sea", "冷灰白 × 深海蓝 × 暖铜", 0xFFF2F5F8, 0xFF315A70, 0xFFC68C57),
        skin("mist_purple", "雾紫 · Mist Purple", "珍珠白 × 雾紫 × 柔金", 0xFFF6F4F9, 0xFF78678F, 0xFFBFA069),
        skin("coral", "珊瑚粉 · Coral", "珍珠白 × 珊瑚粉 × 海军蓝", 0xFFF7F7F8, 0xFFAF506C, 0xFF365C7A),
        skin("coastal_blue", "海盐蓝 · Coastal Blue", "冰川蓝 × 深海蓝 × 落日橙",
            0xFFEBF3F9, 0xFF1B5E91, 0xFFF26B3A,
            surface = 0xFFF8FBFE, nodeLight = 0xFFD5E6F5, border = 0xFF639AC4),
        skin("sunburst", "日落红 · Sunburst", "羊皮纸 × 丹霞红 × 复古金",
            0xFFF4ECE1, 0xFFB84028, 0xFFE09F3E,
            surface = 0xFFFCF9F5, nodeLight = 0xFFF2D6CE, border = 0xFFD07B68),
        skin("lagoon_teal", "松石青 · Lagoon Teal", "薄荷雾 × 松石青 × 珊瑚橘",
            0xFFE6F2F0, 0xFF136F63, 0xFFE07A5F,
            surface = 0xFFF6FBF9, nodeLight = 0xFFCBE8E3, border = 0xFF5DA89E),
        skin("ochre_vibe", "琥珀黄 · Ochre Vibe", "燕麦奶 × 姜黄棕 × 松林绿",
            0xFFF5EFE0, 0xFFB07219, 0xFF2D6A4F,
            surface = 0xFFFDFBF6, nodeLight = 0xFFEFE2C6, border = 0xFFCCA05A),
    )

    val default: AppThemeSpec get() = all.first()
    fun find(id: String?): AppThemeSpec = all.firstOrNull { it.id == id } ?: default

    private fun skin(
        id: String, name: String, description: String, background: Long, primary: Long, accent: Long,
        surface: Long? = null, nodeLight: Long? = null, border: Long? = null,
    ): AppThemeSpec {
        val page = Color(background)
        val main = Color(primary)
        val highlight = Color(accent)
        val cardSurface = surface?.let { Color(it) } ?: Color.White
        val ink = Color(0xFF1E272D)
        val secondary = Color(0xFF525E68)
        val tertiary = Color(0xFF5B6771)
        val mainWash = nodeLight?.let { Color(it) } ?: lerp(cardSurface, main, .14f)
        val normalNoteFill = nodeLight?.let { Color(it) } ?: lerp(cardSurface, main, .20f)
        val accentWash = lerp(cardSurface, highlight, .16f)
        val consoleBg = if (surface != null) page else lerp(page, main, .06f)
        val stageWell = if (surface != null) cardSurface else lerp(cardSurface, page, .58f)
        val outline = border?.let { Color(it) } ?: lerp(page, ink, .14f)
        val onMain = readableOn(main, ink)
        val onHighlight = readableOn(highlight, ink)
        val onNormalNote = readableOn(normalNoteFill, ink)
        return AppThemeSpec(id, name, description, ThemeTokens(
            background = page, surface = cardSurface, surfaceSecondary = consoleBg,
            primary = main, primaryContainer = mainWash, accent = highlight, accentContainer = accentWash,
            onPrimary = onMain, onAccent = onHighlight, onSurface = ink,
            textPrimary = ink, textSecondary = secondary, textTertiary = tertiary, border = outline,
            success = Color(0xFF197449), warning = Color(0xFF855400), danger = Color(0xFFB23B42),
            disabled = Color(0xFF9DA6AE), scrim = Color(0xFF152128).copy(alpha = .42f),
            fretboardNoteNormal = normalNoteFill, fretboardNoteActive = highlight, fretboardRoot = main,
            onFretboardNoteNormal = onNormalNote, onFretboardNoteActive = onHighlight, onFretboardRoot = onMain,
            chordDot = main, onChordDot = onMain,
            progressComplete = if (surface != null) highlight else main, progressPending = outline,
            notationInk = ink, notationStaff = lerp(stageWell, secondary, .62f),
            notationHighlight = mainWash, notationActive = accentWash, notationSecondary = secondary,
            diagramSurface = stageWell, diagramGrid = lerp(stageWell, secondary, .40f),
            primaryText = readablePrimary(main, ink, listOf(page, cardSurface, consoleBg, mainWash, stageWell)),
            fretboardNoteStroke = border?.let { Color(it) } ?: main.copy(alpha = .65f),
        ))
    }

    // Filled controls keep the exact palette; small primary labels need a darker ink on pale containers.
    private fun readablePrimary(primary: Color, ink: Color, backgrounds: List<Color>): Color {
        var candidate = primary
        repeat(20) {
            if (backgrounds.all { (it.luminance() + .05f) / (candidate.luminance() + .05f) >= 4.5f }) return candidate
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
