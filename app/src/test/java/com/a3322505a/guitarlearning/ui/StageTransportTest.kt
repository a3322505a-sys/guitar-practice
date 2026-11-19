package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.a3322505a.guitarlearning.alphatab.AlphaPlayback
import com.a3322505a.guitarlearning.alphatab.CountInPlan
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.music.score.Meter
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.practice.BarRange
import com.a3322505a.guitarlearning.audio.Phase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
class StageTransportTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun restartIs48DpBesideProgressAndFourMainControlsRemainAvailableOnPhone() {
        var restarts = 0
        val phase = mutableStateOf(Phase.PLAYING)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent {
            PracticeTheme {
                androidx.compose.foundation.layout.Box(Modifier.width(360.dp)) {
                    StageTransport(60, phase.value, true, false, false, 18.0, 50.0,
                        {}, {}, {}, {}, { restarts++ })
                }
            }
        }
        compose.onNodeWithTag("restart-button").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .assertContentDescriptionEquals("重新开始").performClick()
        assertEquals(1, restarts)
        listOf("tempo-button", "play-button", "loop-button", "metronome-button").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        compose.onNodeWithTag("entry-signal").assertDoesNotExist()
        compose.onNodeWithTag("entry-light-red").assertDoesNotExist()
        compose.onNodeWithTag("entry-light-amber").assertDoesNotExist()
        compose.onNodeWithTag("entry-light-green").assertDoesNotExist()
        compose.runOnIdle { phase.value = Phase.PREPARING }
        compose.onNodeWithTag("restart-button").assertIsNotEnabled()
        controller.pause().stop().destroy()
    }

    @Test fun countInCueReflectsIdleAllMetersPickupAndFormalEntryWithoutOccupyingTransport() {
        val cue = mutableStateOf(AlphaPlayback.CountInVisualState())
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent {
            PracticeTheme {
                StageCountInCue(cue.value)
            }
        }
        compose.onNodeWithTag("count-in-cue").assertDoesNotExist()

        // 4/4 standard bar
        val plan44 = CountInPlan.forMeter(Meter(4, 4, listOf(1, 1, 1, 1)))
        compose.runOnIdle { cue.value = AlphaPlayback.CountInVisualState(active = true, plan = plan44, currentClick = 2) }
        compose.onNodeWithTag("count-in-cue").assertIsDisplayed().assertContentDescriptionEquals("预备拍 2/4")
        compose.onNodeWithText("预备拍").assertIsDisplayed()
        (1..4).forEach { slot -> compose.onNodeWithTag("count-in-slot-$slot").assertIsDisplayed() }
        compose.onNodeWithTag("count-in-slot-5").assertDoesNotExist()

        // 3/4 standard bar
        val plan34 = CountInPlan.forMeter(Meter(3, 4, listOf(1, 1, 1)))
        compose.runOnIdle { cue.value = AlphaPlayback.CountInVisualState(active = true, plan = plan34, currentClick = 1) }
        compose.onNodeWithTag("count-in-cue").assertContentDescriptionEquals("预备拍 1/3")
        (1..3).forEach { slot -> compose.onNodeWithTag("count-in-slot-$slot").assertIsDisplayed() }
        compose.onNodeWithTag("count-in-slot-4").assertDoesNotExist()

        // 2/4 two-bar preparation
        val plan24 = CountInPlan.forMeter(Meter(2, 4, listOf(1, 1)))
        compose.runOnIdle { cue.value = AlphaPlayback.CountInVisualState(active = true, plan = plan24, currentClick = 1) }
        compose.onNodeWithText("预备 1/2").assertIsDisplayed()
        compose.runOnIdle { cue.value = AlphaPlayback.CountInVisualState(active = true, plan = plan24, currentClick = 3) }
        compose.onNodeWithText("预备 2/2").assertIsDisplayed()
        compose.onNodeWithTag("count-in-slot-2").assertIsDisplayed()
        compose.onNodeWithTag("count-in-slot-3").assertDoesNotExist()

        // 6/8 compound [3,3] grouping
        val plan68 = CountInPlan.forMeter(Meter(6, 8, listOf(3, 3)))
        compose.runOnIdle { cue.value = AlphaPlayback.CountInVisualState(active = true, plan = plan68, currentClick = 4) }
        compose.onNodeWithTag("count-in-cue").assertContentDescriptionEquals("预备拍 4/6")
        (1..6).forEach { slot -> compose.onNodeWithTag("count-in-slot-$slot").assertIsDisplayed() }
        compose.onNodeWithTag("count-in-group-gap-4").assertExists()

        // 4/4 pickup (missing first 2 beats -> entry at beat 3)
        val planPickup = CountInPlan.forMeter(Meter(4, 4, listOf(1, 1, 1, 1)), Q(2))
        compose.runOnIdle { cue.value = AlphaPlayback.CountInVisualState(active = true, plan = planPickup, currentClick = 5) }
        compose.onNodeWithTag("count-in-cue").assertContentDescriptionEquals("弱起引拍 5/6，第3拍进")
        compose.onNodeWithText("弱起 · 第3拍进").assertIsDisplayed()
        compose.onNodeWithText("进").assertIsDisplayed()

        // Formal entry flash
        compose.runOnIdle { cue.value = AlphaPlayback.CountInVisualState(active = false, plan = plan44, currentClick = 4, entryFlash = true) }
        compose.onNodeWithTag("count-in-cue").assertIsDisplayed().assertContentDescriptionEquals("正式进拍")
        compose.onNodeWithText("进拍").assertIsDisplayed()

        compose.runOnIdle { cue.value = AlphaPlayback.CountInVisualState() }
        compose.onNodeWithTag("count-in-cue").assertDoesNotExist()
        controller.pause().stop().destroy()
    }
    @Config(sdk = [28], qualifiers = "w800dp-h1200dp")
    @Test fun openStringsAndFirstFretHaveSeparateAuditionTargetsAcrossThemesAndWindows() {
        val theme = mutableStateOf(ThemeRegistry.default)
        val window = mutableStateOf(1..7)
        val boardWidth = mutableStateOf(360.dp)
        val expanded = mutableStateOf(true)
        val positions = listOf(Position(1, 0), Position(1, 1), Position(1, 5))
        val tapped = mutableListOf<Position>()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent {
            PracticeTheme(theme = theme.value) {
                androidx.compose.foundation.layout.Column(Modifier.width(boardWidth.value)) {
                    StageFretboard(null, null, Phase.IDLE, emptyList(), BarRange(0, 0), expanded.value,
                        { expanded.value = !expanded.value }, 48.dp, {}, true, false)
                    Fretboard(positions, null, Hint.FULL, false, listOf(Position(1, 5)), true,
                        onTap = { tapped += it }, fretWindow = window.value)
                }
            }
        }
        compose.onNodeWithText("当前音位").assertDoesNotExist()
        compose.onNodeWithText("度数").assertDoesNotExist()
        compose.onNodeWithText("指板").assertIsDisplayed()
        compose.onNodeWithTag("fold-fretboard").performClick()
        compose.onNodeWithTag("fold-fretboard").assertContentDescriptionEquals("展开指板")
        compose.onNodeWithTag("fold-fretboard").performClick()
        ThemeRegistry.all.forEach { skin ->
            for (first in listOf(1, 5)) {
                compose.runOnIdle { theme.value = skin; window.value = first..(first + 6); boardWidth.value = if (first == 1) 360.dp else 700.dp }
                val board = compose.onNodeWithTag("fretboard")
                board.assertIsDisplayed()
                board.performTouchInput {
                    val unit = width / (if (first == 1) 360f else 700f)
                    click(Offset(64f * unit, 40f * unit)) // independent 0-fret region
                    val gridLeft = if (first == 1) 82f else 94f
                    val cell = ((if (first == 1) 360f else 700f) - gridLeft - 10f) / 7
                    click(Offset((gridLeft + cell / 2) * unit, 40f * unit))
                }
                assertEquals(listOf(Position(1, 0), Position(1, first)), tapped.takeLast(2))
            }
        }
        controller.pause().stop().destroy()
    }

}
