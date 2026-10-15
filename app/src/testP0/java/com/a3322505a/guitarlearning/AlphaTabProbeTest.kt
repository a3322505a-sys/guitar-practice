@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning

import alphaTab.Settings
import alphaTab.collections.DoubleList
import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.importer.ScoreLoader
import alphaTab.midi.AlphaSynthMidiFileHandler
import alphaTab.midi.MidiFile
import alphaTab.midi.MidiFileGenerator
import alphaTab.midi.NoteOnEvent
import alphaTab.model.Score
import alphaTab.rendering.ScoreRenderer
import alphaTab.synth.AlphaSynthAudioExporter
import alphaTab.synth.AudioExportOptions
import alphaTab.synth.PlaybackRange
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/** These are host probes of the actual Android artifact, not real-device acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlphaTabProbeTest {
    private val project = File(checkNotNull(System.getProperty("user.dir"))).let { if (it.name == "app") it.parentFile else it }
    private val manifest = File(project, "work/evidence/desktop-gp-manifest.json")
    private val evidence = File(project, "work/evidence/host").apply { mkdirs() }
    private fun settings() = Settings()
    private fun read(file: File) = ScoreLoader.loadScoreFromBytes(Uint8Array(file.readBytes().asUByteArray()), settings())
    private fun inventory(): JSONObject {
        assumeTrue("Run scripts/prepare-alphatab-p0.py for the private desktop probes", manifest.exists())
        return JSONObject(manifest.readText())
    }
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun guitarDigest(score: Score, indices: Set<Int>, output: File? = null): Pair<String, Int> {
        var count = 0
        val text = buildString {
            for (track in score.tracks) if (track.index.toInt() in indices) {
                for (staff in track.staves) for (bar in staff.bars) for (voice in bar.voices) for (beat in voice.beats) {
                    for (note in beat.notes.filter { it.string > 0 }.sortedWith(compareBy({ it.string }, { it.fret }))) {
                        append("${track.index.toInt()},${staff.index.toInt()},${bar.index.toInt()},${voice.index.toInt()},${beat.index.toInt()},${note.string.toInt()},${note.fret.toInt()};")
                        count++
                    }
                }
            }
        }
        output?.writeText(text.replace(";", ";\n"))
        return digest(text) to count
    }

    @Test fun auditDesktopFormatsAgainstIndependentGpif() {
        val items = inventory().getJSONArray("files")
        val results = JSONArray()
        var parsed = 0
        var matched = 0
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val result = JSONObject().put("path", item.getString("relativePath")).put("extension", item.getString("extension"))
            val started = System.nanoTime()
            try {
                val score = read(File(item.getString("path")))
                parsed++
                result.put("parsed", true).put("bars", score.masterBars.length.toInt()).put("tracks", score.tracks.length.toInt())
                if (item.has("reference")) {
                    val ref = item.getJSONObject("reference")
                    val indices = ref.getJSONArray("guitarTrackIndices").let { array -> (0 until array.length()).map { array.getInt(it) }.toSet() }
                    val (actualHash, count) = guitarDigest(score, indices)
                    val correct = actualHash == ref.getString("writtenStringFretSha256") && score.masterBars.length.toInt() == ref.getInt("bars")
                    result.put("sourceStringFretMatch", correct).put("sourceNoteCount", ref.getInt("writtenStringFretCount"))
                        .put("loadedNoteCount", count).put("actualStringFretSha256", actualHash)
                    if (correct) matched++
                    else guitarDigest(score, indices, File(evidence, "${File(item.getString("path")).name}-actual.txt"))
                }
                val midi = MidiFile()
                MidiFileGenerator(score, settings(), AlphaSynthMidiFileHandler(midi)).generate()
                result.put("midiEvents", midi.events.length.toInt())
            } catch (error: Throwable) {
                result.put("parsed", false).put("error", error.toString())
            }
            result.put("elapsedMs", (System.nanoTime() - started) / 1_000_000)
            results.put(result)
        }
        File(evidence, "desktop-formats.json").writeText(JSONObject().put("alphaTab", "1.8.4").put("hostOnly", true)
            .put("parsed", parsed).put("sourceMatched", matched).put("results", results).toString(2))
        assertTrue("No actual GP parsed; see desktop-formats.json", parsed > 0)
        assertTrue("No independent GPIF reference matched; see desktop-formats.json", matched > 0)
    }

    @Test fun simpleAndPolyphonicFixturesRenderAndSynthesizeOffline() {
        val fixtures = inventory().getJSONArray("fixtures")
        val report = JSONArray()
        for (index in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(index)
            val asset = fixture.getString("asset")
            val score = read(File(fixture.getString("path")))
            val trackIndex = fixture.getInt("selectedTrack")
            val track = score.tracks[trackIndex]
            val ref = fixture.optJSONObject("reference")
            if (ref != null) {
                val ids = ref.getJSONArray("guitarTrackIndices").let { array -> (0 until array.length()).map { array.getInt(it) }.toSet() }
                val (hash, _) = guitarDigest(score, ids)
                assertEquals("Written guitar positions changed in $asset", ref.getString("writtenStringFretSha256"), hash)
            }
            val polyBars = track.staves.toList().flatMap { it.bars.toList() }.filter { bar -> bar.voices.count { voice -> voice.beats.any { it.notes.length > 0 } } > 1 }
            if (asset == "complex.gp" || asset == "polyphonic.gp") assertTrue("Polyphonic fixture must contain actual multi-voice guitar", polyBars.isNotEmpty())
            val renderSettings = settings().apply {
                core.engine = "svg"
                core.enableLazyLoading = false
                display.startBar = if (polyBars.isNotEmpty()) (polyBars.first().index - 1).coerceAtLeast(0.0) + 1 else 4.0
                display.barCount = 6.0
                display.barCountPerPartial = 6.0
            }
            val rendered = mutableListOf<String>()
            var renderFailure: Throwable? = null
            val renderer = ScoreRenderer(renderSettings)
            renderer.width = 1100.0
            renderer.error.on { renderFailure = it }
            renderer.partialRenderFinished.on { event -> (event.renderResult as? String)?.let(rendered::add) }
            renderer.renderScore(score, DoubleList(trackIndex.toDouble()), null)
            renderFailure?.let { throw AssertionError("Rendering $asset failed", it) }
            assertTrue("No SVG produced for $asset", rendered.isNotEmpty())
            rendered.forEachIndexed { page, svg -> File(evidence, "$asset-$page.svg").writeText(svg) }
            renderer.destroy()

            val midi = MidiFile()
            MidiFileGenerator(score, settings(), AlphaSynthMidiFileHandler(midi)).generate()
            val font = RuntimeEnvironment.getApplication().assets.open("sonivox.sf2").use { it.readBytes() }
            val exporter = AlphaSynthAudioExporter(AudioExportOptions().apply { sampleRate = 44100.0 })
            exporter.loadMidiFile(midi)
            exporter.loadSoundFont(Uint8Array(font.asUByteArray()))
            val channels = setOf(track.playbackInfo.primaryChannel.toInt(), track.playbackInfo.secondaryChannel.toInt())
            for (channel in 0..15) exporter.channelSetMixVolume(channel.toDouble(), if (channel in channels) 1.0 else 0.0)
            val firstSound = midi.events.filterIsInstance<NoteOnEvent>().first { it.noteVelocity > 0 && it.channel.toInt() in channels }.tick
            exporter.limitExport(PlaybackRange().apply { startTick = firstSound; endTick = firstSound + 960 * 16 })
            exporter.setup()
            var peak = 0.0
            var squared = 0.0
            var sampleCount = 0
            val pcm = ByteArrayOutputStream()
            for (chunkIndex in 0 until 24) {
                val chunk = exporter.render(500.0) ?: break
                for (sample in chunk.samples.data) {
                    assertTrue("Invalid PCM in $asset at sample $sampleCount chunk $chunkIndex value $sample", sample.isFinite())
                    peak = maxOf(peak, abs(sample.toDouble()))
                    squared += sample * sample
                    sampleCount++
                    val value = (sample * 32767).toInt().coerceIn(-32767, 32767)
                    pcm.write(value and 255); pcm.write((value shr 8) and 255)
                }
            }
            assertTrue("Offline synthesis is silent for $asset", peak > 0.0001)
            val data = pcm.toByteArray()
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVEfmt ".toByteArray()); putInt(16)
                putShort(1); putShort(2); putInt(44100); putInt(44100 * 4); putShort(4); putShort(16)
                put("data".toByteArray()); putInt(data.size)
            }.array()
            File(evidence, "$asset.wav").outputStream().use { it.write(header); it.write(data) }
            report.put(JSONObject().put("asset", asset).put("selectedTrack", trackIndex).put("polyphonicBars", JSONArray(polyBars.map { it.index.toInt() + 1 }))
                .put("svgParts", rendered.size).put("pcmSamples", sampleCount).put("peak", peak).put("rms", sqrt(squared / sampleCount))
                .put("soundFontSha256", MessageDigest.getInstance("SHA-256").digest(font).joinToString("") { "%02x".format(it) }))
        }
        File(evidence, "fixtures.json").writeText(JSONObject().put("hostOnly", true).put("fixtures", report).toString(2))
    }
}
