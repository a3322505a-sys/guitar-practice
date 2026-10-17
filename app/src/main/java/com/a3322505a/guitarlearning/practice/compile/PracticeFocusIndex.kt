package com.a3322505a.guitarlearning.practice.compile

import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.practice.BarRange

/** A selection belongs to this immutable route, including repeated visits and all voices. */
data class PracticeFocus(val ordinal: Int, val at: Q, val visit: Int,
    val events: List<PlanEvent>, val notes: List<PlanNote>) {
    val positions get() = notes.filter { !it.note.dead }.map { it.position }.distinct()
    val rest get() = events.all { it.effectiveNotes.isEmpty() }
}

class PracticeFocusIndex(val plan: PerformancePlan) {
    private val byEvent = plan.notes.groupBy { it.event.id }
    val groups = plan.events.filter { it.track.id == plan.practice.mainTrackId }
        .groupBy { it.activation }.toSortedMap().entries.mapIndexed { i, (at, events) ->
            PracticeFocus(i, at, plan.visitAt(at.value()), events, events.flatMap { byEvent[it.id].orEmpty() })
        }
    fun at(q: Double, range: BarRange): PracticeFocus? {
        var lo = 0; var hi = groups.size
        while (lo < hi) { val mid = (lo + hi) / 2
            if (groups[mid].at.value() <= q + 1e-8) lo = mid + 1 else hi = mid
        }
        return groups.getOrNull(lo - 1)?.takeIf { it.visit in range.first..range.last }
    }
    fun step(q: Double, direction: Int, range: BarRange): PracticeFocus? {
        val current = at(q, range)
        val next = if (current == null) {
            if(direction<0) null else groups.firstOrNull { it.visit in range.first..range.last }
        }
            else if (direction < 0 && q > current.at.value() + 1e-7) current
            else groups.getOrNull(current.ordinal + direction)
        return next?.takeIf { it.visit in range.first..range.last }
    }
}
