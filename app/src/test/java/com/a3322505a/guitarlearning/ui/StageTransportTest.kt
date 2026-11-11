package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.a3322505a.guitarlearning.music.Position
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
        val light = mutableIntStateOf(0)
        val counted = mutableIntStateOf(0)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent {
            PracticeTheme {
                androidx.compose.foundation.layout.Box(Modifier.width(360.dp)) {
                    StageTransport(60, phase.value, true, false, false, 18.0, 50.0,
                        {}, {}, {}, {}, { restarts++ }, light.intValue, counted.intValue, 4)
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
        compose.onNodeWithTag("entry-signal").assertContentDescriptionEquals("入拍提示待机")
        compose.onNodeWithTag("entry-light-red").assertExists()
        compose.onNodeWithTag("entry-light-amber").assertExists()
        compose.onNodeWithTag("entry-light-green").assertExists()
        compose.runOnIdle { light.intValue = 1; counted.intValue = 1 }
        compose.onNodeWithTag("entry-signal").assertContentDescriptionEquals("预备拍 1/4")
        compose.runOnIdle { light.intValue = 2; counted.intValue = 4 }
        compose.onNodeWithTag("entry-signal").assertContentDescriptionEquals("预备拍最后一拍 4/4")
        compose.runOnIdle { light.intValue = 3 }
        compose.onNodeWithTag("entry-signal").assertContentDescriptionEquals("正式第一拍")
        compose.runOnIdle { phase.value = Phase.PREPARING }
        compose.onNodeWithTag("restart-button").assertIsNotEnabled()
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
