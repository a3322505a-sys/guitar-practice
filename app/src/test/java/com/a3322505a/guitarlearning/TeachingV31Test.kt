@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.alphatab.AlphaScoreAdapter
import com.a3322505a.guitarlearning.alphatab.EngineProjection
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.audio.PlaybackMix
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class TeachingV31Test {
    private fun teaching(id: String) = SongTestLibrary.repository.resolve(PracticeKey("local.teaching-native",id).encoded)

    @Test fun theoreticalSpellingMinorDegreesAndSoundingCapoAreIndependent() {
        assertEquals(listOf("B♭","C","D","E♭","F","G","A"),KeyContext(SpelledPitch.parse("Bb"),ScaleDefinition.MAJOR).spelling.map { it.name })
        assertEquals(listOf("C♯","D♯","E♯","F♯","G♯","A♯","B♯"),KeyContext(SpelledPitch.parse("C#"),ScaleDefinition.MAJOR).spelling.map { it.name })
        assertEquals("♭3",Tonality.A_MINOR.degreeLabel(60))
        assertEquals("1",Tonality.A_MINOR.degreeLabel(57))
        assertEquals("♭7",Tonality.A_MINOR.degreeLabel(55))
        assertEquals(62,TuningContext(capo=2).midi(Position(2,1)))
        val plan=teaching("CAPO-2").sequence.performance
        assertEquals(listOf("D","A","Bm","Gmaj7"),plan.harmonies.map { it.voicing!!.teaching!!.soundingName })
        assertTrue(plan.harmonies.last().voicing!!.quality==ChordQuality.MAJOR_SEVENTH)
    }
    @Test fun retainedFingersRequireSameStringFretAndFingerAndBarresAreExplicit() {
        assertEquals(setOf(Position(2,1),Position(4,2)),retainedFingers(Chords.named("Am"),Chords.named("C")).map { it.position }.toSet())
        assertTrue(retainedFingers(Chords.named("Em"),Chords.named("Am")).isEmpty())
        assertEquals(6,Chords.named("F").barres.single().fromString)
        assertNotEquals(ChordDefinition.parse("F"),ChordDefinition.parse("Fmaj7"))
        listOf("F" to "Fmaj7","Bm" to "Bm7(no5)","G" to "Gsus2").forEach { (a,b) ->
            val choices=ChordSlots.choices(Chords.named(a))
            assertEquals(listOf(a,b),choices.map { it.name })
            assertNotEquals(choices[0].quality,choices[1].quality)
            assertNotEquals(choices[0].positions.map { choices[0].midi(it)%12 }.toSet(),choices[1].positions.map { choices[1].midi(it)%12 }.toSet())
            assertFalse(ChordSlots.grouped(Chords.all).any { it.name==b })
        }
    }
    @Test fun everyNewLessonAndMigratedBuiltinReachesTheOneNativeMidiAndCursorModel() {
        val repo=SongTestLibrary.repository
        val all=repo.snapshot.phrases.filter { it.assetKey?.packageId?.startsWith("local.teaching-")==true }
        assertEquals(89,all.size)
        all.forEach { descriptor ->
            val phrase=repo.resolve(descriptor.id)
            val plan=phrase.sequence.performance
            assertNotNull(descriptor.title,phrase.lesson)
            assertTrue(descriptor.title,plan.notes.isNotEmpty())
            val native=AlphaScoreAdapter.fromPlan(plan)
            val (_,projection)=EngineProjection.generate(native)
            assertEquals(descriptor.title,plan.visits.size,projection.visits.size)
            assertEquals(descriptor.title,plan.length.value()*960,projection.visits.last().end,1e-6)
        }
        assertFalse(repo.snapshot.aliases.keys.any { it.startsWith("SB-") })
    }
    @Test fun sixTeachingKeysHaveOnlyTheirRealScalePitchesAndClosedBoxesHaveNoOpenStrings() {
        Tonality.entries.forEach { key -> Patterns.forKey(key).forEach { pattern ->
            assertTrue(key.title,pattern.positions.all { it.midi%12 in key.pitchClasses })
        } }
        listOf("Am","Bm","Em").forEach { tonic ->
            val plan=teaching("S-$tonic-box5-pent").sequence.performance
            assertTrue(plan.notes.all { it.note.fret>0 && it.finger in 1..4 })
            assertTrue(plan.notes.all { it.note.midi%12 in plan.practice.lesson!!.key!!.pentatonic })
        }
        val c=teaching("S-C-open").sequence.performance.notes.take(8).map { it.note.midi }
        assertEquals(listOf(48,50,52,53,55,57,59,60),c)
        assertEquals(listOf(43,45,47,48,50,52,54,55),teaching("S-G-open").sequence.performance.notes.take(8).map { it.note.midi })
    }
    @Test fun optionalPlaybackHasRealPrerequisitesAndRangesDoNotReverseEvents() {
        val seq=SongTestLibrary.repository.resolve(PracticeKey("local.demo-c","SOLO-original").encoded).sequence
        assertTrue(PracticeExtensions.canFade(seq))
        assertTrue(PracticeExtensions.canFade(SongTestLibrary.repository.resolve(PracticeKey("local.demo-rock","SOLO-original").encoded).sequence))
        assertEquals(setOf("original-lead","original-backing"),SongTestLibrary.repository.resolve(PracticeKey("local.demo-rock","SOLO-original").encoded).sequence.performance.notes.map { it.track.id }.toSet())
        assertFalse(PracticeExtensions.canFade(teaching("FB-1").sequence))
        assertEquals(listOf(1.0,.3,0.0,0.0),(0..3).map(PracticeExtensions::leadGain))
        assertEquals(listOf(BarRange(2,3),BarRange(0,3)),PracticeExtensions.backwardRanges(seq,BarRange(0,3)))
        val join=SongTestLibrary.repository.resolve(PracticeKey("local.demo-c","JOIN-original").encoded).sequence.performance
        assertEquals(listOf("song-m-3","song-m-4"),join.visits.map { it.sourceMeasureId })
        assertEquals(Q(8),join.length)
        assertEquals(listOf(BarRange(0,1)),PracticeExtensions.backwardRanges(teaching("FB-1").sequence,BarRange(0,1)))
    }
    @Test fun globalGainsDoNotConvertOldSwitchesToVolumeAndPreserveExplicitZero() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("v31-mix-test",0)
        prefs.edit().clear().putBoolean("guitar",false).commit()
        prefs.edit().putBoolean("click",false).commit()
        assertEquals(PlaybackMix(1.0,1.0),PlaybackMix.load(prefs))
        prefs.edit().putBoolean("click",true).commit()
        assertEquals(PlaybackMix(1.0,1.0),PlaybackMix.load(prefs))
        PlaybackMix(.37,0.0).save(prefs)
        assertEquals(.37,PlaybackMix.load(prefs).music,1e-6)
        assertEquals(0.0,PlaybackMix.load(prefs).metronome,0.0)
    }
    @Test fun fiveHintsUseExplicitKeyAndChordAndKeepLegacyHiddenOrdinal() {
        assertEquals(Hint.HIDDEN,Hint.entries[2])
        val c=BoardTeachingContext(Tonality.A_MINOR.context,Chords.named("G"))
        assertEquals(5,c.available.size)
        assertTrue(c.title(Hint.ROOTS).contains("A"))
        assertEquals(9,c.emphasisRoot(Hint.ROOTS))
        assertEquals(7,c.emphasisRoot(Hint.CHORD_TONES))
        assertFalse(c.emphasized(Hint.CHORD_TONES,60))
        assertTrue(c.emphasized(Hint.CHORD_TONES,59))
        assertTrue(BoardTeachingContext().available.contains(Hint.CHORD_TONES))
        assertTrue(BoardTeachingContext(chord=Chords.named("Dm"),reference=true).title(Hint.CHORD_TONES).contains("参照"))
    }
    @Test fun effectiveBpmRetainsMidBarTempoChangesAndOneCommonScaleFactor() {
        val original=teaching("FB-1").sequence
        val plan=original.performance
        val timed=original.copy(runtimePlan=plan.copy(tempos=listOf(Tempo(Q.ZERO,Q.ONE,80.0),Tempo(Q(2),Q.ONE,120.0))))
        assertEquals(80.0,TempoControl.sourceBpmAt(timed,1.0),0.0)
        assertEquals(120.0,TempoControl.sourceBpmAt(timed,3.0),0.0)
        val ratio=TempoControl.ratio(60,80.0)
        assertEquals(90.0,TempoControl.sourceBpmAt(timed,3.0)*ratio,0.0)
    }
}
