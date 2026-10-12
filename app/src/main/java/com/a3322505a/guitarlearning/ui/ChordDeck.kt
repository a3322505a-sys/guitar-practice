package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Full-width pages: neighboring edges are behind the main card, outside its width. */
@Composable
fun ChordDeck(index: Int, count: Int, onSelect: (Int)->Unit, modifier: Modifier = Modifier,
              content: @Composable (Int, Boolean)->Unit) {
    val pager=rememberPagerState(initialPage=index,pageCount={count})
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(index) { if(index!=pager.settledPage) pager.scrollToPage(index) }
    LaunchedEffect(pager.settledPage) { select(pager.settledPage) }
    BoxWithConstraints(modifier.testTag("chord-deck")) {
        val edge=if(maxWidth>=300.dp) 6.dp else 0.dp
        if(edge>0.dp) {
            if(index>0) Surface(Modifier.fillMaxSize().padding(vertical=8.dp).offset(x=-edge),
                shape=RoundedCornerShape(28.dp),color=Lavender,border=BorderStroke(1.dp,Color.White)) {}
            if(index<count-1) Surface(Modifier.fillMaxSize().padding(vertical=8.dp).offset(x=edge),
                shape=RoundedCornerShape(28.dp),color=Sky,border=BorderStroke(1.dp,Color.White)) {}
        }
        HorizontalPager(state=pager,modifier=Modifier.fillMaxSize(),key={it},
            flingBehavior=PagerDefaults.flingBehavior(pager,pagerSnapDistance=PagerSnapDistance.atMost(1))) { page ->
            content(page,page==index)
        }
    }
}
