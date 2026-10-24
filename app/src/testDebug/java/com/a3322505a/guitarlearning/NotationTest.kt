@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
package com.a3322505a.guitarlearning
import alphaTab.midi.*
import alphaTab.model.*
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class NotationTest {
    @Test fun tripletTailRestsAndNativeBeatIdsPreserveExactWrittenTime() {
        val seq=NotationFixtures.triplets
        val score=NativeContent.score(seq)
        val voice=score.tracks.first().staves.first().bars.first().voices.first()
        assertEquals(1920.0,voice.beats.sumOf { it.playbackDuration },1e-6)
        assertEquals(seq.events.count { it.rest },voice.beats.count { it.isRest })
        assertEquals(seq.events.map { it.tick*80.0 },voice.beats.map { it.displayStart })
    }
    @Test fun samePitchOnDifferentStringsAndChordTonesRetainTheirNativePositions() {
        val seq=Sequence(listOf(Event(0,12,listOf(Position(2,0))),Event(12,12,listOf(Position(3,4))),
            Event(24,24,listOf(Position(5,3),Position(3,0),Position(1,0)),strum=true)),48,"same pitch")
        val score=NativeContent.score(seq)
        val beats=score.tracks.first().staves.first().bars.first().voices.first().beats.toList()
        assertEquals(59.0,beats[0].notes.first().realValue,0.0)
        assertEquals(59.0,beats[1].notes.first().realValue,0.0)
        assertNotEquals(beats[0].notes.first().string,beats[1].notes.first().string)
        assertEquals(3,beats[2].notes.length.toInt())
    }
    @Test fun crossBarSustainHasOneMidiAttackAndNativeTieDestinations() {
        val seq=Sequence(listOf(Event(0,12,emptyList()),Event(12,72,listOf(Position(5,3))),Event(84,12,emptyList())),96,"tie")
        val score=NativeContent.score(seq)
        val notes=score.tracks.first().staves.first().bars.flatMap { it.voices.toList() }.flatMap { it.beats.toList() }.flatMap { it.notes.toList() }
        assertTrue(notes.any { it.isTieDestination })
        val midi=EngineProjection.generate(score).first
        assertEquals(1,midi.events.filterIsInstance<NoteOnEvent>().size)
        assertEquals(960.0,midi.events.filterIsInstance<NoteOnEvent>().single().tick,0.0)
    }
    @Test fun currentScaleAndChordPracticesHaveCompleteNativeBars() {
        val sequences=ScaleExercise.entries.mapNotNull { Routes.scale(ScaleOptions(exercise=it,notesPerBeat=3)).sequence }+
            ChordExercise.entries.map { chordSequence(com.a3322505a.guitarlearning.music.Chords.named("C"),com.a3322505a.guitarlearning.music.Chords.named("Dm"),it,4) }
        sequences.forEach { seq ->
            val score=NativeContent.score(seq)
            assertEquals(seq.barCount,score.masterBars.length.toInt())
            assertEquals(seq.length*80.0,EngineProjection.generate(score).second.visits.last().end,1e-6)
        }
    }
}
