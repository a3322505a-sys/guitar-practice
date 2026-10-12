package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class ShayouRenderTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun fivePassagesEngraveOriginalBarsCapoTripletsAndFullLengthOnTablet() {
        Phrases.shayou.forEach { phrase ->
            val seq=phrase.sequence
            compose.activity.setContent { PracticeTheme { Tablature(seq,Hint.FULL,null,null,false) } }
            compose.waitForIdle()
            compose.onAllNodesWithContentDescription("变调夹3品，数字为相对品位",substring=true).onFirst().assertIsDisplayed()
            if(phrase.id=="SHA-VERSE") compose.onNodeWithContentDescription("三连音 3",substring=true).assertExists()
            val path=File("../.local/shayou-v68/${phrase.id}-tab.png")
            path.parentFile!!.mkdirs()
            compose.runOnIdle {
                val view=compose.activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        }
    }
    @Test fun fiveCompleteChoicesAreRegisteredTogetherAndSelectionKeepsWholeRange() {
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("短句应用").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("斜阳 · 5类完整乐句").performScrollTo().assertIsDisplayed()
        Phrases.shayou.forEach { phrase ->
            compose.onNodeWithText(phrase.title).performScrollTo().performClick()
            compose.onNodeWithText("当前范围：${phrase.sequence.originalBar+1}–${phrase.sequence.originalBar+phrase.sequence.length/48} 小节；完整经过范围一次计一轮。").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("phrase-cue").assertTextContains("夹3品",substring=true)
    }
}
