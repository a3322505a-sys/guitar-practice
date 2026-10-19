package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentPanelRenderTest {
    @get:Rule val compose = createAndroidComposeRule<LegacyWorkbenchTestActivity>()

    private fun ready() {
        compose.waitUntil(45000) {
            compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    // Source-specific voicings may legitimately share a name (for example Fmaj7).
    private fun card(title: String) = compose.onAllNodes(hasText(title) and hasAnyAncestor(hasTestTag("content-card-list"))).onFirst()

    private fun open() {
        compose.onNodeWithTag("config-summary").performClick()
        val expand = SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)
        if (compose.onAllNodes(expand).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(expand).performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.waitForIdle()
    }

    private fun source(id: String, click: Boolean = true) {
        // The persisted index exists before the recreated activity finishes its async reload.
        compose.waitUntil(45000) {
            runCatching { compose.onNodeWithTag("content-source-filters").performScrollToKey(id) }.isSuccess
        }
        if (click) compose.onNodeWithTag("content-source-$id").performClick()
    }

    private fun capture(name: String) = compose.runOnIdle {
        val base = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        base.draw(canvas)
        val type = Class.forName("android.view.WindowManagerGlobal")
        val manager = type.getDeclaredMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = type.getDeclaredField("mViews").apply { isAccessible = true }.get(manager) as List<android.view.View>
        val origin = IntArray(2); base.getLocationOnScreen(origin)
        views.filter { it !== base && it.visibility == android.view.View.VISIBLE }.forEach {
            val at = IntArray(2); it.getLocationOnScreen(at)
            canvas.save(); canvas.translate((at[0] - origin[0]).toFloat(), (at[1] - origin[1]).toFloat()); it.draw(canvas); canvas.restore()
        }
        File("../.local/teaching-ui/$name.png").apply { parentFile!!.mkdirs(); outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        bitmap.recycle()
    }


    @Test fun fourModulesKeepOriginalSourcesAndExposeReviewedLessons() {
        ready(); open()
        compose.onNode(hasText("基础启蒙") and hasClickAction()).assertIsSelected()
        card("单弦交替拨弦与同步").performScrollTo().assertIsDisplayed()
        card("爬格子与专项保留指").performScrollTo().assertIsDisplayed()
        capture("v31-foundations")
        compose.onNode(hasText("和弦教学") and hasClickAction()).performClick()
        card("Am ↔ C").performScrollTo().assertIsDisplayed()
        card("Fmaj7").performScrollTo().assertIsDisplayed()
        capture("v31-chords")
        source("local.usotsuki")
        compose.onNodeWithTag("content-source-local.usotsuki").assertIsSelected()
        compose.onNode(hasText("综合应用") and hasClickAction()).performClick()
        compose.onNodeWithText("低音与高音分解").assertIsDisplayed()
        source("local.yourtrip")
        compose.onNodeWithTag("content-section-节奏与伴奏").assertIsDisplayed()
        capture("v31-songs")
        compose.onNodeWithText("完成").performClick(); open()
        source("local.yourtrip",click=false)
        compose.onNodeWithTag("content-source-local.yourtrip").assertIsSelected()
        compose.onNodeWithTag("content-package-management").performClick()
        compose.onNodeWithText("选择曲包").assertIsDisplayed()
    }
    @Test @Config(qualifiers="w1024dp-h768dp-mdpi") fun scaleKeyPentatonicAndRhythmChoicesRemainIndependent() {
        ready(); open(); compose.onNode(hasText("音阶教学") and hasClickAction()).performClick()
        compose.onNodeWithText("G 大调").performScrollTo().performClick()
        compose.onNodeWithText("五声骨架").performScrollTo().performClick()
        compose.onNodeWithText("每拍3音").performScrollTo().performClick()
        compose.onNodeWithText("每拍3音").assertIsSelected()
        capture("v31-scale-wide")
        compose.onNodeWithText("五声骨架").performScrollTo().assertIsSelected()
        compose.onNodeWithText("G 大调").performScrollTo().assertIsSelected()
    }
    @Test fun smallScreenLargeFontKeepsModulesAndPlaybackControlsReachable() {
        val player=Player(compose.activity) { ManualSink() }
        try {
            // Separate Android dialog windows obtain font scale from Resources.
            RuntimeEnvironment.setFontScale(1.5f)
            compose.activity.setContent {
                // 411x891 host pixels / this density = 360x780 dp, with 1.5x text.
                CompositionLocalProvider(LocalDensity provides Density(411f/360f,1.5f)) { PracticeApp(player) }
            }
            ready(); open()
            compose.onNode(hasText("基础启蒙") and hasClickAction()).performClick()
            card("单八度音乐化模进").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("完成").assertIsDisplayed()
            compose.onNodeWithTag("content-package-management").assertIsDisplayed()
            capture("v31-small-large-font")
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("volume-button").performClick()
            compose.onNodeWithTag("music-volume").assertIsDisplayed()
            compose.onNodeWithTag("metronome-volume").assertIsDisplayed()
            capture("v31-volume-large-font")
        } finally { player.release(); RuntimeEnvironment.setFontScale(1f) }
    }
    @Test fun volumeSlidersPersistIndependentlyAtZeroAndPartialGain() {
        ready(); compose.onNodeWithTag("volume-button").performClick()
        compose.onNodeWithTag("music-volume").performSemanticsAction(SemanticsActions.SetProgress) { it(.37f) }
        compose.onNodeWithTag("metronome-volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText("完成").performClick()
        val prefs=compose.activity.getSharedPreferences("practice-settings",0)
        assertEquals(.37f,prefs.getFloat("music-volume",-1f),.001f)
        assertEquals(0f,prefs.getFloat("metronome-volume",-1f),.001f)
        compose.activityRule.scenario.recreate(); ready()
        compose.onNodeWithTag("volume-button").performClick()
        compose.onNodeWithText("音乐音量 37%").assertIsDisplayed()
        compose.onNodeWithText("节拍音量 0%").assertIsDisplayed()
    }
}
