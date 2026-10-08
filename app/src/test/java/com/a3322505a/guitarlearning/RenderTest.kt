package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.ui.PracticeApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val path=File("../.local/v50/$name.png")
            path.parentFile!!.mkdirs()
            path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun phoneNavigationConfigurationAndHintApplyToBothViews() {
        compose.onNodeWithTag("config-summary").assertIsDisplayed()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        capture("phone-scale")
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("相邻指型连接").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        capture("phone-connection")
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("C · 下一：Am").assertIsDisplayed()
        capture("phone-chord")
        compose.onNodeWithText("六线谱").performClick()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
        capture("phone-chord-tab")
        compose.onNodeWithText("撤提示").performClick()
        val descriptions=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes()
        assertFalse(descriptions.any { node -> node.config[SemanticsProperties.ContentDescription].any { it.contains("品；") } })
        capture("phone-tab-hidden")
        compose.onNodeWithText("指板").performClick()
        compose.onNodeWithText("已撤提示").assertIsDisplayed()
        compose.onNodeWithText("恢复提示").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onAllNodesWithText("D")[0].performScrollTo().performClick()
        compose.onNodeWithText("D 根音 · A 五音 · D 根音 · F♯ 三音").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("D · 下一：Am").assertIsDisplayed()
        capture("phone-d-chord")
    }
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi") fun tabletDefaultsToTabAndWrapsNotation() {
        compose.onNodeWithTag("tablature").assertIsDisplayed()
        capture("tablet-scale-tab")
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("三音模进").performScrollTo().performClick()
        compose.onNodeWithText("遍历指型音域").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        capture("tablet-multiline-tab")
        compose.onNodeWithText("和弦").performClick()
        capture("tablet-chord-tab")
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("简单分解").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        capture("tablet-arpeggio-tab")
        compose.onNodeWithText("指板").performClick()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
    }
    @Test @Config(qualifiers="w800dp-h1100dp-port-mdpi") fun tabletPortraitAndNarrowNotationRemainReadable() {
        compose.onNodeWithText("六线谱").performClick()
        compose.onNodeWithText("听示范").assertIsDisplayed()
        capture("tablet-portrait-tab")
    }
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi") fun notationShowsEighthBeamsRestsAndAcrossBarTies() {
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("三音模进").performScrollTo().performClick()
        compose.onNodeWithText("遍历指型音域").performScrollTo().performClick()
        compose.onNodeWithText("八分音符").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        capture("tablet-eighths-rest")
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("8 拍").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        capture("tablet-ties")
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("规则下拨").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("↑ 下拨：低音弦 → 高音弦 · 下方是节奏").assertIsDisplayed()
        capture("tablet-downstroke")
    }
    @Test @Config(qualifiers="w891dp-h411dp-land-mdpi") fun phoneLandscapeKeepsTransportReachable() {
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("听示范").assertIsDisplayed()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        capture("phone-landscape")
    }
    @Test fun activityRecreationKeepsPlayerAndSelectedPractice() {
        val original=ViewModelProvider(compose.activity)[PracticeModel::class.java].player
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onAllNodesWithText("D")[0].performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("六线谱").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertSame(original,ViewModelProvider(compose.activity)[PracticeModel::class.java].player)
        compose.onNodeWithText("D · 下一：Am").assertIsDisplayed()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
    }
    @Test fun activeViewSwitchKeepsSessionAndMutedPlaybackAdvances() {
        val sinks=mutableListOf<ManualSink>()
        val player=Player(compose.activity) { ManualSink().also { synchronized(sinks) { check(sinks.all { s -> s.closed }); sinks += it } } }
        try {
            compose.activity.getSharedPreferences("practice-settings",0).edit().putBoolean("guitar",false).putBoolean("click",false).apply()
            org.robolectric.Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java)).setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.waitForIdle()
            compose.onNodeWithText("开始跟练").performClick()
            assertNotNull("New content must call the injected player", player.session)
            compose.waitUntil(8000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                if(player.progress.phase==Phase.ERROR) error(player.progress.message)
                player.progress.phase==Phase.PLAYING
            }
            val s=player.session!!
            val sink=synchronized(sinks) { sinks.last() }
            compose.waitUntil(8000) { sink.written.get()>=s.frame(120.0) }
            sink.head.set(s.frame(120.0))
            compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); player.progress.tick>=119.9 }
            val tick=player.progress.tick
            compose.onNodeWithText("六线谱").performClick()
            assertSame(s,player.session)
            assertEquals(tick,player.progress.tick,0.01)
            assertFalse(player.guitar);assertFalse(player.click)
            assertFalse(sink.nonZero)
            capture("phone-playing-tab")
            compose.onNodeWithText("暂停").performClick()
            assertEquals(Phase.PAUSED,player.progress.phase)
            assertEquals(96.0,player.progress.tick,0.0)
            compose.onNodeWithText("指板").performClick()
            assertSame(s,player.session)
            compose.onNodeWithText("继续").performClick()
            compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); player.progress.phase==Phase.PLAYING }
            assertSame(s,player.session)
            compose.onNodeWithText("重来").performClick()
            compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); player.progress.tick<48.0 && player.progress.phase==Phase.PLAYING }
            compose.onNodeWithText("结束").performClick()
            assertNull(player.session)
        } finally { player.release() }
    }
}

class ManualSink : PlaybackSink {
    val head=AtomicLong(0)
    val written=AtomicLong(0)
    @Volatile var nonZero=false
    @Volatile var closed=false
    override val heardFrames get()=head.get()
    override fun play() {}
    override fun write(samples: ShortArray, offset: Int, count: Int): Int { if(samples.any { it!=0.toShort() }) nonZero=true; written.addAndGet(count.toLong()); Thread.sleep(1); return count }
    override fun close() { closed=true }
}
