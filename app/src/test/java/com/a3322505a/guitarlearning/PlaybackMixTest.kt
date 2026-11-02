package com.a3322505a.guitarlearning
import com.a3322505a.guitarlearning.audio.PlaybackMix
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PlaybackMixTest {
    @Test fun oldClickOffDoesNotCreatePermanentMuteAcrossColdLoadsOrSwitchChanges() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("v114-old-mix",0)
        prefs.edit().clear().putBoolean("click",false).putBoolean("guitar",false).commit()
        repeat(2) { assertEquals(PlaybackMix(),PlaybackMix.load(prefs)) }
        assertFalse(prefs.contains("metronome-volume"))
        prefs.edit().putBoolean("click",true).commit()
        assertEquals(PlaybackMix(),PlaybackMix.load(prefs))
        assertFalse(prefs.getBoolean("guitar",true))
    }
    @Test fun explicitNonzeroAndZeroGainsSurviveColdLoadsAndClickToggles() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("v114-explicit-mix",0)
        for(gain in listOf(.37,0.0)) {
            prefs.edit().clear().commit()
            PlaybackMix(.63,gain).save(prefs)
            for(click in listOf(false,true,false)) {
                prefs.edit().putBoolean("click",click).commit()
                val loaded=PlaybackMix.load(prefs)
                assertEquals(.63,loaded.music,1e-6)
                assertEquals(gain,loaded.metronome,1e-6)
            }
        }
    }
}
