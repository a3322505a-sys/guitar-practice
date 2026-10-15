@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning

import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.model.DynamicValue
import alphaTab.synth.AlphaSynthAudioExporter
import alphaTab.synth.AudioExportOptions
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.Event
import com.a3322505a.guitarlearning.practice.Sequence
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Actual 1.8.4 synthesis of the packaged font. Host checks, not device listening. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AlphaToneTest {
    private val font by lazy { RuntimeEnvironment.getApplication().assets.open("sonivox.sf2").use { it.readBytes() } }
    private val output = File("build/reports/alpha-tone").apply { mkdirs() }
    private fun render(positions: List<Position>, click: Boolean, guitar: Boolean = true,
                       strum: Boolean = false, program: Int = 24,
                       tuning: List<Int> = listOf(64,59,55,50,45,40)): FloatArray {
        val sequence = Sequence(listOf(Event(0, 12, positions, strum = strum)), 12, "音色验证", openStringMidi = tuning)
        val score = AlphaScoreAdapter.fromSequence(sequence)
        score.tracks.first().apply {
            playbackInfo.program = program.toDouble()
            staves.forEach { staff -> staff.bars.forEach { bar -> bar.voices.forEach { voice ->
                voice.beats.forEach { beat -> beat.dynamics = DynamicValue.FFF }
            } } }
        }
        val (midi, _) = EngineProjection.generate(score)
        val exporter = AlphaSynthAudioExporter(AudioExportOptions().apply {
            sampleRate = 44100.0; masterVolume = AlphaTone.MASTER
            metronomeVolume = if (click) AlphaTone.CLICK else 0.0
        })
        exporter.loadMidiFile(midi)
        exporter.loadSoundFont(Uint8Array(font.asUByteArray()))
        for (channel in 0..1) exporter.channelSetMixVolume(channel.toDouble(),
            if (guitar) AlphaTone.trackVolume(score.tracks.first().playbackInfo.volume) else 0.0)
        exporter.setup()
        val samples = mutableListOf<Float>()
        repeat(3) { exporter.render(250.0)?.samples?.data?.let { samples.addAll(it.toList()) } }
        assertTrue(samples.all { it.isFinite() })
        return samples.toFloatArray()
    }
    private fun peak(pcm: FloatArray) = pcm.maxOf { abs(it.toDouble()) }
    private fun rms(pcm: FloatArray, milliseconds: Int = 45): Double {
        val head = pcm.take(milliseconds * 44100 * 2 / 1000)
        return sqrt(head.sumOf { it.toDouble() * it } / head.size)
    }
    private fun wav(name: String, pcm: FloatArray) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size * 2); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(2); putInt(44100); putInt(176400); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(pcm.size * 2)
        }.array()
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort((it * 32767).toInt().coerceIn(-32767, 32767).toShort()) }
        File(output, "$name.wav").writeBytes(header + data.array())
    }
    @Test fun nylonKeyRangesAndOtherGuitarProgramsStillSound() {
        // Low/high transposition, all 22 original roots, and unchanged GM guitars.
        val roots = listOf(40,43,45,47,48,50,52,53,55,57,59,60,62,64,65,67,69,71,72,74,76,77,96)
        roots.forEach { midi ->
            val position = if (midi >= 64) Position(1, midi - 64) else Position(6, midi - 40)
            assertTrue("Missing nylon MIDI $midi", peak(render(listOf(position), false)) > .0001)
        }
        assertTrue("Missing low transposition", peak(render(listOf(Position(6,0)), false,
            tuning = listOf(64,59,55,50,45,28))) > .0001)
        (25..31).forEach { program ->
            assertTrue("Missing original GM $program", peak(render(listOf(Position(1, 0)), false, program = program)) > .0001)
        }
    }
    @Test fun softClickLeadsSixStringFffChordsWithoutClipping() {
        val chord = listOf(Position(6,0),Position(5,2),Position(4,2),Position(3,0),Position(2,0),Position(1,0))
        val click = render(chord, true, guitar = false)
        val guitar = render(chord, false)
        val mixed = render(chord, true)
        val strum = render(chord, true, strum = true)
        val differenceDb = 20 * log10(rms(click) / rms(guitar))
        val report = "clickPeak=${peak(click)}\nchordPeak=${peak(guitar)}\nmixedPeak=${peak(mixed)}\n" +
            "strumPeak=${peak(strum)}\nclickLeadDb45ms=$differenceDb\n"
        File(output, "mix.txt").writeText(report)
        wav("click", click); wav("nylon-chord", guitar); wav("mixed-chord", mixed); wav("mixed-strum", strum)
        assertTrue(report, differenceDb >= 6.0)
        assertTrue(report, peak(mixed) < .85 && peak(strum) < .85)
        assertTrue("Click must finish within 60 ms", click.drop(44100 * 2 * 60 / 1000).take(44100 / 5).all { abs(it) < .0001 })
    }
}
