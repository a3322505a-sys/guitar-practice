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
    private fun select(packageId: String, id: String): CapturingPlayback {
        ready()
        compose.activity.getSharedPreferences("practice-settings", 0).edit().clear()
            .putString("phrase-selected", PracticeKey(packageId, id).encoded).commit()
        val playback = CapturingPlayback()
        compose.activity.setContent { PracticeApp(playback) }
        compose.waitForIdle(); ready(); playable()
        return playback
    }
    private fun playable() = compose.waitUntil(45000) {
        runCatching { compose.onNodeWithText("开始跟练").assertIsEnabled() }.isSuccess
    }
    @Test fun dailyChainExtendsForwardAndWarmupReturnsToTheSelectedRange() {
        val playback = select("local.daily-practice", "DP-03-basic")
        compose.onNodeWithText("倒序接龙").performScrollTo().performClick()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals(BarRange(6, 7), playback.started!!.range)
        assertEquals(0, playback.started!!.repeats)
        compose.onNodeWithText("接上前一段").performScrollTo().performClick()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals(BarRange(4, 7), playback.started!!.range)
        compose.onNodeWithText("退出接龙").performScrollTo().performClick()
        compose.onNodeWithText("练前热身").performScrollTo().performClick(); playable()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals("S-Am-box5-pent", playback.started!!.plan.practice.id)
        compose.onNodeWithText("返回原曲段").performScrollTo().performClick(); playable()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals("DP-03-basic", playback.started!!.plan.practice.id)
        assertEquals(BarRange(0, 7), playback.started!!.range)
    }
    @Test fun nativeDemoTransitionsLoopCompleteBarsAndVersionsChangeRealHarmony() {
        val playback = select("local.demo-c", "A-original")
        compose.onNodeWithText("衔接练习").performScrollTo().performClick(); playable()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals("JOIN-original", playback.started!!.plan.practice.id)
        assertEquals(BarRange(0, 1), playback.started!!.range)
        assertEquals(0, playback.started!!.repeats)
        compose.onNodeWithText("返回原曲段").performScrollTo().performClick(); playable()
        compose.onNodeWithText("新手简化版").performScrollTo().performClick(); playable()
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals("A-beginner", playback.started!!.plan.practice.id)
        assertTrue(playback.started!!.plan.harmonies.any { it.voicing?.teaching?.soundingName == "Fmaj7" })
    }
    @Test fun aSingleNoteLessonCanSelectManualChordTonesWithoutAnAutomaticHarmony() {
        select("local.teaching-native", "FB-1")
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
