package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test

class PhraseTest {
    @Test fun reducedChordGripsHaveCorrectTonesFingersAndRoles() {
        val expected=mapOf(
            "Gsus2" to listOf(43,50,57), "Asus4(no5)" to listOf(45,50,57),
            "Bm7(no5)" to listOf(47,50,57), "D/F♯" to listOf(42,50,57),
        )
        expected.forEach { (name,pitches) ->
            val chord=Chords.named(name)
            assertEquals(pitches,chord.positions.map { it.midi })
            chord.frets.forEachIndexed { index,fret ->
                assertEquals(fret!=null && fret>0,chord.fingers[index]>0)
                assertEquals(fret!=null,chord.positions.any { it.string==6-index })
            }
            val check=chordSequence(chord,chord,ChordExercise.CHECK,4)
            assertEquals(chord.positions,check.events.flatMap { it.positions })
            assertTrue(chord.arpeggio.all { it in chord.positions })
        }
        assertEquals("二音（挂留）",Chords.named("Gsus2").role(Position(3,2)))
        assertEquals("四音（挂留）",Chords.named("Asus4(no5)").role(Position(4,0)))
        assertEquals("小七音",Chords.named("Bm7(no5)").role(Position(3,2)))
        assertEquals("三音",Chords.named("Bm7(no5)").role(Position(4,0)))
        assertEquals("三音",Chords.named("D/F♯").role(Position(6,2)))
    }
    @Test fun phraseHasExactEighthNotesHarmonyAndNoCapoPitchShift() {
        val phrase=LegacyUsotsukiFixture.phrase
        val seq=phrase.sequence
        assertEquals("标准调弦 · 无变调夹",phrase.conditions)
        assertEquals(80,phrase.sourceBpm)
        assertEquals(96,seq.length)
        assertEquals(listOf(43,50,57,50,45,50,57,50,47,50,57,50,42,50,57,50),seq.events.flatMap { it.positions }.map { it.midi })
        assertEquals((0..15).map { it*6 },seq.events.map { it.tick })
        assertTrue(seq.events.all { it.duration==6 && it.ring && !it.strum })
        assertEquals(listOf(0,24,48,72),seq.sections.map { it.tick })
        seq.events.forEach { event ->
            val chord=Chords.named(seq.sections[seq.sectionAt(event.tick.toDouble())].chord)
            assertEquals(chord.name,event.chord)
            assertTrue(event.positions.all { it in chord.positions })
        }
        assertEquals(2,seq.performance.visits.size)
        assertEquals(16,seq.performance.events.size)
        assertTrue(seq.performance.events.all { it.duration.value()==.5 })
        assertEquals("D/F♯",seq.sections[seq.sectionAt(95.9)].chord)
        assertEquals(8.0,seq.performance.length.value(),0.0)
    }
}
