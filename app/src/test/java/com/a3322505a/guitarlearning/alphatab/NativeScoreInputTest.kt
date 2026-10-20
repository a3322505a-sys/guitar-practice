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
        val host = PracticeScoreHost(RuntimeEnvironment.getApplication())
        fun touch(action: Int) {
            val event = MotionEvent.obtain(0L, 20L, action, 20f, 20f, 0)
            host.dispatchTouchEvent(event); event.recycle()
        }
        host.inputEnabled = false
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        assertFalse(host.consumeTap())
        host.inputEnabled = true
        touch(MotionEvent.ACTION_DOWN)
        host.inputEnabled = false; host.inputEnabled = true
        touch(MotionEvent.ACTION_UP)
        assertFalse(host.consumeTap())
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        assertTrue(host.consumeTap())
    }
}
