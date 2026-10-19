package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.practice.LegacyPhraseFixtures as Phrases

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
    private val f = Chord("F", 5, 9, listOf(1,3,3,2,1,1), listOf(1,3,4,2,1,1),barres=listOf(Barre(1,1,6,1)))

    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("../.local/teaching-ui/$name.png").apply {
            parentFile!!.mkdirs(); outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
    }

    @Test fun fullRootsHiddenKeepSameGeometryAndAuditionTargets() {
        var hint by mutableStateOf(Hint.FULL)
        var tapped: Position? = null
        val fixtures = listOf(f,
            f.copy(name = "Bm", frets = listOf(null,2,4,4,3,2), fingers = listOf(0,1,3,4,2,1),barres=listOf(Barre(1,2,5,1))),
            f.copy(name = "mini F", frets = listOf(null,null,3,2,1,1), fingers = listOf(0,0,3,2,1,1),barres=listOf(Barre(1,1,2,1))),
            f.copy(name = "high F", frets = f.frets.map { it!! + 12 },barres=listOf(Barre(1,13,6,1))))
        compose.activity.setContent { PracticeTheme {
            Row(Modifier.fillMaxWidth()) { fixtures.forEach { chord ->
                Box(Modifier.weight(1f)) { ChordDiagram(chord, hint, emptyList(), true, 390.dp) { tapped = it } }
            } }
        } }
        val full = compose.onAllNodesWithTag("chord-diagram").fetchSemanticsNodes().map { it.boundsInRoot }
        fixtures.forEachIndexed { i, chord ->
            val segment = deriveBarreSegments(chord).single()
            compose.onAllNodesWithTag("chord-diagram")[i].assertContentDescriptionContains(segment.description, substring = true)
            compose.onAllNodesWithTag("chord-diagram")[i].performTouchInput {
                click(ChordGeometry(width.toFloat(), height.toFloat(), chord.baseFret).position(chord.positions.first()))
            }
            assertEquals(chord.positions.first(), tapped)
        }
        capture("tablet-full-barres")
        compose.runOnIdle { hint = Hint.ROOTS }
        assertEquals(full, compose.onAllNodesWithTag("chord-diagram").fetchSemanticsNodes().map { it.boundsInRoot })
        compose.onAllNodesWithContentDescription("横按", substring = true).assertCountEquals(0)
        capture("tablet-roots-barres")
        compose.runOnIdle { hint = Hint.HIDDEN }
        assertEquals(full, compose.onAllNodesWithTag("chord-diagram").fetchSemanticsNodes().map { it.boundsInRoot })
        compose.onAllNodesWithContentDescription("和弦图，提示已隐藏").assertCountEquals(4)
        capture("tablet-hidden-barres")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-mdpi") fun narrowLargeFontLegendUsesWhitespaceWithoutTakingCanvasHeight() {
        RuntimeEnvironment.setFontScale(1.5f)
        try {
            compose.activity.setContent { PracticeTheme {
                Box(Modifier.width(240.dp)) { ChordDiagram(f, Hint.FULL, emptyList(), true, 390.dp) {} }
            } }
            val diagram = compose.onNodeWithTag("chord-diagram").fetchSemanticsNode()
            assertEquals(390f, diagram.boundsInRoot.height, .5f)
            compose.onNodeWithText("指法：1食 · 2中 · 3无 · 4小", useUnmergedTree = true).assertIsDisplayed()
            capture("narrow-large-barre")
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    @Test fun linkedBoardOrdinaryMelodyAndExplicitTechniquesHaveDistinctSemantics() {
        var sequence by mutableStateOf(Sequence(listOf(7,5,4,5,4).mapIndexed { i, fret -> Event(i*12,12,listOf(Position(3,fret))) },96,"plain"))
        var hint by mutableStateOf(Hint.FULL)
        compose.activity.setContent { PracticeTheme {
            LinkedFretboardDock(sequence, null, Phase.IDLE, emptyList(), BarRange(0, sequence.barCount-1), hint, {}, {}, Modifier.height(250.dp))
        } }
        compose.onNodeWithTag("fretboard").assertContentDescriptionContains("下一 3 弦 5 品", substring = true)
        compose.onAllNodesWithContentDescription("滑音", substring = true).assertCountEquals(0)
        capture("plain-next-note-ring")
        listOf("SL01" to "滑音 5/7", "H01" to "击弦 5h7", "P01" to "勾弦 7p5").forEach { (id, mark) ->
            compose.runOnIdle { sequence = Phrases.named(id).sequence }
            compose.onNodeWithTag("fretboard").assertContentDescriptionContains(mark, substring = true)
            capture("technique-$id")
        }
        compose.runOnIdle { hint = Hint.HIDDEN }
        compose.onNodeWithTag("fretboard").assertContentDescriptionEquals("指板，提示已隐藏")
        capture("hidden-technique")
    }
}
