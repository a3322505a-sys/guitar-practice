package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

@Composable
fun Tablature(sequence: Sequence, hint: Hint, key: Tonality?, cursor: Cursor?, following: Boolean, modifier: Modifier = Modifier) {
    val bars = remember(sequence) { sequence.tabBars() }
    val state = rememberLazyListState()
    BoxWithConstraints(modifier.fillMaxSize().testTag("tablature")) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        // Short landscape windows trim empty margins, while preserving string and digit sizes.
        val systemHeight = (maxHeight.value / fontScale).coerceIn(196f,230f)
        val rows = remember(bars, maxWidth, fontScale) { tabSystems(bars, (maxWidth.value / fontScale).toInt()) }
        val target = cursor?.bar?.let { bar -> rows.indexOfFirst { row -> row.any { it.index == bar } } } ?: 0
        LaunchedEffect(sequence, target, following) {
            if (following && cursor?.pre == false && target >= 0) state.animateScrollToItem(target)
        }
        LazyColumn(state=state, verticalArrangement=Arrangement.spacedBy(12.dp)) {
            itemsIndexed(rows) { index, row ->
                TabSystem(row, hint, key, cursor, index == rows.lastIndex, fontScale,systemHeight)
            }
        }
    }
}

private fun visible(segment: TabSegment, hint: Hint, key: Tonality?): List<Position> = when (hint) {
    Hint.FULL -> segment.positions
    Hint.HIDDEN -> emptyList()
    Hint.ROOTS -> segment.positions.filter { p -> key?.isRoot(p) ?: (segment.chord?.let { Chords.named(it).root == p.midi % 12 } == true) }
}

@Composable
private fun TabSystem(bars: List<TabBar>, hint: Hint, key: Tonality?, cursor: Cursor?, last: Boolean, scale: Float, height: Float) {
    val measurer = rememberTextMeasurer()
    val description = buildString {
        append("六线谱，第 ${bars.first().index+1} 至 ${bars.last().index+1} 小节。上方 1 弦，下方 6 弦。")
        if(hint != Hint.HIDDEN) bars.forEach { bar -> bar.segments.forEach { s ->
                if(s.rest) append("休止 ${s.value.ticks/12.0} 拍；")
                else visible(s,hint,key).forEach { append("${it.string} 弦 ${it.fret} 品；") }
                if(s.strum) append("下拨，从有效低音弦到高音弦；")
        } }
    }
    Canvas(Modifier.fillMaxWidth().height((height*scale).dp).semantics { contentDescription=description }) {
        val u=1.dp.toPx()*scale
        val left=32*u
        val top=(62-(230-height)*.7f)*u
        val gap=18*u
        val rhythm=(height-25)*u
        val usable=size.width-left-8*u
        val total=bars.sumOf { it.readableWidth() }
        var x0=left
        text(measurer,"TAB",13*u,top+2.5f*gap,Muted,11)
        for(string in 1..6) text(measurer,"$string",24*u,top+(string-1)*gap,Muted,10)
        if(bars.first().index == 0) text(measurer,"4/4",14*u,maxOf(14*u,top-34*u),Ink,12)
        for(bar in bars) {
            val width=usable*bar.readableWidth()/total
            val current=cursor?.pre == false && cursor.bar == bar.index
            if(current) drawRoundRect(Mint,Offset(x0-3*u,4*u),Size(width,(height-12)*u),androidx.compose.ui.geometry.CornerRadius(12*u))
            text(measurer,"${bar.index+1}",x0+10*u,16*u,if(current) Green else Muted,12)
            for(i in 0..5) drawLine(Color(0xFFA0B4A9),Offset(x0,top+i*gap),Offset(x0+width,top+i*gap),u*.8f)
            drawLine(Ink,Offset(x0,top),Offset(x0,top+5*gap),u)
            val start=x0+27*u
            val span=width-49*u
            fun at(s: TabSegment) = start + (s.tick%BAR).toFloat()/BAR*span
            for((i,s) in bar.segments.withIndex()) {
                val x=at(s)
                val active=current && cursor!!.localTick >= s.tick && cursor.localTick < s.end
                if(active) drawRoundRect(if(hint==Hint.HIDDEN) Mint else Pink,Offset(x-12*u,top-13*u),Size(24*u,5*gap+26*u),androidx.compose.ui.geometry.CornerRadius(10*u))
                val notes=visible(s,hint,key)
                if(hint != Hint.HIDDEN) {
                    val previous=bar.segments.getOrNull(i-1)
                    if(s.chord!=null && (previous?.chord != s.chord || i==0) && !s.tieIn) text(measurer,s.chord,x,top-26*u,PurpleInk,13)
                    if(s.rest) rest(s.value, x,rhythm-10*u,u)
                    else {
                        for(p in notes) {
                            val y=top+(p.string-1)*gap
                            drawRoundRect(if(active) Pink else if(current) Mint else Color.White,Offset(x-9*u,y-9*u),Size(18*u,18*u),androidx.compose.ui.geometry.CornerRadius(4*u))
                            text(measurer,p.fret.toString(),x,y,if(active) RoseInk else Ink,15,true)
                            if(s.tieOut) {
                                // Continue one arc across an in-system barline. Split it
                                // only at a real system break, never into a second tie.
                                val endX = bar.segments.getOrNull(i+1)?.let { at(it)-9*u }
                                    ?: if(bar != bars.last()) x0+width+18*u else x0+width-3*u
                                tie(x+9*u,endX,y+6*u,u)
                            }
                            if(s.tieIn && i==0 && bar==bars.first()) tie(x0+3*u,x-9*u,y+6*u,u)
                        }
                        rhythm(s.value,x,rhythm,u,active)
                    }
                    if(s.strum) {
                        val ax=x-15*u
                        // TAB has high strings on top: a low-to-high guitar downstroke
                        // travels upward on the staff. Do not cross the muted strings.
                        val bottom=top+(s.positions.maxOf { it.string }-1)*gap+5*u
                        val head=top+(s.positions.minOf { it.string }-1)*gap-5*u
                        drawLine(PurpleInk,Offset(ax,bottom),Offset(ax,head),1.5f*u)
                        drawLine(PurpleInk,Offset(ax,head),Offset(ax-3*u,head+5*u),1.5f*u)
                        drawLine(PurpleInk,Offset(ax,head),Offset(ax+3*u,head+5*u),1.5f*u)
                    }
                    // Pair eighths only within one beat; rests break beams.
                    val next=bar.segments.getOrNull(i+1)
                    if(!s.rest && s.value==RhythmValue.EIGHTH && next?.value==RhythmValue.EIGHTH && !next.rest && s.tick/QUARTER==next.tick/QUARTER) {
                        drawLine(Ink,Offset(x+5*u,rhythm-29*u),Offset(at(next)+5*u,rhythm-29*u),3*u)
                    } else if(!s.rest && s.value==RhythmValue.EIGHTH && !(previous?.value==RhythmValue.EIGHTH && !previous.rest && previous.tick/QUARTER==s.tick/QUARTER)) {
                        val flag=Path().apply { moveTo(x+5*u,rhythm-29*u); cubicTo(x+18*u,rhythm-20*u,x+15*u,rhythm-15*u,x+12*u,rhythm-10*u) }
                        drawPath(flag,Ink,style=Stroke(2*u))
                    }
                }
            }
            drawLine(Ink,Offset(x0+width,top),Offset(x0+width,top+5*gap),u)
            if(last && bar == bars.last()) {
                drawLine(Ink,Offset(x0+width-4*u,top),Offset(x0+width-4*u,top+5*gap),2.5f*u)
                drawCircle(Ink,1.8f*u,Offset(x0+width-10*u,top+2*gap))
                drawCircle(Ink,1.8f*u,Offset(x0+width-10*u,top+3*gap))
            }
            x0 += width
        }
    }
}

