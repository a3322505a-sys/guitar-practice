package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.a3322505a.guitarlearning.assets.song.LibraryPractice
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.Position
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
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnifiedStageRenderTest {
    @get:Rule val compose = createAndroidComposeRule<UiTestActivity>()
    private val player = RecordingPlayback()
    private fun start(fontScale: Float = 1f) {
        compose.activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                PracticeWorkbench(player, null)
            }
        }
        compose.waitUntil(45000) {
            compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun selectDaily(): LibraryPractice {
        val practice = packageCards(SongTestLibrary.repository.snapshot)
            .single { it.id == "local.daily-practice" }.practices.first()
        compose.onNodeWithTag("package-local.daily-practice").performScrollTo().performClick()
        compose.onNodeWithTag("practice-${practice.key.encoded}").performScrollTo().performClick()
        compose.waitUntil(45000) {
            runCatching { compose.onNodeWithTag("play-button").assertIsEnabled() }.isSuccess
        }
        return practice
    }
    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("../.local/unified-stage/$name.png").apply {
            parentFile!!.mkdirs(); outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
    }
    private fun assertFourControls() {
        val bounds = listOf("tempo-button", "play-button", "loop-button", "metronome-button").map {
            compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach { assertTrue(it.toString(), it.width >= 48f && it.height >= 48f) }
        assertEquals(1, bounds.map { it.top }.distinct().size)
        bounds.zipWithNext().forEach { (a, b) -> assertTrue(a.right <= b.left) }
    }
    @Test fun packageSelectionTitleMenuAndBackUseTheSamePracticeIdentities() {
        start(); selectDaily()
        compose.onNodeWithTag("package-library").assertDoesNotExist()
        compose.onNodeWithTag("practice-title").performClick()
        val other = packageCards(SongTestLibrary.repository.snapshot).single { it.id == "local.daily-practice" }.practices[1]
        compose.onNodeWithTag("switch-${other.key.encoded}").performScrollTo().performClick()
        compose.waitUntil(45000) { runCatching { compose.onNodeWithTag("practice-title").assertTextContains(other.title, substring = true) }.isSuccess }
        compose.onNodeWithTag("back-to-library").performClick()
        compose.onNodeWithTag("package-library").assertIsDisplayed()
    }
    @Test fun bpmPlayPauseLoopAndMetronomeReachTheExistingPlaybackContract() {
        start(); selectDaily(); assertFourControls()
        compose.onNodeWithTag("tempo-button").performClick()
        compose.onNodeWithTag("tempo-step-5").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("loop-button").performClick()
        compose.onNodeWithTag("metronome-button").performClick()
        compose.onNodeWithTag("play-button").performClick()
        compose.runOnIdle {
            assertNotNull(player.session)
            assertTrue(player.session!!.follow)
            assertEquals(0, player.session!!.repeats)
            val session = player.session!!
            val effectiveBpm = if (session.sourceTempo) (TempoControl.sourceBpm(session.sequence) * session.tempoMultiplier).roundToInt() else session.bpm
            assertEquals(87, effectiveBpm)
            assertEquals(BarRange(0, player.session!!.sequence.barCount - 1), player.session!!.range)
            assertFalse(player.click)
        }
        compose.onNodeWithTag("play-button").performClick()
        compose.runOnIdle { assertEquals(Phase.PAUSED, player.progress.phase) }
        compose.onNodeWithTag("play-button").performClick()
        compose.runOnIdle { assertEquals(Phase.PLAYING, player.progress.phase) }
    }
    @Test fun phoneLargeTextKeepsTheScoreLargestAndControlsOnOneRow() {
        start(1.5f); selectDaily(); assertFourControls()
        val board = compose.onNodeWithTag("stage-fretboard").fetchSemanticsNode().boundsInRoot
        val score = compose.onNodeWithTag("score-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue(score.top >= board.bottom)
        assertTrue(score.height > board.height)
        compose.onNodeWithTag("back-to-library").assertContentDescriptionEquals("返回曲包库")
        capture("phone-360-large-text")
    }
    @Test fun cursorOffsetCanBeEditedDuringPlaybackWithoutInvalidatingTheSession() {
        start(); selectDaily()
        compose.onNodeWithTag("play-button").performClick()
        val session=compose.runOnIdle { player.session }
        compose.onNodeWithTag("tempo-button").performClick()
        compose.onNodeWithTag("cursor-offset-plus").performScrollTo().performClick()
        compose.onNodeWithTag("cursor-offset-value").assertTextEquals("+10 ms")
        compose.onNodeWithTag("cursor-offset-minus").performClick()
        compose.onNodeWithTag("cursor-offset-minus").performClick()
        compose.onNodeWithTag("cursor-offset-value").assertTextEquals("-10 ms")
        compose.runOnIdle {
            assertSame(session,player.session)
            assertEquals(Phase.PLAYING,player.progress.phase)
            val prefs=compose.activity.getSharedPreferences("practice-settings",0)
            assertEquals(-10,CursorSyncSettings(prefs).load())
        }
        compose.onNodeWithTag("cursor-offset-reset").performScrollTo().performClick()
        compose.onNodeWithTag("cursor-offset-value").assertTextEquals("0 ms")
        compose.onNodeWithText("完成").performClick()
        assertFourControls()
    }
    @Test @Config(qualifiers = "w412dp-h892dp-mdpi")
    fun portrait412KeepsTheBoardAboveTheActualScoreSlot() {
        start(); selectDaily(); assertFourControls(); capture("phone-412-mint")
        val board = compose.onNodeWithTag("stage-fretboard").fetchSemanticsNode().boundsInRoot
        val score = compose.onNodeWithTag("score-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue(score.height > board.height)
    }
    @Test @Config(qualifiers = "w794dp-h360dp-land-mdpi")
    fun shortLandscapeStartsFoldedAndGivesTheSpaceToTheScore() {
        start(); selectDaily(); assertFourControls()
        compose.onNodeWithTag("fold-fretboard").assertContentDescriptionEquals("展开指板")
        val board = compose.onNodeWithTag("stage-fretboard").fetchSemanticsNode().boundsInRoot
        val score = compose.onNodeWithTag("score-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue(score.height > board.height)
        capture("phone-landscape-folded")
    }
    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletFoldingExpandsTheScoreAndAllThreePalettesApplyToTheStage() {
        start(); selectDaily(); assertFourControls()
        val before = compose.onNodeWithTag("score-viewport").fetchSemanticsNode().boundsInRoot
        val board = compose.onNodeWithTag("stage-fretboard").fetchSemanticsNode().boundsInRoot
        assertTrue(before.height > board.height)
        assertTrue(before.top >= board.bottom)
        val manager = ThemeManager(compose.activity)
        try {
            ThemeRegistry.all.forEach { theme ->
                compose.runOnIdle { manager.select(theme.id) }
                compose.waitForIdle()
                capture("tablet-${theme.id}")
            }
        } finally { manager.close() }
        compose.onNodeWithTag("fold-fretboard").performClick()
        val after = compose.onNodeWithTag("score-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue(after.height > before.height + 100)
        capture("tablet-folded")
    }
}

private class RecordingPlayback : PracticePlayback {
    override var guitar = true
    override var click = true
    override var progress by mutableStateOf(Progress())
    override var session by mutableStateOf<Session?>(null)
    override val active get() = progress.phase in listOf(Phase.PLAYING, Phase.PREPARING)
    override val cursor: Cursor? get() = null
    override fun start(sequence: Sequence, bpm: Int, follow: Boolean, repeats: Int, range: BarRange,
        sourceTempo: Boolean, multiplier: Double) {
        session = Session(sequence, bpm, follow, repeats, range = range, sourceTempo = sourceTempo, tempoMultiplier = multiplier)
        progress = Progress(Phase.PLAYING)
    }
    override fun pause() { if (active) progress = progress.copy(phase = Phase.PAUSED) }
    override fun resume() { progress = progress.copy(phase = Phase.PLAYING) }
    override fun restart() { progress = progress.copy(phase = Phase.PLAYING) }
    override fun invalidate() { session = null; progress = Progress() }
    override fun stop() = invalidate()
    override fun audition(positions: List<Position>, strum: Boolean, capoFret: Int, openStringMidi: List<Int>) {}
    override fun release() {}
}
