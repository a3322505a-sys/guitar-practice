package com.a3322505a.guitarlearning

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChordSourceRenderTest {
    @get:Rule val compose=createAndroidComposeRule<LegacyWorkbenchTestActivity>()

    @Test fun missingNeighborFactsShowUnavailableAndAllowSwipingBack() {
        val valid=Chords.named("C")
        val broken=valid.copy(id="missing-tuning",name="损坏的来源卡",openStringMidi=emptyList())
        val chords=listOf(valid,broken)
        var selected by mutableStateOf(valid)
        compose.activity.setContent { PracticeTheme {
            val sequence=if(selected.unavailableReason==null) chordSequence(selected,selected,ChordExercise.CHECK,4) else null
            PracticeContent(PracticeContentState(page=1,chordPhrases=false,tab=false,tabletControls=false,
                scale=ScaleOptions(),first=selected,phrase=Phrases.all.first(),melody=false,sequence=sequence,
                displayedSequence=sequence,scaleResult=Routes.scale(ScaleOptions()),hint=Hint.FULL,renderHint=Hint.FULL,
                active=false,phase=Phase.IDLE,renderCursor=null,cursor=null,showingSession=false,event=null,
                phraseNow=null,nextEvent=null,moving=null,current=emptyList(),shownChord=selected,nextChord="",
                status="准备好",note=selected.unavailableReason.orEmpty(),beatsPerBar=4,chords=chords),
                Modifier.fillMaxSize(),{ selected=it },{},{},{ fail("Unavailable cards cannot audition") })
        } }
        compose.onNodeWithTag("chord-deck").performTouchInput { swipeLeft() }
        compose.onNodeWithText(broken.unavailableReason!!).assertIsDisplayed()
        compose.onNodeWithText("听和弦").assertDoesNotExist()
        compose.onNodeWithTag("chord-deck").performTouchInput { swipeRight() }
        compose.onNodeWithText("C · 逐弦检查").assertIsDisplayed()
        assertEquals(valid,selected)
    }

    @Test fun everyLoadedCardSelectsSwipesAndSendsItsOwnAuditionAndPracticeFacts() {
        val player=RecordingChordPlayback()
        compose.activity.setContent { PracticeApp(player) }
        compose.waitUntil(12000) {
            runCatching { File(compose.activity.filesDir,"songs/active-index.json").readText().contains("local.yourtrip") }.getOrDefault(false)
        }
        compose.onNodeWithTag("config-summary").performClick()
        val expand=SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)
        if(compose.onAllNodes(expand).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(expand).performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.onNodeWithText("内容").performClick()
        compose.onNodeWithText("和弦学习").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        val chords=SongTestLibrary.repository().reload().chords
        chords.forEachIndexed { index,chord ->
            if(index>0) compose.onNodeWithTag("chord-deck").performTouchInput { swipeLeft() }
            compose.onNodeWithText("${chord.name} · 逐弦检查").assertIsDisplayed()
            compose.onNodeWithTag("chord-diagram").assertContentDescriptionContains("起始相对品位${chord.baseFret}",substring=true)
            compose.onNodeWithText("听和弦").performClick()
            assertEquals(chord.positions,player.auditionPositions)
            assertEquals(chord.capoFret,player.auditionCapo)
            assertEquals(chord.openStringMidi,player.auditionTuning)
            val position=chord.positions.first()
            compose.onNodeWithTag("chord-diagram").performTouchInput {
                val geometry=ChordGeometry(width.toFloat(),height.toFloat(),chord.baseFret)
                click(geometry.position(position))
            }
            assertEquals(listOf(position),player.auditionPositions)
            assertEquals(chord.capoFret,player.auditionCapo)
            assertEquals(chord.openStringMidi,player.auditionTuning)
            compose.onNodeWithText("开始跟练").performClick()
            val sequence=checkNotNull(player.started)
            assertEquals(chord.id,sequence.sections.single().sourceChord!!.id)
            assertEquals(chord.frets,sequence.chordAt(sequence.sections.single())!!.frets)
            assertEquals(chord.positions.map(chord::midi),sequence.performance.notes.map { it.note.midi })
        }
        chords.dropLast(1).reversed().forEach { chord ->
            compose.onNodeWithTag("chord-deck").performTouchInput { swipeRight() }
            compose.onNodeWithText("${chord.name} · 逐弦检查").assertIsDisplayed()
        }
        compose.onNodeWithTag("previous-chord").assertIsNotEnabled()
    }

    /** Captures the real UI requests and compiles starts; device audio is a separate boundary. */
    private class RecordingChordPlayback : PracticePlayback {
        override var guitar=true
        override var click=true
        override val progress=Progress()
        override val session: Session?=null
        override val cursor: Cursor?=null
        override val active=false
        var auditionPositions=emptyList<Position>()
        var auditionCapo=0
        var auditionTuning=emptyList<Int>()
        var started: Sequence?=null
        override fun start(sequence: Sequence,bpm: Int,follow: Boolean,repeats: Int,range: BarRange,sourceTempo: Boolean,multiplier: Double) {
            sequence.performance
            started=sequence
        }
        override fun audition(positions: List<Position>,strum: Boolean,capoFret: Int,openStringMidi: List<Int>) {
            auditionPositions=positions;auditionCapo=capoFret;auditionTuning=openStringMidi
        }
        override fun resume() {}
        override fun restart() {}
        override fun pause() {}
        override fun invalidate() { started=null }
        override fun stop() {}
        override fun release() {}
    }
}
