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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

@Composable
fun PracticeApp(player: AlphaPlayback) = PracticeWorkbench(player, player)

/** One selected PracticeSpec and one permanently mounted score host. */
@Composable
internal fun PracticeWorkbench(
    player: PracticePlayback,
    nativePlayer: AlphaPlayback?,
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    DisposableEffect(themeManager) { onDispose { themeManager.close() } }
    val preferences = remember { context.getSharedPreferences("practice-settings", 0) }
    val repository = remember {
        SongRepository(File(context.filesDir, "songs-native-v31"),
            SongReader(SongSchema { name -> context.assets.open("song-contract/$name").use { it.readBytes() } }),
            context.assets.list("song-packages").orEmpty().filter { it.endsWith(".gpractice") }
                .associateWith { name -> { context.assets.open("song-packages/$name") } })
    }
    var snapshot by remember { mutableStateOf(repository.snapshot) }
    var ready by remember { mutableStateOf(false) }
    val packages = remember(snapshot) { packageCards(snapshot) }
    val store = remember { PhraseSettingsStore(preferences, repository::named) }
    var phraseId by rememberSaveable { mutableStateOf(store.selected().id) }
    var preparedEpoch by remember { mutableIntStateOf(0) }
    var libraryVisible by rememberSaveable { mutableStateOf(true) }
    var managementOpen by rememberSaveable { mutableStateOf(false) }
    var practiceMenuOpen by remember { mutableStateOf(false) }
    var tempoOpen by remember { mutableStateOf(false) }
    var boardOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var looping by rememberSaveable { mutableStateOf(false) }
    val cursorSettings = remember { CursorSyncSettings(preferences) }
    var cursorOffsetMs by remember { mutableIntStateOf(cursorSettings.load()) }
    var click by rememberSaveable { mutableStateOf(preferences.getBoolean("click", true)) }
    var preparationError by remember { mutableStateOf("") }
    val mix = remember { PlaybackMix.load(preferences) }
    val revision = snapshot.packages.firstOrNull { pkg ->
        snapshot.packagePractices[pkg.identity.packageId].orEmpty().any { it.key.encoded == phraseId }
    }?.identity
    val phrase = remember(phraseId, revision, preparedEpoch) { repository.named(phraseId) }
    val sequence = phrase.sequence.takeIf { ready && !phrase.unprepared && revision != null }
    val range = sequence?.let { BarRange(0, it.barCount - 1) } ?: BarRange(0, 0)
    val repeats = if (looping) 0 else 1
    var settingsSequence by remember { mutableStateOf<Sequence?>(null) }

    LaunchedEffect(repository) {
        snapshot = withContext(Dispatchers.IO) { repository.reload() }
        val selected = store.selected()
        phraseId = selected.id.takeIf { id -> snapshot.phrases.any { it.id == id } }
            ?: snapshot.phrases.firstOrNull()?.id.orEmpty()
        ready = true
    }
    LaunchedEffect(ready, phraseId, revision) {
        if (ready && revision != null) {
            preparationError = ""
            val job = currentCoroutineContext()
            try {
                withContext(Dispatchers.IO) { repository.resolve(phraseId) { job.ensureActive() } }
                preparedEpoch++
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { preparationError = "曲目准备失败：${e.message}" }
        }
    }
    LaunchedEffect(sequence) {
        if (sequence != null) {
            val saved = store.load(phrase)
            bpm = store.targetBpm(phrase)
            looping = saved.repeats == 0
            settingsSequence = sequence
        }
        nativePlayer?.prepareSequence(sequence)
    }
    val referenceBpm = sequence?.let { TempoControl.sourceBpm(it) } ?: 60.0
    val minBpm = TempoControl.minimum(referenceBpm)
    val maxBpm = TempoControl.maximum(referenceBpm)
    val targetBpm = bpm.coerceIn(minBpm, maxBpm)
    val speed = TempoControl.ratio(targetBpm, referenceBpm)
    LaunchedEffect(sequence, settingsSequence, targetBpm, repeats, speed) {
        nativePlayer?.configurePractice(sequence, targetBpm, repeats, range, true, speed)
        if (sequence != null && sequence === settingsSequence) {
            store.save(phrase, targetBpm, repeats, range)
            store.saveTargetBpm(phrase, targetBpm)
        }
    }
    SideEffect {
        player.guitar = true; player.click = click; player.setMix(mix)
        nativePlayer?.cursorOffsetMs = cursorOffsetMs
    }
    val activity = LocalActivity.current
    LaunchedEffect(activity) { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }

    fun selectPractice(practice: LibraryPractice) {
        if (phraseId != practice.key.encoded) {
            player.invalidate(); preparationError = ""
            phraseId = practice.key.encoded
            store.select(repository.named(phraseId))
        }
        libraryVisible = false; practiceMenuOpen = false
    }
    fun openLibrary() { player.pause(); libraryVisible = true; practiceMenuOpen = false }
    BackHandler(enabled = !libraryVisible && !managementOpen && !tempoOpen && !practiceMenuOpen) { openLibrary() }
    val progress = player.progress
    val session = player.session?.takeUnless { it.preview }
    val cursor = player.cursor?.takeIf { session != null && progress.phase != Phase.IDLE }
    val visit = (cursor?.bar ?: nativePlayer?.focus?.visit ?: 0).coerceIn(0, (sequence?.barCount ?: 1) - 1)
    val quarter = cursor?.localTick?.div(sequence?.ticksPerQuarter ?: QUARTER)
        ?: sequence?.performance?.visits?.getOrNull(visit)?.start?.value() ?: 0.0
    fun setBpm(value: Int) {
        val next = value.coerceIn(minBpm, maxBpm)
        if (next != targetBpm) {
            player.invalidate()
            bpm = next
        }
    }
    fun play() {
        when {
            player.active -> player.pause()
            progress.phase == Phase.PAUSED && session != null -> player.resume()
            else -> sequence?.let { player.start(it, targetBpm, true, repeats, range, true, speed) }
        }
    }
    val packageCard = packages.firstOrNull { it.id == phrase.assetKey?.packageId }
    val currentSection = sequence?.sectionAt(quarter * (sequence.ticksPerQuarter)) ?: -1
    val shownChord = sequence?.sections?.getOrNull(currentSection)?.let { sequence.chordAt(it) }
    val currentChord = sequence?.runtimePlan?.harmonies?.lastOrNull {
        it.start.value() <= quarter && (it.end == null || quarter < it.end.value())
    }?.symbol?.name ?: sequence?.sections?.getOrNull(currentSection)?.chord.orEmpty()
    val nextChord = sequence?.runtimePlan?.harmonies?.firstOrNull { it.start.value() > quarter }?.symbol?.name
        ?: sequence?.sections?.firstOrNull { it.tick > quarter * sequence.ticksPerQuarter }?.chord.orEmpty()
    val meter = sequence?.performance?.visits?.getOrNull(visit)?.measure?.meter
    val meterLabel = meter?.let { "${it.numerator}/${it.denominator}" } ?: "4/4"
    val teaching = BoardTeachingContext(phrase.lesson?.key, shownChord ?: Chords.named("C"), hasHarmony = shownChord != null)
    val labelRoot = phrase.lesson?.key?.tonic?.pitchClass ?: shownChord?.root
    val currentPositions = if (cursor != null && !cursor.pre) {
        nativePlayer?.focus?.positions ?: nativePlayer?.positions ?: cursor.positions
    } else emptyList()
    val timingScale = if (session != null) {
        if (session.sourceTempo) session.tempoMultiplier else 1.0
    } else speed
    val nativeDuration = if (nativePlayer?.ready == true) nativePlayer.durationMillis else 0.0
    val duration = (nativeDuration / timingScale.coerceAtLeast(.01) / 1000.0).coerceAtLeast(0.0)
    val elapsed = if (progress.phase == Phase.ENDED) duration else if (cursor?.pre == true) 0.0
        else ((nativePlayer?.positionMillis ?: 0.0) / timingScale.coerceAtLeast(.01) / 1000.0).coerceIn(0.0, duration)
    val configuration = LocalConfiguration.current
    PracticeTheme(largeControls = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600,
        theme = themeManager.current) {
        BoxWithConstraints(Modifier.fillMaxSize().practiceBackground().displayCutoutPadding()
            .windowInsetsPadding(WindowInsets.captionBar)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .testTag(if (ready) "practice-library-ready" else "practice-library-loading")) {
            var scoreBounds by remember { mutableStateOf<Rect?>(null) }
            var hostOrigin by remember { mutableStateOf(Offset.Zero) }
            val density = LocalDensity.current
            // This Box and the NativeScore call below never move between branches or parents.
            Box(Modifier.fillMaxSize().onGloballyPositioned { hostOrigin = it.positionInRoot() }) {
                Column(Modifier.fillMaxSize()) {
                    PackageStageTopBar(
                        title = phrase.title,
                        subtitle = packageCard?.title?.takeUnless { it == phrase.title } ?: phrase.conditions,
                        chord = listOf(currentChord, nextChord).filter(String::isNotBlank).distinct().joinToString(" → "),
                        measure = "${visit + 1}/${sequence?.barCount ?: 1} · $meterLabel",
                        practices = packageCard?.practices.orEmpty(), selectedId = phraseId,
                        expanded = practiceMenuOpen, onExpanded = { practiceMenuOpen = it },
                        onPractice = ::selectPractice, onBack = ::openLibrary,
                    )
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                        val expanded = boardOverride ?: (maxHeight >= 350.dp)
                        val boardHeight = minOf(204.dp, maxHeight * .34f)
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompositionLocalProvider(LocalBoardTeaching provides teaching) {
                                StageFretboard(sequence, cursor, progress.phase, currentPositions, range, labelRoot,
                                    expanded, { boardOverride = !expanded },
                                    if (expanded) boardHeight else 48.dp,
                                    onTap = { position -> sequence?.let {
                                        player.audition(listOf(position), capoFret = it.capoFret, openStringMidi = it.runtimePlan?.mainTrack?.openStringMidi ?: it.openStringMidi)
                                    } }, enabled = !player.active && progress.phase != Phase.PAUSED, loopContinues = looping)
                            }
                            Box(Modifier.weight(1f).fillMaxWidth().background(PracticeSkin.colors.diagramSurface,
                                MaterialTheme.shapes.large).clipToBounds().testTag("score-viewport")
                                .onGloballyPositioned { scoreBounds = it.boundsInRoot() })
                        }
                    }
                    StageTransport(targetBpm, progress.phase, sequence != null && (nativePlayer == null || nativePlayer.ready),
                        looping, click, elapsed, duration, { tempoOpen = true }, ::play,
                        { player.invalidate(); looping = !looping },
                        { click = !click; preferences.edit().putBoolean("click", click).apply() },
                        { if (session != null) player.restart()
                            else sequence?.let { player.start(it, targetBpm, true, repeats, range, true, speed) } },
                        nativePlayer?.entryLight ?: 0, nativePlayer?.entryBeatNumber ?: 0,
                        nativePlayer?.entryBeatTotal ?: 0)
                }
                val bounds = scoreBounds
                val hasFrame = bounds != null && bounds.width > 0 && bounds.height > 0
                val frame = if (hasFrame) Modifier.offset {
                    IntOffset((bounds!!.left - hostOrigin.x).roundToInt(), (bounds.top - hostOrigin.y).roundToInt())
                }.size(with(density) { bounds!!.width.toDp() }, with(density) { bounds.height.toDp() })
                else Modifier.fillMaxSize()
                if (nativePlayer != null) {
                    NativeScore(nativePlayer, frame, hasFrame && sequence != null && !libraryVisible,
                        inputEnabled = !libraryVisible && !managementOpen && !tempoOpen && !practiceMenuOpen)
                }
                val error = preparationError.ifBlank {
                    if (ready && revision == null) "曲目不存在或曲包已停用，请从曲包库重新选择" else nativePlayer?.error.orEmpty()
                }
                    .ifBlank { progress.message.takeIf { progress.phase == Phase.ERROR }.orEmpty() }
                if (!libraryVisible && (sequence == null || nativePlayer?.loading == true || error.isNotBlank())) {
                    Box(frame.background(PracticeSkin.colors.diagramSurface), contentAlignment = Alignment.Center) {
                        Text(error.ifBlank { "正在准备谱面…" }, modifier = Modifier.padding(16.dp),
                            color = if (error.isBlank()) PracticeSkin.colors.textSecondary else PracticeSkin.colors.danger)
                    }
                }
                if (libraryVisible) PackageLibraryScreen(packages, ready, phraseId, ::selectPractice, { managementOpen = true })
                if (managementOpen) SongLibrary(repository, { updated ->
                    snapshot = updated
                    if (updated.phrases.none { it.id == phraseId }) {
                        player.invalidate(); phraseId = updated.phrases.firstOrNull()?.id.orEmpty()
                    }
                }, { managementOpen = false }, sequence, themeManager)
                if (tempoOpen) BpmSheet(targetBpm, minBpm, maxBpm, ::setBpm, { tempoOpen = false },
                    cursorOffsetMs, { value ->
                        cursorOffsetMs = value.coerceIn(-500, 500)
                        cursorSettings.save(cursorOffsetMs)
                        nativePlayer?.cursorOffsetMs = cursorOffsetMs
                    })
            }
        }
    }
}
