package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.practice.LegacyPhraseFixtures as Phrases

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.assets.song.*
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        compose.waitForIdle()
        compose.waitUntil(45000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(12000) {
            compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("config-summary").performClick()
        val expand=SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)
        if(compose.onAllNodes(expand).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(expand).performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.onNode(hasText("和弦教学") and hasClickAction()).performScrollTo().performClick()
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

    @Test fun newlyImportedPackageKeepsVoicingsThroughSelectionAuditionStartAndHarmonyChanges() {
        compose.waitUntil(15000) { File(compose.activity.filesDir, "songs/active-index.json").exists() }
        // A new external identity, using reviewed musical bytes unchanged, enters the actual import path.
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            ZipInputStream(File("../scripts/archive/teaching-v31/packages/usotsuki-v1.gpractice").inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = input.readBytes()
                    val data = if (entry.name == "manifest.json") {
                        val manifest = StrictJson.parse(bytes).objectValue()
                        StrictJson.encode(manifest + mapOf("packageId" to "example.teaching",
                            "display" to (manifest["display"].objectValue() + ("title" to "新载入验收"))))
                    } else bytes
                    zip.putNextEntry(ZipEntry(entry.name)); zip.write(data); zip.closeEntry()
                }
            }
        }
        val repo = SongTestLibrary.repository(File(compose.activity.filesDir, "songs"))
        repo.reload()
        val snapshot = repo.commit(repo.prepare(ByteArrayInputStream(output.toByteArray())))
        val content = ContentPresentation(snapshot)
        val imported = content.chordSections("example.teaching").flatMap { it.items }
        assertEquals(4, imported.size)
        assertTrue(imported.all { encodedPackageId(it.id) == "example.teaching" })
        val player = RecordingChordPlayback()
        compose.activity.setContent { PracticeApp(player) }
        compose.waitForIdle()
        compose.waitUntil(45000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("config-summary").performClick()
        val expand = SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)
        if (compose.onAllNodes(expand).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(expand).performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.onNode(hasText("和弦教学") and hasClickAction()).performClick()
        compose.waitUntil(15000) { runCatching {
            compose.onNodeWithTag("content-source-filters").performScrollToKey("example.teaching")
        }.isSuccess }
        compose.onNodeWithTag("content-source-example.teaching").performClick()
        val selected = imported.last()
        compose.onNodeWithText(content.chordText(selected).primary).performClick()
        compose.onNodeWithTag("chord-diagram").assertContentDescriptionContains(selected.name, substring = true)
        compose.onNodeWithText("听和弦").performClick()
        assertEquals(selected.positions, player.auditionPositions)
        compose.onNodeWithText("开始跟练").performClick()
        assertEquals(selected.id, player.started!!.sections.single().sourceChord!!.id)
        assertEquals(selected.frets, player.started!!.chordAt(player.started!!.sections.single())!!.frets)
        compose.onNodeWithTag("config-summary").performClick()
        if (compose.onAllNodes(expand).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(expand).performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.onNode(hasText("综合应用") and hasClickAction()).performClick()
        compose.onNodeWithTag("content-source-example.teaching").assertIsSelected()
        val phrase = content.phraseSections("example.teaching").flatMap { it.items }.first()
        compose.onNodeWithText(content.phraseText(phrase).primary).performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("chord-diagram").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("开始跟练").performClick()
        val sequence = player.started!!
        assertEquals(phrase.id, repo.resolve(phrase.id).id)
        assertEquals(4, sequence.sections.size)
        compose.runOnIdle { player.testSession = Session(sequence, 60, false, 1); player.testProgress = Progress(Phase.PAUSED) }
        sequence.sections.forEach { section ->
            val chord = sequence.chordAt(section)!!
            val source = imported.single { it.id.endsWith(section.voicingId!!) }
            assertEquals(source.frets, chord.frets)
            assertEquals(source.fingers, chord.fingers)
            compose.runOnIdle { player.testCursor = Cursor(false, 0, section.tick.toDouble(), sequence.barAt(section.tick.toDouble()), 0, positions = chord.positions) }
            compose.onNodeWithTag("chord-diagram").assertContentDescriptionContains(chord.name, substring = true)
        }
    }

    /** Captures the real UI requests and compiles starts; device audio is a separate boundary. */
    private class RecordingChordPlayback : PracticePlayback {
        override var guitar=true
        override var click=true
        var testProgress by mutableStateOf(Progress())
        var testSession by mutableStateOf<Session?>(null)
        var testCursor by mutableStateOf<Cursor?>(null)
        override val progress get() = testProgress
        override val session get() = testSession
        override val cursor get() = testCursor
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
