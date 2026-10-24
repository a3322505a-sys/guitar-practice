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
class TabScrollRiskTest {
    @Test fun repeatedRenderingChangesOnlyBoundsAndNeverMusicalPositionOrTempo() {
        val score=NativeContent.score(LegacyUsotsukiFixture.phrase.sequence)
        val (midi,projection)=EngineProjection.generate(score)
        val attacks=midi.events.filterIsInstance<NoteOnEvent>().map { it.tick to it.noteKey }
        listOf(360.0,1280.0,360.0).forEach { width ->
            NativeContent.render(score,width)
            val next=EngineProjection.generate(score)
            assertEquals(attacks,next.first.events.filterIsInstance<NoteOnEvent>().map { it.tick to it.noteKey })
            assertEquals(projection.visits.map { it.start to it.end },next.second.visits.map { it.start to it.end })
        }
    }
}
