package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import com.a3322505a.guitarlearning.audio.*
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
@Config(sdk=[28],qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkbenchRenderTest {
    @get:Rule val compose=createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    private fun expand() {
        val matcher=SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)
        if(compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) compose.onNode(matcher).performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.runOnIdle {
            val base=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);base.draw(canvas)
            val type=Class.forName("android.view.WindowManagerGlobal")
            val manager=type.getDeclaredMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val views=type.getDeclaredField("mViews").apply { isAccessible=true }.get(manager) as List<android.view.View>
            val origin=IntArray(2);base.getLocationOnScreen(origin)
            views.filter { it!==base && it.visibility==android.view.View.VISIBLE }.forEach {
                val at=IntArray(2);it.getLocationOnScreen(at)
                canvas.save();canvas.translate((at[0]-origin[0]).toFloat(),(at[1]-origin[1]).toFloat());it.draw(canvas);canvas.restore()
            }
            File("../.local/ui-v81/$name.png").apply { parentFile!!.mkdirs();outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
            bitmap.recycle()
        }
    }
    @Test fun phonePartialSheetShowsWholeFretboardAndChangesPreviewWithoutClosing() {
        compose.onNodeWithText("结束").assertDoesNotExist()
        capture("phone-workbench")
        compose.onNodeWithTag("config-summary").performClick()
        val diagram=compose.onNodeWithTag("fretboard").fetchSemanticsNode()
        val panel=compose.onNodeWithTag("practice-settings").fetchSemanticsNode().boundsInRoot
        assertTrue("Actual strings are above the sheet",diagram.boundsInRoot.bottom<=panel.top)
        assertEquals("All six strings are visible",diagram.layoutInfo.coordinates.size.height.toFloat(),diagram.boundsInRoot.height,.5f)
        assertTrue("The diagram has meaningful height",diagram.boundsInRoot.height>90)
        capture("phone-partial-content")
        expand()
        compose.onNodeWithText("三度跳进").performScrollTo().performClick()
        compose.onNodeWithText("la · G").performScrollTo().performClick()
        compose.onNodeWithText("每拍1音").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("preferences-button").performClick();expand()
        compose.onNodeWithText("跟练").performClick()
        compose.onNodeWithTag("tempo-slider").assertDoesNotExist()
        compose.onNodeWithText("声音").performClick()
        compose.onNodeWithTag("guitar-switch").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("config-summary").assertTextContains("三度跳进",substring=true).assertTextContains("每拍1音",substring=true)
        compose.onNodeWithTag("management-button").performClick();compose.onNodeWithText("曲包与帮助").performClick()
        compose.onNodeWithText("选择曲包").assertIsDisplayed()
        capture("management-packages")
        compose.onNodeWithText("帮助说明").performClick()
        compose.onNodeWithText("x 为死音",substring=true).assertIsDisplayed()
        capture("management-help")
        compose.runOnIdle {
            val type=Class.forName("android.view.WindowManagerGlobal")
            val manager=type.getDeclaredMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val dialog=(type.getDeclaredField("mViews").apply { isAccessible=true }.get(manager) as List<android.view.View>).last()
            dialog.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK))
        };compose.waitForIdle()
        compose.onNodeWithTag("management-sheet").assertDoesNotExist()
        compose.onNodeWithTag("config-summary").assertTextContains("三度跳进",substring=true).assertTextContains("每拍1音",substring=true)
    }
    private fun sidePanel() {
        compose.onNodeWithTag("preferences-button").performClick()
        val side=compose.onNodeWithTag("practice-side-panel").fetchSemanticsNode().boundsInRoot
        val diagram=compose.onNodeWithTag("tablature").fetchSemanticsNode().boundsInRoot
        assertTrue("Panel and TAB occupy separate horizontal space",diagram.right<=side.left)
        assertTrue("TAB retains useful width",diagram.width>300)
        compose.onNodeWithText("跟练").performClick()
        compose.onNodeWithText("4 次").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers="w891dp-h411dp-land-mdpi") fun landscapeTabKeepsNotationBesideConfiguration() {
        compose.onNodeWithText("六线谱").performClick();capture("phone-landscape-default");sidePanel();capture("phone-landscape-side")
        compose.activity.onBackPressedDispatcher.onBackPressed();compose.waitForIdle()
        compose.onNodeWithTag("practice-side-panel").assertDoesNotExist()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
    }
    private fun transportBudget(fontScale: Float) {
        var mode by mutableStateOf(TransportMode.IDLE)
        compose.activity.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val height=maxHeight
                Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                    Box(Modifier.width(224.dp)) {
                        PracticeTransport(80,false,100,"2/4",Hint.FULL,mode,true,{},{},{},{},{},{},{},{},availableHeight=height,verticalDeck=true)
                    }
                }
            }
        } }
        val heights=mutableMapOf<TransportMode,Float>()
        TransportMode.entries.forEach { value ->
            compose.runOnIdle { mode=value };compose.waitForIdle()
            val bounds=compose.onNodeWithTag("transport-bar").fetchSemanticsNode().boundsInRoot
            val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
            heights[value]=bounds.height
            assertTrue("$value control deck fits the window ${bounds.height}/${root.height}",bounds.height<=root.height)
            listOf("tempo-slower","tempo-button","tempo-faster").forEach { tag ->
                val button=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue(button.height>=48 && button.width>=48)
            }
            val buttons=compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            buttons.forEach { assertTrue("$value touch bounds $it",it.height>=48 && it.width>=48 && it.left>=root.left && it.right<=root.right) }
            buttons.forEachIndexed { index,a -> buttons.drop(index+1).forEach { b -> assertFalse("$value overlapping buttons",a.overlaps(b)) } }
        }
        assertEquals(heights[TransportMode.PLAYING]!!,heights[TransportMode.PAUSED]!!,.5f)
        compose.runOnIdle { mode=TransportMode.IDLE };compose.waitForIdle()
        val before=compose.onNodeWithTag("transport-bar").fetchSemanticsNode().boundsInRoot.height
        listOf(IntRect(0,300,90,348),IntRect(800,300,891,348)).forEach { anchor ->
            val at=TempoPopupPosition.calculatePosition(anchor,IntSize(891,411),LayoutDirection.Ltr,IntSize(360,104))
            assertTrue(at.x>=0 && at.x+360<=891 && at.y>=0 && at.y+104<=411)
        }
        compose.onNodeWithTag("tempo-button").performClick()
        compose.onNodeWithTag("tempo-slider").assertIsDisplayed()
        assertEquals("The popup must not consume more content height",before,compose.onNodeWithTag("transport-bar").fetchSemanticsNode().boundsInRoot.height,.5f)
        capture("transport-${fontScale}-expanded")
        compose.onNodeWithText("收起").performClick()
        compose.onNodeWithTag("tempo-slider").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w891dp-h411dp-land-mdpi") fun landscapeTransportKeepsTouchTargetsAndContentAcrossEveryPhase()=transportBudget(1f)
    @Test @Config(qualifiers="w640dp-h360dp-land-mdpi") fun shortWindowLargeTextTransportKeepsItsBudget()=transportBudget(1.4f)
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi") fun tabletLargeTextKeepsTabAndSidePanelVisible() {
        compose.activity.resources.configuration.fontScale=1.3f
        @Suppress("DEPRECATION")
        compose.activity.resources.updateConfiguration(compose.activity.resources.configuration,compose.activity.resources.displayMetrics)
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        compose.onNodeWithText("六线谱").performClick();sidePanel();capture("tablet-large-text-side")
    }
    @Test fun overlayTabsSoundsAndSliderPreviewKeepSessionUntilSpeedIsCommitted() {
        val player=Player(compose.activity) { ManualSink() }
        try {
            compose.waitUntil(15000) { Shadows.shadowOf(Looper.getMainLooper()).idle();
                runCatching { File(compose.activity.filesDir,"songs/active-index.json").readText().contains("local.yourtrip") }.getOrDefault(false) }
            Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java)).setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
            compose.onNodeWithText("开始跟练").performClick()
            compose.waitUntil(8000) { Shadows.shadowOf(Looper.getMainLooper()).idle();player.progress.phase==Phase.PLAYING }
            val session=player.session
            compose.onNodeWithTag("preferences-button").performClick();expand()
            compose.onNodeWithText("跟练").performClick();assertSame(session,player.session)
            compose.onNodeWithText("声音").performClick()
            compose.onNodeWithTag("guitar-switch").performScrollTo().performClick();assertSame(session,player.session)
            compose.onNodeWithTag("click-switch").performScrollTo().performClick();assertSame(session,player.session)
            compose.onNodeWithText("完成").performClick();assertSame(session,player.session)
            compose.onNodeWithTag("tempo-button").performClick()
            compose.onNodeWithTag("tempo-slider").performTouchInput { down(center);moveTo(centerRight) }
            assertSame("Dragging only previews the value",session,player.session)
            compose.onNodeWithTag("tempo-slider").performTouchInput { up() }
            assertNull("Only the committed speed invalidates the old session",player.session)
            compose.onNodeWithText("结束").assertDoesNotExist()
            compose.onNodeWithText("开始跟练").assertIsEnabled()
        } finally { player.release() }
    }
}
