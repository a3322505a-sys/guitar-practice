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
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.content.Context
import android.widget.FrameLayout
import com.a3322505a.guitarlearning.ui.PracticeSkin
import com.a3322505a.guitarlearning.ui.ThemeTokens

/** Observe gestures without intercepting the native scroll container. */
internal class PracticeScoreHost(context: Context) : FrameLayout(context) {
    val native = AlphaTabView(context,null)
    init { addView(native,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT)) }
    private var x=0f; private var y=0f; private var down=0L
    private var moved=false; private var tapped=false
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x=event.x; y=event.y; down=event.eventTime; moved=false; tapped=false }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> { moved=true; tapped=false }
            MotionEvent.ACTION_MOVE -> if(kotlin.math.abs(event.x-x)>slop || kotlin.math.abs(event.y-y)>slop) moved=true
            MotionEvent.ACTION_UP -> tapped=!moved && event.eventTime-down<ViewConfiguration.getLongPressTimeout()
        }
        return super.dispatchTouchEvent(event)
    }
    fun consumeTap(): Boolean = tapped.also { tapped=false }
}

private fun Color.scoreColor() = alphaTab.model.Color(
    (red * 255).toDouble(), (green * 255).toDouble(), (blue * 255).toDouble(), (alpha * 255).toDouble())

/** Visual resources only: keep the score, MIDI, player and cursor handler intact. */
internal fun applyScoreSkin(view: AlphaTabView, colors: ThemeTokens) {
    view.setBackgroundColor(colors.diagramSurface.toArgb())
    // The Android cursor is painted over the score; an opaque wash hides the notes in the active bar.
    view.barCursorFillColor = colors.notationHighlight.copy(alpha = .18f).toArgb()
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
    AndroidView(factory = { context -> PracticeScoreHost(context).also { host ->
        host.native.barCursorFillColor = colors.notationHighlight.copy(alpha = .18f).toArgb()
        host.native.beatCursorFillColor = colors.accent.copy(alpha = .35f).toArgb()
        host.native.selectionFillColor = colors.primaryContainer.copy(alpha = .6f).toArgb()
        player.attach(host.native,host::consumeTap)
    } },
        modifier = modifier, update = {
            it.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            if (applied[0] != colors) {
                applyScoreSkin(it.native, colors)
                applied[0] = colors
            }
        },
        onRelease = { player.detach(it.native) })
}
