package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalLargePracticeControls = staticCompositionLocalOf { false }
private val LocalPracticeTheme = staticCompositionLocalOf { ThemeRegistry.default }

object PracticeSkin {
    val theme: AppThemeSpec @Composable get() = LocalPracticeTheme.current
    val colors: ThemeTokens @Composable get() = theme.colors
    val metrics: ThemeMetrics @Composable get() = theme.metrics
}

@Composable
fun Modifier.practiceBackground(): Modifier = background(PracticeSkin.colors.background)

@Composable
fun PracticeTheme(
    largeControls: Boolean = false,
    theme: AppThemeSpec = LocalPracticeTheme.current,
    content: @Composable () -> Unit,
) {
    val c = theme.colors
    val m = theme.metrics
    CompositionLocalProvider(LocalLargePracticeControls provides largeControls, LocalPracticeTheme provides theme) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = c.primary, onPrimary = c.onPrimary,
                primaryContainer = c.primaryContainer, onPrimaryContainer = c.textPrimary,
                secondary = c.primary, onSecondary = c.onPrimary,
                secondaryContainer = c.surfaceSecondary, onSecondaryContainer = c.textPrimary,
                tertiary = c.accent, onTertiary = c.onAccent,
                tertiaryContainer = c.accentContainer, onTertiaryContainer = c.textPrimary,
                background = c.background, onBackground = c.textPrimary,
                surface = c.surface, onSurface = c.textPrimary,
                surfaceVariant = c.surfaceSecondary, onSurfaceVariant = c.textSecondary,
                outline = c.border, outlineVariant = c.border,
                error = c.danger, onError = c.surface, scrim = c.scrim,
                surfaceTint = c.primary,
                surfaceContainerLowest = c.surface,
                surfaceContainerLow = c.surface,
                surfaceContainer = c.surface,
                surfaceContainerHigh = c.surfaceSecondary,
                surfaceContainerHighest = c.surfaceSecondary,
                surfaceBright = c.surface, surfaceDim = c.background,
                inverseSurface = c.textPrimary, inverseOnSurface = c.surface,
                inversePrimary = c.primaryContainer,
            ),
            typography = Typography(
                titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-.4).sp),
                titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
                labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = if (largeControls) 18.sp else 14.sp, lineHeight = if (largeControls) 26.sp else 20.sp),
                bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(m.cornerSmall),
                small = RoundedCornerShape(m.cornerSmall),
                medium = RoundedCornerShape(m.cornerMedium),
                large = RoundedCornerShape(m.cornerLarge),
                extraLarge = RoundedCornerShape(m.cornerLarge),
            ),
            content = content,
        )
    }
}

/** Shared shell for fretboard, tablature, chord, rhythm and settings content. */
@Composable
fun PracticeContentCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PracticeSkin.colors
    val m = PracticeSkin.metrics
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(m.cornerLarge),
        color = c.surface,
        contentColor = c.textPrimary,
        border = BorderStroke(m.borderWidth, c.border),
        shadowElevation = m.shadowLevel,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null || subtitle != null) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            if (actions != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            content()
            if (footer != null) {
                HorizontalDivider(color = c.border, thickness = m.borderWidth)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), content = footer)
            }
        }
    }
}

@Composable
fun DiagramContainer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = PracticeSkin.colors
    val m = PracticeSkin.metrics
    Surface(modifier = modifier, shape = RoundedCornerShape(m.cornerMedium), color = c.diagramSurface,
        border = BorderStroke(m.borderWidth, c.border)) {
        Box(Modifier.padding(6.dp), content = content)
    }
}

