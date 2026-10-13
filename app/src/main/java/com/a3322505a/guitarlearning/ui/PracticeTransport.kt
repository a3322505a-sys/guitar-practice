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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*


/** Presentation only: playback and persistence are owned by PracticeApp. */
@Composable
internal fun PracticeTransport(tab: Boolean, bpm: Int, beatsPerBar: Int, hint: Hint,
    active: Boolean, phase: Phase, hasSequence: Boolean, onTempo: ()->Unit, onHint: ()->Unit,
    onPauseResume: ()->Unit, onRestart: ()->Unit, onDemo: ()->Unit, onFollow: ()->Unit, onStop: ()->Unit) {
// Keep the original footer typography, control sizes and TAB row layout.
PracticeTheme {
Surface(color=Color.Transparent,shadowElevation=10.dp,shape=RoundedCornerShape(topStart=26.dp,topEnd=26.dp),border=BorderStroke(1.dp,Color.White)) {
    Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFFF4FFFC),Color(0xFFE5F6F2)))).navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(!tab) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={onTempo()},modifier=Modifier.testTag("tempo-button"),contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {
                MusicMotif(Modifier.size(22.dp),"metronome");Spacer(Modifier.width(6.dp));Text("$bpm BPM",fontWeight=FontWeight.Bold)
            }
            Text("· $beatsPerBar/4",color=Muted,style=MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick={onHint()},contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) { Text(if(hint==Hint.HIDDEN) "恢复提示" else "撤提示") }
        }
        Row(Modifier.fillMaxWidth().testTag("transport"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            if(tab) {
                TextButton(onClick={onTempo()},modifier=Modifier.testTag("tempo-button"),contentPadding=PaddingValues(horizontal=4.dp)) { Text("$bpm BPM") }
                TextButton(onClick={onHint()},contentPadding=PaddingValues(horizontal=4.dp)) { Text(if(hint==Hint.HIDDEN) "恢复提示" else "撤提示") }
            }
            if(active || phase==Phase.PAUSED) {
                CandyAction(if(active) "暂停" else "继续",modifier=Modifier.weight(1f),onClick={onPauseResume()})
                OutlinedButton(onClick={onRestart()},modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=Color.White),border=BorderStroke(1.5.dp,Mint)) { Text("重来") }
            } else {
                OutlinedButton(onClick={onDemo()},enabled=hasSequence,modifier=Modifier.weight(.85f).heightIn(min=52.dp),shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=Color.White),border=BorderStroke(1.5.dp,Mint),contentPadding=PaddingValues(8.dp)) {
                    TransportMotif("speaker",Modifier.size(18.dp),Green);Spacer(Modifier.width(6.dp));Text("听示范")
                }
                CandyAction("开始跟练",modifier=Modifier.weight(1.25f),enabled=hasSequence,onClick={onFollow()})
            }
            TextButton(onClick={onStop()},modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)) { Text("结束",color=Muted) }
        }
    }
}
}
}

