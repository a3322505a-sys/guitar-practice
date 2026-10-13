package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.MovingPosition
import kotlin.math.hypot

enum class Hint(val title: String) { FULL("完整提示"), ROOTS("仅主音／根音"), HIDDEN("隐藏音位／指法") }

private fun DrawScope.label(measurer: TextMeasurer, value: String, x: Float, y: Float, color: Color = Ink, size: Int = 13) {
    val layout = measurer.measure(value, TextStyle(color=color, fontSize=size.sp))
    drawText(layout, topLeft=Offset(x-layout.size.width/2f, y-layout.size.height/2f))
}

@Composable
fun Fretboard(positions: List<Position>, key: Tonality?, hint: Hint, degrees: Boolean, current: List<Position>, enabled: Boolean, height: androidx.compose.ui.unit.Dp = 238.dp, onTap: (Position)->Unit, moving: MovingPosition? = null,
    upcoming: List<Position> = emptyList(), showLegend: Boolean = true, capoFret: Int = 0,
    openStringMidi: List<Int> = Music.openMidi.toList(),movingPositions: List<MovingPosition> = moving?.let { listOf(it) }.orEmpty()) {
    if(positions.isNotEmpty() && positions.all { it.fret==0 }) {
        OpenStringBoard(positions,key,hint,current,upcoming,enabled,onTap,capoFret,openStringMidi)
        return
    }
    val visible = when(hint) { Hint.FULL -> positions; Hint.ROOTS -> if(key==null) positions else positions.filter(key::isRoot); Hint.HIDDEN -> emptyList() }
    val hasOpen=positions.any { it.fret==0 }
    val low = positions.filter { it.fret>0 }.minOf { it.fret }.let { if(hasOpen) 1 else it }
    val high = positions.maxOf { it.fret }
    val columns = high-low+1
    val measurer=rememberTextMeasurer()
    val diagramHeight=height
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=maxOf(maxWidth, (columns*36+56+if(hasOpen) 36 else 0).dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(width).height(diagramHeight).testTag("fretboard").semantics {
                contentDescription=if(hint==Hint.HIDDEN) "指板，提示已隐藏" else buildString {
                    append("指板，上为 1 弦，下为 6 弦。")
                    if(capoFret>0) append("变调夹${capoFret}品，品位相对变调夹，音名为实际发声。")
                    current.filter { it in visible }.forEach { append("当前 ${it.string} 弦 ${it.fret} 品；") }
                    upcoming.filter { it in visible }.forEach { append("下一 ${it.string} 弦 ${it.fret} 品；") }
                }
            }.pointerInput(visible, enabled, onTap) {
                if(enabled) detectTapGestures { tap ->
                    val left=46.dp.toPx(); val gridLeft=left+if(hasOpen) 36.dp.toPx() else 0f
                    val cell=(size.width-gridLeft-10.dp.toPx())/columns
                    fun x(fret: Int)=if(fret==0) left+18.dp.toPx() else gridLeft+(fret-low+.5f)*cell
                    val vertical=size.height/238.dp.toPx()
                    val top=40.dp.toPx()*vertical; val row=32.dp.toPx()*vertical
                    visible.minByOrNull { p -> hypot((tap.x-x(p.fret)).toDouble(), (tap.y-(top+(p.string-1)*row)).toDouble()) }
                        ?.let { p ->
                            if(hypot((tap.x-x(p.fret)).toDouble(),(tap.y-(top+(p.string-1)*row)).toDouble()) < 23.dp.toPx()) onTap(p)
                        }
                }
            }) {
                val left=46.dp.toPx(); val gridLeft=left+if(hasOpen) 36.dp.toPx() else 0f
                val cell=(size.width-gridLeft-10.dp.toPx())/columns
                fun x(fret: Float)=if(fret==0f) left+18.dp.toPx() else gridLeft+(fret-low+.5f)*cell
                val vertical=size.height/238.dp.toPx()
                val top=40.dp.toPx()*vertical; val row=32.dp.toPx()*vertical
                drawRoundRect(Color(0xFFEDF7F0), Offset(left,top-12.dp.toPx()*vertical), androidx.compose.ui.geometry.Size(size.width-left,5*row+24.dp.toPx()*vertical), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                for(i in 0..columns) drawLine(Color(0xFFB9AA94),Offset(gridLeft+i*cell,top-12.dp.toPx()*vertical),Offset(gridLeft+i*cell,top+5*row+12.dp.toPx()*vertical),1.2.dp.toPx())
                for(i in 0 until columns) label(measurer,"${low+i}",gridLeft+(i+0.5f)*cell,16.dp.toPx()*vertical)
                if(hasOpen) {
                    label(measurer,"O",x(0f),16.dp.toPx()*vertical)
                    drawLine(Ink,Offset(gridLeft,top-12.dp.toPx()*vertical),Offset(gridLeft,top+5*row+12.dp.toPx()*vertical),3.dp.toPx())
                }
                for(s in 1..6) {
                    val y=top+(s-1)*row
                    drawLine(Color(0xFF9E9688),Offset(left,y),Offset(size.width-10.dp.toPx(),y),(0.45+s*0.12).dp.toPx())
                    label(measurer,"$s ${Music.name(openStringMidi[s-1]+capoFret)}",21.dp.toPx(),y,size=12)
                }
                for(p in visible) {
                    val center=Offset(x(p.fret.toFloat()),top+(p.string-1)*row)
                    val root=key?.isRoot(p)==true
                    val active=p in current
                    drawCircle(if(active) Pink else if(root) Gold else Green,13.dp.toPx()*vertical,center)
                    if(active) drawCircle(Ink,16.dp.toPx()*vertical,center,style=Stroke(2.dp.toPx()))
                    if(p in upcoming) drawCircle(BlueInk,19.dp.toPx()*vertical,center,style=Stroke(2.dp.toPx()))
                    label(measurer,if(degrees && key!=null) key.degree(openStringMidi[p.string-1]+p.fret+capoFret).toString() else Music.name(openStringMidi[p.string-1]+p.fret+capoFret),center.x,center.y,if(root||active) Ink else Color.White)
                }
                movingPositions.forEachIndexed { moveIndex,moving -> if(hint!=Hint.HIDDEN) {
                    val y=top+(moving.source.string-1)*row
                    val a=x(moving.source.fret.toFloat());val b=x(moving.target.fret.toFloat())
                    drawLine(PurpleInk,Offset(a,y),Offset(b,y),3.dp.toPx())
                    val movingX=if(hasOpen && moving.fret<1) x(0f)+(x(1f)-x(0f))*moving.fret else x(moving.fret.toFloat())
                    drawCircle(Pink,9.dp.toPx(),Offset(movingX,y))
                    label(measurer,"${moving.source.fret}${moving.mark}${moving.target.fret}",(moveIndex+1)*size.width/(movingPositions.size+1),
                        205.dp.toPx()*vertical,color=PurpleInk)
                } }
                if(showLegend) label(measurer,"上为 1 弦，下为 6 弦 · 数字为品位",size.width/2,225.dp.toPx()*vertical,size=12)
            }
        }
    }
}

