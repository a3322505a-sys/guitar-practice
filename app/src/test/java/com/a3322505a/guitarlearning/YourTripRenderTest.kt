package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class YourTripRenderTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun capture(name: String,dialog: Boolean=false) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view=if(dialog) {
                val clazz=Class.forName("android.view.WindowManagerGlobal")
                val global=clazz.getDeclaredMethod("getInstance").invoke(null)
                @Suppress("UNCHECKED_CAST")
                val views=clazz.getDeclaredField("mViews").apply { isAccessible=true }.get(global) as List<android.view.View>
                views.last()
            } else compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("../.local/your-trip-v72/$name.png").apply { parentFile!!.mkdirs();outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        }
    }
    @Test fun allSourceSystemsRenderWithCapoLegatoTripletsAndOriginalBarNumbers() {
        YourTrip.phrases.forEach { phrase ->
            val seq=phrase.sequence
            compose.activity.setContent { PracticeTheme { Tablature(seq,Hint.FULL,null,null,false) } }
            compose.waitForIdle()
            val rows=tabSystems(seq.tabBars(),1280)
            for(i in rows.indices step 3) {
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(i)
                compose.waitForIdle()
                val first=rows[i].first().index+seq.originalBar+1
                compose.onAllNodesWithContentDescription("六线谱，第 $first 至",substring=true).onFirst().assertExists()
                compose.onAllNodesWithContentDescription("变调夹2品",substring=true).onFirst().assertExists()
                capture("${phrase.id}-row-$i")
            }
        }
    }
    @Test fun curatedMenuSelectsCompleteExercisesAndShowsTheBoundaryExplanation() {
        val player=Player(compose.activity)
        try {
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("和弦").performClick()
            compose.onNodeWithText("短句应用").performClick()
            compose.onNodeWithTag("config-summary").performClick()
            YourTrip.phrases.forEach { phrase ->
                compose.onNodeWithText(phrase.title).performScrollTo().performClick()
                val seq=phrase.sequence
                compose.onNodeWithTag("phrase-range").performScrollTo().assertTextEquals("当前范围：${seq.originalBar+1}–${seq.originalBar+seq.barCount} 小节；完整经过范围一次计一轮。")
            }
            compose.onNodeWithText("Your Trip · 节奏与伴奏").performScrollTo()
            capture("menu",true)
            compose.onNodeWithText("Your Trip · 连续击勾弦推进").performScrollTo().performClick()
            compose.onNodeWithTag("phrase-range").performScrollTo()
            capture("legato-selection",true)
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("听示范").performClick()
            assertEquals(BarRange(0,7),player.session!!.range)
            assertEquals(384,player.session!!.practiceLength)
            player.stop()
        } finally { player.release() }
    }
}
