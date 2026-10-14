package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position

enum class RhythmValue(val ticks: Int, val flags: Int = 0) {
    WHOLE(48), HALF(24), QUARTER(12), DOTTED_EIGHTH(9,1), EIGHTH(6,1), SIXTEENTH(3,2), THIRTY_SECOND(0,3), SIXTY_FOURTH(0,4), TRIPLET_EIGHTH(4,1), TRIPLET_SIXTEENTH(2,2), TRIPLET_THIRTY_SECOND(1,3);
    fun duration(quarter: Int) = if(this==THIRTY_SECOND) (if(quarter==24) 3 else 0) else ticks*quarter/12
}

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
            val group=tuplets.firstOrNull { at>=it.tick && at<it.end }
            val value = if(group!=null) when(group.unit*12/ticksPerQuarter) {
                4 -> RhythmValue.TRIPLET_EIGHTH
                2 -> RhythmValue.TRIPLET_SIXTEENTH
                else -> RhythmValue.TRIPLET_THIRTY_SECOND
            } else RhythmValue.entries.firstOrNull {
                !it.name.startsWith("TRIPLET_") && it.duration(ticksPerQuarter)>0 && it.duration(ticksPerQuarter) <= room && at % (if(it==RhythmValue.DOTTED_EIGHTH) ticksPerQuarter else it.duration(ticksPerQuarter)) == 0
            }
                ?: error("Unsupported subdivision at tick $at")
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

/** Shared render cursor: audio, diagrams and notation consume the same absolute tick. */
data class Cursor(val pre: Boolean, val loop: Int, val localTick: Double, val bar: Int, val beat: Int,
    val frame: Long = 0, val visitId: String? = null, val measureId: String? = null,
    val activeNoteIds: Set<String> = emptySet(), val positions: List<Position> = emptyList(),
    val movingPositions: List<MovingPosition> = emptyList(),val leadIn: Boolean = false)
fun Session.cursor(tick: Double): Cursor {
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

/** Beam adjacent short notes within the beat; rests and beat boundaries break groups. */
fun beamCount(left: TabSegment?, right: TabSegment?): Int =
    if(left==null || right==null || left.rest || right.rest || left.end!=right.tick || left.tick/left.ticksPerQuarter!=right.tick/right.ticksPerQuarter || left.tuplet!=right.tuplet) 0
    else minOf(left.value.flags,right.value.flags)
