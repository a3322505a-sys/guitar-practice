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
                engine.tickPosition = 0.0
                samplesPlayed.trigger(44100.0)
                assertEquals(target * 960 / 60.0, engine.tickPosition, 1.000001)
            }
        } finally { engine.destroy() }
    }
}
