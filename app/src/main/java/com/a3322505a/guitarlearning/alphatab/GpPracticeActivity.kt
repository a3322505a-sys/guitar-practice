@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.model.Score
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.immersiveStatusBar
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practiceOrientation
import com.a3322505a.guitarlearning.ui.*
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GpPracticeActivity : ComponentActivity() {
    private lateinit var player: AlphaPlayback
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersiveStatusBar(window)
        player = AlphaPlayback(this)
        setContent { GpPracticeApp(player, this) }
    }
    override fun onResume() { super.onResume(); immersiveStatusBar(window) }
    override fun onStop() { player.pause(); super.onStop() }
    override fun onDestroy() { player.release(); super.onDestroy() }
}

@Composable
private fun GpPracticeApp(player: AlphaPlayback, activity: GpPracticeActivity) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    DisposableEffect(themeManager) { onDispose { themeManager.close() } }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val library = remember { GpLibrary(File(context.filesDir, "gp-library")) }
    val selection = remember { context.getSharedPreferences("gp-library-ui", 0) }
    val writer = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(writer) { onDispose { writer.shutdown() } }
    var songs by remember { mutableStateOf(emptyList<GpSong>()) }
    var song by remember { mutableStateOf<GpSong?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showLibrary by rememberSaveable { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GpSong?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    fun save(value: GpSong) {
        song = value
        songs = songs.map { if (it.id == value.id) value else it }
        writer.execute {
            try {
                library.save(value)
            } catch (failure: Exception) {
                activity.runOnUiThread { message = "偏好未能保存：${failure.message}" }
            }
        }
    }
    fun change(p: GpPreferences, stop: Boolean = true) {
        val current = song ?: return
        if (stop) player.invalidate()
        save(current.copy(preferences = p))
    }
    fun accept(result: Pair<GpSong, Score>, keepLibrary: Boolean = false) {
        val (loaded, score) = result
        val tracks = GpLibrary.guitarTracks(score)
        val track = tracks.firstOrNull { it.index.toInt() == loaded.preferences.track } ?: tracks.first()
        val current = loaded.copy(opened = System.currentTimeMillis(), preferences = loaded.preferences.copy(track = track.index.toInt()))
        player.loadScore(score, track.index.toInt())
        player.solo = current.preferences.solo
        player.click = current.preferences.metronome
        song = current
        showLibrary = keepLibrary
        showSettings = false
        message = ""
        selection.edit().putString("selected", current.id).apply()
        writer.execute { library.save(current) }
        songs = (library.list().filter { it.id != current.id } + current).sortedByDescending { maxOf(it.opened, it.imported) }
    }
    fun open(value: GpSong, keepLibrary: Boolean = false) {
        if (busy) return
        busy = true; player.stop(); message = "正在读取 ${value.title}…"
        scope.launch {
            try {
                accept(withContext(Dispatchers.IO) { writer.submit {}.get(); library.open(value.id) }, keepLibrary)
            } catch (failure: Exception) {
                message = "打开失败：${failure.message}"
                showLibrary = true
            } finally {
                busy = false
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true; message = "正在导入…"; player.stop()
            scope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) {
                        writer.submit {}.get()
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: uri.lastPathSegment.orEmpty()
                        checkNotNull(context.contentResolver.openInputStream(uri)) { "文件无法读取" }.use { library.import(name, it) }
                    }
                    accept(imported)
                } catch (failure: Exception) {
                    message = "导入失败：${failure.message}"
                    showLibrary = true
                } finally {
                    busy = false
                }
            }
        }
    }
    LaunchedEffect(library) {
        songs = withContext(Dispatchers.IO) { library.list() }
        val restored = selection.getString("selected", null)
        val keepLibrary = activity.intent.getBooleanExtra("open-library", false)
        activity.intent.removeExtra("open-library")
        songs.firstOrNull { it.id == restored }?.let { value -> open(value, keepLibrary) }
    }
    val preferences = song?.preferences ?: GpPreferences()
    val configuration = LocalConfiguration.current
    LaunchedEffect(preferences.tab, configuration) {
        activity.requestedOrientation = practiceOrientation(activity, preferences.tab)
    }
    val projection = player.projection
    val visits = projection?.visits.orEmpty()
    val first = preferences.first.coerceIn(0, visits.lastIndex.coerceAtLeast(0))
    val last = if (preferences.last < 0) visits.lastIndex.coerceAtLeast(0) else preferences.last.coerceIn(first, visits.lastIndex.coerceAtLeast(first))
    val track = player.selectedTrack
    val staff = track?.staves?.firstOrNull { it.tuning.length == 6.0 }
    val boardPositions = remember(track, player.currentVisit, projection) {
        val bar = visits.getOrNull(player.currentVisit)?.masterBar?.index?.toInt() ?: 0
        staff?.bars?.toList()?.getOrNull(bar)?.voices?.toList()?.flatMap {
            it.beats.toList().flatMap { beat ->
                beat.notes.toList().filter { !it.isDead && it.fret >= 0 && it.string in 1.0..6.0 }
                    .map { Position(7 - it.string.toInt(), it.fret.toInt()) }
            }
        }?.distinct().orEmpty()
    }
    fun start(follow: Boolean) {
        if (visits.isEmpty()) return
        // No guessed carry-in when the selected entry begins with a tied/legato continuation.
        val sourceBar = visits[first].masterBar.index.toInt()
        val crossesEntry = first > 0 && track?.staves?.any { s ->
            s.bars[sourceBar].voices.any { v ->
                v.beats.firstOrNull { !it.isRest }?.notes?.any { n ->
                    n.isTieDestination || n.hammerPullOrigin?.beat?.voice?.bar?.index?.let { it < sourceBar } == true ||
                        n.slideOrigin?.beat?.voice?.bar?.index?.let { it < sourceBar } == true
                } == true
            }
        } == true
        if (crossesEntry) { message = "选段起点包含上一小节的延音或连奏，请把起点前移。"; return }
        message = ""
        player.startGp(
            first, last, if (follow) preferences.repeats else 1,
            follow && preferences.countIn, preferences.speed / 100.0,
        )
    }
    val status = when {
        busy -> message
        player.error.isNotBlank() -> player.error
        message.isNotBlank() -> message
        player.progress.phase == Phase.ENDED -> "本次练习结束"
        player.progress.phase == Phase.PAUSED -> "已暂停 · ${projection?.label(player.currentVisit).orEmpty()}"
        player.active -> if (player.countingIn) "预备拍…" else "第 ${player.roundNumber} 轮 · ${projection?.label(player.currentVisit).orEmpty()}"
        !player.ready && song != null -> "正在准备谱面与声音…"
        song == null -> "导入一次，以后从本地曲库直接练习"
        else -> "${visits.size} 次小节经过 · 选段结束时截断剩余延音"
    }
    BackHandler(showLibrary) { if (song != null) showLibrary = false else activity.finish() }
    BackHandler(showSettings) { showSettings = false }

    PracticeTheme(largeControls = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600, theme = themeManager.current) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .practiceBackground()
                .displayCutoutPadding()
                .navigationBarsPadding(),
        ) {
            val useStudioDeck = maxWidth >= 600.dp || maxWidth > maxHeight
            val deckWidth = when {
                maxWidth >= 960.dp -> 248.dp
                maxWidth >= 720.dp -> 224.dp
                else -> 206.dp
            }
            val colors = PracticeSkin.colors
            val metrics = PracticeSkin.metrics
            val density = LocalDensity.current
            var scoreBounds by remember { mutableStateOf<Rect?>(null) }
            var hostOrigin by remember { mutableStateOf(Offset.Zero) }

            val rangeSpec = if (song != null && visits.isNotEmpty()) {
                "${first + 1}–${last + 1}经过 · ${preferences.speed}%"
            } else ""

            val gpTopActions: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        onClick = { activity.finish() },
                        modifier = Modifier.height(metrics.segmentStyle.height),
                        shape = MaterialTheme.shapes.medium,
                        color = colors.surface,
                        border = BorderStroke(metrics.borderWidth, colors.border),
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text("‹ 教学", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                    }
                    Surface(
                        onClick = { player.pause(); showLibrary = true },
                        modifier = Modifier.weight(1f).height(metrics.segmentStyle.height),
                        shape = MaterialTheme.shapes.medium,
                        color = if (showLibrary) colors.primaryContainer else colors.surface,
                        border = BorderStroke(metrics.borderWidth, if (showLibrary) colors.primary else colors.border),
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text("曲库", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                    }
                    Surface(
                        onClick = { showAppearance = true },
                        modifier = Modifier.size(metrics.segmentStyle.height).semantics { contentDescription = "设置与帮助" },
                        shape = MaterialTheme.shapes.medium,
                        color = colors.surface,
                        border = BorderStroke(metrics.borderWidth, colors.border),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("⚙", fontSize = 21.sp, color = colors.textSecondary) }
                    }
                }
            }

            val gpSummaryTrigger: @Composable () -> Unit = {
                Surface(
                    onClick = { if (song != null && !busy) showSettings = true else showLibrary = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = metrics.segmentStyle.height),
                    shape = MaterialTheme.shapes.medium,
                    color = colors.primaryContainer,
                    border = BorderStroke(metrics.borderWidth, colors.primary.copy(alpha = 0.45f)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                song?.title ?: "本地 GP 曲库",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.textPrimary,
                            )
                            if (rangeSpec.isNotBlank()) {
                                Text(rangeSpec, style = MaterialTheme.typography.labelSmall, color = colors.primary, maxLines = 1)
                            }
                        }
                        Text("配置 ▾", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                }
            }

            val transportBlock: @Composable (Boolean) -> Unit = { verticalDeck ->
                PracticeTransport(
                    player.effectiveBpm.toInt(),
                    true,
                    preferences.speed,
                    visits.getOrNull(player.currentVisit)?.masterBar?.let {
                        "${it.timeSignatureNumerator.toInt()}/${it.timeSignatureDenominator.toInt()}"
                    } ?: "4/4",
                    Hint.FULL,
                    transportMode(player.progress.phase, song != null, false),
                    song != null && player.ready && !busy,
                    {},
                    { value -> change(preferences.copy(speed = value), false); player.setSpeed(value / 100.0) },
                    {},
                    { if (player.active) player.pause() else player.resume() },
                    { player.restart() },
                    { start(false) },
                    { start(true) },
                    { player.stop() },
                    availableHeight = maxHeight,
                    showHintAction = false,
                    verticalDeck = verticalDeck,
                )
            }

            val stageCard: @Composable (Modifier) -> Unit = { stageModifier ->
                PracticeContentCard(
                    title = track?.name?.ifBlank { "GP 练习" } ?: "GP 练习",
                    subtitle = status,
                    actions = {
                        ViewSwitch(
                            preferences.tab,
                            { index -> change(preferences.copy(tab = index == 1), false) },
                        )
                    },
                    modifier = stageModifier,
                ) {
                    DiagramContainer(Modifier.weight(1f).fillMaxWidth()) {
                        BoxWithConstraints(
                            Modifier.fillMaxSize().onGloballyPositioned { scoreBounds = it.boundsInRoot() },
                        ) {
                            val boardHeight = maxHeight.coerceAtMost(300.dp)
                            if (!preferences.tab && song != null) {
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(colors.diagramSurface)
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val positions = (boardPositions + player.positions).distinct()
                                    if (positions.isEmpty()) {
                                        Text("当前小节休止", color = colors.textSecondary)
                                    } else {
                                        Fretboard(
                                            positions,
                                            null,
                                            Hint.FULL,
                                            false,
                                            player.positions,
                                            !player.active,
                                            height = boardHeight,
                                            onTap = {
                                                player.audition(
                                                    listOf(it),
                                                    capoFret = staff?.capo?.toInt() ?: 0,
                                                    openStringMidi = staff?.tuning?.toList()?.map(Double::toInt) ?: listOf(64, 59, 55, 50, 45, 40),
                                                )
                                            },
                                            showLegend = false,
                                            capoFret = staff?.capo?.toInt() ?: 0,
                                            openStringMidi = staff?.tuning?.toList()?.map(Double::toInt) ?: listOf(64, 59, 55, 50, 45, 40),
                                        )
                                    }
                                }
                            }
                            if (busy) {
                                Box(Modifier.fillMaxSize().background(colors.diagramSurface), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = colors.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Root Box keeps NativeScore at a single constant callsite across orientation and view switches.
            Box(Modifier.fillMaxSize().onGloballyPositioned { hostOrigin = it.positionInRoot() }) {
                if (useStudioDeck) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.width(deckWidth).fillMaxHeight(),
                            shape = MaterialTheme.shapes.large,
                            color = colors.surfaceSecondary,
                            border = BorderStroke(metrics.borderWidth, colors.border),
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    gpTopActions()
                                    gpSummaryTrigger()
                                }
                                transportBlock(true)
                            }
                        }
                        stageCard(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = { transportBlock(false) },
                    ) { padding ->
                        Column(
                            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            gpTopActions()
                            gpSummaryTrigger()
                            stageCard(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }

                val bounds = scoreBounds
                val hasFrame = bounds != null && bounds.width > 0 && bounds.height > 0
                val frame = if (preferences.tab && hasFrame) {
                    Modifier.offset {
                        IntOffset((bounds!!.left - hostOrigin.x).roundToInt(), (bounds.top - hostOrigin.y).roundToInt())
                    }.size(with(density) { bounds!!.width.toDp() }, with(density) { bounds.height.toDp() })
                } else Modifier.fillMaxSize()

                NativeScore(player, frame, preferences.tab && song != null && hasFrame && !busy)
            }
        }

        if (showAppearance) {
            AlertDialog(
                onDismissRequest = { showAppearance = false },
                title = { Text("主题 / 外观") },
                text = {
                    Column(
                        Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { ThemePicker(themeManager) }
                },
                confirmButton = {
                    TextButton({ showAppearance = false }, shape = MaterialTheme.shapes.small) { Text("完成") }
                },
            )
        }
        if (showLibrary) {
            AlertDialog(
                onDismissRequest = { if (song != null) showLibrary = false },
                title = { Text("本地 GP 曲库") },
                text = {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "GP5 / GPX / GP 已通过具体文件试读；不同导出版本仍可能有差异。",
                            style = MaterialTheme.typography.bodySmall,
                            color = PracticeSkin.colors.textSecondary,
                        )
                        OutlinedTextField(
                            search,
                            { search = it },
                            label = { Text("搜索曲目") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                        if (message.isNotBlank()) Text(message, color = PracticeSkin.colors.danger, style = MaterialTheme.typography.bodySmall)
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(songs.filter { it.title.contains(search, true) || it.fileName.contains(search, true) }, key = { it.id }) { item ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton({ open(item) }, enabled = !busy, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                item.fileName + if (item.opened > 0) " · 最近练习" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PracticeSkin.colors.textSecondary,
                                            )
                                        }
                                    }
                                    TextButton({ deleting = item }, enabled = !busy, shape = MaterialTheme.shapes.small) { Text("删除") }
                                }
                            }
                        }
                        if (songs.isEmpty()) Text("还没有导入的 GP。原有短句与曲包仍在教学入口中。", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton({ import.launch(arrayOf("*/*")) }, enabled = !busy, shape = MaterialTheme.shapes.small) {
                        Text(if (busy) "读取中…" else "导入 GP")
                    }
                },
                dismissButton = {
                    TextButton({ if (song != null) showLibrary = false else activity.finish() }, shape = MaterialTheme.shapes.small) {
                        Text(if (song != null) "继续练习" else "返回教学")
                    }
                },
            )
        }
        deleting?.let { target ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("删除 ${target.title}？") },
                text = { Text("只删除 App 保存的本地副本和这首曲子的练习偏好，原 GP 文件保留。") },
                confirmButton = {
                    TextButton(
                        {
                            deleting = null; busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { writer.submit {}.get(); library.delete(target.id) }
                                    songs = withContext(Dispatchers.IO) { library.list() }
                                    if (song?.id == target.id) {
                                        player.stop(); song = null; showLibrary = true; selection.edit().remove("selected").apply()
                                    }
                                } catch (failure: Exception) {
                                    message = "删除失败：${failure.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.small,
                    ) { Text("删除副本") }
                },
                dismissButton = {
                    TextButton({ deleting = null }, shape = MaterialTheme.shapes.small) { Text("取消") }
                },
            )
        }
        if (showSettings && song != null) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("跟练配置") },
                text = {
                    Column(
                        Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val tracks = player.score?.let(GpLibrary::guitarTracks).orEmpty()
                        GpChoice("吉他轨", tracks.map { it.name }, tracks.indexOf(track)) { index ->
                            player.selectTrack(tracks[index])
                            change(preferences.copy(track = tracks[index].index.toInt()))
                        }
                        val labels = visits.indices.map { "${it + 1} · ${projection?.label(it)}" }
                        GpChoice("起点", labels, first) { change(preferences.copy(first = it, last = maxOf(it, last))) }
                        GpChoice("终点", labels, last) { change(preferences.copy(last = maxOf(first, it))) }
                        TextButton({ change(preferences.copy(first = 0, last = -1)) }, shape = MaterialTheme.shapes.small) { Text("恢复整曲") }
                        Text("原速 ${preferences.speed}%")
                        var speedDraft by remember(preferences.speed) { mutableFloatStateOf(preferences.speed.toFloat()) }
                        Slider(
                            speedDraft,
                            { speedDraft = it },
                            onValueChangeFinished = {
                                change(preferences.copy(speed = speedDraft.toInt()), false)
                                player.setSpeed(speedDraft.toInt() / 100.0)
                            },
                            valueRange = 25f..200f,
                        )
                        GpChoice("循环次数", listOf("持续循环") + (1..16).map { "$it 次" }, preferences.repeats) {
                            change(preferences.copy(repeats = it))
                        }
                        GpToggle("只听所选吉他轨", preferences.solo) { change(preferences.copy(solo = it), false); player.solo = it }
                        GpToggle("开始前预备拍", preferences.countIn) { change(preferences.copy(countIn = it)) }
                        GpToggle("节拍器", preferences.metronome) { change(preferences.copy(metronome = it), false); player.click = it }
                        Text("起止点按引擎实际演奏顺序选择；遇到反复时，每次经过分别列出。", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                    }
                },
                confirmButton = {
                    TextButton({ showSettings = false }, shape = MaterialTheme.shapes.small) { Text("完成") }
                },
            )
        }
    }
}

@Composable
private fun GpChoice(label: String, choices: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = choices.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
            Text("$label：${choices.getOrNull(selected).orEmpty()}", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            choices.forEachIndexed { index, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(index) })
            }
        }
    }
}

@Composable
private fun GpToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange)
    }
}
