package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
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
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun scaleDensityRestoresLegacyBooleansAndSafelyHandlesOldOrMalformedLists() {
        assertEquals(1,ScaleSaver.restore(listOf(0,0,0,1,6,true,false,1,false))!!.notesPerBeat)
        assertEquals(2,ScaleSaver.restore(listOf(0,0,0,1,6,true,true,1,false))!!.notesPerBeat)
        assertEquals(ScaleOptions(),ScaleSaver.restore(emptyList<Any>()))
        assertEquals(ScaleOptions(),ScaleSaver.restore(listOf(-4,99,"wrong",1,6,"wrong",99,-2)))
        val options=ScaleOptions(pattern=2,key=Tonality.A_MINOR,exercise=ScaleExercise.THREE,octave=false,notesPerBeat=3,startDegree=4)
        val saved=with(ScaleSaver) { androidx.compose.runtime.saveable.SaverScope { true }.save(options) }!!
        assertEquals(options,ScaleSaver.restore(saved))
    }
    @Suppress("DEPRECATION")
    @Test fun allViewsAndSettingsKeepTheSameImmersiveWindowTarget() {
        fun fullscreen()=assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN!=0)
        fullscreen()
        repeat(3) {
            compose.onNodeWithText("六线谱").performClick();fullscreen()
            compose.onNodeWithTag("config-summary").performClick()
            compose.runOnIdle {
                val globalClass=Class.forName("android.view.WindowManagerGlobal")
                val global=globalClass.getDeclaredMethod("getInstance").invoke(null)
                val field=globalClass.getDeclaredField("mViews").apply { isAccessible=true }
                @Suppress("UNCHECKED_CAST")
                val dialog=(field.get(global) as List<android.view.View>).last()
                // InsetsControllerCompat on API 28 uses decor UI flags for a dialog.
                assertTrue("Dialog status bar should be hidden",dialog.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN!=0)
                assertTrue("System bars remain available transiently",dialog.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY!=0)
            }
            compose.onNodeWithText("完成").performClick();fullscreen()
            compose.onNodeWithText("指板").performClick();fullscreen()
        }
        compose.activityRule.scenario.recreate();compose.waitForIdle();fullscreen()
    }
    @Test fun scaleThreePerBeatIsSelectableSavedAndUsesTheOriginalPlayer() {
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("每拍3音").performScrollTo().performClick()
        capture("scale-triplet-settings","five-fixes")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("音阶往返 · 每拍3音").assertIsDisplayed()
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        compose.onNodeWithText("音阶往返 · 每拍3音").assertIsDisplayed()
        compose.onNodeWithText("六线谱").performClick()
        compose.onAllNodesWithContentDescription("三连音 3",substring=true)[0].assertIsDisplayed()
        capture("scale-triplet-tab","five-fixes")
        compose.onNodeWithText("听示范").performClick()
        val player=ViewModelProvider(compose.activity)[PracticeModel::class.java].player
        assertEquals(4,player.session!!.sequence.events.first().duration)
        assertTrue(player.session!!.sequence.tuplets.isNotEmpty())
        compose.onNodeWithText("结束").performClick()
    }
    @Test fun phraseViewportAndControlsStayFixedThroughEveryEventAndPlaybackPhase() {
        val sinks=mutableListOf<ManualSink>()
        val player=Player(compose.activity) { ManualSink().also { sinks+=it } }
        try {
            org.robolectric.Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java)).setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("和弦").performClick()
            compose.onNodeWithText("短句应用").performClick()
            for(id in listOf("M01","R02","PM01","S01")) {
                val phrase=Phrases.named(id)
                compose.onNodeWithTag("config-summary").performClick()
                compose.onNodeWithText(phrase.title).performScrollTo().performClick()
                compose.onNodeWithText("完成").performClick()
                compose.onAllNodesWithText(phrase.title).assertCountEquals(1)
                val tags=listOf("practice-card","diagram-viewport","playback-status","transport")
                fun bounds()=tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
                val expected=bounds()
                capture("$id-idle-board","five-fixes")
                compose.onNodeWithText("开始跟练").performClick()
                assertEquals(expected,bounds())
                val session=player.session!!
                compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();player.progress.phase==Phase.PLAYING }
                val sink=sinks.last()
                assertEquals(expected,bounds())
                phrase.sequence.events.forEach { event ->
                    val tick=session.preTicks+event.tick+.1
                    compose.waitUntil(8000) { sink.written.get()>=session.frame(tick) }
                    sink.head.set(session.frame(tick))
                    compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();player.progress.tick>=tick-.05 }
                    assertEquals("$id at ${event.tick}",expected,bounds())
                    if(id=="PM01" && event.tick==24) compose.onNodeWithTag("phrase-cue").assertTextContains("死音 · 6 弦 x")
                    if(event.rest) compose.onNodeWithTag("phrase-cue").assertTextContains(if(id=="S01") "休止" else "休止 · 留拍",substring=true)
                    if(id=="PM01") capture("$id-event-${event.tick}","five-fixes")
                }
                capture("$id-playing-board","five-fixes")
                compose.onNodeWithText("暂停").performClick();assertEquals(expected,bounds())
                val origin=session.frame(player.progress.tick)
                compose.onNodeWithText("继续").performClick();assertEquals(expected,bounds())
                compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();player.progress.phase==Phase.PLAYING }
                val resumed=sinks.last()
                val end=session.frame(session.totalTicks!!.toDouble())-origin
                compose.waitUntil(8000) { resumed.written.get()>=end }
                resumed.head.set(end)
                compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();player.progress.phase==Phase.ENDED }
                assertEquals(expected,bounds())
                compose.onNodeWithText("撤提示").performClick();assertEquals(expected,bounds())
                compose.onNodeWithTag("phrase-cue").assertTextEquals("已撤提示")
                if(phrase.sequence.sections.isEmpty()) compose.onNodeWithContentDescription("指板，提示已隐藏").assertIsDisplayed()
                compose.onNodeWithText("恢复提示").performClick()
                compose.onNodeWithText("结束").performClick()
            }
        } finally { player.release() }
    }
    private fun capture(name: String, directory: String = "five-fixes") {
        compose.waitForIdle()
        compose.runOnIdle {
            // Bottom sheets live in a dialog window; capture that window instead of the obscured activity.
            val view=if(name.endsWith("settings")) {
                val globalClass=Class.forName("android.view.WindowManagerGlobal")
                val global=globalClass.getDeclaredMethod("getInstance").invoke(null)
                val viewsField=globalClass.getDeclaredField("mViews").apply { isAccessible=true }
                @Suppress("UNCHECKED_CAST")
                val views=viewsField.get(global) as List<android.view.View>
                views.last()
            } else compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val path=File("../.local/$directory/$name.png")
            path.parentFile!!.mkdirs()
            path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun phoneNavigationConfigurationAndHintApplyToBothViews() {
        compose.onNodeWithTag("config-summary").assertIsDisplayed()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        capture("phone-scale")
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("指型连接").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        capture("phone-connection")
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("C · 逐弦检查").assertIsDisplayed()
        compose.onNodeWithText("根音 C · 三音 E · 五音 G").assertIsDisplayed()
        compose.onNodeWithText("听和弦").assertIsDisplayed()
        compose.onNodeWithText("下一：Asus4(no5)").assertDoesNotExist()
        capture("phone-chord-learning")
        compose.onNodeWithText("短句应用").performClick()
        compose.onNodeWithText("Gsus2 · 按音位弹奏").assertIsDisplayed()
        compose.onNodeWithText("下一：Asus4(no5)").assertIsDisplayed()
        capture("phone-chord")
        compose.onNodeWithText("六线谱").performClick()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
        capture("phone-chord-tab")
        compose.onNodeWithText("撤提示").performClick()
        val descriptions=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes()
        assertFalse(descriptions.any { node -> node.config[SemanticsProperties.ContentDescription].any { it.contains("品；") } })
        capture("phone-tab-hidden")
        compose.onNodeWithText("指板").performClick()
        compose.onNodeWithText("已撤提示").assertIsDisplayed()
        compose.onNodeWithText("恢复提示").performClick()
        compose.onNodeWithText("和弦学习").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onAllNodesWithText("D")[0].performScrollTo().performClick()
        compose.onNodeWithTag("tempo-slider").assertDoesNotExist()
        compose.onNodeWithText("点音与和弦说明").assertDoesNotExist()
        capture("phone-practice-settings")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("D · 逐弦检查").assertIsDisplayed()
        capture("phone-d-chord")
    }
    @Test fun practiceAndTempoPanelsAreSeparateAndKeepSelections() {
        compose.onNodeWithTag("tempo-button").performClick()
        compose.onNodeWithText("速度与播放").assertIsDisplayed()
        compose.onNodeWithTag("tempo-slider").assertIsDisplayed()
        compose.onNodeWithText("选音阶与指型").assertDoesNotExist()
        compose.onNodeWithTag("tempo-faster").performClick()
        compose.onNode(hasText("65 BPM") and hasAnyAncestor(hasTestTag("tempo-settings"))).assertIsDisplayed()
        compose.onNodeWithText("1 次").performScrollTo().performClick()
        capture("phone-tempo-settings")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("65 BPM").assertIsDisplayed()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("练习设置").assertIsDisplayed()
        compose.onNodeWithTag("tempo-slider").assertDoesNotExist()
        compose.onNodeWithTag("guitar-switch").assertDoesNotExist()
        compose.onNodeWithText("完整提示").assertDoesNotExist()
        compose.onNodeWithText("起始弦").assertDoesNotExist()
        compose.onNodeWithTag("advanced-toggle").assertDoesNotExist()
        compose.onNodeWithText("范围与显示").assertDoesNotExist()
        compose.onNodeWithText("高 → 低 → 高").assertDoesNotExist()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("选择和弦").assertIsDisplayed()
        compose.onNodeWithText("Gsus2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("more-chord-toggle").assertDoesNotExist()
        capture("phone-check-settings")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("短句应用").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("选择短句").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("两和弦转换").assertDoesNotExist()
        compose.onNodeWithText("规则下拨").assertDoesNotExist()
        compose.onNodeWithText("简单分解").assertDoesNotExist()
        compose.onNodeWithText("检查和弦发音").assertDoesNotExist()
        capture("phone-phrase-settings")
        compose.onNodeWithText("第二个和弦").assertDoesNotExist()
        compose.onNodeWithText("每个和弦的长度").assertDoesNotExist()
        compose.onNodeWithText("完成").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("65 BPM").assertIsDisplayed()
        capture("recreated-phrase-board","five-fixes")
        compose.onNodeWithText("Gsus2 · 按音位弹奏").assertIsDisplayed()
        compose.onNodeWithText("和弦学习").performClick()
        compose.onNodeWithText("C · 逐弦检查").assertIsDisplayed()
    }
    @Test fun scaleSettingsFoldSpecialtiesAndRestoreApplicableParameters() {
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("练习方式").assertIsDisplayed()
        listOf("音阶往返","三度跳进","指型连接").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        listOf("主音与八度","三音模进","换起点","起点级数","起始弦").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("一八度").assertIsDisplayed()
        compose.onNodeWithText("每拍1音").assertIsDisplayed()
        capture("phone-scale-settings")
        compose.onNodeWithText("la · G").performClick()
        compose.onNodeWithText("la 指型三弦 4 品 B：可伸指或轻微移位。").assertIsDisplayed()
        compose.onNodeWithTag("more-exercises-toggle").performScrollTo().performClick()
        compose.onNodeWithText("主音与八度").performScrollTo().performClick()
        compose.onNodeWithText("一八度").assertDoesNotExist()
        compose.onNodeWithText("整型音域").assertDoesNotExist()
        compose.onNodeWithText("换起点").performScrollTo().performClick()
        compose.onNodeWithText("每拍2音").performScrollTo().performClick()
        compose.onNodeWithText("起点级数").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3").performScrollTo().performClick()
        capture("phone-more-scale-settings")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("换起点 · 每拍2音").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("换起点 · 每拍2音").assertIsDisplayed()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("起点级数").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("三度跳进").performScrollTo().performClick()
        compose.onNodeWithText("起点级数").assertDoesNotExist()
        compose.onNodeWithText("指型连接").performScrollTo().performClick()
        compose.onNodeWithText("一八度").assertDoesNotExist()
        compose.onNodeWithText("整型音域").assertDoesNotExist()
        compose.onNodeWithText("re · D").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("开始跟练").assertIsNotEnabled()
    }
    @Test fun legacyScaleChoicesRestoreWithoutHiddenRangeOrDirection() {
        val oldOrder=listOf(ScaleExercise.ASCEND,ScaleExercise.ROOTS,ScaleExercise.CHANGE_START,ScaleExercise.THREE,ScaleExercise.THIRDS,ScaleExercise.CONNECTION)
        oldOrder.forEachIndexed { index,exercise ->
            val restored=ScaleSaver.restore(listOf(2,1,index,2,5,false,true,4,true))!!
            assertEquals(ScaleOptions(2,Tonality.A_MINOR,exercise,1,6,false,2,4,false),restored)
        }
    }
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi") fun tabletStartsWithBoardAndOnlyButtonEnablesTab() {
        compose.waitForIdle()
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,compose.activity.requestedOrientation)
        compose.onNodeWithTag("tablature").assertDoesNotExist()
        compose.onNodeWithText("六线谱").performClick()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,compose.activity.requestedOrientation)
        capture("tablet-scale-tab")
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithTag("more-exercises-toggle").performScrollTo().performClick()
        compose.onNodeWithText("三音模进").performScrollTo().performClick()
        compose.onNodeWithText("整型音域").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        capture("tablet-multiline-tab")
        compose.onNodeWithText("和弦").performClick()
        capture("tablet-chord-tab")
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("Bm7(no5)").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        capture("tablet-check-tab")
        compose.onNodeWithText("指板").performClick()
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,compose.activity.requestedOrientation)
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        capture("tablet-chord-diagram")
        compose.onNodeWithText("短句应用").performClick()
        compose.onNodeWithText("Gsus2 · 按音位弹奏").assertIsDisplayed()
        capture("tablet-phrase-diagram")
    }
    @Test @Config(qualifiers="w800dp-h1100dp-port-mdpi") fun tabletPortraitAndNarrowNotationRemainReadable() {
        compose.onNodeWithText("六线谱").performClick()
        compose.onNodeWithText("听示范").assertIsDisplayed()
        capture("tablet-portrait-tab")
    }
    @Test fun openStringPracticeUsesCompactTargetsAndPreservesAuditionAndHints() {
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("短句应用").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText(Phrases.named("PM01").title).performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        val viewport=compose.onNodeWithTag("diagram-viewport").fetchSemanticsNode().boundsInRoot
        val card=compose.onNodeWithTag("practice-card").fetchSemanticsNode().boundsInRoot
        assertTrue("Open strings should not allocate a full fret grid",viewport.height<150)
        assertTrue("The empty fret area must not stretch the card",card.height<380)
        compose.onNodeWithTag("open-string-board").assertIsDisplayed()
        compose.onNodeWithTag("open-string-6").performClick()
        compose.onNodeWithText("6 弦 0 品",substring=true).assertIsDisplayed()
        compose.onNodeWithTag("open-string-5").assertIsNotEnabled()
        capture("pm-compact-open-strings","v65")
        compose.onNodeWithText("撤提示").performClick()
        compose.onNodeWithContentDescription("指板，提示已隐藏").assertIsDisplayed()
        compose.onNodeWithTag("open-string-6").assertIsNotEnabled()
        assertEquals(viewport,compose.onNodeWithTag("diagram-viewport").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("恢复提示").performClick()
        compose.onNodeWithText("六线谱").performClick()
        compose.onNodeWithContentDescription("P.M.",substring=true).assertIsDisplayed()
        capture("pm-rhythmic-tab","v65")
    }
    @Test @Config(qualifiers="w891dp-h411dp-land-mdpi") fun rhythmicTabAttachesDurationsAcrossStringsAndKeepsLongValuesAndRests() {
        val seq=Sequence(listOf(
            Event(0,6,listOf(Position(6,5))),Event(6,6,listOf(Position(5,6))),
            Event(12,3,listOf(Position(4,7))),Event(15,3,listOf(Position(3,8))),
            Event(18,3,listOf(Position(2,10))),Event(21,3,listOf(Position(1,12))),
            Event(24,24,Chords.named("C").positions)),48,"",beatsPerBar=4)
        compose.activity.setContent { PracticeTheme { Tablature(seq,Hint.FULL,null,null,false) } }
        capture("all-strings-eighths-sixteenths-half","v65")
        compose.activity.setContent { PracticeTheme { Tablature(Phrases.mozart[1].sequence,Hint.FULL,null,null,false) } }
        capture("dotted-eighths-and-rests","v65")
        val tri=Routes.scale(ScaleOptions(exercise=ScaleExercise.THIRDS,notesPerBeat=3)).sequence!!
        compose.activity.setContent { PracticeTheme { Tablature(tri,Hint.FULL,Tonality.C_MAJOR,Session(tri,60,true,2).cursor(68.0),true) } }
        compose.onAllNodesWithContentDescription("三连音 3",substring=true)[0].assertIsDisplayed()
        capture("triplets-active","v65")
    }
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi") fun notationShowsEighthBeamsRestsAndAcrossBarTies() {
        compose.onNodeWithText("六线谱").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithTag("more-exercises-toggle").performScrollTo().performClick()
        compose.onNodeWithText("三音模进").performScrollTo().performClick()
        compose.onNodeWithText("整型音域").performScrollTo().performClick()
        compose.onNodeWithText("每拍2音").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        capture("tablet-eighths-rest")
        compose.activity.setContent { PracticeTheme { Tablature(chordSequence(Chords.named("C"),Chords.named("Am"),ChordExercise.CHANGE,8),Hint.FULL,null,null,false) } }
        compose.waitForIdle()
        capture("tablet-ties")
        compose.activity.setContent { PracticeTheme { Tablature(chordSequence(Chords.named("C"),Chords.named("Am"),ChordExercise.DOWN,4),Hint.FULL,null,null,false) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("下拨，从有效低音弦到高音弦",substring=true).assertExists()
        capture("tablet-downstroke")
    }
    @Test @Config(qualifiers="w891dp-h411dp-land-mdpi") fun phoneLandscapeKeepsTransportReachable() {
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("听示范").assertIsDisplayed()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        capture("phone-landscape")
        compose.onNodeWithTag("tempo-button").assertIsDisplayed().performClick()
        compose.onNodeWithTag("tempo-slider").assertIsDisplayed()
        capture("landscape-tempo-settings")
    }
    @Test @Config(qualifiers="w794dp-h361dp-land-mdpi") fun phoneTabUsesFullWidthAndRestoresOrientation() {
        compose.waitForIdle()
        val original=compose.activity.requestedOrientation
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,original)
        compose.onNodeWithText("六线谱").performClick()
        compose.waitForIdle()
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,compose.activity.requestedOrientation)
        val tab=compose.onNodeWithTag("tablature").fetchSemanticsNode().boundsInRoot
        assertTrue("TAB should use most of the window width",tab.width>700f)
        val row=compose.onAllNodesWithContentDescription("六线谱，第",substring=true).fetchSemanticsNodes().first().boundsInRoot
        assertTrue("All six strings and rhythm fit in the viewport",row.bottom<=tab.bottom+.5f)
        compose.onNodeWithContentDescription("第 1 至 3 小节",substring=true).assertIsDisplayed()
        listOf("开始跟练","听示范","撤提示","60 BPM").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("config-summary").assertIsDisplayed()
        capture("phone-landscape-tab")
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("短句应用").performClick()
        compose.onNodeWithContentDescription("第 1 至 2 小节",substring=true).assertIsDisplayed()
        capture("phone-landscape-phrase-tab")
        compose.onNodeWithText("指板").performClick()
        compose.waitForIdle()
        assertEquals(original,compose.activity.requestedOrientation)
        compose.onNodeWithText("六线谱").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,compose.activity.requestedOrientation)
        compose.onNodeWithText("短句应用").assertIsDisplayed()
        compose.onNodeWithText("指板").performClick()
        compose.waitForIdle()
        assertEquals(original,compose.activity.requestedOrientation)
    }
    @Test @Config(qualifiers="w320dp-h640dp-port-mdpi") fun smallPhoneKeepsPracticeAndSettingsReachable() {
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("C · 逐弦检查").assertIsDisplayed()
        compose.onNodeWithText("听示范").assertIsDisplayed()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        capture("small-phone-chord")
        compose.onNodeWithText("六线谱").performClick()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
        capture("small-phone-tab")
        compose.onNodeWithTag("config-summary").performClick()
        compose.onNodeWithText("选择和弦").assertIsDisplayed()
        compose.onNodeWithText("完成").assertIsDisplayed()
        capture("small-phone-settings")
    }
    @Test fun enlargedTextKeepsTransportAndViewsReachable() {
        compose.activity.resources.configuration.fontScale=1.3f
        @Suppress("DEPRECATION")
        compose.activity.resources.updateConfiguration(compose.activity.resources.configuration,compose.activity.resources.displayMetrics)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        capture("large-text-chord")
        compose.onNodeWithText("六线谱").performClick()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
        capture("large-text-tab")
    }
    private fun assertWholeChordDiagram() {
        val diagram=compose.onNodeWithTag("chord-diagram").fetchSemanticsNode()
        val viewport=compose.onNodeWithTag("diagram-viewport").fetchSemanticsNode().boundsInRoot
        // boundsInRoot is clipped: compare it with the actual measured Canvas, not just assertIsDisplayed.
        assertEquals("The full Canvas must be visible",diagram.layoutInfo.coordinates.size.height.toFloat(),diagram.boundsInRoot.height,.5f)
        assertTrue(diagram.boundsInRoot.top>=viewport.top-.5f)
        assertTrue(diagram.boundsInRoot.bottom<=viewport.bottom+.5f)
        assertFalse("The chord itself must not scroll",compose.onNodeWithTag("diagram-viewport").fetchSemanticsNode().config.contains(SemanticsProperties.VerticalScrollAxisRange))
    }
    private fun scrollChordDiagramIntoView() {
        val diagram=compose.onNodeWithTag("chord-diagram").fetchSemanticsNode()
        val page=compose.onNodeWithTag("chord-page").fetchSemanticsNode().boundsInRoot
        val top=diagram.layoutInfo.coordinates.localToRoot(Offset.Zero).y
        val bottom=top+diagram.layoutInfo.coordinates.size.height
        val delta=if(bottom>page.bottom) bottom-page.bottom else if(top<page.top) top-page.top else 0f
        // HorizontalPager is the closest scroll ancestor; explicitly scroll the vertical page.
        compose.onNodeWithTag("chord-page").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f,delta) }
    }
    @Test @Config(qualifiers="w360dp-h800dp-port-mdpi") fun representativeChordsFitWithoutDiagramScrolling() {
        compose.onNodeWithText("和弦").performClick()
        listOf("D","C","G","D/F♯","Asus4(no5)").forEach { name ->
            compose.onNodeWithTag("config-summary").performClick()
            compose.onAllNodesWithText(name)[0].performScrollTo().performClick()
            compose.onNodeWithText("完成").performClick()
            assertWholeChordDiagram()
            compose.onNodeWithText("听和弦").assertIsDisplayed()
            compose.onNodeWithText("开始跟练").assertIsDisplayed()
            capture("chord-fit-${Chords.all.indexOf(Chords.named(name))}")
        }
    }
    @Test @Config(qualifiers="w320dp-h640dp-port-mdpi") fun shortLargeTextChordPageScrollsAsAWhole() {
        compose.activity.resources.configuration.fontScale=1.3f
        @Suppress("DEPRECATION")
        compose.activity.resources.updateConfiguration(compose.activity.resources.configuration,compose.activity.resources.displayMetrics)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithText("和弦学习").assertIsDisplayed()
        compose.onNodeWithText("短句应用").assertIsDisplayed()
        scrollChordDiagramIntoView()
        assertWholeChordDiagram()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        capture("small-large-text-chord-fit")
        compose.onNodeWithText("六线谱").performScrollTo().performClick()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
        compose.onNodeWithText("指板").performClick()
        scrollChordDiagramIntoView()
        assertWholeChordDiagram()
    }
    @Test fun activityRecreationKeepsPlayerAndSelectedPractice() {
        val original=ViewModelProvider(compose.activity)[PracticeModel::class.java].player
        compose.onNodeWithText("和弦").performClick()
        compose.onNodeWithTag("config-summary").performClick()
        compose.onAllNodesWithText("D")[0].performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("六线谱").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertSame(original,ViewModelProvider(compose.activity)[PracticeModel::class.java].player)
        compose.onNodeWithText("D · 逐弦检查").assertIsDisplayed()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
    }
    @Test fun oldSavedTabPreferenceDoesNotStartLandscape() {
        compose.activity.getSharedPreferences("practice-settings",0).edit().putInt("view",1).apply()
        compose.activity.setContent {}
        compose.waitForIdle()
        val player=ViewModelProvider(compose.activity)[PracticeModel::class.java].player
        compose.activity.setContent { PracticeApp(player) }
        compose.waitForIdle()
        compose.onNodeWithTag("tablature").assertDoesNotExist()
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,compose.activity.requestedOrientation)
    }
    @Test fun chordLearningAuditionsRealChordTonesAndSwitchesToSecondaryPhrase() {
        val player=Player(compose.activity) { ManualSink() }
        try {
            org.robolectric.Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java)).setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.waitForIdle()
            compose.onNodeWithText("和弦").performClick()
            compose.onNodeWithText("听和弦").performClick()
            assertTrue(player.session!!.preview)
            assertEquals(listOf(48,52,55,60,64),player.session!!.sequence.events.single().positions.map { it.midi })
            compose.onNodeWithText("结束").performClick()
            compose.onNodeWithTag("chord-diagram").performTouchInput {
                // C's fifth string, third fret, on the centered square grid.
                val unit=minOf(width/260f,height/300f)
                click(Offset(width/2f-54*unit,(height-300*unit)/2+166.5f*unit))
            }
            assertEquals(listOf(Position(5,3)),player.session!!.sequence.events.single().positions)
            compose.onNodeWithText("结束").performClick()
            compose.onNodeWithText("开始跟练").performClick()
            val learning=player.session!!
            assertEquals(listOf(48,52,55,60,64),learning.sequence.events.flatMap { it.positions }.map { it.midi })
            compose.onNodeWithText("和弦学习").performClick()
            assertSame("Selecting the current mode keeps playback",learning,player.session)
            compose.onNodeWithText("短句应用").performClick()
            assertNull("Switching content ends the old learning sequence",player.session)
            compose.onNodeWithText("下一：Asus4(no5)").assertIsDisplayed()
            compose.onNodeWithText("开始跟练").performClick()
            assertEquals(Phrases.usotsuki.sequence,player.session!!.sequence)
            compose.onNodeWithText("和弦学习").performClick()
            assertNull(player.session)
            compose.onNodeWithText("C · 逐弦检查").assertIsDisplayed()
            compose.onNodeWithText("撤提示").performClick()
            compose.onNodeWithText("根音 C · 三音 E · 五音 G").assertDoesNotExist()
            val tapped=mutableListOf<Position>()
            compose.activity.setContent {
                PracticeTheme { Box(Modifier.width(200.dp)) {
                    ChordDiagram(Chords.named("Asus4(no5)"),Hint.FULL,emptyList(),true,176.dp) { tapped+=it }
                } }
            }
            compose.waitForIdle()
            // A compact diagram's adjacent strings are less than a 44dp touch target apart.
            // Tapping the silent sixth string must still stay silent.
            compose.onNodeWithTag("chord-diagram").performTouchInput { click(Offset(47.2f,16.43f)) }
            assertTrue(tapped.isEmpty())
            compose.onNodeWithTag("chord-diagram").performTouchInput { click(Offset(68.32f,16.43f)) }
            assertEquals(listOf(Position(5,0)),tapped)
        } finally { player.release() }
    }
    @Test fun wholeChordCardsSwipeOneAtATimeAndKeepBoundaries() {
        val player=Player(compose.activity) { ManualSink() }
        try {
            org.robolectric.Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java)).setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("和弦").performClick()
            compose.onNodeWithTag("previous-chord").assertIsNotEnabled()
            compose.onNodeWithText("开始跟练").performClick()
            val old=player.session
            compose.onNodeWithTag("chord-deck").performTouchInput { swipeRight() }
            assertSame("Boundary gesture keeps the old playback",old,player.session)
            compose.onNodeWithTag("chord-deck").performTouchInput { swipeLeft() }
            compose.onNodeWithText("Am · 逐弦检查").assertIsDisplayed()
            compose.onNodeWithText("根音 A · 三音 C · 五音 E").assertIsDisplayed()
            assertNull("Settling on a different whole card ends the old session",player.session)
            assertWholeChordDiagram()
            val card=compose.onNodeWithTag("practice-card").fetchSemanticsNode().boundsInRoot
            val deck=compose.onNodeWithTag("chord-deck").fetchSemanticsNode().boundsInRoot
            assertEquals("Neighbors do not reduce the main card width",deck.width,card.width,.5f)
            compose.onNodeWithText("听和弦").performClick()
            assertEquals(Chords.named("Am").positions,player.session!!.sequence.events.single().positions)
            compose.onNodeWithText("结束").performClick()
            for(chord in Chords.all.drop(2)) {
                compose.onNodeWithTag("chord-deck").performTouchInput { swipeLeft() }
                compose.onNodeWithText("${chord.name} · 逐弦检查").assertIsDisplayed()
                assertWholeChordDiagram()
            }
            compose.onNodeWithTag("next-chord").assertIsNotEnabled()
            compose.onNodeWithTag("chord-deck").performTouchInput { swipeLeft() }
            compose.onNodeWithText("D/F♯ · 逐弦检查").assertIsDisplayed()
            capture("deck-last")
            for(chord in Chords.all.dropLast(1).reversed()) {
                compose.onNodeWithTag("chord-deck").performTouchInput { swipeRight() }
                compose.onNodeWithText("${chord.name} · 逐弦检查").assertIsDisplayed()
            }
            compose.onNodeWithTag("next-chord").performClick()
            capture("deck-quick-next")
            compose.onNodeWithText("Am · 逐弦检查").assertIsDisplayed()
            compose.onNodeWithText("六线谱").performClick()
            compose.onNodeWithText("指板").performClick()
            compose.onNodeWithText("Am · 逐弦检查").assertIsDisplayed()
            capture("deck-am")
        } finally { player.release() }
    }
    @Test fun selectedWholeCardSurvivesRealActivityRecreation() {
        // Use MainActivity's real saved-state tree, rather than replacing it with an injected player.
        compose.onNodeWithText("和弦").performClick()
        repeat(3) { compose.onNodeWithTag("next-chord").performClick() }
        compose.onNodeWithText("Em · 逐弦检查").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Em · 逐弦检查").assertIsDisplayed()
        assertWholeChordDiagram()
    }
    @Test fun emptyHarmonyPhraseUsesNotesAndOriginalPlaybackAcrossLoopsAndViews() {
        val sinks=mutableListOf<ManualSink>()
        val player=Player(compose.activity) { ManualSink().also { sinks+=it } }
        try {
            org.robolectric.Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java)).setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("和弦").performClick()
            compose.onNodeWithText("短句应用").performClick()
            compose.onNodeWithTag("config-summary").performClick()
            compose.onNodeWithText(Phrases.singleNote.title).performScrollTo().performClick()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("chord-diagram").assertDoesNotExist()
            compose.onNodeWithText("下一：Asus4(no5)").assertDoesNotExist()
            compose.onNodeWithText("听和弦").assertDoesNotExist()
            compose.onNodeWithContentDescription("当前 5 弦 3 品；下一 4 弦 0 品；",substring=true).assertIsDisplayed()
            capture("single-note-board")
            compose.onNodeWithText("撤提示").performClick()
            compose.onNodeWithContentDescription("当前 5 弦 3 品；下一 4 弦 0 品；",substring=true).assertDoesNotExist()
            compose.onNodeWithText("恢复提示").performClick()
            compose.onNodeWithText("听示范").performClick()
            assertEquals(Phrases.singleNote.sequence,player.session!!.sequence)
            compose.onNodeWithText("结束").performClick()
            compose.onNodeWithText("开始跟练").performClick()
            val session=player.session!!
            compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();player.progress.phase==Phase.PLAYING }
            val sink=sinks.last()
            val tick=120.0 // pre-bar + full first loop + third note in the second loop.
            compose.waitUntil(8000) { sink.written.get()>=session.frame(tick) }
            sink.head.set(session.frame(tick))
            compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();player.progress.tick>=tick-.1 }
            assertEquals(1,session.cursor(player.progress.tick).loop)
            compose.onNodeWithText("六线谱").performClick()
            assertSame(session,player.session)
            compose.onNodeWithTag("tablature").assertIsDisplayed()
            capture("single-note-playing-tab")
            compose.onNodeWithText("暂停").performClick()
            assertEquals(Phase.PAUSED,player.progress.phase)
            compose.onNodeWithText("指板").performClick()
            assertSame(session,player.session)
            compose.onNodeWithTag("chord-diagram").assertDoesNotExist()
            compose.onNodeWithText("继续").performClick()
            assertSame(session,player.session)
            compose.onNodeWithText("结束").performClick()
            compose.onNodeWithTag("config-summary").performClick()
            compose.onNodeWithText(Phrases.usotsuki.title).performScrollTo().performClick()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("下一：Asus4(no5)").assertIsDisplayed()
            compose.onNodeWithTag("chord-diagram").assertIsDisplayed()
        } finally { player.release() }
    }
    @Test @Config(qualifiers="w891dp-h411dp-mdpi")
    fun mozartPhrasesAreSelectableAndUseTwoBeatPlaybackAndSixteenthTab() {
        val player=Player(compose.activity) { ManualSink() }
        try {
            org.robolectric.Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java)).setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("和弦").performClick()
            compose.onNodeWithText("短句应用").performClick()
            Phrases.mozart.forEach { phrase ->
                compose.onNodeWithTag("config-summary").performClick()
                compose.onNodeWithText(phrase.title).performScrollTo().performClick()
                compose.onNodeWithText("完成").performClick()
                compose.onNodeWithTag("chord-diagram").assertDoesNotExist()
                compose.onNodeWithText("听示范").performClick()
                assertEquals(phrase.sequence,player.session!!.sequence)
                compose.onNodeWithText("结束").performClick()
            }
            compose.onNodeWithText("六线谱").performClick()
            compose.onNodeWithTag("tablature").assertIsDisplayed()
            capture("mozart-ending-tab","v62")
            compose.onNodeWithText("开始跟练").performClick()
            val session=player.session!!
            assertEquals(24,session.preTicks)
            compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();player.progress.phase==Phase.PLAYING }
            compose.onNodeWithText("预备拍 1 / 2").assertIsDisplayed()
            compose.onNodeWithText("暂停").performClick()
            compose.onNodeWithText("指板").performClick()
            assertSame(session,player.session)
            compose.onNodeWithText("结束").performClick()
        } finally { player.release() }
    }
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi")
    fun mozartDottedCadenceRendersWithOriginalFingering() {
        compose.activity.setContent { PracticeTheme { Tablature(Phrases.mozart[1].sequence,Hint.FULL,null,null,false) } }
        compose.onNodeWithContentDescription("第 9 至 12 小节",substring=true).assertIsDisplayed()
        compose.onNodeWithContentDescription("2 弦 8 品；2 弦 5 品；3 弦 7 品；",substring=true).assertIsDisplayed()
        capture("mozart-dotted-eighth-tab","v62")
    }
    @Test @Config(qualifiers="w891dp-h411dp-mdpi")
    fun tripletDeadMuteAndSlideNotationRenderFromRegisteredContent() {
        listOf("T01","R02","PM01","SL01").forEach { id ->
            val seq=Phrases.named(id).sequence
            compose.activity.setContent { PracticeTheme { Tablature(seq,Hint.FULL,null,null,false) } }
            compose.onNodeWithTag("tablature").assertIsDisplayed()
            if(id=="T01") compose.onNodeWithContentDescription("三连音 3",substring=true).assertIsDisplayed()
            if(id=="R02") compose.onNodeWithContentDescription("弦 x",substring=true).assertIsDisplayed()
            if(id=="PM01") compose.onNodeWithContentDescription("P.M.",substring=true).assertIsDisplayed()
            if(id=="SL01") compose.onNodeWithContentDescription("5/7",substring=true).assertIsDisplayed()
            capture("$id-tab","five-fixes")
        }
        val scale=Routes.scale(ScaleOptions(exercise=ScaleExercise.THIRDS,notesPerBeat=3)).sequence!!
        compose.activity.setContent { PracticeTheme { Tablature(scale,Hint.FULL,Tonality.C_MAJOR,null,false) } }
        compose.onNodeWithContentDescription("三连音 3",substring=true).assertIsDisplayed()
        capture("scale-triplet-landscape","five-fixes")
    }
    @Test @Config(qualifiers="w411dp-h891dp-mdpi")
    fun phraseRangeAndTempoRestoreInUiWithoutStartingPlayback() {
        val prefs=compose.activity.getSharedPreferences("practice-settings",0)
        val phrase=Phrases.mozart[1]
        PhraseSettingsStore(prefs).save(phrase,73,8,BarRange(2,3))
        val player=Player(compose.activity) { ManualSink() }
        try {
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("和弦").performClick()
            compose.onNodeWithText("短句应用").performClick()
            compose.onNodeWithTag("config-summary").performClick()
            compose.onNodeWithTag("phrase-range").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("当前范围：11–12 小节；完整经过范围一次计一轮。").assertIsDisplayed()
            capture("range-settings","v63")
            compose.onNodeWithText("完成").performClick()
            assertNull(player.session)
            compose.onNodeWithTag("tempo-button").performClick()
            compose.onNodeWithTag("tempo-slider").assertIsDisplayed()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("听示范").performClick()
            assertEquals(BarRange(2,3),player.session!!.range)
            assertEquals(73,player.session!!.bpm)
            compose.onNodeWithText("结束").performClick()
        } finally { player.release() }
    }
    @Test fun activeViewSwitchKeepsSessionAndMutedPlaybackAdvances() {
        val sinks=mutableListOf<ManualSink>()
        val player=Player(compose.activity) { ManualSink().also { synchronized(sinks) { check(sinks.all { s -> s.closed }); sinks += it } } }
        try {
            compose.activity.getSharedPreferences("practice-settings",0).edit().putBoolean("guitar",false).putBoolean("click",false).apply()
            org.robolectric.Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java)).setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.waitForIdle()
            compose.onNodeWithText("开始跟练").performClick()
            assertNotNull("New content must call the injected player", player.session)
            compose.waitUntil(8000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                if(player.progress.phase==Phase.ERROR) error(player.progress.message)
                player.progress.phase==Phase.PLAYING
            }
            val s=player.session!!
            val sink=synchronized(sinks) { sinks.last() }
            compose.waitUntil(8000) { sink.written.get()>=s.frame(120.0) }
            sink.head.set(s.frame(120.0))
            compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); player.progress.tick>=119.9 }
            val tick=player.progress.tick
            compose.onNodeWithTag("config-summary").performClick()
            assertSame(s,player.session)
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("tempo-button").performClick()
            assertSame(s,player.session)
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("六线谱").performClick()
            compose.waitForIdle()
            assertSame(s,player.session)
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,compose.activity.requestedOrientation)
            assertEquals(tick,player.progress.tick,0.01)
            assertFalse(player.guitar);assertFalse(player.click)
            assertFalse(sink.nonZero)
            capture("phone-playing-tab")
            compose.onNodeWithText("暂停").performClick()
            assertEquals(Phase.PAUSED,player.progress.phase)
            assertEquals(96.0,player.progress.tick,0.0)
            compose.onNodeWithText("指板").performClick()
            assertSame(s,player.session)
            compose.onNodeWithText("继续").performClick()
            compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); player.progress.phase==Phase.PLAYING }
            assertSame(s,player.session)
            compose.onNodeWithText("重来").performClick()
            compose.waitUntil(8000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); player.progress.tick<48.0 && player.progress.phase==Phase.PLAYING }
            compose.onNodeWithTag("tempo-button").performClick()
            compose.onNodeWithTag("tempo-faster").performClick()
            assertNull("Changing tempo must end the old session",player.session)
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("开始跟练").performClick()
            assertEquals(65,player.session!!.bpm)
            assertEquals(s.sequence,player.session!!.sequence)
            val updated=player.session!!
            compose.onNodeWithTag("config-summary").performClick()
            compose.onNodeWithText("音阶往返").performScrollTo().performClick()
            assertSame("Selecting the existing exercise must keep the session",updated,player.session)
            compose.onNodeWithTag("more-exercises-toggle").performScrollTo().performClick()
            assertSame("Expanding choices must keep the session",updated,player.session)
            compose.onNodeWithText("三度跳进").performScrollTo().performClick()
            assertNull("Changing the exercise must end the old session",player.session)
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("开始跟练").performClick()
            assertEquals(listOf(48,52,50,53,52,55,53,57,55,59,57,60),player.session!!.sequence.events.flatMap { it.positions }.map { it.midi })
            compose.onNodeWithText("结束").performClick()
            assertNull(player.session)
        } finally { player.release() }
    }
}

class ManualSink : PlaybackSink {
    val head=AtomicLong(0)
    val written=AtomicLong(0)
    @Volatile var nonZero=false
    @Volatile var closed=false
    override val heardFrames get()=head.get()
    override fun play() {}
    override fun write(samples: ShortArray, offset: Int, count: Int): Int { if(samples.any { it!=0.toShort() }) nonZero=true; written.addAndGet(count.toLong()); Thread.sleep(1); return count }
    override fun close() { closed=true }
}
