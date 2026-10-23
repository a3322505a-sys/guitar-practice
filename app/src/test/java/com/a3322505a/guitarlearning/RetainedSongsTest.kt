package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.assets.song.SongReader
import com.a3322505a.guitarlearning.assets.song.SongSchema
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.music.score.ScoreEvent
import com.a3322505a.guitarlearning.practice.compile.PracticeCompiler
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RetainedSongsTest {
    private val reader = SongReader(SongSchema { File("../contracts/song-package/v1", it).readBytes() })
    private fun song(file: String) = reader.read(File("src/main/assets/song-packages", file).readBytes())
    private fun positions(e: ScoreEvent) = e.notes.map { Triple(it.string, it.fret, it.midi) }

    @Test fun onlySelectedDifficultPassagesHaveIsolatedVariants() {
        for ((file, expected) in listOf(
            "shayou-v4.gpractice" to setOf("SHA-RIFF", "SHA-VERSE", "SHA-CHORUS"),
            "your-trip-v3.gpractice" to setOf("YT-GROOVE", "YT-LEGATO", "YT-RIFF", "YT-END"))) {
            val song = song(file)
            val easy = song.practices.filter { it.variantOf != null }
            assertEquals(expected, easy.map { it.variantOf }.toSet())
            assertFalse(song.practices.any { it.id == "SHA-INTRO-easy" })
            val ordered = song.practices.sortedBy { it.order }
            easy.forEach { practice ->
                val source = song.practices.single { it.id == practice.variantOf }
                val plan = PracticeCompiler.compile(song.score, practice)
                val original = PracticeCompiler.compile(song.score, source)
                assertEquals(ordered.indexOf(source) + 1, ordered.indexOf(practice))
                assertEquals(original.length, plan.length)
                assertEquals(original.visits.map { it.measure.id }, plan.visits.map { it.measure.id })
                assertEquals(listOf("easy-guitar"), practice.mixTrackIds)
                assertFalse(source.mixTrackIds.contains("easy-guitar"))
                plan.notes.forEach { assertEquals(it.track.midi(it.note.string, it.note.fret), it.note.midi) }
            }
        }
    }

    @Test fun verseRetainsAllChordHeadsAndReplacesOnlyPullsAndTheTripletFill() {
        val song = song("shayou-v4.gpractice")
        val source = song.score.voices.first { it.id != "easy-voice" }.events.filter { it.id.startsWith("SHA-VERSE-") }
        val easy = song.score.voices.single { it.id == "easy-voice" }.events.filter { it.id.startsWith("easy-SHA-VERSE-") }
        val byid = easy.associateBy { it.id }
        source.filter { it.notes.size > 1 }.forEach { chord ->
            val derived = byid.getValue("easy-" + chord.id)
            assertEquals(positions(chord), positions(derived))
            assertEquals(chord.duration, derived.duration)
            assertEquals(chord.onset, derived.onset)
            assertEquals(chord.notes.map { it.articulation }, derived.notes.map { it.articulation })
        }
        assertEquals(source.size - 2, easy.size)
        assertEquals(source.filter { it.notes.isEmpty() }.map { it.measureId to it.onset },
            easy.filter { it.notes.isEmpty() }.map { it.measureId to it.onset })
        assertFalse(easy.any { it.written.tupletGroupIds.isNotEmpty() })
        assertEquals(Q(1, 2), byid.getValue("easy-SHA-VERSE-e-174-0").duration)
        assertFalse(song.score.relations.any { it.kind == "pull" && it.fromNoteId.startsWith("easy-SHA-VERSE") })
    }

    @Test fun basicRhythmsAreEightSoundingEighthsAndNeverCollapseDyadsOrOctaves() {
        val selections = listOf("shayou-v4.gpractice" to listOf("SHA-CHORUS"),
            "your-trip-v3.gpractice" to listOf("YT-GROOVE", "YT-RIFF", "YT-END"))
        for ((file, ids) in selections) {
            val song = song(file)
            for (id in ids) {
                val practice = song.practices.single { it.id == id + "-easy" }
                val plan = PracticeCompiler.compile(song.score, practice)
                assertEquals(plan.visits.size * 8, plan.events.size)
                plan.visits.forEachIndexed { index, _ ->
                    val events = plan.events.filter { it.visit == index }
                    assertEquals(8, events.size)
                    assertEquals((0..7).map { Q(it.toLong(), 2) }, events.map { it.event.onset })
                    events.forEach { e ->
                        assertEquals(Q(1, 2), e.duration)
                        assertEquals(8, e.event.written.baseDenominator)
                        assertTrue(e.event.notes.isNotEmpty())
                        assertFalse(e.event.notes.any { it.dead })
                    }
                    if (id == "SHA-CHORUS") assertTrue(events.all { it.event.notes.size == 2 })
                }
            }
        }
    }

    @Test fun legatoSimplificationPreservesTheFullEndingChord() {
        val song = song("your-trip-v3.gpractice")
        val source = song.score.voices.first { it.id != "easy-voice" }.events.filter { it.id.startsWith("YT-LEGATO-") }
        val simple = song.score.voices.single { it.id == "easy-voice" }.events.filter { it.id.startsWith("easy-YT-LEGATO-") }
        assertEquals(source.map(::positions), simple.map(::positions))
        assertTrue(simple.any { it.notes.size >= 3 })
    }
}
