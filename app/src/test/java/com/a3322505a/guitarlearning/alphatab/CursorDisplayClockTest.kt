@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.midi.MidiFile
import alphaTab.midi.TempoChangeEvent
import org.junit.Assert.*
import org.junit.Test

class CursorDisplayClockTest {
    private fun map() = CursorTempoMap(MidiFile().apply {
        addEvent(TempoChangeEvent(0.0, 500000.0)) // 120 BPM
        addEvent(TempoChangeEvent(1920.0, 1000000.0)) // 60 BPM after two quarters
    })
    @Test fun offsetUsesRealMillisecondsAtEveryPlaybackSpeedWithoutDrift() {
        for (speed in listOf(.125, 1.0, 2.0)) {
            val clock = CursorDisplayClock(map(), 0.0, 5760.0)
            clock.sample(960.0, 1000, speed)
            assertEquals(960.0, clock.tickAt(1000, 0, false), 1e-6)
            assertEquals(960.0 - 192 * speed, clock.tickAt(1000, 100, false), 1e-6)
            assertEquals(960.0 + 192 * speed, clock.tickAt(1000, -100, false), 1e-6)
            // Offset edits recalculate from the same heard sample, never accumulate.
            repeat(50) { clock.tickAt(1000, 500, false); clock.tickAt(1000, -500, false) }
            assertEquals(960.0, clock.tickAt(1000, 0, false), 1e-6)
        }
    }
    @Test fun advancingAndDelayingAcrossTempoChangesIntegrateTheGeneratedMap() {
        val clock = CursorDisplayClock(map(), 0.0, 5760.0)
        clock.sample(1824.0, 1000, 1.0) // 950 ms source time
        assertEquals(1968.0, clock.tickAt(1000, -100, false), 1e-6) // 1050 ms
        clock.sample(1968.0, 1100, 1.0)
        assertEquals(1824.0, clock.tickAt(1100, 100, false), 1e-6)
    }
    @Test fun delaySurvivesLoopBoundaryAndAdvanceWrapsToFirstBeat() {
        val clock = CursorDisplayClock(map(), 960.0, 1920.0)
        clock.sample(1824.0, 1000, 1.0)
        assertEquals(1056.0, clock.tickAt(1000, -100, true), 1e-6)
        clock.sample(1056.0, 1100, 1.0)
        assertEquals(1824.0, clock.tickAt(1100, 100, true), 1e-6)
        assertEquals(1248.0, clock.tickAt(1200, 0, true), 1e-6)
    }
    @Test fun delayedPlayerSamplesNeverSnapTheDisplayBackwards() {
        val clock = CursorDisplayClock(map(), 0.0, 5760.0)
        clock.sample(960.0, 1000, 1.0)
        val at1100 = clock.tickAt(1100, 0, false)
        // A late/stale SDK position would have reset the old clock's anchor.
        clock.sample(1000.0, 1100, 1.0)
        assertEquals(at1100, clock.tickAt(1100, 0, false), 1e-6)
        assertTrue(clock.tickAt(1116, 0, false) > at1100)
        clock.sample(999.0, 1116, 1.0) // small backwards sample is NOT a loop
        assertTrue(clock.tickAt(1132, 0, false) > clock.tickAt(1116, 0, false))
    }

    @Test fun irregularCallbackIntervalsKeepContinuousMonotonicMovement() {
        val clock = CursorDisplayClock(map(), 0.0, 5760.0)
        clock.sample(960.0, 1000, 1.0)
        var previous = clock.tickAt(1000, 0, false)
        for (elapsed in listOf(17L, 48L, 90L, 117L, 153L, 202L, 268L)) {
            val wall = 1000 + elapsed
            // Alternating late/early snapshots must not make the display snap.
            val behind = if (elapsed % 2 == 0L) 50.0 else 15.0
            clock.sample(960.0 + elapsed * 1.92 - behind, wall, 1.0)
            val now = clock.tickAt(wall, 0, false)
            assertTrue("Cursor regressed at $elapsed ms: $now < $previous", now >= previous)
            previous = now
        }
    }

    @Test fun firstPassAndFinalPassClampAndNewClockDropsOldLoopHistory() {
        val clock = CursorDisplayClock(map(), 960.0, 1920.0)
        clock.sample(960.0, 0, 1.0)
        assertEquals(960.0, clock.tickAt(0, 500, true), 1e-6)
        clock.sample(1824.0, 1000, 1.0)
        assertEquals(1920.0, clock.tickAt(1000, -500, false), 1e-5)
        val resumed = CursorDisplayClock(map(), 960.0, 1920.0)
        resumed.sample(1056.0, 2000, 1.0)
        assertEquals(960.0, resumed.tickAt(2000, 500, false), 1e-6)
    }
}
