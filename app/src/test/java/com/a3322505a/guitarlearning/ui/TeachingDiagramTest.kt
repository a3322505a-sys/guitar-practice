package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test

class TeachingDiagramTest {
    private val f = Chord("F", 5, 9, listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1))

    @Test fun fullPartialAndHighBarresUseRecordedFingersAndActualStrings() {
        assertEquals(listOf(BarreSegment(1, 1, 6, 1)), deriveBarreSegments(f))
        val bm = f.copy(name = "Bm", frets = listOf(null, 2, 4, 4, 3, 2), fingers = listOf(0, 1, 3, 4, 2, 1))
        assertEquals(listOf(BarreSegment(1, 2, 5, 1)), deriveBarreSegments(bm))
        assertEquals(listOf(BarreSegment(1, 1, 2, 1)), deriveBarreSegments(f.copy(frets = listOf(null, null, 3, 2, 1, 1), fingers = listOf(0, 0, 3, 2, 1, 1))))
        val high = f.copy(frets = f.frets.map { it!! + 12 })
        assertEquals(listOf(BarreSegment(1, 13, 6, 1)), deriveBarreSegments(high))
        assertEquals(13, high.baseFret)
    }

    @Test fun ambiguousMissingOrConflictingFingersNeverInventBarres() {
        listOf(
            f.copy(fingers = listOf(1, 3, 4, 2, 2, 4)),
            f.copy(frets = listOf(1, 0, 3, 2, 1, 1)),
            f.copy(frets = listOf(1, null, 3, 2, 1, 1)),
            f.copy(frets = listOf(1, 3, 3, 2, 0, 0), fingers = listOf(1, 3, 4, 2, 0, 0)),
            f.copy(fingers = List(6) { 0 }), f.copy(fingers = emptyList()), f.copy(fingers = listOf(1, 3, 0, 2, 1, 1)),
            f.copy(frets = listOf(1, 3, 3, 2, 1, 2)),
            Chords.named("C").copy(name = "F"), Chords.named("Bm7(no5)"), Chords.named("D/F♯")
        ).forEach { assertTrue(it.toString(), deriveBarreSegments(it).isEmpty()) }
    }

    @Test fun ordinaryMelodyAndSameBoxJumpsHaveNoTechniqueConnections() {
        val route = listOf(7, 5, 4, 5, 4).map { Position(3, it) }
        val sequence = Sequence(route.mapIndexed { i, p -> Event(i * 12, 12, listOf(p)) }, 96, "plain")
        val index = LessonBoardIndex(sequence.performance)
        route.indices.forEach { i ->
            assertEquals(listOf(route[i]), index.targetAt(i.toDouble())!!.positions)
            assertTrue(index.techniquesAt(i + .5).isEmpty())
        }
        val cross = sequence.copy(events = listOf(Event(0, 12, listOf(Position(6, 2))), Event(12, 12, listOf(Position(1, 8)))))
        assertTrue(LessonBoardIndex(cross.performance).techniquesAt(.5).isEmpty())
    }

    @Test fun explicitRelationsKeepTechniqueKindAndEndpointsUntilTargetOnset() {
        listOf("SL01" to listOf("/", "\\"), "H01" to listOf("h", "p"), "P01" to listOf("p", "h")).forEach { (id, marks) ->
            val sequence = Phrases.named(id).sequence
            val index = LessonBoardIndex(sequence.performance)
            sequence.links.forEachIndexed { i, link ->
                val move = index.techniquesAt((link.sourceTick + 6.0) / QUARTER).single()
                assertEquals(marks[i], move.mark)
                assertEquals(sequence.position(link.sourceTick, 3), move.source)
                assertEquals(sequence.position(link.targetTick, 3), move.target)
                assertTrue(index.techniquesAt(link.targetTick.toDouble() / QUARTER).isEmpty())
            }
        }
    }
}
