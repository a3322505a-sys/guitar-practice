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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
internal fun PracticeTransport(bpm: Int,sourceTempo: Boolean,multiplier: Int,meter: String,hint: Hint,
    mode: TransportMode,hasSequence: Boolean,onBpm: (Int)->Unit,onMultiplier: (Int)->Unit,onHint: ()->Unit,
    onPauseResume: ()->Unit,onRestart: ()->Unit,onDemo: ()->Unit,onFollow: ()->Unit,onStop: ()->Unit) {
    PracticeTheme {
        var expanded by rememberSaveable { mutableStateOf(false) }
        var draft by remember(bpm) { mutableFloatStateOf(bpm.toFloat()) }
        val multipliers=listOf(50,75,100,125,150)
        Surface(color=Color.Transparent,shadowElevation=10.dp,shape=RoundedCornerShape(topStart=26.dp,topEnd=26.dp),border=BorderStroke(1.dp,Color.White)) {
            Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFFF4FFFC),Color(0xFFE5F6F2))))
                .navigationBarsPadding().padding(horizontal=12.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={ if(sourceTempo) onMultiplier(multipliers.lastOrNull { it<multiplier } ?: 50) else onBpm((bpm-5).coerceAtLeast(30)) },
                        enabled=if(sourceTempo) multiplier>50 else bpm>30,modifier=Modifier.testTag("tempo-slower"),contentPadding=PaddingValues(4.dp)) { Text(if(sourceTempo) "−" else "−5") }
                    TextButton(onClick={expanded=!expanded},modifier=Modifier.testTag("tempo-button"),contentPadding=PaddingValues(4.dp)) {
                        Text(if(sourceTempo) "原谱 $multiplier%" else "$bpm BPM",fontWeight=FontWeight.Bold)
                    }
                    TextButton(onClick={ if(sourceTempo) onMultiplier(multipliers.firstOrNull { it>multiplier } ?: 150) else onBpm((bpm+5).coerceAtMost(180)) },
                        enabled=if(sourceTempo) multiplier<150 else bpm<180,modifier=Modifier.testTag("tempo-faster"),contentPadding=PaddingValues(4.dp)) { Text(if(sourceTempo) "+" else "+5") }
                    Text(meter,color=Muted,style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f))
                    TextButton(onClick=onHint,contentPadding=PaddingValues(4.dp)) { Text(if(hint==Hint.HIDDEN) "恢复提示" else "撤提示") }
                }
                if(expanded) {
                    if(sourceTempo) Choices(multipliers.map { "$it%" },multipliers.indexOf(multiplier),Mint) { onMultiplier(multipliers[it]) }
                    else Slider(value=draft,onValueChange={draft=it},onValueChangeFinished={onBpm(draft.toInt())},
                        valueRange=30f..180f,modifier=Modifier.testTag("tempo-slider"))
                    if(!sourceTempo) Text("${draft.toInt()} BPM · 松手应用",style=MaterialTheme.typography.labelSmall,color=Muted)
                }
                Row(Modifier.fillMaxWidth().testTag("transport"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    when(mode) {
                        TransportMode.IDLE,TransportMode.ENDED -> {
                            OutlinedButton(onClick=onDemo,enabled=hasSequence,modifier=Modifier.weight(.85f).heightIn(min=52.dp),contentPadding=PaddingValues(8.dp)) { Text("听示范") }
                            CandyAction(if(mode==TransportMode.ENDED) "再练一次" else "开始跟练",modifier=Modifier.weight(1.25f),enabled=hasSequence,
                                onClick=if(mode==TransportMode.ENDED) onRestart else onFollow)
                        }
                        TransportMode.PLAYING,TransportMode.PAUSED -> {
                            CandyAction(if(mode==TransportMode.PAUSED) "继续" else "暂停",modifier=Modifier.weight(1f),onClick=onPauseResume)
                            OutlinedButton(onClick=onRestart,modifier=Modifier.heightIn(min=52.dp)) { Text("重来") }
                        }
                        TransportMode.PREPARING -> CandyAction("准备中…",enabled=false,modifier=Modifier.weight(1f),onClick={})
                        TransportMode.PREVIEW -> Text("点音 / 和弦试听",modifier=Modifier.weight(1f),color=Muted)
                        TransportMode.ERROR -> Text("播放失败，请结束后重试",modifier=Modifier.weight(1f),color=RoseInk)
                    }
                    if(mode!=TransportMode.IDLE) TextButton(onClick=onStop,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)) { Text("结束") }
                }
            }
        }
    }
}
