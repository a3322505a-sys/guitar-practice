@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.AlphaTabView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import com.a3322505a.guitarlearning.ui.PracticeSkin
import com.a3322505a.guitarlearning.ui.ThemeTokens

private fun Color.scoreColor() = alphaTab.model.Color(
    (red * 255).toDouble(), (green * 255).toDouble(), (blue * 255).toDouble(), (alpha * 255).toDouble())

/** Visual resources only: keep the score, MIDI, player and cursor handler intact. */
internal fun applyScoreSkin(view: AlphaTabView, colors: ThemeTokens) {
    view.setBackgroundColor(colors.surface.toArgb())
    view.barCursorFillColor = colors.notationHighlight.toArgb()
    view.beatCursorFillColor = colors.accent.copy(alpha = .35f).toArgb()
    view.selectionFillColor = colors.primaryContainer.copy(alpha = .6f).toArgb()
    applyScoreResources(view.settings.display.resources, colors)
    // Only visual resources change. In pinned SDK 1.8.4 this public refresh keeps
    // the unchanged player mode/instance and recolors already-mounted cursors.
    // Do not replace settings, the score, MIDI or the custom cursor handler here.
    view.api.updateSettings()
    if (view.api.score != null) view.api.render(null)
}

internal fun applyScoreResources(resources: alphaTab.RenderingResources, colors: ThemeTokens) {
    resources.apply {
        staffLineColor = colors.notationStaff.scoreColor()
        barSeparatorColor = colors.notationInk.scoreColor()
        barNumberColor = colors.textSecondary.scoreColor()
        mainGlyphColor = colors.notationInk.scoreColor()
        secondaryGlyphColor = colors.notationSecondary.scoreColor()
        scoreInfoColor = colors.textPrimary.scoreColor()
    }
}

/** Mounted in both views, so changing to the fretboard cannot destroy the session. */
@Composable
fun NativeScore(player: AlphaPlayback, modifier: Modifier, visible: Boolean) {
    val colors = PracticeSkin.colors
    val applied = remember(player) { arrayOfNulls<ThemeTokens>(1) }
    AndroidView(factory = { context -> AlphaTabView(context, null).also {
        it.barCursorFillColor = colors.notationHighlight.toArgb()
        it.beatCursorFillColor = colors.accent.copy(alpha = .35f).toArgb()
        it.selectionFillColor = colors.primaryContainer.copy(alpha = .6f).toArgb()
        player.attach(it)
    } },
        modifier = modifier, update = {
            it.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            if (applied[0] != colors) {
                applyScoreSkin(it, colors)
                applied[0] = colors
            }
        },
        onRelease = { player.detach(it) })
}
