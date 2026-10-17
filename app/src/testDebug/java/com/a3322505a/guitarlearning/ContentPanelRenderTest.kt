package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.compose.setContent
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
        compose.waitUntil(15000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            File(compose.activity.filesDir, "songs/active-index.json").let { it.exists() && it.readText().contains("local.yourtrip") }
        }
        compose.waitForIdle()
    }

    private fun open() {
        compose.onNodeWithTag("config-summary").performClick()
        val expand = SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)
        if (compose.onAllNodes(expand).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(expand).performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.waitForIdle()
    }

    private fun source(id: String, click: Boolean = true) {
        // The persisted index exists before the recreated activity finishes its async reload.
        compose.waitUntil(15000) {
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
        File("../.local/content-panel-v101/$name.png").apply { parentFile!!.mkdirs(); outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        bitmap.recycle()
    }

    @Test fun phoneSourceNavigationHeadingsCleanTitlesAndRestoration() {
        ready()
        ThemeManager(compose.activity).let { it.select("studio_dark"); it.close() }
        open(); compose.onNodeWithText("歌曲与短句").performClick()
        compose.onNodeWithTag("content-section-单音").assertIsDisplayed()
        compose.onNodeWithText("C 大调小句").assertIsDisplayed()
        compose.onNodeWithText("2 变奏").assertIsDisplayed()
        capture("phone-basic-dark")
        compose.onNodeWithText("7p5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("content-source-filters").assertIsDisplayed()
        compose.onNodeWithText("歌曲与短句").assertIsDisplayed()
        compose.onNodeWithTag("content-package-management").assertIsDisplayed()
        capture("phone-techniques-dark")
        compose.onNodeWithText("完整版").performScrollTo().assertIsDisplayed()
        capture("phone-rhythm-dark")
        source("local.usotsuki")
        compose.onNodeWithText("低音与高音分解").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("content-section-练习片段")).assertCountEquals(0)
        compose.onNodeWithText("段落与版本 ▾").assertDoesNotExist()
        capture("phone-usotsuki-dark")
        source("local.shayou")
        compose.onNodeWithText("前奏").assertIsDisplayed()
        capture("phone-shayou-songs-dark")
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithTag("content-source-local.shayou").assertIsSelected()
        capture("phone-shayou-chords-dark")
        compose.onNodeWithText("歌曲与短句").performClick(); source("local.yourtrip")
        compose.onNodeWithTag("content-section-节奏与伴奏").assertIsDisplayed()
        compose.onNodeWithTag("content-section-旋律与连奏").performScrollTo().assertIsDisplayed()
        capture("phone-yourtrip-dark")
        compose.onNodeWithText("和弦").performClick(); source(BASIC_CONTENT, click = false)
        compose.onNodeWithTag("content-source-$BASIC_CONTENT").assertIsSelected()
        compose.onNodeWithText("歌曲与短句").performClick(); source("local.yourtrip", click = false)
        compose.onNodeWithTag("content-source-local.yourtrip").assertIsSelected()
        compose.onNodeWithText("完成").performClick(); open()
        source("local.yourtrip", click = false)
        compose.onNodeWithTag("content-source-local.yourtrip").assertIsSelected()
        compose.onNodeWithText("完成").performClick()
        compose.activityRule.scenario.recreate(); ready(); open(); source("local.yourtrip", click = false)
        compose.onNodeWithTag("content-source-local.yourtrip").assertIsSelected()
        compose.onNodeWithTag("content-package-management").performClick()
        compose.onNodeWithText("选择曲包").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w1024dp-h768dp-mdpi") fun wideLightPanelKeepsVoicingsAndLabelsReadable() {
        ready()
        ThemeManager(compose.activity).let { it.select("forest"); it.close() }
        open(); compose.onNodeWithText("和弦").performClick(); source("local.shayou")
        compose.onNodeWithTag("content-section-斜阳 · 伴奏指法").assertDoesNotExist()
        capture("tablet-shayou-forest")
        source(BASIC_CONTENT)
        compose.onNodeWithText("C").assertIsSelected()
        capture("tablet-selected-forest")
        ThemeManager(compose.activity).let { it.select("warm_neutral"); it.close() }
        compose.onNodeWithText("C").assertIsSelected()
        capture("tablet-selected-warm-neutral")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-mdpi") fun largeFontReflowsCardsAndKeepsPinnedControlsAvailable() {
        RuntimeEnvironment.setFontScale(1.5f)
        try {
            compose.activityRule.scenario.recreate()
            ready(); open(); compose.onNodeWithText("歌曲与短句").performClick()
            compose.onNodeWithText("3 再现与结尾").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("完成").assertIsDisplayed()
            compose.onNodeWithTag("content-package-management").assertIsDisplayed()
            capture("phone-basic-large-font")
            source("local.usotsuki"); capture("phone-usotsuki-large-font")
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    @Test fun browsingSourceWhilePlayingRetainsSessionTempoAndSelectedPractice() {
        val player = Player(compose.activity) { ManualSink() }
        try {
            ready()
            Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java))
                .setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            ready(); open(); compose.onNodeWithText("歌曲与短句").performClick(); compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("开始跟练").performClick()
            compose.waitUntil(8000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); player.progress.phase == Phase.PLAYING }
            val session = player.session
            val summary = compose.onNodeWithTag("config-summary").fetchSemanticsNode().config[SemanticsProperties.Text]
            open(); source("local.yourtrip"); source("local.usotsuki")
            assertSame(session, player.session)
            assertEquals(Phase.PLAYING, player.progress.phase)
            compose.onNodeWithText("完成").performClick()
            assertEquals(summary, compose.onNodeWithTag("config-summary").fetchSemanticsNode().config[SemanticsProperties.Text])
        } finally { player.release() }
    }
}
