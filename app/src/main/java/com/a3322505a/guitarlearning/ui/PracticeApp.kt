package com.a3322505a.guitarlearning.ui

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.a3322505a.guitarlearning.immersiveStatusBar
import com.a3322505a.guitarlearning.practiceOrientation
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeApp(player: Player) {
    val context=LocalContext.current
    val preferences=remember { context.getSharedPreferences("practice-settings",0) }
    val phraseStore=remember { PhraseSettingsStore(preferences) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var scale by rememberSaveable(stateSaver=ScaleSaver) { mutableStateOf(ScaleOptions()) }
    var firstName by rememberSaveable { mutableStateOf("C") }
    var phraseId by rememberSaveable { mutableStateOf(phraseStore.selected().id) }
    var chordPhrases by rememberSaveable { mutableStateOf(false) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var repeats by rememberSaveable { mutableIntStateOf(4) }
    var firstBar by rememberSaveable { mutableIntStateOf(phraseStore.load(phraseStore.selected()).range.first) }
    var lastBar by rememberSaveable { mutableIntStateOf(phraseStore.load(phraseStore.selected()).range.last) }
    var hintIndex by rememberSaveable { mutableIntStateOf(preferences.getInt("hint",0).coerceIn(0,2)) }
    var guitar by rememberSaveable { mutableStateOf(preferences.getBoolean("guitar",true)) }
    var click by rememberSaveable { mutableStateOf(preferences.getBoolean("click",true)) }
    var viewPreference by rememberSaveable { mutableIntStateOf(0) }
    val activity=checkNotNull(LocalActivity.current)
    // 0: closed, 1: practice content, 2: tempo/playback, 3: diagram help.
    var settingsPanel by rememberSaveable { mutableIntStateOf(0) }
    var moreExercisesOpen by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    val hint=Hint.entries[hintIndex]
    val first=Chords.named(firstName)
    val phrase=Phrases.practiceNamed(phraseId)
    val wholeRange=BarRange(0,phrase.sequence.barCount-1)
    val range=BarRange(firstBar,lastBar).takeIf { it.valid(phrase.sequence) } ?: wholeRange
    LaunchedEffect(page,chordPhrases,phrase.id,bpm,repeats,firstBar,lastBar) {
        if(page==1 && chordPhrases) phraseStore.save(phrase,bpm,repeats,range)
    }
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
    val beatsPerBar=if(showingSession && cursor?.pre==true) session!!.preBeats
        else displayedSequence?.meter(if(showingSession) cursor?.bar ?: 0 else if(page==1 && chordPhrases) range.first else 0) ?: 4
    val renderCursor=cursor?.takeIf { showingSession }
    val event=if(showingSession && cursor?.pre==false) displayedSequence?.eventAt(cursor.localTick) else null
    val phraseNow=if(showingSession && cursor?.pre==false) event else displayedSequence?.events?.firstOrNull()
    val nextEvent=phraseNow?.let { now -> displayedSequence?.events?.firstOrNull { it.tick > now.tick } ?: displayedSequence?.events?.firstOrNull() }
    val moving=if(showingSession && cursor?.pre==false) displayedSequence?.movingAt(cursor.localTick) else null
    val current=event?.positions ?: emptyList()
    val section=if(showingSession && cursor?.pre==false) displayedSequence!!.sectionAt(cursor.localTick) else -1
    val harmony=displayedSequence?.sections.orEmpty()
    val melody=page==1 && chordPhrases && harmony.isEmpty()
    val renderHint=if(melody && hint==Hint.ROOTS) Hint.FULL else hint
    val shownChord=if(page==1) harmony.getOrNull(maxOf(0,section))?.let { Chords.named(it.chord) } else first
    val nextChord=if(page==1 && chordPhrases && harmony.isNotEmpty()) harmony[(maxOf(0,section)+1)%harmony.size].chord else ""
    fun changed() { player.invalidate(); note="" }
    fun scaleChange(value: ScaleOptions) { if(value!=scale) { changed(); scale=value } }
    fun firstChange(value: Chord) { if(firstName!=value.name) { changed();firstName=value.name } }
    fun loadPhrase(value: Phrase) { val saved=phraseStore.load(value,bpm,repeats);bpm=saved.bpm;repeats=saved.repeats;firstBar=saved.range.first;lastBar=saved.range.last }
    fun phraseChange(value: Phrase) { if(phraseId!=value.id) { changed();phraseId=value.id;loadPhrase(value) } }
    fun chordMode(phrases: Boolean) { if(chordPhrases!=phrases) { changed();chordPhrases=phrases;if(phrases) loadPhrase(phrase) } }
    fun tap(p: Position) {
        val capo=if(page==1) displayedSequence?.capoFret ?: 0 else 0
        player.audition(listOf(p),capoFret=capo)
        val name=if(page==1 && !chordPhrases) first.noteName(p,true) else Music.name(p.midi+capo,true)
        note="${p.string} 弦 ${p.fret} 品 · $name" + if(page==0) " · ${scale.key.degree(p.midi)} 级" else if(!chordPhrases || harmony.isNotEmpty()) shownChord?.let { " · ${it.role(p)}" }.orEmpty() else ""
    }
    fun setHint(index: Int) { hintIndex=index; note=""; preferences.edit().putInt("hint",index).apply() }
    val status=when {
        session?.preview==true && player.active -> if(session.sequence.events.single().positions.size>1) "整和弦试听" else "点音试听"
        player.active && cursor?.pre==true -> "预备拍 ${cursor.beat+1} / ${session!!.preBeats}"
        progress.phase==Phase.PLAYING -> "第 ${cursor!!.loop+1}${if(session!!.repeats>0) "/${session.repeats}" else ""} 轮 · 第 ${cursor.bar+session!!.sequence.originalBar+1} 小节"
        progress.phase==Phase.PAUSED -> "已暂停 · 从${if(cursor?.pre==true) "预备" else "第 ${(cursor?.bar ?: 0)+(session?.sequence?.originalBar ?: 0)+1}"}小节继续"
        progress.phase==Phase.PREPARING -> "准备声音…"
        progress.phase==Phase.ENDED -> progress.message
        progress.phase==Phase.ERROR -> progress.message
        else -> "准备好，就开始一轮"
    }
    BackHandler(enabled=settingsPanel==0 && (player.active || progress.phase==Phase.PAUSED)) { player.stop() }
    val windowConfiguration=LocalConfiguration.current
    val tabletControls=minOf(windowConfiguration.screenWidthDp,windowConfiguration.screenHeightDp)>=600
    PracticeTheme(largeControls=tabletControls) {
      BoxWithConstraints(Modifier.fillMaxSize().displayCutoutPadding().windowInsetsPadding(WindowInsets.captionBar)
          .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)).practiceBackground()) {
        val tab=viewPreference==1
        val configuration=LocalConfiguration.current
        LaunchedEffect(activity,tab,configuration) {
            val orientation=practiceOrientation(activity,tab)
            if(activity.requestedOrientation!=orientation) activity.requestedOrientation=orientation
        }
        Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
            PracticeTransport(tab,bpm,beatsPerBar,hint,player.active,progress.phase,sequence!=null,
                {settingsPanel=2},{setHint(if(hint==Hint.HIDDEN) 0 else 2)},
                {if(player.active) player.pause() else player.resume()},{player.restart()},
                {sequence?.let { note="";player.start(it,bpm,false,1,if(page==1 && chordPhrases) range else BarRange(0,it.barCount-1)) }},
                {sequence?.let { note="";player.start(it,bpm,true,repeats,if(page==1 && chordPhrases) range else BarRange(0,it.barCount-1)) }},
                {player.stop();note=""})
        }) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
              val navigationCompact=(tab || maxHeight<520.dp) && maxWidth>=600.dp*LocalDensity.current.fontScale
              Column(Modifier.fillMaxSize()) {
                PracticeNavigation(page,chordPhrases,navigationCompact,tabletControls,
                    { index -> if(page!=index) { player.stop();page=index;note="" } },::chordMode,{settingsPanel=1},
                    { if(!tab) ChordSteps(first,::firstChange) },
                    { if(tab || tabletControls) ViewSwitch(tab) { viewPreference=it } })
              PracticeContent(PracticeContentState(page,chordPhrases,tab,tabletControls,scale,first,phrase,melody,
                  sequence,displayedSequence,scaleResult,hint,renderHint,player.active,progress.phase,renderCursor,cursor,
                  showingSession,event,phraseNow,nextEvent,moving,current,shownChord,nextChord,status,note,beatsPerBar),
                  Modifier.weight(1f),::firstChange,{viewPreference=it},::tap,
                  { chord -> player.audition(chord.positions,capoFret=chord.capoFret);note="${chord.name} · 整和弦试听" },{settingsPanel=3})
              }
            }
        }
      }
        PracticeSettings(PracticeSettingsState(settingsPanel,page,chordPhrases,tabletControls,scale,first,phrase,
            Phrases.family(phrase),Phrases.versions(phrase),Phrases.catalogue.groupBy { it.use },Chords.all.groupBy { it.group },
            phrase.relatedId?.let { Phrases.named(it).title },bpm,repeats,firstBar,lastBar,range,wholeRange,guitar,click,
            moreExercisesOpen,beatsPerBar,sequence,displayedSequence,scaleResult),
            {settingsPanel=0},{value -> changed();bpm=value},{value -> changed();repeats=value},
            {guitar=it;player.guitar=it;preferences.edit().putBoolean("guitar",it).apply()},
            {click=it;player.click=it;preferences.edit().putBoolean("click",it).apply()},
            {moreExercisesOpen=!moreExercisesOpen},::scaleChange,::phraseChange,::firstChange,
            {value -> changed();firstBar=value.first;lastBar=value.last})
    }
}
