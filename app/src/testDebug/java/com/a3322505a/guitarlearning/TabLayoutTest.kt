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
class TabLayoutTest {
    @Test fun nativeOnsetsRemainSeparateFromSustainingBassAndRestVoices() {
        val projection=EngineProjection.generate(NativeContent.score("complex")).second
        assertTrue(projection.visits.size>1)
        for(note in NativeContent.plan("complex").notes.filter { !it.note.dead && it.note.harmonic==null }) {
            assertEquals(note.note.midi,note.track.midi(note.position.string,note.position.fret))
        }
        for(name in listOf("ties","boundary-carry","boundary-truncate","shared","harmonics","nested")) {
            val plan=NativeContent.plan(name)
            val score=AlphaScoreAdapter.fromPlan(plan)
            NativeContent.render(score,360.0)
            assertEquals(plan.visits.size,score.masterBars.length.toInt())
        }
    }
    @Test fun nativeGeometryKeepsEveryOriginalNoteAtReadableNonzeroBounds() {
        for(name in listOf("complex","techniques","harmonics","nested")) {
            val score=NativeContent.score(name)
            val renderer=NativeContent.render(score,600.0)
            score.tracks.first().staves.first().bars.forEach { bar -> bar.voices.forEach { voice -> voice.beats.forEach { beat ->
                val bounds=renderer.boundsLookup!!.findBeat(beat)
                assertNotNull("$name beat ${beat.index}",bounds)
                assertTrue(bounds!!.realBounds.w>0)
            } } }
        }
    }
    @Test fun notationCleanupChangesNeitherNativeMidiAttacksNorDurations() {
        val score=NativeContent.score(NotationFixtures.mixedStrings)
        fun facts()=EngineProjection.generate(score).first.events.map { Triple(it.type,it.tick,it.toString()) }
        val before=facts()
        AlphaNotation.prepare(score)
        assertEquals(before,facts())
    }
}
