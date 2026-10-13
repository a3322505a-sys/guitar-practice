package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*


/** Derived display values; this object stores no preferences or playback state. */
internal data class PracticeContentState(
    val page: Int,val chordPhrases: Boolean,val tab: Boolean,val tabletControls: Boolean,
    val scale: ScaleOptions,val first: Chord,val phrase: Phrase,val melody: Boolean,
    val sequence: Sequence?,val displayedSequence: Sequence?,val scaleResult: RouteResult,
    val hint: Hint,val renderHint: Hint,val active: Boolean,val phase: Phase,
    val renderCursor: Cursor?,val cursor: Cursor?,val showingSession: Boolean,
    val event: Event?,val phraseNow: Event?,val nextEvent: Event?,val moving: MovingPosition?,
    val current: List<Position>,val shownChord: Chord?,val nextChord: String,
    val status: String,val note: String,val beatsPerBar: Int)

@Composable
internal fun PracticeContent(state: PracticeContentState,modifier: Modifier,
    onChord: (Chord)->Unit,onView: (Int)->Unit,onTap: (Position)->Unit,
    onAudition: (Chord)->Unit,onHelp: ()->Unit) = with(state) {
    if(displayedSequence==null) {
        Box(modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            Text(if(note.isNotBlank()) note else scaleResult.reason.ifBlank { "正在准备曲目…" },color=Muted)
        }
        return@with
    }
    fun eventLabel(value: Event?)=if(value==null || value.rest) "留拍" else listOf(value.positions.joinToString { "${it.string} 弦 ${it.fret} 品 ${Music.name(displayedSequence!!.midi(it),true)}" },value.expressionLabel()).filter { it.isNotBlank() }.joinToString(" ")
BoxWithConstraints(modifier.fillMaxWidth()) {
  val availableWidth=maxWidth
  val availableHeight=maxHeight
  val wide=maxWidth>=700.dp
  val compact=maxHeight<(if(tabletControls) 360.dp else 520.dp)
  val fontScale=LocalDensity.current.fontScale
  // Decide from the complete route, so rests/dead notes never resize the card.
  val openStringsOnly=melody && displayedSequence!!.route.isNotEmpty() && displayedSequence.route.all { it.fret==0 }
  // A short window scrolls the whole chord page, never the labels inside its diagram.
  val chordPageScroll=page==1 && !wide && maxHeight<600.dp*fontScale
  val chordScrollState=remember(page) { ScrollState(0) }
  if(tab) {
      Column(Modifier.fillMaxSize().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
          Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                  Text(if(page==0) "${scale.key.title} · ${Patterns.all[scale.pattern].solfege} · ${scale.exercise.title}"
                      else if(chordPhrases) phrase.title else "${first.name} · 逐弦检查",
                      maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge,color=PurpleInk)
                  Text(status,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=if(phase==Phase.ERROR) RoseInk else Green)
              }
          }
          if(displayedSequence!=null) Tablature(displayedSequence,renderHint,if(page==0) scale.key else null,renderCursor,active || phase==Phase.PAUSED,
              Modifier.weight(1f).background(Color.White.copy(alpha=.94f),RoundedCornerShape(20.dp)).padding(horizontal=8.dp,vertical=4.dp))
          else Text(scaleResult.reason,color=RoseInk)
      }
  } else {
  val content: @Composable () -> Unit = {
      Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
          if(!tabletControls) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
              Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                  Box(Modifier.size(7.dp).background(PurpleInk,RoundedCornerShape(4.dp)))
                  if(page==1 && !chordPhrases) {
                      ChordSteps(first,onChord)
                  } else Text("练习视图",style=MaterialTheme.typography.labelLarge,color=PurpleInk)
              }
              Spacer(Modifier.weight(1f))
              ViewSwitch(tab,onView)
          }
          val cardContent: @Composable (Chord,Boolean)->Unit = { cardChord,selected ->
          val shownChord=cardChord
          val chordTones=cardChord.positions.distinctBy { cardChord.midi(it)%12 }.sortedBy { (cardChord.midi(it)-cardChord.root)%12 }
          CandyCard((if(openStringsOnly) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).then(if(selected) Modifier.testTag("practice-card") else Modifier.clearAndSetSemantics {}),if(page==1) lerp(Pink,Color.White,.45f) else lerp(Sky,Color.White,.45f)) {
              val target=if(page==1) "${shownChord.name} · ${if(!chordPhrases) "逐弦检查" else "下一：$nextChord"}"
                  else if(hint==Hint.FULL && event!=null) { if(event.rest) "现在：留拍" else "现在：${eventLabel(event)}" }
                  else "${scale.key.title} · ${Patterns.all[scale.pattern].solfege} 指型"
              val phraseCard=page==1 && chordPhrases
              Row(Modifier.fillMaxWidth().then(if(phraseCard) Modifier.height((if(openStringsOnly) 80.dp else if(compact) 96.dp else 120.dp)*fontScale) else Modifier)
                  .background(candyBrush(if(page==1) Pink else Mint),RoundedCornerShape(20.dp)).padding(horizontal=12.dp,vertical=if(compact) 4.dp else 12.dp),verticalAlignment=Alignment.CenterVertically) {
                  Column(Modifier.weight(1f)) {
                      val headline=buildAnnotatedString {
                          if(phraseCard) append(phrase.title)
                          else if(page==1) {
                              withStyle(SpanStyle(color=RoseInk,fontWeight=FontWeight.ExtraBold,fontSize=if(compact || shownChord.name.length>5) 22.sp else 30.sp)) { append(shownChord.name) }
                              if(!chordPhrases) withStyle(SpanStyle(color=Ink,fontSize=14.sp)) { append(" · 逐弦检查") }
                          } else append(if(melody) phrase.title else target)
                      }
                      Text(headline,style=if(compact || phraseCard) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Ink,
                          maxLines=if(phraseCard) 2 else Int.MAX_VALUE,overflow=TextOverflow.Ellipsis)
                      if(phraseCard) {
                          val cue=if(hint==Hint.HIDDEN) "已撤提示" else displayedSequence?.runtimePlan?.let {
                              planCue(it,if(showingSession) cursor else null)
                          } ?: if(!melody) "${shownChord.name} · ${phraseCue(phraseNow,displayedSequence!!)}" else phraseCue(phraseNow,displayedSequence!!)
                          Text(cue,modifier=Modifier.testTag("phrase-cue"),style=MaterialTheme.typography.labelLarge,color=BlueInk,maxLines=2,overflow=TextOverflow.Ellipsis)
                          if(!melody) Text("下一：$nextChord",style=MaterialTheme.typography.labelLarge,color=BlueInk,maxLines=1)
                      }
                      if(page==1 && !chordPhrases && shownChord.capoFret>0) Text(shownChord.conditions,style=MaterialTheme.typography.bodySmall,color=PurpleInk,modifier=Modifier.testTag("chord-conditions"))
                      if(page==1 && !chordPhrases && hint!=Hint.HIDDEN) Text(
                          chordTones.filter { hint==Hint.FULL || shownChord.isRoot(it) }.joinToString(" · ") { "${shownChord.role(it)} ${shownChord.noteName(it)}" },
                          style=MaterialTheme.typography.bodySmall,color=BlueInk)
                      if(page==0 && hint==Hint.FULL && nextEvent!=null) Text("下一："+if(nextEvent.positions.isEmpty()) "留拍" else nextEvent.positions.joinToString { Music.name(it.midi,true) },style=MaterialTheme.typography.labelMedium,color=BlueInk)
                  }
                  if(page==1 && !chordPhrases) PracticeTextButton(onClick={onAudition(shownChord)},enabled=selected && !active && phase!=Phase.PAUSED,contentPadding=PaddingValues(horizontal=6.dp)) { Text("听和弦",style=MaterialTheme.typography.labelLarge) }
                  else if(phraseCard) PracticeTextButton(onClick={onHelp()},modifier=Modifier.testTag("diagram-help"),contentPadding=PaddingValues(horizontal=4.dp)) { Text("帮助") }
                  else if(!compact && availableWidth>370.dp && !active) MusicMotif(Modifier.size(30.dp),"notes",if(page==1) RoseInk else Green)
                  if(hint==Hint.HIDDEN && !phraseCard) Text("已撤提示",color=Muted,style=MaterialTheme.typography.labelSmall)
              }
                  BoxWithConstraints((if(openStringsOnly) Modifier.height(128.dp*fontScale) else Modifier.weight(1f)).fillMaxWidth().testTag("diagram-viewport").background(Color.White.copy(alpha=.94f),RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center) {
                      if(page==0) {
                          val h=maxOf(176.dp*fontScale,maxHeight).coerceAtMost(300.dp*fontScale)
                          Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),contentAlignment=Alignment.Center) {
                              val positions=if(scale.exercise==ScaleExercise.CONNECTION && scale.pattern<4) (Patterns.all[scale.pattern].positions+Patterns.all[scale.pattern+1].positions).distinct() else Patterns.all[scale.pattern].positions
                              Fretboard(positions,scale.key,hint,false,current,!active,h,onTap)
                          }
                      } else if(melody && displayedSequence!!.route.isNotEmpty()) Fretboard(displayedSequence.route,null,renderHint,false,
                          if(showingSession) current else phraseNow?.positions ?: displayedSequence.runtimePlan?.notes?.take(1)?.map { it.position }.orEmpty(),
                          !active,maxHeight.coerceAtMost(300.dp*fontScale),onTap,moving,
                          nextEvent?.positions.orEmpty(),showLegend=false,capoFret=displayedSequence.capoFret,
                          openStringMidi=displayedSequence.runtimePlan?.mainTrack?.openStringMidi ?: Music.openMidi.toList(),
                          movingPositions=cursor?.movingPositions ?: moving?.let { listOf(it) }.orEmpty())
                      else if(melody) Text(if(hint==Hint.HIDDEN) "按节拍演奏" else "死音节奏",color=PurpleInk)
                      else ChordDiagram(shownChord,if(shownChord.root<0 && hint==Hint.ROOTS) Hint.FULL else hint,
                          if(selected) current else emptyList(),selected && !active,maxHeight.coerceAtMost(390.dp*fontScale).coerceAtMost(availableHeight),onTap)
                  }
                  if(!compact && !phraseCard) Text(if(hint==Hint.FULL) { if(page==0) "金色：主音 · 绿色：音阶音 · 粉色：当前音" else "左 6 弦 → 右 1 弦 · 金色根音 · O 空弦 · X 不弹" } else "保留拍点与控制，可随时恢复提示。",style=MaterialTheme.typography.labelSmall,color=Muted)
              if(!compact || !wide || phraseCard) Row(Modifier.fillMaxWidth().height(56.dp*fontScale).testTag("playback-status")
                  .background(Color.White.copy(alpha=.7f),RoundedCornerShape(14.dp)).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                  Text(if(selected) status else "准备好，就开始一轮",style=MaterialTheme.typography.labelLarge,color=if(phase==Phase.ERROR) RoseInk else Green,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                  repeat(beatsPerBar) { i ->
                      Box(Modifier.padding(start=6.dp).size(11.dp).background(if(i==cursor?.beat && showingSession) Green else Lavender,RoundedCornerShape(8.dp)))
                  }
              }
              if(phraseCard || !compact) Box(Modifier.fillMaxWidth().height(4.dp)) {
                  if(selected && showingSession && cursor?.pre==false) LinearProgressIndicator(progress={ (cursor.localTick / displayedSequence!!.length).toFloat() },modifier=Modifier.fillMaxWidth(),color=Green,trackColor=Mint)
              }
              if(phraseCard) Box(Modifier.fillMaxWidth().height(20.dp*fontScale)) {
                  if(selected && hint==Hint.FULL) Text(note,color=Green,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
              } else if(selected && note.isNotEmpty() && hint==Hint.FULL && !compact) Text(note,color=Green,style=MaterialTheme.typography.bodySmall)
          }
          }
          if(page==1 && !chordPhrases) ChordDeck(Chords.all.indexOf(first),Chords.all.size,{onChord(Chords.all[it])},Modifier.weight(1f).fillMaxWidth()) { index,selected -> cardContent(Chords.all[index],selected) }
          else Box(Modifier.weight(1f).fillMaxWidth()) { cardContent(shownChord ?: first,true) }
          if(!compact && !wide && !active && page==0) Surface(color=Color.Transparent,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color.White)) {
              Row(Modifier.background(candyBrush(Warm)).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                  MusicMotif(Modifier.size(24.dp),"pick",Color(0xFFD3961B))
                  Text(sequence?.explanation ?: scaleResult.reason,style=MaterialTheme.typography.bodySmall,color=Color(0xFF775829),modifier=Modifier.weight(1f))
              }
          }
      }
  }
  Box(Modifier.fillMaxSize().testTag("chord-page").then(if(chordPageScroll) Modifier.verticalScroll(chordScrollState) else Modifier)) {
  Column(Modifier.fillMaxWidth().height(if(chordPageScroll) 600.dp*fontScale else availableHeight).padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
      val summary: @Composable () -> Unit = {
          Text(if(page==0) "${scale.exercise.title} · 每拍${scale.notesPerBeat}音"
              else if(!chordPhrases) "认音、听音、逐弦跟练" else "${phrase.passage} · ${phrase.conditions}",
              style=MaterialTheme.typography.bodySmall,color=Muted)
      }
      if(wide) {
          Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
              Column(Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                  summary()
                  CandyCard(Modifier.fillMaxWidth(),Warm) {
                      CandySectionTitle("练习小提示",Warm)
                      Text(sequence?.explanation ?: scaleResult.reason,style=MaterialTheme.typography.bodySmall)
                      Text("先听一遍，再拿琴跟练。",style=MaterialTheme.typography.bodySmall,color=Muted)
                  }
                  Text("标准调弦 · 离线练习",style=MaterialTheme.typography.labelSmall,color=Muted)
              }
              Box(Modifier.weight(1f)) { content() }
          }
      } else {
          if(!compact && page==0) summary()
          Box(Modifier.weight(1f)) { content() }
      }
  }
  }
  }
    }
}

