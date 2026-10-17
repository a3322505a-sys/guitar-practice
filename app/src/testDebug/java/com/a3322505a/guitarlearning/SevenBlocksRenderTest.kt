package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.a3322505a.guitarlearning.audio.Player
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SevenBlocksRenderTest {
    @get:Rule val compose=createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    private fun capture(name: String) {
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("../.local/seven-blocks-v74/$name.png").apply { parentFile!!.mkdirs();outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        }
    }
    @Test fun all38WrittenBarsRenderWithThreeBeamsTripletsDeadNotesAndOriginalNumbers() {
        SevenBlocks.phrases.forEach { phrase ->
            val seq=phrase.sequence
            seq.sourceBars.distinct().forEach { number ->
                val bar=seq.sourceBars.indexOf(number)
                val first=seq.barStart(bar)
                val end=seq.barEnd(bar)
                // Isolate each written bar for visual review without inherited lazy-list
                // scroll/clipping from the preceding block's unfolded performance route.
                val written=seq.copy(events=seq.events.filter { it.tick in first until end }.map { it.copy(tick=it.tick-first) },
                    length=end-first,beatsPerBar=seq.meter(bar),barMeters=listOf(seq.meter(bar)),sourceBars=listOf(number),
                    originalBar=number-1,tuplets=seq.tuplets.filter { it.tick in first until end }.map { it.copy(tick=it.tick-first) })
                compose.activity.setContent { PracticeTheme { Tablature(written,Hint.FULL,null,null,false) } }
                compose.waitForIdle()
                compose.onAllNodesWithContentDescription("六线谱，第 $number 至 $number",substring=true).onFirst().assertExists()
                capture("${phrase.id}-source-$number")
            }
        }
    }
    @Test fun sevenCompleteBlocksCanBeSelectedAndTheirExpandedRangeReachesThePlayer() {
        val player=Player(compose.activity)
        try {
            compose.waitUntil(15000) { Shadows.shadowOf(Looper.getMainLooper()).idle();
                runCatching { File(compose.activity.filesDir,"songs/active-index.json").readText().contains("local.yourtrip") }.getOrDefault(false) }
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithTag("config-summary").performClick()
            compose.onNodeWithText("歌曲与短句").performClick();compose.onNodeWithText("完成").performClick()
            SevenBlocks.phrases.forEach { phrase ->
                compose.onNodeWithTag("config-summary").performClick()
                compose.onNodeWithText(phrase.title).performScrollTo().performClick()
                compose.onNodeWithTag("practice-settings").assertDoesNotExist()
                compose.onNodeWithTag("config-summary").assertTextContains(phrase.title,substring=true)
            }
            compose.onNodeWithText("听示范").performClick()
            assertEquals(SevenBlocks.phrases.last().sequence,player.session!!.sequence)
            assertEquals(2304,player.session!!.practiceLength)
            assertEquals(1,player.session!!.repeats)
        } finally { player.release() }
    }
}
