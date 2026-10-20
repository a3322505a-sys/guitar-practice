package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.practice.Phrases as Phrases

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.a3322505a.guitarlearning.audio.*
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
@Config(sdk=[28],qualifiers="w794dp-h361dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnifiedTabRenderTest {
    @get:Rule val compose=createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    private fun usotsuki()=LegacyUsotsukiFixture.phrase.sequence
    @Test fun suppressedTieShowsBoundaryExplanationWithoutGhostFretOrFalseRest() {
        val contract=File("../contracts/song-package/v1")
        val song=com.a3322505a.guitarlearning.assets.song.SongReader(com.a3322505a.guitarlearning.assets.song.SongSchema {
            File(contract,it).readBytes() }).read(File(contract,"fixtures/ties.gpractice").readBytes())
        val segment=com.a3322505a.guitarlearning.music.score.PracticeSegment(
            com.a3322505a.guitarlearning.music.score.Coordinate("tie-b"),
            com.a3322505a.guitarlearning.music.score.Coordinate("tie-b",com.a3322505a.guitarlearning.music.score.Q(4)),
            entry="truncate",affectedNoteIds=listOf("tie-origin","tie-target"))
        val plan=com.a3322505a.guitarlearning.practice.compile.PracticeCompiler.compile(song.score,song.practices.single().copy(segments=listOf(segment)))
        render(Sequence(emptyList(),48,"",runtimePlan=plan),null,"truncated-tie-no-ghost")
        val text=compose.onNodeWithContentDescription("窗口：截音",substring=true).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertFalse(text.contains("休止"));assertFalse(text.contains(" 品"))
    }
    private fun image(name: String): Bitmap {
        var result: Bitmap?=null
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            result=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(result!!))
            File("../work/tablature-v79/$name.png").apply { parentFile!!.mkdirs();outputStream().use { result!!.compress(Bitmap.CompressFormat.PNG,100,it) } }
        }
        return result!!
    }
    private fun assertPink(name: String,expected: Boolean) {
        val bitmap=image(name);var activePixels=0
        val activeColor=ThemeRegistry.default.colors.notationActive.toArgb()
        for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) if(bitmap.getPixel(x,y)==activeColor) activePixels++
        assertEquals("active theme highlight pixels: $activePixels",expected,activePixels>100);bitmap.recycle()
    }
    private fun render(seq: Sequence,tick: Double?,name: String,fontScale: Float=1f,following: Boolean=false) {
        val cursor=tick?.let { Session(seq,60,false,1).cursor(it) }
        compose.activity.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)) {
            PracticeTheme { Tablature(seq,Hint.FULL,null,cursor,following,Modifier.fillMaxSize()) }
        } }
        compose.waitForIdle();assertPink(name,tick!=null)
    }
    @Test fun phoneUsotsukiAndPalmMuteUseTheSamePinkHighlightAndReadableFretMasks() {
        render(usotsuki(),6.0,"phone-usotsuki-playing")
        compose.onAllNodesWithContentDescription("let ring",substring=true).onFirst().assertIsDisplayed()
        render(NotationFixtures.demonstrations.getValue("palm-mute"),12.0,"phone-pm-playing")
        compose.onNodeWithContentDescription("P.M.",substring=true).assertIsDisplayed()
        render(NotationFixtures.mixedStrings,9.0,"phone-chord-dead-rest")
        render(NotationFixtures.legato(Technique.SLIDE),42.0,"phone-cross-bar-slide")
    }
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi") fun tabletUsotsukiAndPalmMuteKeepTheSameLayoutAndHighlightContract() {
        render(usotsuki(),54.0,"tablet-usotsuki-playing")
        render(NotationFixtures.demonstrations.getValue("palm-mute"),24.0,"tablet-pm-dead-note")
    }
    @Test fun phonePracticePageShowsUsotsukiWithItsTransportAndAllSixStrings() {
        val player=Player(compose.activity)
        try {
            // Replace content before the first semantics traversal. Storage is shared safely
            // with the initial activity repository; no nested Looper drain during Compose layout.
            compose.activity.setContent { PracticeApp(player) }
        compose.waitForIdle()
        compose.waitUntil(45000) { compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("config-summary").performClick()
            compose.onNode(hasText("综合应用") and hasClickAction()).performClick()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("config-summary").performClick()
            compose.waitUntil(45000) { runCatching { compose.onNodeWithTag("content-source-filters").performScrollToKey("local.demo-c") }.isSuccess }
            compose.onNodeWithTag("content-source-local.demo-c").performClick()
            val content = ContentPresentation(SongTestLibrary.repository.snapshot)
            val phrase = content.phraseSections("local.demo-c").flatMap { it.items }.first()
            compose.onNodeWithText(content.phraseCard(phrase).title).performScrollTo().performClick()
            compose.waitUntil(45000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                runCatching { compose.onNodeWithText("开始跟练").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithTag("dock-toggle").performClick()
            image("phone-full-practice-beforeassert").recycle()
            compose.onNodeWithTag("tablature").assertIsDisplayed()
            compose.onNodeWithText("听示范").assertIsDisplayed()
            image("phone-full-practice-usotsuki").recycle()
            compose.onNodeWithText("倒序接龙").performScrollTo().assertIsEnabled()
        } finally { player.release() }
    }
    @Test @Config(qualifiers="w640dp-h360dp-land-mdpi") fun largeTextDenseAndHarmonicNotesKeepTheirMeasuredWidth() {
        val dense=SongTestLibrary.repository.snapshot.phrases.filter { it.assetKey?.packageId=="local.teaching-native" }.map { SongTestLibrary.repository.resolve(it.id) }[0].sequence
        render(dense,(dense.barEnd(1)-6).toDouble(),"large-text-block-following",1.4f,true)
        val reader=com.a3322505a.guitarlearning.assets.song.SongReader(com.a3322505a.guitarlearning.assets.song.SongSchema {
            File("../contracts/song-package/v1",it).readBytes() })
        val song=reader.read(File("../contracts/song-package/v1/fixtures/harmonics.gpractice").readBytes())
        val plan=com.a3322505a.guitarlearning.practice.compile.PracticeCompiler.compile(song.score,song.practices.single())
        render(Sequence(emptyList(),(plan.length.value()*12).toInt(),"",runtimePlan=plan),0.0,"large-text-harmonics",1.4f)
    }
    @Test fun realPlayerFramesPauseContinueRestartSpeedAndEndDriveHighlights() {
        val seq=usotsuki();val sinks=mutableListOf<ManualSink>()
        val player=Player(compose.activity) { ManualSink().also { synchronized(sinks) { sinks+=it } } }
        fun idle()=Shadows.shadowOf(Looper.getMainLooper()).idle()
        fun playing()=compose.waitUntil(8000) { idle();if(player.progress.phase==Phase.ERROR) error(player.progress.message);player.progress.phase==Phase.PLAYING }
        fun advance(tick: Double) {
            val session=player.session!!;val sink=synchronized(sinks) { sinks.last() }
            val origin=session.frame(player.progress.tick)-sink.head.get()
            val frame=session.frame(tick)-origin
            compose.waitUntil(8000) { sink.written.get()>=frame }
            sink.head.set(frame);compose.waitUntil(8000) { idle();player.progress.tick>=tick-.01 || player.progress.phase==Phase.ENDED }
            compose.waitForIdle()
        }
        try {
            compose.activity.setContent { PracticeTheme {
                val session=player.session
                Tablature(session?.sequence ?: seq,Hint.FULL,null,notationCursor(session?.cursor(player.progress.tick),player.progress.phase),true,Modifier.fillMaxSize())
            } }
            player.start(seq,60,true,1,sourceTempo=false);playing();assertPink("lifecycle-count-in",false)
            advance(54.0);assertPink("lifecycle-playing",true)
            player.pause();compose.waitForIdle();assertPink("lifecycle-paused",true)
            assertEquals(48.0,player.progress.tick,.01)
            player.resume();playing();assertPink("lifecycle-continued",true)
            advance(102.0)
            val current=compose.onAllNodes(hasStateDescription("当前小节 2")).fetchSemanticsNodes()
            assertEquals(1,current.size)
            assertPink("lifecycle-second-bar",true)
            player.restart();playing();assertPink("lifecycle-restarted",false)
            player.invalidate();compose.waitForIdle();assertPink("lifecycle-speed-invalidated",false)
            player.start(seq,120,false,1,sourceTempo=false);playing()
            assertTrue(player.session!!.frame(6.0)<Session(seq,60,false,1,sourceTempo=false).frame(6.0))
            advance(6.0);assertPink("lifecycle-faster",true)
            advance(player.session!!.totalExactTicks!!)
            compose.waitUntil(8000) { idle();player.progress.phase==Phase.ENDED }
            compose.waitForIdle();assertPink("lifecycle-ended",false)
        } finally { player.release() }
    }
    private fun hasStateDescription(value: String)=SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,value)
}
