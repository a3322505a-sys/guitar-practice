package com.a3322505a.guitarlearning.practice.compile

import com.a3322505a.guitarlearning.music.score.*
import kotlin.math.*

enum class TempoMode { SOURCE, FIXED }

/** One public axis for tempo, click, note timing and cursor, with analytic inverse. */
class TempoMap(val length: Double, tempos: List<Tempo>, val multiplier: Double = 1.0,
    val mode: TempoMode = TempoMode.SOURCE, val fixedBpm: Double = 60.0,
    private val globalSwing: List<SwingSpec> = emptyList()) {
    private data class Piece(val start: Double,val end: Double,val bpm: Double,val slope: Double,val seconds: Double) {
        fun integral(x: Double) = if(abs(slope)<1e-12) 60*x/bpm else 60/slope*ln1p(slope*x/bpm)
        fun inverse(seconds: Double) = if(abs(slope)<1e-12) seconds*bpm/60 else bpm*expm1(seconds*slope/60)/slope
    }
    private val pieces: List<Piece>
    init {
        require(multiplier in .05..8.0 && fixedBpm in 1.0..600.0)
        val output=mutableListOf<Piece>(); var seconds=0.0
        val marks=if(mode==TempoMode.FIXED) listOf(Tempo(Q.ZERO,Q.ONE,fixedBpm)) else tempos
        marks.forEachIndexed { i,t ->
            val start=performed(t.at.value()); val next=performed(marks.getOrNull(i+1)?.at?.value() ?: length)
            val bpm=t.bpm*t.beatUnit.value()*multiplier
            val ramp=t.rampEnd?.let { performed(it.value()) }
            if(ramp!=null && ramp>start) {
                val endBpm=t.endBpm!!*t.beatUnit.value()*multiplier
                val piece=Piece(start,minOf(ramp,next),bpm,(endBpm-bpm)/(ramp-start),seconds)
                output+=piece; seconds+=piece.integral(piece.end-start)
                if(next>ramp) { val hold=Piece(ramp,next,endBpm,0.0,seconds);output+=hold;seconds+=hold.integral(next-ramp) }
            } else if(next>start) { val hold=Piece(start,next,bpm,0.0,seconds);output+=hold;seconds+=hold.integral(next-start) }
        }
        require(output.isNotEmpty())
        pieces=output
        require(seconds.isFinite() && seconds<=24*3600) { "TIME_LIMIT" }
    }
    fun performed(written: Double): Double = warp(written,globalSwing)
    fun written(performed: Double): Double = unwarp(performed,globalSwing)
    fun seconds(written: Double): Double = performedSeconds(performed(written))
    fun performedSeconds(p: Double): Double {
        val piece=pieces[(pieces.binarySearch { if(it.start>p) 1 else if(it.end<=p) -1 else 0 }).let {
            if(it>=0) it else if(p<pieces.first().start) 0 else pieces.lastIndex }]
        return piece.seconds+piece.integral(p-piece.start)
    }
    fun atSeconds(seconds: Double): Double {
        return written(performedAtSeconds(seconds))
    }
    fun performedAtSeconds(seconds: Double): Double {
        var lo=0;var hi=pieces.lastIndex
        while(lo<hi) { val mid=(lo+hi+1)/2; if(pieces[mid].seconds<=seconds) lo=mid else hi=mid-1 }
        val piece=pieces[lo]
        return piece.start+piece.inverse(seconds-piece.seconds)
    }
    fun frame(written: Double, rate: Int = 44100) = (seconds(written)*rate).roundToLong()
    fun atFrame(frame: Long, rate: Int = 44100) = atSeconds(frame.toDouble()/rate)
    fun effectiveQuarterBpm(written: Double): Double {
        val p=performed(written)
        val piece=pieces.lastOrNull { it.start<=p } ?: pieces.first()
        return piece.bpm+piece.slope*(p-piece.start)
    }
    companion object {
        fun warp(x: Double,specs: List<SwingSpec>): Double {
            val s=specs.firstOrNull { x>=it.start.value() && x<it.end.value() } ?: return x
            val unit=s.unit.value(); val pair=2*unit; val base=s.start.value()+floor((x-s.start.value())/pair)*pair
            val local=x-base; val first=pair*s.ratio[0]/s.ratio.sum()
            return base+if(local<=unit) local*first/unit else first+(local-unit)*(pair-first)/unit
        }
        fun unwarp(x: Double,specs: List<SwingSpec>): Double {
            val s=specs.firstOrNull { x>=it.start.value() && x<it.end.value() } ?: return x
            val unit=s.unit.value(); val pair=2*unit; val base=s.start.value()+floor((x-s.start.value())/pair)*pair
            val local=x-base; val first=pair*s.ratio[0]/s.ratio.sum()
            return base+if(local<=first) local*unit/first else unit+(local-first)*unit/(pair-first)
        }
    }
}
