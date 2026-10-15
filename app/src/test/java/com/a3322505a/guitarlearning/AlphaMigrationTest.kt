@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning

import alphaTab.exporter.Gp7Exporter
import alphaTab.midi.NoteOnEvent
import alphaTab.model.*
import alphaTab.collections.DoubleList
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AlphaMigrationTest {
    private fun audit(sequence: Sequence) {
        val source = sequence.performance
        val native = AlphaScoreAdapter.fromSequence(sequence)
        assertEquals(source.visits.size, native.masterBars.length.toInt())
        val tracks = source.score.tracks.filter { it.id in source.practice.mixTrackIds }
        source.events.forEach { event ->
            val track = native.tracks[tracks.indexOfFirst { it.id == event.track.id }]
            val staff = track.staves.first()
            val voice = source.score.voices.filter { it.trackId == event.track.id }.indexOfFirst { it.id == event.voiceId }
            val bar = staff.bars[event.visit]
            val onset = (maxOf(event.start, source.visits[event.visit].start) - source.visits[event.visit].start).value() * 960
            val notes = bar.voices[voice].beats.toList().filter { kotlin.math.abs(it.displayStart - onset) < 1e-5 && it.graceType == GraceType.None }
                .flatMap { it.notes.toList() }
            event.effectiveNotes.forEach { expected ->
                val matches = notes.filter { 7 - it.string.toInt() == expected.string && it.fret.toInt() == expected.fret && it.isDead == expected.dead }
                assertTrue("${source.practice.id}/${event.id}/${expected.id}: missing original string/fret", matches.isNotEmpty())
                if (!expected.dead && expected.harmonic == null) assertEquals(expected.midi.toDouble(), matches.first().realValue, 0.0)
            }
        }
        val (midi, projection) = EngineProjection.generate(native)
        assertTrue(midi.events.any { it is NoteOnEvent })
        assertEquals(source.visits.size, projection.visits.size)
        assertEquals(source.length.value() * 960, projection.visits.last().end, 1e-6)
    }
    @Test fun everyExistingScaleRouteAndChordRetainsPositionsRhythmAndPitch() {
        Tonality.entries.forEach { key -> Patterns.all.indices.forEach { pattern ->
            ScaleExercise.entries.forEach { exercise -> (1..3).forEach { rhythm ->
                listOf(true, false).forEach { octave ->
                    Routes.scale(ScaleOptions(pattern = pattern, key = key, exercise = exercise,
                        notesPerBeat = rhythm, octave = octave)).sequence?.let(::audit)
                }
            } }
        } }
        Chords.all.forEach { audit(chordSequence(it, it, ChordExercise.CHECK, 4)) }
        Phrases.all.forEach { audit(it.sequence) }
    }
    @Test fun everyInstalledPackagePracticeRemainsUsableWithoutOriginalGp() {
        val repository = SongTestLibrary.repository()
        val snapshot = repository.reload()
        val failures = mutableListOf<String>()
        snapshot.phrases.filter { it.assetKey != null }.forEach { phrase ->
            try { audit(repository.resolve(phrase.id).sequence) }
            catch (failure: Throwable) { failures += "${phrase.id}: ${failure.message}" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
    private fun polyphonic(): Score {
        val score = Score().apply { title = "原创事务与持续音测试" }
        score.addMasterBar(MasterBar())
        val track = Track().apply { name = "六弦吉他"; playbackInfo.program = 24.0 }
        score.addTrack(track)
        val staff = Staff().apply { stringTuning = Tuning("标准", DoubleList(64.0, 59.0, 55.0, 50.0, 45.0, 40.0)); capo = 2.0 }
        track.addStaff(staff)
        val bar = Bar(); staff.addBar(bar)
        val bass = Voice(); bar.addVoice(bass)
        bass.addBeat(Beat().apply { duration = Duration.Whole; addNote(Note().apply { string = 1.0; fret = 0.0 }) })
        val melody = Voice(); bar.addVoice(melody)
        melody.addBeat(Beat().apply { duration = Duration.Quarter; addNote(Note().apply { string = 6.0; fret = 3.0 }) })
        melody.addBeat(Beat().apply { duration = Duration.Half; dots = 1.0; isEmpty = false })
        score.finish(AlphaScoreAdapter.settings())
        return score
    }
    @Test fun engineNoteOffIntervalsKeepOtherVoicesSustainingThroughRests() {
        val (_, projection) = EngineProjection.generate(polyphonic())
        assertEquals(setOf(Position(6, 0), Position(1, 3)), projection.active(480.0, 0).toSet())
        assertEquals(listOf(Position(6, 0)), projection.active(1440.0, 0))
        assertTrue(projection.active(3840.0, 0).isEmpty())
    }
    @Test fun migratedLetRingKeepsItsAcceptedCutoffAndMidiReleasesItBeforeTheNextAttack() {
        val seq=Sequence(listOf(Event(0,12,listOf(Position(6,0)),ring=true),
            Event(12,12,listOf(Position(1,3))),Event(24,12,listOf(Position(6,2))),
            Event(36,12,emptyList())),48,"独立延响测试")
        val (_,projection)=EngineProjection.generate(AlphaScoreAdapter.fromSequence(seq))
        assertEquals(setOf(Position(6,0),Position(1,3)),projection.active(1440.0,0).toSet())
        assertEquals(listOf(Position(6,2)),projection.active(2400.0,0))
        assertTrue(projection.active(3000.0,0).isEmpty())
    }
    @Test fun auditionUsesTheSelectedTracksLoadedPresetAndUnmutedChannels() {
        val selected=polyphonic().tracks.first()
        selected.playbackInfo.program=27.0
        selected.playbackInfo.primaryChannel=6.0
        selected.playbackInfo.secondaryChannel=7.0
        val preview=Sequence(listOf(Event(0,12,listOf(Position(6,0)))),12,"点音",capoFret=2)
        val beat=AlphaScoreAdapter.audition(preview,selected)
        val midi=alphaTab.midi.MidiFile()
        alphaTab.midi.MidiFileGenerator(beat.voice.bar.staff.track.score,AlphaScoreAdapter.settings(),
            alphaTab.midi.AlphaSynthMidiFileHandler(midi)).generateSingleBeat(beat)
        val attacks=midi.events.filterIsInstance<NoteOnEvent>()
        assertTrue(attacks.isNotEmpty())
        assertTrue(attacks.all { it.channel==6.0 || it.channel==7.0 })
        assertTrue(attacks.all { it.noteKey==42.0 })
        val programs=midi.events.filterIsInstance<alphaTab.midi.ProgramChangeEvent>().filter { it.channel==6.0 || it.channel==7.0 }
        assertTrue(programs.isNotEmpty())
        assertTrue(programs.all { it.program==27.0 })
    }
    @Test fun gpCopiesReopenAfterRestartDuplicatesKeepPreferencesAndFailuresKeepExistingSongs() {
        val root = Files.createTempDirectory("gp-library-test").toFile()
        val bytes = Gp7Exporter().export(polyphonic()).buffer.toByteArray()
        val library = GpLibrary(root)
        val imported = library.import("author.gp", bytes.inputStream()).first
        val preferences = imported.preferences.copy(speed = 75, first = 0, last = 0, repeats = 3, tab = false, solo = false)
        library.save(imported.copy(preferences = preferences, opened = 1234))
        val restarted = GpLibrary(root)
        val reopened = restarted.open(imported.id)
        assertEquals(preferences, reopened.first.preferences)
        assertEquals(2.0, reopened.second.tracks.first().staves.first().capo, 0.0)
        assertEquals(preferences, restarted.import("another-name.gp", bytes.inputStream()).first.preferences)
        assertEquals(1, restarted.list().size)
        assertTrue(runCatching { restarted.import("broken.gp", byteArrayOf(1, 2, 3).inputStream()) }.isFailure)
        assertTrue(runCatching { restarted.import("unverified.gp3", bytes.inputStream()) }.isFailure)
        assertEquals(preferences, restarted.open(imported.id).first.preferences)
        assertArrayEquals(bytes, root.resolve("${imported.id}.score").readBytes())
        val unrelated = root.resolve("original-user-document").apply { writeText("keep") }
        restarted.delete(imported.id)
        assertTrue(restarted.list().isEmpty())
        assertEquals("keep", unrelated.readText())
    }
    @Test fun copiedFileCorruptionIsDetectedBeforePlaybackAndOtherRecordsSurvive() {
        val root = Files.createTempDirectory("gp-corruption-test").toFile()
        val bytes = Gp7Exporter().export(polyphonic()).buffer.toByteArray()
        val library = GpLibrary(root)
        val song = library.import("first.gp", bytes.inputStream()).first
        root.resolve("${song.id}.score").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(runCatching { library.open(song.id) }.exceptionOrNull()?.message.orEmpty().contains("校验失败"))
        assertEquals(1, library.list().size)
    }
}