/** Open strings have no fret grid. Reserve six compact, labeled audition targets. */
@Composable
private fun OpenStringBoard(positions: List<Position>, key: Tonality?, hint: Hint, current: List<Position>, upcoming: List<Position>, enabled: Boolean, onTap: (Position)->Unit, capoFret: Int = 0,
    openStringMidi: List<Int> = Music.openMidi.toList()) {
    val visible=when(hint) { Hint.FULL -> positions; Hint.ROOTS -> if(key==null) positions else positions.filter(key::isRoot); Hint.HIDDEN -> emptyList() }
    val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth().testTag("fretboard").semantics {
        contentDescription=if(hint==Hint.HIDDEN) "指板，提示已隐藏" else buildString {
            append("空弦指板，0 品，无需按品。左为 6 弦，右为 1 弦。")
            current.filter { it in visible }.forEach { append("当前 ${it.string} 弦 0 品；") }
            upcoming.filter { it in visible }.forEach { append("下一 ${it.string} 弦 0 品；") }
        }
    }.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(hint==Hint.HIDDEN) "按节拍演奏" else "空弦 · 无需按品",style=MaterialTheme.typography.labelLarge,color=Muted)
        Row(Modifier.widthIn(max=600.dp).fillMaxWidth().testTag("open-string-board"),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            for(string in 6 downTo 1) {
                val position=Position(string,0)
                val shown=position in visible
                val active=shown && position in current
                val next=shown && position in upcoming
                Surface(onClick={onTap(position)},enabled=enabled && shown,modifier=Modifier.weight(1f).height(76.dp*fontScale).testTag("open-string-$string"),shape=RoundedCornerShape(12.dp),
                    color=if(active) Pink else if(shown) Mint else Color(0xFFF1F4F5),
                    border=BorderStroke(if(active || next) 2.dp else 1.dp,if(active) RoseInk else if(next) BlueInk else Outline)) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Text("$string 弦",style=MaterialTheme.typography.labelSmall,color=Muted)
                        Text(if(shown) Music.name(openStringMidi[position.string-1]+position.fret+capoFret,true) else "—",style=MaterialTheme.typography.labelLarge,color=if(shown) Ink else Muted)
                        Text(if(shown) "0" else "",style=MaterialTheme.typography.labelSmall,color=Green)
                    }
                }
            }
        }
    }
}

