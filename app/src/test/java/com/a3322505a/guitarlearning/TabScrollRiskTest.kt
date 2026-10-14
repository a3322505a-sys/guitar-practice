package com.a3322505a.guitarlearning

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w411dp-h891dp-mdpi")
class TabScrollRiskTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun sequence(fret: Int)=Sequence((0 until 192).map { Event(it*3,3,listOf(Position(1,fret))) },576,"")
    private fun description(start: Int)="六线谱，第 $start 至 $start 小节。"
    @Test fun switchingStoppedContentResetsBothAxesButRecompositionDoesNot() {
        var seq by mutableStateOf(sequence(12))
        var hint by mutableStateOf(Hint.FULL)
        compose.activity.setContent { PracticeTheme { Tablature(seq,hint,null,null,false,Modifier.fillMaxSize()) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        val row=compose.onNodeWithContentDescription(description(6),substring=true)
        row.performTouchInput { swipeLeft() }
        fun horizontal()=row.onParent().fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue(horizontal()>0)
        val offset=horizontal()
        compose.runOnIdle { hint=Hint.ROOTS };compose.waitForIdle()
        assertEquals(offset,horizontal(),.5f)
        compose.runOnIdle { seq=sequence(7);hint=Hint.FULL };compose.waitForIdle()
        compose.onNodeWithContentDescription(description(1),substring=true).assertIsDisplayed()
        val first=compose.onNodeWithContentDescription(description(1),substring=true)
        assertEquals(0f,first.onParent().fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value(),.5f)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        assertEquals(0f,row.onParent().fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value(),.5f)
    }
    @Test fun pausedViewRoundTripFollowsTheSameMusicalPosition() {
        val seq=sequence(12)
        val cursor=Session(seq,60,false,1).cursor(6*48.0+42)
        var tab by mutableStateOf(true)
        var hint by mutableStateOf(Hint.FULL)
        compose.activity.setContent { PracticeTheme {
            if(tab) Tablature(seq,hint,null,cursor,true,Modifier.fillMaxSize())
        } }
        val row=compose.onNodeWithContentDescription(description(7),substring=true)
        row.assertIsDisplayed()
        fun horizontal()=row.onParent().fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue(horizontal()>0)
        val offset=horizontal()
        compose.runOnIdle { hint=Hint.ROOTS };compose.waitForIdle()
        assertEquals(offset,horizontal(),.5f)
        compose.runOnIdle { tab=false };compose.waitForIdle()
        compose.runOnIdle { tab=true };compose.waitForIdle()
        row.assertIsDisplayed();assertEquals(offset,horizontal(),.5f)
    }
}
