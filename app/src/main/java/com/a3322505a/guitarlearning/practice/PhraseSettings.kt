package com.a3322505a.guitarlearning.practice

import android.content.SharedPreferences

data class PhraseSettings(val bpm: Int,val repeats: Int,val range: BarRange)
class PhraseSettingsStore(private val prefs: SharedPreferences) {
    fun selected()=Phrases.practiceNamed(runCatching { prefs.getString("phrase-selected","S01") }.getOrNull() ?: "S01")
    fun load(phrase: Phrase,fallbackBpm: Int=60,fallbackRepeats: Int=4): PhraseSettings {
        val previous=runCatching { prefs.getString("phrase-selected",null) }.getOrNull()
            ?.takeIf { KafkaPractice.replacement(it)==phrase.id } ?: KafkaPractice.previousSelection(phrase.id)
        val settingsId=if(previous!=null && !prefs.contains("phrase.${phrase.id}.bpm")) previous else phrase.id
        fun read(key: String,default: Int)=runCatching { prefs.getInt("phrase.$settingsId.$key",default) }.getOrDefault(default)
        val whole=BarRange(0,phrase.sequence.barCount-1)
        val bpm=read("bpm",fallbackBpm).takeIf { it in 30..180 } ?: fallbackBpm
        val repeats=read("repeats",fallbackRepeats).takeIf { it in listOf(0,1,4,8) } ?: fallbackRepeats
        val saved=BarRange(read("first",whole.first),read("last",whole.last))
        val previousWhole=if(settingsId!=phrase.id) Phrases.named(settingsId).sequence.let { BarRange(0,it.barCount-1) } else null
        return PhraseSettings(bpm,repeats,if(saved==previousWhole || !saved.valid(phrase.sequence)) whole else saved)
    }
    fun save(phrase: Phrase,bpm: Int,repeats: Int,range: BarRange) {
        require(bpm in 30..180 && repeats in listOf(0,1,4,8) && range.valid(phrase.sequence))
        prefs.edit().putString("phrase-selected",phrase.id).putInt("phrase.${phrase.id}.bpm",bpm)
            .putInt("phrase.${phrase.id}.repeats",repeats).putInt("phrase.${phrase.id}.first",range.first)
            .putInt("phrase.${phrase.id}.last",range.last).apply()
    }
}
