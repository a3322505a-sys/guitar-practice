package com.a3322505a.guitarlearning

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.TeachingModule
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentPanelRenderTest {
    @get:Rule val compose = createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    private fun ready() {
        compose.waitUntil(45000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun open() {
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithTag("content-panel").assertIsDisplayed()
    }
    private fun card(title: String) = compose.onAllNodes(hasText(title) and hasAnyAncestor(hasTestTag("content-card-list"))).onFirst()

    @Test fun allFourModulesKeepRealLessonsAndDictionaryAnchorsIndividuallyReachable() {
        ready(); open()
        val content = ContentPresentation(SongTestLibrary.repository().reload())
        listOf("tab-foundations", "tab-chords", "tab-scales", "tab-application").forEachIndexed { index, tag ->
            compose.onNodeWithTag(tag).performClick().assertIsSelected()
            val lesson = content.phraseSections(BASIC_CONTENT, TeachingModule.entries[index]).flatMap { it.items }.first()
            card(content.phraseCard(lesson).title).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("chord-dictionary").performClick()
        compose.onNodeWithTag("dictionary-results").assertIsDisplayed()
        compose.onAllNodesWithText("最低音", substring = true).onFirst().assertIsDisplayed()
        compose.onNodeWithTag("scale-dictionary").performClick()
        compose.onNodeWithTag("dictionary-scale-options").assertIsDisplayed()
    }

    @Test fun dailyPackageRemainsFreelySelectableAndSelectionClosesTheWorkbench() {
        ready(); open()
        compose.onNodeWithTag("content-source-filters").performScrollToKey("local.daily-practice")
        compose.onNodeWithTag("content-source-local.daily-practice").performClick().assertIsSelected()
        val content = ContentPresentation(SongTestLibrary.repository().reload())
        val lessons = content.phraseSections("local.daily-practice").flatMap { it.items }
        assertEquals(5, lessons.size)
        lessons.forEach { card(content.phraseCard(it).title).performScrollTo().assertIsDisplayed() }
        card(content.phraseCard(lessons.first()).title).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("content-panel").assertDoesNotExist()
        compose.waitUntil(45000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching { compose.onNodeWithTag("config-summary").assertTextContains(lessons.first().title, substring = true) }.isSuccess
        }
        open()
        compose.onNodeWithText("设为重点").performClick()
        compose.onNodeWithText("取消重点").assertIsDisplayed()
        compose.onNodeWithText("完整练习").performClick()
        compose.onNodeWithTag("content-package-management").performClick()
        compose.onNodeWithText("选择曲包").assertIsDisplayed()
    }

    @Test fun phoneLargeTextKeepsContentAndAllFourMixSlidersReachable() {
        val player = Player(compose.activity) { ManualSink() }
        try {
            compose.activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) { PracticeApp(player) }
            }
            ready(); open()
            val content = ContentPresentation(SongTestLibrary.repository().reload())
            val lesson = content.phraseSections(BASIC_CONTENT, TeachingModule.FOUNDATIONS).flatMap { it.items }.last()
            card(content.phraseCard(lesson).title).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("volume-button").performClick()
            listOf("lead-volume", "backing-volume", "music-volume", "metronome-volume").forEach {
                compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
            }
        } finally { player.release() }
    }

    @Test fun masterAndMetronomeVolumesPersistIndependently() {
        ready(); compose.onNodeWithTag("volume-button").performClick()
        compose.onNodeWithTag("music-volume").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.37f) }
        compose.onNodeWithTag("metronome-volume").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText("完成").performClick()
        val prefs = compose.activity.getSharedPreferences("practice-settings", 0)
        assertEquals(.37f, prefs.getFloat("music-volume", -1f), .001f)
        assertEquals(0f, prefs.getFloat("metronome-volume", -1f), .001f)
        compose.activityRule.scenario.recreate(); ready()
        compose.onNodeWithTag("volume-button").performClick()
        compose.onNodeWithText("音乐音量 37%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("节拍音量 0%").performScrollTo().assertIsDisplayed()
    }
}
