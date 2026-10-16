package com.a3322505a.guitarlearning.practice

/** Accepted source subdivisions, also used by the debug notation comparison. */
enum class RhythmValue(val ticks: Int, val flags: Int = 0) {
    WHOLE(48), HALF(24), QUARTER(12), DOTTED_EIGHTH(9,1), EIGHTH(6,1), SIXTEENTH(3,2), THIRTY_SECOND(0,3), SIXTY_FOURTH(0,4), TRIPLET_EIGHTH(4,1), TRIPLET_SIXTEENTH(2,2), TRIPLET_THIRTY_SECOND(1,3);
    fun duration(quarter: Int) = if(this==THIRTY_SECOND) (if(quarter==24) 3 else 0) else ticks*quarter/12
}

/** Preserve the catalogue's existing rhythm acceptance without constructing TAB glyphs. */
internal fun Sequence.validateTabRhythm() {
    validateExpression()
    require(barMeters.isNotEmpty() || length % barTicks == 0)
    for(event in events) {
        var at=event.tick
        val end=event.tick+event.duration
        while(at<end) {
            val bar=barAt(at.toDouble())
            val room=minOf(end-at,barEnd(bar)-at)
            at+=rhythmAt(at,room).first.duration(ticksPerQuarter)
        }
    }
}

internal fun Sequence.rhythmAt(at: Int, room: Int): Pair<RhythmValue, Tuplet?> {
    val group=tuplets.firstOrNull { at>=it.tick && at<it.end }
    val value=if(group!=null) when(group.unit*12/ticksPerQuarter) {
        4 -> RhythmValue.TRIPLET_EIGHTH
        2 -> RhythmValue.TRIPLET_SIXTEENTH
        else -> RhythmValue.TRIPLET_THIRTY_SECOND
    } else RhythmValue.entries.firstOrNull {
        !it.name.startsWith("TRIPLET_") && it.duration(ticksPerQuarter)>0 && it.duration(ticksPerQuarter)<=room &&
            at % (if(it==RhythmValue.DOTTED_EIGHTH) ticksPerQuarter else it.duration(ticksPerQuarter))==0
    } ?: error("Unsupported subdivision at tick $at")
    return value to group
}
