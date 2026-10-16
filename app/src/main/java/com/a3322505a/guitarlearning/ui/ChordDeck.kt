package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Full-width pages keep navigation and content inside the same practice card. */
@Composable
fun ChordDeck(index: Int, count: Int, onSelect: (Int)->Unit, modifier: Modifier = Modifier,
              content: @Composable (Int, Boolean)->Unit) {
    val pager=rememberPagerState(initialPage=index,pageCount={count})
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(index) { if(index!=pager.settledPage) pager.scrollToPage(index) }
    LaunchedEffect(pager.settledPage) { select(pager.settledPage) }
    HorizontalPager(state=pager,modifier=modifier.testTag("chord-deck"),key={it},
        flingBehavior=PagerDefaults.flingBehavior(pager,pagerSnapDistance=PagerSnapDistance.atMost(1))) { page ->
        content(page,page==index)
    }
}
