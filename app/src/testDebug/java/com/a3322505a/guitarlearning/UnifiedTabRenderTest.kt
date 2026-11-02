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
class UnifiedTabRenderTest {
    @Test fun nativePhoneAndTabletTabRetainFretPositionsAndHighlightLookup() {
        for(seq in listOf(LegacyUsotsukiFixture.phrase.sequence,NotationFixtures.mixedStrings,NotationFixtures.legato(Technique.SLIDE))) {
            val score=NativeContent.score(seq)
            val projection=EngineProjection.generate(score).second
            for(width in listOf(360.0,640.0,1280.0)) for(scale in listOf(1.0,1.5)) {
                val renderer=NativeContent.render(score,width,scale)
                val beats=score.tracks.first().staves.first().bars.flatMap { it.voices.toList() }.flatMap { it.beats.toList() }
                beats.forEach { assertNotNull(renderer.boundsLookup!!.findBeat(it)) }
                assertEquals(seq.barCount,projection.visits.size)
            }
        }
    }
}