private fun DrawScope.text(m: TextMeasurer, value: String, x: Float, y: Float, color: Color, size: Int, bold: Boolean=false) {
    val layout=m.measure(value,TextStyle(color=color,fontSize=size.sp,fontWeight=if(bold) FontWeight.Bold else FontWeight.Medium))
    drawText(layout,topLeft=Offset(x-layout.size.width/2f,y-layout.size.height/2f))
}
private fun DrawScope.tie(x1: Float,x2: Float,y: Float,u: Float) {
    if(x2<=x1) return
    val p=Path().apply { moveTo(x1,y); cubicTo(x1+(x2-x1)*.25f,y+9*u,x1+(x2-x1)*.75f,y+9*u,x2,y) }
    drawPath(p,Ink,style=Stroke(u))
}
private fun DrawScope.rhythm(v: RhythmValue,x: Float,y: Float,u: Float,active: Boolean) {
    val color=if(active) RoseInk else Ink
    val head=Offset(x-5*u,y-3*u)
    if(v==RhythmValue.WHOLE || v==RhythmValue.HALF) drawOval(color,head,Size(11*u,7*u),style=Stroke(1.5f*u))
    else drawOval(color,head,Size(11*u,7*u))
    if(v!=RhythmValue.WHOLE) drawLine(color,Offset(x+5*u,y),Offset(x+5*u,y-29*u),1.2f*u)
}
private fun DrawScope.rest(v: RhythmValue,x: Float,y: Float,u: Float) {
    when(v) {
        RhythmValue.WHOLE -> { drawLine(Ink,Offset(x-10*u,y-5*u),Offset(x+10*u,y-5*u),u); drawRect(Ink,Offset(x-6*u,y-5*u),Size(12*u,5*u)) }
        RhythmValue.HALF -> { drawLine(Ink,Offset(x-10*u,y),Offset(x+10*u,y),u); drawRect(Ink,Offset(x-6*u,y-5*u),Size(12*u,5*u)) }
        RhythmValue.QUARTER -> {
            val p=Path().apply { moveTo(x-3*u,y-13*u); lineTo(x+4*u,y-5*u); lineTo(x-3*u,y); lineTo(x+4*u,y+6*u); cubicTo(x-7*u,y+2*u,x-6*u,y+10*u,x,y+13*u) }
            drawPath(p,Ink,style=Stroke(2.2f*u))
        }
        RhythmValue.EIGHTH -> {
            drawCircle(Ink,3*u,Offset(x-3*u,y-7*u))
            val p=Path().apply { moveTo(x-3*u,y-7*u); quadraticTo(x+2*u,y-2*u,x+6*u,y-11*u); lineTo(x,y+11*u) }
            drawPath(p,Ink,style=Stroke(1.8f*u))
        }
    }
}
