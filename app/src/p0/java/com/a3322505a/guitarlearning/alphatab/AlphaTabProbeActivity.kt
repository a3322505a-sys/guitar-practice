@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.AlphaTabView
import alphaTab.PlayerMode
import alphaTab.Settings
import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.importer.ScoreLoader
import alphaTab.model.Track
import alphaTab.synth.PlayerState
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import org.json.JSONObject

/** P0 only. No access to the production song repository, settings or Player. */
@SuppressLint("SetTextI18n")
class AlphaTabProbeActivity : ComponentActivity() {
    private lateinit var notation: AlphaTabView
    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var trackChoice: Spinner
    private lateinit var play: Button
    private val loader = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var tracks = emptyList<Track>()
    private var disposed = false
    private var renderCount = 0
    private var positionCount = 0
    private var lastSecond = -1
    private var sourceName = ""
    private var previewEnabled = false

    private val open = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) load("本地 GP") {
            checkNotNull(contentResolver.openInputStream(uri)) { "无法打开文件" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewEnabled = intent.getBooleanExtra("p0Preview", false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xfffaf6f0.toInt()) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        setContentView(root)
        val files = row(root)
        button(files, "打开 GP") { open.launch(arrayOf("*/*")) }
        val samples = listOf("简单 GP5" to "simple.gp5", "多声部 GP" to "complex.gp", "GPX" to "sample.gpx", "轻量多声部 GP" to "polyphonic.gp")
        samples.forEach { (label, path) ->
            button(files, label) { fixture(label, path) }
        }
        status = TextView(this).apply { textSize = 14f; setPadding(dp(8), 0, dp(8), 0); text = "P0 · alphaTab 1.8.4 · 原曲库和教学入口保留" }
        root.addView(status)
        trackChoice = Spinner(this)
        root.addView(trackChoice, LinearLayout.LayoutParams(-1, dp(48)))
        val controls = row(root)
        play = button(controls, "播放") { notation.api.playPause() }.apply { isEnabled = false }
        button(controls, "重来") { notation.api.stop() }
        val speed = Spinner(this)
        val speeds = listOf(50, 75, 100, 125)
        speed.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, speeds.map { "$it%" })
        speed.setSelection(2)
        controls.addView(speed, LinearLayout.LayoutParams(dp(95), dp(48)))
        speed.onItemSelectedListener = selected { index -> if (::notation.isInitialized) notation.api.playbackSpeed = speeds[index] / 100.0 }
        CheckBox(this).apply {
            text = "预备拍"
            setOnCheckedChangeListener { _, checked -> notation.api.countInVolume = if (checked) 0.7 else 0.0 }
            controls.addView(this)
        }
        CheckBox(this).apply {
            text = "节拍器"
            setOnCheckedChangeListener { _, checked -> notation.api.metronomeVolume = if (checked) 0.5 else 0.0 }
            controls.addView(this)
        }
        CheckBox(this).apply {
            text = "循环"
            setOnCheckedChangeListener { _, checked -> notation.api.isLooping = checked }
            controls.addView(this)
        }
        progress = TextView(this).apply { textSize = 13f; setPadding(dp(8), 0, dp(8), 0); text = "音色来自 APK；可点谱面选段。P0 选区仅用于调查，不承诺反复边界。" }
        root.addView(progress)
        notation = AlphaTabView(this, null).apply {
            settings = probeSettings()
            // Keep fret numbers readable through the cursor.
            barCursorFillColor = 0x18e890ac
            beatCursorFillColor = 0x32e890ac
        }
        root.addView(notation, LinearLayout.LayoutParams(-1, 0, 1f))
        notation.api.error.on { error -> runOnUiThread { status.text = "引擎失败：${error.message}"; record("error", error.toString()) } }
        notation.api.renderFinished.on {
            renderCount++
            record("renderFinished", "count=$renderCount width=${it.width} height=${it.height}")
        }
        notation.api.playerReady.on {
            runOnUiThread { play.isEnabled = true }
            record("playerReady", "offlineSoundFont=true")
        }
        notation.api.playerStateChanged.on { event ->
            runOnUiThread { play.text = if (event.state == PlayerState.Playing) "暂停" else "播放" }
            record("state", event.state.toString())
        }
        notation.api.playerPositionChanged.on { event ->
            positionCount++
            val second = (event.currentTime / 1000).toInt()
            if (second != lastSecond) {
                lastSecond = second
                runOnUiThread { progress.text = "${second}s / ${(event.endTime / 1000).toInt()}s · tick ${event.currentTick.toInt()} · 进度回调 $positionCount" }
                record("position", "tick=${event.currentTick} ms=${event.currentTime} callbacks=$positionCount")
            }
        }
        notation.api.playedBeatChanged.on { beat ->
            // alphaTab string 1 = lowest string; the teaching UI uses 1 = highest.
            val stringCount = beat.voice.bar.staff.tuning.length.toInt()
            val positions = beat.notes.joinToString { "${stringCount + 1 - it.string.toInt()}弦${it.fret.toInt()}品" }
            record("beat", "bar=${beat.voice.bar.index + 1} voice=${beat.voice.index + 1} $positions")
        }
        trackChoice.onItemSelectedListener = selected { index ->
            val chosen = tracks.getOrNull(index) ?: return@selected
            // Spinner initialization posts a selection callback after load already rendered it.
            if (notation.tracks?.firstOrNull() === chosen) return@selected
            notation.api.stop()
            notation.tracks = listOf(chosen)
            for (track in chosen.score.tracks) notation.api.changeTrackMute(alphaTab.collections.List(track), track !== chosen)
            record("track", "index=${chosen.index} name=${chosen.name}")
        }
        // Local fixtures are prepared explicitly by scripts/prepare-alphatab-p0.py.
        val requested = intent.getStringExtra("p0Fixture") ?: "simple.gp5"
        val sample = samples.firstOrNull { it.second == requested }
        if (sample != null && assets.list("p0").orEmpty().contains(requested)) fixture(sample.first, sample.second)
        else {
            status.text = "P0 样本不存在：$requested"
            record("loadError", "Unknown P0 fixture")
        }
    }

    private fun fixture(name: String, path: String) {
        if (previewEnabled) {
            // A display window only: the original source and alphaTab playback model remain intact.
            notation.settings = probeSettings().apply {
                // AlphaTabView recycles offscreen bitmaps and requests them again on scroll.
                core.enableLazyLoading = true
                display.startBar = when (path) { "complex.gp" -> 21.0; "polyphonic.gp" -> 52.0; else -> 1.0 }
                display.barCount = 6.0
                display.barCountPerPartial = 6.0
            }
            record("preview", "asset=$path startBar=${notation.settings.display.startBar} bars=6")
        }
        val entries = runCatching { JSONObject(assets.open("p0/manifest.json").bufferedReader().use { it.readText() }).getJSONArray("fixtures") }.getOrNull()
        val track = entries?.let { array -> (0 until array.length()).map { array.getJSONObject(it) }.firstOrNull { it.getString("asset") == path }?.optInt("selectedTrack") }
        load(name, track) { assets.open("p0/$path") }
    }

    private fun load(name: String, preferredTrack: Int? = null, input: () -> InputStream) {
        val request = ++generation
        notation.api.stop()
        play.isEnabled = false
        status.text = "正在读取 $name…"
        loader.execute {
            try {
                val started = System.nanoTime()
                val bytes = input().use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val length = stream.read(buffer)
                        if (length < 0) break
                        require(output.size() + length <= MAX_FILE_BYTES) { "P0 文件上限 96 MiB" }
                        output.write(buffer, 0, length)
                        check(!Thread.currentThread().isInterrupted) { "读取已取消" }
                    }
                    output.toByteArray()
                }
                val score = ScoreLoader.loadScoreFromBytes(Uint8Array(bytes.asUByteArray()), probeSettings())
                val guitars = score.tracks.toList().filter { track -> track.playbackInfo.program.toInt() in 24..31 && track.staves.any { !it.isPercussion && it.tuning.length >= 6 } }
                require(guitars.isNotEmpty()) { "未发现至少六弦的吉他轨；此 P0 入口暂只验证吉他" }
                for (track in guitars) for (staff in track.staves) for (bar in staff.bars) for (voice in bar.voices) for (beat in voice.beats) {
                    require(beat.notes.none { it.string > 0 && it.fret < 0 && !it.isDead }) {
                        "原谱 ${track.name} 第 ${bar.index.toInt() + 1} 小节存在负品位，请先核对源谱"
                    }
                }
                val elapsed = (System.nanoTime() - started) / 1_000_000
                runOnUiThread {
                    if (disposed || request != generation) return@runOnUiThread
                    sourceName = name
                    tracks = guitars
                    renderCount = 0; positionCount = 0; lastSecond = -1
                    status.text = "${score.title} · ${score.masterBars.length.toInt()} 小节 · ${guitars.size} 吉他轨 · 读取 ${elapsed}ms"
                    trackChoice.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, guitars.map { it.name })
                    val selected = guitars.indexOfFirst { it.index.toInt() == preferredTrack }.coerceAtLeast(0)
                    trackChoice.setSelection(selected)
                    notation.tracks = listOf(guitars[selected])
                    for (track in score.tracks) notation.api.changeTrackMute(alphaTab.collections.List(track), track !== guitars[selected])
                    play.isEnabled = notation.api.isReadyForPlayback
                    record("loaded", "bars=${score.masterBars.length} tracks=${score.tracks.length} elapsedMs=$elapsed")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!disposed && request == generation) {
                        status.text = "读取失败：${error.message}（原曲库保留）"
                        play.isEnabled = false
                        record("loadError", error.toString())
                    }
                }
            }
        }
    }

    override fun onStop() {
        if (::notation.isInitialized) notation.api.pause()
        super.onStop()
    }

    override fun onDestroy() {
        disposed = true; generation++
        loader.shutdownNow()
        // AlphaTabView.onDetachedFromWindow owns api.destroy; do not double release.
        super.onDestroy()
    }

    private fun row(root: LinearLayout): LinearLayout {
        val scroll = HorizontalScrollView(this)
        val row = LinearLayout(this)
        scroll.addView(row)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(52)))
        return row
    }
    private fun button(row: LinearLayout, label: String, action: () -> Unit) = Button(this).apply {
        text = label; minHeight = dp(48); setOnClickListener { action() }; row.addView(this)
    }
    private fun selected(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
    private fun record(event: String, detail: String) = Log.i("AlphaTabP0", "$event source=$sourceName $detail")
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_FILE_BYTES = 96 * 1024 * 1024
        fun probeSettings() = Settings().apply {
            core.engine = "skia"
            player.playerMode = PlayerMode.EnabledSynthesizer
            player.enableCursor = true
            player.enableUserInteraction = true
            display.barCountPerPartial = 4.0
        }
    }
}
