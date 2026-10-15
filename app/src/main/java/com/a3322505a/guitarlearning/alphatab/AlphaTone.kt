package com.a3322505a.guitarlearning.alphatab

/** Mix only: alphaTab still owns every voice, metronome event and count-in. */
internal object AlphaTone {
    const val MASTER = 0.8
    const val TRACK = 0.15
    const val CLICK = 0.9

    fun trackVolume(sourceVolume: Double) = TRACK * (sourceVolume / 16.0).coerceIn(0.0, 1.0)
}
