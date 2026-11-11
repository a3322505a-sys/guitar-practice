package com.a3322505a.guitarlearning.alphatab

internal data class PlaybackTime(val positionMillis: Double = 0.0, val durationMillis: Double = 0.0)

/** One pass of the selected native MIDI range. Looping reuses this same clock. */
internal class PlaybackTimeline(map: CursorTempoMap, firstTick: Double, lastTick: Double, speed: Double) {
    private val startMillis = map.millisAt(firstTick) / speed
    val durationMillis = ((map.millisAt(lastTick) - map.millisAt(firstTick)) / speed).coerceAtLeast(0.0)
    // alphaTab timePosition is already wall time at playbackSpeed.
    fun elapsedMillis(nativeMillis: Double) = (nativeMillis - startMillis).coerceIn(0.0, durationMillis)
}