@Composable
fun PracticeAction(title: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = PracticeSkin.colors
    val m = PracticeSkin.metrics
    val large = LocalLargePracticeControls.current
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = if (large) m.buttonStyle.minHeightLarge else m.buttonStyle.minHeight),
        shape = RoundedCornerShape(m.cornerSmall),
        contentPadding = PaddingValues(horizontal = m.buttonStyle.horizontalPadding, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary,
            disabledContainerColor = c.surfaceSecondary, disabledContentColor = c.textTertiary)) {
        TransportMotif(if (title == "暂停") "pause" else "play", Modifier.size(if (large) 22.dp else 17.dp), if (enabled) c.onPrimary else c.textTertiary)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TransportMotif(kind: String, modifier: Modifier = Modifier, color: Color = PracticeSkin.colors.onPrimary) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (kind == "pause") {
            drawRoundRect(color, Offset(w * .15f, h * .1f), Size(w * .22f, h * .8f), CornerRadius(w * .07f))
            drawRoundRect(color, Offset(w * .63f, h * .1f), Size(w * .22f, h * .8f), CornerRadius(w * .07f))
        } else if (kind == "speaker") {
            val p = Path().apply { moveTo(w * .1f, h * .35f); lineTo(w * .3f, h * .35f); lineTo(w * .53f, h * .13f); lineTo(w * .53f, h * .87f); lineTo(w * .3f, h * .65f); lineTo(w * .1f, h * .65f); close() }
            drawPath(p, color)
            drawArc(color, -55f, 110f, false, Offset(w * .4f, h * .18f), Size(w * .48f, h * .64f), style = Stroke(w * .1f))
        } else {
            val p = Path().apply { moveTo(w * .22f, h * .1f); lineTo(w * .86f, h * .5f); lineTo(w * .22f, h * .9f); close() }
            drawPath(p, color)
        }
    }
}

private fun DrawScope.musicNote(x: Float, y: Float, s: Float, color: Color) {
    drawOval(color, Offset(x, y + s * .65f), Size(s * .38f, s * .25f))
    drawLine(color, Offset(x + s * .34f, y + s * .77f), Offset(x + s * .34f, y), s * .075f)
    drawLine(color, Offset(x + s * .34f, y + s * .04f), Offset(x + s * .64f, y + s * .18f), s * .12f)
}

/** Small native vector; every decorative value follows the active palette. */
@Composable
fun MusicMotif(modifier: Modifier = Modifier, kind: String = "guitar", color: Color = PracticeSkin.colors.primary) {
    val c = PracticeSkin.colors
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (kind == "notes") {
            musicNote(w * .1f, h * .12f, w * .68f, color)
            musicNote(w * .62f, h * .45f, w * .38f, c.accent)
        } else if (kind == "metronome") {
            val p = Path().apply { moveTo(w * .2f, h * .86f); lineTo(w * .38f, h * .15f); quadraticTo(w * .5f, 0f, w * .62f, h * .15f); lineTo(w * .82f, h * .86f); close() }
            drawPath(p, c.accent)
            drawLine(color, Offset(w * .5f, h * .68f), Offset(w * .72f, h * .18f), w * .08f)
            drawCircle(color, w * .09f, Offset(w * .69f, h * .25f))
            drawLine(color, Offset(w * .29f, h * .77f), Offset(w * .74f, h * .77f), w * .07f)
        } else if (kind == "pick") {
            val p = Path().apply { moveTo(w * .16f, h * .2f); cubicTo(w * .4f, -h * .07f, w * .95f, h * .08f, w * .87f, h * .4f); cubicTo(w * .8f, h * .75f, w * .46f, h * 1.02f, w * .3f, h * .84f); cubicTo(w * .08f, h * .6f, w * .02f, h * .37f, w * .16f, h * .2f); close() }
            drawPath(p, color)
            drawCircle(c.onPrimary, w * .12f, Offset(w * .47f, h * .35f), style = Stroke(w * .055f))
        } else {
            drawCircle(c.primaryContainer, w * .43f, Offset(w * .49f, h * .54f))
            rotate(27f, Offset(w * .47f, h * .52f)) {
                drawOval(c.accent, Offset(w * .17f, h * .51f), Size(w * .53f, h * .46f))
                drawOval(c.accent, Offset(w * .24f, h * .36f), Size(w * .4f, h * .34f))
                drawRoundRect(c.primary, Offset(w * .37f, h * .13f), Size(w * .14f, h * .46f), CornerRadius(w * .035f))
                drawRoundRect(c.textPrimary, Offset(w * .32f, h * .03f), Size(w * .23f, h * .2f), CornerRadius(w * .05f))
                drawCircle(c.onAccent, w * .1f, Offset(w * .44f, h * .66f))
                drawLine(c.textPrimary, Offset(w * .32f, h * .83f), Offset(w * .55f, h * .83f), w * .05f)
                repeat(3) { i -> drawLine(c.surface.copy(alpha = .75f), Offset(w * (.405f + i * .033f), h * .14f), Offset(w * (.405f + i * .033f), h * .82f), w * .012f) }
            }
            musicNote(w * .76f, h * .08f, w * .26f, c.primary)
            drawCircle(c.surface, w * .035f, Offset(w * .08f, h * .3f))
        }
    }
}

@Composable
fun PickMotif(modifier: Modifier = Modifier) = MusicMotif(modifier.size(38.dp), "pick", PracticeSkin.colors.primary)
