package com.a3322505a.guitarlearning

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
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
class WorkbenchRenderTest {
    @get:Rule val compose = createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    private fun ready() = compose.waitUntil(45000) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun scrimDismissesTheModalAndTheStageAnchorsRemainSeparate() {
        ready()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithTag("content-panel").assertIsDisplayed()
        compose.onNodeWithTag("tab-chords").assertExists()
        compose.onNodeWithTag("tab-scales").assertExists()
        compose.onNodeWithTag("modal-scrim").performTouchInput { click(Offset(10f, 10f)) }
        compose.onNodeWithTag("content-panel").assertDoesNotExist()
        compose.onNodeWithTag("preferences-button").performClick()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithTag("theme-forest_daylight").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("quick-dictionary").assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w1024dp-h768dp-mdpi")
    fun tabletDictionaryKeepsItsPreviewWhileTheFilterListScrolls() {
        ready(); compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithTag("chord-dictionary").performClick()
        val before = compose.onNodeWithTag("dictionary-results").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("dictionary-quality-options").performScrollToNode(hasText("强力与八度"))
        val after = compose.onNodeWithTag("dictionary-results").fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.onNodeWithText("使用此配法").assertIsDisplayed()
    }
    @Test fun transportKeepsFortyEightDpTargetsAndNonOverlappingPrimaryControlsAtLargeText() {
        var mode by mutableStateOf(TransportMode.IDLE)
        val actions = mutableListOf<String>()
        compose.activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                PracticeTheme {
                    Column {
                        PracticeTransportDock(82, 1.0, "4/4", mode, true, false, "小节 1–8",
                            { actions += "tempo" }, { actions += "loop" }, { actions += "range" }, { actions += "mix" },
                            { actions += "pause" }, { actions += "restart" }, { actions += "demo" },
                            { actions += "follow" }, { actions += "stop" }, { Text("练习策略") }, "继续")
                    }
                }
            }
        }
        TransportMode.entries.forEach { value ->
            compose.runOnIdle { mode = value }
            val controls = listOf("tempo-button", "loop-button", "volume-button").map {
                compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            controls.forEach { assertTrue(it.width >= 48 && it.height >= 48) }
            controls.forEachIndexed { i, a -> controls.drop(i + 1).forEach { b -> assertFalse(a.overlaps(b)) } }
            val dock = compose.onNodeWithTag("transport-bar").fetchSemanticsNode().boundsInRoot
            assertTrue(dock.width <= 360f)
            assertTrue(dock.height < 210f)
        }
        compose.runOnIdle { mode = TransportMode.IDLE }
        compose.onNodeWithText("听示范").performClick()
        compose.onNodeWithText("开始跟练").performClick()
        compose.onNodeWithTag("loop-button").performClick()
        assertEquals(listOf("demo", "follow", "loop"), actions)
    }
    @Test fun speedStepsAndPresetsUpdateTheRealCallbacks() {
        var bpm by mutableIntStateOf(80)
        var multiplier by mutableDoubleStateOf(1.0)
        var open by mutableStateOf(true)
        compose.activity.setContent {
            PracticeTheme {
                if (open) SpeedAndTempoSheet(bpm, multiplier, 30, 180, "固定 BPM", { bpm = it }, { multiplier = it }, { open = false })
            }
        }
        compose.onNodeWithTag("tempo-step-10").performScrollTo().performClick()
        assertEquals(90, bpm)
        compose.onNodeWithText("0.75x").performScrollTo().performClick()
        assertEquals(.75, multiplier, .0001)
        compose.onNodeWithText("收起").performClick()
        compose.onNodeWithTag("tempo-popup").assertDoesNotExist()
    }
}
