package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test

class PhraseRhythmCompatibilityTest {
    private fun compare(sequence: Sequence) {
        val before=runCatching { sequence.tabBars() }
        val after=runCatching { sequence.validateTabRhythm() }
        assertEquals(sequence.toString(),before.isSuccess,after.isSuccess)
        assertEquals(before.exceptionOrNull()?.javaClass,after.exceptionOrNull()?.javaClass)
        assertEquals(before.exceptionOrNull()?.message,after.exceptionOrNull()?.message)
    }

    @Test fun sourceValidationKeepsAcceptedAndRejectedSubdivisionsAndBarBoundaries() {
        for(quarter in listOf(12,24)) for(beats in 2..6) {
            val length=quarter*beats*2
            for(start in listOf(0,1,2,3,4,6,8,9,12,18,24,30,36,47)) {
                for(duration in listOf(1,2,3,4,6,9,12,18,24,36,48,72)) {
                    if(start+duration<=length) compare(Sequence(
                        listOf(Event(start,duration,listOf(Position(6,0)))),length,"rhythm boundary",
                        ticksPerQuarter=quarter,beatsPerBar=beats))
                }
            }
        }
        compare(Sequence(listOf(Event(0,47,listOf(Position(6,0)))),47,"partial bar"))
        compare(Sequence(emptyList(),48,"empty source"))
    }

    @Test fun existingPhrasesAndExplicitTripletsRetainTheirValidation() {
        Phrases.all.forEach { compare(it.sequence) }
        for(pattern in 0..4) for(exercise in ScaleExercise.entries) {
            Routes.scale(ScaleOptions(pattern=pattern,exercise=exercise,notesPerBeat=3))
                .sequence?.let(::compare)
        }
    }
}
