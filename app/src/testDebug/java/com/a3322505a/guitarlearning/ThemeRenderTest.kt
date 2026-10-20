package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
class ThemeRenderTest {
    @get:Rule val compose=createAndroidComposeRule<LegacyWorkbenchTestActivity>()
    private fun preferences() {
        if (compose.onAllNodesWithTag("preferences-button").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("management-button").performClick()
            compose.onNodeWithText("练习偏好").performClick()
        } else compose.onNodeWithTag("preferences-button").performClick()
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
            File("../.local/ui-v95/$name.png").apply { parentFile!!.mkdirs();outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
            bitmap.recycle()
        }
    }
    private fun expand() {
        val matcher=SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)
        if(compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) compose.onNode(matcher).performSemanticsAction(SemanticsActions.Expand) { it() }
    }
    @Test fun appearanceCanSwitchEveryPresetAndRestoreAfterRecreation() {
        capture("forest-phone")
        compose.onNodeWithTag("config-summary").performClick();expand()
        compose.onNode(hasText("和弦教学") and hasClickAction()).performClick();compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("chord-diagram").assertIsDisplayed()
        preferences();expand()
        compose.onNodeWithText("外观").performClick()
        ThemeRegistry.all.forEach { theme ->
            compose.onNodeWithTag("theme-${theme.id}").performScrollTo().performClick().assertIsSelected()
            val reader=ThemeManager(compose.activity)
            assertEquals(theme.id,reader.current.id);reader.close()
        }
        compose.onNodeWithTag("theme-studio_dark").performScrollTo().performClick()
        capture("appearance-studio-dark")
        compose.onNodeWithText("完成").performClick()
        capture("studio-dark-phone-chord")
        compose.onNodeWithTag("config-summary").performClick();expand()
        capture("studio-dark-content")
        compose.onNodeWithText("完成").performClick()
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        ThemeManager(compose.activity).let { assertEquals("studio_dark",it.current.id);it.close() }
        preferences();expand()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithTag("theme-studio_dark").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("theme-reset").performScrollTo().performClick()
        ThemeManager(compose.activity).let { assertEquals("studio_dark",it.current.id);it.close() }
    }
    @Test fun themeChangeKeepsPlayingSessionAndContentSelection() {
        val player=Player(compose.activity) { ManualSink() }
        try {
            compose.waitUntil(15000) { Shadows.shadowOf(Looper.getMainLooper()).idle();
                compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty() }
            Shadows.shadowOf(compose.activity.getSystemService(android.media.AudioManager::class.java))
                .setNextFocusRequestResponse(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            compose.activity.setContent { PracticeApp(player) }
        compose.waitForIdle()
        compose.waitUntil(45000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); compose.onAllNodesWithTag("practice-library-ready").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(45000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); runCatching { compose.onNodeWithText("开始跟练").assertIsEnabled() }.isSuccess }
            compose.onNodeWithText("开始跟练").performClick()
            compose.waitUntil(8000) { Shadows.shadowOf(Looper.getMainLooper()).idle();player.progress.phase==Phase.PLAYING }
            val session=player.session
            preferences();expand()
            compose.onNodeWithText("外观").performClick()
            compose.onNodeWithTag("theme-studio_dark").performScrollTo().performClick()
            assertSame(session,player.session)
            assertEquals(Phase.PLAYING,player.progress.phase)
            capture("studio-dark-playing")
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("fretboard").assertIsDisplayed()
            assertSame(session,player.session)
        } finally {player.release()}
    }
    @Test @Config(qualifiers="w891dp-h411dp-land-mdpi") fun landscapeUsesSameCardAndIntegratedPod() {
        compose.waitUntil(45000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); runCatching { compose.onNodeWithText("开始跟练").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("dock-toggle").performClick()
        compose.onNodeWithTag("tablature").assertIsDisplayed()
        capture("forest-landscape-tab")
        preferences()
        compose.onNodeWithTag("practice-settings").assertIsDisplayed()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithTag("theme-studio_dark").performScrollTo().performClick()
        capture("studio-dark-landscape-side")
    }
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi") fun tabletKeepsSharedSkeleton() {
        capture("forest-tablet")
        preferences()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithTag("theme-studio_dark").performScrollTo().performClick()
        capture("studio-dark-tablet-side")
    }
}
