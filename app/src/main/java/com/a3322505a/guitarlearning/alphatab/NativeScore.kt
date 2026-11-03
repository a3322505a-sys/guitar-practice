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
internal class PracticeScoreHost(context: Context, private val refreshViewport: Boolean = true) : FrameLayout(context) {
    val native = AlphaTabView(context,null)
    init { addView(native,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT)) }
    // AlphaTab 1.8.4 measures its visible bitmap chunks on the render surface.
    // During a fling the scroll changes faster than the child can complete layout;
    // an off-screen bitmap can be recycled after the last scroll callback.
    // Coalesce the cheap viewport sync to one frame, then rerender once at rest.
    private var viewport: List<Int>? = null
    private var framePending = false
    private var forceFrame = false
    private var redrawOnIdle = false
    private val touchGate = ScoreTouchGate(ViewConfiguration.get(context).scaledTouchSlop) {
        val cancel = MotionEvent.obtain(0L, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }
    private val viewportFrame = Runnable {
        framePending = false
        val force = forceFrame
        forceFrame = false
        refreshViewport(force)
    }
    private fun scheduleViewportRefresh(force: Boolean = false) {
        if (!refreshViewport) return
        forceFrame = forceFrame || force
        if (!framePending) {
            framePending = true
            postOnAnimation(viewportFrame)
        }
    }
    private fun refreshViewport(force: Boolean = false) {
        if (!refreshViewport || !isAttachedToWindow) return
        if (!isShown) { viewport = null; return }
        val horizontal = native.findViewById<android.widget.HorizontalScrollView>(net.alphatab.R.id.outerScroll)
        val vertical = native.findViewById<android.widget.ScrollView>(net.alphatab.R.id.innerScroll)
        val value = listOf(horizontal?.scrollX ?: 0, vertical?.scrollY ?: 0,
            vertical?.width ?: 0, vertical?.height ?: 0)
        if (value[2] <= 0 || value[3] <= 0) return
        if (!force && value == viewport) return
        viewport = value
        native.findViewById<View>(net.alphatab.R.id.renderSurface)?.let { surface ->
            surface.forceLayout()
            surface.requestLayout()
            surface.postInvalidateOnAnimation()
        }
    }
    private val settledRedraw = Runnable {
        val shouldRender = redrawOnIdle
        redrawOnIdle = false
        // Even if the user returned to the previous scroll offset, the visible
        // chunk bitmap might already have been recycled.
        refreshViewport(force = true)
        if (shouldRender && isAttachedToWindow && isShown && native.api.score != null) {
            // Redraw the existing score, never reassign tracks or reload MIDI/audio.
            native.api.render(null)
        }
    }
    private fun scheduleSettledRedraw() {
        if (!refreshViewport) return
        redrawOnIdle = true
        removeCallbacks(settledRedraw)
        postDelayed(settledRedraw, 160L)
    }
    private val viewportObserver = android.view.ViewTreeObserver.OnScrollChangedListener {
        scheduleViewportRefresh()
        // An alphaTab player-follow scroll still gets the cheap viewport sync.
        // Only a user drag/fling or a restored window requires the full redraw.
        if (touchGate.dragged || redrawOnIdle) scheduleSettledRedraw()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (refreshViewport) viewTreeObserver.addOnScrollChangedListener(viewportObserver)
        scheduleViewportRefresh(force = true)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scheduleViewportRefresh(force = true)
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) {
            scheduleViewportRefresh(force = true)
            scheduleSettledRedraw()
        } else {
            viewport = null
            redrawOnIdle = false
            removeCallbacks(settledRedraw)
        }
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            scheduleViewportRefresh(force = true)
            scheduleSettledRedraw()
        }
    }
    override fun onDetachedFromWindow() {
        if (refreshViewport) viewTreeObserver.removeOnScrollChangedListener(viewportObserver)
        removeCallbacks(viewportFrame)
        removeCallbacks(settledRedraw)
        framePending = false
        forceFrame = false
        redrawOnIdle = false
        viewport = null
        super.onDetachedFromWindow()
    }
    var inputEnabled: Boolean
        get() = touchGate.enabled
        set(value) { touchGate.enabled = value }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (isAttachedToWindow && !hasWindowFocus() || !touchGate.observe(event)) return true
        if (event.actionMasked == MotionEvent.ACTION_UP && touchGate.dragged) scheduleSettledRedraw()
        return super.dispatchTouchEvent(event)
    }
    fun consumeTap(): Boolean = touchGate.consumeTap()
}

/** Platform-independent gesture state; disabling it also cancels the native child gesture. */
internal class ScoreTouchGate(private val slop: Int, private val cancel: () -> Unit) {
    private var x = 0f; private var y = 0f; private var down = 0L
    private var moved = false; private var tapped = false
    var enabled = true
        set(value) {
            if (field && !value) { cancel(); tapped = false; moved = true }
            field = value
        }
    fun observe(event: MotionEvent): Boolean {
        if (!enabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x = event.x; y = event.y; down = event.eventTime; moved = false; tapped = false }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> { moved = true; tapped = false }
            MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - x) > slop || kotlin.math.abs(event.y - y) > slop) moved = true
            MotionEvent.ACTION_UP -> tapped = !moved && event.eventTime - down < ViewConfiguration.getLongPressTimeout()
        }
        return true
    }
    val dragged: Boolean get() = moved
    fun consumeTap(): Boolean = tapped.also { tapped = false }
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
fun NativeScore(player: AlphaPlayback, modifier: Modifier, visible: Boolean, inputEnabled: Boolean = true) {
    val colors = PracticeSkin.colors
    val applied = remember(player) { arrayOfNulls<ThemeTokens>(1) }
    AndroidView(factory = { context -> PracticeScoreHost(context).also { host ->
        host.native.barCursorFillColor = colors.notationHighlight.copy(alpha = .18f).toArgb()
        host.native.beatCursorFillColor = colors.accent.copy(alpha = .35f).toArgb()
        host.native.selectionFillColor = colors.primaryContainer.copy(alpha = .6f).toArgb()
        player.attach(host.native,host::consumeTap)
    } },
        modifier = modifier, update = {
            it.inputEnabled = inputEnabled && visible
            it.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            if (applied[0] != colors) {
                applyScoreSkin(it.native, colors)
                applied[0] = colors
            }
        },
        onRelease = { player.detach(it.native) })
}
