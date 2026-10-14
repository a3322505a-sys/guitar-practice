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

/** Domain, secondary chord content, and editing have distinct visual roles in every layout. */
@Composable
internal fun PracticeNavigation(page: Int, phrases: Boolean, compact: Boolean, tablet: Boolean,
    onPage: (Int)->Unit, onMode: (Boolean)->Unit, onSettings: ()->Unit,
    chordSteps: @Composable ()->Unit, trailing: @Composable ()->Unit) {
    val domains: @Composable ()->Unit = {
        listOf("音阶","和弦").forEachIndexed { index,title ->
            CandyChip(title,page==index,if(index==0) Mint else Pink,Modifier.testTag("domain-$index")) { onPage(index) }
        }
    }
    val modes: @Composable ()->Unit = {
        if(page==1) listOf("和弦学习","短句应用").forEachIndexed { index,title ->
            val checked=phrases==(index==1)
            PracticeTextButton(onClick={onMode(index==1)},modifier=Modifier.testTag("chord-mode-$index").semantics { selected=checked },
                contentPadding=PaddingValues(horizontal=8.dp)) {
                Text(title,color=if(checked) PurpleInk else Muted,fontWeight=if(checked) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    val settings: @Composable ()->Unit = {
        PracticeTextButton(onClick=onSettings,modifier=Modifier.testTag("config-summary"),contentPadding=PaddingValues(horizontal=8.dp)) {
            Text("练习设置 ›",color=Muted,style=MaterialTheme.typography.labelLarge)
        }
    }
    Column(Modifier.fillMaxWidth().testTag("practice-navigation"),verticalArrangement=Arrangement.spacedBy(if(tablet) 4.dp else 0.dp)) {
        if(tablet) {
            Row(Modifier.fillMaxWidth().testTag("navigation-primary"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("吉他练习",modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                domains()
                settings()
            }
            Row(Modifier.fillMaxWidth().testTag("navigation-secondary"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(page==1) modes() else Text("练习视图",style=MaterialTheme.typography.labelLarge,color=PurpleInk)
                    if(page==1 && !phrases) chordSteps()
                }
                trailing()
            }
        } else {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(compact) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    domains()
                    if(page==1) { Box(Modifier.width(1.dp).height(20.dp).background(Outline)); modes() }
                }
                settings()
                trailing()
            } else {
                Text("吉他练习",modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                domains()
                trailing()
            }
        }
        if(!compact) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            modes(); Spacer(Modifier.weight(1f)); settings()
        }
        }
    }
}


