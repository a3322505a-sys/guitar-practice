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
    Canvas(Modifier.fillMaxWidth().height(diagramHeight).pointerInput(chord,hint,enabled,onTap) {
        if(enabled) detectTapGestures { tap ->
            val left=32.dp.toPx(); val step=(size.width-64.dp.toPx())/5
            val vertical=size.height/300.dp.toPx()
            val closest=(0..5).minBy { kotlin.math.abs(tap.x-(left+it*step)) }
            val p=visible.firstOrNull { it.string==6-closest }
            if(p!=null && kotlin.math.abs(tap.x-(left+closest*step))<step/2 && tap.y in (25.dp.toPx()*vertical)..(242.dp.toPx()*vertical)) onTap(p)
        }
    }) {
        val left=32.dp.toPx(); val step=(size.width-64.dp.toPx())/5
        val vertical=size.height/300.dp.toPx()
        val top=64.dp.toPx()*vertical; val fretStep=40.dp.toPx()*vertical
        for(i in 0..4) drawLine(if(i==0) Ink else Color(0xFFB7B9AF),Offset(left,top+i*fretStep),Offset(size.width-left,top+i*fretStep),if(i==0) 4.dp.toPx() else 1.dp.toPx())
        for(i in 0..5) {
            val x=left+i*step
            drawLine(Color(0xFF8B998E),Offset(x,top),Offset(x,top+4*fretStep),(1.1-i*0.11).dp.toPx())
            label(measurer,"${6-i} 弦",x,281.dp.toPx()*vertical,size=12)
            if(hint==Hint.FULL) {
                label(measurer,chord.label(i),x,253.dp.toPx()*vertical,size=15)
                when(chord.frets[i]) { null -> label(measurer,"X",x,38.dp.toPx()*vertical); 0 -> label(measurer,"O",x,38.dp.toPx()*vertical) }
            }
        }
        for(p in visible) {
            val x=left+(6-p.string)*step
            val y=if(p.fret==0) 38.dp.toPx()*vertical else top+(p.fret-0.5f)*fretStep
            val root=p.midi%12==chord.root
            val changed=chord.name in listOf("D","Dm","A","Am") && if(chord.name.startsWith("D")) p.string==1 else p.string==2
            val active=p in current
            if(p.fret>0 || hint==Hint.ROOTS) {
                drawCircle(if(active) Pink else if(root) Gold else Green,13.dp.toPx()*vertical,Offset(x,y))
                label(measurer,if(hint==Hint.FULL && p.fret>0) chord.fingers[6-p.string].toString() else "根",x,y,if(root||active) Ink else Color.White)
            }
            if(active || changed && hint==Hint.FULL) drawCircle(if(active) Ink else Color(0xFF9364AE),17.dp.toPx()*vertical,Offset(x,y),style=Stroke(2.dp.toPx()))
        }
        for(i in 1..4) label(measurer,"$i",12.dp.toPx(),top+(i-0.5f)*fretStep,size=12)
    }
}
