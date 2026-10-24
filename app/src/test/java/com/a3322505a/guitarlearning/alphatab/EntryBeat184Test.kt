@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.*
import alphaTab.core.ecmaScript.*
import alphaTab.midi.*
import alphaTab.platform.android.JavaThreadAlphaSynthWorker
import alphaTab.platform.worker.AlphaSynthWebWorkerApi
import alphaTab.synth.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.*

/** Real 1.8.4 MIDI -> sequencer -> TSF -> PCM, with a deterministic output device. */
class EntryBeat184Test {
    private class Signal : IEventEmitter {
        val listeners = mutableListOf<() -> Unit>()
        override fun on(value: () -> Unit): () -> Unit { listeners += value; return { off(value) } }
        override fun off(value: () -> Unit) { listeners -= value }
        fun fire() = listeners.toList().forEach { it() }
    }
    private class SignalT<T> : IEventEmitterOfT<T> {
        val listeners = mutableListOf<(T) -> Unit>()
        override fun on(value: (T) -> Unit): () -> Unit { listeners += value; return { off(value) } }
        override fun off(value: (T) -> Unit) { listeners -= value }
        fun fire(value: T) = listeners.toList().forEach { it(value) }
    }
    private class Output {
        val ready = Signal(); val request = Signal(); val played = SignalT<Double>()
        val pcm = mutableListOf<Float>()
        var frames = 0.0
        val device = Proxy.newProxyInstance(ISynthOutput::class.java.classLoader, arrayOf(ISynthOutput::class.java)) { _, method, args ->
            when (method.name) {
                "getSampleRate" -> 44100.0
                "getReady" -> ready
                "getSampleRequest" -> request
                "getSamplesPlayed" -> played
                "open" -> { ready.fire(); null }
                "addSamples" -> { val samples = args!![0] as Float32Array
                    pcm.addAll(samples.data.toList()); frames = samples.length / 2; null }
                else -> null
            }
        } as ISynthOutput
        fun pump(seconds: Double) {
            var attempts = 0
            while (pcm.size < seconds * 44100 * 2) {
                check(attempts++ < 10000) { "engine stopped producing audio" }
                frames = 0.0; request.fire()
                if (frames > 0) played.fire(frames)
            }
        }
        fun peak(at: Double, duration: Double = .08): Double = pcm.subList(
            (at * 88200).toInt().coerceAtLeast(0), ((at + duration) * 88200).toInt().coerceAtMost(pcm.size))
            .maxOfOrNull { abs(it.toDouble()) } ?: 0.0
        fun onset(at: Double): Double {
            val from = ((at - .01).coerceAtLeast(0.0) * 88200).toInt()
            val to = ((at + .08) * 88200).toInt().coerceAtMost(pcm.size)
            val threshold = peak(at) * .01
            return (from until to).first { abs(pcm[it]) > threshold } / 88200.0
        }
    }
    private fun midi(n: Int, d: Int, bpm: Double = 60.0, changeAt: Double? = null) = MidiFile().apply {
        addEvent(TempoChangeEvent(0.0, 60000000 / bpm))
        addEvent(TimeSignatureEvent(0.0, 0.0, if (changeAt == null) n.toDouble() else 4.0,
            if (changeAt == null) log2(d.toDouble()) else 2.0, 24.0, 8.0))
        if (changeAt != null) {
            addEvent(TempoChangeEvent(changeAt, 60000000 / bpm))
            addEvent(TimeSignatureEvent(0.0, changeAt, n.toDouble(), log2(d.toDouble()), 24.0, 8.0))
        }
        // No guitar note at zero: entry follows the score, not first NoteOn.
        addEvent(EndOfTrackEvent(0.0, 38400.0))
    }
    private fun engine(out: Output, file: MidiFile) = AlphaSynth(out.device, 100.0).apply {
        loadMidiFile(file)
        loadSoundFont(Uint8Array(File("src/main/assets/sonivox.sf2").readBytes().toUByteArray()), false)
        masterVolume = 1.0
        metronomeVolume = AlphaTone.CLICK * EntryBeat184.FORMAL_GAIN
    }
    @Test fun metersAndSpeedsCountExactlyOneBarThenOneAccentEvenWithClickOff() {
        for ((n, d) in listOf(4 to 4, 3 to 4, 5 to 4, 6 to 8)) for (speed in listOf(.5, 2.0)) {
            val out = Output(); val synth = engine(out, midi(n, d))
            try {
                val shim = EntryBeat184().apply { click = false }
                synth.playbackSpeed = speed
                synth.countInVolume = AlphaTone.CLICK * .7
                shim.prepareSynth(synth, 0.0, n, d)
                assertTrue(synth.play())
                val beat = 4.0 / d / speed
                val start = n * beat
                out.pump(start + beat + .2)
                val pre = (0 until n).map { out.peak(it * beat) }
                assertTrue("missing count-in $n/$d speed=$speed: $pre", pre.all { it > .001 })
                val entry = out.peak(start)
                assertEquals("entry/count-in $n/$d speed=$speed", 2.0, entry / pre.first(), .08)
                for (i in 1..n) assertEquals("audible click spacing $n/$d speed=$speed beat=$i",
                    i * beat, out.onset(i * beat) - out.onset(0.0), .0035)
                assertTrue("extra click after entry", out.peak(start + beat) < .001)
                assertTrue("PCM clips", out.pcm.all { abs(it) < 1f })
            } finally { synth.destroy() }
        }
    }
    @Test fun normalClicksRetainLoudnessAndAccentIsNotDoubledOrRepeatedOnLoop() {
        val out = Output(); val synth = engine(out, midi(4, 4))
        try {
            val shim = EntryBeat184()
            shim.prepareSynth(synth, 0.0, 4, 4)
            synth.isLooping = true
            synth.playbackRange = PlaybackRange().apply { startTick = 0.0; endTick = 3840.0 }
            assertTrue(synth.play()); out.pump(5.2)
            assertEquals(1.4, out.peak(0.0) / out.peak(1.0), .04)
            assertEquals("automatic loop must not re-arm", out.peak(1.0), out.peak(4.0), .002)
            val reference = Output(); val native = engine(reference, midi(4, 4))
            try {
                native.metronomeVolume = AlphaTone.CLICK
                assertTrue(native.play()); reference.pump(1.2)
                assertEquals("ordinary loudness changed", reference.peak(1.0), out.peak(1.0), .001)
            } finally { native.destroy() }
        } finally { synth.destroy() }
    }
    @Test fun selectedMeterAtBoundaryAndOffGridPickupUseExactStartAndCanRearm() {
        val out = Output(); val synth = engine(out, midi(6, 8, changeAt = 3840.0))
        try {
            val shim = EntryBeat184().apply { click = false }
            synth.playbackRange = PlaybackRange().apply { startTick = 3840.0; endTick = 15360.0 }
            synth.tickPosition = 3840.0
            shim.prepareSynth(synth, 3840.0, 6, 8)
            synth.countInVolume = AlphaTone.CLICK * .7
            assertTrue(synth.play()); out.pump(3.7)
            assertTrue(out.peak(2.5) > .001)
            assertEquals(2.0, out.peak(3.0) / out.peak(0.0), .08)
            synth.stop(); synth.loadMidiFile(midi(4, 4))
            synth.playbackRange = PlaybackRange().apply { startTick = 240.0; endTick = 15360.0 }
            synth.tickPosition = 240.0
            shim.prepareSynth(synth, 240.0, 4, 4)
            synth.countInVolume = 0.0
            val before = out.pcm.size / 88200.0
            assertTrue(synth.play()); out.pump(before + 1.2)
            assertTrue("pickup missing entry", out.peak(before) > .001)
            assertTrue("ordinary clicks should be off", out.peak(before + .75) < .001)
        } finally { synth.destroy() }
    }
    @Test fun shippedAndroidWorkerHandlerCanBeResolvedOnWorkerThread() {
        val worker = JavaThreadAlphaSynthWorker { it() }
        try {
            val api = AlphaSynthWebWorkerApi(Output().device, Settings(), worker)
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            worker.postToWorker {
                failure = runCatching { assertNotNull(EntryBeat184.workerSynth(worker)) }.exceptionOrNull()
                done.countDown()
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            failure?.let { throw it }
            api.destroy()
        } finally { worker.terminate() }
    }
}
