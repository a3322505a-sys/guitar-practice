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
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF183C42)
val Green = Color(0xFF007E62)
val Gold = Color(0xFFFFC84C)
val Paper = Color(0xFFEDE8FB)
val Mint = Color(0xFFA8EDD3)
val Pink = Color(0xFFFFB9D4)
val RoseInk = Color(0xFFA01855)
val Sky = Color(0xFFAEDCFF)
val BlueInk = Color(0xFF175DAB)
val Lavender = Color(0xFFD2BCFB)
val PurpleInk = Color(0xFF623BA2)
val Warm = Color(0xFFFFE39C)
val Muted = Color(0xFF546979)
val Outline = Color(0xFFCDD9E7)

/** A consistent candy palette; music diagrams keep their existing semantic colors. */
fun candyInk(color: Color): Color = when(color) {
    Pink -> RoseInk
    Sky -> BlueInk
    Lavender -> PurpleInk
    Warm, Gold -> Color(0xFF80501C)
    else -> Color(0xFF00664F)
}

fun candyBrush(color: Color) = Brush.linearGradient(listOf(color, lerp(color, Color.White, .55f)))

fun Modifier.practiceBackground(): Modifier = drawWithCache {
    val wash = Brush.linearGradient(listOf(Color(0xFFD9EAFF), Paper, Color(0xFFFFDEEB)))
    onDrawBehind {
        drawRect(wash)
        drawCircle(Mint.copy(alpha=.55f), size.width*.38f, Offset(-size.width*.13f, size.height*.23f))
        drawCircle(Lavender.copy(alpha=.5f), size.width*.4f, Offset(size.width*1.15f, size.height*.6f))
        drawCircle(Warm.copy(alpha=.7f), size.width*.17f, Offset(size.width*.98f, size.height*.92f))
    }
}

@Composable
fun PracticeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary=Green, onPrimary=Color.White,
            primaryContainer=Mint, onPrimaryContainer=Ink, secondary=BlueInk, secondaryContainer=Sky,
            tertiary=PurpleInk, tertiaryContainer=Lavender, background=Paper, surface=Color.White,
            surfaceVariant=Mint, onSurface=Ink, onSurfaceVariant=Muted, outline=Outline),
        typography = Typography(
            titleLarge=TextStyle(fontWeight=FontWeight.ExtraBold,fontSize=24.sp,lineHeight=30.sp,letterSpacing=(-.4).sp),
            titleMedium=TextStyle(fontWeight=FontWeight.Bold,fontSize=17.sp,lineHeight=24.sp),
            labelLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp),
            bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
            labelSmall=TextStyle(fontWeight=FontWeight.Medium,fontSize=11.sp,lineHeight=16.sp)),
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(28.dp)),
        content=content)
}

@Composable
fun CandyCard(modifier: Modifier = Modifier, color: Color = Lavender, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier=modifier, shape=RoundedCornerShape(28.dp), border=BorderStroke(1.5.dp, Color.White.copy(alpha=.9f)),
        colors=CardDefaults.cardColors(containerColor=Color.Transparent), elevation=CardDefaults.cardElevation(5.dp)) {
        Column(Modifier.background(candyBrush(color)).padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp), content=content)
    }
}

/** Small native vector motif: no bitmap or network dependency, outside musical diagrams. */
@Composable
fun CandySectionTitle(title: String, color: Color) {
    Row(Modifier.fillMaxWidth().background(candyBrush(color),RoundedCornerShape(16.dp)).padding(horizontal=10.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        MusicMotif(Modifier.size(25.dp),if(color==Warm) "metronome" else "pick",candyInk(color))
        Text(title,color=candyInk(color),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    }
}

@Composable
fun CandyChip(title: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(selected=selected,onClick=onClick,modifier=modifier.heightIn(min=44.dp),shape=RoundedCornerShape(16.dp),
        border=BorderStroke(if(selected) 1.5.dp else 1.dp,if(selected) candyInk(color).copy(alpha=.3f) else Color.White),
        colors=FilterChipDefaults.filterChipColors(containerColor=Color.White.copy(alpha=.72f),labelColor=Muted,
            selectedContainerColor=color,selectedLabelColor=candyInk(color)),
        label={Text(title,fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium)})
}

@Composable
fun CandyAction(title: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape=RoundedCornerShape(20.dp)
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=52.dp)
        .shadow(if(enabled) 5.dp else 0.dp,shape,ambientColor=Green,spotColor=Green)
        .background(if(enabled) Brush.linearGradient(listOf(Color(0xFF078165),Green,Color(0xFF04765D))) else Brush.linearGradient(listOf(Outline,Outline)),shape),
        shape=shape,border=BorderStroke(1.5.dp,Color.White.copy(alpha=.35f)),contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,disabledContainerColor=Color.Transparent)) {
        TransportMotif(if(title=="暂停") "pause" else "play",Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(title,fontWeight=FontWeight.Bold)
    }
}

