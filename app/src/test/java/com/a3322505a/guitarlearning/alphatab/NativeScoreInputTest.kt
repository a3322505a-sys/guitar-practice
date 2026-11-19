package com.a3322505a.guitarlearning.alphatab

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.RelativeLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NativeScoreInputTest {
    private class FakeRenderSurface(context: Context) : View(context) {
        var totalWidth = 0
        var totalHeight = 0
        var measureCount = 0
        var layoutCount = 0
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            measureCount++
            setMeasuredDimension(totalWidth, totalHeight)
        }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            layoutCount++
            super.onLayout(changed, left, top, right, bottom)
        }
    }

    @Test fun modalBlocksTapsAndCancelsAnInFlightGestureUntilANewDown() {
        var cancellations = 0
        val host = ScoreTouchGate(4) { cancellations++ }
        fun touch(action: Int) {
            val event = MotionEvent.obtain(0L, 20L, action, 20f, 20f, 0)
            host.observe(event); event.recycle()
        }
        host.enabled = false
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        assertFalse(host.consumeTap())
        host.enabled = true
        touch(MotionEvent.ACTION_DOWN)
        host.enabled = false; host.enabled = true
        touch(MotionEvent.ACTION_UP)
        assertFalse(host.consumeTap())
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        assertTrue(host.consumeTap())
        assertEquals(2, cancellations)
    }

    @Test fun viewportSyncRefreshesRenderSurfaceOnScrollAndVisibilityWithoutIdleFullRerender() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        lateinit var surface: FakeRenderSurface
        lateinit var vertical: ScrollView
        val host = PracticeScoreHost(activity) { ctx ->
            HorizontalScrollView(ctx).apply {
                id = net.alphatab.R.id.outerScroll
                vertical = ScrollView(ctx).apply {
                    id = net.alphatab.R.id.innerScroll
                    val wrapper = RelativeLayout(ctx).apply {
                        id = net.alphatab.R.id.renderWrapper
                        surface = FakeRenderSurface(ctx).apply {
                            id = net.alphatab.R.id.renderSurface
                        }
                        addView(surface, RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    }
                    addView(wrapper, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                addView(vertical, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
        activity.setContentView(host)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        host.measure(widthSpec, heightSpec)
        host.layout(0, 0, 400, 600)

        // Simulate AlphaTab addPlaceholder growing total size while host stays fixed at 400x600.
        surface.totalWidth = 400
        surface.totalHeight = 2000
        val measuresBefore = surface.measureCount
        val layoutsBefore = surface.layoutCount

        host.notifyScoreRendered()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))

        assertTrue("Expected surface measure on render finish", surface.measureCount > measuresBefore)
        assertTrue("Expected surface layout on render finish", surface.layoutCount > layoutsBefore)
        assertEquals("Fixed Compose host size must still remeasure renderSurface height", 2000, surface.height)
        assertFalse("renderSurface layout must have completed synchronously", surface.isLayoutRequested)

        // Downward scroll triggers surface measure + layout, and settling 250ms causes no extra redraw.
        val downMeasures = surface.measureCount
        val downLayouts = surface.layoutCount
        vertical.scrollTo(0, 1000)
        host.syncRenderSurfaceViewport(force = false)
        assertEquals(downMeasures + 1, surface.measureCount)
        assertEquals(downLayouts + 1, surface.layoutCount)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        assertEquals("Idle settling must not trigger extra measure/redraw", downMeasures + 1, surface.measureCount)
        assertEquals("Idle settling must not trigger extra layout/redraw", downLayouts + 1, surface.layoutCount)

        // Upward scroll back to 0 must still refresh surface measure + layout.
        val upMeasures = surface.measureCount
        val upLayouts = surface.layoutCount
        vertical.scrollTo(0, 0)
        host.syncRenderSurfaceViewport(force = false)
        assertEquals(upMeasures + 1, surface.measureCount)
        assertEquals(upLayouts + 1, surface.layoutCount)

        // INVISIBLE -> VISIBLE refreshes surface without idle full redraw.
        val visMeasures = surface.measureCount
        val visLayouts = surface.layoutCount
        host.visibility = View.INVISIBLE
        host.visibility = View.VISIBLE
        host.notifyBecameVisible()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        assertTrue("Becoming visible must measure renderSurface", surface.measureCount > visMeasures)
        assertTrue("Becoming visible must layout renderSurface", surface.layoutCount > visLayouts)

        // resetScrollPosition resets vertical scroll back to top.
        vertical.scrollTo(0, 900)
        host.resetScrollPosition()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        assertEquals(0, vertical.scrollY)

        controller.pause().stop().destroy()
    }
}
