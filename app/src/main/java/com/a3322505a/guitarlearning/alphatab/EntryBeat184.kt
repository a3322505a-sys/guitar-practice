@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.midi.*
import alphaTab.platform.android.JavaThreadAlphaSynthWorker
import alphaTab.platform.android.JavaThreadWorkerBase
import alphaTab.platform.worker.AlphaSynthWebWorker
import alphaTab.platform.worker.AlphaSynthWebWorkerApi
import alphaTab.synth.*
import alphaTab.synth.synthesis.SynthEvent
import kotlin.jvm.internal.CallableReference
import kotlin.math.abs

/**
 * Pinned alphaTab 1.8.4 shim. No new clock, output, track or sample.
 * The existing worker queues NoteOff/NoteOn on its own metronome channel at the
 * original SynthEvent time. Velocities 90/126 give 1.4x amplitude in TSF;
 * the channel gain compensates 95/90 to retain the existing ordinary loudness.
 * Internal access and the three private fields are deliberately isolated here;
 * integration tests must be rerun before changing the alphaTab dependency.
 */
internal class EntryBeat184 {
    @Volatile var click = true
    private var installed: ClickSynth? = null // accessed only on the synth worker

    fun prepare(player: IAlphaSynth, tick: Double, numerator: Int, denominator: Int, complete: (Throwable?) -> Unit) {
        val wrapper = player as AlphaSynthWrapper
        val entryTick = tick + wrapper.midiTickShift
        val instance = wrapper.instance as AlphaSynthWebWorkerApi
        val worker = instance.worker as JavaThreadAlphaSynthWorker
        worker.postToWorker {
            val failure = runCatching {
                val synth = workerSynth(worker)
                prepareSynth(synth, entryTick, numerator, denominator)
            }.exceptionOrNull()
            complete(failure)
        }
    }

    internal fun prepareSynth(synth: AlphaSynth, tick: Double, numerator: Int, denominator: Int) {
        val shim = installed?.takeIf { it.owner === synth } ?: ClickSynth(synth, this).also {
            // Intercept event dispatch only; retain the original TSF instance
            // for rendering, soundfont loading and the SDK's audio exporter.
            field(MidiFileSequencer::class.java, "_synthesizer").set(synth.sequencer, it)
            installed = it
        }
        shim.prepare(tick, numerator, denominator)
    }

    companion object {
        const val FORMAL_GAIN = 95.0 / 90.0
        private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }

        /** Resolve the bound SDK message handler, on its own worker thread. */
        internal fun workerSynth(worker: JavaThreadAlphaSynthWorker): AlphaSynth {
            val handlers = field(JavaThreadWorkerBase::class.java, "_listenerInsideWorker").get(worker) as Map<*, *>
            val handler = handlers.keys.mapNotNull { callback ->
                // The shipped AAR uses JVM lambda captures; some Kotlin builds
                // emit a CallableReference for the same bound method instead.
                val bound = (callback as? CallableReference)?.boundReceiver
                if (bound is AlphaSynthWebWorker) bound else callback?.javaClass?.declaredFields
                    ?.firstOrNull { AlphaSynthWebWorker::class.java.isAssignableFrom(it.type) }
                    ?.apply { isAccessible = true }?.get(callback) as? AlphaSynthWebWorker
            }.single()
            return field(AlphaSynthWebWorker::class.java, "_player").get(handler) as AlphaSynth
        }
    }

    private class ClickSynth(val owner: AlphaSynth, val controls: EntryBeat184,
        val original: IAudioSampleSynthesizer = owner.synthesizer) : IAudioSampleSynthesizer by original {
        private var entryTick = 0.0
        private var armed = false
        private var entryTempo = 120.0
        private var numerator = 4.0
        private var denominator = 4.0

        // Silent seeks exclude events exactly at the destination. Count-in must
        // still use the destination bar's meter/tempo, including a change there.
        override val currentTempo get() = entryTempo
        override val timeSignatureNumerator get() = numerator
        override val timeSignatureDenominator get() = denominator

        fun prepare(tick: Double, n: Int, d: Int) {
            // The SDK's read-only tickPosition rounds and adds one tick. Use the
            // exact requested start, otherwise it would insert a duplicate hit.
            entryTick = tick
            armed = true
            numerator = n.toDouble(); denominator = d.toDouble()
            val state = field(MidiFileSequencer::class.java, "_mainState").get(owner.sequencer) as MidiSequencerState
            entryTempo = state.tempoChanges.last { it.ticks <= entryTick }.bpm
            // A pickup/grace offset may not coincide with a generated metronome
            // event. Add only the missing click to the SAME engine event list.
            if (state.synthData.none { it.isMetronome && abs(it.event.tick - entryTick) < 1e-6 }) {
                val length = state.division * 4.0 / denominator
                state.synthData.push(SynthEvent.newMetronomeEvent(-1.0, entryTick, 0.0, length,
                    length * 60000.0 / (entryTempo * state.division)).apply {
                    time = owner.sequencer.mainTickPositionToTimePosition(entryTick) * owner.playbackSpeed
                })
                state.synthData.sort { a, b ->
                    if (a.time == b.time) a.eventIndex.compareTo(b.eventIndex).toDouble()
                    else a.time.compareTo(b.time).toDouble()
                }
            }
        }

        override fun dispatchEvent(synthEvent: SynthEvent) {
            if (!synthEvent.isMetronome || owner.sequencer.isPlayingCountIn) {
                original.dispatchEvent(synthEvent)
                return
            }
            val first = armed && abs(synthEvent.event.tick - entryTick) < 1e-6
            if (first) armed = false
            // Retain original metronome telemetry but suppress its fixed-95 hit.
            // Never mutate the stored event: loops and silent seeks reuse it.
            original.dispatchEvent(SynthEvent(synthEvent.eventIndex, synthEvent.event).apply {
                time = synthEvent.time; isMetronome = false
            })
            if (!first && !controls.click) return
            val channel = owner.sequencer.metronomeChannel
            val key = SynthConstants.MetronomeKey
            fun enqueue(event: MidiEvent) = original.dispatchEvent(SynthEvent(synthEvent.eventIndex, event).apply {
                time = synthEvent.time
            })
            enqueue(NoteOffEvent(0.0, synthEvent.event.tick, channel, key, 0.0))
            enqueue(NoteOnEvent(0.0, synthEvent.event.tick, channel, key, if (first) 126.0 else 90.0))
        }
    }
}
