package com.a3322505a.guitarlearning.alphatab

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NativeScoreInputTest {
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
}