@Composable
fun ChordDiagram(chord: Chord, hint: Hint, current: List<Position>, enabled: Boolean, height: androidx.compose.ui.unit.Dp = 300.dp, onTap: (Position)->Unit) {
    val measurer=rememberTextMeasurer()
    val diagramHeight=height
    val visible=when(hint) { Hint.FULL -> chord.positions; Hint.ROOTS -> chord.positions.filter(chord::isRoot); Hint.HIDDEN -> emptyList() }
    Canvas(Modifier.fillMaxWidth().height(diagramHeight).testTag("chord-diagram").semantics {
        contentDescription=if(hint==Hint.HIDDEN) "和弦图，提示已隐藏" else buildString {
            append("${chord.name}，${chord.conditions}，起始相对品位${chord.baseFret}。")
            visible.forEach { append("${it.string} 弦 ${it.fret} 品，${chord.noteName(it,true)} ${chord.role(it)}；") }
        }
    }.pointerInput(chord,hint,enabled,onTap) {
        if(enabled) detectTapGestures { tap ->
            val geometry=ChordGeometry(size.width.toFloat(),size.height.toFloat(),chord.baseFret)
            val stringIndex=(0..5).minBy { kotlin.math.abs(tap.x-(geometry.left+it*geometry.stringStep)) }
            visible.firstOrNull { it.string==6-stringIndex }?.let { p ->
                if((tap-geometry.position(p)).getDistance() <= maxOf(22.dp.toPx(),geometry.unit*20)) onTap(p)
            }
        }
    }) {
        val geometry=ChordGeometry(size.width,size.height,chord.baseFret)
        val left=geometry.left; val step=geometry.stringStep
        val top=geometry.top; val fretStep=geometry.fretStep
        val right=left+5*step
        // One scale for both axes: the grid stays square, even inside a wide card.
        drawRoundRect(Color(0xFFF4F8F3),Offset(left,top),androidx.compose.ui.geometry.Size(5*step,4*fretStep),androidx.compose.ui.geometry.CornerRadius(5*geometry.unit))
        for(i in 0..4) drawLine(if(i==0 && chord.baseFret==1) Ink else Color(0xFFB7B9AF),Offset(left,top+i*fretStep),Offset(right,top+i*fretStep),if(i==0 && chord.baseFret==1) 4.dp.toPx() else 1.dp.toPx())
        for(i in 0..5) {
            val x=left+i*step
            drawLine(Color(0xFF8B998E),Offset(x,top),Offset(x,top+4*fretStep),(1.1-i*0.11).dp.toPx())
            label(measurer,"${6-i}",x,geometry.stringY,size=12)
            if(hint==Hint.FULL) {
                label(measurer,chord.label(i),x,geometry.noteY,size=13)
                if(chord.frets[i]==null) label(measurer,"X",x,geometry.openY,color=Muted)
            }
        }
        for(p in visible) {
            val center=geometry.position(p)
            val root=chord.isRoot(p)
            val active=p in current
            drawCircle(if(active) Pink else if(root) Gold else Green,13*geometry.unit,center)
            label(measurer,if(hint==Hint.ROOTS) "根" else if(p.fret==0) "O" else chord.fingers[6-p.string].toString(),center.x,center.y,if(root||active) Ink else Color.White)
            if(active) drawCircle(Ink,17*geometry.unit,center,style=Stroke(2.dp.toPx()))
        }
        for(i in 1..4) label(measurer,"${chord.baseFret+i-1}",left-23*geometry.unit,top+(i-0.5f)*fretStep,size=12)
    }
}

internal class ChordGeometry(width: Float, height: Float, private val baseFret: Int = 1) {
    val unit=minOf(width/260f,height/300f)
    val stringStep=36*unit
    val fretStep=45*unit
    val left=width/2-90*unit
    val top=(height-300*unit)/2+54*unit
    val openY=top-26*unit
    val noteY=top+207*unit
    val stringY=top+236*unit
    fun position(p: Position)=Offset(left+(6-p.string)*stringStep,if(p.fret==0) openY else top+(p.fret-baseFret+.5f)*fretStep)
}
