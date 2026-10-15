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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.immersiveStatusBar
import com.a3322505a.guitarlearning.practiceOrientation
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

class GpPracticeActivity : ComponentActivity() {
    private lateinit var player: AlphaPlayback
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); immersiveStatusBar(window)
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
            try { library.save(value) }
            catch (failure: Exception) { activity.runOnUiThread { message = "偏好未能保存：${failure.message}" } }
        }
    }
    fun change(p: GpPreferences, stop: Boolean = true) {
        val current = song ?: return
        if (stop) player.invalidate()
        save(current.copy(preferences = p))
    }
    fun accept(result: Pair<GpSong, Score>) {
        val (loaded, score) = result
        val tracks = GpLibrary.guitarTracks(score)
        val track = tracks.firstOrNull { it.index.toInt() == loaded.preferences.track } ?: tracks.first()
        val current = loaded.copy(opened = System.currentTimeMillis(), preferences = loaded.preferences.copy(track = track.index.toInt()))
        player.loadScore(score, track.index.toInt()); player.solo = current.preferences.solo; player.click = current.preferences.metronome
        song = current; showLibrary = false; showSettings = false; message = ""
        selection.edit().putString("selected", current.id).apply()
        writer.execute { library.save(current) }
        songs = (library.list().filter { it.id != current.id } + current).sortedByDescending { maxOf(it.opened, it.imported) }
    }
    fun open(value: GpSong) {
        if (busy) return
        busy = true; player.stop(); message = "正在读取 ${value.title}…"
        scope.launch {
            try { accept(withContext(Dispatchers.IO) { writer.submit {}.get(); library.open(value.id) }) }
            catch (failure: Exception) { message = "打开失败：${failure.message}"; showLibrary = true }
            finally { busy = false }
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
                } catch (failure: Exception) { message = "导入失败：${failure.message}"; showLibrary = true }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(library) {
        songs = withContext(Dispatchers.IO) { library.list() }
        val restored = selection.getString("selected", null)
        songs.firstOrNull { it.id == restored }?.let(::open)
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
        staff?.bars?.toList()?.getOrNull(bar)?.voices?.toList()?.flatMap { it.beats.toList().flatMap { beat ->
            beat.notes.toList().filter { !it.isDead && it.fret >= 0 && it.string in 1.0..6.0 }
                .map { Position(7 - it.string.toInt(), it.fret.toInt()) }
        } }?.distinct().orEmpty()
    }
    fun start(follow: Boolean) {
        if (visits.isEmpty()) return
        // No guessed carry-in when the selected entry begins with a tied/legato continuation.
        val sourceBar = visits[first].masterBar.index.toInt()
        val crossesEntry = first > 0 && track?.staves?.any { s -> s.bars[sourceBar].voices.any { v ->
            v.beats.firstOrNull { !it.isRest }?.notes?.any { n ->
                n.isTieDestination || n.hammerPullOrigin?.beat?.voice?.bar?.index?.let { it < sourceBar } == true ||
                    n.slideOrigin?.beat?.voice?.bar?.index?.let { it < sourceBar } == true
            } == true
        } } == true
        if (crossesEntry) { message = "选段起点包含上一小节的延音或连奏，请把起点前移。"; return }
        message = ""
        player.startGp(first, last, if (follow) preferences.repeats else 1,
            follow && preferences.countIn, preferences.speed / 100.0)
    }
    val status = when {
        busy -> message
        player.error.isNotBlank() -> player.error
        message.isNotBlank() -> message
        player.progress.phase == Phase.ENDED -> "本次练习结束"
        player.progress.phase == Phase.PAUSED -> "已暂停 · ${projection?.label(player.currentVisit).orEmpty()}"
        player.active -> if(player.countingIn) "预备拍…" else "第 ${player.roundNumber} 轮 · ${projection?.label(player.currentVisit).orEmpty()}"
        !player.ready && song != null -> "正在准备谱面与声音…"
        song == null -> "导入一次，以后从本地曲库直接练习"
        else -> "${visits.size} 次小节经过 · 选段结束时截断剩余延音"
    }
    BackHandler(showLibrary) { if (song != null) showLibrary = false else activity.finish() }
    BackHandler(showSettings) { showSettings = false }
    PracticeTheme(largeControls = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600) {
        Scaffold(modifier = Modifier.fillMaxSize().practiceBackground().displayCutoutPadding().navigationBarsPadding(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                PracticeTransport(player.effectiveBpm.toInt(), true, preferences.speed,
                    visits.getOrNull(player.currentVisit)?.masterBar?.let { "${it.timeSignatureNumerator.toInt()}/${it.timeSignatureDenominator.toInt()}" } ?: "4/4",
                    Hint.FULL, transportMode(player.progress.phase, song != null, false), song != null && player.ready && !busy,
                    {}, { value -> change(preferences.copy(speed = value), false); player.setSpeed(value / 100.0) }, {},
                    { if (player.active) player.pause() else player.resume() }, { player.restart() },
                    { start(false) }, { start(true) }, { player.stop() }, showHintAction = false)
            }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PracticeTextButton({ activity.finish() }) { Text("‹ 教学") }
                    Text(song?.title ?: "本地 GP 曲库", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    PracticeTextButton({ player.pause(); showLibrary = true }) { Text("曲库") }
                    PracticeTextButton({ showSettings = true }, enabled = song != null && !busy) { Text("配置") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ViewSwitch(preferences.tab) { change(preferences.copy(tab = it == 1), false) }
                    Text(track?.name.orEmpty(), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Text(status, maxLines = 2, style = MaterialTheme.typography.labelSmall, color = if (player.error.isNotBlank()) RoseInk else Green)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Paper)) {
                    val boardHeight = maxHeight.coerceAtMost(300.dp)
                    NativeScore(player, Modifier.fillMaxSize(), preferences.tab && song != null)
                    if (!preferences.tab && song != null) Box(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                        val positions = (boardPositions + player.positions).distinct()
                        if (positions.isEmpty()) Text("当前小节休止", color = Muted)
                        else Fretboard(positions, null, Hint.FULL, false, player.positions, !player.active,
                            height = boardHeight, onTap = {
                                player.audition(listOf(it), capoFret = staff?.capo?.toInt() ?: 0,
                                    openStringMidi = staff?.tuning?.toList()?.map(Double::toInt) ?: listOf(64, 59, 55, 50, 45, 40))
                            }, showLegend = false, capoFret = staff?.capo?.toInt() ?: 0,
                            openStringMidi = staff?.tuning?.toList()?.map(Double::toInt) ?: listOf(64, 59, 55, 50, 45, 40))
                    }
                    if (busy) Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Green) }
                }
            }
        }
        if (showLibrary) AlertDialog(onDismissRequest = { if (song != null) showLibrary = false },
            title = { Text("本地 GP 曲库") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GP5 / GPX / GP 已通过具体文件试读；不同导出版本仍可能有差异。", style = MaterialTheme.typography.bodySmall, color = Muted)
                    OutlinedTextField(search, { search = it }, label = { Text("搜索曲目") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (message.isNotBlank()) Text(message, color = RoseInk, style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(songs.filter { it.title.contains(search, true) || it.fileName.contains(search, true) }, key = { it.id }) { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton({ open(item) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(item.fileName + if (item.opened > 0) " · 最近练习" else "", style = MaterialTheme.typography.labelSmall, color = Muted)
                                    }
                                }
                                TextButton({ deleting = item }, enabled = !busy) { Text("删除") }
                            }
                        }
                    }
                    if (songs.isEmpty()) Text("还没有导入的 GP。原有短句与曲包仍在教学入口中。", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = { TextButton({ import.launch(arrayOf("*/*")) }, enabled = !busy) { Text(if (busy) "读取中…" else "导入 GP") } },
            dismissButton = { TextButton({ if (song != null) showLibrary = false else activity.finish() }) { Text(if (song != null) "继续练习" else "返回教学") } })
        deleting?.let { target -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除 ${target.title}？") },
            text = { Text("只删除 App 保存的本地副本和这首曲子的练习偏好，原 GP 文件保留。") },
            confirmButton = { TextButton({
                deleting = null; busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { writer.submit {}.get(); library.delete(target.id) }
                        songs = withContext(Dispatchers.IO) { library.list() }
                        if (song?.id == target.id) { player.stop(); song = null; showLibrary = true; selection.edit().remove("selected").apply() }
                    } catch (failure: Exception) { message = "删除失败：${failure.message}" }
                    finally { busy = false }
                }
            }) { Text("删除副本") } }, dismissButton = { TextButton({ deleting = null }) { Text("取消") } }) }
        if (showSettings && song != null) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("跟练配置") },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tracks = player.score?.let(GpLibrary::guitarTracks).orEmpty()
                GpChoice("吉他轨", tracks.map { it.name }, tracks.indexOf(track)) { index ->
                    player.selectTrack(tracks[index]); change(preferences.copy(track = tracks[index].index.toInt()))
                }
                val labels = visits.indices.map { "${it + 1} · ${projection?.label(it)}" }
                GpChoice("起点", labels, first) { change(preferences.copy(first = it, last = maxOf(it, last))) }
                GpChoice("终点", labels, last) { change(preferences.copy(last = maxOf(first, it))) }
                TextButton({ change(preferences.copy(first = 0, last = -1)) }) { Text("恢复整曲") }
                Text("原速 ${preferences.speed}%")
                var speedDraft by remember(preferences.speed) { mutableFloatStateOf(preferences.speed.toFloat()) }
                Slider(speedDraft,{speedDraft=it},onValueChangeFinished={
                    change(preferences.copy(speed=speedDraft.toInt()),false);player.setSpeed(speedDraft.toInt()/100.0)
                },valueRange=25f..200f)
                GpChoice("循环次数", listOf("持续循环") + (1..16).map { "$it 次" }, preferences.repeats) { change(preferences.copy(repeats = it)) }
                GpToggle("只听所选吉他轨", preferences.solo) { change(preferences.copy(solo = it), false); player.solo = it }
                GpToggle("开始前预备拍", preferences.countIn) { change(preferences.copy(countIn = it)) }
                GpToggle("节拍器", preferences.metronome) { change(preferences.copy(metronome = it), false); player.click = it }
                Text("起止点按引擎实际演奏顺序选择；遇到反复时，每次经过分别列出。", style = MaterialTheme.typography.bodySmall, color = Muted)
            } }, confirmButton = { TextButton({ showSettings = false }) { Text("完成") } })
    }
}

@Composable
private fun GpChoice(label: String, choices: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = choices.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label：${choices.getOrNull(selected).orEmpty()}", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            choices.forEachIndexed { index, name -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(index) }) }
        }
    }
}
@Composable
private fun GpToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f)); Switch(checked, onCheckedChange = onChange)
    }
}
