package com.a3322505a.guitarlearning.practice.compile

import com.a3322505a.guitarlearning.music.score.*

/** The pre-alphaTab swing/tempo clock is a debug comparison implementation. */
internal class LegacyPlanTiming(plan: PerformancePlan) {
    private val score = plan.score
    private val events = plan.events
    private val swing = plan.swing
    private val length = plan.length
    private val tempos = plan.tempos
    private val tupletSpans by lazy {
        val indices=score.measures.withIndex().associate { it.value.id to it.index }
        val bounds=score.voices.flatMap { v -> v.events.map { e ->
            val start=score.measureStarts[indices.getValue(e.measureId)]+e.onset
            e.id to (start to start+e.duration)
        } }.toMap()
        val groups=score.tuplets.associateBy { it.id }
        events.filter { it.event.written.tupletGroupIds.isNotEmpty() }.associate { e ->
            val g=groups.getValue(e.event.written.tupletGroupIds.first())
            val applicable=score.swing.filter { it.trackIds.isEmpty() && it.voiceIds.isEmpty() ||
                e.track.id in it.trackIds || e.voiceId in it.voiceIds }
            val a=g.eventIds.minOf { bounds.getValue(it).first.value() }
            val b=g.eventIds.maxOf { bounds.getValue(it).second.value() }
            val offset=(e.start-e.sourceStart-e.sourceOffset).value()
            e.id to doubleArrayOf(a+offset,b+offset,TempoMap.warp(a,applicable)+offset,TempoMap.warp(b,applicable)+offset)
        }
    }
    fun map(multiplier: Double=1.0,mode: TempoMode=TempoMode.SOURCE,fixedBpm: Double=60.0) =
        TempoMap(length.value(),tempos,multiplier,mode,fixedBpm,swing.filter { it.trackIds.isEmpty() && it.voiceIds.isEmpty() })
    /** Explicit tuplets interpolate between warped group endpoints and keep their internal ratio. */
    fun performed(q: Double,event: PlanEvent): Double {
        val applicable=swing.filter { it.trackIds.isEmpty() && it.voiceIds.isEmpty() ||
            event.track.id in it.trackIds || event.voiceId in it.voiceIds }
        if(event.event.written.tupletGroupIds.isEmpty()) return TempoMap.warp(q,applicable)
        val bounds=tupletSpans.getValue(event.id)
        val start=bounds[0];val end=bounds[1]
        val a=bounds[2]; val b=bounds[3]
        return a+(q-start)*(b-a)/(end-start)
    }
}

fun PerformancePlan.map(multiplier: Double = 1.0, mode: TempoMode = TempoMode.SOURCE, fixedBpm: Double = 60.0) =
    LegacyPlanTiming(this).map(multiplier, mode, fixedBpm)
