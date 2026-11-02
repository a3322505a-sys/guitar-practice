package com.a3322505a.guitarlearning.alphatab

/** Mix only: alphaTab still owns every voice, metronome event and count-in. */
internal object AlphaTone {
    // Raise both effective gains by 30% while keeping every engine control within 0..1:
    // guitar: 0.8 * 0.15 * 1.3 = 1.0 * 0.156; click: 0.8 * 0.9 * 1.3 = 1.0 * 0.936.
    const val MASTER = 1.0
    const val TRACK = 0.156
    const val CLICK = 0.936

    fun trackVolume(sourceVolume: Double, music: Double = 1.0, lead: Double = 1.0) =
        TRACK * (sourceVolume / 16.0).coerceIn(0.0, 1.0) * music * lead
}
