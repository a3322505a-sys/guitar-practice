package com.a3322505a.guitarlearning.ui

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.a3322505a.guitarlearning.practice.Cursor
import com.a3322505a.guitarlearning.audio.Phase

/** Mature colours/shapes, fed exclusively by the new layout's anchors. */
internal fun DrawScope.tabHighlight(layout: TabSystemLayout, playback: TabPlayback, hint: Hint, u: Float, colors: ThemeTokens) {
    val bar=layout.bars.firstOrNull { it.bar.index==playback.barIndex } ?: return
    drawRoundRect(colors.notationHighlight,Offset((bar.left-30)*u,2*u),
        Size((bar.right-bar.left+46)*u,(layout.height-4)*u),CornerRadius(10*u))
    bar.events.filter { it.glyph.event.id in playback.eventIds }.map { it.x }.distinct().forEach { x ->
        drawRoundRect(if(hint==Hint.HIDDEN) colors.notationHighlight else colors.notationActive,Offset((x-12)*u,(bar.staffTop-11)*u),
            Size(24*u,112*u),CornerRadius(9*u))
    }
}
