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
    @Test fun integerTargetsAndGlobalCursorOffsetSurviveNewStoresAndKeepSeparateScopes() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("v111-settings-test",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store=PhraseSettingsStore(prefs)
        val phrase=SongTestLibrary.sourceMozart[0].copy(assetKey=com.a3322505a.guitarlearning.music.score.PracticeKey("test.tempo","one"))
        val reference=TempoControl.sourceBpm(phrase.sequence)
        store.saveTempo(phrase,true,75.0)
        val migrated=kotlin.math.round(reference*.75).toInt().coerceIn(TempoControl.minimum(reference),TempoControl.maximum(reference))
        assertEquals(migrated,store.targetBpm(phrase))
        store.saveTargetBpm(phrase,20)
        assertEquals(20,PhraseSettingsStore(prefs).targetBpm(phrase))
        val offset=CursorSyncSettings(prefs)
        assertEquals(0,offset.load())
        offset.save(-170)
        assertEquals(-170,CursorSyncSettings(prefs).load())
        store.saveTargetBpm(phrase,21)
        assertEquals(-170,CursorSyncSettings(prefs).load())
        offset.save(900)
        assertEquals(500,CursorSyncSettings(prefs).load())
        offset.save(0)
        assertEquals(0,CursorSyncSettings(prefs).load())
    }
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
