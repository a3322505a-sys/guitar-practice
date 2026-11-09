package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.score.Q
import kotlin.math.roundToInt

/** All user speeds are BPM. The original tempo map is uniformly scaled, never flattened. */
object TempoControl {
    const val MIN_BPM = 20
    const val MAX_BPM = 240
    // alphaTab 1.8.4 clamps synth speed to this range. Larger ratios are
    // split between temporary MIDI tempo scaling and the synth multiplier.
    const val ENGINE_MIN_SPEED = .125
    const val ENGINE_MAX_SPEED = 8.0
    fun engineSpeed(ratio: Double) = ratio.coerceIn(ENGINE_MIN_SPEED, ENGINE_MAX_SPEED)
    fun sourceBpm(sequence: Sequence, visit: Int = 0): Double {
        val plan = sequence.runtimePlan ?: return 60.0
        val at = plan.visits.getOrNull(visit)?.start ?: Q.ZERO
        return sourceBpmAt(sequence,at.value())
    }
    fun sourceBpmAt(sequence: Sequence, quarter: Double): Double {
        val plan = sequence.runtimePlan ?: return 60.0
        val tempo = plan.tempos.last { it.at.value() <= quarter }
        return tempo.bpm * tempo.beatUnit.value()
    }
    fun effective(sequence: Sequence, visit: Int, ratio: Double) = (sourceBpm(sequence, visit) * ratio).roundToInt()
    fun ratio(bpm: Int, reference: Double): Double {
        return bpm.coerceIn(MIN_BPM, MAX_BPM) / reference
    }
}
