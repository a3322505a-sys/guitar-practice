package com.a3322505a.guitarlearning.alphatab

/** Mix only: alphaTab still owns every voice, metronome event and count-in. */
internal object AlphaTone {
    const val MASTER = 1.0
    const val TRACK = 0.42
    const val CLICK = 1.60
    const val MIN_COUNT_IN_MIX = 0.20

    fun trackVolume(sourceVolume: Double, music: Double = 1.0, lead: Double = 1.0) =
        TRACK * (sourceVolume / 16.0).coerceIn(0.0, 1.0) * music.coerceIn(0.0, 1.0) * lead.coerceIn(0.0, 1.0)

    fun clickVolume(metronome: Double = 1.0) = CLICK * metronome.coerceIn(0.0, 1.0)

    fun countInVolume(metronome: Double = 1.0): Double {
        // Legacy no-UI releases could persist metronome-volume == 0.0; self-heal to 1.0 so count-in never goes silent.
        val effective = if (metronome <= 0.0) 1.0 else metronome.coerceIn(MIN_COUNT_IN_MIX, 1.0)
        return CLICK * effective
    }
}
