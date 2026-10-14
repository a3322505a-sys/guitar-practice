package com.a3322505a.guitarlearning.ui

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import com.a3322505a.guitarlearning.practice.RhythmValue

internal fun DrawScope.rest(v: RhythmValue,x: Float,y: Float,u: Float) {
    when(v) {
        RhythmValue.WHOLE -> { drawLine(Ink,Offset(x-10*u,y-5*u),Offset(x+10*u,y-5*u),u); drawRect(Ink,Offset(x-6*u,y-5*u),Size(12*u,5*u)) }
        RhythmValue.HALF -> { drawLine(Ink,Offset(x-10*u,y),Offset(x+10*u,y),u); drawRect(Ink,Offset(x-6*u,y-5*u),Size(12*u,5*u)) }
        RhythmValue.QUARTER -> {
            val p=Path().apply { moveTo(x-3*u,y-13*u); lineTo(x+4*u,y-5*u); lineTo(x-3*u,y); lineTo(x+4*u,y+6*u); cubicTo(x-7*u,y+2*u,x-6*u,y+10*u,x,y+13*u) }
            drawPath(p,Ink,style=Stroke(2.2f*u))
        }
        RhythmValue.EIGHTH, RhythmValue.DOTTED_EIGHTH, RhythmValue.SIXTEENTH, RhythmValue.THIRTY_SECOND, RhythmValue.SIXTY_FOURTH,
        RhythmValue.TRIPLET_EIGHTH, RhythmValue.TRIPLET_SIXTEENTH, RhythmValue.TRIPLET_THIRTY_SECOND -> {
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
