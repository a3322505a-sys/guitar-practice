package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
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
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
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
            views.filter { it!==base && it.visibility==android.view.View.VISIBLE }.forEach { it.draw(canvas) }
            File("../.local/workbench-v80/$name.png").apply { parentFile!!.mkdirs();outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
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
        compose.onNodeWithText("三度练习").performScrollTo().performClick()
        compose.onNodeWithText("la · G").performScrollTo().performClick()
        compose.onNodeWithText("自定义").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("跟练").performClick()
        compose.onNodeWithTag("tempo-slider").assertDoesNotExist()
        compose.onNodeWithText("声音").performClick()
        compose.onNodeWithTag("guitar-switch").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("三度跳进 · 每拍1音").assertIsDisplayed()
        compose.onNodeWithTag("management-button").performClick()
        compose.onNodeWithText("选择曲包").assertIsDisplayed()
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
        compose.onNodeWithText("三度跳进 · 每拍1音").assertIsDisplayed()
    }
    private fun sidePanel() {
        compose.onNodeWithTag("config-summary").performClick()
        val side=compose.onNodeWithTag("practice-side-panel").fetchSemanticsNode().boundsInRoot
        val diagram=compose.onNodeWithTag("tablature").fetchSemanticsNode().boundsInRoot
        assertTrue("Panel and TAB occupy separate horizontal space",diagram.right<=side.left)
        assertTrue("TAB retains useful width",diagram.width>300)
        compose.onNodeWithText("跟练").performClick()
        compose.onNodeWithText("4 次").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers="w891dp-h411dp-land-mdpi") fun landscapeTabKeepsNotationBesideConfiguration() {
        compose.onNodeWithText("六线谱").performClick();sidePanel();capture("phone-landscape-side")
        compose.activity.onBackPressedDispatcher.onBackPressed();compose.waitForIdle()
        compose.onNodeWithTag("practice-side-panel").assertDoesNotExist()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
    }
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
            compose.onNodeWithTag("config-summary").performClick();expand()
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