@Composable
fun TransportMotif(kind: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        if(kind=="pause") {
            drawRoundRect(color,Offset(w*.15f,h*.1f),Size(w*.22f,h*.8f),androidx.compose.ui.geometry.CornerRadius(w*.07f))
            drawRoundRect(color,Offset(w*.63f,h*.1f),Size(w*.22f,h*.8f),androidx.compose.ui.geometry.CornerRadius(w*.07f))
        } else if(kind=="speaker") {
            val p=Path().apply { moveTo(w*.1f,h*.35f);lineTo(w*.3f,h*.35f);lineTo(w*.53f,h*.13f);lineTo(w*.53f,h*.87f);lineTo(w*.3f,h*.65f);lineTo(w*.1f,h*.65f);close() }
            drawPath(p,color)
            drawArc(color,-55f,110f,false,Offset(w*.4f,h*.18f),Size(w*.48f,h*.64f),style=Stroke(w*.1f))
        } else {
            val p=Path().apply { moveTo(w*.22f,h*.1f);lineTo(w*.86f,h*.5f);lineTo(w*.22f,h*.9f);close() }
            drawPath(p,color)
        }
    }
}

private fun DrawScope.musicNote(x: Float,y: Float,s: Float,color: Color) {
    drawOval(color,Offset(x,y+s*.65f),Size(s*.38f,s*.25f))
    drawLine(color,Offset(x+s*.34f,y+s*.77f),Offset(x+s*.34f,y),s*.075f)
    drawLine(color,Offset(x+s*.34f,y+s*.04f),Offset(x+s*.64f,y+s*.18f),s*.12f)
}

/** Native vector illustration stays inside headers, never over the fretboard or TAB. */
@Composable
fun MusicMotif(modifier: Modifier = Modifier, kind: String = "guitar", color: Color = Green) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        if(kind=="notes") {
            musicNote(w*.1f,h*.12f,w*.68f,color)
            musicNote(w*.62f,h*.45f,w*.38f,Gold)
        } else if(kind=="metronome") {
            val p=Path().apply {moveTo(w*.2f,h*.86f);lineTo(w*.38f,h*.15f);quadraticTo(w*.5f,0f,w*.62f,h*.15f);lineTo(w*.82f,h*.86f);close()}
            drawPath(p,Gold)
            drawLine(color,Offset(w*.5f,h*.68f),Offset(w*.72f,h*.18f),w*.08f)
            drawCircle(color,w*.09f,Offset(w*.69f,h*.25f))
            drawLine(color,Offset(w*.29f,h*.77f),Offset(w*.74f,h*.77f),w*.07f)
        } else if(kind=="pick") {
            val p=Path().apply {moveTo(w*.16f,h*.2f);cubicTo(w*.4f,-h*.07f,w*.95f,h*.08f,w*.87f,h*.4f)
                cubicTo(w*.8f,h*.75f,w*.46f,h*1.02f,w*.3f,h*.84f);cubicTo(w*.08f,h*.6f,w*.02f,h*.37f,w*.16f,h*.2f);close()}
            drawPath(p,color)
            drawCircle(Color.White,w*.12f,Offset(w*.47f,h*.35f),style=Stroke(w*.055f))
        } else {
            drawCircle(Sky.copy(alpha=.8f),w*.43f,Offset(w*.49f,h*.54f))
            rotate(27f,Offset(w*.47f,h*.52f)) {
                drawOval(Gold,Offset(w*.17f,h*.51f),Size(w*.53f,h*.46f))
                drawOval(Gold,Offset(w*.24f,h*.36f),Size(w*.4f,h*.34f))
                drawRoundRect(Color(0xFFB9753A),Offset(w*.37f,h*.13f),Size(w*.14f,h*.46f),androidx.compose.ui.geometry.CornerRadius(w*.035f))
                drawRoundRect(Ink,Offset(w*.32f,h*.03f),Size(w*.23f,h*.2f),androidx.compose.ui.geometry.CornerRadius(w*.05f))
                drawCircle(Color(0xFF8B4C25),w*.1f,Offset(w*.44f,h*.66f))
                drawLine(Ink,Offset(w*.32f,h*.83f),Offset(w*.55f,h*.83f),w*.05f)
                repeat(3) { i -> drawLine(Color.White.copy(alpha=.75f),Offset(w*(.405f+i*.033f),h*.14f),Offset(w*(.405f+i*.033f),h*.82f),w*.012f) }
            }
            musicNote(w*.76f,h*.08f,w*.26f,RoseInk)
            drawCircle(Color.White,w*.035f,Offset(w*.08f,h*.3f))
        }
    }
}

@Composable
fun PickMotif(modifier: Modifier = Modifier) = MusicMotif(modifier.size(38.dp),"pick",Gold)
