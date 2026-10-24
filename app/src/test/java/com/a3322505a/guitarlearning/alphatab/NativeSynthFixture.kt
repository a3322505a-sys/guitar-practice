@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.IEventEmitter
import alphaTab.IEventEmitterOfT
import alphaTab.core.ecmaScript.Float32Array
import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.midi.MidiEventType
import alphaTab.model.Score
import alphaTab.synth.AlphaSynth
import alphaTab.synth.ISynthOutput
import org.junit.Assert.*
import java.io.File
import java.lang.reflect.Proxy

/** Shipped synth + shipped font; the test output consumes real PCM synchronously. */
internal class NativeSynthFixture(score: Score) : AutoCloseable {
    private class Signal : IEventEmitter {
        private val listeners = mutableListOf<() -> Unit>()
        override fun on(value: () -> Unit): () -> Unit { listeners += value; return { off(value) } }
        override fun off(value: () -> Unit) { listeners -= value }
        fun trigger() = listeners.toList().forEach { it() }
    }
    private class ValueSignal<T> : IEventEmitterOfT<T> {
        private val listeners = mutableListOf<(T) -> Unit>()
        override fun on(value: (T) -> Unit): () -> Unit { listeners += value; return { off(value) } }
        override fun off(value: (T) -> Unit) { listeners -= value }
        fun trigger(value: T) = listeners.toList().forEach { it(value) }
    }
    private val ready = Signal()
    private val request = Signal()
    private val played = ValueSignal<Double>()
    private val pending = ArrayDeque<FloatArray>()
    val heard = ArrayList<Float>()
    var frames = 0L; private set
    private val output = Proxy.newProxyInstance(ISynthOutput::class.java.classLoader,
        arrayOf(ISynthOutput::class.java)) { _, method, args ->
        when (method.name) {
            "getSampleRate" -> 44100.0
            "getReady" -> ready
            "getSampleRequest" -> request
            "getSamplesPlayed" -> played
            "open" -> { ready.trigger(); null }
            "addSamples" -> { pending.add((args!![0] as Float32Array).data.toList().toFloatArray()); null }
            "resetSamples" -> { pending.clear(); null }
            else -> null
        }
    } as ISynthOutput
    val engine = AlphaSynth(output, 100.0)
    init {
        engine.loadMidiFile(EngineProjection.generate(score).first)
        engine.loadSoundFont(Uint8Array(File("src/main/assets/sonivox.sf2").readBytes().asUByteArray()), false)
        engine.masterVolume = AlphaTone.MASTER
        engine.midiEventsPlayedFilter = alphaTab.collections.List(MidiEventType.AlphaTabMetronome, MidiEventType.NoteOn)
        assertTrue(engine.isReadyForPlayback)
    }
    fun step() {
        if (pending.isEmpty()) request.trigger()
        val data = pending.removeFirst()
        heard.addAll(data.toList())
        frames += data.size / 2
        played.trigger(data.size / 2.0)
    }
    fun until(condition: () -> Boolean) {
        repeat(4000) { if (condition()) return; step() }
        fail("Native synth did not reach the expected playback state")
    }
    fun advance(milliseconds: Int) {
        val end = frames + milliseconds * 44100L / 1000
        until { frames >= end }
    }
    override fun close() = engine.destroy()
}
