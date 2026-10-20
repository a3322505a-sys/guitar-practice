@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.ui

import android.content.pm.ActivityInfo
import androidx.compose.ui.draw.clipToBounds
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal val ScaleSaver = listSaver<ScaleOptions, Any>(
    save = { listOf(it.pattern, it.key.ordinal, it.exercise.ordinal, it.firstString, it.lastString, it.octave, it.notesPerBeat, it.startDegree, it.reverse, it.pentatonic) },
    // Removed controls use fixed defaults, so old hidden limits cannot restrict practice.
    restore = { values ->
        ScaleOptions(
            pattern = (values.getOrNull(0) as? Int)?.takeIf { it in Patterns.all.indices } ?: 0,
            key = (values.getOrNull(1) as? Int)?.let { Tonality.entries.getOrNull(it) } ?: Tonality.C_MAJOR,
            exercise = (values.getOrNull(2) as? Int)?.let { ScaleExercise.entries.getOrNull(it) } ?: ScaleExercise.ASCEND,
            octave = values.getOrNull(5) as? Boolean ?: true,
            notesPerBeat = when (val rhythm = values.getOrNull(6)) {
                is Boolean -> if (rhythm) 2 else 1
                is Int -> rhythm.takeIf { it in 1..3 } ?: 1
                else -> 1
            },
            startDegree = (values.getOrNull(7) as? Int)?.takeIf { it in 1..7 } ?: 1,
            pentatonic = values.getOrNull(9) as? Boolean ?: false,
        )
    },
)

/** Visible playing information that cannot be conveyed by the colored pitch markers. */
internal fun phraseCue(event: Event?, sequence: Sequence): String {
    if (event == null || event.rest) return "休止 · 留拍"
    return buildList {
        if (sequence.capoFret > 0) add("夹${sequence.capoFret}品")
        if (event.positions.isEmpty()) add("死音")
        event.expressionLabel().takeIf { it.isNotBlank() }?.let(::add)
        sequence.links.firstOrNull { it.sourceTick == event.tick }?.let {
            add(
                when (it.kind) {
                    Technique.SLIDE -> "滑音"
                    Technique.HAMMER -> "击弦"
                    Technique.PULL -> "勾弦"
                },
            )
        }
    }.joinToString(" · ").ifBlank { "按音位弹奏" }
}

enum class StageLayout { LINKED, TAB_ONLY, FRETBOARD_ONLY }

