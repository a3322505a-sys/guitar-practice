package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position

/** Shared render cursor: audio, diagrams and notation consume the same absolute tick. */
data class Cursor(val pre: Boolean, val loop: Int, val localTick: Double, val bar: Int, val beat: Int,
    val frame: Long = 0, val visitId: String? = null, val measureId: String? = null,
    val activeNoteIds: Set<String> = emptySet(), val positions: List<Position> = emptyList(),
    val movingPositions: List<MovingPosition> = emptyList(),val leadIn: Boolean = false)
