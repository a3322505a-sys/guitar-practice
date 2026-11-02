package com.a3322505a.guitarlearning.audio

import android.content.SharedPreferences

/** Music is a track gain; the engine's master also contains clicks and must stay independent. */
data class PlaybackMix(val music: Double = 1.0, val metronome: Double = 1.0) {
    init { require(music in 0.0..1.0 && metronome in 0.0..1.0) }
    companion object {
        fun load(prefs: SharedPreferences): PlaybackMix {
            fun gain(key: String) = if (prefs.contains(key))
                prefs.getFloat(key, 1f).toDouble().coerceIn(0.0, 1.0)
            else 1.0
            // Old on/off switches never represented an independent mix level.
            // In particular, click=false must not persist a zero count-in gain.
            return PlaybackMix(gain("music-volume"), gain("metronome-volume"))
        }
    }
    fun save(prefs: SharedPreferences) {
        prefs.edit().putFloat("music-volume", music.toFloat()).putFloat("metronome-volume", metronome.toFloat()).apply()
    }
}
