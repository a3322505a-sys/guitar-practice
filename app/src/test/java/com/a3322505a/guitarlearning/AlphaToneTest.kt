@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning

import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.model.DynamicValue
import alphaTab.synth.AlphaSynthAudioExporter
import alphaTab.synth.AudioExportOptions
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.music.score.PracticeKey
import com.a3322505a.guitarlearning.practice.PracticeExtensions
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
                       tuning: List<Int> = listOf(64,59,55,50,45,40),
                       music: Double = 1.0, clickMix: Double = 1.0): FloatArray {
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
            metronomeVolume = if (click) AlphaTone.CLICK * clickMix else 0.0
        })
        exporter.loadMidiFile(midi)
        exporter.loadSoundFont(Uint8Array(font.asUByteArray()))
        for (channel in 0..1) exporter.channelSetMixVolume(channel.toDouble(),
            if (guitar) AlphaTone.trackVolume(score.tracks.first().playbackInfo.volume,music) else 0.0)
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
    @Test fun twoGainRoutesMuteIndependentlyInActualSynthesis() {
        val notes=listOf(Position(6,0),Position(3,0))
        assertArrayEquals(render(notes,true,guitar=false),render(notes,true,music=0.0),1e-7f)
        val full=render(notes,false)
        val half=render(notes,true,music=.5,clickMix=0.0)
        assertEquals(.5,peak(half)/peak(full),.001)
        assertTrue(render(notes,true,music=0.0,clickMix=0.0).all { abs(it)<1e-7f })
    }
    @Test fun bothAuthoredLeadDemosFadeOnlyLeadAndKeepAnAudibleBackingTrack() {
        val reports=mutableListOf<String>()
        for(id in listOf("local.demo-c","local.demo-capo","local.demo-rock")) {
            val plan=SongTestLibrary.repository.resolve(PracticeKey(id,"SOLO-original").encoded).sequence.performance
            val score=AlphaScoreAdapter.fromPlan(plan)
            val (midi,_)=EngineProjection.generate(score)
            val sourceTracks=plan.score.tracks.filter { it.id in plan.practice.mixTrackIds }
            fun sample(round: Int): FloatArray {
                val exporter=AlphaSynthAudioExporter(AudioExportOptions().apply { sampleRate=44100.0;masterVolume=AlphaTone.MASTER;metronomeVolume=0.0 })
                exporter.loadMidiFile(midi);exporter.loadSoundFont(Uint8Array(font.asUByteArray()))
                score.tracks.forEach { track ->
                    val lead=sourceTracks[track.index.toInt()].id in plan.practice.lesson!!.leadTrackIds
                    val gain=AlphaTone.trackVolume(track.playbackInfo.volume,1.0,if(lead) PracticeExtensions.leadGain(round) else 1.0)
                    exporter.channelSetMixVolume(track.playbackInfo.primaryChannel,gain)
                    exporter.channelSetMixVolume(track.playbackInfo.secondaryChannel,gain)
                }
                exporter.setup()
                return exporter.render(750.0)!!.samples!!.data.toList().toFloatArray()
            }
            val full=sample(0);val low=sample(1);val backing=sample(2)
            val delta=full.indices.map { full[it]-backing[it] }
            assertTrue("$id backing was muted",peak(backing)>.0001)
            assertTrue("$id lead had no sound",delta.maxOf { abs(it) }>.0001)
            val error=full.indices.maxOf { abs((low[it]-backing[it])-.3f*delta[it]) }
            val deltaPeak=delta.maxOf { abs(it) }
            reports+="$id fullPeak=${peak(full)} lowPeak=${peak(low)} backingPeak=${peak(backing)} gainError=$error relativeError=${error/deltaPeak}"
            File(output,"lead-fade.txt").writeText(reports.joinToString("\n"))
            // The SDK's palm-mute voice processing is not exactly linear at every sample.
            assertTrue(reports.last(),error/deltaPeak<.01)
            wav("$id-lead-100",full);wav("$id-lead-30",low);wav("$id-lead-0-backing",backing)
        }
        File(output,"lead-fade.txt").writeText(reports.joinToString("\n"))
    }
}
