package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test

class NotationTest {
    @Test fun samePitchKeepsItsStringAndChordGrouping() {
        val a=Position(2,0); val b=Position(3,4)
        assertEquals(59,a.midi); assertEquals(a.midi,b.midi)
        val seq=Sequence(listOf(Event(0,12,listOf(a)),Event(12,12,listOf(b)),Event(24,24,Chords.named("C").positions,"C",true)),48,"")
        val s=seq.tabBars().single().segments
        assertEquals(listOf(a),s[0].positions); assertEquals(listOf(b),s[1].positions)
        assertEquals(Chords.named("C").positions,s[2].positions)
        assertEquals(24,s[2].eventId); assertTrue(s[2].strum)
        assertEquals(RhythmValue.HALF,s[2].value)
    }
    @Test fun offBeatAndAcrossBarSustainHasTiesAndNoNewOnsets() {
        val note=Position(5,3)
        val seq=Sequence(listOf(Event(0,12,emptyList()),Event(12,72,listOf(note),"C",true),Event(84,12,emptyList())),96,"")
        val segments=seq.tabBars().flatMap { it.segments }
        assertEquals(listOf(12,24,48,72),segments.filter { !it.rest }.map { it.tick })
        assertEquals(listOf(RhythmValue.QUARTER,RhythmValue.HALF,RhythmValue.HALF,RhythmValue.QUARTER),segments.filter { !it.rest }.map { it.value })
        assertEquals(1,segments.count { it.strum })
        assertTrue(segments.filter { !it.rest }.all { it.eventId==12 && it.positions==listOf(note) })
        assertFalse(segments[1].tieIn); assertTrue(segments[1].tieOut)
        assertTrue(segments[2].tieIn); assertTrue(segments[3].tieIn)
        assertFalse(segments[4].tieOut)
        assertTrue(segments.first().rest); assertTrue(segments.last().rest)
    }
    @Test fun allExistingPracticesHaveExactNotationTimeAndNoImplicitMute() {
        val sequences=ScaleExercise.entries.mapNotNull { Routes.scale(ScaleOptions(exercise=it,octave=false,eighth=true)).sequence } +
            ChordExercise.entries.flatMap { kind -> listOf(1,2,4,8).map { chordSequence(Chords.named("C"),Chords.named("Dm"),kind,it) } }
        sequences.forEach { seq ->
            val bars=seq.tabBars()
            bars.forEach { assertEquals(48,it.segments.sumOf { s -> s.value.ticks }) }
            for(tick in 0 until seq.length) {
                val event=seq.eventAt(tick.toDouble())!!
                val segment=bars[tick/48].segments.single { tick>=it.tick && tick<it.end }
                assertEquals(event.id,segment.eventId); assertEquals(event.positions,segment.positions)
            }
        }
    }
    @Test fun layoutUsesReadableDynamicSystemsAndCursorKeepsLoopPosition() {
        val bars=Routes.scale(ScaleOptions(exercise=ScaleExercise.THREE,eighth=true)).sequence!!.tabBars()
        assertTrue(tabSystems(bars,360).all { it.size==1 })
        assertTrue(tabSystems(bars,1200).any { it.size>1 })
        val session=Session(Routes.scale(ScaleOptions()).sequence!!,60,true,4)
        assertTrue(session.cursor(30.0).pre)
        val c=session.cursor(210.0)
        assertEquals(1,c.loop); assertEquals(1,c.bar); assertEquals(1,c.beat)
        assertEquals(66.0,c.localTick,0.0)
        assertEquals(3,session.cursor(432.0).loop)
        assertEquals(1,session.cursor(432.0).bar)
    }
}
