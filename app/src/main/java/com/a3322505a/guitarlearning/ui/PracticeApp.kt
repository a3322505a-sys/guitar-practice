@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.practiceOrientation
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.alphatab.*
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import java.io.File

internal val ScaleSaver = listSaver<ScaleOptions, Any>(
    save={ listOf(it.pattern,it.key.ordinal,it.exercise.ordinal,it.firstString,it.lastString,it.octave,it.notesPerBeat,it.startDegree,it.reverse) },
    // Removed controls use fixed defaults, so old hidden limits cannot restrict practice.
    restore={ values -> ScaleOptions(
        pattern=(values.getOrNull(0) as? Int)?.takeIf { it in Patterns.all.indices } ?: 0,
        key=(values.getOrNull(1) as? Int)?.let { Tonality.entries.getOrNull(it) } ?: Tonality.C_MAJOR,
        exercise=(values.getOrNull(2) as? Int)?.let { ScaleExercise.entries.getOrNull(it) } ?: ScaleExercise.ASCEND,
        octave=values.getOrNull(5) as? Boolean ?: true,
        notesPerBeat=when(val rhythm=values.getOrNull(6)) { is Boolean -> if(rhythm) 2 else 1; is Int -> rhythm.takeIf { it in 1..3 } ?: 1; else -> 1 },
        startDegree=(values.getOrNull(7) as? Int)?.takeIf { it in 1..7 } ?: 1) })

/** Visible playing information that cannot be conveyed by the colored pitch markers. */
internal fun phraseCue(event: Event?, sequence: Sequence): String {
    if(event==null || event.rest) return "休止 · 留拍"
    return buildList {
        if(sequence.capoFret>0) add("夹${sequence.capoFret}品")
        if(event.positions.isEmpty()) add("死音")
        event.expressionLabel().takeIf { it.isNotBlank() }?.let(::add)
        sequence.links.firstOrNull { it.sourceTick==event.tick }?.let {
            add(when(it.kind) { Technique.SLIDE -> "滑音"; Technique.HAMMER -> "击弦"; Technique.PULL -> "勾弦" })
        }
    }.joinToString(" · ").ifBlank { "按音位弹奏" }
}

