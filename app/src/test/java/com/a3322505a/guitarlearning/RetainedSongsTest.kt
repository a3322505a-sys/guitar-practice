package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.assets.song.SongReader
import com.a3322505a.guitarlearning.assets.song.SongSchema
import com.a3322505a.guitarlearning.music.score.Articulation
import com.a3322505a.guitarlearning.practice.compile.PracticeCompiler
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RetainedSongsTest {
    private val reader = SongReader(SongSchema { File("../contracts/song-package/v1", it).readBytes() })

    @Test fun simplifiedSegmentsUseTheirOwnSingleNoteTrackAndKeepOriginalTiming() {
        var variants = 0
        for ((file, expected) in listOf("shayou-v3.gpractice" to 5, "your-trip-v2.gpractice" to 6)) {
            val song = reader.read(File("src/main/assets/song-packages", file).readBytes())
            val easy = song.practices.filter { it.variantOf != null }
            assertEquals(expected, easy.size)
            val originalVoice = song.score.voices.first { it.id != "easy-voice" }
            val easyVoice = song.score.voices.first { it.id == "easy-voice" }
            assertEquals(originalVoice.events.size, easyVoice.events.size)
            originalVoice.events.zip(easyVoice.events).forEach { (source, simple) ->
                assertEquals(source.measureId, simple.measureId)
                assertEquals(source.onset, simple.onset)
                assertEquals(source.duration, simple.duration)
                assertTrue(simple.notes.size <= 1)
                val candidates = source.notes.filterNot { it.dead }
                if (candidates.isEmpty()) assertTrue(simple.notes.isEmpty())
                else assertEquals(candidates.maxOf { it.midi }, simple.notes.single().midi)
                assertEquals(Articulation(), simple.articulation)
                simple.notes.forEach { note ->
                    assertFalse(note.dead)
                    assertNull(note.harmonic)
                    assertEquals(Articulation(velocity = note.articulation.velocity, pick = note.articulation.pick), note.articulation)
                }
            }
            val ordered = song.practices.sortedBy { it.order }
            easy.forEach { practice ->
                val source = song.practices.single { it.id == practice.variantOf }
                assertEquals(ordered.indexOf(source) + 1, ordered.indexOf(practice))
                val plan = PracticeCompiler.compile(song.score, practice)
                val original = PracticeCompiler.compile(song.score, source)
                assertEquals(original.length, plan.length)
                assertEquals(original.visits.map { it.measure.id }, plan.visits.map { it.measure.id })
                assertEquals(listOf("easy-guitar"), practice.mixTrackIds)
                assertFalse(source.mixTrackIds.contains("easy-guitar"))
                assertTrue(plan.notes.isNotEmpty())
                plan.notes.forEach { assertEquals(it.track.midi(it.note.string, it.note.fret), it.note.midi) }
                assertTrue(plan.relations.all { it.kind == "tie" })
            }
            variants += easy.size
        }
        assertEquals(11, variants)
    }
}
