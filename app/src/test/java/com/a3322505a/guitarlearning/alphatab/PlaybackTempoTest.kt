@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.EventEmitter
import alphaTab.EventEmitterOfT
import alphaTab.midi.*
import alphaTab.synth.AlphaSynth
import alphaTab.synth.ISynthOutput
import com.a3322505a.guitarlearning.practice.TempoControl
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Exercise the shipped 1.8.4 synth, not a duplicate rate formula. No device audio output. */
class PlaybackTempoTest {
    private val ready = EventEmitter()
    private val sampleRequest = EventEmitter()
    private val samplesPlayed = EventEmitterOfT<Double>()
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
            for (source in listOf(60.0, 124.0, 132.0, 160.0, 180.0, 240.0)) {
                val midi = MidiFile().apply {
                    addEvent(TempoChangeEvent(0.0, 60000000 / source))
                    addEvent(TempoChangeEvent(1920.0, 60000000 / (source * 1.5)))
                    addEvent(EndOfTrackEvent(0.0, 3840.0))
                }
                engine.loadMidiFile(midi)
                val target = TempoControl.minimum(source)
                val ratio = TempoControl.ratio(target, source)
                engine.playbackSpeed = ratio
                assertEquals(ratio, engine.playbackSpeed, 1e-12)
                engine.tickPosition = 960.0
                assertEquals("source=$source", 60000.0 / target, engine.timePosition, 1e-5)
                engine.tickPosition = 2880.0
                assertEquals(2 * 60000.0 / target + 60000.0 / (target * 1.5), engine.timePosition, 1e-5)
                engine.tickPosition = 0.0
                samplesPlayed.trigger(44100.0) // output reports one real second heard
                assertEquals(target * 960 / 60.0, engine.tickPosition, 1e-5)
            }
            assertEquals(20, TempoControl.minimum(132.0))
            assertEquals(23, TempoControl.minimum(180.0))
            engine.playbackSpeed = .01
            assertEquals(.125, engine.playbackSpeed, 0.0)
        } finally { engine.destroy() }
    }
}
