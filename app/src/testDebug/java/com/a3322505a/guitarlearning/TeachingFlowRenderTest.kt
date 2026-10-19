package com.a3322505a.guitarlearning

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.music.score.PracticeKey
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.ui.PracticeApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w411dp-h891dp-mdpi")
class TeachingFlowRenderTest {
    @get:Rule val compose=createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    private fun ready() {
        compose.waitUntil(45000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun select(id: String): CapturingPlayback {
        ready()
        compose.activity.getSharedPreferences("practice-settings",0).edit()
            .putString("phrase-selected",PracticeKey("local.teaching-v31",id).encoded).commit()
        val playback=CapturingPlayback()
        compose.activity.setContent { PracticeApp(playback) }
        compose.waitForIdle();ready()
        playable()
        return playback
    }
    private fun playable() = compose.waitUntil(45000) {
        runCatching { compose.onNodeWithText("开始跟练").assertIsEnabled() }.isSuccess
    }
    @Test fun backwardChainExtendsForwardRangesAndWarmupReturnsToTheSameSelectedRange() {
        val playback=select("LEAD-1")
        compose.onNodeWithText("渐隐领奏").assertExists()
        compose.onNodeWithText("倒序接龙").performClick()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals(BarRange(3,3),playback.started!!.range)
        assertEquals(0,playback.started!!.repeats)
        compose.onNodeWithText("接上前一段").performClick()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals(BarRange(2,3),playback.started!!.range)
        compose.onNodeWithText("退出接龙").performClick()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals(BarRange(0,3),playback.started!!.range)
        assertEquals(4,playback.started!!.repeats)
        compose.onNodeWithText("练前热身").performClick();playable()
        compose.onNodeWithTag("config-summary").assertTextContains("封闭 Box1",substring=true)
        compose.onNodeWithText("返回原曲段").performClick();playable()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals("LEAD-1",playback.started!!.plan.practice.id)
        assertEquals(BarRange(0,3),playback.started!!.range)
    }
    @Test fun suppliedVersionsSwitchDirectlyAndSingleTrackHasNoFadingControl() {
        val playback=select("POWER-E5")
        compose.onNodeWithText("衔接练习").assertIsEnabled()
        compose.onAllNodesWithText("渐隐领奏").assertCountEquals(0)
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals(6,playback.started!!.plan.notes.size)
        compose.onNodeWithText("双音版").performClick();playable()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals(4,playback.started!!.plan.notes.size)
        assertEquals("POWER-E5-dual",playback.started!!.plan.practice.id)
        assertEquals(4.0,playback.started!!.plan.length.value(),0.0)
    }
    @Test fun transitionLoopsBothCompleteBarsAndReturnsToTheOriginalPhrase() {
        val playback=select("FLOW-C")
        compose.onNodeWithText("衔接练习").performClick();playable()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals("JOIN-C-Am",playback.started!!.plan.practice.id)
        assertEquals(BarRange(0,1),playback.started!!.range)
        assertEquals(0,playback.started!!.repeats)
        compose.onNodeWithText("返回原曲段").performClick();playable()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals("FLOW-C",playback.started!!.plan.practice.id)
        assertEquals(BarRange(0,1),playback.started!!.range)
        assertEquals(4,playback.started!!.repeats)
    }
    @Test fun beginnerSongVersionChangesRealHarmonyInsideTheSameSlot() {
        val playback=select("SONG-1")
        val alternative=compose.onNode(hasText("简化替代 ·",substring=true) and hasClickAction())
        alternative.performClick();playable()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals("SONG-1-beginner",playback.started!!.plan.practice.id)
        val names=playback.started!!.plan.harmonies.map { it.voicing!!.teaching!!.soundingName }.toSet()
        assertTrue(names.containsAll(listOf("Fmaj7","Gsus2")))
        assertFalse(names.contains("F"));assertFalse(names.contains("G"))
    }
    @Test fun aSingleNoteLessonCanSelectManualChordTonesWithoutAnAutomaticHarmony() {
        select("FB-1")
        compose.onAllNodesWithTag("board-label-menu").onFirst().performClick()
        compose.onNodeWithText("选择参照和弦…").performClick()
        compose.onNodeWithText("参照 Dm").performScrollTo().performClick()
        compose.onAllNodesWithTag("board-label-menu").onFirst().assertTextContains("参照和弦音 · Dm",substring=true)
        assertEquals("Dm",compose.activity.getSharedPreferences("practice-settings",0).getString("reference-chord",null))
    }
    private class CapturingPlayback : PracticePlayback {
        var started: Session?=null
        override var guitar=true
        override var click=true
        override val progress=Progress()
        override val session: Session? get()=null
        override val active=false
        override val cursor: Cursor?=null
        override fun start(sequence: Sequence,bpm: Int,follow: Boolean,repeats: Int,range: BarRange,sourceTempo: Boolean,multiplier: Double) {
            started=Session(sequence,bpm,follow,repeats,range=range,sourceTempo=sourceTempo,tempoMultiplier=multiplier)
        }
        override fun resume() {} override fun restart() {} override fun pause() {}
        override fun invalidate() {} override fun stop() {}
        override fun audition(positions: List<Position>,strum: Boolean,capoFret: Int,openStringMidi: List<Int>) {}
        override fun release() {}
    }
}
