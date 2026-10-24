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
}
