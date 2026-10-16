package com.a3322505a.guitarlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Adding a local skin only requires registering its palette here. */
object ThemeRegistry {
    val all: List<AppThemeSpec> = listOf(
        skin("forest", "森林绿 · Forest Default", "冷灰白 × 森林绿 × 琥珀金", 0xFFF4F6F8, 0xFF057A55, 0xFFF59E0B),
        skin("dusty_pink", "豆沙粉 · Dusty Pink", "暖灰白 × 豆沙粉 × 香槟金", 0xFFF7F4F1, 0xFFA34F70, 0xFFBE9552),
        skin("sakura", "樱花粉 · Sakura Soft", "奶油白 × 樱花粉 × 玫瑰金", 0xFFFBF6F5, 0xFFAD5278, 0xFFC08B79),
        skin("berry", "莓果粉 · Berry Calm", "雾灰白 × 莓果粉 × 深莓红", 0xFFF6F5F7, 0xFF9F3F69, 0xFF794253),
        skin("warm_neutral", "暖米白 · Warm Neutral", "暖米白 × 焦糖棕 × 杏橙", 0xFFF8F4EE, 0xFF8A573A, 0xFFD79960),
        skin("cool_minimal", "极简灰 · Cool Minimal", "冷白灰 × 石墨灰 × 冰蓝", 0xFFF4F6F8, 0xFF414C57, 0xFF86AEC5),
        skin("sage", "鼠尾草绿 · Sage", "奶油白 × 鼠尾草绿 × 香槟金", 0xFFF7F6F2, 0xFF607663, 0xFFD1AC63),
        skin("deep_sea", "深海蓝 · Deep Sea", "冷灰白 × 深海蓝 × 暖铜", 0xFFF2F5F8, 0xFF315A70, 0xFFC68C57),
        skin("ink_green", "墨绿 · Ink Green", "暖象牙 × 墨绿 × 陶土橙", 0xFFF8F4EE, 0xFF2E5B4D, 0xFFC77D59),
        skin("mist_purple", "雾紫 · Mist Purple", "珍珠白 × 雾紫 × 柔金", 0xFFF6F4F9, 0xFF78678F, 0xFFBFA069),
        skin("coral", "珊瑚粉 · Coral", "珍珠白 × 珊瑚粉 × 海军蓝", 0xFFF7F7F8, 0xFFAF506C, 0xFF365C7A),
    )

    val default: AppThemeSpec get() = all.first()
    fun find(id: String?): AppThemeSpec = all.firstOrNull { it.id == id } ?: default

    private fun skin(id: String, name: String, description: String, background: Long, primary: Long, accent: Long): AppThemeSpec {
        val page = Color(background)
        val main = Color(primary)
        val highlight = Color(accent)
        val surface = Color.White
        val ink = Color(0xFF1E272D)
        val secondary = Color(0xFF525E68)
        val tertiary = Color(0xFF5B6771)
        val mainWash = lerp(surface, main, .14f)
        val normalNoteFill = lerp(surface, main, .20f)
        val accentWash = lerp(surface, highlight, .16f)
        val consoleBg = lerp(page, main, .06f)
        val stageWell = lerp(surface, page, .58f)
        val border = lerp(page, ink, .14f)
        val onMain = readableOn(main, ink)
        val onHighlight = readableOn(highlight, ink)
        val onNormalNote = readableOn(normalNoteFill, ink)
        return AppThemeSpec(id, name, description, ThemeTokens(
            background = page, surface = surface, surfaceSecondary = consoleBg,
            primary = main, primaryContainer = mainWash, accent = highlight, accentContainer = accentWash,
            onPrimary = onMain, onAccent = onHighlight, onSurface = ink,
            textPrimary = ink, textSecondary = secondary, textTertiary = tertiary, border = border,
            success = Color(0xFF197449), warning = Color(0xFF855400), danger = Color(0xFFB23B42),
            disabled = Color(0xFF9DA6AE), scrim = Color(0xFF152128).copy(alpha = .42f),
            fretboardNoteNormal = normalNoteFill, fretboardNoteActive = highlight, fretboardRoot = main,
            onFretboardNoteNormal = onNormalNote, onFretboardNoteActive = onHighlight, onFretboardRoot = onMain,
            chordDot = main, onChordDot = onMain,
            progressComplete = main, progressPending = border,
            notationInk = ink, notationStaff = lerp(stageWell, secondary, .62f),
            notationHighlight = mainWash, notationActive = accentWash, notationSecondary = secondary,
            diagramSurface = stageWell, diagramGrid = lerp(stageWell, secondary, .40f),
        ))
    }

    private fun readableOn(background: Color, dark: Color): Color {
        val lightContrast = 1.05f / (background.luminance() + .05f)
        val darkContrast = (background.luminance() + .05f) / (dark.luminance() + .05f)
        return if (lightContrast >= darkContrast) Color.White else dark
    }
}
