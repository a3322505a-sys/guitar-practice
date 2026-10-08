package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class RenderTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun showControls(title: String) {
        compose.onNodeWithText(title).performScrollTo()
        val delta=compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top-16f
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f,delta) }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val path=File("../.local/$name.png")
            path.parentFile!!.mkdirs()
            path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun phoneNavigationAndHiddenChordDoNotLeakFingerPositions() {
        compose.onNodeWithText("mi 指型 · CAGED C 形").assertExists()
        capture("phone-scale")
        compose.onNodeWithText("和弦").performClick()
        showControls("C · 下一：Am")
        capture("phone-chord")
        showControls("自主对照")
        compose.onNodeWithText("隐藏音位／指法").performScrollTo().performClick()
        showControls("C · 下一：Am")
        compose.onNodeWithText("O 空弦 · X 不弹 · 1 食指、2 中指、3 无名指、4 小指").assertDoesNotExist()
        compose.onNodeWithText("C 开放指法实际发声：X · C · E · G · C · E（6→1 弦）；同一组成音可出现在不同八度。").assertDoesNotExist()
        capture("phone-chord-hidden")
        showControls("自主对照")
        compose.onNodeWithText("完整提示").performScrollTo().performClick()
        compose.onNodeWithText("第一个和弦").performScrollTo()
        compose.onAllNodesWithText("D",useUnmergedTree=false)[0].performScrollTo().performClick()
        showControls("D · 下一：Am")
        compose.onNodeWithText("D 根音 · A 五音 · D 根音 · F♯ 三音").assertExists()
        capture("phone-d-chord")
    }
    @Test @Config(qualifiers="w891dp-h411dp-land-mdpi") fun landscapeKeepsTransportReachable() {
        compose.onNodeWithText("听示范").assertIsDisplayed()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        compose.onNodeWithText("和弦").performClick()
        showControls("C · 下一：Am")
        compose.onNodeWithText("听示范").assertIsDisplayed()
        compose.onNodeWithText("开始跟练").assertIsDisplayed()
        capture("landscape-chord")
    }
}
