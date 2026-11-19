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
internal class PracticeScoreHost(
    context: Context,
    private val refreshViewport: Boolean = true,
    nativeFactory: ((Context) -> View)? = null,
) : FrameLayout(context) {
    private val nativeView: View = nativeFactory?.invoke(context) ?: AlphaTabView(context, null)
    val native: AlphaTabView get() = nativeView as AlphaTabView
    init { addView(nativeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
    // AlphaTab 1.8.4 measures its visible bitmap chunks on the render surface.
    // OnScrollChange has a bug comparing _bottomVisibleItem.top on upward scroll,
    // and Compose AndroidView fixed constraints swallow child requestLayout().
    // Coalesce viewport sync to one animation frame and drive measure/layout directly
    // on the render surface without ever clearing existing chunks via api.render(null).
    private var viewport: List<Int>? = null
    private var framePending = false
    private var forceFrame = false
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
    internal fun notifyScoreRendered() {
        scheduleViewportRefresh(force = true)
    }
    internal fun notifyBecameVisible() {
        scheduleViewportRefresh(force = true)
    }
    internal fun resetScrollPosition() {
        viewport = null
        nativeView.findViewById<android.widget.HorizontalScrollView>(net.alphatab.R.id.outerScroll)?.scrollTo(0, 0)
        nativeView.findViewById<android.widget.ScrollView>(net.alphatab.R.id.innerScroll)?.scrollTo(0, 0)
        scheduleViewportRefresh(force = true)
    }
    internal var viewportSyncs = 0; private set
    internal fun syncRenderSurfaceViewport(force: Boolean = false) = refreshViewport(force)
    private fun refreshViewport(force: Boolean = false) {
        if (!refreshViewport || !isAttachedToWindow || !isShown) {
            if (!isShown) viewport = null
            return
        }
        val hostW = width
        val hostH = height
        if (hostW <= 0 || hostH <= 0) return
        if (isInLayout) {
            scheduleViewportRefresh(force = true)
            return
        }
        val horizontal = nativeView.findViewById<android.widget.HorizontalScrollView>(net.alphatab.R.id.outerScroll)
        val vertical = nativeView.findViewById<android.widget.ScrollView>(net.alphatab.R.id.innerScroll)
        val surface = nativeView.findViewById<View>(net.alphatab.R.id.renderSurface) ?: return
        val state = listOf(
            horizontal?.scrollX ?: 0,
            vertical?.scrollY ?: 0,
            vertical?.width ?: hostW,
            vertical?.height ?: hostH,
            surface.measuredWidth,
            surface.measuredHeight,
        )
        if (!force && state == viewport) return
        viewport = state

        val prevW = surface.measuredWidth
        val prevH = surface.measuredHeight
        // Ensure onMeasure runs even if incoming MeasureSpecs match cached specs,
        // so AlphaTabRenderSurface sets _visibleRectDirty = true and PFLAG_LAYOUT_REQUIRED.
        surface.forceLayout()
        surface.measure(
            MeasureSpec.makeMeasureSpec(prevW.coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(prevH.coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
        val newW = surface.measuredWidth
        val newH = surface.measuredHeight
        if (newW != prevW || newH != prevH || surface.width != newW || surface.height != newH ||
            (vertical != null && (vertical.width <= 0 || vertical.height <= 0))
        ) {
            var ancestor: View? = surface
            while (ancestor != null) {
                ancestor.forceLayout()
                ancestor = if (ancestor === this) null else ancestor.parent as? View
            }
            measure(
                MeasureSpec.makeMeasureSpec(hostW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(hostH, MeasureSpec.EXACTLY),
            )
            layout(left, top, right, bottom)
            viewportSyncs++
        } else if (newW > 0 && newH > 0) {
            surface.layout(surface.left, surface.top, surface.right, surface.bottom)
            viewportSyncs++
        }
        surface.postInvalidateOnAnimation()
    }
    private val viewportObserver = android.view.ViewTreeObserver.OnScrollChangedListener {
        scheduleViewportRefresh()
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
        } else {
            viewport = null
        }
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            scheduleViewportRefresh(force = true)
        }
    }
    override fun onDetachedFromWindow() {
        if (refreshViewport) viewTreeObserver.removeOnScrollChangedListener(viewportObserver)
        removeCallbacks(viewportFrame)
        framePending = false
        forceFrame = false
        viewport = null
        super.onDetachedFromWindow()
    }
    var inputEnabled: Boolean
        get() = touchGate.enabled
        set(value) { touchGate.enabled = value }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (isAttachedToWindow && !hasWindowFocus() || !touchGate.observe(event)) return true
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
            val becomingVisible = visible && it.visibility != View.VISIBLE
            it.inputEnabled = inputEnabled && visible
            it.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            if (becomingVisible) it.notifyBecameVisible()
            if (applied[0] != colors) {
                applyScoreSkin(it.native, colors)
                applied[0] = colors
            }
        },
        onRelease = { player.detach(it.native) })
}
