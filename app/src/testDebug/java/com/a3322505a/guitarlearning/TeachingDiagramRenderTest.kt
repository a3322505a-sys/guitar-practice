package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.practice.Phrases as Phrases

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w1024dp-h768dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TeachingDiagramRenderTest {
    @get:Rule val compose = createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("../.local/teaching-ui/$name.png").apply {
            parentFile!!.mkdirs(); outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
    }

    @Test fun linkedBoardOrdinaryMelodyAndExplicitTechniquesHaveDistinctSemantics() {
        var sequence by mutableStateOf(Sequence(listOf(7,5,4,5,4).mapIndexed { i, fret -> Event(i*12,12,listOf(Position(3,fret))) },96,"plain"))
        compose.activity.setContent { PracticeTheme {
            LinkedFretboardDock(sequence, null, Phase.IDLE, emptyList(), BarRange(0, sequence.barCount-1), {}, Modifier.height(180.dp))
        } }
        compose.onNodeWithTag("fretboard").assertContentDescriptionContains("下一 3 弦 5 品", substring = true)
        compose.onAllNodesWithContentDescription("滑音", substring = true).assertCountEquals(0)
        capture("plain-next-note-ring")
        listOf(Technique.SLIDE to "滑音 5/7", Technique.HAMMER to "击弦 5h7", Technique.PULL to "勾弦 9p7").forEach { (id, mark) ->
            compose.runOnIdle { sequence = NotationFixtures.legato(id) }
            compose.onNodeWithTag("fretboard").assertContentDescriptionContains(mark, substring = true)
            capture("technique-$id")
        }
    }
}
