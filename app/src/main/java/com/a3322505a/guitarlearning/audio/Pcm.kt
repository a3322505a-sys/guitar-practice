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
    fun note(midi: Int, frames: Int): FloatArray {
        val root = roots.minBy { abs(it-midi) }
        val wave = samples.getValue(root)
        require(frames > 0)
        val ratio = bank.sampleRate.toDouble()/SAMPLE_RATE * 2.0.pow((midi-root)/12.0)
        val release = max(1, min(frames/3, SAMPLE_RATE*bank.releaseMs/1000))
        return FloatArray(frames) { i ->
            val at = i*ratio
            val index = at.toInt()
            val value = if(index >= wave.lastIndex) 0f else wave[index] + (wave[index+1]-wave[index])*(at-index).toFloat()
            val attack = min(1.0, i/(SAMPLE_RATE*bank.attackMs/1000))
            val tail = min(1.0, (frames-i-1).toDouble()/release)
            (value * attack * tail).toFloat()
        }
    }
}

private data class Voice(val tick: Int, val offset: Int, val wave: FloatArray, val gain: Float)

/** Every onset is rounded from its absolute tick, including across loop boundaries. */
class Pcm(val session: Session, sampler: Sampler) {
    private val cache = mutableMapOf<Pair<Int,Int>, FloatArray>()
    private val voices = session.sequence.events.flatMap { event ->
        event.positions.mapIndexed { index, p ->
            val offset = if(event.strum) (index * 0.012 * SAMPLE_RATE).roundToInt() else 0
            val count = session.frame(event.duration.toDouble()).toInt()-offset
            val wave = cache.getOrPut(p.midi to count) { sampler.note(p.midi, count) }
            Voice(event.tick, offset, wave, (0.72/sqrt(event.positions.size.toDouble())).toFloat())
        }
    }
    private val clicks = listOf(1800.0, 1100.0).map { frequency ->
        FloatArray(SAMPLE_RATE*40/1000) { i -> (sin(2*PI*frequency*i/SAMPLE_RATE)*exp(-i/(SAMPLE_RATE*0.008))*0.20).toFloat() }
    }
    fun block(start: Long, count: Int, guitar: Boolean, click: Boolean): ShortArray {
        val mix = FloatArray(count)
        val startTick = session.tick(start)
        val endTick = session.tick(start+count)
        if(guitar) {
            val firstLoop = max(0, session.loopAt(startTick)-1)
            val lastLoop = max(0, session.loopAt(endTick))
            for(loop in firstLoop..lastLoop) {
                if(session.repeats != 0 && loop >= session.repeats) break
                val base = session.preTicks.toDouble()+loop.toDouble()*session.sequence.length
                for(voice in voices) {
                    val onset = session.frame(base+voice.tick)+voice.offset
                    add(mix, start, onset, voice.wave, voice.gain)
                }
            }
        }
        if(click && !session.preview) {
            val firstBeat = max(0, floor(startTick/QUARTER).toLong()-1)
            val lastBeat = floor(endTick/QUARTER).toLong()
            for(beat in firstBeat..lastBeat) {
                if(session.totalTicks != null && beat*QUARTER >= session.totalTicks!!) break
                add(mix, start, session.frame((beat*QUARTER).toDouble()), clicks[if(beat%4==0L) 0 else 1], 1f)
            }
        }
        return ShortArray(count) { (mix[it].coerceIn(-0.96f,0.96f)*32767).roundToInt().toShort() }
    }
    private fun add(mix: FloatArray, start: Long, onset: Long, wave: FloatArray, gain: Float) {
        val from = max(0L, onset-start).toInt()
        val until = min(mix.size.toLong(), onset+wave.size-start).toInt()
        for(i in from until until) mix[i] += wave[(start+i-onset).toInt()]*gain
    }
}
