@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.IEventEmitter
import alphaTab.IEventEmitterOfT
import alphaTab.midi.*
import alphaTab.synth.AlphaSynth
import alphaTab.synth.ISynthOutput
import com.a3322505a.guitarlearning.practice.TempoControl
import com.a3322505a.guitarlearning.practice.Sequence
import com.a3322505a.guitarlearning.practice.Event
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.music.score.Tempo
import com.a3322505a.guitarlearning.SongTestLibrary
import com.a3322505a.guitarlearning.assets.song.SongReader
import com.a3322505a.guitarlearning.practice.compile.PracticeCompiler
import com.a3322505a.guitarlearning.practice.compile.PerformancePlan
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/** Exercise the shipped 1.8.4 synth, not a duplicate rate formula. No device audio output. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackTempoTest {
    private class Signal : IEventEmitter {
        private val listeners=mutableListOf<() -> Unit>()
        override fun on(value: () -> Unit): () -> Unit { listeners+=value; return { off(value) } }
        override fun off(value: () -> Unit) { listeners-=value }
        fun trigger() { listeners.toList().forEach { it() } }
    }
    private class SignalOfT<T> : IEventEmitterOfT<T> {
        private val listeners=mutableListOf<(T) -> Unit>()
        override fun on(value: (T) -> Unit): () -> Unit { listeners+=value; return { off(value) } }
        override fun off(value: (T) -> Unit) { listeners-=value }
        fun trigger(value: T) { listeners.toList().forEach { it(value) } }
    }
    private val ready = Signal()
    private val sampleRequest = Signal()
    private val samplesPlayed = SignalOfT<Double>()
    private fun synth(): AlphaSynth {
        val output = Proxy.newProxyInstance(ISynthOutput::class.java.classLoader, arrayOf(ISynthOutput::class.java)) { _, method, _ ->
            when (method.name) {
                "getSampleRate" -> 44100.0
                "getReady" -> ready
                "getSampleRequest" -> sampleRequest
                "getSamplesPlayed" -> samplesPlayed
                "open" -> { ready.trigger(); null }
                else -> null
            }
        } as ISynthOutput
        return AlphaSynth(output, 100.0)
    }
    @Test fun actualSynthAccepts20BpmBelowFormer25PercentAndPreservesTempoMap() {
        val engine = synth()
        try {
            val sequence = Sequence((0 until 8).map { Event(it * 12, 12, listOf(Position(1, 5))) }, 96, "tempo")
            for ((source, target) in listOf(30.0 to 60, 30.0 to 90, 30.0 to 120,
                60.0 to 20, 132.0 to 20, 180.0 to 20, 240.0 to 20, 20.0 to 240)) {
                val score = AlphaScoreAdapter.fromPlan(sequence.performance.copy(tempos = listOf(
                    Tempo(Q.ZERO, Q.ONE, source), Tempo(Q(2), Q.ONE, source * 1.5))))
                val ratio = TempoControl.ratio(target, source)
                val speed = TempoControl.engineSpeed(ratio)
                val midi = AlphaNotation.withPlaybackTempo(score, true, target, ratio / speed) {
                    EngineProjection.generate(score).first
                }
                // Generating playback MIDI leaves the printed source tempo untouched.
                assertEquals(source, score.masterBars.first().tempoAutomations.first().value, 1e-9)
                engine.loadMidiFile(midi)
                engine.playbackSpeed = speed
                assertEquals(speed, engine.playbackSpeed, 1e-12)
                engine.tickPosition = 960.0
                assertEquals("source=$source target=$target", 60000.0 / target, engine.timePosition, .01)
                engine.tickPosition = 2880.0
                assertEquals(2 * 60000.0 / target + 60000.0 / (target * 1.5), engine.timePosition, .01)
                val timeline = PlaybackTimeline(CursorTempoMap(midi), 0.0, 8 * 960.0, speed)
                assertEquals(2 * 60000.0 / target + 6 * 60000.0 / (target * 1.5), timeline.durationMillis, 2.0)
                engine.tickPosition = 0.0
                samplesPlayed.trigger(11025.0)
                assertEquals(target * 960 / 60.0 / 4, engine.tickPosition, 1.000001)
            }
        } finally { engine.destroy() }
    }
    // Independent score integration: visits already include written repeats and
    // segment boundaries. Never derive expected seconds from MIDI or UI output.
    private fun expectedMillis(plan: PerformancePlan, first: Double, end: Double, ratio: Double): Double {
        val cuts = (listOf(first, end) + plan.tempos.map { it.at.value() }.filter { it > first && it < end }).distinct().sorted()
        return cuts.zipWithNext().sumOf { (a, b) ->
            val tempo = plan.tempos.last { it.at.value() <= a }
            (b - a) * 60000.0 / (tempo.bpm * tempo.beatUnit.value() * ratio)
        }
    }
    private fun auditTimeline(plan: PerformancePlan, targets: List<Int>) {
        val score = AlphaScoreAdapter.fromPlan(plan)
        val (sourceMidi, sourceProjection) = EngineProjection.generate(score)
        val sourceMap = CursorTempoMap(sourceMidi)
        val reference = plan.tempos.first().let { it.bpm * it.beatUnit.value() }
        val engine = synth()
        try {
            for (target in targets) {
                val ratio = TempoControl.ratio(target, reference)
                val rate = TempoControl.engineSpeed(ratio)
                val (midi, projection) = AlphaNotation.withPlaybackTempo(score, true, target, ratio / rate) {
                    EngineProjection.generate(score)
                }
                engine.loadMidiFile(midi)
                engine.playbackSpeed = rate
                val map = CursorTempoMap(midi)
                val ranges = listOf(0 to projection.visits.lastIndex,
                    1 to minOf(3, projection.visits.lastIndex))
                for ((first, last) in ranges) {
                    val a = projection.visits[first].start
                    val b = projection.visits[last].end
                    val expected = expectedMillis(plan, a / 960, b / 960, ratio)
                    val timeline = PlaybackTimeline(map, a, b, rate)
                    val preview = PlaybackTimeline(sourceMap, sourceProjection.visits[first].start,
                        sourceProjection.visits[last].end, ratio)
                    val label = "${plan.score.documentId}/${plan.practice.id} target=$target range=$first..$last"
                    assertEquals(label, expected, timeline.durationMillis, 2.0)
                    assertEquals("idle preview: $label", expected, preview.durationMillis, 2.0)
                    engine.playbackRange = alphaTab.synth.PlaybackRange().apply { startTick = a; endTick = b }
                    engine.tickPosition = a
                    assertEquals("entry: $label", 0.0, timeline.elapsedMillis(engine.timePosition), 2.0)
                    val middle = (a + b) / 2
                    engine.tickPosition = middle
                    assertEquals("seek/pause: $label", expectedMillis(plan, a / 960, middle / 960, ratio),
                        timeline.elapsedMillis(engine.timePosition), 2.0)
                    engine.tickPosition = b
                    assertEquals("end: $label", expected, timeline.elapsedMillis(engine.timePosition), 2.0)
                    engine.tickPosition = a // native loop wraps to the selected range
                    assertEquals("loop: $label", 0.0, timeline.elapsedMillis(engine.timePosition), 2.0)
                    println("TIME_AUDIT $label quarters=${(b-a)/960} expectedMs=$expected actualMs=${timeline.durationMillis}")
                }
                engine.playbackRange = null
                val total = expectedMillis(plan, 0.0, plan.length.value(), ratio)
                engine.tickPosition = projection.visits.last().end
                assertEquals("native duration already scaled, target=$target", total, engine.timePosition, 2.0)
                assertEquals(reference, score.masterBars.first().tempoAutomations.first().value, 1e-9)
            }
        } finally { engine.destroy() }
    }
    @Test fun importedMozart80BarsHasCorrectNativeAndStageTimeAt30_60_90Bpm() {
        val song = SongReader(SongTestLibrary.schema()).read(File("src/test/resources/mozart-timeline-r10.gpractice").readBytes())
        val plan = PracticeCompiler.compile(song.score, song.practices.first { it.id == "K265-THEME-TO-VAR1" })
        assertEquals(80, plan.visits.size)
        assertEquals(160.0, plan.length.value(), 1e-9)
        auditTimeline(plan, listOf(30, 60, 90))
    }
    @Test fun sampledPackagesKeepCorrectTimeAcrossTempoChangesRestsAndPolyphony() {
        val samples = listOf("doraemon-no-uta-v4" to "doraemon-full", "isana-v1" to "isana-complete",
            "tsukiusagi-ensemble-v3" to "complete", "your-trip-v3" to "YT-GROOVE")
        samples.forEach { (file, id) ->
            val song = SongReader(SongTestLibrary.schema()).read(File(SongTestLibrary.assets, "$file.gpractice").readBytes())
            val plan = PracticeCompiler.compile(song.score, song.practices.first { it.id == id })
            auditTimeline(plan, listOf(20, 60, 90, 240))
        }
    }

}