private enum class PracticeOverlay { NONE, PRACTICE, MANAGEMENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeApp(player: PracticePlayback) {
    val context=LocalContext.current
    val preferences=remember { context.getSharedPreferences("practice-settings",0) }
    val repository=remember { SongRepository(File(context.filesDir,"songs"),
        SongReader(SongSchema { name -> context.assets.open("song-contract/"+name).use { it.readBytes() } }),
        context.assets.list("song-packages").orEmpty().filter { it.endsWith(".gpractice") }.associateWith { name ->
            { context.assets.open("song-packages/"+name) } }) }
    var librarySnapshot by remember { mutableStateOf(repository.snapshot) }
    var libraryReady by remember { mutableStateOf(false) }
    var preparedEpoch by remember { mutableIntStateOf(0) }
    val phraseStore=remember { PhraseSettingsStore(preferences,repository::named) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var scale by rememberSaveable(stateSaver=ScaleSaver) { mutableStateOf(ScaleOptions()) }
    var firstName by rememberSaveable { mutableStateOf("C") }
    var phraseId by rememberSaveable { mutableStateOf(phraseStore.selected().id) }
    var chordPhrases by rememberSaveable { mutableStateOf(false) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var repeats by rememberSaveable { mutableIntStateOf(4) }
    var sourceTempo by rememberSaveable { mutableStateOf(true) }
    var tempoMultiplier by rememberSaveable { mutableIntStateOf(100) }
    var selectedTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var soloTrack by rememberSaveable { mutableStateOf(false) }
    var firstBar by rememberSaveable { mutableIntStateOf(phraseStore.load(phraseStore.selected()).range.first) }
    var lastBar by rememberSaveable { mutableIntStateOf(phraseStore.load(phraseStore.selected()).range.last) }
    var hintIndex by rememberSaveable { mutableIntStateOf(preferences.getInt("hint",0).coerceIn(0,2)) }
    var guitar by rememberSaveable { mutableStateOf(preferences.getBoolean("guitar",true)) }
    var click by rememberSaveable { mutableStateOf(preferences.getBoolean("click",true)) }
    var viewPreference by rememberSaveable { mutableIntStateOf(0) }
    val activity=checkNotNull(LocalActivity.current)
    var overlay by rememberSaveable { mutableStateOf(PracticeOverlay.NONE) }
    var settingsTab by rememberSaveable { mutableIntStateOf(0) }
    var moreExercisesOpen by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    val hint=Hint.entries[hintIndex]
    val chords=librarySnapshot.chords
    val first=chords.firstOrNull { it.id==firstName } ?: chords.first()
    val phrase=remember(phraseId,librarySnapshot,preparedEpoch) { repository.named(phraseId) }
    val wholeRange=BarRange(0,phrase.sequence.barCount-1)
    val range=BarRange(firstBar,lastBar).takeIf { it.valid(phrase.sequence) } ?: wholeRange
    LaunchedEffect(libraryReady,page,chordPhrases,phrase.id,bpm,repeats,firstBar,lastBar) {
        if(libraryReady && !phrase.unprepared && page==1 && chordPhrases) phraseStore.save(phrase,bpm,repeats,range)
    }
    player.guitar=guitar; player.click=click
    val scaleResult=remember(scale) { Routes.scale(scale) }
    val checkSequence=remember(first) { chordSequence(first,first,ChordExercise.CHECK,4) }
    val phraseSequence=remember(phrase.sequence,selectedTrackId,soloTrack) {
        val original=phrase.sequence
        val plan=original.runtimePlan
        if(plan==null) original else {
            val main=selectedTrackId?.takeIf { it in plan.practice.mixTrackIds } ?: plan.practice.mainTrackId
            val selected=plan.copy(practice=plan.practice.copy(mainTrackId=main,mixTrackIds=if(soloTrack) listOf(main) else plan.practice.mixTrackIds))
            original.copy(runtimePlan=selected,capoFret=selected.mainTrack.capo,openStringMidi=selected.mainTrack.openStringMidi,
                sections=selected.harmonies.map { Section((it.start.value()*QUARTER).toInt(),it.symbol.name,it.voicing?.id) })
        }
    }
    val sequence=if(page==0) scaleResult.sequence else if(chordPhrases) {
        if(phrase.unprepared) player.session?.sequence else phraseSequence
    } else checkSequence
    val nativePlayer=player as? AlphaPlayback
    LaunchedEffect(sequence) { nativePlayer?.prepareSequence(sequence) }
    val progress=player.progress
    val session=player.session
    val cursor=player.cursor
    val practiceSession=session?.takeUnless { it.preview }
    val showingSession=practiceSession!=null && progress.phase!=Phase.IDLE
    val displayedSequence=if(showingSession) practiceSession!!.sequence else sequence
    val beatsPerBar=if(showingSession && cursor?.pre==true) session!!.preBeats
        else displayedSequence?.meter(if(showingSession) cursor?.bar ?: 0 else if(page==1 && chordPhrases) range.first else 0) ?: 4
    val renderCursor=notationCursor(cursor?.takeIf { showingSession },progress.phase)
    val event=if(showingSession && cursor?.pre==false) displayedSequence?.eventAt(cursor.localTick) else null
    val phraseNow=if(showingSession && cursor?.pre==false) event else displayedSequence?.events?.firstOrNull()
    val nextEvent=phraseNow?.let { now -> displayedSequence?.events?.firstOrNull { it.tick > now.tick } ?: displayedSequence?.events?.firstOrNull() }
    val moving=if(showingSession && cursor?.pre==false) displayedSequence?.movingAt(cursor.localTick) else null
    val current=if(showingSession && cursor?.pre==false) cursor.positions else event?.positions ?: emptyList()
    val section=if(showingSession && cursor?.pre==false) displayedSequence!!.sectionAt(cursor.localTick) else -1
    val harmony=displayedSequence?.sections.orEmpty()
    val shownChord=if(page==1) harmony.getOrNull(maxOf(0,section))?.let { displayedSequence?.chordAt(it) } else first
    val melody=page==1 && chordPhrases && (harmony.isEmpty() || shownChord==null)
    val renderHint=if(melody && hint==Hint.ROOTS) Hint.FULL else hint
    val nextChord=if(page==1 && chordPhrases && harmony.isNotEmpty()) harmony[(maxOf(0,section)+1)%harmony.size].chord else ""
    fun changed() { player.invalidate(); note="" }
    fun scaleChange(value: ScaleOptions) { if(value!=scale) { changed(); scale=value } }
    fun firstChange(value: Chord) { if(firstName!=value.id) { changed();firstName=value.id } }
    fun loadPhrase(value: Phrase) {
        if(value.unprepared) { note="正在准备曲目…";return }
        val saved=phraseStore.load(value,bpm,repeats);bpm=saved.bpm;repeats=saved.repeats;firstBar=saved.range.first;lastBar=saved.range.last
        val tempo=phraseStore.tempo(value);sourceTempo=tempo.first;tempoMultiplier=tempo.second
        val prefix=value.assetKey?.let { "asset."+it.encoded+"." }
        selectedTrackId=prefix?.let { preferences.getString(it+"mainTrack",null) }
        soloTrack=prefix?.let { preferences.getBoolean(it+"solo",false) } ?: false
        if(saved.rangeReset) note="曲包修订改变了范围，已恢复整句；速度和次数保留。"
    }
    LaunchedEffect(repository) {
        librarySnapshot=withContext(Dispatchers.IO) { repository.reload() }
        val restored=phraseStore.selected()
        phraseId=restored.id
        loadPhrase(restored)
        libraryReady=true
    }
    val selectedRevision=phrase.assetKey?.let { key -> librarySnapshot.packages.firstOrNull { it.identity.packageId==key.packageId }?.identity }
    LaunchedEffect(libraryReady,phraseId,selectedRevision) {
        if(libraryReady && repository.named(phraseId).unprepared) {
            val job=currentCoroutineContext()
            try {
                val loaded=withContext(Dispatchers.IO) { repository.resolve(phraseId) { job.ensureActive() } }
                preparedEpoch++;loadPhrase(loaded);note=""
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { note="曲目准备失败："+e.message }
        }
    }
    fun phraseChange(value: Phrase) { phraseStore.select(value);if(phraseId!=value.id) { changed();phraseId=value.id;loadPhrase(value) } }
    fun tap(p: Position) {
        val capo=if(page==1) displayedSequence?.capoFret ?: 0 else 0
        player.audition(listOf(p),capoFret=capo,openStringMidi=displayedSequence?.runtimePlan?.mainTrack?.openStringMidi ?: Music.openMidi.toList())
        val name=if(page==1 && !chordPhrases) first.noteName(p,true) else Music.name(displayedSequence?.midi(p) ?: (p.midi+capo),true)
        note="${p.string} 弦 ${p.fret} 品 · $name" + if(page==0) " · ${scale.key.degree(p.midi)} 级" else if(!chordPhrases || harmony.isNotEmpty()) shownChord?.let { " · ${it.role(p)}" }.orEmpty() else ""
    }
    fun setHint(index: Int) { hintIndex=index; note=""; preferences.edit().putInt("hint",index).apply() }
    val status=when {
        session?.preview==true && player.active -> if(session.sequence.events.single().positions.size>1) "整和弦试听" else "点音试听"
        player.active && cursor?.leadIn==true -> "拍位对齐 · 随后进入谱面"
        player.active && cursor?.pre==true -> if(nativePlayer!=null) "预备拍…" else "预备拍 ${cursor.beat+1} / ${session!!.preBeats}"
        progress.phase==Phase.PLAYING -> "第 ${cursor!!.loop+1}${if(session!!.repeats>0) "/${session.repeats}" else ""} 轮 · 第 ${session!!.sequence.barLabel(cursor.bar)} 小节"+
            (session.plan.revisionPin?.split('/')?.getOrNull(1)?.let { " · 修订"+it } ?: "")
        progress.phase==Phase.PAUSED -> "已暂停 · 从${if(cursor?.pre==true) "预备" else "第 ${session?.sequence?.barLabel(cursor?.bar ?: 0) ?: "1"}"}小节继续"
        progress.phase==Phase.PREPARING -> "准备声音…"
        progress.phase==Phase.ENDED -> progress.message
        progress.phase==Phase.ERROR -> progress.message
        else -> progress.message.takeIf { it.startsWith("设置已改变") } ?: "准备好，就开始一轮"
    }
    BackHandler(enabled=overlay==PracticeOverlay.NONE && (player.active || progress.phase==Phase.PAUSED)) { player.stop() }
    BackHandler(enabled=overlay==PracticeOverlay.PRACTICE) { overlay=PracticeOverlay.NONE }
    val windowConfiguration=LocalConfiguration.current
    val tabletControls=minOf(windowConfiguration.screenWidthDp,windowConfiguration.screenHeightDp)>=600
    PracticeTheme(largeControls=tabletControls) {
      // Paint the entire window before insets keep controls clear of cutouts and system UI.
      BoxWithConstraints(Modifier.fillMaxSize().practiceBackground().displayCutoutPadding().windowInsetsPadding(WindowInsets.captionBar)
          .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))) {
        val tab=viewPreference==1
        val configuration=LocalConfiguration.current
        LaunchedEffect(activity,tab,configuration) {
            val orientation=practiceOrientation(activity,tab)
            if(activity.requestedOrientation!=orientation) activity.requestedOrientation=orientation
        }
        val windowHeight=maxHeight
        val configuring=overlay==PracticeOverlay.PRACTICE
        val sidePanel=maxWidth>=600.dp || maxWidth>maxHeight
        val panelWidth=if(configuring && sidePanel) minOf(420.dp,maxWidth*.44f) else 0.dp
        fun setBpm(value: Int) { if(value!=bpm) { changed();bpm=value } }
        fun setMultiplier(value: Int) { if(value!=tempoMultiplier) { changed();tempoMultiplier=value;phraseStore.saveTempo(phrase,sourceTempo,value) } }
        fun category(value: Int) {
            val nextPage=if(value==0) 0 else 1
            val nextPhrases=value==2
            if(page!=nextPage || (nextPage==1 && chordPhrases!=nextPhrases)) {
                changed();page=nextPage
                if(nextPage==1) { chordPhrases=nextPhrases;if(nextPhrases) loadPhrase(phrase) }
            }
        }
        val summary=if(page==0) "${scale.key.title} · ${Patterns.all[scale.pattern].solfege}型 · ${scale.exercise.title}"
            else if(chordPhrases) phrase.title else "${first.name} · 逐弦"
        Scaffold(modifier=Modifier.fillMaxSize().padding(end=panelWidth),containerColor=Color.Transparent,
            contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
            PracticeTransport(bpm,sequence?.runtimePlan!=null && sourceTempo,tempoMultiplier,
                displayedSequence?.runtimePlan?.visits?.getOrNull(cursor?.bar ?: range.first)?.measure?.meter?.let { it.numerator.toString()+"/"+it.denominator } ?: "$beatsPerBar/4",
                hint,transportMode(progress.phase,session!=null,session?.preview==true),sequence!=null && (nativePlayer==null || nativePlayer.ready),::setBpm,::setMultiplier,
                {setHint(if(hint==Hint.HIDDEN) 0 else 2)},
                {if(player.active) player.pause() else player.resume()},{player.restart()},
                {sequence?.let { note="";player.start(it,bpm,false,1,if(page==1 && chordPhrases) range else BarRange(0,it.barCount-1),
                    it.runtimePlan!=null && sourceTempo,if(it.runtimePlan!=null && sourceTempo) tempoMultiplier/100.0 else 1.0) }},
                {sequence?.let { note="";player.start(it,bpm,true,repeats,if(page==1 && chordPhrases) range else BarRange(0,it.barCount-1),
                    it.runtimePlan!=null && sourceTempo,if(it.runtimePlan!=null && sourceTempo) tempoMultiplier/100.0 else 1.0) }},
                {player.stop();note=""},availableHeight=windowHeight,showHintAction=nativePlayer==null || !tab)
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=12.dp)) {
                val compactHeader=windowHeight<520.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)
                val views: @Composable ()->Unit = {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        ViewSwitch(tab) { overlay=PracticeOverlay.NONE;viewPreference=it }
                        if(nativePlayer!=null) PracticeTextButton(onClick={player.pause();context.startActivity(Intent(context,GpPracticeActivity::class.java))}) { Text("GP 曲库") }
                    }
                }
                PracticeNavigation(summary,{settingsTab=0;overlay=PracticeOverlay.PRACTICE},{overlay=PracticeOverlay.MANAGEMENT},compactHeader,views)
                if(!compactHeader) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { views() }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Reserve a real diagram above the half-height sheet, not just a card title.
                    val contentHeight=if(configuring && !sidePanel) minOf(maxHeight,(windowHeight*.48f-112.dp).coerceAtLeast(120.dp)) else maxHeight
                    val nativeContentModifier=Modifier.fillMaxWidth().height(contentHeight)
                    if(nativePlayer!=null) NativeScore(nativePlayer,nativeContentModifier.then(if(tab) Modifier.padding(top=24.dp) else Modifier),tab && displayedSequence!=null)
                    if(nativePlayer!=null && tab) {
                        Text(status,style=MaterialTheme.typography.labelSmall,color=if(progress.phase==Phase.ERROR) RoseInk else Green,
                            maxLines=1,modifier=Modifier.align(Alignment.TopStart))
                        if(nativePlayer.loading || nativePlayer.error.isNotBlank()) Box(nativeContentModifier.background(Paper),contentAlignment=Alignment.Center) {
                            Text(nativePlayer.error.ifBlank { "正在准备谱面…" },color=RoseInk)
                        }
                    } else PracticeContent(PracticeContentState(page,chordPhrases,tab,tabletControls,scale,first,phrase,melody,
                        sequence,displayedSequence,scaleResult,hint,renderHint,player.active,progress.phase,renderCursor,cursor,
                        showingSession,event,phraseNow,nextEvent,moving,current,shownChord,nextChord,status,note,beatsPerBar,chords,configuring),
                        Modifier.fillMaxWidth().height(contentHeight),::firstChange,{viewPreference=it},::tap,
                        { chord -> player.audition(chord.positions,capoFret=chord.capoFret,openStringMidi=chord.openStringMidi);note="${chord.name} · 整和弦试听" })
                }
            }
        }
        if(configuring && sidePanel) Box(Modifier.fillMaxSize().padding(end=panelWidth)
            .clickable { overlay=PracticeOverlay.NONE })
        if(configuring) Box(if(sidePanel) Modifier.align(Alignment.CenterEnd).width(panelWidth).fillMaxHeight() else Modifier) {
            PracticeSettings(PracticeSettingsState(page,chordPhrases,tabletControls,scale,first,phrase,
                repository.family(phrase),repository.versions(phrase),librarySnapshot.catalogue.groupBy { it.use },chords.groupBy { it.group },
                phrase.relatedId?.let { repository.named(it).title },bpm,repeats,firstBar,lastBar,range,wholeRange,guitar,click,
                moreExercisesOpen,beatsPerBar,sequence,displayedSequence,scaleResult,sourceTempo,tempoMultiplier,selectedTrackId,soloTrack,if(page==0) scaleResult.reason else if(!libraryReady) "正在加载曲库…" else note),
                sidePanel,settingsTab,{settingsTab=it},{overlay=PracticeOverlay.NONE},::category,
                {value -> if(value!=repeats) { changed();repeats=value }},
                {guitar=it;player.guitar=it;preferences.edit().putBoolean("guitar",it).apply()},
                {click=it;player.click=it;preferences.edit().putBoolean("click",it).apply()},
                {moreExercisesOpen=!moreExercisesOpen},::scaleChange,::phraseChange,::firstChange,
                {value -> if(value!=range) { changed();firstBar=value.first;lastBar=value.last }},
                {value -> if(value!=sourceTempo) { changed();sourceTempo=value;phraseStore.saveTempo(phrase,value,tempoMultiplier) }},
                {value -> if(value!=selectedTrackId) { changed();selectedTrackId=value;phrase.assetKey?.let { preferences.edit().putString("asset."+it.encoded+".mainTrack",value).apply() } }},
                {value -> if(value!=soloTrack) { changed();soloTrack=value;phrase.assetKey?.let { preferences.edit().putBoolean("asset."+it.encoded+".solo",value).apply() } }})
        }
        if(overlay==PracticeOverlay.MANAGEMENT) SongLibrary(repository,{updated ->
            librarySnapshot=updated
            val restored=phraseStore.selected()
            val current=if(restored.legacyId!=null) restored else repository.named(phraseId)
            phraseId=current.id;loadPhrase(current)
        },{overlay=PracticeOverlay.NONE},displayedSequence)
      }
    }
}
