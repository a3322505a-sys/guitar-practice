@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.model.Score
import alphaTab.model.Automation

/** Engraving choices shared by accepted practices and imported GP scores. */
object AlphaNotation {
    /** The SDK generates MIDI synchronously. Fixed practice speed must never rewrite
     * the source score's printed tempo, including after rotation or a track switch. */
    fun <T> withPlaybackTempo(score: Score, sourceTempo: Boolean, bpm: Int,
        tempoScale: Double = 1.0, generate: () -> T): T {
        if (sourceTempo && tempoScale == 1.0) return generate()
        val original = score.masterBars.toList().map { it.tempoAutomations }
        val values = original.map { events -> events.toList().map { it.value } }
        try {
            score.masterBars.forEachIndexed { index, bar ->
                if (sourceTempo) bar.tempoAutomations.forEach { it.value *= tempoScale }
                else bar.tempoAutomations = if (index == 0) alphaTab.collections.List(
                    Automation.buildTempoAutomation(false, 0.0, bpm * tempoScale, 2.0, false))
                else alphaTab.collections.List()
            }
            return generate()
        } finally {
            score.masterBars.forEachIndexed { index, bar ->
                bar.tempoAutomations = original[index]
                bar.tempoAutomations.forEachIndexed { event, automation -> automation.value = values[index][event] }
            }
        }
    }

    fun prepare(score: Score) {
        score.tracks.forEach { track -> track.staves.forEach { staff ->
            if (!staff.isPercussion && staff.tuning.length > 0) {
                staff.showStandardNotation = false
                staff.showTablature = true
                staff.showSlash = false
                staff.showNumbered = false
            }
        } }
        // The SDK plays its default tempo when the opening bar has no automation.
        // Making that same event explicit supplies the required initial printed tempo.
        score.masterBars.firstOrNull()?.takeIf { it.tempoAutomations.length == 0.0 }?.let { first ->
            first.tempoAutomations.push(Automation.buildTempoAutomation(false, 0.0, score.tempo, 2.0))
        }
        // GP tempo automations remain intact for repeats and MIDI. Only their engraving
        // visibility changes; each real change keeps its original in-bar ratioPosition.
        var previousTempo: Double? = null
        var previousText = ""
        score.masterBars.forEach { bar ->
            bar.tempoAutomations.toList().sortedBy { it.ratioPosition }.forEach { automation ->
                val needed = previousTempo == null || automation.value != previousTempo ||
                    automation.text.isNotBlank() && automation.text != previousText
                automation.isVisible = needed
                previousTempo = automation.value
                previousText = automation.text
            }
        }
        // Capo and tuning stay on each original staff. alphaTab's shared effect bands
        // render the capo at that staff's first bar, including when only TAB is visible.
    }
}
