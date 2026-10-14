package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

/** Visible button bounds grow with the window; phone layouts retain their existing sizes. */
@Composable
internal fun PracticeTextButton(onClick: ()->Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), contentPadding: PaddingValues = PaddingValues(horizontal=12.dp),
    content: @Composable RowScope.()->Unit) {
    val large=LocalLargePracticeControls.current
    TextButton(onClick=onClick,modifier=modifier.then(if(large) Modifier.sizeIn(minWidth=64.dp,minHeight=64.dp) else Modifier),
        enabled=enabled,colors=colors,contentPadding=if(large) PaddingValues(horizontal=18.dp,vertical=12.dp) else contentPadding,content=content)
}

@Composable
internal fun ChordSteps(first: Chord, onSelect: (Chord)->Unit,chords: List<Chord> = Chords.all) {
    val large=LocalLargePracticeControls.current
    PracticeTextButton(onClick={onSelect(chords[chords.indexOf(first)-1])},enabled=first!=chords.first(),
        modifier=Modifier.width(if(large) 64.dp else 48.dp).testTag("previous-chord").semantics { contentDescription="上一和弦" },contentPadding=PaddingValues(0.dp)) { Text("‹",fontSize=if(large) 32.sp else 24.sp) }
    PracticeTextButton(onClick={onSelect(chords[chords.indexOf(first)+1])},enabled=first!=chords.last(),
        modifier=Modifier.width(if(large) 64.dp else 48.dp).testTag("next-chord").semantics { contentDescription="下一和弦" },contentPadding=PaddingValues(0.dp)) { Text("›",fontSize=if(large) 32.sp else 24.sp) }
}

/** Wrapping choices keep every shape/chord discoverable without horizontal clipping. */
@Composable
internal fun Choices(options: List<String>, selected: Int, color: Color = Lavender, onSelect: (Int)->Unit) {
    val large=LocalLargePracticeControls.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val longest=options.maxOfOrNull { it.length } ?: 1
        val target=(if(longest>8) 158 else if(longest>4) 114 else 76)*if(large) 1.3f else 1f
        val columns=(maxWidth.value / target).toInt().coerceAtLeast(1)
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            options.indices.chunked(columns).forEach { row ->
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    row.forEach { i ->
                        val checked=selected==i
                        Surface(onClick={onSelect(i)},modifier=Modifier.weight(1f).heightIn(min=if(large) 64.dp else 48.dp),
                            shape=RoundedCornerShape(16.dp), color=Color.Transparent,
                            shadowElevation=if(checked) 3.dp else 0.dp,
                            border=BorderStroke(if(checked) 2.dp else 1.dp,if(checked) candyInk(color).copy(alpha=.4f) else Outline.copy(alpha=.7f))) {
                            Box(Modifier.background(if(checked) candyBrush(color) else Brush.horizontalGradient(listOf(Color.White,Color(0xFFF7F8FF))))
                                .padding(horizontal=10.dp,vertical=11.dp),contentAlignment=Alignment.Center) {
                                Text(options[i],fontWeight=if(checked) FontWeight.Bold else FontWeight.Medium,
                                    color=if(checked) candyInk(color) else Muted,style=MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun Label(title: String, content: @Composable ColumnScope.()->Unit) {
    val color=when(title) {
        "伴奏短句", "选择和弦" -> Pink
        "选音阶与指型" -> Mint
        "速度", "循环与声音" -> Warm
        else -> Lavender
    }
    CandyCard(Modifier.fillMaxWidth(),lerp(color,Color.White,.8f)) {
        CandySectionTitle(title,color)
        content()
    }
}

@Composable
internal fun ViewSwitch(tab: Boolean, onSelect: (Int)->Unit) {
    Row(Modifier.testTag("view-switch"),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        listOf("指板","六线谱").forEachIndexed { index,title ->
            val checked=tab==(index==1)
            PracticeTextButton(onClick={onSelect(index)},modifier=Modifier.semantics { selected=checked },
                colors=ButtonDefaults.textButtonColors(containerColor=if(checked) Sky else Color.Transparent),
                contentPadding=PaddingValues(horizontal=10.dp)) { Text(title,fontWeight=if(checked) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

/** The summary is the sole content selector; management has one fixed entry. */
@Composable
internal fun PracticeNavigation(summary: String,onSettings: ()->Unit,onManagement: ()->Unit,
    compact: Boolean = false,views: @Composable ()->Unit = {}) {
    Row(Modifier.fillMaxWidth().testTag("practice-navigation"),verticalAlignment=Alignment.CenterVertically) {
        Surface(onClick=onSettings,modifier=Modifier.weight(1f).heightIn(min=if(LocalLargePracticeControls.current) 64.dp else 48.dp).testTag("config-summary"),color=Color.Transparent) {
            Column(Modifier.padding(horizontal=8.dp,vertical=8.dp)) {
                Text("当前内容 · 点此选择",style=MaterialTheme.typography.labelSmall,color=Muted)
                Text("$summary ›",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,
                    maxLines=1,overflow=TextOverflow.Ellipsis,color=PurpleInk)
            }
        }
        if(compact) views()
        PracticeTextButton(onClick=onManagement,modifier=Modifier.testTag("management-button").semantics { contentDescription="管理与帮助" }) { Text("⚙",fontSize=24.sp) }
    }
}
