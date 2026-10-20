package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.first

/** External choices position the deck; a completed user drag selects a chord. */
@Composable
fun ChordDeck(index: Int, count: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
              content: @Composable (Int, Boolean) -> Unit) {
    val pager = rememberPagerState(initialPage = index, pageCount = { count })
    val select by rememberUpdatedState(onSelect)
    val selectedIndex by rememberUpdatedState(index)
    LaunchedEffect(index, count) {
        if (index != pager.settledPage) pager.scrollToPage(index)
    }
    LaunchedEffect(pager) {
        var dragOrigin: Int? = null
        pager.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragOrigin = selectedIndex
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    val origin = dragOrigin
                    dragOrigin = null
                    if (origin != null) {
                        snapshotFlow { pager.isScrollInProgress }.first { !it }
                        if (origin == selectedIndex && pager.settledPage != selectedIndex) select(pager.settledPage)
                    }
                }
            }
        }
    }
    HorizontalPager(state = pager, modifier = modifier.testTag("chord-deck"), key = { it },
        flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1))) { page ->
        content(page, page == index)
    }
}
