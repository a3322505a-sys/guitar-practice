package com.a3322505a.guitarlearning.audio

import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*

/** The workbench consumes state, never an audio clock or output implementation. */
interface PracticePlayback {
    var guitar: Boolean
    var click: Boolean
    val progress: Progress
    val session: Session?
    val active: Boolean
    val cursor: Cursor? get() = session?.cursor(progress.tick)
    fun start(sequence: Sequence, bpm: Int, follow: Boolean, repeats: Int,
        range: BarRange = BarRange(0, sequence.barCount - 1),
        sourceTempo: Boolean = sequence.runtimePlan != null, multiplier: Double = 1.0)
    fun resume()
    fun restart()
    fun pause()
    fun invalidate()
    fun stop()
    fun audition(positions: List<Position>, strum: Boolean = false, capoFret: Int = 0,
        openStringMidi: List<Int> = listOf(64, 59, 55, 50, 45, 40))
    fun release()
}
