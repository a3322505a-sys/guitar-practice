package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF173E35)
val Green = Color(0xFF008563)
val Gold = Color(0xFFFFD370)
val Paper = Color(0xFFFFFCF5)
val Mint = Color(0xFFE0F4E9)
val Pink = Color(0xFFFFDDE8)
val RoseInk = Color(0xFFA42858)
val Sky = Color(0xFFDCEFFF)
val BlueInk = Color(0xFF236A9B)
val Lavender = Color(0xFFEEE1FF)
val PurpleInk = Color(0xFF69458F)
val Warm = Color(0xFFFFF0C9)
val Muted = Color(0xFF65796F)
val Outline = Color(0xFFDEE7E1)

@Composable
fun PracticeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary=Green, onPrimary=Color.White,
        primaryContainer=Mint, onPrimaryContainer=Ink, secondary=BlueInk, secondaryContainer=Sky,
        tertiary=PurpleInk, tertiaryContainer=Lavender, background=Paper, surface=Color.White,
        surfaceVariant=Mint, onSurface=Ink, onSurfaceVariant=Muted, outline=Outline), content=content)
}

@Composable
fun CandyCard(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier=modifier, shape=RoundedCornerShape(28.dp), border=BorderStroke(1.dp, Outline),
        colors=CardDefaults.cardColors(containerColor=color), elevation=CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp), content=content)
    }
}

/** Small native vector motif: no bitmap or network dependency, outside musical diagrams. */
@Composable
fun PickMotif(modifier: Modifier = Modifier) {
    Canvas(modifier.size(38.dp)) {
        val w=size.width; val h=size.height
        val p=Path().apply { moveTo(w*.2f,h*.18f); cubicTo(w*.45f,-h*.06f,w*.9f,h*.12f,w*.82f,h*.42f)
            cubicTo(w*.76f,h*.73f,w*.46f,h*1.04f,w*.32f,h*.85f); cubicTo(w*.13f,h*.65f,w*.04f,h*.36f,w*.2f,h*.18f); close() }
        drawPath(p,Gold)
        drawCircle(Color.White,w*.12f,Offset(w*.47f,h*.36f),style=Stroke(w*.055f))
    }
}
