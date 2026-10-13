package com.a3322505a.guitarlearning.practice

import android.content.SharedPreferences

data class PhraseSettings(val bpm: Int,val repeats: Int,val range: BarRange)
class PhraseSettingsStore(private val prefs: SharedPreferences) {
    fun selected()=Phrases.named(runCatching { prefs.getString("phrase-selected","S01") }.getOrNull() ?: "S01")
    fun load(phrase: Phrase,fallbackBpm: Int=60,fallbackRepeats: Int=4): PhraseSettings {
        fun read(key: String,default: Int)=runCatching { prefs.getInt("phrase.${phrase.id}.$key",default) }.getOrDefault(default)
        val whole=BarRange(0,phrase.sequence.barCount-1)
        val bpm=read("bpm",fallbackBpm).takeIf { it in 30..180 } ?: fallbackBpm
        val repeats=read("repeats",fallbackRepeats).takeIf { it in listOf(0,1,4,8) } ?: fallbackRepeats
        val saved=BarRange(read("first",whole.first),read("last",whole.last))
        return PhraseSettings(bpm,repeats,if(saved.valid(phrase.sequence)) saved else whole)
    }
    fun save(phrase: Phrase,bpm: Int,repeats: Int,range: BarRange) {
        require(bpm in 30..180 && repeats in listOf(0,1,4,8) && range.valid(phrase.sequence))
        prefs.edit().putString("phrase-selected",phrase.id).putInt("phrase.${phrase.id}.bpm",bpm)
            .putInt("phrase.${phrase.id}.repeats",repeats).putInt("phrase.${phrase.id}.first",range.first)
            .putInt("phrase.${phrase.id}.last",range.last).apply()
    }
}
