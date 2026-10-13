package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.a3322505a.guitarlearning.immersiveStatusBar
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*


internal data class PracticeSettingsState(
    val settingsPanel: Int,val page: Int,val chordPhrases: Boolean,val tabletControls: Boolean,
    val scale: ScaleOptions,val first: Chord,val phrase: Phrase,val family: Phrase,val versions: List<Phrase>,
    val catalogueGroups: Map<String,List<Phrase>>,val chordGroups: Map<String,List<Chord>>,val relatedTitle: String?,
    val bpm: Int,val repeats: Int,val firstBar: Int,val lastBar: Int,val range: BarRange,val wholeRange: BarRange,
    val guitar: Boolean,val click: Boolean,val moreExercisesOpen: Boolean,val beatsPerBar: Int,
    val sequence: Sequence?,val displayedSequence: Sequence?,val scaleResult: RouteResult)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PracticeSettings(state: PracticeSettingsState,onClose: ()->Unit,onBpm: (Int)->Unit,
    onRepeats: (Int)->Unit,onGuitar: (Boolean)->Unit,onClick: (Boolean)->Unit,onMoreExercises: ()->Unit,
    onScale: (ScaleOptions)->Unit,onPhrase: (Phrase)->Unit,onChord: (Chord)->Unit,onRange: (BarRange)->Unit) = with(state) {
if(settingsPanel!=0) ModalBottomSheet(onDismissRequest={onClose()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Paper) {
    val sheetView=LocalView.current
    DisposableEffect(sheetView) {
        // The dialog ancestor may be attached after the first composition.
        fun applyWindowPolicy() {
            generateSequence(sheetView) { it.parent as? android.view.View }
                .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window?.let(::immersiveStatusBar)
        }
        val attached=object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) { applyWindowPolicy() }
            override fun onViewDetachedFromWindow(view: android.view.View) { }
        }
        sheetView.addOnAttachStateChangeListener(attached)
        if(sheetView.isAttachedToWindow) applyWindowPolicy()
        onDispose { sheetView.removeOnAttachStateChangeListener(attached) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=24.dp).testTag(if(settingsPanel==1) "practice-settings" else "tempo-settings"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text(when(settingsPanel) { 1 -> "练习设置"; 2 -> "速度与播放"; else -> "指板与演奏提示" },style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            PracticeTextButton(onClick={onClose()}) { Text("完成") }
        }
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        if(settingsPanel==3) {
            Text("横向指板上方是 1 弦，下方是 6 弦；左侧数字为弦号，上方数字为品位。",style=MaterialTheme.typography.bodySmall)
            Text("绿色为路线音位，粉色为当前音，蓝色圈为下一音。同一音位可同时是当前和下一音。",style=MaterialTheme.typography.bodySmall)
            Text("和弦图左至右为 6→1 弦，金色为根音；O 为空弦，X 不弹。",style=MaterialTheme.typography.bodySmall)
            Text("x 为死音；P.M. 为手掌制音；↑/↓ 为拨弦方向；滑音、击弦、勾弦只拨来源音一次。休止继续数拍。",style=MaterialTheme.typography.bodySmall)
            if(displayedSequence?.swing!=Swing.NONE && displayedSequence!=null) Text("${displayedSequence.swing.title}：谱面保留原时值，按长短2:1演奏。b2.0 表示推高2半音；小数字／是拍上的32分装饰滑音。滑入与滑出不指定终点，声音为采样近似。",style=MaterialTheme.typography.bodySmall)
            Text("撤提示会隐藏当前与下一音位及演奏提示，保留拍点与播放控制。",style=MaterialTheme.typography.bodySmall)
        } else if(settingsPanel==2) {
            Text("调整速度或循环后，下一次从头开始；声音开关立即生效。",style=MaterialTheme.typography.bodySmall,color=Muted)
            Label("速度") {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={onBpm((bpm-5).coerceAtLeast(30))},enabled=bpm>30,modifier=Modifier.then(if(tabletControls) Modifier.heightIn(min=64.dp) else Modifier).testTag("tempo-slower")) { Text("−5") }
                    Text("$bpm BPM",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick={onBpm((bpm+5).coerceAtMost(180))},enabled=bpm<180,modifier=Modifier.then(if(tabletControls) Modifier.heightIn(min=64.dp) else Modifier).testTag("tempo-faster")) { Text("+5") }
                }
                Slider(value=bpm.toFloat(),onValueChange={ value -> val next=value.toInt();if(next!=bpm) { onBpm(next) } },valueRange=30f..180f,modifier=Modifier.testTag("tempo-slider"))
            }
            Label("循环与声音") {
                Text("跟练次数")
                val values=listOf(1,4,8,0)
                Choices(listOf("1 次","4 次","8 次","持续循环"),values.indexOf(repeats),Mint) { if(repeats!=values[it]) { onRepeats(values[it]) } }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("吉他声",modifier=Modifier.weight(1f));Switch(guitar,onCheckedChange={onGuitar(it)},modifier=Modifier.testTag("guitar-switch"))
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("节拍声",modifier=Modifier.weight(1f));Switch(click,onCheckedChange={onClick(it)},modifier=Modifier.testTag("click-switch"))
                }
                Text("$beatsPerBar/4 · 跟练预备 1 小节 · 示范只播 1 遍",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        } else {
        Text("选择要练的内容；更改后，下一次从头开始。",style=MaterialTheme.typography.bodySmall,color=Muted)
        if(page==0) {
            Label("选音阶与指型") {
                Choices(Tonality.entries.map { it.title },scale.key.ordinal,Mint) { onScale(scale.copy(key=Tonality.entries[it])) }
                Choices(Patterns.all.map { "${it.solfege} · ${it.caged}" },scale.pattern,Sky) { onScale(scale.copy(pattern=it)) }
                if(scale.pattern==2) Text("la 指型三弦 4 品 B：可伸指或轻微移位。",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
            Label("练习方式") {
                val common=listOf(ScaleExercise.ASCEND,ScaleExercise.THIRDS,ScaleExercise.CONNECTION)
                Choices(common.map { it.title },common.indexOf(scale.exercise)) { val kind=common[it];onScale(scale.copy(exercise=kind,octave=if(kind==ScaleExercise.CONNECTION) false else scale.octave)) }
                PracticeTextButton(onClick={onMoreExercises()},modifier=Modifier.testTag("more-exercises-toggle")) { Text(if(moreExercisesOpen) "收起更多练法 ▴" else "更多练法 ▾") }
                if(moreExercisesOpen) {
                    val more=listOf(ScaleExercise.ROOTS,ScaleExercise.THREE,ScaleExercise.CHANGE_START)
                    Choices(more.map { it.title },more.indexOf(scale.exercise)) { onScale(scale.copy(exercise=more[it])) }
                }
                if(scale.exercise!=ScaleExercise.ROOTS && scale.exercise!=ScaleExercise.CONNECTION) Choices(listOf("一八度","整型音域"),if(scale.octave) 0 else 1,Mint) { onScale(scale.copy(octave=it==0)) }
                Choices(listOf("每拍1音","每拍2音","每拍3音"),scale.notesPerBeat-1) { onScale(scale.copy(notesPerBeat=it+1)) }
                if(scale.exercise==ScaleExercise.CHANGE_START) {
                    Text("起点级数"); Choices((1..7).map(Int::toString),scale.startDegree-1) { onScale(scale.copy(startDegree=it+1)) }
                }
            }
        } else if(chordPhrases) {
            Label("选择短句") {
                catalogueGroups.forEach { (use,items) ->
                    Text(use,style=MaterialTheme.typography.labelLarge,color=PurpleInk)
                    Choices(items.map { it.title },items.indexOf(family),Pink) { onPhrase(items[it]) }
                }

                if(versions.size>1) {
                    val base=family
                    var versionsOpen by remember(base.id) { mutableStateOf(phrase.relatedId!=null) }
                    PracticeTextButton(onClick={versionsOpen=!versionsOpen},modifier=Modifier.testTag("phrase-versions-toggle")) {
                        Text(if(versionsOpen) "收起更多版本 ▴" else "更多版本 ▾")
                    }
                    if(versionsOpen) Choices(versions.map { it.version ?: it.title },versions.indexOf(phrase),Sky) { onPhrase(versions[it]) }
                }
                Text("${phrase.passage} · ${phrase.conditions}",style=MaterialTheme.typography.bodySmall,color=Muted)
                Text("${phrase.technique} · ${phrase.tempoLabel} ${phrase.sourceBpm} BPM · ${phrase.difficulty}",style=MaterialTheme.typography.bodySmall,color=Muted)
                Text("${phrase.category} · ${phrase.source}",style=MaterialTheme.typography.bodySmall,color=Muted)
                Text(phrase.sequence.explanation,style=MaterialTheme.typography.bodySmall,color=Muted)
                Text("目标：${phrase.goal} 建议起步 ${phrase.suggestedBpm} BPM。",style=MaterialTheme.typography.bodySmall,color=BlueInk)
                phrase.relatedId?.let { Text("基础素材：${relatedTitle}",style=MaterialTheme.typography.bodySmall,color=Muted) }
            }
            Label("练习小节") {
                Text("当前范围：${range.first+phrase.sequence.originalBar+1}–${range.last+phrase.sequence.originalBar+1} 小节；完整经过范围一次计一轮。",modifier=Modifier.testTag("phrase-range"))
                OutlinedButton(onClick={if(range!=wholeRange) { onRange(wholeRange) }},modifier=Modifier.then(if(tabletControls) Modifier.heightIn(min=64.dp) else Modifier)) { Text("整句") }
                val bars=(0..wholeRange.last).toList()
                Text("起始小节")
                Choices(bars.map { "${it+phrase.sequence.originalBar+1}" },range.first,Mint) { if(it!=firstBar) { onRange(BarRange(it,maxOf(lastBar,it))) } }
                Text("结束小节")
                val endings=(range.first..wholeRange.last).toList()
                Choices(endings.map { "${it+phrase.sequence.originalBar+1}" },endings.indexOf(range.last),Sky) { if(endings[it]!=lastBar) { onRange(BarRange(firstBar,endings[it])) } }
            }
        } else {
            Label("选择和弦") {
                chordGroups.forEach { (group,items) ->
                    Text(group,fontWeight=FontWeight.Bold,color=PurpleInk)
                    Choices(items.map { it.name },items.indexOf(first),Pink) { onChord(items[it]) }
                }
                if(first.source.isNotBlank()) Text("${first.conditions}。${first.source}",style=MaterialTheme.typography.bodySmall,color=BlueInk)
                Text("先看根音和组成音，点图中的音位试听，再听整和弦。",style=MaterialTheme.typography.bodySmall,color=Muted)
                Text("听示范和跟练按有效弦逐根弹，每弦一拍；数字 1—4 表示手指，O 为空弦，X 弦不弹。",style=MaterialTheme.typography.bodySmall,color=Muted)
                Text("no5 表示省略五音；斜杠后的音是最低音；5 表示根音与五音构成的力量和弦，双音 riff 按两根有效弦演奏。斜阳指法用变调夹3品，数字相对变调夹；基础和弦无夹。",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        }
        if(sequence==null) Text(scaleResult.reason,color=RoseInk,style=MaterialTheme.typography.bodySmall)
        }
        }
    }
}
}

