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
class SongAdvancedTest {
    @Test fun boundaryRejectCarryReattackAndTruncateStayDistinctAndTraceable() {
        val song=NativeContent.fixture("ties");val p=song.practices.single()
        val selected=com.a3322505a.guitarlearning.music.score.PracticeSegment(com.a3322505a.guitarlearning.music.score.Coordinate("tie-b"),com.a3322505a.guitarlearning.music.score.Coordinate("tie-b",com.a3322505a.guitarlearning.music.score.Q(4)))
        assertTrue(runCatching { PracticeCompiler.compile(song.score,p.copy(segments=listOf(selected))) }.isFailure)
        val affected=listOf("tie-origin","tie-target")
        val carry=PracticeCompiler.compile(song.score,p.copy(segments=listOf(selected.copy(entry="carryIn",affectedNoteIds=affected))))
        val attack=PracticeCompiler.compile(song.score,p.copy(segments=listOf(selected.copy(entry="reattack",affectedNoteIds=affected))))
        val cut=PracticeCompiler.compile(song.score,p.copy(segments=listOf(selected.copy(entry="truncate",affectedNoteIds=affected))))
        assertEquals(1,carry.relations.size);assertTrue(carry.notes.first().start<com.a3322505a.guitarlearning.music.score.Q.ZERO)
        assertTrue(attack.relations.isEmpty());assertTrue(cut.notes.isEmpty())
        assertEquals("tie-b",carry.visits.first().measure.id)
    }
    @Test fun multitrackMainChoiceDoesNotConfuseTuningOrPitch() {
        val original=NativeContent.plan("multi-track")
        val solo=original.copy(practice=original.practice.copy(mainTrackId="second-guitar",mixTrackIds=listOf("second-guitar")))
        val score=AlphaScoreAdapter.fromPlan(solo)
        assertEquals(1,score.tracks.length.toInt())
        val attacks=EngineProjection.generate(score).first.events.filterIsInstance<NoteOnEvent>()
        assertTrue(attacks.any { it.noteKey==65.0 })
        assertEquals("local-grip",NativeContent.plan("strum").harmonies.single().voicing!!.id)
    }
    @Test fun everyExpandedFixtureHasValidNativeSynthesisAndNoSongRegistration() {
        NativeContent.unsupported.keys.forEach(NativeContent::assertRejected)
        for(name in listOf("shared","multi-track","conditional","pickup","strum","boundary-carry","boundary-reattack","boundary-truncate")) {
            val score=NativeContent.score(name)
            NativeContent.render(score)
            val pcm=NativeContent.pcm(score)
            if(name=="boundary-truncate") assertTrue(pcm.all { kotlin.math.abs(it)<1e-7f })
            else assertTrue(name,pcm.any { kotlin.math.abs(it)>.0001f })
        }
    }
}
