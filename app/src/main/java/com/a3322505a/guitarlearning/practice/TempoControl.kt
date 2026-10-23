package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.score.Q
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.floor

/** All user speeds are BPM. The original tempo map is uniformly scaled, never flattened. */
object TempoControl {
    // Verified against alphaTab 1.8.4 AlphaSynthBase/SynthConstants, and tested
    // with the real synth. Keep the existing 2x UI ceiling.
    const val ENGINE_MIN_SPEED = .125
    const val MAX_SPEED = 2.0
    const val TARGET_MIN_BPM = 20
    fun minimum(reference: Double) = maxOf(TARGET_MIN_BPM, ceil(reference * ENGINE_MIN_SPEED).toInt())
    fun maximum(reference: Double) = floor(reference * MAX_SPEED).toInt().coerceAtLeast(minimum(reference))
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
        require(reference.isFinite() && reference > 0 && bpm in minimum(reference)..maximum(reference))
        return bpm / reference
    }
}
