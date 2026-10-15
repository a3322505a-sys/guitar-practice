package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val status: String,val note: String,val beatsPerBar: Int,val chords: List<Chord> = Chords.all,
    val configuring: Boolean = false)

@Composable
internal fun PracticeContent(state: PracticeContentState,modifier: Modifier,
    onChord: (Chord)->Unit,onView: (Int)->Unit,onTap: (Position)->Unit,
    onAudition: (Chord)->Unit) = with(state) {
    val colors=PracticeSkin.colors
    if(displayedSequence==null && (page!=1 || chordPhrases)) {
        PracticeContentCard(title="练习内容",modifier=modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Text(if(note.isNotBlank()) note else scaleResult.reason.ifBlank { "正在准备曲目…" },color=colors.textSecondary)
            }
        }
        return@with
    }
    fun eventLabel(value: Event?)=if(value==null || value.rest) "留拍" else listOf(
        value.positions.joinToString { "${it.string} 弦 ${it.fret} 品 ${Music.name(displayedSequence!!.midi(it),true)}" },
        value.expressionLabel()).filter { it.isNotBlank() }.joinToString(" ")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableHeight=maxHeight
        val wide=maxWidth>=1000.dp && !configuring
        val compact=configuring || maxHeight<(if(tabletControls) 360.dp else 520.dp)
        val fontScale=LocalDensity.current.fontScale
        // Decide from the complete route, so rests/dead notes never resize the card.
        val openStringsOnly=melody && displayedSequence!!.route.isNotEmpty() && displayedSequence.route.all { it.fret==0 }
        // A short window scrolls the whole chord page, never labels inside its diagram.
        val chordPageScroll=page==1 && !configuring && !wide && maxHeight<600.dp*fontScale
        val chordScrollState=remember(page) { ScrollState(0) }
        if(tab) {
            PracticeContentCard(title="练习谱面",modifier=Modifier.fillMaxSize(),footer={
                PracticeStatus(status,phase,beatsPerBar,cursor,showingSession,compact=true)
            }) {
                if(displayedSequence!=null) DiagramContainer(Modifier.weight(1f).fillMaxWidth()) {
                    Tablature(displayedSequence,renderHint,if(page==0) scale.key else null,renderCursor,
                        active || phase==Phase.PAUSED,Modifier.fillMaxSize())
                } else Text(scaleResult.reason,color=colors.danger)
            }
            return@BoxWithConstraints
        }
        val cardContent: @Composable (Chord,Boolean)->Unit = card@{ cardChord,selected ->
            val cardModifier=(if(openStringsOnly) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .then(if(selected) Modifier.testTag("practice-card") else Modifier.clearAndSetSemantics {})
            val unavailable=cardChord.unavailableReason
            if(unavailable!=null) {
                PracticeContentCard(title=cardChord.name,modifier=cardModifier) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        ChordSteps(first,onChord,chords)
                        Text("左右滑动切换和弦",style=MaterialTheme.typography.labelSmall,color=colors.textSecondary)
                    }
                    Text(unavailable,color=colors.danger)
                }
                return@card
            }
            val phraseCard=page==1 && chordPhrases
            val chordTones=cardChord.positions.distinctBy { cardChord.midi(it)%12 }
                .sortedBy { (cardChord.midi(it)-cardChord.root)%12 }
            val title=if(phraseCard) "演奏提示 · ${phrase.passage}"
                else if(page==1) "${cardChord.name} · 逐弦检查"
                else if(melody) phrase.title
                else if(hint==Hint.FULL && event!=null) { if(event.rest) "现在：留拍" else "现在：${eventLabel(event)}" }
                else "${scale.key.title} · ${Patterns.all[scale.pattern].solfege} 指型"
            PracticeContentCard(modifier=cardModifier,footer={
                PracticeStatus(if(selected) status else "准备好，就开始一轮",phase,beatsPerBar,
                    if(selected) cursor else null,selected && showingSession,compact)
                if(!configuring && (phraseCard || !compact)) Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if(selected && showingSession && cursor?.pre==false) LinearProgressIndicator(
                        progress={ (cursor.localTick / displayedSequence!!.length).toFloat() },modifier=Modifier.fillMaxWidth(),
                        color=colors.progressComplete,trackColor=colors.progressPending)
                }
                if(phraseCard && !configuring) Box(Modifier.fillMaxWidth().height(20.dp*fontScale)) {
                    if(selected && hint==Hint.FULL) Text(note,color=colors.primary,style=MaterialTheme.typography.bodySmall,
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                } else if(selected && note.isNotEmpty() && hint==Hint.FULL && !compact)
                    Text(note,color=colors.primary,style=MaterialTheme.typography.bodySmall)
            }) {
                // Header, navigation, cues, and status belong to the card in every orientation.
                Column(Modifier.fillMaxWidth().then(if(phraseCard && !configuring)
                    Modifier.height((if(openStringsOnly) 80.dp else if(compact) 96.dp else 120.dp)*fontScale) else Modifier),
                    verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(title,style=if(compact || phraseCard) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight=FontWeight.Bold,color=colors.textPrimary,maxLines=2,
                        overflow=TextOverflow.Ellipsis)
                    if(phraseCard) {
                        val cue=if(hint==Hint.HIDDEN) "已撤提示" else displayedSequence?.runtimePlan?.let {
                            planCue(it,if(showingSession) cursor else null)
                        } ?: if(!melody) "${cardChord.name} · ${phraseCue(phraseNow,displayedSequence!!)}"
                            else phraseCue(phraseNow,displayedSequence!!)
                        Text(cue,modifier=Modifier.testTag("phrase-cue"),style=MaterialTheme.typography.labelLarge,
                            color=colors.primary,maxLines=2,overflow=TextOverflow.Ellipsis)
                        if(!melody) Text("下一：$nextChord",style=MaterialTheme.typography.labelLarge,color=colors.textSecondary,maxLines=1)
                    } else if(page==1) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            ChordSteps(first,onChord,chords)
                            Text("左右滑动切换",modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=colors.textSecondary)
                            PracticeTextButton(onClick={onAudition(cardChord)},enabled=selected && !active && phase!=Phase.PAUSED,
                                contentPadding=PaddingValues(horizontal=6.dp)) { Text("听和弦",style=MaterialTheme.typography.labelLarge) }
                        }
                        if(cardChord.capoFret>0 || cardChord.openStringMidi!=Music.openMidi)
                            Text(cardChord.conditions,style=MaterialTheme.typography.bodySmall,color=colors.textSecondary,modifier=Modifier.testTag("chord-conditions"))
                        if(hint!=Hint.HIDDEN && !configuring) Text(
                            chordTones.filter { hint==Hint.FULL || cardChord.isRoot(it) }.joinToString(" · ") { "${cardChord.role(it)} ${cardChord.noteName(it)}" },
                            style=MaterialTheme.typography.bodySmall,color=colors.textSecondary)
                    } else {
                        Text("${scale.exercise.title} · 每拍${scale.notesPerBeat}音",style=MaterialTheme.typography.bodySmall,color=colors.textSecondary)
                        if(hint==Hint.FULL && nextEvent!=null && !configuring) Text("下一："+
                            if(nextEvent.positions.isEmpty()) "留拍" else nextEvent.positions.joinToString { Music.name(it.midi,true) },
                            style=MaterialTheme.typography.labelMedium,color=colors.textSecondary)
                    }
                    if(hint==Hint.HIDDEN && !phraseCard) Text("已撤提示",color=colors.textSecondary,style=MaterialTheme.typography.labelSmall)
                }
                Box((if(openStringsOnly) Modifier.height(128.dp*fontScale) else Modifier.weight(1f))
                    .fillMaxWidth().testTag("diagram-viewport"),contentAlignment=Alignment.Center) {
                    DiagramContainer(Modifier.fillMaxSize()) {
                      BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                        val viewportHeight=maxHeight
                        if(page==0) {
                            val h=maxOf(if(configuring) 80.dp else 176.dp*fontScale,viewportHeight).coerceAtMost(300.dp*fontScale)
                            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),contentAlignment=Alignment.Center) {
                                val positions=if(scale.exercise==ScaleExercise.CONNECTION && scale.pattern<4)
                                    (Patterns.all[scale.pattern].positions+Patterns.all[scale.pattern+1].positions).distinct()
                                    else Patterns.all[scale.pattern].positions
                                Fretboard(positions,scale.key,hint,false,current,!active && phase!=Phase.PAUSED,h,onTap,showLegend=!configuring)
                            }
                        } else if(melody && displayedSequence!!.route.isNotEmpty()) Fretboard(displayedSequence.route,null,renderHint,false,
                            if(showingSession) current else phraseNow?.positions ?: displayedSequence.runtimePlan?.notes?.take(1)?.map { it.position }.orEmpty(),
                            !active && phase!=Phase.PAUSED,viewportHeight.coerceAtMost(300.dp*fontScale),onTap,moving,
                            nextEvent?.positions.orEmpty(),showLegend=false,capoFret=displayedSequence.capoFret,
                            openStringMidi=displayedSequence.runtimePlan?.mainTrack?.openStringMidi ?: Music.openMidi.toList(),
                            movingPositions=cursor?.movingPositions ?: moving?.let { listOf(it) }.orEmpty())
                        else if(melody) Text(if(hint==Hint.HIDDEN) "按节拍演奏" else "死音节奏",color=colors.textSecondary)
                        else ChordDiagram(cardChord,if(cardChord.root<0 && hint==Hint.ROOTS) Hint.FULL else hint,
                            if(selected) current else emptyList(),selected && !active && phase!=Phase.PAUSED,
                            viewportHeight.coerceAtMost(390.dp*fontScale).coerceAtMost(availableHeight),onTap)
                      }
                    }
                }
                if(!compact && !phraseCard) Text(if(hint==Hint.FULL) {
                    if(page==0) "主音强调显示 · 当前音带外环" else "左 6 弦 → 右 1 弦 · 根音强调显示 · O 空弦 · X 不弹"
                } else "保留拍点与控制，可随时恢复提示。",style=MaterialTheme.typography.labelSmall,color=colors.textSecondary)
                if(!compact && !wide && !active && page==0) Text(sequence?.explanation ?: scaleResult.reason,
                    style=MaterialTheme.typography.bodySmall,color=colors.textSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxSize().testTag("chord-page").then(if(chordPageScroll) Modifier.verticalScroll(chordScrollState) else Modifier)) {
            Row(Modifier.fillMaxWidth().height(if(chordPageScroll) 600.dp*fontScale else availableHeight),
                horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                if(wide) PracticeContentCard(title="练习提示",modifier=Modifier.width(240.dp).fillMaxHeight()) {
                    Text(if(page==0) "${scale.exercise.title} · 每拍${scale.notesPerBeat}音"
                        else if(!chordPhrases) "认音、听音、逐弦跟练" else "${phrase.passage} · ${phrase.conditions}",
                        style=MaterialTheme.typography.bodySmall,color=colors.textSecondary)
                    Text(sequence?.explanation ?: scaleResult.reason,style=MaterialTheme.typography.bodySmall,color=colors.textPrimary)
                    Text("先听一遍，再拿琴跟练。",style=MaterialTheme.typography.bodySmall,color=colors.textSecondary)
                    Text("标准调弦 · 离线练习",style=MaterialTheme.typography.labelSmall,color=colors.textSecondary)
                }
                if(page==1 && !chordPhrases) ChordDeck(chords.indexOf(first),chords.size,{onChord(chords[it])},
                    Modifier.weight(1f).fillMaxHeight()) { index,selected -> cardContent(chords[index],selected) }
                else Box(Modifier.weight(1f).fillMaxHeight()) { cardContent(shownChord ?: first,true) }
            }
        }
    }
}

/** A stable status slot shared by diagrams and the legacy TAB fallback. */
@Composable
private fun PracticeStatus(status: String,phase: Phase,beatsPerBar: Int,cursor: Cursor?,showingSession: Boolean,compact: Boolean) {
    val colors=PracticeSkin.colors
    val metrics=PracticeSkin.metrics
    val fontScale=LocalDensity.current.fontScale
    Row(Modifier.fillMaxWidth().height((if(compact) 40.dp else 48.dp)*fontScale).testTag("playback-status")
        .background(colors.surfaceSecondary,RoundedCornerShape(metrics.cornerSmall)).padding(horizontal=10.dp,vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Text(status,style=MaterialTheme.typography.labelLarge,color=if(phase==Phase.ERROR) colors.danger else colors.primary,
            modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
        repeat(beatsPerBar) { i ->
            Box(Modifier.padding(start=6.dp).size(9.dp).background(
                if(i==cursor?.beat && showingSession) colors.progressComplete else colors.progressPending,
                RoundedCornerShape(metrics.cornerSmall)))
        }
    }
}
