package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.music.score.PracticeKey
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V107RenderTest {
    @get:Rule val compose=createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    private fun capture(name: String) = compose.runOnIdle {
        val view=compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val type=Class.forName("android.view.WindowManagerGlobal")
        val manager=type.getDeclaredMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val windows=type.getDeclaredField("mViews").apply { isAccessible=true }.get(manager) as List<android.view.View>
        windows.filter { it!==view && it.visibility==android.view.View.VISIBLE }.forEach { window ->
            val at=IntArray(2);window.getLocationOnScreen(at);val canvas=Canvas(bitmap)
            canvas.save();canvas.translate(at[0].toFloat(),at[1].toFloat());window.draw(canvas);canvas.restore()
        }
        File("../docs/screenshots/v107/$name.png").apply { parentFile!!.mkdirs();outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        bitmap.recycle()
    }
    @Test fun phoneDictionarySelectsARealGripWithoutListingTheWholeDictionary() {
        RuntimeEnvironment.setFontScale(1.3f)
        var selected: Chord?=null
        compose.activity.setContent { PracticeTheme { Column { ChordDictionaryEntry { selected=it } } } }
        compose.onNodeWithTag("chord-dictionary").performClick()
        compose.onNodeWithTag("dictionary-results").assertIsDisplayed()
        compose.onAllNodesWithText("最低音",substring=true).onFirst().assertIsDisplayed()
        capture("chord-dictionary-phone")
        compose.onAllNodesWithText("使用此配法").onFirst().performClick()
        assertEquals(0,selected!!.root)
        assertNull(selected!!.unavailableReason)
        RuntimeEnvironment.setFontScale(1f)
    }
    @Test fun fullScaleDictionarySelectsBluesAndKeepsTheFiveClosedBoxes() {
        var selected=Tonality.C_MAJOR
        compose.activity.setContent { PracticeTheme { Column { ScaleDictionaryEntry(selected) { selected=it } } } }
        compose.onNodeWithTag("scale-dictionary").performClick()
        compose.onNodeWithTag("dictionary-scale-options").performScrollToNode(hasText("小调布鲁斯"))
        compose.onNodeWithText("小调布鲁斯").performClick()
        compose.onAllNodesWithText("♭5",substring=true).onFirst().assertIsDisplayed()
        capture("scale-dictionary-phone")
        compose.onNodeWithText("在指板与谱面中练习").performClick()
        assertEquals(ScaleDefinition.BLUES,selected.mode)
        assertEquals(5,Patterns.forKey(selected).size)
    }
    @Test fun dailySoloCanSelectManualVolumeChainAndStartTheExactForwardRange() {
        compose.activity.getSharedPreferences("practice-settings",0).edit().clear()
            .putString("phrase-selected",PracticeKey("local.daily-practice","DP-03-basic").encoded).commit()
        val playback=CapturingPlayback()
        compose.activity.setContent { PracticeApp(playback) }
        compose.waitUntil(45000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("领奏 0%").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("领奏 0%").performScrollTo().performClick()
        compose.onNodeWithText("倒序接龙").performScrollTo().performClick()
        compose.onNodeWithTag("fretboard").assertContentDescriptionContains("当前 2 弦 1 品",substring=true)
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals(BarRange(6,7),playback.started!!.range)
        assertEquals("DP-03-basic",playback.started!!.plan.practice.id)
        capture("daily-solo-phone")
    }
    private class CapturingPlayback : PracticePlayback {
        var started: Session?=null
        override var guitar=true;override var click=true
        override val progress=Progress()
        override val session: Session? get()=null
        override val active=false;override val cursor: Cursor?=null
        override fun start(sequence: Sequence,bpm: Int,follow: Boolean,repeats: Int,range: BarRange,sourceTempo: Boolean,multiplier: Double) { started=Session(sequence,bpm,follow,repeats,range=range,sourceTempo=sourceTempo,tempoMultiplier=multiplier) }
        override fun resume() {} override fun restart() {} override fun pause() {} override fun invalidate() {} override fun stop() {}
        override fun audition(positions: List<Position>,strum: Boolean,capoFret: Int,openStringMidi: List<Int>) {}
        override fun release() {}
    }
}
