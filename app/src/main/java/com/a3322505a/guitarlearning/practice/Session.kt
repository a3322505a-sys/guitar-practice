package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.score.*
import kotlin.math.*

/** Range and UI metadata shared by alphaTab and the debug comparison player. */
data class Session(val sequence: Sequence, val bpm: Int, val follow: Boolean, val repeats: Int, val preview: Boolean = false,
    val range: BarRange = BarRange(0,sequence.barCount-1), val sourceTempo: Boolean = sequence.runtimePlan!=null,
    val tempoMultiplier: Double = 1.0) {
    init { require(bpm>0 && repeats>=0); require(preview || range.valid(sequence)) }
    val plan = sequence.performance
    val quarter = sequence.ticksPerQuarter.toDouble()
    val rangeStart = if(preview) Q.ZERO else plan.visits[range.first].start
    val rangeEnd = if(preview) plan.length else plan.visits[range.last].let { it.start+it.duration }
    val rangeDuration = rangeEnd-rangeStart
    val preMeter = plan.visits[if(preview) 0 else range.first].measure.meter
    val leadIn = if(follow) plan.visits[range.first].let { v ->
        v.measure.leadIn+(plan.score.measureIndices[v.measure.id]?.let { v.sourceStart-plan.score.measureStarts[it] } ?: Q.ZERO)
    } else Q.ZERO
    val preDuration = if(follow) preMeter.duration+leadIn else Q.ZERO
    val startTick = (rangeStart.value()*quarter).roundToInt()
    val endTick = (rangeEnd.value()*quarter).roundToInt()
    val practiceLength = endTick-startTick
    val preBeats = preMeter.beatGroups.size
    val preTicks = (preDuration.value()*quarter).roundToInt()
    val totalExactTicks = if(repeats==0) null else (preDuration+rangeDuration*Q(repeats.toLong())).value()*quarter
    val totalTicks: Long? = totalExactTicks?.roundToLong()
}
