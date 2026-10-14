package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import com.a3322505a.guitarlearning.audio.Phase

/** Only real sessions get pause/restart controls; auditions retain their own meaning. */
internal enum class TransportMode { IDLE, PREPARING, PLAYING, PAUSED, ENDED, ERROR, PREVIEW }
internal fun transportMode(phase: Phase,hasSession: Boolean,preview: Boolean): TransportMode = when {
    preview && phase in listOf(Phase.PREPARING,Phase.PLAYING,Phase.PAUSED) -> TransportMode.PREVIEW
    !hasSession -> if(phase==Phase.ERROR) TransportMode.ERROR else TransportMode.IDLE
    else -> when(phase) {
        Phase.IDLE -> TransportMode.IDLE
        Phase.PREPARING -> TransportMode.PREPARING
        Phase.PLAYING -> TransportMode.PLAYING
        Phase.PAUSED -> TransportMode.PAUSED
        Phase.ENDED -> if(preview) TransportMode.IDLE else TransportMode.ENDED
        Phase.ERROR -> TransportMode.ERROR
    }
}

/** Dragging previews locally; the player only receives the final value once. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PracticeTransport(bpm: Int,sourceTempo: Boolean,multiplier: Int,meter: String,hint: Hint,
    mode: TransportMode,hasSequence: Boolean,onBpm: (Int)->Unit,onMultiplier: (Int)->Unit,onHint: ()->Unit,
    onPauseResume: ()->Unit,onRestart: ()->Unit,onDemo: ()->Unit,onFollow: ()->Unit,onStop: ()->Unit,
    availableHeight: Dp = Dp.Infinity) {
    PracticeTheme {
        var expanded by rememberSaveable { mutableStateOf(false) }
        var draft by remember(bpm) { mutableFloatStateOf(bpm.toFloat()) }
        val multipliers=listOf(50,75,100,125,150)
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("transport-bar")) {
            val scale=LocalDensity.current.fontScale.coerceAtLeast(1f)
            val popupWidth=maxWidth.coerceAtMost(360.dp)
            // Account for both window height and text size; every playback state uses the same rows.
            val singleRow=maxWidth>=680.dp*scale && (availableHeight<600.dp*scale || maxWidth>=1000.dp)
            val tempo: @Composable ()->Unit = {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick={ if(sourceTempo) onMultiplier(multipliers.lastOrNull { it<multiplier } ?: 50) else onBpm((bpm-5).coerceAtLeast(30)) },
                        enabled=if(sourceTempo) multiplier>50 else bpm>30,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).testTag("tempo-slower"),contentPadding=PaddingValues(4.dp)) { Text(if(sourceTempo) "−" else "−5",maxLines=1) }
                    Box {
                        Button(onClick={expanded=!expanded},modifier=Modifier.heightIn(min=48.dp).testTag("tempo-button"),
                            colors=ButtonDefaults.buttonColors(containerColor=Mint,contentColor=Green),contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp)) {
                            Text((if(sourceTempo) "原谱 $multiplier%" else "$bpm BPM")+" ⌃",fontWeight=FontWeight.Bold,maxLines=1)
                        }
                        if(expanded) Popup(popupPositionProvider=TempoPopupPosition,onDismissRequest={expanded=false},properties=PopupProperties(focusable=true)) {
                            Surface(shape=RoundedCornerShape(20.dp),color=Color(0xFFF4FFFC),shadowElevation=8.dp,border=BorderStroke(1.dp,Outline),
                                modifier=Modifier.width(popupWidth).testTag("tempo-popup")) {
                                Column(Modifier.padding(horizontal=12.dp,vertical=4.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Text(if(sourceTempo) "原谱速度" else "${draft.toInt()} BPM · 松手应用",modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=Green)
                                        TextButton(onClick={expanded=false}) { Text("收起") }
                                    }
                                    if(sourceTempo) FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                        multipliers.forEach { value -> OutlinedButton(onClick={onMultiplier(value)},contentPadding=PaddingValues(8.dp),
                                            colors=ButtonDefaults.outlinedButtonColors(containerColor=if(value==multiplier) Mint else Color.Transparent)) { Text("$value%") } }
                                    } else Slider(value=draft,onValueChange={draft=it},onValueChangeFinished={onBpm(draft.toInt())},valueRange=30f..180f,modifier=Modifier.testTag("tempo-slider"))
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick={ if(sourceTempo) onMultiplier(multipliers.firstOrNull { it>multiplier } ?: 150) else onBpm((bpm+5).coerceAtMost(180)) },
                        enabled=if(sourceTempo) multiplier<150 else bpm<180,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).testTag("tempo-faster"),contentPadding=PaddingValues(4.dp)) { Text(if(sourceTempo) "+" else "+5",maxLines=1) }
                    Text(meter,color=Muted,style=MaterialTheme.typography.labelSmall)
                }
            }
            val actions: @Composable ()->Unit = {
                FlowRow(Modifier.testTag("transport"),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    when(mode) {
                        TransportMode.IDLE,TransportMode.ENDED -> {
                            TransportAction("听示范",enabled=hasSequence,onClick=onDemo)
                            TransportAction(if(mode==TransportMode.ENDED) "再练一次" else "开始跟练",primary=true,enabled=hasSequence,
                                onClick=if(mode==TransportMode.ENDED) onRestart else onFollow)
                        }
                        TransportMode.PLAYING,TransportMode.PAUSED -> {
                            TransportAction(if(mode==TransportMode.PAUSED) "继续" else "暂停",primary=true,onClick=onPauseResume)
                            TransportAction("重来",onClick=onRestart)
                        }
                        TransportMode.PREPARING -> TransportAction("准备中…",primary=true,enabled=false,onClick={})
                        TransportMode.PREVIEW -> TransportAction("试听中",enabled=false,onClick={})
                        TransportMode.ERROR -> TransportAction("播放失败",enabled=false,onClick={})
                    }
                    if(mode!=TransportMode.IDLE) TransportAction("结束",onClick=onStop)
                    TransportAction(if(hint==Hint.HIDDEN) "恢复提示" else "撤提示",onClick=onHint)
                }
            }
            Surface(color=Color.Transparent,shadowElevation=6.dp,shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp),border=BorderStroke(1.dp,Color.White)) {
                Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFFF4FFFC),Color(0xFFE5F6F2))))
                    .navigationBarsPadding().padding(horizontal=10.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    if(singleRow) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        tempo();Spacer(Modifier.weight(1f));actions()
                    } else {
                        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { tempo() }
                        Box(Modifier.fillMaxWidth().heightIn(min=48.dp),contentAlignment=Alignment.Center) { actions() }
                    }
                }
            }
        }
    }
}

/** Center over the tempo button, then keep the complete editor inside the usable window. */
internal object TempoPopupPosition: PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect,windowSize: IntSize,layoutDirection: LayoutDirection,popupContentSize: IntSize): IntOffset = IntOffset(
        (anchorBounds.left+anchorBounds.width/2-popupContentSize.width/2).coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0)),
        (anchorBounds.bottom-popupContentSize.height).coerceIn(0,(windowSize.height-popupContentSize.height).coerceAtLeast(0)))
}

@Composable
private fun TransportAction(title: String,primary: Boolean=false,enabled: Boolean=true,onClick: ()->Unit) {
    val colors=ButtonDefaults.outlinedButtonColors(containerColor=if(primary) Green else Color.White.copy(alpha=.8f),contentColor=if(primary) Color.White else Green)
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.heightIn(min=48.dp),colors=colors,
        shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp),border=BorderStroke(1.dp,if(primary) Green else Outline)) {
        if(primary) { TransportMotif(if(title=="暂停") "pause" else "play",Modifier.size(15.dp));Spacer(Modifier.width(6.dp)) }
        Text(title,fontWeight=if(primary) FontWeight.Bold else FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
