package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
        val rows = remember(bars, maxWidth, fontScale) { tabSystems(bars, (maxWidth.value / fontScale).toInt()) }
        val target = cursor?.bar?.let { bar -> rows.indexOfFirst { row -> row.any { it.index == bar } } } ?: 0
        LaunchedEffect(sequence, target, following) {
            if (following && cursor?.pre == false && target >= 0) state.animateScrollToItem(target)
        }
        LazyColumn(state=state, verticalArrangement=Arrangement.spacedBy(12.dp)) {
            itemsIndexed(rows) { index, row ->
                // A dense single bar keeps its readable width even in split-screen/large text.
                val width=maxOf(maxWidth,((40+row.sumOf { it.readableWidth() })*fontScale).dp)
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    TabSystem(row, hint, key, cursor, index == rows.lastIndex, fontScale,sequence,Modifier.width(width))
                }
            }
        }
    }
}

private fun visible(segment: TabSegment, hint: Hint, key: Tonality?): List<Position> = when (hint) {
    Hint.FULL -> segment.positions
    Hint.HIDDEN -> emptyList()
    Hint.ROOTS -> segment.positions.filter { p -> key?.isRoot(p) ?: (segment.chord?.let { Chords.named(it).isRoot(p) } == true) }
}

@Composable
private fun TabSystem(bars: List<TabBar>, hint: Hint, key: Tonality?, cursor: Cursor?, last: Boolean, scale: Float, sequence: Sequence, modifier: Modifier) {
    val beatsPerBar=sequence.beatsPerBar
    val segments=bars.flatMap { it.segments }
    // Reserve lanes for the marks actually present, independent of window height.
    // Keep full-size strings/digits; small windows scroll complete systems vertically.
    val staffTop=if(segments.any { it.expression?.expressionLabel()?.isNotBlank()==true }) 54f
        else if(segments.any { it.chord!=null }) 48f else 32f
    // Rhythmic TAB: fret digits are the noteheads. Stems descend from the lowest
    // sounding digit to shared beams below string 6; there is no second note staff.
    val beamY=staffTop+5*18f+if(sequence.links.any { it.string==6 && segments.any { s -> s.tick==it.sourceTick || s.tick==it.targetTick } }) 34f else 24f
    val height=beamY+if(segments.any { it.tuplet!=null }) 32f else 14f
    val measurer = rememberTextMeasurer()
    val description = buildString {
        append("六线谱，第 ${bars.first().index+sequence.originalBar+1} 至 ${bars.last().index+sequence.originalBar+1} 小节。${bars.map { sequence.meter(it.index) }.distinct().joinToString("、") { "$it/4" }} 拍。上方 1 弦，下方 6 弦。")
        if(sequence.swing!=Swing.NONE) append("${sequence.swing.title}；")
        if(sequence.capoFret>0) append("变调夹${sequence.capoFret}品，数字为相对品位。")
        if(hint != Hint.HIDDEN) bars.forEach { bar -> bar.segments.forEach { s ->
                if(s.rest) append("休止 ${s.value.ticks/12.0} 拍；")
                else visible(s,hint,key).forEach { append("${it.string} 弦 ${it.fret} 品；") }
                s.expression?.let { e ->
                    if(s.strum && e.stroke==Pick.DOWN) append("下拨，从有效低音弦到高音弦；")
                    else if(s.strum && e.stroke==Pick.UP) append("上拨，从有效高音弦到低音弦；")
                    e.expressionLabel().takeIf { it.isNotBlank() }?.let { append("$it；") }
                }
                s.tuplet?.takeIf { s.tick==it.tick }?.let { append("三连音 3；") }
                s.outgoing?.let { append("${sequence.position(it.sourceTick,it.string).fret}${s.linkMark}${sequence.position(it.targetTick,it.string).fret}；") }
        } }
    }
    Canvas(modifier.height((height*scale).dp).semantics { contentDescription=description }) {
        val u=1.dp.toPx()*scale
        val left=32*u
        val top=staffTop*u
        val gap=18*u
        val beam=beamY*u
        val usable=size.width-left-8*u
        val total=bars.sumOf { it.readableWidth() }
        fun rowX(tick: Int): Float? {
            val target=bars.firstOrNull { it.index==sequence.barAt(tick.toDouble()) } ?: return null
            val preceding=bars.takeWhile { it.index!=target.index }.sumOf { it.readableWidth() }
            val width=usable*target.readableWidth()/total
            return left+usable*preceding/total+27*u+(tick-sequence.barStart(target.index)).toFloat()/(sequence.meter(target.index)*QUARTER)*(width-49*u)
        }
        var x0=left
        text(measurer,"TAB",13*u,top+2.5f*gap,Muted,11)
        for(string in 1..6) text(measurer,"$string",24*u,top+(string-1)*gap,Muted,10)
        if(bars.first().index == 0) text(measurer,"$beatsPerBar/4",14*u,maxOf(14*u,top-34*u),Ink,12)
        for(bar in bars) {
            val width=usable*bar.readableWidth()/total
            val current=cursor?.pre == false && cursor.bar == bar.index
            if(current) drawRoundRect(Mint,Offset(x0-3*u,4*u),Size(width,(height-12)*u),androidx.compose.ui.geometry.CornerRadius(12*u))
            text(measurer,"${bar.index+sequence.originalBar+1}",x0+10*u,16*u,if(current) Green else Muted,12)
            if(bar.index>0 && sequence.meter(bar.index)!=sequence.meter(bar.index-1)) text(measurer,"${sequence.meter(bar.index)}/4",x0+30*u,16*u,Ink,12)
            if(bar.index==0 && sequence.swing!=Swing.NONE) text(measurer,sequence.swing.title,x0+width/2,16*u,PurpleInk,10)
            for(i in 0..5) drawLine(Color(0xFFA0B4A9),Offset(x0,top+i*gap),Offset(x0+width,top+i*gap),u*.8f)
            drawLine(Ink,Offset(x0,top),Offset(x0,top+5*gap),u)
            val start=x0+27*u
            val span=width-49*u
            val barTicks=sequence.meter(bar.index)*QUARTER
            fun at(s: TabSegment) = start + (s.tick-sequence.barStart(bar.index)).toFloat()/barTicks*span
            for((i,s) in bar.segments.withIndex()) {
                val x=at(s)
                val active=current && cursor!!.localTick >= s.tick && cursor.localTick < s.end
                if(active) drawRoundRect(if(hint==Hint.HIDDEN) Mint else Pink,Offset(x-12*u,top-13*u),Size(24*u,5*gap+26*u),androidx.compose.ui.geometry.CornerRadius(10*u))
                val notes=visible(s,hint,key)
                if(hint != Hint.HIDDEN) {
                    val previous=bar.segments.getOrNull(i-1)
                    if(s.chord!=null && (previous?.chord != s.chord || i==0) && !s.tieIn) text(measurer,s.chord,x,top-26*u,PurpleInk,13)
                    if(s.rest) {
                        val restY=top+2.5f*gap
                        drawRect(if(current) Mint else Color.White,Offset(x-11*u,restY-17*u),Size(22*u,34*u))
                        rest(s.value,x,restY,u)
                    }
                    else {
                        val strings=notes.map { it.string } + if(hint==Hint.FULL) s.expression?.deadStrings.orEmpty() else emptySet()
                        val digitY=top+((strings.maxOrNull() ?: 6)-1)*gap
                        val color=if(active) RoseInk else Ink
                        if(s.value!=RhythmValue.WHOLE) {
                            drawLine(color,Offset(x,digitY+7*u),Offset(x,beam),1.2f*u)
                            // Full rhythmic tablature uses a double stem for a half note.
                            if(s.value==RhythmValue.HALF) drawLine(color,Offset(x+3*u,digitY+7*u),Offset(x+3*u,beam),1.2f*u)
                        }
                        if(s.value==RhythmValue.DOTTED_EIGHTH) drawCircle(color,1.6f*u,Offset(x+13*u,digitY+5*u))
                        if(hint==Hint.FULL) s.expression?.deadStrings?.forEach { string ->
                            text(measurer,"x",x,top+(string-1)*gap,if(active) RoseInk else Ink,15,true)
                        }
                        for(p in notes) {
                            val y=top+(p.string-1)*gap
                            drawRoundRect(if(active) Pink else if(current) Mint else Color.White,Offset(x-9*u,y-9*u),Size(18*u,18*u),androidx.compose.ui.geometry.CornerRadius(4*u))
                            text(measurer,p.fret.toString(),x,y,if(active) RoseInk else Ink,15,true)
                            if(s.tick==s.eventId) s.expression?.stringExpression?.get(p.string)?.let { expression ->
                                if(expression.short) drawCircle(PurpleInk,1.6f*u,Offset(x,y-13*u))
                                expression.grace?.let { grace ->
                                    text(measurer,"${grace.fret}/",x-19*u,y-6*u,PurpleInk,10,true)
                                    drawLine(PurpleInk,Offset(x-22*u,y+5*u),Offset(x-16*u,y-3*u),u)
                                }
                                val mark=if(expression.bend.isNotEmpty()) "b${expression.bend.maxOf { it.semitones }}" else when(expression.slide) { 4->"↘";8,16->"↗";else->"" }
                                if(mark.isNotEmpty()) text(measurer,mark,x+15*u,y-9*u,PurpleInk,9,true)
                            }
                            if(s.tieOut) {
                                // Continue one arc across an in-system barline. Split it
                                // only at a real system break, never into a second tie.
                                val endX = bar.segments.getOrNull(i+1)?.let { at(it)-9*u }
                                    ?: if(bar != bars.last()) x0+width+18*u else x0+width-3*u
                                tie(x+9*u,endX,y+6*u,u)
                            }
                            if(s.tieIn && i==0 && bar==bars.first()) tie(x0+3*u,x-9*u,y+6*u,u)
                        }
                    }
                    if(s.tick==s.eventId && !s.rest) {
                        val expression=s.expression
                        val previousVelocity=sequence.events.lastOrNull { it.tick<s.eventId && !it.rest }?.velocity ?: 1f
                        val marks=listOfNotNull(if(expression?.accent==true) ">" else null,if(expression?.velocity!=null && expression.velocity<1f && expression.velocity!=previousVelocity) "弱" else null,
                            expression?.positions?.filter { expression.muted(it.string) }?.takeIf { it.isNotEmpty() }?.let { "P.M." },if(expression?.short==true) "·" else null,
                            if(expression?.vibrato==true) "~" else null,
                            if(expression?.stroke==Pick.UP) "↑" else if(expression?.stroke==Pick.DOWN && !s.strum) "↓" else null).joinToString(" ")
                        if(marks.isNotEmpty()) text(measurer,marks,x,top-43*u,PurpleInk,10)
                    }
                    s.tuplet?.takeIf { s.tick==it.tick }?.let { group ->
                        val right=rowX(group.end-group.unit) ?: x
                        val mid=(x+right)/2
                        val bracketY=beam+11*u
                        drawLine(PurpleInk,Offset(x,bracketY),Offset(mid-7*u,bracketY),u)
                        drawLine(PurpleInk,Offset(mid+7*u,bracketY),Offset(right,bracketY),u)
                        drawLine(PurpleInk,Offset(x,bracketY),Offset(x,bracketY-4*u),u)
                        drawLine(PurpleInk,Offset(right,bracketY),Offset(right,bracketY-4*u),u)
                        text(measurer,"3",mid,bracketY,PurpleInk,11,true)
                    }
                    sequence.links.filter { it.sourceTick==s.tick }.forEach { link ->
                        val y=top+(link.string-1)*gap+8*u
                        val right=rowX(link.targetTick)?.minus(9*u) ?: x0+width-3*u
                        tie(x+9*u,right,y,u)
                        text(measurer,sequence.linkMark(link),(x+right)/2,y+9*u,PurpleInk,11,true)
                    }
                    sequence.links.filter { it.targetTick==s.tick && rowX(it.sourceTick)==null }.forEach { link ->
                        val y=top+(link.string-1)*gap+8*u
                        tie(x0+3*u,x-9*u,y,u);text(measurer,sequence.linkMark(link),x-15*u,y+9*u,PurpleInk,11,true)
                    }
                    if(s.strum) {
                        val ax=x-15*u
                        // TAB has high strings on top: a low-to-high guitar downstroke
                        // travels upward on the staff. Do not cross the muted strings.
                        val strings=s.expression!!.strings
                        val bottom=top+(strings.max()-1)*gap+5*u
                        val high=top+(strings.min()-1)*gap-5*u
                        val up=s.expression.stroke==Pick.UP
                        val tail=if(up) high else bottom;val head=if(up) bottom else high;val direction=if(up) -1 else 1
                        drawLine(PurpleInk,Offset(ax,tail),Offset(ax,head),1.5f*u)
                        drawLine(PurpleInk,Offset(ax,head),Offset(ax-3*u,head+direction*5*u),1.5f*u)
                        drawLine(PurpleInk,Offset(ax,head),Offset(ax+3*u,head+direction*5*u),1.5f*u)
                    }
                    // One/two beams for eighths/sixteenths. Never cross a beat or rest.
                    val next=bar.segments.getOrNull(i+1)
                    if(!s.rest) {
                        val before=beamCount(previous,s)
                        val after=beamCount(s,next)
                        repeat(s.value.flags) { level ->
                            val y=beam-6*level*u
                            if(after>level) drawLine(Ink,Offset(x,y),Offset(at(next!!),y),3*u)
                            else if(before<=level) {
                                if(level>0 && (before>0 || after>0)) {
                                    val direction=if(before>0) -1 else 1
                                    drawLine(Ink,Offset(x,y),Offset(x+8*direction*u,y),3*u)
                                } else {
                                    val flag=Path().apply { moveTo(x,y); cubicTo(x+13*u,y-9*u,x+10*u,y-14*u,x+7*u,y-19*u) }
                                    drawPath(flag,Ink,style=Stroke(2*u))
                                }
                            }
                        }
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
private fun DrawScope.rest(v: RhythmValue,x: Float,y: Float,u: Float) {
    when(v) {
        RhythmValue.WHOLE -> { drawLine(Ink,Offset(x-10*u,y-5*u),Offset(x+10*u,y-5*u),u); drawRect(Ink,Offset(x-6*u,y-5*u),Size(12*u,5*u)) }
        RhythmValue.HALF -> { drawLine(Ink,Offset(x-10*u,y),Offset(x+10*u,y),u); drawRect(Ink,Offset(x-6*u,y-5*u),Size(12*u,5*u)) }
        RhythmValue.QUARTER -> {
            val p=Path().apply { moveTo(x-3*u,y-13*u); lineTo(x+4*u,y-5*u); lineTo(x-3*u,y); lineTo(x+4*u,y+6*u); cubicTo(x-7*u,y+2*u,x-6*u,y+10*u,x,y+13*u) }
            drawPath(p,Ink,style=Stroke(2.2f*u))
        }
        RhythmValue.EIGHTH, RhythmValue.DOTTED_EIGHTH, RhythmValue.SIXTEENTH, RhythmValue.TRIPLET_EIGHTH, RhythmValue.TRIPLET_SIXTEENTH -> {
            repeat(v.flags) { level ->
                val yy=y+level*6*u
                drawCircle(Ink,3*u,Offset(x-3*u,yy-7*u))
                val p=Path().apply { moveTo(x-3*u,yy-7*u); quadraticTo(x+2*u,yy-2*u,x+6*u,yy-11*u); lineTo(x,y+11*u) }
                drawPath(p,Ink,style=Stroke(1.8f*u))
            }
            if(v==RhythmValue.DOTTED_EIGHTH) drawCircle(Ink,1.6f*u,Offset(x+12*u,y-6*u))
        }
    }
}