private sealed class PracticeOverlay(val id: String) {
    data object NONE : PracticeOverlay("none")
    data object CONTENT : PracticeOverlay("content")
    data object PRACTICE : PracticeOverlay("practice")
    data object MANAGEMENT : PracticeOverlay("management")
    companion object {
        val saver = Saver<PracticeOverlay, String>(save = { it.id }, restore = { id ->
            listOf(NONE, CONTENT, PRACTICE, MANAGEMENT).firstOrNull { it.id == id } ?: NONE
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeApp(player: AlphaPlayback) {
    PracticeWorkbench(player, player)
}

/** Debug binds its historical renderer explicitly; release only binds alphaTab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PracticeWorkbench(
    player: PracticePlayback,
    nativePlayer: AlphaPlayback?,
    legacyTab: (@Composable (PracticeContentState, Modifier, (Int) -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    DisposableEffect(themeManager) { onDispose { themeManager.close() } }
    val preferences = remember { context.getSharedPreferences("practice-settings", 0) }
    val repository = remember {
        SongRepository(
            File(context.filesDir, "songs-native-v31"),
            SongReader(SongSchema { name -> context.assets.open("song-contract/" + name).use { it.readBytes() } }),
            context.assets.list("song-packages").orEmpty().filter { it.endsWith(".gpractice") }.associateWith { name ->
                { context.assets.open("song-packages/" + name) }
            },
        )
    }
    var librarySnapshot by remember { mutableStateOf(repository.snapshot) }
    var libraryReady by remember { mutableStateOf(false) }
    val contentPresentation = remember(librarySnapshot) { ContentPresentation(librarySnapshot) }
    var activeSourceFilter by rememberSaveable { mutableStateOf(BASIC_CONTENT) }
    LaunchedEffect(libraryReady, contentPresentation) {
        activeSourceFilter = contentPresentation.restoredSource(activeSourceFilter, libraryReady)
    }
    var preparedEpoch by remember { mutableIntStateOf(0) }
    val phraseStore = remember { PhraseSettingsStore(preferences, repository::named) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var module by rememberSaveable { mutableStateOf(TeachingModule.FOUNDATIONS) }
    var userSelectedModule by rememberSaveable { mutableStateOf(false) }
    var scale by rememberSaveable(stateSaver = ScaleSaver) { mutableStateOf(ScaleOptions()) }
    var firstName by rememberSaveable { mutableStateOf("C") }
    var phraseId by rememberSaveable { mutableStateOf(phraseStore.selected().id) }
    var chordPhrases by rememberSaveable { mutableStateOf(true) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var repeats by rememberSaveable { mutableIntStateOf(4) }
    var sourceTempo by rememberSaveable { mutableStateOf(true) }
    var tempoMultiplier by rememberSaveable { mutableDoubleStateOf(100.0) }
    var selectedTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var soloTrack by rememberSaveable { mutableStateOf(false) }
    var firstBar by rememberSaveable { mutableIntStateOf(phraseStore.load(phraseStore.selected()).range.first) }
    var lastBar by rememberSaveable { mutableIntStateOf(phraseStore.load(phraseStore.selected()).range.last) }
    var hintIndex by rememberSaveable { mutableIntStateOf(preferences.getInt("hint", 0).coerceIn(0, Hint.entries.lastIndex)) }
    var referenceChordName by rememberSaveable { mutableStateOf(preferences.getString("reference-chord","auto") ?: "auto") }
    var guitar by rememberSaveable { mutableStateOf(preferences.getBoolean("guitar", true)) }
    var click by rememberSaveable { mutableStateOf(preferences.getBoolean("click", true)) }
    var stageLayout by rememberSaveable { mutableStateOf(if (nativePlayer != null) StageLayout.LINKED else StageLayout.FRETBOARD_ONLY) }
    val activity = checkNotNull(LocalActivity.current)
    var overlay by rememberSaveable(stateSaver = PracticeOverlay.saver) { mutableStateOf<PracticeOverlay>(PracticeOverlay.NONE) }
    var workbenchDestinationId by rememberSaveable { mutableStateOf("curriculum") }
    var focusedPracticeId by rememberSaveable { mutableStateOf(preferences.getString("focused-practice", null)) }
    var returnPhraseId by rememberSaveable { mutableStateOf<String?>(null) }
    var transitionTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnModule by rememberSaveable { mutableStateOf(TeachingModule.APPLICATION) }
    var leadFade by rememberSaveable { mutableStateOf(false) }
    var leadLevel by rememberSaveable { mutableStateOf(1.0) }
    var backingLevel by rememberSaveable { mutableStateOf(1.0) }
    var tempoOpen by remember { mutableStateOf(false) }
    var finiteRepeats by rememberSaveable { mutableIntStateOf(4) }
    var chainFirst by rememberSaveable { mutableIntStateOf(-1) }
    var chainLast by rememberSaveable { mutableIntStateOf(-1) }
    var chainStep by rememberSaveable { mutableIntStateOf(0) }
    var chainRepeats by rememberSaveable { mutableIntStateOf(4) }
    val chainOriginal = if(chainFirst>=0) BarRange(chainFirst,chainLast) else null
    var moreExercisesOpen by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var mix by remember { mutableStateOf(PlaybackMix.load(preferences)) }
    var volumeOpen by remember { mutableStateOf(false) }
    player.setMix(mix)
    val hint = Hint.entries[hintIndex]
    val chords = librarySnapshot.chords
    val first = chords.firstOrNull { it.id == firstName } ?: ChordDictionary.voicings.firstOrNull { it.id == firstName }?.chord ?: chords.first()
    val phrase = remember(phraseId, librarySnapshot, preparedEpoch) { repository.named(phraseId) }
    val wholeRange = BarRange(0, phrase.sequence.barCount - 1)
    val range = BarRange(firstBar, lastBar).takeIf { it.valid(phrase.sequence) } ?: wholeRange
    LaunchedEffect(libraryReady, page, chordPhrases, phrase.id, bpm, repeats, firstBar, lastBar) {
        if (libraryReady && !phrase.unprepared && page == 1 && chordPhrases) phraseStore.save(phrase, bpm, repeats, range)
    }
    player.guitar = true
    player.click = true
    val scaleResult = remember(scale) { Routes.scale(scale) }
    val chordUnavailable = first.unavailableReason
    val checkSequence = remember(first) { if (chordUnavailable == null) chordSequence(first, first, ChordExercise.CHECK, 4) else null }
    val phraseSequence = remember(phrase.sequence, selectedTrackId, soloTrack) {
        val original = phrase.sequence
        val plan = original.runtimePlan
        if (plan == null) original else {
            val main = selectedTrackId?.takeIf { it in plan.practice.mixTrackIds } ?: plan.practice.mainTrackId
            val selected = plan.copy(practice = plan.practice.copy(mainTrackId = main, mixTrackIds = if (soloTrack) listOf(main) else plan.practice.mixTrackIds))
            original.copy(
                runtimePlan = selected,
                capoFret = selected.mainTrack.capo,
                openStringMidi = selected.mainTrack.openStringMidi,
                sections = selected.harmonies.map { Section((it.start.value() * QUARTER).toInt(), it.symbol.name, it.voicing?.id) },
            )
        }
    }
    val sequence = if (page == 0) scaleResult.sequence else if (chordPhrases) {
        if (!libraryReady || phrase.unprepared) player.session?.sequence else phraseSequence
    } else checkSequence
    LaunchedEffect(sequence) { nativePlayer?.prepareSequence(sequence) }
    nativePlayer?.leadFade = leadFade && sequence?.let(PracticeExtensions::canFade)==true
    nativePlayer?.leadLevel = leadLevel
    nativePlayer?.backingLevel = backingLevel
    val activeRange=if(page==1 && chordPhrases) range else sequence?.let { BarRange(0,it.barCount-1) } ?: BarRange(0,0)
    LaunchedEffect(sequence,bpm,repeats,activeRange,sourceTempo,tempoMultiplier) {
        nativePlayer?.configurePractice(sequence,bpm,repeats,activeRange,sequence?.runtimePlan!=null && sourceTempo,
            if(sequence?.runtimePlan!=null && sourceTempo) tempoMultiplier/100.0 else 1.0)
    }
    val progress = player.progress
    val session = player.session
    val cursor = player.cursor
    val practiceSession = session?.takeUnless { it.preview }
    val showingSession = practiceSession != null && progress.phase != Phase.IDLE
    val displayedSequence = if (showingSession) practiceSession!!.sequence else sequence
    val beatsPerBar = if (showingSession && cursor?.pre == true) session!!.preBeats
    else displayedSequence?.meter(if (showingSession) cursor?.bar ?: 0 else if (page == 1 && chordPhrases) range.first else 0) ?: 4
    val renderCursor = notationCursor(cursor?.takeIf { showingSession }, progress.phase)
    val event = if (showingSession && cursor?.pre == false) displayedSequence?.eventAt(cursor.localTick) else null
    val previewStart=displayedSequence?.let { it.barStart(activeRange.first.coerceIn(0,it.barCount-1)) } ?: 0
    val previewEnd=displayedSequence?.let { it.barEnd(activeRange.last.coerceIn(0,it.barCount-1)) } ?: 0
    val boardIndex=remember(displayedSequence) { displayedSequence?.runtimePlan?.let(::LessonBoardIndex) }
    val previewQuarter=previewStart.toDouble()/(displayedSequence?.ticksPerQuarter ?: QUARTER)
    val boardQuarter=if(showingSession && cursor?.pre==false) cursor.localTick/(displayedSequence?.ticksPerQuarter ?: QUARTER) else previewQuarter
    val previewPositions=boardIndex?.targetAt(previewQuarter)?.positions.orEmpty()
    val loopContinues=if(showingSession) practiceSession?.let { it.repeats==0 || (cursor?.loop ?: 0)+1<it.repeats }==true else repeats!=1
    val upcomingPositions=if(cursor?.pre==true || progress.phase in listOf(Phase.ENDED,Phase.ERROR)) emptyList() else
        boardIndex?.next(boardQuarter,previewQuarter,previewEnd.toDouble()/(displayedSequence?.ticksPerQuarter ?: QUARTER),loopContinues)?.first?.positions.orEmpty()
    val loopEvents=remember(displayedSequence,previewStart,previewEnd) { displayedSequence?.events.orEmpty().filter { it.tick<previewEnd && it.tick+it.duration>previewStart } }
    val phraseNow = if (showingSession && cursor?.pre == false) event else displayedSequence?.eventAt(previewStart.toDouble()) ?: loopEvents.firstOrNull()
    val nextEvent = phraseNow?.let { now -> loopEvents.firstOrNull { it.tick > now.tick } ?: loopEvents.firstOrNull() }
    val moving = if (showingSession && cursor?.pre == false) displayedSequence?.movingAt(cursor.localTick) else null
    val current = if (showingSession && cursor?.pre == false) {
        nativePlayer?.focus?.positions ?: nativePlayer?.projection?.active(nativePlayer.currentTick, nativePlayer.selectedTrack?.index?.toInt() ?: 0)
            ?: cursor.positions
    } else event?.positions ?: emptyList()
    val section = displayedSequence?.sectionAt(if(showingSession && cursor?.pre==false) cursor.localTick else previewStart.toDouble()) ?: -1
    val harmony = displayedSequence?.sections.orEmpty()
    val shownChord = if (page == 1) harmony.getOrNull(if(showingSession) section else displayedSequence?.sectionAt((activeRange.first.let { displayedSequence?.barStart(it) } ?: 0).toDouble()) ?: -1)?.let { displayedSequence?.chordAt(it) } else first
    val singleVoiceMelody=displayedSequence?.runtimePlan?.let { plan ->
        plan.events.filter { it.track.id==plan.mainTrack.id }.all { it.event.notes.size<=1 }
    } == true
    val melody = page == 1 && chordPhrases && (singleVoiceMelody || harmony.isEmpty() || shownChord == null)
    val automaticChord=if(page==0) Chords.named(scale.key.tonic+if(scale.key.mode in listOf(ScaleDefinition.NATURAL_MINOR,ScaleDefinition.MINOR_PENTATONIC,ScaleDefinition.BLUES)) "m" else "") else if(!chordPhrases) first else shownChord
    val referenceChord=Chords.named(if(referenceChordName=="auto") phrase.lesson?.referenceChordName ?: "C" else referenceChordName)
    val boardTeaching = BoardTeachingContext(if(page==0) scale.key.context else if(chordPhrases) phrase.lesson?.key else null,
        if(referenceChordName=="auto") automaticChord ?: referenceChord else referenceChord,
        referenceChordName!="auto" || automaticChord==null || page==0)
    val renderHint = hint.takeIf { it in boardTeaching.available } ?: Hint.FULL
    val nextChord = if (page == 1 && chordPhrases && harmony.isNotEmpty())
        (harmony.firstOrNull { it.tick>(phraseNow?.tick ?: previewStart) && it.tick<previewEnd }
            ?: harmony.getOrNull(displayedSequence?.sectionAt(previewStart.toDouble()) ?: -1))?.chord.orEmpty() else ""
    fun changed() { player.invalidate(); note = "" }
    fun scaleChange(value: ScaleOptions) { if (value != scale) { changed(); scale = value } }
    fun firstChange(value: Chord) { if (firstName != value.id) { changed(); firstName = value.id } }
    fun loadPhrase(value: Phrase) {
        if (value.unprepared) { note = "正在准备曲目…"; return }
        val saved = phraseStore.load(value, bpm, repeats)
        bpm = saved.bpm; repeats = saved.repeats; firstBar = saved.range.first; lastBar = saved.range.last
        if(transitionTargetId==value.id) {
            repeats=0; firstBar=0; lastBar=value.sequence.barCount-1; transitionTargetId=null
        }
        val tempo = phraseStore.tempo(value)
        sourceTempo = tempo.first; tempoMultiplier = tempo.second
        val prefix = value.assetKey?.let { "asset." + it.encoded + "." }
        selectedTrackId = prefix?.let { preferences.getString(it + "mainTrack", null) }
        soloTrack = prefix?.let { preferences.getBoolean(it + "solo", false) } ?: false
        if(value.sequence.runtimePlan?.tempos?.size?.let { it>1 }==true && !sourceTempo) {
            tempoMultiplier = TempoControl.ratio(saved.bpm,TempoControl.sourceBpm(value.sequence,saved.range.first))*100
            sourceTempo=true; phraseStore.saveTempo(value,true,tempoMultiplier)
        }
        if (saved.rangeReset) note = "曲包修订改变了范围，已恢复整句；速度和次数保留。"
    }
    LaunchedEffect(repository) {
        librarySnapshot = withContext(Dispatchers.IO) { repository.reload() }
        val saved = phraseStore.selected()
        val restored = if(userSelectedModule && chordPhrases) librarySnapshot.catalogue.firstOrNull { contentPresentation.moduleOf(it)==module } ?: saved else saved
        if(!userSelectedModule) module = contentPresentation.moduleOf(restored)
        phraseId = restored.id
        if(page==1 && chordPhrases) loadPhrase(restored)
        libraryReady = true
    }
    val selectedRevision = phrase.assetKey?.let { key -> librarySnapshot.packages.firstOrNull { it.identity.packageId == key.packageId }?.identity }
    LaunchedEffect(libraryReady, phraseId, selectedRevision) {
        if (libraryReady && repository.named(phraseId).unprepared) {
            val job = currentCoroutineContext()
            try {
                val loaded = withContext(Dispatchers.IO) { repository.resolve(phraseId) { job.ensureActive() } }
                preparedEpoch++; loadPhrase(loaded); note = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note = "曲目准备失败：" + e.message
            }
        }
    }
    fun phraseChange(value: Phrase) {
        if(transitionTargetId!=value.id) transitionTargetId=null
        chainFirst=-1; chainLast=-1; chainStep=0; leadFade=false
        phraseStore.select(value)
        if (phraseId != value.id) { changed(); phraseId = value.id; loadPhrase(value) }
    }
    fun tap(p: Position) {
        val card = first.takeIf { page == 1 && !chordPhrases }
        if (card?.unavailableReason != null) { note = card.unavailableReason!!; return }
        val capo = card?.capoFret ?: if (page == 1) displayedSequence?.capoFret ?: 0 else 0
        val tuning = card?.openStringMidi ?: displayedSequence?.runtimePlan?.mainTrack?.openStringMidi
            ?: displayedSequence?.openStringMidi ?: Music.openMidi
        player.audition(listOf(p), capoFret = capo, openStringMidi = tuning)
        val name = if (page == 1 && !chordPhrases) first.noteName(p, true) else Music.name(displayedSequence?.midi(p) ?: (p.midi + capo), true)
        note = "${p.string} 弦 ${p.fret} 品 · $name" +
            if (page == 0) " · ${scale.key.degreeLabel(p.midi)} 级"
            else if (!chordPhrases || harmony.isNotEmpty()) shownChord?.let { " · ${it.role(p)}" }.orEmpty()
            else ""
    }
    fun setHint(index: Int) { hintIndex = index; note = ""; preferences.edit().putInt("hint", index).apply() }
    val status = when {
        session?.preview == true && player.active -> if (session.sequence.events.single().positions.size > 1) "整和弦试听" else "点音试听"
        player.active && cursor?.leadIn == true -> "拍位对齐 · 随后进入谱面"
        player.active && cursor?.pre == true -> if (nativePlayer != null) "预备拍…" else "预备拍 ${cursor.beat + 1} / ${session!!.preBeats}"
        progress.phase == Phase.PLAYING -> "第 ${cursor!!.loop + 1}${if (session!!.repeats > 0) "/${session.repeats}" else ""} 轮 · 第 ${session!!.sequence.barLabel(cursor.bar)} 小节" +
            (session.plan.revisionPin?.split('/')?.getOrNull(1)?.let { " · 修订" + it } ?: "")
        nativePlayer?.fromMeasure == true -> "已定位 · 第 ${displayedSequence?.barLabel(nativePlayer.focus!!.visit)} 小节 · 第 ${displayedSequence?.performance?.visits?.getOrNull(nativePlayer.focus!!.visit)?.occurrence} 次经过 · 从本小节练"
        progress.phase == Phase.PAUSED -> "已暂停 · 从${if (cursor?.pre == true) "预备" else "第 ${session?.sequence?.barLabel(cursor?.bar ?: 0) ?: "1"}"}小节继续"
        progress.phase == Phase.PREPARING -> "准备声音…"
        progress.phase == Phase.ENDED -> progress.message
        progress.phase == Phase.ERROR -> progress.message
        else -> progress.message.takeIf { it.startsWith("设置已改变") } ?: "准备好，就开始一轮"
    }
    BackHandler(enabled = overlay == PracticeOverlay.NONE && (player.active || progress.phase == Phase.PAUSED)) { player.stop() }
    BackHandler(enabled = overlay == PracticeOverlay.PRACTICE || overlay == PracticeOverlay.CONTENT) { overlay = PracticeOverlay.NONE }
    val windowConfiguration = LocalConfiguration.current
    val tabletControls = minOf(windowConfiguration.screenWidthDp, windowConfiguration.screenHeightDp) >= 600
    PracticeTheme(largeControls = tabletControls, theme = themeManager.current) {
        if (volumeOpen) PracticeMixSheet(mix, { value -> mix = value; value.save(preferences) },
            leadLevel.toFloat(), backingLevel.toFloat(), { leadFade = false; leadLevel = it.toDouble() },
            { backingLevel = it.toDouble() }, { volumeOpen = false })
        // Paint the entire window before insets keep controls clear of cutouts and system UI.
        BoxWithConstraints(
            Modifier.fillMaxSize().testTag(if(libraryReady) "practice-library-ready" else "practice-library-loading")
                .practiceBackground()
                .displayCutoutPadding()
                .windowInsetsPadding(WindowInsets.captionBar)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
        ) {
            val tab = stageLayout == StageLayout.TAB_ONLY
            val showNativeScore = nativePlayer != null && stageLayout != StageLayout.FRETBOARD_ONLY
            LaunchedEffect(activity) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            val windowHeight = maxHeight
            val configuring = overlay == PracticeOverlay.PRACTICE || overlay == PracticeOverlay.CONTENT

            val tempoVisit = if (showingSession) nativePlayer?.currentVisit ?: cursor?.bar ?: activeRange.first else activeRange.first
            val tempoReference = sequence?.let { if(showingSession) TempoControl.sourceBpmAt(it,(cursor?.localTick ?: (it.barStart(tempoVisit).toDouble()))/it.ticksPerQuarter) else TempoControl.sourceBpm(it, tempoVisit) } ?: 60.0
            val mapTempo = sequence?.runtimePlan != null && sourceTempo
            val visibleBpm = if (mapTempo) (tempoReference * tempoMultiplier / 100.0).roundToInt() else bpm
            fun setBpm(value: Int) {
                changed()
                if (mapTempo) {
                    tempoMultiplier = TempoControl.ratio(value, tempoReference) * 100.0
                    phraseStore.saveTempo(phrase, true, tempoMultiplier)
                } else bpm = value
            }
            fun setMultiplier(value: Double) { if (value != tempoMultiplier) { changed(); tempoMultiplier = value; phraseStore.saveTempo(phrase, sourceTempo, value) } }
            fun category(value: Int) {
                userSelectedModule=true
                val next = TeachingModule.entries[value]
                if(module != next || next == TeachingModule.CHORDS && chordPhrases || next == TeachingModule.SCALES && page!=0) {
                    changed(); module = next; page = if(next == TeachingModule.SCALES) 0 else 1
                    chordPhrases = next != TeachingModule.SCALES && next != TeachingModule.CHORDS
                    stageLayout = if(chordPhrases && nativePlayer != null) StageLayout.LINKED else StageLayout.FRETBOARD_ONLY
                    if(chordPhrases) {
                        val candidate = librarySnapshot.catalogue.firstOrNull { contentPresentation.moduleOf(it) == next }
                        if(candidate != null && contentPresentation.moduleOf(phrase) != next) phraseChange(candidate)
                    }
                }
            }
            val summary = if (page == 0) "${scale.key.title} · ${Patterns.forKey(scale.key,scale.pentatonic)[scale.pattern].solfege} · ${scale.exercise.title}"
            else if (chordPhrases) phrase.title else "${first.name} · 逐弦"
            val specBadge = when {
                page == 0 -> "${if (scale.octave) "一八度" else "整型"} · 每拍${scale.notesPerBeat}音"
                chordPhrases -> "${phrase.sequence.barLabel(range.first)}–${phrase.sequence.barLabel(range.last)}小节"
                else -> first.group
            }
            val meterLabel = displayedSequence?.runtimePlan?.visits?.getOrNull(cursor?.bar ?: range.first)?.measure?.meter?.let {
                it.numerator.toString() + "/" + it.denominator
            } ?: "$beatsPerBar/4"

            val selectView: (Int) -> Unit = { selected ->
                overlay = PracticeOverlay.NONE
                stageLayout = if (selected == 1) StageLayout.TAB_ONLY else StageLayout.FRETBOARD_ONLY
            }

            var scoreBounds by remember { mutableStateOf<Rect?>(null) }
            var hostOrigin by remember { mutableStateOf(Offset.Zero) }
            val density = LocalDensity.current

            val stageSlot: @Composable (Modifier) -> Unit = { stageModifier ->
                BoxWithConstraints(stageModifier) {
                    val contentHeight = maxHeight
                    val contentState = PracticeContentState(
                        page, chordPhrases, tab, tabletControls, scale, first, phrase, melody,
                        sequence, displayedSequence, scaleResult, hint, renderHint, player.active, progress.phase, renderCursor, cursor,
                        showingSession, event, phraseNow, nextEvent, moving, current, shownChord, nextChord, status,
                        if (page == 1 && !chordPhrases) chordUnavailable ?: note else note, beatsPerBar, chords, configuring,
                        previewPositions,upcomingPositions,previewQuarter,
                    )
                    CompositionLocalProvider(
                        LocalStageViewSwitch provides false,
                        LocalBoardTeaching provides boardTeaching,
                        LocalSelectHint provides { value -> setHint(value.ordinal) },
                        LocalSelectReferenceChord provides { name ->
                            referenceChordName = name
                            preferences.edit().putString("reference-chord",name).apply()
                            setHint(Hint.CHORD_TONES.ordinal)
                        },
                    ) {
                        if (showNativeScore) {
                            val fontScale = density.fontScale.coerceAtLeast(1f)
                            val targetHeight = if (stageLayout == StageLayout.LINKED && contentHeight >= 320.dp * fontScale) {
                                minOf(280.dp * fontScale, maxOf(180.dp * fontScale, contentHeight * .35f), contentHeight - 198.dp * fontScale)
                            } else 0.dp
                            val dockHeight = targetHeight
                            val breakdown: @Composable () -> Unit = {
                                nativePlayer?.let { native ->
                                    BreakdownControls(native.ready,native.canStep(-1),native.canStep(1),
                                        { native.step(-1) },{ native.step(1) },{ native.auditionFocus() })
                                }
                            }
                            Column(Modifier.fillMaxWidth().height(contentHeight)) {
                                if (dockHeight == 0.dp || displayedSequence == null) breakdown()
                                if (stageLayout == StageLayout.LINKED && targetHeight == 0.dp) {
                                    Text("窗口较矮，已优先显示谱面", color = PracticeSkin.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                                if (dockHeight > 0.dp && displayedSequence != null) {
                                    Box(Modifier.fillMaxWidth().height(dockHeight).clipToBounds()) {
                                        LinkedFretboardDock(
                                            sequence = displayedSequence,
                                            cursor = cursor?.takeIf { showingSession },
                                            phase = progress.phase,
                                            current = current,
                                            range = if (page == 1 && chordPhrases) range else BarRange(0, displayedSequence.barCount - 1),
                                            hint = renderHint,
                                            onHint = { setHint(if (hint == Hint.HIDDEN) 0 else 2) },
                                            onTap = ::tap,
                                            modifier = Modifier.fillMaxSize(),
                                            enabled = !player.active && progress.phase != Phase.PAUSED,
                                            labelRoot = boardTeaching.root,
                                            loopContinues = practiceSession?.let { it.repeats == 0 || (cursor?.loop ?: 0) + 1 < it.repeats } == true,
                                            controls = breakdown,
                                        )
                                    }
                                }
                                HorizontalDivider(color = PracticeSkin.colors.borderSubtle)
                                Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { scoreBounds = it.boundsInRoot() })
                                PracticeStatus(status, progress.phase, beatsPerBar, cursor, showingSession, compact = true, note = note)
                            }
                        } else {
                            PracticeContent(
                                contentState,
                                Modifier.fillMaxWidth().height(contentHeight),
                                ::firstChange,
                                selectView,
                                ::tap,
                                { chord ->
                                    val unavailable = chord.unavailableReason
                                    if (unavailable != null) note = unavailable else {
                                        player.audition(chord.positions, capoFret = chord.capoFret, openStringMidi = chord.openStringMidi)
                                        note = "${chord.name} · 整和弦试听"
                                    }
                                },
                                legacyTab = legacyTab,
                                onHint = { setHint(if (hint == Hint.HIDDEN) 0 else 2) },
                            )
                        }
                    }
                }
            }

            val lessonActions: @Composable RowScope.() -> Unit = {
                if(page==1 && chordPhrases && !phrase.unprepared) {
                    fun destination(id: String): Phrase? {
                        val global=librarySnapshot.phrases.firstOrNull { it.id==id }
                        val encoded=phrase.assetKey?.let { com.a3322505a.guitarlearning.music.score.PracticeKey(it.packageId,id).encoded }
                        return global ?: encoded?.let { key -> librarySnapshot.phrases.firstOrNull { it.id==key } }
                    }
                    val versions=repository.versions(phrase)
                    val warmups=phrase.lesson?.warmupIds.orEmpty().mapNotNull(::destination)
                    val transitions=phrase.lesson?.transitionIds.orEmpty().mapNotNull(::destination)
                    val chain=remember(sequence,range,chainOriginal) { sequence?.let { PracticeExtensions.backwardRanges(it,chainOriginal ?: range) }.orEmpty() }
                        if(versions.size>1) versions.forEach { version ->
                            FilterChip(selected=version.id==phrase.id,onClick={ changed(); phraseChange(version) },label={ Text(version.version ?: version.title) })
                        }
                        if(returnPhraseId!=null) TextButton(onClick={
                            val target=returnPhraseId!!; changed(); module=returnModule; phraseChange(repository.named(target)); returnPhraseId=null
                        }) { Text("返回原曲段") }
                        fun jump(target: Phrase) {
                            phraseStore.save(phrase,bpm,repeats,range); phraseStore.saveTempo(phrase,sourceTempo,tempoMultiplier)
                            if(returnPhraseId==null) { returnPhraseId=phrase.id; returnModule=module }
                            changed(); phraseChange(target); module=contentPresentation.moduleOf(target)
                        }
                        if(warmups.isNotEmpty()) TextButton(onClick={ jump(warmups.first()) }) { Text("练前热身") }
                        transitions.forEach { target -> TextButton(onClick={ transitionTargetId=target.id; jump(target) }) { Text("衔接练习") } }
                        if(sequence?.let(PracticeExtensions::canFade)==true) {
                            listOf(1.0,.3,0.0).forEach { gain -> FilterChip(selected=!leadFade && leadLevel==gain,onClick={ leadFade=false;leadLevel=gain },label={ Text("领奏 ${(gain*100).toInt()}%") }) }
                            FilterChip(selected=leadFade,onClick={ leadFade=!leadFade },label={ Text("循环渐隐") })
                            TextButton(onClick={ volumeOpen=true }) { Text("伴奏 ${(backingLevel*100).toInt()}%") }
                        }
                        if(chain.isNotEmpty()) {
                            TextButton(onClick={
                                changed()
                                if(chainOriginal==null) {
                                    chainFirst=range.first; chainLast=range.last; chainStep=0; chainRepeats=repeats; repeats=0
                                    firstBar=chain.first().first; lastBar=chain.first().last
                                } else {
                                    firstBar=chainOriginal!!.first; lastBar=chainOriginal!!.last; repeats=chainRepeats; chainFirst=-1; chainLast=-1
                                }
                            }) { Text(if(chainOriginal==null) "倒序接龙" else "退出接龙") }
                            if(chainOriginal!=null && chainStep<chain.lastIndex) TextButton(onClick={
                                changed(); chainStep++; firstBar=chain[chainStep].first; lastBar=chain[chainStep].last
                            }) { Text("接上前一段") }
                        }
                    if(leadFade) Text("领奏随循环 100% → 30% → 0% · 伴奏保持",style=MaterialTheme.typography.labelSmall)
                    PracticeExtensions.action(phrase.sequence,if(showingSession) (cursor?.localTick ?: 0.0)/phrase.sequence.ticksPerQuarter else 0.0)?.let { Text(it,style=MaterialTheme.typography.labelSmall) }
                }
            }
            val speedMultiplier = if (mapTempo) tempoMultiplier / 100.0 else visibleBpm / tempoReference
            if (tempoOpen) SpeedAndTempoSheet(visibleBpm, speedMultiplier,
                if (mapTempo) (tempoReference * .25).roundToInt().coerceAtLeast(1) else 30,
                if (mapTempo) (tempoReference * 2).roundToInt() else 180,
                if (mapTempo) "原谱 ${tempoReference.roundToInt()} BPM · 保留原谱变速" else "固定四分音符 BPM",
                ::setBpm, { value -> if (mapTempo) setMultiplier(value * 100.0) else setBpm((tempoReference * value).roundToInt().coerceIn(30, 180)) },
                { tempoOpen = false })
            fun start(follow: Boolean) {
                sequence?.let {
                    note = ""
                    player.start(it, bpm, follow, if (follow) repeats else 1,
                        if (page == 1 && chordPhrases) range else BarRange(0, it.barCount - 1),
                        it.runtimePlan != null && sourceTempo,
                        if (it.runtimePlan != null && sourceTempo) tempoMultiplier / 100.0 else 1.0)
                }
            }
            val transportBlock: @Composable () -> Unit = {
                PracticeTransportDock(visibleBpm, speedMultiplier, meterLabel,
                    transportMode(progress.phase, session != null, session?.preview == true),
                    sequence != null && (nativePlayer == null || nativePlayer.ready), repeats == 0,
                    "小节 ${displayedSequence?.barLabel(activeRange.first) ?: 1}–${displayedSequence?.barLabel(activeRange.last) ?: 1}",
                    onTempo = { tempoOpen = true },
                    onLoop = { changed(); if (repeats == 0) repeats = finiteRepeats else { finiteRepeats = repeats; repeats = 0 } },
                    onRange = { overlay = PracticeOverlay.PRACTICE }, onVolume = { volumeOpen = true },
                    onPauseResume = { if (player.active) player.pause() else player.resume() },
                    onRestart = { player.restart() }, onDemo = { start(false) }, onFollow = { start(true) },
                    onStop = { player.stop(); note = "" }, strategy = lessonActions,
                    pausedTitle = if (nativePlayer?.fromMeasure == true) "从本小节练" else "继续")
            }

            // Root coordinate anchor: NativeScore stays at a single fixed sibling index in this root Box
            // across all orientation, deck/column, view, and overlay changes so AlphaTabView never detaches.
            Box(Modifier.fillMaxSize().onGloballyPositioned { hostOrigin = it.positionInRoot() }) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = { transportBlock() },
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StageTopBar(
                            title = summary,
                            subtitle = "${module.title} · $visibleBpm BPM · $specBadge",
                            stageLayout = stageLayout,
                            onStageLayoutChange = { requested ->
                                overlay = PracticeOverlay.NONE
                                stageLayout = if (nativePlayer == null && requested == StageLayout.LINKED) StageLayout.TAB_ONLY else requested
                            },
                            onOpenLibrary = { workbenchDestinationId = WorkbenchDestination.Curriculum.id; overlay = PracticeOverlay.CONTENT },
                            onOpenSettings = { overlay = PracticeOverlay.PRACTICE },
                            onOpenDictionary = {
                                workbenchDestinationId = if (page == 0 || module == TeachingModule.SCALES) WorkbenchDestination.Scales.id else WorkbenchDestination.Chords.id
                                overlay = PracticeOverlay.CONTENT
                            },
                            linkedAvailable = nativePlayer != null,
                        )
                        if (stageLayout != StageLayout.TAB_ONLY) {
                            CompositionLocalProvider(LocalBoardTeaching provides boardTeaching, LocalSelectHint provides { setHint(it.ordinal) }) {
                                FretboardHeaderStrip(renderHint, nextChord)
                            }
                        }
                        stageSlot(Modifier.weight(1f).fillMaxWidth())
                    }
                }

                // Single fixed callsite for NativeScore: alphaTab destroys its API on detach.
                val bounds = scoreBounds
                val hasFrame = bounds != null && bounds.width > 0 && bounds.height > 0
                val frame = if (hasFrame) {
                    Modifier.offset {
                        IntOffset((bounds!!.left - hostOrigin.x).roundToInt(), (bounds.top - hostOrigin.y).roundToInt())
                    }.size(with(density) { bounds!!.width.toDp() }, with(density) { bounds.height.toDp() })
                } else Modifier.fillMaxSize()

                if (nativePlayer != null) {
                    NativeScore(nativePlayer, frame, showNativeScore && displayedSequence != null && hasFrame,
                        inputEnabled = overlay == PracticeOverlay.NONE && !volumeOpen && !tempoOpen)
                }
                if (showNativeScore && nativePlayer != null && (nativePlayer.loading || nativePlayer.error.isNotBlank())) {
                    Box(frame.background(PracticeSkin.colors.diagramSurface), contentAlignment = Alignment.Center) {
                        Text(nativePlayer.error.ifBlank { "正在准备谱面…" }, color = PracticeSkin.colors.danger)
                    }
                }

                if (configuring) {
                    Box {
                        ContentWorkbenchSheet(
                            state = PracticeSettingsState(
                                page, chordPhrases, tabletControls, scale, first, phrase,
                                repository.family(phrase), repository.versions(phrase), librarySnapshot.catalogue.groupBy { it.use }, chords.groupBy { it.group },
                                phrase.relatedId?.let { repository.named(it).title }, bpm, repeats, firstBar, lastBar, range, wholeRange, guitar, click,
                                moreExercisesOpen, beatsPerBar, sequence, displayedSequence, scaleResult, sourceTempo, tempoMultiplier, selectedTrackId, soloTrack,
                                if (page == 0) scaleResult.reason else if (!libraryReady) "正在加载曲库…" else if (!chordPhrases) chordUnavailable ?: note else note,
                                content = contentPresentation, activeSourceFilter = activeSourceFilter, module = module, focusedPracticeId = focusedPracticeId,
                                dictionaryChord = shownChord ?: first,
                                dictionaryScale = if (page == 1 && chordPhrases) phrase.lesson?.key?.let { key ->
                                    scale.copy(key = Tonality.entries.first { it.root == key.tonic.pitchClass && it.mode == key.scale }, pattern = 0, pentatonic = false)
                                } ?: scale else scale,
                            ),
                            contentOnly = overlay == PracticeOverlay.CONTENT,
                            destination = WorkbenchDestination.fromId(workbenchDestinationId),
                            onDestination = { workbenchDestinationId = it.id },
                            onFocus = { focusedPracticeId = it; preferences.edit().putString("focused-practice", it).apply() },
                            onClose = { overlay = PracticeOverlay.NONE },
                            onCategory = ::category,
                            onScale = ::scaleChange,
                            onChord = { firstChange(it); module = TeachingModule.CHORDS; chordPhrases = false; page = 1; overlay = PracticeOverlay.NONE },
                            onPhrase = { changed(); chordPhrases=true; page=1; module=contentPresentation.moduleOf(it); stageLayout=if(nativePlayer!=null) StageLayout.LINKED else StageLayout.FRETBOARD_ONLY; phraseChange(it); overlay = PracticeOverlay.NONE },
                            onRange = { value -> if (value != range) { changed(); firstBar = value.first; lastBar = value.last } },
                            onRepeats = { value -> if (value != repeats) { changed(); repeats = value; if (value > 0) finiteRepeats = value } },
                            onSourceTempo = { value -> if (value != sourceTempo) { changed(); sourceTempo = value; phraseStore.saveTempo(phrase, value, tempoMultiplier) } },
                            onGuitar = { guitar = it; player.guitar = it; preferences.edit().putBoolean("guitar", it).apply() },
                            onClick = { click = it; player.click = it; preferences.edit().putBoolean("click", it).apply() },
                            mix = mix,
                            onMix = { mix = it; it.save(preferences) },
                            themeManager = themeManager,
                            onSourceFilter = { activeSourceFilter = it },
                            onManagePackages = { overlay = PracticeOverlay.MANAGEMENT },
                        )
                    }
                }
                if (overlay == PracticeOverlay.MANAGEMENT) {
                    SongLibrary(
                        repository,
                        { updated ->
                            librarySnapshot = updated
                            val restored = phraseStore.selected()
                            val current = librarySnapshot.phrases.firstOrNull { it.id==phraseId } ?: restored
                            phraseId = current.id; loadPhrase(current)
                        },
                        { overlay = PracticeOverlay.NONE },
                        displayedSequence,
                        themeManager,
                    )
                }
            }
        }
    }
}
