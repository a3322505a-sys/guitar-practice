package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.compile.*
import kotlin.math.*

const val SAMPLE_RATE = 44100

/** Historical PCM/frame projection. This class is compiled only for debug. */
internal class LegacySessionClock(metadata: Session) {
    private val plan = metadata.plan
    private val quarter = metadata.quarter
    private val rangeStart = metadata.rangeStart
    private val rangeEnd = metadata.rangeEnd
    private val rangeDuration = metadata.rangeDuration
    private val preDuration = metadata.preDuration
    private val preMeter = metadata.preMeter
    private val range = metadata.range
    private val leadIn = metadata.leadIn
    private val totalExactTicks = metadata.totalExactTicks
    private val repeats = metadata.repeats
    private val sourceTempo = metadata.sourceTempo
    private val tempoMultiplier = metadata.tempoMultiplier
    private val bpm = metadata.bpm
    private val timing = LegacyPlanTiming(plan)
    val clock = timing.map(if(sourceTempo) tempoMultiplier else 1.0,if(sourceTempo) TempoMode.SOURCE else TempoMode.FIXED,bpm.toDouble())
    val sourceStartSeconds=clock.seconds(rangeStart.value())
    val loopSeconds=clock.seconds(rangeEnd.value())-sourceStartSeconds
    val preSeconds=preDuration.value()*60/clock.effectiveQuarterBpm(rangeStart.value())
    val totalFrames: Long? = if(repeats==0) null else ((preSeconds+loopSeconds*repeats)*SAMPLE_RATE).roundToLong()
    private val activity by lazy { IntervalIndex(plan.notes.filter { it.track.id==plan.practice.mainTrackId },
        { clock.performedSeconds(timing.performed(maxOf(it.start,it.event.activation).value(),it.event)) },
        { clock.performedSeconds(timing.performed(it.soundEnd.value(),it.event)) }) }
    fun activeNotes(localQuarter: Double): List<PlanNote> {
        val seconds=clock.seconds(localQuarter)
        return activity.query(seconds,seconds+1e-9)
    }
    fun movingPositions(localQuarter: Double): List<MovingPosition> = activeNotes(localQuarter).flatMap { n ->
        plan.outgoingRelations[n.id].orEmpty().filter { it.kind in listOf("slide","slide-pick","hammer","pull","tap") }.map { r ->
            val target=plan.noteById.getValue(r.to)
            val a=clock.performedSeconds(timing.performed(n.start.value(),n.event))
            val b=clock.performedSeconds(timing.performed(target.start.value(),target.event))
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
    fun performed(q: Double, event: PlanEvent) = timing.performed(q, event)

    fun cursor(tick: Double): Cursor {
    val bounded = totalExactTicks?.let { tick.coerceIn(0.0, (it - 0.00001).coerceAtLeast(0.0)) } ?: tick.coerceAtLeast(0.0)
    val local = localTick(bounded)
    val pre=bounded<preDuration.value()*quarter
    val bar=if(pre) range.first else plan.visitAt(local/quarter)
    val visit=plan.visits[bar]
    val leading=pre && leadIn>com.a3322505a.guitarlearning.music.score.Q.ZERO && local/quarter>=preMeter.duration.value()
    val sourceOffset=plan.score.measureIndices[visit.measure.id]?.let { visit.sourceStart-plan.score.measureStarts[it] }?.value() ?: 0.0
    val beatPosition=if(pre) local/quarter-(if(leading) preMeter.duration.value() else 0.0)
        else local/quarter-visit.start.value()+visit.measure.leadIn.value()+sourceOffset
    val meter=if(pre) preMeter else visit.measure.meter
    val active=if(pre) emptyList() else activeNotes(local/quarter)
    return Cursor(pre,loopAt(bounded),local,bar,meter.groups.indexOfLast { it.value()<=beatPosition }.coerceAtLeast(0),
        frame(bounded),visit.id,visit.sourceMeasureId,active.map { it.id }.toSet(),active.filter { !it.note.dead }.map { it.position }.distinct(),
        if(pre) emptyList() else movingPositions(local/quarter),leading)
}
}

// Source-compatible helpers for the existing debug comparison tests.
val Session.clock get() = LegacySessionClock(this).clock
val Session.sourceStartSeconds get() = LegacySessionClock(this).sourceStartSeconds
val Session.loopSeconds get() = LegacySessionClock(this).loopSeconds
val Session.preSeconds get() = LegacySessionClock(this).preSeconds
val Session.totalFrames get() = LegacySessionClock(this).totalFrames
fun Session.frame(tick: Double) = LegacySessionClock(this).frame(tick)
fun Session.tick(frame: Long) = LegacySessionClock(this).tick(frame)
fun Session.sourceFrames(tick: Double) = LegacySessionClock(this).sourceFrames(tick)
fun Session.loopAt(tick: Double) = LegacySessionClock(this).loopAt(tick)
fun Session.localTick(tick: Double) = LegacySessionClock(this).localTick(tick)
fun Session.resumeTick(tick: Double) = LegacySessionClock(this).resumeTick(tick)
fun Session.loopStartFrame(loop: Int) = LegacySessionClock(this).loopStartFrame(loop)
fun Session.noteFrame(loop: Int, performedQ: Double, offsetSeconds: Double = 0.0) = LegacySessionClock(this).noteFrame(loop, performedQ, offsetSeconds)
fun Session.activeNotes(localQuarter: Double) = LegacySessionClock(this).activeNotes(localQuarter)
fun Session.movingPositions(localQuarter: Double) = LegacySessionClock(this).movingPositions(localQuarter)
fun Session.cursor(tick: Double) = LegacySessionClock(this).cursor(tick)
