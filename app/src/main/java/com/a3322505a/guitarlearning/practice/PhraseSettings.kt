package com.a3322505a.guitarlearning.practice

import android.content.SharedPreferences

data class PhraseSettings(val bpm: Int,val repeats: Int,val range: BarRange,val rangeReset: Boolean = false)
class PhraseSettingsStore(private val prefs: SharedPreferences,private val resolve: (String)->Phrase = Phrases::practiceNamed) {
    fun tempo(phrase: Phrase): Pair<Boolean,Int> {
        if(phrase.assetKey==null) return false to 100
        val prefix=assetPrefix(phrase)
        val default=if(phrase.unprepared) phrase.defaultSourceTempo else phrase.sequence.performance.practice.tempoMode!="fixed"
        val source=runCatching { prefs.getBoolean(prefix+"sourceTempo",default) }.getOrDefault(default)
        val multiplier=runCatching { prefs.getInt(prefix+"multiplier",100) }.getOrDefault(100).coerceIn(25,200)
        return source to multiplier
    }
    fun saveTempo(phrase: Phrase,source: Boolean,multiplier: Int) {
        if(phrase.assetKey==null) return
        val prefix="asset."+phrase.assetKey.encoded+"."
        prefs.edit().putBoolean(prefix+"sourceTempo",source).putInt(prefix+"multiplier",multiplier.coerceIn(25,200)).apply()
    }
    fun selected(): Phrase {
        val pending=runCatching { prefs.getString("phrase-pending-import",null) }.getOrNull()
        if(pending!=null && resolve(pending).assetKey!=null) return resolve(pending)
        val id=runCatching { prefs.getString("phrase-selected","M01") }.getOrNull() ?: "M01"
        val selected=resolve(id)
        if(id in MigratedPracticeIds.targets && selected.id!=MigratedPracticeIds.targets[id])
            prefs.edit().putString("phrase-pending-import",id).apply()
        return selected
    }
    fun select(phrase: Phrase) { prefs.edit().remove("phrase-pending-import").putString("phrase-selected",phrase.id).apply() }
    fun load(phrase: Phrase,fallbackBpm: Int=60,fallbackRepeats: Int=4): PhraseSettings {
        if(phrase.assetKey!=null) {
            migrate(phrase,fallbackBpm,fallbackRepeats)
            return loadAsset(phrase)
        }
        val settingsId=phrase.id
        fun read(key: String,default: Int)=runCatching { prefs.getInt("phrase.$settingsId.$key",default) }.getOrDefault(default)
        val whole=BarRange(0,phrase.sequence.barCount-1)
        val bpm=read("bpm",fallbackBpm).takeIf { it in 30..180 } ?: fallbackBpm
        val repeats=read("repeats",fallbackRepeats).takeIf { it in listOf(0,1,4,8) } ?: fallbackRepeats
        val saved=BarRange(read("first",whole.first),read("last",whole.last))
        return PhraseSettings(bpm,repeats,if(!saved.valid(phrase.sequence)) whole else saved)
    }
    fun save(phrase: Phrase,bpm: Int,repeats: Int,range: BarRange) {
        require(bpm in 30..180 && repeats in listOf(0,1,4,8) && range.valid(phrase.sequence))
        if(phrase.assetKey!=null) {
            val prefix="asset."+phrase.assetKey.encoded+"."
            val plan=phrase.sequence.performance
            prefs.edit().putString("phrase-selected",phrase.id).putInt(prefix+"bpm",bpm).putInt(prefix+"repeats",repeats)
                .putString(prefix+"first",anchor(phrase,range.first)).putString(prefix+"last",anchor(phrase,range.last))
                .putString(prefix+"route",plan.routeFingerprint).apply()
            return
        }
        prefs.edit().putString("phrase-selected",phrase.id).putInt("phrase.${phrase.id}.bpm",bpm)
            .putInt("phrase.${phrase.id}.repeats",repeats).putInt("phrase.${phrase.id}.first",range.first)
            .putInt("phrase.${phrase.id}.last",range.last).apply()
    }
    private fun anchor(phrase: Phrase,index: Int): String {
        val plan=phrase.sequence.performance;val v=plan.visits[index]
        val sourceIndex=plan.score.measures.indexOfFirst { it.id==v.measure.id }
        val offset=if(sourceIndex<0) v.sourceStart else v.sourceStart-plan.score.measureStarts[sourceIndex]
        return v.measure.id+":"+v.occurrence+":"+offset+":"+v.duration
    }
    private fun migrate(phrase: Phrase,fallbackBpm: Int,fallbackRepeats: Int) {
        val ids=listOfNotNull(phrase.legacyId)+phrase.legacyIds
        if(ids.isEmpty()) return
        val selected=runCatching { prefs.getString("phrase-selected",null) }.getOrNull()
        val legacy=selected?.takeIf { it in ids && prefs.contains("phrase.$it.bpm") }
            ?: ids.firstOrNull { prefs.contains("phrase.$it.bpm") } ?: ids.first()
        val prefix="asset."+phrase.assetKey!!.encoded+"."
        if(phrase.unprepared || prefs.contains(prefix+"bpm")) return
        fun number(key: String,default: Int)=runCatching { prefs.getInt("phrase.$legacy.$key",default) }.getOrDefault(default)
        val bpm=number("bpm",fallbackBpm).takeIf { it in 30..180 } ?: fallbackBpm
        val repeats=number("repeats",fallbackRepeats).takeIf { it in listOf(0,1,4,8) } ?: fallbackRepeats
        val whole=BarRange(0,phrase.sequence.barCount-1)
        val saved=BarRange(number("first",whole.first),number("last",whole.last))
        val range=saved.takeIf { it.valid(phrase.sequence) } ?: whole
        prefs.edit().putInt(prefix+"bpm",bpm).putInt(prefix+"repeats",repeats).putBoolean(prefix+"sourceTempo",false)
            .putString(prefix+"first",anchor(phrase,range.first)).putString(prefix+"last",anchor(phrase,range.last))
            .putString(prefix+"route",phrase.sequence.performance.routeFingerprint).apply()
    }
    private fun loadAsset(phrase: Phrase): PhraseSettings {
        val prefix=assetPrefix(phrase)
        fun number(key: String,default: Int)=runCatching { prefs.getInt(prefix+key,default) }.getOrDefault(default)
        val bpm=number("bpm",phrase.suggestedBpm).takeIf { it in 30..180 } ?: phrase.suggestedBpm
        val repeats=number("repeats",4).takeIf { it in listOf(0,1,4,8) } ?: 4
        val whole=BarRange(0,phrase.sequence.barCount-1)
        if(phrase.unprepared) return PhraseSettings(bpm,repeats,whole)
        val first=runCatching { prefs.getString(prefix+"first",null) }.getOrNull()
        val last=runCatching { prefs.getString(prefix+"last",null) }.getOrNull()
        val plan=phrase.sequence.performance
        val a=plan.visits.indices.indexOfFirst { anchor(phrase,it)==first }
        val b=plan.visits.indices.indexOfFirst { anchor(phrase,it)==last }
        val valid=a>=0 && b>=a
        return PhraseSettings(bpm,repeats,if(valid) BarRange(a,b) else whole,first!=null && !valid)
    }
    private fun assetPrefix(phrase: Phrase): String {
        val current="asset."+phrase.assetKey!!.encoded+"."
        val selected=runCatching { prefs.getString("phrase-selected",null) }.getOrNull()
        return if(!prefs.contains(current+"bpm") && selected!=null && selected!=phrase.id && resolve(selected).id==phrase.id)
            "asset."+selected+"." else current
    }
}
