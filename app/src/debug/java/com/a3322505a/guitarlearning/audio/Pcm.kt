package com.a3322505a.guitarlearning.audio

import com.a3322505a.guitarlearning.practice.*
import kotlin.math.*

/** CC0 samples selected by bank metadata; rendering never chooses a guitar fingering. */
class Sampler(private val bank: SoundBank = SoundBank.NYLON, load: (Int) -> ByteArray) {
    private val roots = bank.roots
    private val samples = roots.associateWith { root ->
        val bytes = load(root)
        val wave = FloatArray(bytes.size/2) { i ->
            ((bytes[i*2].toInt() and 255) or (bytes[i*2+1].toInt() shl 8)).toShort()/32768f
        }
        require(wave.isNotEmpty()) { "Empty guitar sample $root" }
        val head = wave.take(bank.sampleRate * 360 / 1000)
        val rms = sqrt(head.sumOf { (it*it).toDouble() } / head.size)
        val gain = min(0.23/max(rms, 0.00001), 0.88/max(wave.maxOf { abs(it) }, 0.00001f))
        FloatArray(wave.size) { wave[it] * gain.toFloat() }
    }
    internal fun source(midi: Int): SampleSource {
        val root=roots.minBy { abs(it-midi) }
        return SampleSource(root,samples.getValue(root),bank.sampleRate,bank.attackMs,bank.releaseMs)
    }
    fun note(midi: Int, frames: Int, vibrato: Boolean = false): FloatArray {
        val root = roots.minBy { abs(it-midi) }
        val wave = samples.getValue(root)
        require(frames > 0)
        val ratio = bank.sampleRate.toDouble()/SAMPLE_RATE * 2.0.pow((midi-root)/12.0)
        val release = max(1, min(frames/3, SAMPLE_RATE*bank.releaseMs/1000))
        var phase=0.0
        return FloatArray(frames) { i ->
            val at = if(vibrato) phase else i*ratio
            if(vibrato) phase += ratio*2.0.pow(.15*sin(2*PI*5*i/SAMPLE_RATE)/12.0)
            val index = at.toInt()
            val value = if(index >= wave.lastIndex) 0f else wave[index] + (wave[index+1]-wave[index])*(at-index).toFloat()
            val attack = min(1.0, i/(SAMPLE_RATE*bank.attackMs/1000))
            val tail = min(1.0, (frames-i-1).toDouble()/release)
            (value * attack * tail).toFloat()
        }
    }
    /** Continuous sample phase: targets never receive an ordinary picked attack. */
    fun linked(from: Int,to: Int,arrival: Int,frames: Int,kind: Technique): FloatArray =
        linkedChain(from,listOf(Triple(arrival,to,kind)),frames)

    /** Successive gestures share one picked attack and continuous sample phase. */
    fun linkedChain(from: Int,targets: List<Triple<Int,Int,Technique>>,frames: Int): FloatArray {
        require(targets.isNotEmpty() && targets.first().first>0 && targets.zipWithNext().all { (a,b)->a.first<b.first })
        val root=roots.minBy { abs(it-from) };val wave=samples.getValue(root)
        var phase=0.0;var segment=0
        val release=max(1,min(frames/3,SAMPLE_RATE*bank.releaseMs/1000))
        return FloatArray(frames) { i ->
            while(segment<targets.lastIndex && i>=targets[segment].first) segment++
            val (arrival,to,kind)=targets[segment]
            val start=if(segment==0) 0 else targets[segment-1].first
            val origin=if(segment==0) from else targets[segment-1].second
            val progress=if(kind==Technique.SLIDE) ((i-start).toDouble()/(arrival-start)).coerceIn(0.0,1.0) else ((i-arrival+SAMPLE_RATE*.003)/(SAMPLE_RATE*.003)).coerceIn(0.0,1.0)
            val midi=origin+(to-origin)*progress
            val at=phase.toInt()
            val value=if(at>=wave.lastIndex) 0f else wave[at]+(wave[at+1]-wave[at])*(phase-at).toFloat()
            phase+=bank.sampleRate.toDouble()/SAMPLE_RATE*2.0.pow((midi-root)/12.0)
            (value*min(1.0,i/(SAMPLE_RATE*bank.attackMs/1000))*min(1.0,(frames-i-1).toDouble()/release)).toFloat()
        }
    }
    /** GP pitch envelopes keep sample phase continuous; bends and open-ended slides are approximations. */
    fun expressive(midi: Int,frames: Int,points: List<Pair<Int,Double>>): FloatArray {
        val root=roots.minBy { abs(it-midi) };val wave=samples.getValue(root)
        var phase=0.0;var segment=0
        val release=max(1,min(frames/3,SAMPLE_RATE*bank.releaseMs/1000))
        return FloatArray(frames) { i ->
            while(segment<points.lastIndex && i>=points[segment+1].first) segment++
            val a=points[segment];val b=points.getOrElse(segment+1) { a }
            val fraction=if(b.first==a.first) 1.0 else ((i-a.first).toDouble()/(b.first-a.first)).coerceIn(0.0,1.0)
            val shift=a.second+(b.second-a.second)*fraction
            val at=phase.toInt()
            val value=if(at>=wave.lastIndex) 0f else wave[at]+(wave[at+1]-wave[at])*(phase-at).toFloat()
            phase+=bank.sampleRate.toDouble()/SAMPLE_RATE*2.0.pow((midi+shift-root)/12.0)
            (value*min(1.0,i/(SAMPLE_RATE*bank.attackMs/1000))*min(1.0,(frames-i-1).toDouble()/release)).toFloat()
        }
    }
}
