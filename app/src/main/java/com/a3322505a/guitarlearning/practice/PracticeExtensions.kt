package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.music.*

/** Optional range operations consume the same compiled timeline as the player and cursor. */
object PracticeExtensions {
    fun canFade(sequence: Sequence): Boolean {
        val p = sequence.runtimePlan?.practice ?: return false
        val leads = p.lesson?.leadTrackIds.orEmpty()
        return leads.isNotEmpty() && leads.all { it in p.mixTrackIds } && leads.size < p.mixTrackIds.size
    }
    fun leadGain(round: Int) = when(round) { 0 -> 1.0; 1 -> .3; else -> 0.0 }
    fun backwardRanges(sequence: Sequence, range: BarRange): List<BarRange> {
        val plan = sequence.runtimePlan ?: return emptyList()
        if(!range.valid(sequence)) return emptyList()
        val boundaries = plan.practice.lesson?.chainStarts.orEmpty().toSet()
        val points = (range.first..range.last).filter { it==range.first || boundaries.isEmpty() || plan.visits[it].sourceMeasureId in boundaries }
        return points.reversed().map { BarRange(it,range.last) }
    }
    fun action(sequence: Sequence, quarter: Double): String? {
        val plan = sequence.runtimePlan ?: return null
        val actions = plan.practice.lesson?.actions.orEmpty().associateBy { it.eventId }
        if(actions.isEmpty()) return null
        val next = plan.events.firstOrNull { it.track.id == plan.mainTrack.id &&
            it.start.value() >= quarter-.1 && it.start.value() <= quarter+1 && it.event.id in actions }
        return next?.let { event -> actions[event.event.id]?.let { action ->
            if(action.kind != "retained") action.text else {
                val from=plan.score.voicings.first { it.id==action.fromVoicingId }
                val to=plan.score.voicings.first { it.id==action.voicingId }
                val track=plan.score.tracks.first { it.id==from.trackId }
                val held=retainedFingers(from.chord(track),to.chord(track))
                action.text+" · 保留："+held.joinToString("，") { "${it.position.string}弦${it.position.fret}品${it.finger}指" }
            }
        } }
    }
}
