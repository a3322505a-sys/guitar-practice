package com.a3322505a.guitarlearning

import android.content.Context
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PhraseSettingsTest {
    @Test fun eachPhraseRestoresItsOwnSettingsAndInvalidValuesFallBackWithoutClearingOthers() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("kernel-test",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store=PhraseSettingsStore(prefs);val a=SongTestLibrary.sourceMozart[0];val b=SongTestLibrary.sourceMozart[1]
        store.save(a,73,8,BarRange(2,3));store.save(b,91,1,BarRange(4,4))
        assertEquals(PhraseSettings(73,8,BarRange(2,3)),PhraseSettingsStore(prefs).load(a))
        assertEquals(PhraseSettings(91,1,BarRange(4,4)),PhraseSettingsStore(prefs).load(b))
        prefs.edit().putInt("phrase.${a.id}.bpm",999).putString("phrase.${a.id}.repeats","wrong type").putInt("phrase.${a.id}.last",100).putString("phrase-selected","unknown").commit()
        assertEquals(PhraseSettings(60,4,BarRange(0,7)),store.load(a))
        assertEquals(91,store.load(b).bpm)
        assertEquals("startup",store.selected().id)
    }
}
