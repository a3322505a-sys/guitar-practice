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
    fun linked(from: Int,to: Int,arrival: Int,frames: Int,kind: Technique): FloatArray {
        val root=roots.minBy { abs(it-from) };val wave=samples.getValue(root)
        var phase=0.0
        val release=max(1,min(frames/3,SAMPLE_RATE*bank.releaseMs/1000))
        return FloatArray(frames) { i ->
            val progress=if(kind==Technique.SLIDE) (i.toDouble()/arrival).coerceIn(0.0,1.0) else ((i-arrival+SAMPLE_RATE*.003)/(SAMPLE_RATE*.003)).coerceIn(0.0,1.0)
            val midi=from+(to-from)*progress
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

private data class Voice(val tick: Int, val offset: Int, val wave: FloatArray, val gain: Float)

/** Every onset is rounded from its absolute tick, including across loop boundaries. */
class Pcm(val session: Session, sampler: Sampler) {
    init { session.sequence.validateExpression() }
    private val cache = mutableMapOf<Triple<Int,Int,Boolean>, FloatArray>()
    private val voices = session.sequence.events.flatMap { event ->
        val seq=session.sequence
        val ordered=when(event.stroke) { Pick.DOWN->event.strings.sortedDescending();Pick.UP->event.strings.sorted();else->event.strings }
        ordered.mapIndexedNotNull { index,string ->
            if(seq.links.any { it.targetTick==event.tick && it.string==string }) return@mapIndexedNotNull null
            val offset = if(event.strum) (index * 0.012 * SAMPLE_RATE).roundToInt() else 0
            val p=event.positions.firstOrNull { it.string==string }
            val link=seq.links.firstOrNull { it.sourceTick==event.tick && it.string==string }
            val target=link?.let { seq.events.first { e->e.tick==it.targetTick } }
            val duration=if(link!=null) link.targetTick-event.tick+seq.soundDuration(target!!,seq.position(link.targetTick,string)) else if(p!=null) seq.soundDuration(event,p) else event.duration
            fun relativeFrame(tick: Double)=(session.sourceFrames(event.tick+tick)-session.sourceFrames(event.tick.toDouble())).toInt()-offset
            val expression=event.stringExpression[string]
            // GP staccato (Accent bit 1) releases at half the performed note duration;
            // its written value/onset remains untouched, unlike an accent's gain.
            val count=(relativeFrame(duration.toDouble())*(if(expression?.short==true) .5 else 1.0)).toInt().coerceAtLeast(1)
            val contour=when {
                expression?.bend?.isNotEmpty()==true -> expression.bend.map { relativeFrame(it.tick).coerceAtLeast(0) to it.semitones }
                expression?.grace!=null && p!=null -> listOf(0 to (expression.grace.fret-p.fret).toDouble(),relativeFrame(expression.grace.writtenTicks).coerceAtLeast(1) to 0.0)
                expression?.slide==4 -> listOf(0 to 0.0,(count*.75).toInt() to 0.0,count to -3.0)
                expression?.slide==8 -> listOf(0 to 0.0,(count*.75).toInt() to 0.0,count to 3.0)
                expression?.slide==16 -> listOf(0 to -3.0,(count*.25).toInt() to 0.0)
                else -> emptyList()
            }
            var wave=if(p==null) dead(string,count) else if(link!=null) sampler.linked(seq.midi(p),seq.midi(seq.position(link.targetTick,string)),relativeFrame((link.targetTick-event.tick).toDouble()).coerceAtLeast(1),count,link.kind)
                else if(contour.isNotEmpty()) sampler.expressive(seq.midi(p),count,contour)
                else cache.getOrPut(Triple(seq.midi(p),count,event.vibrato)) { sampler.note(seq.midi(p),count,event.vibrato) }
            if(p!=null && event.muted(string)) wave=muted(wave)
            if(target!=null && target.strength(string)!=event.strength(string)) {
                val arrival=relativeFrame((link!!.targetTick-event.tick).toDouble())
                val ratio=target.strength(string)/event.strength(string)
                wave=FloatArray(wave.size) { i->wave[i]*(1f+(ratio-1f)*((i-arrival)/(SAMPLE_RATE*.003f)).coerceIn(0f,1f)) }
            }
            Voice(event.tick,offset,wave,(.72/sqrt(event.strings.size.toDouble())).toFloat()*event.strength(string))
        }
    }
    private fun dead(string: Int,count: Int): FloatArray {
        var seed=7919L*string;var low=0f
        return FloatArray(count) { i ->
            seed=(seed*1664525+1013904223) and 0xffffffffL
            val noise=((seed ushr 8)/8388608f-1f)
            low+=.25f*(noise-low)
            (.40f*(noise-low)*exp(-i/(SAMPLE_RATE*.035))*min(1f,i/(SAMPLE_RATE*.002f))).toFloat()
        }
    }
    private fun muted(wave: FloatArray): FloatArray {
        var low=0f
        return FloatArray(wave.size) { i -> low+=.16f*(wave[i]-low);low*exp(-i/(SAMPLE_RATE*.10)).toFloat() }
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
                val loopStart=session.preTicks.toDouble()+loop.toDouble()*session.practiceLength
                for(voice in voices) {
                    val onset = session.frame(loopStart)+session.sourceFrames(voice.tick.toDouble())-session.sourceFrames(session.startTick.toDouble())+voice.offset
                    add(mix, start, onset, voice.wave, voice.gain,session.frame(loopStart),session.frame(loopStart+session.practiceLength))
                }
            }
        }
        if(click && !session.preview) {
            val firstBeat = max(0, floor(startTick/QUARTER).toLong()-1)
            val lastBeat = floor(endTick/QUARTER).toLong()
            for(beat in firstBeat..lastBeat) {
                if(session.totalTicks != null && beat*QUARTER >= session.totalTicks!!) break
                val written=(beat*QUARTER).toDouble()
                val cursor=session.cursor(written)
                add(mix,start,session.frame(written),clicks[if(cursor.beat==0) 0 else 1],1f)
            }
        }
        return ShortArray(count) { (mix[it].coerceIn(-0.96f,0.96f)*32767).roundToInt().toShort() }
    }
    private fun add(mix: FloatArray, start: Long, onset: Long, wave: FloatArray, gain: Float, rangeStart: Long = onset, rangeEnd: Long = onset+wave.size) {
        val from = maxOf(0L, onset-start,rangeStart-start).coerceAtMost(mix.size.toLong()).toInt()
        val until = minOf(mix.size.toLong(), onset+wave.size-start,rangeEnd-start).coerceAtLeast(0L).toInt()
        for(i in from until until) {
            val frame=start+i
            val fadeOut=if(rangeEnd<onset+wave.size) ((rangeEnd-frame-1)/(SAMPLE_RATE*.005f)).coerceIn(0f,1f) else 1f
            val fadeIn=if(rangeStart>onset) ((frame-rangeStart)/(SAMPLE_RATE*.005f)).coerceIn(0f,1f) else 1f
            mix[i] += wave[(frame-onset).toInt()]*gain*fadeOut*fadeIn
        }
    }
}
