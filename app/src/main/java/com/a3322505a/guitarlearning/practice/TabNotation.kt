package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position

enum class RhythmValue(val ticks: Int, val flags: Int = 0) {
    WHOLE(48), HALF(24), QUARTER(12), DOTTED_EIGHTH(9,1), EIGHTH(6,1), SIXTEENTH(3,2), TRIPLET_EIGHTH(4,1), TRIPLET_SIXTEENTH(2,2)
}

/** Engraving segments reference the original event; they never create audio onsets. */
data class TabSegment(
    val eventId: Int, val tick: Int, val value: RhythmValue, val positions: List<Position>,
    val chord: String?, val strum: Boolean, val tieIn: Boolean, val tieOut: Boolean,
    val expression: Event? = null, val tuplet: Tuplet? = null, val outgoing: Legato? = null, val linkMark: String? = null,
) {
    val rest get() = expression?.rest ?: positions.isEmpty()
    val end get() = tick + value.ticks
}
data class TabBar(val index: Int, val segments: List<TabSegment>)

fun TabBar.readableWidth(): Int {
    val ticks=(segments.lastOrNull()?.end ?: 48)-(segments.firstOrNull()?.tick ?: 0)
    // Temporal spacing must leave room for two-digit triplets and grace notes.
    val required=segments.zipWithNext().maxOfOrNull { (a,b) ->
        val gap=when {
            b.expression?.stringExpression?.values?.any { it.grace!=null }==true && b.tick==b.eventId -> 40
            (a.positions+b.positions).any { it.fret>=10 } -> 26
            else -> 22
        }
        kotlin.math.ceil(ticks.toDouble()*gap/(b.tick-a.tick)).toInt()
    } ?: 0
    return 68+maxOf(maxOf(4,segments.size)*28,required)
}
fun tabSystems(bars: List<TabBar>, width: Int): List<List<TabBar>> {
    val rows = mutableListOf<MutableList<TabBar>>()
    var used = 40
    for (bar in bars) {
        if (rows.isEmpty() || used + bar.readableWidth() > width && rows.last().isNotEmpty()) {
            rows.add(mutableListOf())
            used = 40
        }
        rows.last() += bar
        used += bar.readableWidth()
    }
    return rows
}

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
            val group=tuplets.firstOrNull { at>=it.tick && at<it.end }
            val value = if(group!=null) (if(group.unit==4) RhythmValue.TRIPLET_EIGHTH else RhythmValue.TRIPLET_SIXTEENTH) else RhythmValue.entries.firstOrNull {
                it!=RhythmValue.TRIPLET_EIGHTH && it!=RhythmValue.TRIPLET_SIXTEENTH && it.ticks <= room && at % (if(it==RhythmValue.DOTTED_EIGHTH) QUARTER else it.ticks) == 0
            }
                ?: error("Unsupported subdivision at tick $at")
            result[bar] += TabSegment(event.id, at, value, event.positions, event.chord,
                event.strum && at == event.tick, event.positions.isNotEmpty() && at > event.tick,
                event.positions.isNotEmpty() && at + value.ticks < end,event,group,
                links.firstOrNull { it.sourceTick==event.tick }.takeIf { at==event.tick },
                links.firstOrNull { it.sourceTick==event.tick }?.let(::linkMark).takeIf { at==event.tick })
            at += value.ticks
        }
    }
    return result.mapIndexed { index, segments -> TabBar(index, segments.sortedBy { it.tick }) }
}

/** Shared render cursor: audio, diagrams and notation consume the same absolute tick. */
data class Cursor(val pre: Boolean, val loop: Int, val localTick: Double, val bar: Int, val beat: Int)
fun Session.cursor(tick: Double): Cursor {
    val bounded = totalTicks?.let { tick.coerceIn(0.0, (it - 0.00001).coerceAtLeast(0.0)) } ?: tick.coerceAtLeast(0.0)
    val local = localTick(bounded)
    val pre=bounded<preTicks
    val bar=if(pre) range.first else sequence.barAt(local)
    return Cursor(pre,loopAt(bounded),local,bar,if(pre) local.toInt()/QUARTER else (local-sequence.barStart(bar)).toInt()/QUARTER)
}

/** Beam adjacent short notes within the beat; rests and beat boundaries break groups. */
fun beamCount(left: TabSegment?, right: TabSegment?): Int =
    if(left==null || right==null || left.rest || right.rest || left.end!=right.tick || left.tick/QUARTER!=right.tick/QUARTER || left.tuplet!=right.tuplet) 0
    else minOf(left.value.flags,right.value.flags)
