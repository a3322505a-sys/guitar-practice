package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position

enum class RhythmValue(val ticks: Int) { WHOLE(48), HALF(24), QUARTER(12), EIGHTH(6) }

/** Engraving segments reference the original event; they never create audio onsets. */
data class TabSegment(
    val eventId: Int, val tick: Int, val value: RhythmValue, val positions: List<Position>,
    val chord: String?, val strum: Boolean, val tieIn: Boolean, val tieOut: Boolean,
) {
    val rest get() = positions.isEmpty()
    val end get() = tick + value.ticks
}
data class TabBar(val index: Int, val segments: List<TabSegment>)

fun TabBar.readableWidth() = 68 + maxOf(4, segments.size) * 28
fun tabSystems(bars: List<TabBar>, width: Int): List<List<TabBar>> {
    val rows = mutableListOf<MutableList<TabBar>>()
    var used = 32
    for (bar in bars) {
        if (rows.isEmpty() || used + bar.readableWidth() > width && rows.last().isNotEmpty()) {
            rows.add(mutableListOf())
            used = 32
        }
        rows.last() += bar
        used += bar.readableWidth()
    }
    return rows
}

fun Sequence.tabBars(): List<TabBar> {
    require(length % BAR == 0)
    val result = List(length / BAR) { mutableListOf<TabSegment>() }
    for (event in events) {
        var at = event.tick
        val end = event.tick + event.duration
        while (at < end) {
            val room = minOf(end - at, BAR - at % BAR)
            // Start long values on their natural metric boundary. Eighths on weak beats
            // tie into the next beat rather than disguising syncopation as a long value.
            val value = RhythmValue.entries.firstOrNull { it.ticks <= room && at % it.ticks == 0 }
                ?: error("Unsupported subdivision at tick $at")
            result[at / BAR] += TabSegment(event.id, at, value, event.positions, event.chord,
                event.strum && at == event.tick, event.positions.isNotEmpty() && at > event.tick,
                event.positions.isNotEmpty() && at + value.ticks < end)
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
    return Cursor(bounded < preTicks, loopAt(bounded), local, local.toInt() / BAR, local.toInt() % BAR / QUARTER)
}
