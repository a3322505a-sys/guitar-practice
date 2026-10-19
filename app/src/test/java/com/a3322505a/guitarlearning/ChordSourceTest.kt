@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.alphatab.AlphaScoreAdapter
import com.a3322505a.guitarlearning.alphatab.EngineProjection
import com.a3322505a.guitarlearning.music.score.GuitarVoicing
import com.a3322505a.guitarlearning.music.score.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ChordSourceTest {
    @Test fun selectedPackageChordResolvesWithoutFoundationNameLookup() {
        val repository=SongTestLibrary.repository()
        val chord=repository.reload().chords.first { it.group.startsWith("斜阳") }
        val sequence=chordSequence(chord,chord,ChordExercise.CHECK,4)
        assertSame(chord,sequence.chordAt(sequence.sections.single()))
    }

    @Test fun selectedPackageChordCompilesWithoutFoundationNameLookup() {
        val repository=SongTestLibrary.repository()
        val chord=repository.reload().chords.first { it.group.startsWith("斜阳") }
        val sequence=chordSequence(chord,chord,ChordExercise.CHECK,4)
        assertEquals(chord.positions.map(chord::midi),sequence.performance.notes.map { it.note.midi })
    }

    @Test fun allLoadedCardsKeepGripIdentityTeachingAndSoundAcrossExercises() {
        val chords=SongTestLibrary.repository().reload().chords
        assertEquals(50,chords.size)
        assertEquals(16,chords.count { it.group.startsWith("斜阳") })
        assertEquals(4,chords.count { it.group=="本地曲库 · 嘘月" })
        assertTrue(chords.any { c -> c.positions.size==2 && c.baseFret>=10 })
        assertTrue(chords.any { c -> c.frets.any { it==null } && c.frets.any { it==0 } })
        chords.forEach { chord ->
            assertNull(chord.name,chord.unavailableReason)
            ChordExercise.entries.forEach { exercise ->
                val sequence=chordSequence(chord,chord,exercise,4)
                sequence.sections.forEach { assertSame(chord,sequence.chordAt(it)) }
                assertEquals(chord.openStringMidi,sequence.openStringMidi)
                assertEquals(chord.capoFret,sequence.capoFret)
                val plan=sequence.performance
                assertEquals(chord.openStringMidi,plan.mainTrack.openStringMidi)
                assertEquals(chord.capoFret,plan.mainTrack.capo)
                assertEquals(chord.id,plan.score.voicings.single().id)
                val projected=plan.score.voicings.single().chord(plan.mainTrack)
                assertEquals(chord.frets,projected.frets)
                assertEquals(chord.fingers,projected.fingers)
                assertEquals(chord.barres,projected.barres)
                assertEquals(chord.root,projected.root)
                assertEquals(chord.source,projected.source)
                assertEquals(chord.intervalRoles,projected.intervalRoles)
                assertEquals(chord.toneNames,projected.toneNames)
                assertTrue(plan.harmonies.all { it.voicing?.id==chord.id })
                assertTrue(plan.notes.all { n -> n.position in chord.positions && n.note.midi==chord.midi(n.position) })
                if(exercise==ChordExercise.CHECK) assertEquals(chord.positions,plan.notes.map { it.position })
                val (_,projection)=EngineProjection.generate(AlphaScoreAdapter.fromSequence(sequence))
                assertEquals(plan.length.value()*960,projection.visits.last().end,1e-6)
            }
        }
    }

    @Test fun identicalNamesNeverReplaceDifferentLoadedGrips() {
        val loaded=SongTestLibrary.repository().reload().chords.filter { it.group.startsWith("斜阳") }
        // Exercise a name collision with two real, independently reviewed source grips.
        val low=loaded.first().copy(name="同名测试")
        val high=loaded.first { it.baseFret>=10 }.copy(name="同名测试")
        val sequence=chordSequence(low,high,ChordExercise.CHANGE,4)
        assertSame(low,sequence.chordAt(sequence.sections[0]))
        assertSame(high,sequence.chordAt(sequence.sections[1]))
        val plan=sequence.performance
        assertEquals(listOf(low.id,high.id),plan.score.voicings.map { it.id })
        assertEquals(listOf(low.id,high.id),plan.harmonies.map { it.voicing?.id })
        assertEquals(low.positions+high.positions,plan.notes.map { it.position })
        assertEquals(low.positions.map(low::midi)+high.positions.map(high::midi),plan.notes.map { it.note.midi })
    }

    @Test fun explicitAlternateTuningAndCapoAreKeptWithoutGuessingFingerings() {
        // No bundled teaching card uses alternate tuning; this is a data-path contract fixture.
        val tuning=listOf(64,59,55,50,45,38)
        val chord=Chords.named("Em").copy(id="test-drop-d",openStringMidi=tuning,capoFret=2,
            conditionsLabel="Drop D · 变调夹2品")
        val sequence=chordSequence(chord,chord,ChordExercise.CHECK,4)
        assertSame(chord,sequence.chordAt(sequence.sections.single()))
        assertEquals(tuning,sequence.performance.mainTrack.openStringMidi)
        assertEquals(chord.positions.map(chord::midi),sequence.performance.notes.map { it.note.midi })
        assertTrue(chord.copy(conditionsLabel=null).conditions.contains("D2"))
        assertFalse(chord.copy(conditionsLabel=null).conditions.contains("标准调弦"))
        val loaded=GuitarVoicing("drop-d","来源按法","track",chord.frets.reversed().map { it ?: -1 },chord.fingers.reversed())
            .chord(Track("track","测试轨",tuning,2))
        assertTrue(loaded.conditions.contains("D2"))
        assertTrue(loaded.conditions.contains("变调夹2品"))
    }

    @Test fun incompleteFactsAreUnavailableAndNeverReplacedBySameNamedFoundationGrip() {
        listOf(
            Chords.named("C").copy(id="missing-fingers",fingers=emptyList()),
            Chords.named("C").copy(id="missing-tuning",openStringMidi=emptyList()),
            Chords.named("C").copy(id="missing-grip",frets=List(6) { null })
        ).forEach { chord ->
            assertTrue(chord.unavailableReason!!.contains("和弦数据不可用"))
            val sequence=Sequence(listOf(Event(0,48,emptyList())),48,"",
                listOf(Section(0,chord.name,chord.id,chord)))
            assertNull(sequence.chordAt(sequence.sections.single()))
            val error=runCatching { chordSequence(chord,chord,ChordExercise.CHECK,4) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertEquals(chord.unavailableReason,error!!.message)
        }
        val unknown=Sequence(listOf(Event(0,48,emptyList())),48,"",listOf(Section(0,"缺失的来源和弦")))
        assertNull(unknown.chordAt(unknown.sections.single()))
    }
}
