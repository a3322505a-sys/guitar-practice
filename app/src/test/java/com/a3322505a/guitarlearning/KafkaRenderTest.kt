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
class KafkaRenderTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val out=File("../.local/kafka-v70/$name.png");out.parentFile!!.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun sourceTabsDrawGraceBendAndMixedMeterWithoutSplittingThePhrase() {
        for(id in listOf("KAF-B-9-G3","KAF-H-77-G2","KAF-I-82-G3","KAF-K-117-G1")) {
            val seq=Phrases.named(id).sequence
            compose.activity.setContent { PracticeTheme { Tablature(seq,Hint.FULL,null,null,false) } }
            compose.waitForIdle()
            compose.onAllNodesWithContentDescription(seq.swing.title,substring=true).onFirst().assertIsDisplayed()
            if(id=="KAF-B-9-G3") compose.onNodeWithContentDescription("装饰15/",substring=true).assertExists()
            if(id=="KAF-I-82-G3") compose.onAllNodesWithContentDescription("推弦／保持",substring=true).onFirst().assertExists()
            capture("$id-tab")
        }
        val mixed=Phrases.named("KAF-H-77-G2").sequence
        compose.activity.setContent { PracticeTheme { Tablature(mixed,Hint.FULL,null,Cursor(false,0,204.0,4,1),true) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("2/4",substring=true).assertExists()
        capture("mixed-meter-ending")
    }
    @Test fun fullPhraseSelectionAndHighChordTapUseRegisteredSourcePitch() {
        val player=Player(compose.activity)
        try {
            Shadows.shadowOf(compose.activity.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("和弦").performClick()
            compose.onNodeWithTag("config-summary").performClick()
            val high=KafkaChords.all.first { it.soundingName=="C7(no3,no5)/B♭" }
            compose.onNodeWithText(high.name).performScrollTo().performClick()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("chord-diagram").performTouchInput {
                click(ChordGeometry(width.toFloat(),height.toFloat(),high.baseFret).position(Position(3,15)))
            }
            assertEquals(listOf(Position(3,15)),player.session!!.sequence.events.single().positions)
            assertEquals(70,player.session!!.sequence.midi(Position(3,15)))
            compose.onNodeWithText("结束").performClick()
            compose.onNodeWithText("听和弦").performClick()
            assertEquals(high.positions,player.session!!.sequence.events.single().positions)
            compose.onNodeWithText("结束").performClick()
            compose.onNodeWithText("听示范").performClick()
            assertEquals(high.positions,player.session!!.sequence.events.flatMap { it.positions })
            compose.onNodeWithText("结束").performClick()
            capture("high-chord")
            compose.onNodeWithText("短句应用").performClick()
            compose.onNodeWithTag("config-summary").performClick()
            val phrase=Phrases.named("KAF-H-77-G2")
            compose.onNodeWithText(phrase.title).performScrollTo().performClick()
            compose.onNodeWithTag("phrase-range").performScrollTo().assertTextEquals("当前范围：77–81 小节；完整经过范围一次计一轮。")
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("开始跟练").performClick()
            assertEquals(BarRange(0,4),player.session!!.range)
            assertEquals(216,player.session!!.practiceLength)
            player.stop()
            capture("whole-phrase")
        } finally { player.release() }
    }
}
