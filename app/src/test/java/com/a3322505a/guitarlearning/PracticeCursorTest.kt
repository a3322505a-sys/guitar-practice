@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning

import alphaTab.midi.MidiTickLookupFindBeatResultCursorMode
import alphaTab.model.Beat
import alphaTab.platform.IContainer
import alphaTab.platform.Cursors
import alphaTab.rendering.utils.*
import com.a3322505a.guitarlearning.alphatab.PracticeCursorHandler
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PracticeCursorTest {
    private class Container {
        var bounds = emptyList<Double>()
        var transitions = mutableListOf<Pair<Double, Double>>()
        var stops = 0
        val value = Proxy.newProxyInstance(IContainer::class.java.classLoader,
            arrayOf(IContainer::class.java)) { _, method, args ->
            when (method.name) {
                "setBounds" -> bounds = args!!.map { it as Double }
                "transitionToX" -> transitions += (args!![0] as Double) to (args[1] as Double)
                "stopAnimation" -> stops++
            }
            null
        } as IContainer
    }
    private fun beat(x: Double, y: Double) = BeatBounds().apply {
        beat = Beat()
        onNotesX = x
        barBounds = BarBounds().apply {
            masterBarBounds = MasterBarBounds().apply { visualBounds = Bounds(x - 40, y, 300.0, 90.0) }
        }
    }

    @Test fun countInHoldsSelectedLaterBeatAndScrollsOnceDespiteStaleCursorUpdates() {
        var scrolls = 0
        val handler = PracticeCursorHandler { scrolls++ }
        val cursor = Container()
        val start = beat(420.0, 200.0)
        handler.prepare(true, start.beat)
        handler.placeBeatCursor(cursor.value, beat(50.0, 0.0), 90.0)
        assertTrue(cursor.bounds.isEmpty())
        handler.placeBeatCursor(cursor.value, start, 540.0)
        handler.transitionBeatCursor(cursor.value, start, 540.0, 600.0, 500.0,
            MidiTickLookupFindBeatResultCursorMode.ToNextBext)
        assertEquals(listOf(414.0, 200.0, 12.0, 90.0), cursor.bounds)
        assertTrue(cursor.transitions.isEmpty())
        assertEquals(1, scrolls)
    }

    @Test fun playbackAndLoopLineChangesUseOnlyEngineCoordinatesAndDurations() {
        val handler = PracticeCursorHandler {}
        val cursor = Container()
        val start = beat(420.0, 200.0)
        handler.prepare(true, start.beat)
        handler.placeBeatCursor(cursor.value, start, 420.0)
        handler.follow()
        handler.transitionBeatCursor(cursor.value, start, 445.0, 550.0, 375.0,
            MidiTickLookupFindBeatResultCursorMode.ToNextBext)
        assertEquals(listOf(439.0, 200.0, 12.0, 90.0), cursor.bounds)
        assertEquals(375.0 to 544.0, cursor.transitions.last())
        repeat(2) {
            val nextLine = beat(70.0, 350.0)
            handler.placeBeatCursor(cursor.value, nextLine, 70.0)
            assertEquals(listOf(64.0, 350.0, 12.0, 90.0), cursor.bounds)
            handler.placeBeatCursor(cursor.value, start, 420.0)
            assertEquals(listOf(414.0, 200.0, 12.0, 90.0), cursor.bounds)
        }
        val stops = cursor.stops
        handler.stop()
        assertEquals(stops + 1, cursor.stops)
    }
    @Test fun compensatedFrameOwnsBothCursorsAcrossLineChangesUntilPausedOrStopped() {
        val handler=PracticeCursorHandler {}
        val cursor=Container(); val bar=Container()
        handler.onAttach(Cursors(cursor.value,bar.value,cursor.value,cursor.value))
        val first=beat(420.0,200.0); val next=beat(70.0,350.0)
        handler.display(next,80.0)
        handler.placeBeatCursor(cursor.value,first,420.0)
        handler.placeBarCursor(bar.value,first)
        handler.transitionBeatCursor(cursor.value,first,420.0,500.0,100.0,
            MidiTickLookupFindBeatResultCursorMode.ToNextBext)
        assertEquals(listOf(74.0,350.0,12.0,90.0),cursor.bounds)
        assertEquals(listOf(30.0,350.0,300.0,90.0),bar.bounds)
        assertTrue(cursor.transitions.isEmpty())
        handler.releaseDisplay()
        handler.placeBeatCursor(cursor.value,first,420.0)
        assertEquals(listOf(414.0,200.0,12.0,90.0),cursor.bounds)
        handler.onDetach(Cursors(cursor.value,bar.value,cursor.value,cursor.value))
        assertFalse(handler.displaying)
    }
}
