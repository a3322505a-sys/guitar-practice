package com.a3322505a.guitarlearning.alphatab

import com.a3322505a.guitarlearning.music.score.Meter
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.practice.compile.PerformancePlan

/** 单个预备拍点的语义描述，供方案 A 视觉提示纯只读渲染。 */
data class CountInBeat(
    val index: Int,
    val displayNumber: Int,
    val barIndex: Int,
    val totalBars: Int,
    val barSize: Int,
    val isAccent: Boolean,
    val isLeadIn: Boolean,
    val isFinalBeat: Boolean,
)

data class CountInPlan(
    val meter: Meter,
    val leadInBeats: Int,
    val entrySlotInBar: Int,
    val beats: List<CountInBeat>,
) {
    val totalClicks: Int get() = beats.size
    val groupStarts: Set<Int> get() = groupStartIndices(meter)

    companion object {
        /** 计算每个强拍/拍组首拍在 1..numerator 中的位置。 */
        fun groupStartIndices(meter: Meter): Set<Int> {
            val groups = meter.beatGroups
                .takeIf { it.isNotEmpty() && it.sum() == meter.numerator && it.any { g -> g > 1 } }
                ?: defaultGroups(meter.numerator, meter.denominator)
            val result = mutableSetOf<Int>()
            var pos = 1
            for (g in groups) {
                result += pos
                pos += g
            }
            return result
        }

        private fun defaultGroups(numerator: Int, denominator: Int): List<Int> = when {
            denominator == 8 && numerator % 3 == 0 && numerator >= 6 -> List(numerator / 3) { 3 }
            else -> listOf(numerator.coerceAtLeast(1))
        }

        /** 从编译结果的首个访问小节提取真实弱起前置拍长（四分音符为单位）。 */
        fun leadInForFirstVisit(plan: PerformancePlan): Q {
            val v0 = plan.visits.firstOrNull() ?: return Q.ZERO
            if (v0.duration >= v0.measure.meter.duration) return Q.ZERO
            val explicit = v0.measure.leadIn + (plan.score.measureIndices[v0.measure.id]?.let {
                v0.sourceStart - plan.score.measureStarts[it]
            } ?: Q.ZERO)
            return if (explicit > Q.ZERO) explicit else (v0.measure.meter.duration - v0.duration)
        }

        /** 仅当弱起前空缺恰好为分母拍的整数倍且小于一整小节时返回 >0，避免分数拍破坏节拍网格。 */
        fun exactLeadInBeats(meter: Meter, leadInQuarters: Q): Int {
            if (leadInQuarters <= Q.ZERO) return 0
            val inDenominatorUnits = leadInQuarters * Q(meter.denominator.toLong(), 4L)
            if (inDenominatorUnits.d != 1L) return 0
            val count = inDenominatorUnits.n.toInt()
            return if (count in 1 until meter.numerator) count else 0
        }

        /** 计算某小节作为起播点时，MIDI TimeSignatureEvent 应写入的预备拍总声数。 */
        fun midiNumeratorFor(meter: Meter, leadInQuarters: Q = Q.ZERO): Int {
            val baseBars = if (meter.numerator <= 2) 2 else 1
            val baseClicks = meter.numerator.coerceAtLeast(1) * baseBars
            val leadInBeats = exactLeadInBeats(meter, leadInQuarters)
            return baseClicks + leadInBeats
        }

        fun forMeter(meter: Meter, leadInQuarters: Q = Q.ZERO): CountInPlan {
            val n = meter.numerator.coerceAtLeast(1)
            val baseBars = if (n <= 2) 2 else 1
            val leadIn = exactLeadInBeats(meter, leadInQuarters)
            val accents = groupStartIndices(meter)
            val totalBars = baseBars + if (leadIn > 0) 1 else 0
            val raw = mutableListOf<CountInBeat>()
            var idx = 1
            repeat(baseBars) { b ->
                for (slot in 1..n) {
                    raw += CountInBeat(
                        index = idx++,
                        displayNumber = slot,
                        barIndex = b,
                        totalBars = totalBars,
                        barSize = n,
                        isAccent = slot in accents,
                        isLeadIn = false,
                        isFinalBeat = false,
                    )
                }
            }
            if (leadIn > 0) {
                for (slot in 1..leadIn) {
                    raw += CountInBeat(
                        index = idx++,
                        displayNumber = slot,
                        barIndex = baseBars,
                        totalBars = totalBars,
                        barSize = n,
                        isAccent = slot in accents,
                        isLeadIn = true,
                        isFinalBeat = false,
                    )
                }
            }
            val finalBeats = raw.mapIndexed { i, b -> b.copy(isFinalBeat = i == raw.lastIndex) }
            return CountInPlan(
                meter = meter,
                leadInBeats = leadIn,
                entrySlotInBar = if (leadIn > 0) leadIn + 1 else 1,
                beats = finalBeats,
            )
        }
    }
}
