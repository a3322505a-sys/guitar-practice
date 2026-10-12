package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
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
class ShayouChordRenderTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun select(chord: Chord) {
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText(chord.name).performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val out=File("../.local/shayou-v69/$name.png")
            out.parentFile!!.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun newChordsAppearInLearningWithActualNamesAndAuditionUsesCapoThree() {
        val player=Player(compose.activity)
        try {
            Shadows.shadowOf(compose.activity.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("和弦").performClick()
            listOf(0,7,9,14,15).forEach { index ->
                val chord=ShayouChords.all[index]
                select(chord)
                compose.onNodeWithTag("chord-conditions").assertTextEquals(chord.conditions)
                compose.onNodeWithContentDescription("起始相对品位${chord.baseFret}",substring=true).assertIsDisplayed()
                compose.onNodeWithText("听和弦").performClick()
                assertEquals(chord.positions,player.session!!.sequence.events.single().positions)
                assertEquals(3,player.session!!.sequence.capoFret)
                compose.onNodeWithText("结束").performClick()
                compose.onNodeWithText("听示范").performClick()
                assertEquals(chord.positions,player.session!!.sequence.events.flatMap { it.positions })
                assertEquals(3,player.session!!.sequence.capoFret)
                compose.onNodeWithText("结束").performClick()
                capture("chord-$index")
            }
            compose.onNodeWithText("短句应用").performClick()
            assertEquals(5,Phrases.shayou.size)
        } finally { player.release() }
    }
    @Test fun highFretTapAndRootOnlyHintsUseActualPitchInBothDiagramAndTab() {
        val player=Player(compose.activity)
        try {
            Shadows.shadowOf(compose.activity.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("和弦").performClick()
            val chord=ShayouChords.all[14]
            select(chord)
            compose.onNodeWithTag("chord-diagram").performTouchInput {
                click(ChordGeometry(width.toFloat(),height.toFloat(),chord.baseFret).position(Position(1,14)))
            }
            assertEquals(listOf(Position(1,14)),player.session!!.sequence.events.single().positions)
            assertEquals(3,player.session!!.sequence.capoFret)
            compose.onNodeWithText("结束").performClick()
            compose.activity.setContent { PracticeTheme { ChordDiagram(chord,Hint.ROOTS,emptyList(),false) {} } }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("1 弦 14 品，A5 根音",substring=true).assertExists()
            compose.onNodeWithContentDescription("2 弦 14 品",substring=true).assertDoesNotExist()
            capture("high-roots")
            compose.activity.setContent { PracticeTheme { Tablature(chordSequence(chord,chord,ChordExercise.CHECK,4),Hint.ROOTS,null,null,false) } }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("1 弦 14 品",substring=true).assertExists()
            compose.onNodeWithContentDescription("2 弦 14 品",substring=true).assertDoesNotExist()
            capture("high-roots-tab")
        } finally { player.release() }
    }
}
