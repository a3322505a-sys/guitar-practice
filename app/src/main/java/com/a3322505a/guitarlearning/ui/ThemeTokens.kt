package com.a3322505a.guitarlearning.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Visual values shared by every native practice screen and musical diagram. */
@Immutable
data class ThemeTokens(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val primary: Color,
    val primaryContainer: Color,
    val accent: Color,
    val accentContainer: Color,
    val onPrimary: Color,
    val onAccent: Color,
    val onSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val disabled: Color,
    val scrim: Color,
    val fretboardNoteNormal: Color,
    val fretboardNoteActive: Color,
    val fretboardRoot: Color,
    val onFretboardNoteNormal: Color,
    val onFretboardNoteActive: Color,
    val onFretboardRoot: Color,
    val chordDot: Color,
    val onChordDot: Color,
    val progressComplete: Color,
    val progressPending: Color,
    val notationInk: Color,
    val notationStaff: Color,
    val notationHighlight: Color,
    val notationActive: Color,
    val notationSecondary: Color,
    val diagramSurface: Color,
    val diagramGrid: Color,
)

@Immutable
data class ThemeButtonStyle(
    val minHeight: Dp = 48.dp,
    val minHeightLarge: Dp = 64.dp,
    val horizontalPadding: Dp = 12.dp,
)

@Immutable
data class ThemeSegmentStyle(
    val height: Dp = 44.dp,
    val inset: Dp = 3.dp,
)

@Immutable
data class ThemeMetrics(
    val cornerLarge: Dp = 20.dp,
    val cornerMedium: Dp = 14.dp,
    val cornerSmall: Dp = 12.dp,
    val borderWidth: Dp = 1.dp,
    val shadowLevel: Dp = 1.dp,
    val buttonStyle: ThemeButtonStyle = ThemeButtonStyle(),
    val segmentStyle: ThemeSegmentStyle = ThemeSegmentStyle(),
)

@Immutable
data class AppThemeSpec(
    val id: String,
    val displayName: String,
    val description: String,
    val colors: ThemeTokens,
    val metrics: ThemeMetrics = ThemeMetrics(),
)
