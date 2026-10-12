package com.a3322505a.guitarlearning.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

internal val ScaleSaver = listSaver<ScaleOptions, Any>(
    save={ listOf(it.pattern,it.key.ordinal,it.exercise.ordinal,it.firstString,it.lastString,it.octave,it.eighth,it.startDegree,it.reverse) },
    // Removed controls use fixed defaults, so old hidden limits cannot restrict practice.
    restore={ ScaleOptions(pattern=it[0] as Int,key=Tonality.entries[it[1] as Int],exercise=ScaleExercise.entries[it[2] as Int],octave=it[5] as Boolean,eighth=it[6] as Boolean,startDegree=it[7] as Int) })

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
                        Surface(onClick={onSelect(i)},modifier=Modifier.weight(1f).heightIn(min=48.dp),
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
private fun Label(title: String, content: @Composable ColumnScope.()->Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeApp(player: Player) {
    val context=LocalContext.current
    val preferences=remember { context.getSharedPreferences("practice-settings",0) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var scale by rememberSaveable(stateSaver=ScaleSaver) { mutableStateOf(ScaleOptions()) }
    var firstName by rememberSaveable { mutableStateOf("C") }
    var phraseId by rememberSaveable { mutableStateOf("S01") }
    var chordPhrases by rememberSaveable { mutableStateOf(false) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var repeats by rememberSaveable { mutableIntStateOf(4) }
    var hintIndex by rememberSaveable { mutableIntStateOf(preferences.getInt("hint",0).coerceIn(0,2)) }
    var guitar by rememberSaveable { mutableStateOf(preferences.getBoolean("guitar",true)) }
    var click by rememberSaveable { mutableStateOf(preferences.getBoolean("click",true)) }
    var viewPreference by rememberSaveable { mutableIntStateOf(0) }
    val activity=checkNotNull(LocalActivity.current)
    // 0: closed, 1: practice content, 2: tempo/playback. Only one panel can be open.
    var settingsPanel by rememberSaveable { mutableIntStateOf(0) }
    var moreExercisesOpen by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    val hint=Hint.entries[hintIndex]
    val first=Chords.named(firstName)
    val phrase=Phrases.named(phraseId)
    player.guitar=guitar; player.click=click
    val scaleResult=remember(scale) { Routes.scale(scale) }
    val checkSequence=remember(first) { chordSequence(first,first,ChordExercise.CHECK,4) }
    val sequence=if(page==0) scaleResult.sequence else if(chordPhrases) phrase.sequence else checkSequence
    val progress=player.progress
    val session=player.session
    val cursor=session?.cursor(progress.tick)
    val practiceSession=session?.takeUnless { it.preview }
    val showingSession=practiceSession!=null && progress.phase!=Phase.IDLE
    val displayedSequence=if(showingSession) practiceSession!!.sequence else sequence
    val beatsPerBar=displayedSequence?.beatsPerBar ?: 4
    val renderCursor=cursor?.takeIf { showingSession }
    val event=if(showingSession && cursor?.pre==false) displayedSequence?.eventAt(cursor.localTick) else null
    val phraseNow=if(showingSession && cursor?.pre==false) event else displayedSequence?.events?.firstOrNull()
    val nextEvent=phraseNow?.let { now -> displayedSequence?.events?.firstOrNull { it.tick > now.tick } ?: displayedSequence?.events?.firstOrNull() }
    val current=event?.positions ?: emptyList()
    val section=if(showingSession && cursor?.pre==false) displayedSequence!!.sectionAt(cursor.localTick) else -1
    val harmony=displayedSequence?.sections.orEmpty()
    val melody=page==1 && chordPhrases && harmony.isEmpty()
    val renderHint=if(melody && hint==Hint.ROOTS) Hint.FULL else hint
    val shownChord=if(page==1) harmony.getOrNull(maxOf(0,section))?.let { Chords.named(it.chord) } else first
    val nextChord=if(page==1 && chordPhrases && harmony.isNotEmpty()) harmony[(maxOf(0,section)+1)%harmony.size].chord else ""
    fun eventLabel(value: Event?)=value?.positions?.takeIf { it.isNotEmpty() }?.joinToString { "${it.string} 弦 ${it.fret} 品 ${Music.name(it.midi,true)}" } ?: "留拍"
    fun changed() { player.invalidate(); note="" }
    fun scaleChange(value: ScaleOptions) { if(value!=scale) { changed(); scale=value } }
    fun firstChange(value: Chord) { if(firstName!=value.name) { changed();firstName=value.name } }
    fun phraseChange(value: Phrase) { if(phraseId!=value.id) { changed();phraseId=value.id } }
    fun chordMode(phrases: Boolean) { if(chordPhrases!=phrases) { changed();chordPhrases=phrases } }
    fun tap(p: Position) {
        player.audition(listOf(p))
        note="${p.string} 弦 ${p.fret} 品 · ${Music.name(p.midi,true)}" + if(page==0) " · ${scale.key.degree(p.midi)} 级" else shownChord?.let { " · ${it.role(p)}" }.orEmpty()
    }
    fun setHint(index: Int) { hintIndex=index; note=""; preferences.edit().putInt("hint",index).apply() }
    val status=when {
        session?.preview==true && player.active -> if(session.sequence.events.single().positions.size>1) "整和弦试听" else "点音试听"
        player.active && cursor?.pre==true -> "预备拍 ${cursor.beat+1} / ${session!!.sequence.beatsPerBar}"
        progress.phase==Phase.PLAYING -> "第 ${cursor!!.loop+1}${if(session!!.repeats>0) "/${session.repeats}" else ""} 轮 · 第 ${cursor.bar+1} 小节"
        progress.phase==Phase.PAUSED -> "已暂停 · 从${if(cursor?.pre==true) "预备" else "第 ${(cursor?.bar ?: 0)+1}"}小节继续"
        progress.phase==Phase.PREPARING -> "准备声音…"
        progress.phase==Phase.ENDED -> progress.message
        progress.phase==Phase.ERROR -> progress.message
        else -> "准备好，就开始一轮"
    }
    BackHandler(enabled=settingsPanel==0 && (player.active || progress.phase==Phase.PAUSED)) { player.stop() }
    PracticeTheme {
      BoxWithConstraints(Modifier.fillMaxSize().displayCutoutPadding().practiceBackground()) {
        val shortWindow=maxHeight<520.dp
        val narrowWindow=maxWidth<700.dp
        val tab=viewPreference==1
        DisposableEffect(activity,tab) {
            activity.requestedOrientation=if(tab) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val bars=WindowCompat.getInsetsController(activity.window,activity.window.decorView)
            if(tab) bars.hide(WindowInsetsCompat.Type.statusBars())
            onDispose {
                activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if(tab) bars.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        Scaffold(containerColor=Color.Transparent,bottomBar={
            Surface(color=Color.Transparent,shadowElevation=10.dp,shape=RoundedCornerShape(topStart=26.dp,topEnd=26.dp),border=BorderStroke(1.dp,Color.White)) {
                Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFFF4FFFC),Color(0xFFE5F6F2)))).navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(!tab && (!shortWindow || narrowWindow)) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={settingsPanel=2},modifier=Modifier.testTag("tempo-button"),contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {
                            MusicMotif(Modifier.size(22.dp),"metronome");Spacer(Modifier.width(6.dp));Text("$bpm BPM",fontWeight=FontWeight.Bold)
                        }
                        Text("· $beatsPerBar/4",color=Muted,style=MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick={setHint(if(hint==Hint.HIDDEN) 0 else 2)},contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) { Text(if(hint==Hint.HIDDEN) "恢复提示" else "撤提示") }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(tab) {
                            TextButton(onClick={settingsPanel=2},modifier=Modifier.testTag("tempo-button"),contentPadding=PaddingValues(horizontal=4.dp)) { Text("$bpm BPM") }
                            TextButton(onClick={setHint(if(hint==Hint.HIDDEN) 0 else 2)},contentPadding=PaddingValues(horizontal=4.dp)) { Text(if(hint==Hint.HIDDEN) "恢复提示" else "撤提示") }
                        }
                        if(player.active || progress.phase==Phase.PAUSED) {
                            CandyAction(if(player.active) "暂停" else "继续",modifier=Modifier.weight(1f),onClick={if(player.active) player.pause() else player.resume()})
                            OutlinedButton(onClick={player.restart()},modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=Color.White),border=BorderStroke(1.5.dp,Mint)) { Text("重来") }
                        } else {
                            OutlinedButton(onClick={sequence?.let { note=""; player.start(it,bpm,false,1) }},enabled=sequence!=null,modifier=Modifier.weight(.85f).heightIn(min=52.dp),shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=Color.White),border=BorderStroke(1.5.dp,Mint),contentPadding=PaddingValues(8.dp)) {
                                TransportMotif("speaker",Modifier.size(18.dp),Green);Spacer(Modifier.width(6.dp));Text("听示范")
                            }
                            CandyAction("开始跟练",modifier=Modifier.weight(1.25f),enabled=sequence!=null,onClick={sequence?.let { note=""; player.start(it,bpm,true,repeats) }})
                        }
                        TextButton(onClick={player.stop();note=""},modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)) { Text("结束",color=Muted) }
                    }
                }
            }
        }) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
                val availableWidth=maxWidth
                val availableHeight=maxHeight
                val wide=maxWidth>=700.dp
                val compact=maxHeight<520.dp
                val fontScale=LocalDensity.current.fontScale
                val narrowHeader=availableWidth<350.dp || fontScale>1.2f
                // A short window scrolls the whole chord page, never the labels inside its diagram.
                val chordPageScroll=page==1 && !wide && maxHeight<600.dp*fontScale
                val chordScrollState=remember(page) { ScrollState(0) }
                if(tab) {
                    Column(Modifier.fillMaxSize().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                            listOf("指板","六线谱").forEachIndexed { index,title ->
                                CandyChip(title,index==1,Sky) { viewPreference=index }
                            }
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                            listOf("音阶","和弦").forEachIndexed { index,title ->
                                CandyChip(title,page==index,if(index==0) Mint else Pink) { if(page!=index) { player.stop();page=index;note="" } }
                            }
                            if(page==1) {
                                CandyChip("和弦学习",!chordPhrases,Pink) { chordMode(false) }
                                CandyChip("短句应用",chordPhrases,Sky) { chordMode(true) }
                            }
                            TextButton(onClick={settingsPanel=1},modifier=Modifier.testTag("config-summary")) {
                                Text(if(page==0) "${Patterns.all[scale.pattern].solfege} · ${scale.exercise.title} · 练习 ›" else if(!chordPhrases) "${shownChord?.name} · 逐弦检查" else "${shownChord?.name ?: phrase.title} · 练习 ›")
                            }
                            if(page==1 && chordPhrases && (!melody || hint!=Hint.HIDDEN)) Text("下一：${if(melody) eventLabel(nextEvent) else nextChord}",style=MaterialTheme.typography.labelMedium,color=BlueInk)
                            Text(status,style=MaterialTheme.typography.labelMedium,color=if(progress.phase==Phase.ERROR) RoseInk else Green)
                            }
                        }
                        if(displayedSequence!=null) Tablature(displayedSequence,renderHint,if(page==0) scale.key else null,renderCursor,player.active || progress.phase==Phase.PAUSED,
                            Modifier.weight(1f).background(Color.White.copy(alpha=.94f),RoundedCornerShape(20.dp)).padding(horizontal=8.dp,vertical=4.dp))
                        else Text(scaleResult.reason,color=RoseInk)
                    }
                } else {
                val content: @Composable () -> Unit = {
                    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(!compact || !wide) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(7.dp).background(PurpleInk,RoundedCornerShape(4.dp)))
                                if(page==1 && !chordPhrases) {
                                    TextButton(onClick={firstChange(Chords.all[Chords.all.indexOf(first)-1])},enabled=first!=Chords.all.first(),modifier=Modifier.width(48.dp).testTag("previous-chord").semantics { contentDescription="上一和弦" },contentPadding=PaddingValues(0.dp)) { Text("‹",fontSize=24.sp) }
                                    TextButton(onClick={firstChange(Chords.all[Chords.all.indexOf(first)+1])},enabled=first!=Chords.all.last(),modifier=Modifier.width(48.dp).testTag("next-chord").semantics { contentDescription="下一和弦" },contentPadding=PaddingValues(0.dp)) { Text("›",fontSize=24.sp) }
                                } else Text("练习视图",style=MaterialTheme.typography.labelLarge,color=PurpleInk)
                            }
                            Spacer(Modifier.weight(1f))
                            listOf("指板","六线谱").forEachIndexed { index,title ->
                                CandyChip(title,tab==(index==1),Sky,Modifier.padding(start=6.dp)) { viewPreference=index }
                            }
                        }
                        val cardContent: @Composable (Chord,Boolean)->Unit = { cardChord,selected ->
                        val shownChord=cardChord
                        val chordTones=cardChord.positions.distinctBy { it.midi%12 }.sortedBy { (it.midi-cardChord.root)%12 }
                        CandyCard(Modifier.fillMaxSize().then(if(selected) Modifier.testTag("practice-card") else Modifier.clearAndSetSemantics {}),if(page==1) lerp(Pink,Color.White,.45f) else lerp(Sky,Color.White,.45f)) {
                            val target=if(page==1) "${shownChord.name} · ${if(!chordPhrases) "逐弦检查" else "下一：$nextChord"}"
                                else if(hint==Hint.FULL && event!=null) { if(event.positions.isEmpty()) "现在：留拍" else "现在：${event.positions.joinToString { Music.name(it.midi,true) }}" }
                                else "${scale.key.title} · ${Patterns.all[scale.pattern].solfege} 指型"
                            Row(Modifier.fillMaxWidth().background(candyBrush(if(page==1) Pink else Mint),RoundedCornerShape(20.dp)).padding(horizontal=12.dp,vertical=if(compact) 4.dp else 12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val headline=buildAnnotatedString {
                                        if(page==1 && !melody) {
                                            withStyle(SpanStyle(color=RoseInk,fontWeight=FontWeight.ExtraBold,fontSize=if(compact || shownChord.name.length>5) 22.sp else 30.sp)) { append(shownChord.name) }
                                            if(!chordPhrases) withStyle(SpanStyle(color=Ink,fontSize=14.sp)) { append(" · 逐弦检查") }
                                        } else append(if(melody) phrase.title else target)
                                    }
                                    Text(headline,style=if(compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Ink)
                                    if(page==1 && chordPhrases && (!melody || hint!=Hint.HIDDEN)) Text(if(melody) "现在：${eventLabel(phraseNow)}\n下一：${eventLabel(nextEvent)}" else "下一：$nextChord",style=MaterialTheme.typography.labelLarge,color=BlueInk)
                                    if(page==1 && !chordPhrases && hint!=Hint.HIDDEN) Text(
                                        chordTones.filter { hint==Hint.FULL || it.midi%12==shownChord.root }.joinToString(" · ") { "${shownChord.role(it)} ${Music.name(it.midi)}" },
                                        style=MaterialTheme.typography.bodySmall,color=BlueInk)
                                    if(page==0 && hint==Hint.FULL && nextEvent!=null) Text("下一："+if(nextEvent.positions.isEmpty()) "留拍" else nextEvent.positions.joinToString { Music.name(it.midi,true) },style=MaterialTheme.typography.labelMedium,color=BlueInk)
                                }
                                if(page==1 && !chordPhrases) TextButton(onClick={player.audition(shownChord.positions);note="${shownChord.name} · 整和弦试听"},enabled=selected && !player.active && progress.phase!=Phase.PAUSED,contentPadding=PaddingValues(horizontal=6.dp)) { Text("听和弦",style=MaterialTheme.typography.labelLarge) }
                                else if(!compact && availableWidth>370.dp && !player.active) MusicMotif(Modifier.size(30.dp),"notes",if(page==1) RoseInk else Green)
                                if(hint==Hint.HIDDEN) Text("已撤提示",color=Muted,style=MaterialTheme.typography.labelSmall)
                            }
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("diagram-viewport").background(Color.White.copy(alpha=.94f),RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center) {
                                    if(page==0) {
                                        val h=maxOf(176.dp*fontScale,maxHeight).coerceAtMost(300.dp*fontScale)
                                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),contentAlignment=Alignment.Center) {
                                            val positions=if(scale.exercise==ScaleExercise.CONNECTION && scale.pattern<4) (Patterns.all[scale.pattern].positions+Patterns.all[scale.pattern+1].positions).distinct() else Patterns.all[scale.pattern].positions
                                            Fretboard(positions,scale.key,hint,false,current,!player.active,h,::tap)
                                        }
                                    } else if(melody) Fretboard(displayedSequence!!.route,null,renderHint,false,current,!player.active,maxHeight.coerceAtMost(300.dp*fontScale),::tap)
                                    else ChordDiagram(shownChord,hint,if(selected) current else emptyList(),selected && !player.active,maxHeight.coerceAtMost(390.dp*fontScale),::tap)
                                }
                                if(!compact) Text(if(hint==Hint.FULL) { if(melody) "上 1 弦 → 下 6 弦 · 绿色音位 · 粉色当前音" else if(page==0) "金色：主音 · 绿色：音阶音 · 粉色：当前音" else "左 6 弦 → 右 1 弦 · 金色根音 · O 空弦 · X 不弹" } else "保留拍点与控制，可随时恢复提示。",style=MaterialTheme.typography.labelSmall,color=Muted)
                            if(!compact || !wide) Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.7f),RoundedCornerShape(14.dp)).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(selected) status else "准备好，就开始一轮",style=MaterialTheme.typography.labelLarge,color=if(progress.phase==Phase.ERROR) RoseInk else Green,modifier=Modifier.weight(1f))
                                repeat(beatsPerBar) { i ->
                                    Box(Modifier.padding(start=6.dp).size(if(i==cursor?.beat && showingSession) 11.dp else 8.dp).background(if(i==cursor?.beat && showingSession) Green else Lavender,RoundedCornerShape(8.dp)))
                                }
                            }
                            if(selected && showingSession && cursor?.pre==false && !compact) LinearProgressIndicator(progress={ (cursor.localTick / displayedSequence!!.length).toFloat() },modifier=Modifier.fillMaxWidth(),color=Green,trackColor=Mint)
                            if(selected && note.isNotEmpty() && hint==Hint.FULL && !compact) Text(note,color=Green,style=MaterialTheme.typography.bodySmall)
                        }
                        }
                        if(page==1 && !chordPhrases) ChordDeck(Chords.all.indexOf(first),Chords.all.size,{firstChange(Chords.all[it])},Modifier.weight(1f).fillMaxWidth()) { index,selected -> cardContent(Chords.all[index],selected) }
                        else Box(Modifier.weight(1f).fillMaxWidth()) { cardContent(shownChord ?: first,true) }
                        if(!compact && !wide && !player.active && page==0) Surface(color=Color.Transparent,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color.White)) {
                            Row(Modifier.background(candyBrush(Warm)).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                MusicMotif(Modifier.size(24.dp),"pick",Color(0xFFD3961B))
                                Text(sequence?.explanation ?: scaleResult.reason,style=MaterialTheme.typography.bodySmall,color=Color(0xFF775829),modifier=Modifier.weight(1f))
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxSize().testTag("chord-page").then(if(chordPageScroll) Modifier.verticalScroll(chordScrollState) else Modifier)) {
                Column(Modifier.fillMaxWidth().height(if(chordPageScroll) 600.dp*fontScale else availableHeight).padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Mint.copy(alpha=.8f),Sky.copy(alpha=.6f),Lavender.copy(alpha=.55f))),RoundedCornerShape(24.dp)).padding(horizontal=10.dp,vertical=if(compact) 0.dp else 7.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(!compact) { MusicMotif(Modifier.size(if(narrowHeader) 34.dp else 46.dp)); Spacer(Modifier.width(6.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text("吉他练习",style=if(compact || narrowHeader) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold,color=Ink)
                            if(!compact && !narrowHeader) Text("把音乐，弹进日常",style=MaterialTheme.typography.labelSmall,color=Green)
                        }
                        if(compact && wide) listOf("指板","六线谱").forEachIndexed { index,title ->
                            CandyChip(title,tab==(index==1),Sky,Modifier.padding(start=6.dp)) { viewPreference=index }
                        }
                        listOf("音阶","和弦").forEachIndexed { index,title ->
                            CandyChip(title,page==index,if(index==0) Mint else Pink,Modifier.padding(start=6.dp)) { if(page!=index) { player.stop();page=index;note="" } }
                        }
                    }
                    val summary: @Composable () -> Unit = {
                      Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        if(page==1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            CandyChip("和弦学习",!chordPhrases,Pink) { chordMode(false) }
                            CandyChip("短句应用",chordPhrases,Sky) { chordMode(true) }
                        }
                        Surface(onClick={settingsPanel=1},shape=RoundedCornerShape(22.dp),color=Color.Transparent,border=BorderStroke(1.5.dp,Color.White),shadowElevation=4.dp,modifier=Modifier.fillMaxWidth().testTag("config-summary")) {
                            Row(Modifier.background(candyBrush(Lavender)).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                                MusicMotif(Modifier.size(30.dp),"pick",PurpleInk);Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Text(if(page==0) "${scale.key.title} · ${Patterns.all[scale.pattern].solfege} / ${Patterns.all[scale.pattern].caged}" else if(!chordPhrases) firstName else phrase.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                    Text(if(page==0) "${scale.exercise.title} · 每拍${if(scale.eighth) 2 else 1}音" else if(!chordPhrases) "和弦学习 · 认音、听音、逐弦跟练" else "${phrase.category} · ${phrase.passage} · ${phrase.conditions}",style=MaterialTheme.typography.bodySmall,color=Muted)
                                }
                                Text("练习 ›",color=PurpleInk,fontWeight=FontWeight.Bold,modifier=Modifier.background(Color.White.copy(alpha=.65f),RoundedCornerShape(12.dp)).padding(horizontal=10.dp,vertical=8.dp))
                            }
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
                                        repeat(beatsPerBar) { i -> Box(Modifier.size(9.dp).background(if(i==cursor?.beat && showingSession) Green else Outline,RoundedCornerShape(9.dp))) }
                                    }
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        TextButton(onClick={settingsPanel=2},modifier=Modifier.testTag("tempo-button")) { Text("$bpm BPM") }
                                        TextButton(onClick={setHint(if(hint==Hint.HIDDEN) 0 else 2)}) { Text(if(hint==Hint.HIDDEN) "恢复提示" else "撤提示") }
                                    }
                                }
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
                        if(!compact && page==0) summary() else {
                            if(page==1) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                CandyChip("和弦学习",!chordPhrases,Pink) { chordMode(false) }
                                CandyChip("短句应用",chordPhrases,Sky) { chordMode(true) }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick={settingsPanel=1},modifier=Modifier.testTag("config-summary"),contentPadding=PaddingValues(0.dp)) { Text("练习 ›") }
                            }
                            else TextButton(onClick={settingsPanel=1},modifier=Modifier.testTag("config-summary"),contentPadding=PaddingValues(0.dp)) { Text("${scale.exercise.title} · 练习 ›") }
                            if(page==1 && chordPhrases) Text("${phrase.title} · ${phrase.passage}",style=MaterialTheme.typography.bodySmall,color=Muted)
                        }
                        Box(Modifier.weight(1f)) { content() }
                    }
                }
                }
                }
            }
        }
      }
        if(settingsPanel!=0) ModalBottomSheet(onDismissRequest={settingsPanel=0},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Paper) {
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=24.dp).testTag(if(settingsPanel==1) "practice-settings" else "tempo-settings"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(settingsPanel==1) "练习设置" else "速度与播放",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick={settingsPanel=0}) { Text("完成") }
                }
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(settingsPanel==2) {
                    Text("调整速度或循环后，下一次从头开始；声音开关立即生效。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    Label("速度") {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick={changed();bpm=(bpm-5).coerceAtLeast(30)},enabled=bpm>30,modifier=Modifier.testTag("tempo-slower")) { Text("−5") }
                            Text("$bpm BPM",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
                            OutlinedButton(onClick={changed();bpm=(bpm+5).coerceAtMost(180)},enabled=bpm<180,modifier=Modifier.testTag("tempo-faster")) { Text("+5") }
                        }
                        Slider(value=bpm.toFloat(),onValueChange={ value -> val next=value.toInt();if(next!=bpm) { changed();bpm=next } },valueRange=30f..180f,modifier=Modifier.testTag("tempo-slider"))
                    }
                    Label("循环与声音") {
                        Text("跟练次数")
                        val values=listOf(1,4,8,0)
                        Choices(listOf("1 次","4 次","8 次","持续循环"),values.indexOf(repeats),Mint) { if(repeats!=values[it]) { changed();repeats=values[it] } }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("吉他声",modifier=Modifier.weight(1f));Switch(guitar,onCheckedChange={guitar=it;player.guitar=it;preferences.edit().putBoolean("guitar",it).apply()},modifier=Modifier.testTag("guitar-switch"))
                        }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("节拍声",modifier=Modifier.weight(1f));Switch(click,onCheckedChange={click=it;player.click=it;preferences.edit().putBoolean("click",it).apply()},modifier=Modifier.testTag("click-switch"))
                        }
                        Text("$beatsPerBar/4 · 跟练预备 1 小节 · 示范只播 1 遍",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                } else {
                Text("选择要练的内容；更改后，下一次从头开始。",style=MaterialTheme.typography.bodySmall,color=Muted)
                if(page==0) {
                    Label("选音阶与指型") {
                        Choices(Tonality.entries.map { it.title },scale.key.ordinal,Mint) { scaleChange(scale.copy(key=Tonality.entries[it])) }
                        Choices(Patterns.all.map { "${it.solfege} · ${it.caged}" },scale.pattern,Sky) { scaleChange(scale.copy(pattern=it)) }
                        if(scale.pattern==2) Text("la 指型三弦 4 品 B：可伸指或轻微移位。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                    Label("练习方式") {
                        val common=listOf(ScaleExercise.ASCEND,ScaleExercise.THIRDS,ScaleExercise.CONNECTION)
                        Choices(common.map { it.title },common.indexOf(scale.exercise)) { val kind=common[it];scaleChange(scale.copy(exercise=kind,octave=if(kind==ScaleExercise.CONNECTION) false else scale.octave)) }
                        TextButton(onClick={moreExercisesOpen=!moreExercisesOpen},modifier=Modifier.testTag("more-exercises-toggle")) { Text(if(moreExercisesOpen) "收起更多练法 ▴" else "更多练法 ▾") }
                        if(moreExercisesOpen) {
                            val more=listOf(ScaleExercise.ROOTS,ScaleExercise.THREE,ScaleExercise.CHANGE_START)
                            Choices(more.map { it.title },more.indexOf(scale.exercise)) { scaleChange(scale.copy(exercise=more[it])) }
                        }
                        if(scale.exercise!=ScaleExercise.ROOTS && scale.exercise!=ScaleExercise.CONNECTION) Choices(listOf("一八度","整型音域"),if(scale.octave) 0 else 1,Mint) { scaleChange(scale.copy(octave=it==0)) }
                        Choices(listOf("每拍1音","每拍2音"),if(scale.eighth) 1 else 0) { scaleChange(scale.copy(eighth=it==1)) }
                        if(scale.exercise==ScaleExercise.CHANGE_START) {
                            Text("起点级数"); Choices((1..7).map(Int::toString),scale.startDegree-1) { scaleChange(scale.copy(startDegree=it+1)) }
                        }
                    }
                } else if(chordPhrases) {
                    Label("选择短句") {
                        Choices(Phrases.all.map { it.title },Phrases.all.indexOf(phrase),Pink) { phraseChange(Phrases.all[it]) }
                        Text("${phrase.passage} · ${phrase.conditions}",style=MaterialTheme.typography.bodySmall,color=Muted)
                        Text("${phrase.technique} · ${phrase.tempoLabel} ${phrase.sourceBpm} BPM · ${phrase.difficulty}",style=MaterialTheme.typography.bodySmall,color=Muted)
                        Text("${phrase.category} · ${phrase.source}",style=MaterialTheme.typography.bodySmall,color=Muted)
                        Text(phrase.sequence.explanation,style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                } else {
                    Label("选择和弦") {
                        Choices(Chords.all.map { it.name },Chords.all.indexOf(first),Pink) { firstChange(Chords.all[it]) }
                        Text("先看根音和组成音，点图中的音位试听，再听整和弦。",style=MaterialTheme.typography.bodySmall,color=Muted)
                        Text("听示范和跟练按有效弦逐根弹，每弦一拍；数字 1—4 表示手指，O 为空弦，X 弦不弹。",style=MaterialTheme.typography.bodySmall,color=Muted)
                        Text("no5 表示省略五音；斜杠后的音是最低音。所有指法均为标准调弦、无变调夹。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                }
                if(sequence==null) Text(scaleResult.reason,color=RoseInk,style=MaterialTheme.typography.bodySmall)
                }
                }
            }
        }
    }
}
