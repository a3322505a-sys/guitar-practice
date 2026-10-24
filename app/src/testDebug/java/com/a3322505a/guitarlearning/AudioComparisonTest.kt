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
class AudioComparisonTest {
    @Test fun actualNativeFontAuditionAndMixRemainAudibleAndIndependentlyMutable() {
        val seq=Sequence(listOf(Event(0,12,listOf(Position(5,3),Position(3,0)),strum=true)),48,"audition")
        val score=NativeContent.score(seq)
        val full=NativeContent.pcm(score)
        val muted=NativeContent.pcm(score,music=0.0)
        val click=NativeContent.pcm(score,music=0.0,click=1.0)
        assertTrue(full.any { kotlin.math.abs(it)>.0001f })
        assertTrue(muted.all { kotlin.math.abs(it)<1e-7f })
        assertTrue(click.any { kotlin.math.abs(it)>.0001f })
        val audition=AlphaScoreAdapter.audition(seq,score.tracks.first())
        assertEquals(setOf(48.0,55.0),audition.notes.map { it.realValue }.toSet())
    }
}
