package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position

/** Engraving segments reference the original event; they never create audio onsets. */
data class TabSegment(
    val eventId: Int, val tick: Int, val value: RhythmValue, val positions: List<Position>,
    val chord: String?, val strum: Boolean, val tieIn: Boolean, val tieOut: Boolean,
    val expression: Event? = null, val tuplet: Tuplet? = null, val outgoing: Legato? = null, val linkMark: String? = null,
    val ticksPerQuarter: Int = QUARTER,
) {
    val rest get() = expression?.rest ?: positions.isEmpty()
    val duration get() = value.duration(ticksPerQuarter)
    val end get() = tick + duration
}
data class TabBar(val index: Int, val segments: List<TabSegment>)

fun Sequence.tabBars(): List<TabBar> {
    validateExpression()
    require(barMeters.isNotEmpty() || length % barTicks == 0)
    val result = List(barCount) { mutableListOf<TabSegment>() }
    for (event in events) {
        var at = event.tick
        val end = event.tick + event.duration
        while (at < end) {
            val bar=barAt(at.toDouble())
            val room = minOf(end - at, barEnd(bar) - at)
            // Start long values on their natural metric boundary. Eighths on weak beats
            // tie into the next beat rather than disguising syncopation as a long value.
            val (value,group)=rhythmAt(at,room)
            result[bar] += TabSegment(event.id, at, value, event.positions, event.chord,
                event.strum && at == event.tick, event.positions.isNotEmpty() && at > event.tick,
                event.positions.isNotEmpty() && at + value.duration(ticksPerQuarter) < end,event,group,
                links.firstOrNull { it.sourceTick==event.tick }.takeIf { at==event.tick },
                links.firstOrNull { it.sourceTick==event.tick }?.let(::linkMark).takeIf { at==event.tick },ticksPerQuarter)
            at += value.duration(ticksPerQuarter)
        }
    }
    return result.mapIndexed { index, segments -> TabBar(index, segments.sortedBy { it.tick }) }
}

/** Beam adjacent short notes within the beat; rests and beat boundaries break groups. */
fun beamCount(left: TabSegment?, right: TabSegment?): Int =
    if(left==null || right==null || left.rest || right.rest || left.end!=right.tick || left.tick/left.ticksPerQuarter!=right.tick/right.ticksPerQuarter || left.tuplet!=right.tuplet) 0
    else minOf(left.value.flags,right.value.flags)
