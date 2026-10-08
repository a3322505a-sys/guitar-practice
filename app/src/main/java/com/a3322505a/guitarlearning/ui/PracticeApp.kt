package com.a3322505a.guitarlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

@Composable
private fun Choices(options: List<String>, selected: Int, onSelect: (Int)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, text -> FilterChip(selected=selected==i,onClick={onSelect(i)},label={Text(text)}) }
    }
}
@Composable
private fun Block(title: String, content: @Composable ColumnScope.()->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=Ink)
            content()
        }
    }
}

@Composable
fun PracticeApp(player: Player) {
    val context=LocalContext.current
    val preferences=remember { context.getSharedPreferences("practice-settings",0) }
    var page by remember { mutableIntStateOf(0) }
    var scale by remember { mutableStateOf(ScaleOptions()) }
    var first by remember { mutableStateOf(Chords.named("C")) }
    var second by remember { mutableStateOf(Chords.named("Am")) }
    var chordKind by remember { mutableStateOf(ChordExercise.CHANGE) }
    var beats by remember { mutableIntStateOf(4) }
    var bpm by remember { mutableIntStateOf(60) }
    var repeats by remember { mutableIntStateOf(4) }
    var hint by remember { mutableStateOf(Hint.entries[preferences.getInt("hint",0)]) }
    var degrees by remember { mutableStateOf(preferences.getBoolean("degrees",false)) }
    var guitar by remember { mutableStateOf(preferences.getBoolean("guitar",true)) }
    var click by remember { mutableStateOf(preferences.getBoolean("click",true)) }
    var note by remember { mutableStateOf("") }
    player.guitar=guitar; player.click=click
    val scaleResult=remember(scale) { Routes.scale(scale) }
    val sequence=if(page==0) scaleResult.sequence else remember(first,second,chordKind,beats) { chordSequence(first,second,chordKind,beats) }
    val progress=player.progress
    val currentSession=player.session
    val playing=progress.phase==Phase.PLAYING
    val tick=currentSession?.localTick(progress.tick) ?: 0.0
    val pre=currentSession!=null && progress.tick<currentSession.preTicks
    val event=if(playing && !pre) currentSession?.sequence?.eventAt(tick) else null
    val current=event?.positions ?: emptyList()
    val section=currentSession?.sequence?.sectionAt(tick) ?: -1
    val shownChord=if(page==1 && playing && !pre && section>=0) Chords.named(currentSession!!.sequence.sections[section].chord) else first
    val nextChord=if(page==1 && currentSession?.sequence?.sections?.isNotEmpty()==true && section>=0)
        currentSession.sequence.sections[(section+1)%currentSession.sequence.sections.size].chord else second.name
    fun scaleChange(newValue: ScaleOptions) { player.invalidate(); scale=newValue; note="" }
    fun firstChange(chord: Chord) { player.invalidate(); first=chord; if(first==second) second=Chords.all.first { it!=first }; note="" }
    fun tap(p: Position) {
        player.audition(listOf(p))
        note="${p.string} 弦 ${p.fret} 品 · ${Music.name(p.midi,true)}" + if(page==0) " · ${scale.key.degree(p.midi)} 级，${scale.key.intervals[scale.key.degree(p.midi)-1]}" else " · ${shownChord.role(p)}"
    }
    BackHandler(enabled=player.active || progress.phase==Phase.PAUSED) { player.stop() }
    MaterialTheme(colorScheme=lightColorScheme(primary=Green,onPrimary=Color.White,primaryContainer=Color(0xFFDBEDE0),background=Paper,surface=Paper,onSurface=Ink,secondary=Green)) {
        Scaffold(containerColor=Paper,bottomBar={
            Surface(color=Paper,shadowElevation=6.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    val status=when {
                        progress.phase==Phase.PLAYING && currentSession?.preview==true -> "点音试听"
                        progress.phase==Phase.PLAYING && pre -> "预备拍 ${tick.toInt()/12+1} / 4"
                        progress.phase==Phase.PLAYING -> "第 ${currentSession!!.loopAt(progress.tick)+1} 轮 · 第 ${tick.toInt()/48+1} 小节 · 第 ${(tick.toInt()%48)/12+1} 拍"
                        else -> progress.message
                    }
                    Text(status,style=MaterialTheme.typography.bodyMedium,color=Green)
                    if(playing) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        repeat(4) { i -> Box(Modifier.size(9.dp).background(if(i==(tick.toInt()%48)/12) Green else Color(0xFFD6DFD8),RoundedCornerShape(9.dp))) }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(player.active) {
                            Button(onClick={player.pause()},modifier=Modifier.weight(1f)) { Text("暂停") }
                            OutlinedButton(onClick={player.restart()},modifier=Modifier.weight(1f)) { Text("重来") }
                        } else if(progress.phase==Phase.PAUSED) {
                            Button(onClick={player.resume()},modifier=Modifier.weight(1f)) { Text("继续") }
                            OutlinedButton(onClick={player.restart()},modifier=Modifier.weight(1f)) { Text("重来") }
                        } else {
                            OutlinedButton(onClick={sequence?.let { note=""; player.start(it,bpm,false,1) }},enabled=sequence!=null,modifier=Modifier.weight(1f)) { Text("听示范") }
                            Button(onClick={sequence?.let { note=""; player.start(it,bpm,true,repeats) }},enabled=sequence!=null,modifier=Modifier.weight(1f)) { Text("开始跟练") }
                        }
                        TextButton(onClick={player.stop(); note=""}) { Text("结束") }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                        TextButton(onClick={player.stop();page=0;note=""}) { Text(if(page==0) "● 音阶" else "音阶",fontWeight=FontWeight.SemiBold) }
                        Spacer(Modifier.width(48.dp))
                        TextButton(onClick={player.stop();page=1;note=""}) { Text(if(page==1) "● 和弦" else "和弦",fontWeight=FontWeight.SemiBold) }
                    }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("吉他练习",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text("看图 · 听示范 · 拿琴练",style=MaterialTheme.typography.bodyMedium,color=Green)
                }
                if(page==0) {
                    Block("选音阶与指型") {
                        Choices(Tonality.entries.map { it.title },scale.key.ordinal) { scaleChange(scale.copy(key=Tonality.entries[it])) }
                        Choices(Patterns.all.map { "${it.solfege} · ${it.caged}" },scale.pattern) { scaleChange(scale.copy(pattern=it)) }
                        Text(Patterns.all[scale.pattern].title,style=MaterialTheme.typography.bodyLarge)
                        Text(if(scale.key==Tonality.C_MAJOR) { if(hint==Hint.FULL) "推荐起点：mi 指型，5 弦 3 品 C 开始的一八度。" else "推荐起点：mi 指型，主音起的一八度。" } else "同一组音位，以 A 为主音；C 是小调 3 级，距 A 为小三度。",style=MaterialTheme.typography.bodySmall)
                    }
                    Block("${scale.key.title} · ${if(hint==Hint.FULL) "点音探索" else hint.title}") {
                        val positions=if(scale.exercise==ScaleExercise.CONNECTION && scale.pattern<4)
                            (Patterns.all[scale.pattern].positions+Patterns.all[scale.pattern+1].positions).distinct() else Patterns.all[scale.pattern].positions
                        Fretboard(positions,scale.key,hint,degrees,current,!player.active,::tap)
                        Text(if(hint==Hint.FULL) "金色：调性主音 · 绿色：其他音阶音 · 指型音位集合不能同时按下" else "保留练习目标和拍点，随时恢复完整提示查看对照。",style=MaterialTheme.typography.bodySmall)
                        if(player.active) Text("播放中不能点音试听；请先暂停。",color=Green)
                        if(note.isNotEmpty() && hint==Hint.FULL) Text(note,color=Green)
                        if(hint==Hint.FULL && playing && !pre) Text(if(current.isEmpty()) "现在：留拍" else "现在：${current.joinToString { Music.name(it.midi,true) }}",color=Green)
                    }
                    Block("练什么、怎么弹") {
                        Choices(ScaleExercise.entries.map { it.title },scale.exercise.ordinal) {
                            val kind=ScaleExercise.entries[it]
                            scaleChange(scale.copy(exercise=kind,octave=if(kind==ScaleExercise.CONNECTION) false else scale.octave))
                        }
                        Text(sequence?.explanation ?: scaleResult.reason)
                        Choices(listOf("主音起的一八度","遍历指型音域"),if(scale.octave) 0 else 1) { scaleChange(scale.copy(octave=it==0)) }
                        Row(verticalAlignment=Alignment.CenterVertically) { Text("从弦 ${scale.firstString} 到弦 ${scale.lastString}") }
                        Text("上界弦号")
                        Choices((1..6).map { "$it 弦" },scale.firstString-1) { scaleChange(scale.copy(firstString=it+1,lastString=maxOf(it+1,scale.lastString))) }
                        Text("下界弦号")
                        Choices((1..6).map { "$it 弦" },scale.lastString-1) { scaleChange(scale.copy(lastString=it+1,firstString=minOf(it+1,scale.firstString))) }
                        Choices(listOf("四分音符","八分音符"),if(scale.eighth) 1 else 0) { scaleChange(scale.copy(eighth=it==1)) }
                        Choices(listOf("原顺序／上行","反向／下行"),if(scale.reverse) 1 else 0) { scaleChange(scale.copy(reverse=it==1)) }
                        if(scale.exercise==ScaleExercise.CHANGE_START) {
                            Text("起点级数")
                            Choices((1..7).map(Int::toString),scale.startDegree-1) { scaleChange(scale.copy(startDegree=it+1)) }
                        }
                        if(hint==Hint.FULL && sequence!=null) {
                            Text("本次路线（弦 / 品）",style=MaterialTheme.typography.labelLarge)
                            Text(sequence.events.filter { it.positions.isNotEmpty() }.joinToString(" → ") { e -> e.positions.first().let { "${it.string}/${it.fret}" } },style=MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Block("选和弦与练法") {
                        Text("第一个和弦")
                        Choices(Chords.all.map { it.name },Chords.all.indexOf(first)) { firstChange(Chords.all[it]) }
                        Choices(ChordExercise.entries.map { it.title },chordKind.ordinal) { player.invalidate(); chordKind=ChordExercise.entries[it] }
                        if(chordKind!=ChordExercise.CHECK) {
                            Text("第二个和弦")
                            val others=Chords.all.filter { it!=first }
                            Choices(others.map { it.name },others.indexOf(second)) { player.invalidate(); second=others[it] }
                        }
                        Text(sequence!!.explanation)
                    }
                    Block("${shownChord.name} · ${if(chordKind==ChordExercise.CHECK) "逐弦检查" else "下一：$nextChord"}") {
                        ChordDiagram(shownChord,hint,current,!player.active,::tap)
                        if(hint==Hint.FULL) {
                            Text("O 空弦 · X 不弹 · 1 食指、2 中指、3 无名指、4 小指",style=MaterialTheme.typography.bodySmall)
                            Text("根音金色；其余组成音绿色。底部是实际发声音名。",style=MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                (6 downTo 1).forEach { string ->
                                    val p=shownChord.positions.firstOrNull { it.string==string }
                                    OutlinedButton(onClick={tap(p!!)},enabled=p!=null && !player.active,contentPadding=PaddingValues(horizontal=8.dp)) { Text("$string 弦") }
                                }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={player.audition(shownChord.positions)},enabled=!player.active) { Text("整和弦试听") }
                                TextButton(onClick={note=""; player.start(chordSequence(shownChord,second,ChordExercise.CHECK,4),bpm,false,1)},enabled=!player.active) { Text("依次试听") }
                            }
                            if(note.isNotEmpty()) Text(note,color=Green)
                            Text(shownChord.positions.joinToString(" · ") { "${Music.name(it.midi)} ${shownChord.role(it)}" },style=MaterialTheme.typography.bodySmall)
                        } else Text("练习目标：${shownChord.name}。可隐藏指法自行弹，再恢复提示对照。",style=MaterialTheme.typography.bodySmall)
                        if(player.active) Text("播放中不能点音试听；请先暂停。",color=Green)
                        if(chordKind==ChordExercise.ARPEGGIO && hint==Hint.FULL) Text("分解弦序：${shownChord.arpeggio.joinToString(" → ") { it.string.toString() }}；每弦一拍。",color=Green)
                        if(shownChord.name in listOf("D","Dm","A","Am")) {
                            Text(if(hint!=Hint.FULL) "大、小和弦对照：三音降低半音。" else if(shownChord.name.startsWith("D")) "D / Dm：1 弦从 2 品 F♯ 改为 1 品 F，三音降低半音。" else "A / Am：2 弦从 2 品 C♯ 改为 1 品 C，三音降低半音。",style=MaterialTheme.typography.bodySmall)
                            val counterpart=when(shownChord.name) { "D"->"Dm";"Dm"->"D";"A"->"Am";else->"A" }
                            TextButton(onClick={firstChange(Chords.named(counterpart))}) { Text("查看 $counterpart 对照") }
                        }
                    }
                    if(chordKind!=ChordExercise.CHECK) Block("每个和弦的长度") {
                        val values=listOf(1,2,4,8)
                        Choices(values.map { "$it 拍" },values.indexOf(beats)) { player.invalidate();beats=values[it] }
                    }
                }
                Block("自主对照") {
                    Choices(Hint.entries.map { it.title },hint.ordinal) { hint=Hint.entries[it];note="";preferences.edit().putInt("hint",it).apply() }
                    if(page==0) Choices(listOf("音名","调内级数"),if(degrees) 1 else 0) { degrees=it==1;preferences.edit().putBoolean("degrees",degrees).apply() }
                    Text("先听示范，再撤提示自己弹；也可改起点或顺序。不需要提交答案，也不记录学习成绩。",style=MaterialTheme.typography.bodySmall)
                }
                Block("速度与循环") {
                    Text("$bpm BPM · 4/4 · 跟练预备 1 小节",style=MaterialTheme.typography.bodyLarge)
                    Slider(value=bpm.toFloat(),onValueChange={player.invalidate();bpm=it.toInt()},valueRange=30f..180f,steps=149)
                    val values=listOf(1,4,8,0)
                    Choices(listOf("1 次","4 次","8 次","持续循环"),values.indexOf(repeats)) { player.invalidate();repeats=values[it] }
                    Text("示范演奏 1 遍后停；跟练使用上面的循环次数。暂停后从当前小节起点继续。",style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("示范音");Switch(guitar,onCheckedChange={guitar=it;player.guitar=it;preferences.edit().putBoolean("guitar",it).apply()})
                        Text("节拍声");Switch(click,onCheckedChange={click=it;player.click=it;preferences.edit().putBoolean("click",it).apply()})
                    }
                    Text("两个声音均关闭时，视觉拍点仍继续。",style=MaterialTheme.typography.bodySmall)
                }
                Block("音阶与和弦的联系") {
                    Text("C 大调的 1–3–5 是 C–E–G，它们组成 C 和弦。",style=MaterialTheme.typography.bodyMedium)
                    if(hint==Hint.FULL) Text("C 开放指法实际发声：X · C · E · G · C · E（6→1 弦）；同一组成音可出现在不同八度。",style=MaterialTheme.typography.bodySmall)
                    if(page==0 && scale.key==Tonality.A_MINOR) Text("A 自然小调的主音是 A，C 是 3 级／小三度；C 和弦的根音仍是 C。",style=MaterialTheme.typography.bodySmall)
                    Text("调性主音与和弦根音分别看待；其他和弦的根、三、五不等于 C 大调 1、3、5。",style=MaterialTheme.typography.bodySmall)
                }
                Text("标准调弦 E A D G B E（6→1 弦） · 离线使用 · 3.0.0-local01",style=MaterialTheme.typography.labelSmall,color=Green)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
