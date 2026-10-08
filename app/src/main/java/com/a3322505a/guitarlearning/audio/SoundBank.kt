package com.a3322505a.guitarlearning.audio

/** Only sample storage and articulation live here; practice semantics remain unchanged. */
data class SoundBank(val folder: String, val roots: List<Int>, val sampleRate: Int, val attackMs: Double, val releaseMs: Int) {
    companion object {
        val ELECTRIC = SoundBank("guitar", listOf(36,41,45,48,52,55,59,64,67,71,74,80,85), 22050, 8.0, 60)
        val NYLON = SoundBank("guitar-nylon", listOf(40,43,45,47,48,50,52,53,55,57,59,60,62,64,65,67,69,71,72,74,76,77), 22050, 1.5, 90)
    }
}
