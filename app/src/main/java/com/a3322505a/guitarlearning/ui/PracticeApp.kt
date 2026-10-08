package com.a3322505a.guitarlearning.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

private val ScaleSaver = listSaver<ScaleOptions, Any>(
    save={ listOf(it.pattern,it.key.ordinal,it.exercise.ordinal,it.firstString,it.lastString,it.octave,it.eighth,it.startDegree,it.reverse) },
    restore={ ScaleOptions(it[0] as Int,Tonality.entries[it[1] as Int],ScaleExercise.entries[it[2] as Int],it[3] as Int,it[4] as Int,it[5] as Boolean,it[6] as Boolean,it[7] as Int,it[8] as Boolean) })

/** Wrapping choices keep every shape/chord discoverable without horizontal clipping. */
@Composable
private fun Choices(options: List<String>, selected: Int, color: Color = Lavender, onSelect: (Int)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val longest=options.maxOfOrNull { it.length } ?: 1
        val target=if(longest>8) 158 else if(longest>4) 114 else 76
        val columns=(maxWidth.value / target).toInt().coerceAtLeast(1)
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            options.indices.chunked(columns).forEach { row ->
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    row.forEach { i ->
                        val checked=selected==i
                        Surface(onClick={onSelect(i)},modifier=Modifier.weight(1f).heightIn(min=44.dp),
                            shape=RoundedCornerShape(16.dp), color=if(checked) color else Color(0xFFFAFCFA),
                            border=BorderStroke(if(checked) 1.5.dp else 1.dp,if(checked) Ink.copy(alpha=.25f) else Outline)) {
                            Box(Modifier.padding(horizontal=10.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                                Text(options[i],fontWeight=if(checked) FontWeight.Bold else FontWeight.Medium,
                                    color=if(checked) Ink else Muted,style=MaterialTheme.typography.labelLarge)
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
private fun Label(title: String, content: @Composable ColumnScope.()->Unit) {
    CandyCard(Modifier.fillMaxWidth()) {
        Text(title,color=Ink,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeApp(player: Player) {
    val context=LocalContext.current
    val preferences=remember { context.getSharedPreferences("practice-settings",0) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var scale by rememberSaveable(stateSaver=ScaleSaver) { mutableStateOf(ScaleOptions()) }
    var firstName by rememberSaveable { mutableStateOf("C") }
    var secondName by rememberSaveable { mutableStateOf("Am") }
    var chordKindIndex by rememberSaveable { mutableIntStateOf(ChordExercise.CHANGE.ordinal) }
    var beats by rememberSaveable { mutableIntStateOf(4) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var repeats by rememberSaveable { mutableIntStateOf(4) }
    var hintIndex by rememberSaveable { mutableIntStateOf(preferences.getInt("hint",0).coerceIn(0,2)) }
    var degrees by rememberSaveable { mutableStateOf(preferences.getBoolean("degrees",false)) }
    var guitar by rememberSaveable { mutableStateOf(preferences.getBoolean("guitar",true)) }
    var click by rememberSaveable { mutableStateOf(preferences.getBoolean("click",true)) }
    var viewPreference by rememberSaveable { mutableIntStateOf(preferences.getInt("view",-1).coerceIn(-1,1)) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    val hint=Hint.entries[hintIndex]
    val first=Chords.named(firstName); val second=Chords.named(secondName)
    val chordKind=ChordExercise.entries[chordKindIndex]
    player.guitar=guitar; player.click=click
    val scaleResult=remember(scale) { Routes.scale(scale) }
    val chordSeq=remember(first,second,chordKind,beats) { chordSequence(first,second,chordKind,beats) }
    val sequence=if(page==0) scaleResult.sequence else chordSeq
    val progress=player.progress
    val session=player.session
    val cursor=session?.cursor(progress.tick)
    val practiceSession=session?.takeUnless { it.preview }
    val showingSession=practiceSession!=null && progress.phase!=Phase.IDLE
    val displayedSequence=if(showingSession) practiceSession!!.sequence else sequence
    val renderCursor=cursor?.takeIf { showingSession }
    val event=if(showingSession && cursor?.pre==false) displayedSequence?.eventAt(cursor.localTick) else null
    val nextEvent=event?.let { now -> displayedSequence?.events?.firstOrNull { it.tick > now.tick } ?: displayedSequence?.events?.firstOrNull() }
    val current=event?.positions ?: emptyList()
    val section=if(showingSession && cursor?.pre==false) displayedSequence!!.sectionAt(cursor.localTick) else -1
    val shownChord=if(page==1 && section>=0) Chords.named(displayedSequence!!.sections[section].chord) else first
    val nextChord=if(chordKind==ChordExercise.CHECK) "" else if(section>=0) displayedSequence!!.sections[(section+1)%displayedSequence.sections.size].chord else second.name
    fun changed() { player.invalidate(); note="" }
    fun scaleChange(value: ScaleOptions) { changed(); scale=value }
    fun firstChange(value: Chord) { changed(); firstName=value.name; if(secondName==firstName) secondName=Chords.all.first { it!=value }.name }
    fun tap(p: Position) {
        player.audition(listOf(p))
        note="${p.string} 弦 ${p.fret} 品 · ${Music.name(p.midi,true)}" + if(page==0) " · ${scale.key.degree(p.midi)} 级" else " · ${shownChord.role(p)}"
    }
    fun setHint(index: Int) { hintIndex=index; note=""; preferences.edit().putInt("hint",index).apply() }
    val status=when {
        session?.preview==true && player.active -> "点音试听"
        player.active && cursor?.pre==true -> "预备拍 ${cursor.beat+1} / 4"
        progress.phase==Phase.PLAYING -> "第 ${cursor!!.loop+1}${if(session!!.repeats>0) "/${session.repeats}" else ""} 轮 · 第 ${cursor.bar+1} 小节"
        progress.phase==Phase.PAUSED -> "已暂停 · 从${if(cursor?.pre==true) "预备" else "第 ${(cursor?.bar ?: 0)+1}"}小节继续"
        progress.phase==Phase.PREPARING -> "准备声音…"
        progress.phase==Phase.ENDED -> progress.message
        progress.phase==Phase.ERROR -> progress.message
        else -> "准备好，就开始一轮"
    }
    BackHandler(enabled=!settingsOpen && (player.active || progress.phase==Phase.PAUSED)) { player.stop() }
    PracticeTheme {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val shortWindow=maxHeight<520.dp
        Scaffold(containerColor=Paper,bottomBar={
            Surface(color=Color.White,shadowElevation=3.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(!shortWindow) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={settingsOpen=true},contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) { Text("$bpm BPM",fontWeight=FontWeight.Bold) }
                        Text("· 4/4",color=Muted,style=MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick={setHint(if(hint==Hint.HIDDEN) 0 else 2)},contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) { Text(if(hint==Hint.HIDDEN) "恢复提示" else "撤提示") }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(player.active || progress.phase==Phase.PAUSED) {
                            Button(onClick={if(player.active) player.pause() else player.resume()},modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text(if(player.active) "暂停" else "继续",fontWeight=FontWeight.Bold) }
                            OutlinedButton(onClick={player.restart()},modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("重来") }
                        } else {
                            OutlinedButton(onClick={sequence?.let { note=""; player.start(it,bpm,false,1) }},enabled=sequence!=null,modifier=Modifier.weight(.85f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)) { Text("听示范") }
                            Button(onClick={sequence?.let { note=""; player.start(it,bpm,true,repeats) }},enabled=sequence!=null,modifier=Modifier.weight(1.25f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)) { Text("开始跟练",fontWeight=FontWeight.Bold) }
                        }
                        TextButton(onClick={player.stop();note=""},modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)) { Text("结束",color=Muted) }
                    }
                }
            }
        }) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
                val wide=maxWidth>=700.dp
                val compact=maxHeight<520.dp
                val tab=if(viewPreference<0) maxWidth>=840.dp && !compact else viewPreference==1
                val fontScale=LocalDensity.current.fontScale
                val content: @Composable () -> Unit = {
                    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(!compact || !wide) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("练习视图",style=MaterialTheme.typography.labelLarge,color=Muted)
                            Spacer(Modifier.weight(1f))
                            listOf("指板","六线谱").forEachIndexed { index,title ->
                                FilterChip(selected=tab==(index==1),onClick={viewPreference=index;preferences.edit().putInt("view",index).apply()},label={Text(title)},modifier=Modifier.padding(start=6.dp))
                            }
                        }
                        CandyCard(Modifier.weight(1f).fillMaxWidth().testTag("practice-card")) {
                            val target=if(page==1) "${shownChord.name} · ${if(chordKind==ChordExercise.CHECK) "逐弦检查" else "下一：$nextChord"}"
                                else if(hint==Hint.FULL && event!=null) { if(event.positions.isEmpty()) "现在：留拍" else "现在：${event.positions.joinToString { Music.name(it.midi,true) }}" }
                                else "${scale.key.title} · ${Patterns.all[scale.pattern].solfege} 指型"
                            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(if(page==1) Pink else Mint,Color.White)),RoundedCornerShape(18.dp)).padding(horizontal=12.dp,vertical=if(compact) 4.dp else 12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(target,style=if(compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Ink)
                                    if(page==0 && hint==Hint.FULL && nextEvent!=null) Text("下一："+if(nextEvent.positions.isEmpty()) "留拍" else nextEvent.positions.joinToString { Music.name(it.midi,true) },style=MaterialTheme.typography.labelMedium,color=BlueInk)
                                }
                                if(hint==Hint.HIDDEN) Text("已撤提示",color=Muted,style=MaterialTheme.typography.labelSmall)
                            }
                            if(tab) {
                                if(!compact) Text(if(displayedSequence?.events?.any { it.strum }==true) "↑ 下拨：低音弦 → 高音弦 · 下方是节奏" else "上为 1 弦 · 数字为品位 · 下方是节奏",style=MaterialTheme.typography.labelSmall,color=Muted)
                                if(displayedSequence!=null) Tablature(displayedSequence,hint,if(page==0) scale.key else null,renderCursor,player.active || progress.phase==Phase.PAUSED,Modifier.weight(1f))
                                else Text(scaleResult.reason,color=RoseInk)
                            } else {
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                                    // Labels remain readable; exceptionally short/large-font windows can scroll the diagram region.
                                    val minimum=if(compact) 176.dp else if(page==0) 176.dp else 215.dp
                                    val h=maxOf(minimum*fontScale,maxHeight).coerceAtMost(if(page==0) 300.dp*fontScale else 390.dp*fontScale)
                                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),contentAlignment=Alignment.Center) {
                                        if(page==0) {
                                            val positions=if(scale.exercise==ScaleExercise.CONNECTION && scale.pattern<4) (Patterns.all[scale.pattern].positions+Patterns.all[scale.pattern+1].positions).distinct() else Patterns.all[scale.pattern].positions
                                            Fretboard(positions,scale.key,hint,degrees,current,!player.active,h,::tap)
                                        } else ChordDiagram(shownChord,hint,current,!player.active,h,::tap)
                                    }
                                }
                                if(!compact) Text(if(hint==Hint.FULL) { if(page==0) "金色：主音 · 绿色：音阶音 · 粉色：当前音" else "金色根音 · O 空弦 · X 不弹 · 数字为手指" } else "保留拍点与控制，可随时恢复提示。",style=MaterialTheme.typography.labelSmall,color=Muted)
                            }
                            if(!compact || !wide) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text(status,style=MaterialTheme.typography.labelLarge,color=if(progress.phase==Phase.ERROR) RoseInk else Green,modifier=Modifier.weight(1f))
                                repeat(4) { i ->
                                    Box(Modifier.padding(start=6.dp).size(if(i==cursor?.beat && showingSession) 11.dp else 8.dp).background(if(i==cursor?.beat && showingSession) Green else Outline,RoundedCornerShape(8.dp)))
                                }
                            }
                            if(showingSession && cursor?.pre==false && !compact) LinearProgressIndicator(progress={ (cursor.localTick / displayedSequence!!.length).toFloat() },modifier=Modifier.fillMaxWidth(),color=Green,trackColor=Mint)
                            if(note.isNotEmpty() && hint==Hint.FULL && !compact) Text(note,color=Green,style=MaterialTheme.typography.bodySmall)
                        }
                        if(!compact && !wide && !player.active) Surface(color=Warm,shape=RoundedCornerShape(18.dp)) {
                            Text(sequence?.explanation ?: scaleResult.reason,modifier=Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=Color(0xFF775829))
                        }
                    }
                }
                Column(Modifier.fillMaxSize().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        if(!compact) { PickMotif(); Spacer(Modifier.width(8.dp)) }
                        Text("吉他练习",style=if(compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        if(compact && wide) listOf("指板","六线谱").forEachIndexed { index,title ->
                            FilterChip(selected=tab==(index==1),onClick={viewPreference=index;preferences.edit().putInt("view",index).apply()},label={Text(title)},modifier=Modifier.padding(start=6.dp))
                        }
                        listOf("音阶","和弦").forEachIndexed { index,title ->
                            FilterChip(selected=page==index,onClick={if(page!=index) { player.stop();page=index;note="" }},label={Text(title)},modifier=Modifier.padding(start=6.dp))
                        }
                    }
                    val summary: @Composable () -> Unit = {
                        Surface(onClick={settingsOpen=true},shape=RoundedCornerShape(22.dp),color=if(page==0) Mint else Lavender,border=BorderStroke(1.dp,Outline),modifier=Modifier.fillMaxWidth().testTag("config-summary")) {
                            Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Text(if(page==0) "${scale.key.title} · ${Patterns.all[scale.pattern].solfege} / ${Patterns.all[scale.pattern].caged}" else "$firstName → $secondName",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                    Text(if(page==0) "${scale.exercise.title} · ${if(scale.eighth) "八分" else "四分"}音符" else "${chordKind.title} · 每和弦 $beats 拍",style=MaterialTheme.typography.bodySmall,color=Muted)
                                }
                                Text("设置 ›",color=if(page==0) Green else PurpleInk,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                    if(wide) {
                        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                summary()
                                if(compact) {
                                    Text(status,color=Green,style=MaterialTheme.typography.labelLarge)
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        repeat(4) { i -> Box(Modifier.size(9.dp).background(if(i==cursor?.beat && showingSession) Green else Outline,RoundedCornerShape(9.dp))) }
                                    }
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        TextButton(onClick={settingsOpen=true}) { Text("$bpm BPM") }
                                        TextButton(onClick={setHint(if(hint==Hint.HIDDEN) 0 else 2)}) { Text(if(hint==Hint.HIDDEN) "恢复提示" else "撤提示") }
                                    }
                                }
                                CandyCard(Modifier.fillMaxWidth(),Warm) {
                                    Text("练习小提示",fontWeight=FontWeight.Bold,color=Color(0xFF775829))
                                    Text(sequence?.explanation ?: scaleResult.reason,style=MaterialTheme.typography.bodySmall)
                                    Text("先听一遍，再拿琴跟练。",style=MaterialTheme.typography.bodySmall,color=Muted)
                                }
                                Text("标准调弦 · 离线练习",style=MaterialTheme.typography.labelSmall,color=Muted)
                            }
                            Box(Modifier.weight(1f)) { content() }
                        }
                    } else {
                        if(!compact) summary() else TextButton(onClick={settingsOpen=true},contentPadding=PaddingValues(0.dp)) { Text("${if(page==0) scale.exercise.title else chordKind.title} · 设置 ›") }
                        Box(Modifier.weight(1f)) { content() }
                    }
                }
            }
        }
      }
        if(settingsOpen) ModalBottomSheet(onDismissRequest={settingsOpen=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Paper) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("练习设置",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick={settingsOpen=false}) { Text("完成") }
                }
                Text("更改内容、速度或循环会结束本次播放，下次从头开始。提示和声音开关即时生效。",style=MaterialTheme.typography.bodySmall,color=Muted)
                if(page==0) {
                    Label("选音阶与指型") {
                        Choices(Tonality.entries.map { it.title },scale.key.ordinal,Mint) { scaleChange(scale.copy(key=Tonality.entries[it])) }
                        Choices(Patterns.all.map { "${it.solfege} · ${it.caged}" },scale.pattern,Sky) { scaleChange(scale.copy(pattern=it)) }
                        Text(Patterns.all[scale.pattern].title,style=MaterialTheme.typography.bodyMedium)
                    }
                    Label("练什么、怎么弹") {
                        Choices(ScaleExercise.entries.map { it.title },scale.exercise.ordinal) { val kind=ScaleExercise.entries[it];scaleChange(scale.copy(exercise=kind,octave=if(kind==ScaleExercise.CONNECTION) false else scale.octave)) }
                        Choices(listOf("主音起的一八度","遍历指型音域"),if(scale.octave) 0 else 1,Mint) { scaleChange(scale.copy(octave=it==0)) }
                        Text("上界弦号")
                        Choices((1..6).map { "$it 弦" },scale.firstString-1,Sky) { scaleChange(scale.copy(firstString=it+1,lastString=maxOf(it+1,scale.lastString))) }
                        Text("下界弦号")
                        Choices((1..6).map { "$it 弦" },scale.lastString-1,Sky) { scaleChange(scale.copy(lastString=it+1,firstString=minOf(it+1,scale.firstString))) }
                        Choices(listOf("四分音符","八分音符"),if(scale.eighth) 1 else 0) { scaleChange(scale.copy(eighth=it==1)) }
                        Choices(listOf("原顺序／上行","反向／下行"),if(scale.reverse) 1 else 0) { scaleChange(scale.copy(reverse=it==1)) }
                        if(scale.exercise==ScaleExercise.CHANGE_START) {
                            Text("起点级数"); Choices((1..7).map(Int::toString),scale.startDegree-1) { scaleChange(scale.copy(startDegree=it+1)) }
                        }
                        Text(sequence?.explanation ?: scaleResult.reason,style=MaterialTheme.typography.bodySmall)
                        if(hint==Hint.FULL && sequence!=null) Text("本次路线（弦 / 品）："+sequence.events.filter { it.positions.isNotEmpty() }.joinToString(" → ") { it.positions.first().let { p -> "${p.string}/${p.fret}" } },style=MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Label("选和弦与练法") {
                        Text("第一个和弦"); Choices(Chords.all.map { it.name },Chords.all.indexOf(first),Pink) { firstChange(Chords.all[it]) }
                        if(chordKind!=ChordExercise.CHECK) {
                            Text("第二个和弦"); val others=Chords.all.filter { it!=first }
                            Choices(others.map { it.name },others.indexOf(second),Sky) { changed();secondName=others[it].name }
                        }
                        Text("练习方式"); Choices(ChordExercise.entries.map { it.title },chordKind.ordinal) { changed();chordKindIndex=it }
                        if(chordKind!=ChordExercise.CHECK) {
                            Text("每个和弦的长度"); val values=listOf(1,2,4,8)
                            Choices(values.map { "$it 拍" },values.indexOf(beats),Mint) { changed();beats=values[it] }
                        }
                        Text(sequence!!.explanation,style=MaterialTheme.typography.bodySmall)
                    }
                    Label("点音与和弦说明") {
                        if(hint==Hint.FULL) {
                            Text("O 空弦 · X 不弹 · 1 食指、2 中指、3 无名指、4 小指",style=MaterialTheme.typography.bodySmall)
                            Text(shownChord.positions.joinToString(" · ") { "${Music.name(it.midi)} ${shownChord.role(it)}" },style=MaterialTheme.typography.bodySmall)
                            Choices((6 downTo 1).map { "$it 弦${if(shownChord.frets[6-it]==null) " X" else ""}" },-1,Mint) { i -> shownChord.positions.firstOrNull { it.string==6-i }?.takeUnless { player.active }?.let(::tap) }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={player.audition(shownChord.positions)},enabled=!player.active) { Text("整和弦试听") }
                                TextButton(onClick={player.start(chordSequence(shownChord,second,ChordExercise.CHECK,4),bpm,false,1)},enabled=!player.active) { Text("依次试听") }
                            }
                            if(chordKind==ChordExercise.ARPEGGIO) Text("分解弦序：${shownChord.arpeggio.joinToString(" → ") { it.string.toString() }}；每弦一拍。",color=Green)
                        } else Text("提示已收起，恢复后可查看弦品和用指。",color=Muted)
                        if(shownChord.name in listOf("D","Dm","A","Am")) {
                            Text(if(hint!=Hint.FULL) "大、小和弦对照：三音降低半音。" else if(shownChord.name.startsWith("D")) "D / Dm：1 弦从 2 品 F♯ 改为 1 品 F。" else "A / Am：2 弦从 2 品 C♯ 改为 1 品 C。",style=MaterialTheme.typography.bodySmall)
                            val counterpart=when(shownChord.name) { "D"->"Dm";"Dm"->"D";"A"->"Am";else->"A" }
                            TextButton(onClick={firstChange(Chords.named(counterpart))}) { Text("查看 $counterpart 对照") }
                        }
                        if(player.active) Text("播放中不能点音试听；请先暂停。",color=Green)
                        if(note.isNotEmpty() && hint==Hint.FULL) Text(note,color=Green)
                    }
                }
                Label("自主对照") {
                    Choices(Hint.entries.map { it.title },hintIndex,Mint,::setHint)
                    if(page==0) Choices(listOf("音名","调内级数"),if(degrees) 1 else 0,Sky) { degrees=it==1;preferences.edit().putBoolean("degrees",degrees).apply() }
                    Text("撤提示同时作用于指板和六线谱；恢复对照保留当前进度。",style=MaterialTheme.typography.bodySmall,color=Muted)
                }
                Label("速度与循环") {
                    Text("$bpm BPM · 4/4 · 跟练预备 1 小节",fontWeight=FontWeight.Bold)
                    Slider(value=bpm.toFloat(),onValueChange={changed();bpm=it.toInt()},valueRange=30f..180f,steps=149)
                    val values=listOf(1,4,8,0)
                    Choices(listOf("1 次","4 次","8 次","持续循环"),values.indexOf(repeats),Mint) { changed();repeats=values[it] }
                    Text("示范 1 遍后停。暂停后从所在小节起点继续。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("示范音",modifier=Modifier.weight(1f));Switch(guitar,onCheckedChange={guitar=it;player.guitar=it;preferences.edit().putBoolean("guitar",it).apply()})
                        Spacer(Modifier.width(20.dp));Text("节拍声",modifier=Modifier.weight(1f));Switch(click,onCheckedChange={click=it;player.click=it;preferences.edit().putBoolean("click",it).apply()})
                    }
                    Text("两个声音均关闭，视觉拍点仍推进。",style=MaterialTheme.typography.bodySmall,color=Muted)
                }
                Label("音阶与和弦的联系") {
                    Text("C 大调的 1–3–5 是 C–E–G，它们组成 C 和弦。",style=MaterialTheme.typography.bodyMedium)
                    if(hint==Hint.FULL) Text("C 开放指法实际发声：X · C · E · G · C · E（6→1 弦）；同一组成音可出现在不同八度。",style=MaterialTheme.typography.bodySmall)
                    if(page==0 && scale.key==Tonality.A_MINOR) Text("A 自然小调的主音是 A，C 是 3 级／小三度；C 和弦的根音仍是 C。",style=MaterialTheme.typography.bodySmall)
                    Text("调性主音与和弦根音分别看待；其他和弦的根、三、五不等于 C 大调 1、3、5。",style=MaterialTheme.typography.bodySmall,color=Muted)
                }
                Text("标准调弦 E A D G B E（6→1 弦） · 离线使用 · 3.1.0-local02",style=MaterialTheme.typography.labelSmall,color=Muted)
            }
        }
    }
}
