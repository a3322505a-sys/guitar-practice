package com.a3322505a.guitarlearning
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
class PhraseRhythmCompatibilityTest {
    @Test fun independentAcceptedAndRejectedSubdivisionCorpusRetainsValidation() {
        for(duration in listOf(3,6,9,12,18,24,36,48,72,96)) {
            val length=if(duration>48) 96 else 48
            Sequence(listOf(Event(0,duration,listOf(Position(6,0)))),length,"accepted").validateTabRhythm()
        }
        for(duration in listOf(1,2,4,5,7,10,47)) assertTrue("duration=$duration",runCatching {
            Sequence(listOf(Event(0,duration,listOf(Position(6,0)))),48,"rejected").validateTabRhythm()
        }.isFailure)
        assertTrue(runCatching { Sequence(emptyList(),47,"partial").validateTabRhythm() }.isFailure)
    }
    @Test fun everyScaleRouteKeepsExplicitTripletsAndWholeBarBoundaries() {
        for(pattern in 0..4) for(exercise in ScaleExercise.entries) {
            Routes.scale(ScaleOptions(pattern=pattern,exercise=exercise,notesPerBeat=3)).sequence?.let { seq ->
                seq.validateTabRhythm()
                assertTrue(seq.tuplets.isNotEmpty())
                assertEquals(0,seq.length%48)
                assertEquals(seq.length,seq.events.sumOf { it.duration })
            }
        }
    }
}
