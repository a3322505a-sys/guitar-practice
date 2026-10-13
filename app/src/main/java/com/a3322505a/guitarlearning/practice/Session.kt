package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.compile.*
import kotlin.math.*

/** Int tick accessors retain legacy callers; musical arithmetic uses Q and one TempoMap. */
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
    val clock = plan.map(if(sourceTempo) tempoMultiplier else 1.0,if(sourceTempo) TempoMode.SOURCE else TempoMode.FIXED,bpm.toDouble())
    val sourceStartSeconds=clock.seconds(rangeStart.value())
    val loopSeconds=clock.seconds(rangeEnd.value())-sourceStartSeconds
    val preSeconds=preDuration.value()*60/clock.effectiveQuarterBpm(rangeStart.value())
    val totalFrames: Long? = if(repeats==0) null else ((preSeconds+loopSeconds*repeats)*SAMPLE_RATE).roundToLong()
    private val activity by lazy { IntervalIndex(plan.notes.filter { it.track.id==plan.practice.mainTrackId },
        { clock.performedSeconds(plan.performed(maxOf(it.start,it.event.activation).value(),it.event)) },
        { clock.performedSeconds(plan.performed(it.soundEnd.value(),it.event)) }) }
    fun activeNotes(localQuarter: Double): List<PlanNote> {
        val seconds=clock.seconds(localQuarter)
        return activity.query(seconds,seconds+1e-9)
    }
    fun movingPositions(localQuarter: Double): List<MovingPosition> = activeNotes(localQuarter).flatMap { n ->
        plan.outgoingRelations[n.id].orEmpty().filter { it.kind in listOf("slide","slide-pick","hammer","pull","tap") }.map { r ->
            val target=plan.noteById.getValue(r.to)
            val a=clock.performedSeconds(plan.performed(n.start.value(),n.event))
            val b=clock.performedSeconds(plan.performed(target.start.value(),target.event))
            val time=clock.seconds(localQuarter)
            val fraction=if(r.kind.startsWith("slide")) ((time-a)/(b-a)).coerceIn(0.0,1.0)
                else ((time-b+.003)/.003).coerceIn(0.0,1.0)
            MovingPosition(n.position,target.position,(n.position.fret+(target.position.fret-n.position.fret)*fraction).toFloat(),
                when(r.kind) { "hammer" -> "h";"pull" -> "p";"tap" -> "T";else -> if(target.position.fret>=n.position.fret) "/" else "\\" })
        }
    }
    fun frame(tick: Double): Long {
        val q=tick/quarter
        val pre=preDuration.value()
        val seconds=if(q<pre) q*60/clock.effectiveQuarterBpm(rangeStart.value()) else {
            val loop=floor((q-pre)/rangeDuration.value())
            val local=rangeStart.value()+(q-pre)-loop*rangeDuration.value()
            preSeconds+loop*loopSeconds+clock.seconds(local)-sourceStartSeconds
        }
        return (seconds*SAMPLE_RATE).roundToLong()
    }
    fun tick(frame: Long): Double {
        val seconds=frame.toDouble()/SAMPLE_RATE
        if(seconds<preSeconds) return seconds*clock.effectiveQuarterBpm(rangeStart.value())/60*quarter
        val loop=floor((seconds-preSeconds)/loopSeconds)
        val localSeconds=seconds-preSeconds-loop*loopSeconds
        val local=clock.atSeconds(localSeconds+sourceStartSeconds)-rangeStart.value()
        return (preDuration.value()+loop*rangeDuration.value()+local)*quarter
    }
    fun sourceFrames(tick: Double) = clock.frame(tick/quarter)
    fun loopAt(tick: Double) = if(tick<preDuration.value()*quarter) -1 else floor(
        (tick-preDuration.value()*quarter)/(rangeDuration.value()*quarter)+1e-10).toInt()
    fun localTick(tick: Double): Double {
        if(tick<preDuration.value()*quarter) return tick
        val loop=loopAt(tick)
        // Subtract in the public tick axis: division followed by subtraction lost
        // an ULP at exact legacy triplet boundaries (52/12 - 4, for example).
        return rangeStart.value()*quarter+(tick-preDuration.value()*quarter)-loop*(rangeDuration.value()*quarter)
    }
    fun resumeTick(tick: Double): Double {
        if(tick<preDuration.value()*quarter) return 0.0
        val loop=loopAt(tick)
        val visit=plan.visits[plan.visitAt(localTick(tick)/quarter)]
        return (preDuration.value()+loop*rangeDuration.value()+maxOf(visit.start,rangeStart).value()-rangeStart.value())*quarter
    }
    fun loopStartFrame(loop: Int) = ((preSeconds+loop*loopSeconds)*SAMPLE_RATE).roundToLong()
    fun noteFrame(loop: Int,performedQ: Double,offsetSeconds: Double = 0.0) =
        ((preSeconds+loop*loopSeconds+clock.performedSeconds(performedQ)-sourceStartSeconds+offsetSeconds)*SAMPLE_RATE).roundToLong()
}
