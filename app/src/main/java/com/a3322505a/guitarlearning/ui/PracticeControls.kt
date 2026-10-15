package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.music.*

/** Ordinary content actions retain larger touch bounds on tablets. */
@Composable
internal fun PracticeTextButton(onClick: ()->Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), contentPadding: PaddingValues = PaddingValues(horizontal=12.dp),
    content: @Composable RowScope.()->Unit) {
    val large=LocalLargePracticeControls.current
    val style=PracticeSkin.metrics.buttonStyle
    TextButton(onClick=onClick,modifier=modifier.then(if(large) Modifier.sizeIn(minWidth=style.minHeightLarge,minHeight=style.minHeightLarge) else Modifier),
        enabled=enabled,colors=colors,shape=MaterialTheme.shapes.small,
        contentPadding=if(large) PaddingValues(horizontal=18.dp,vertical=12.dp) else contentPadding,content=content)
}

@Composable
internal fun ChordSteps(first: Chord, onSelect: (Chord)->Unit,chords: List<Chord> = Chords.all) {
    val large=LocalLargePracticeControls.current
    PracticeTextButton(onClick={onSelect(chords[chords.indexOf(first)-1])},enabled=first!=chords.first(),
        modifier=Modifier.width(if(large) 64.dp else 44.dp).testTag("previous-chord").semantics { contentDescription="上一和弦" },contentPadding=PaddingValues(0.dp)) { Text("‹",fontSize=if(large) 32.sp else 24.sp) }
    PracticeTextButton(onClick={onSelect(chords[chords.indexOf(first)+1])},enabled=first!=chords.last(),
        modifier=Modifier.width(if(large) 64.dp else 44.dp).testTag("next-chord").semantics { contentDescription="下一和弦" },contentPadding=PaddingValues(0.dp)) { Text("›",fontSize=if(large) 32.sp else 24.sp) }
}

/** One shared segmented control for page modes and short, mutually exclusive settings. */
@Composable
internal fun PracticeSegments(options: List<String>,selected: Int,onSelect: (Int)->Unit,
    modifier: Modifier = Modifier,enabled: Boolean = true) {
    val colors=PracticeSkin.colors
    val style=PracticeSkin.metrics.segmentStyle
    val density=LocalDensity.current
    val measurer=rememberTextMeasurer()
    val textStyle=MaterialTheme.typography.labelLarge
    val widths=options.map { title ->
        with(density) { measurer.measure(title,style=textStyle.copy(fontWeight=FontWeight.Bold),maxLines=1).size.width.toDp() }
            .plus(style.inset*2+8.dp).coerceAtLeast(style.height)
    }
    val minimumWidth=widths.fold(0.dp) { sum,width -> sum+width }
    BoxWithConstraints(modifier.height(style.height).clip(MaterialTheme.shapes.medium).background(colors.surfaceSecondary)) {
    val contentWidth=if(constraints.hasBoundedWidth) maxOf(maxWidth,minimumWidth) else minimumWidth
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).width(contentWidth)
        .selectableGroup(),verticalAlignment=Alignment.CenterVertically) {
        options.forEachIndexed { index,title ->
            val checked=selected==index
            Box(Modifier.weight(widths[index].value).fillMaxHeight()
                .selectable(selected=checked,enabled=enabled,role=Role.Tab,onClick={onSelect(index)})
                .padding(style.inset),contentAlignment=Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
                    .background(if(checked) colors.surface else Color.Transparent).padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
                    Text(title,color=if(checked) colors.textPrimary else colors.textSecondary,
                        fontWeight=if(checked) FontWeight.Bold else FontWeight.Medium,
                        style=textStyle,maxLines=1,overflow=TextOverflow.Clip)
                }
            }
        }
    }
    }
}

/** Wrapping settings share the same neutral surface and selected treatment as the toolbar. */
@Composable
internal fun Choices(options: List<String>, selected: Int, onSelect: (Int)->Unit) {
    val colors=PracticeSkin.colors
    val metrics=PracticeSkin.metrics
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
                            shape=MaterialTheme.shapes.small,color=if(checked) colors.primaryContainer else colors.surface,
                            border=BorderStroke(metrics.borderWidth,if(checked) colors.primary else colors.border)) {
                            Box(Modifier.padding(horizontal=10.dp,vertical=11.dp),contentAlignment=Alignment.Center) {
                                Text(options[i],fontWeight=if(checked) FontWeight.Bold else FontWeight.Medium,
                                    color=if(checked) colors.primary else colors.textSecondary,
                                    style=MaterialTheme.typography.labelLarge)
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
    PracticeContentCard(modifier=Modifier.fillMaxWidth()) {
        Text(title,color=PracticeSkin.colors.textPrimary,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        content()
    }
}

@Composable
internal fun ViewSwitch(tab: Boolean, onSelect: (Int)->Unit) {
    PracticeSegments(listOf("指板","六线谱"),if(tab) 1 else 0,onSelect,
        Modifier.width(174.dp).testTag("view-switch"))
}

/** The content selector, view segments and settings use one 44dp toolbar rhythm. */
@Composable
internal fun PracticeNavigation(summary: String,onSettings: ()->Unit,onManagement: ()->Unit,
    compact: Boolean = false,views: @Composable ()->Unit = {}) {
    val colors=PracticeSkin.colors
    val metrics=PracticeSkin.metrics
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("practice-navigation")) {
        val inline=compact && maxWidth>=620.dp
        val tools: @Composable RowScope.()->Unit = {
            Surface(onClick=onSettings,modifier=Modifier.weight(1f).height(metrics.segmentStyle.height).testTag("config-summary"),
                shape=MaterialTheme.shapes.medium,color=colors.surface,border=BorderStroke(metrics.borderWidth,colors.border)) {
                Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(summary,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,
                        maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.textPrimary)
                    Text("⌄",color=colors.textSecondary,fontSize=20.sp,modifier=Modifier.padding(start=8.dp))
                }
            }
            if(inline) views()
            Surface(onClick=onManagement,modifier=Modifier.size(metrics.segmentStyle.height).testTag("management-button").semantics { contentDescription="设置与帮助" },
                shape=MaterialTheme.shapes.medium,color=colors.surface,border=BorderStroke(metrics.borderWidth,colors.border)) {
                Box(contentAlignment=Alignment.Center) { Text("⚙",fontSize=23.sp,color=colors.textSecondary) }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically,content=tools)
            if(compact && !inline) views()
        }
    }
}
