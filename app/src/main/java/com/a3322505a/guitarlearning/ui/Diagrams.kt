package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.music.*
import kotlin.math.hypot

enum class Hint(val title: String) { FULL("完整提示"), ROOTS("仅主音／根音"), HIDDEN("隐藏音位／指法") }

private fun DrawScope.label(measurer: TextMeasurer, value: String, x: Float, y: Float, color: Color = Ink, size: Int = 13) {
    val layout = measurer.measure(value, TextStyle(color=color, fontSize=size.sp))
    drawText(layout, topLeft=Offset(x-layout.size.width/2f, y-layout.size.height/2f))
}

@Composable
fun Fretboard(positions: List<Position>, key: Tonality, hint: Hint, degrees: Boolean, current: List<Position>, enabled: Boolean, height: androidx.compose.ui.unit.Dp = 238.dp, onTap: (Position)->Unit) {
    val visible = when(hint) { Hint.FULL -> positions; Hint.ROOTS -> positions.filter(key::isRoot); Hint.HIDDEN -> emptyList() }
    val low = positions.minOf { it.fret }
    val high = positions.maxOf { it.fret }
    val columns = high-low+1
    val measurer=rememberTextMeasurer()
    val diagramHeight=height
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=maxOf(maxWidth, (columns*36+56).dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(width).height(diagramHeight).pointerInput(visible, enabled, onTap) {
                if(enabled) detectTapGestures { tap ->
                    val left=46.dp.toPx(); val cell=(size.width-left-10.dp.toPx())/columns
                    val vertical=size.height/238.dp.toPx()
                    val top=40.dp.toPx()*vertical; val row=32.dp.toPx()*vertical
                    visible.minByOrNull { p -> hypot((tap.x-(left+(p.fret-low+0.5f)*cell)).toDouble(), (tap.y-(top+(p.string-1)*row)).toDouble()) }
                        ?.let { p ->
                            if(hypot((tap.x-(left+(p.fret-low+0.5f)*cell)).toDouble(),(tap.y-(top+(p.string-1)*row)).toDouble()) < 23.dp.toPx()) onTap(p)
                        }
                }
            }) {
                val left=46.dp.toPx(); val cell=(size.width-left-10.dp.toPx())/columns
                val vertical=size.height/238.dp.toPx()
                val top=40.dp.toPx()*vertical; val row=32.dp.toPx()*vertical
                drawRoundRect(Color(0xFFEDF7F0), Offset(left,top-12.dp.toPx()*vertical), androidx.compose.ui.geometry.Size(size.width-left,5*row+24.dp.toPx()*vertical), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                for(i in 0..columns) drawLine(Color(0xFFB9AA94),Offset(left+i*cell,top-12.dp.toPx()*vertical),Offset(left+i*cell,top+5*row+12.dp.toPx()*vertical),1.2.dp.toPx())
                for(i in 0 until columns) label(measurer, if(low+i==0) "0 / O" else "${low+i}",left+(i+0.5f)*cell,16.dp.toPx()*vertical)
                if(low==0) drawLine(Ink,Offset(left+cell,top-12.dp.toPx()*vertical),Offset(left+cell,top+5*row+12.dp.toPx()*vertical),3.dp.toPx())
                for(s in 1..6) {
                    val y=top+(s-1)*row
                    drawLine(Color(0xFF9E9688),Offset(left,y),Offset(size.width-10.dp.toPx(),y),(0.45+s*0.12).dp.toPx())
                    label(measurer,"$s ${Music.name(Music.openMidi[s-1])}",21.dp.toPx(),y,size=12)
                }
                for(p in visible) {
                    val center=Offset(left+(p.fret-low+0.5f)*cell,top+(p.string-1)*row)
                    val root=key.isRoot(p)
                    val active=p in current
                    drawCircle(if(active) Pink else if(root) Gold else Green,13.dp.toPx()*vertical,center)
                    if(active) drawCircle(Ink,16.dp.toPx()*vertical,center,style=Stroke(2.dp.toPx()))
                    label(measurer,if(degrees) key.degree(p.midi).toString() else Music.name(p.midi),center.x,center.y,if(root||active) Ink else Color.White)
                }
                label(measurer,"上为 1 弦，下为 6 弦 · 数字为品位",size.width/2,225.dp.toPx()*vertical,size=12)
            }
        }
    }
}

@Composable
fun ChordDiagram(chord: Chord, hint: Hint, current: List<Position>, enabled: Boolean, height: androidx.compose.ui.unit.Dp = 300.dp, onTap: (Position)->Unit) {
    val measurer=rememberTextMeasurer()
    val diagramHeight=height
    val visible=when(hint) { Hint.FULL -> chord.positions; Hint.ROOTS -> chord.positions.filter { it.midi%12==chord.root }; Hint.HIDDEN -> emptyList() }
    Canvas(Modifier.fillMaxWidth().height(diagramHeight).testTag("chord-diagram").pointerInput(chord,hint,enabled,onTap) {
        if(enabled) detectTapGestures { tap ->
            val geometry=ChordGeometry(size.width.toFloat(),size.height.toFloat())
            val stringIndex=(0..5).minBy { kotlin.math.abs(tap.x-(geometry.left+it*geometry.stringStep)) }
            visible.firstOrNull { it.string==6-stringIndex }?.let { p ->
                if((tap-geometry.position(p)).getDistance() <= maxOf(22.dp.toPx(),geometry.unit*20)) onTap(p)
            }
        }
    }) {
        val geometry=ChordGeometry(size.width,size.height)
        val left=geometry.left; val step=geometry.stringStep
        val top=geometry.top; val fretStep=geometry.fretStep
        val right=left+5*step
        // One scale for both axes: the grid stays square, even inside a wide card.
        drawRoundRect(Color(0xFFF4F8F3),Offset(left,top),androidx.compose.ui.geometry.Size(5*step,4*fretStep),androidx.compose.ui.geometry.CornerRadius(5*geometry.unit))
        for(i in 0..4) drawLine(if(i==0) Ink else Color(0xFFB7B9AF),Offset(left,top+i*fretStep),Offset(right,top+i*fretStep),if(i==0) 4.dp.toPx() else 1.dp.toPx())
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
            val root=p.midi%12==chord.root
            val active=p in current
            drawCircle(if(active) Pink else if(root) Gold else Green,13*geometry.unit,center)
            label(measurer,if(hint==Hint.ROOTS) "根" else if(p.fret==0) "O" else chord.fingers[6-p.string].toString(),center.x,center.y,if(root||active) Ink else Color.White)
            if(active) drawCircle(Ink,17*geometry.unit,center,style=Stroke(2.dp.toPx()))
        }
        for(i in 1..4) label(measurer,"$i",left-23*geometry.unit,top+(i-0.5f)*fretStep,size=12)
    }
}

private class ChordGeometry(width: Float, height: Float) {
    val unit=minOf(width/260f,height/300f)
    val stringStep=36*unit
    val fretStep=45*unit
    val left=width/2-90*unit
    val top=(height-300*unit)/2+54*unit
    val openY=top-26*unit
    val noteY=top+207*unit
    val stringY=top+236*unit
    fun position(p: Position)=Offset(left+(6-p.string)*stringStep,if(p.fret==0) openY else top+(p.fret-.5f)*fretStep)
}
