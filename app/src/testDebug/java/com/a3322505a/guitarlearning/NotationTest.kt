package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.tabProjection
import com.a3322505a.guitarlearning.ui.tabLayoutSystems
import org.junit.Assert.*
import org.junit.Test

class NotationTest {
    @Test fun scaleTripletsKeepExplicitTailRestsAndCursorEventIdentity() {
        listOf(ScaleOptions(exercise=ScaleExercise.THIRDS),ScaleOptions(exercise=ScaleExercise.ROOTS),ScaleOptions(pattern=1,octave=false)).forEach { o ->
            val seq=Routes.scale(o.copy(notesPerBeat=3)).sequence!!
            val bars=seq.tabBars()
            val segments=bars.flatMap { it.segments }
            assertTrue(bars.all { it.segments.sumOf { s -> s.value.ticks }==48 })
            assertEquals(seq.tuplets,segments.filter { s -> s.tuplet?.tick==s.tick }.map { it.tuplet })
            seq.tuplets.forEach { group ->
                val members=segments.filter { it.tuplet==group }
                assertEquals(3,members.size)
                assertTrue(members.all { it.value==RhythmValue.TRIPLET_EIGHTH })
                assertEquals(seq.events.filter { it.tick in group.tick until group.end }.map { it.rest },members.map { it.rest })
            }
            val session=Session(seq,60,true,2)
            for(tick in 0 until seq.length) {
                val cursor=session.cursor((48+tick).toDouble())
                val segment=segments.single { tick>=it.tick && tick<it.end }
                assertEquals(seq.eventAt(cursor.localTick)!!.id,segment.eventId)
                assertEquals(seq.eventAt(cursor.localTick)!!.positions,segment.positions)
            }
        }
    }
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
        val sequences=ScaleExercise.entries.mapNotNull { Routes.scale(ScaleOptions(exercise=it,octave=false,notesPerBeat=2)).sequence } +
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
        val plan=Routes.scale(ScaleOptions(exercise=ScaleExercise.THREE,notesPerBeat=2)).sequence!!.performance
        fun rows(width: Float)=tabLayoutSystems(plan,plan.tabProjection(),width) { s,size -> s.length*size*.6f }
        assertTrue(rows(360f).all { it.bars.size==1 })
        assertTrue(rows(1200f).any { it.bars.size>1 })
        val session=Session(Routes.scale(ScaleOptions()).sequence!!,60,true,4)
        assertTrue(session.cursor(30.0).pre)
        val c=session.cursor(306.0)
        assertEquals(1,c.loop); assertEquals(1,c.bar); assertEquals(1,c.beat)
        assertEquals(66.0,c.localTick,0.0)
        assertEquals(3,session.cursor(672.0).loop)
        assertEquals(1,session.cursor(672.0).bar)
    }
}
