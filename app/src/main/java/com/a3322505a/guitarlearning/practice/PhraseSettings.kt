package com.a3322505a.guitarlearning.practice

import android.content.SharedPreferences

data class PhraseSettings(val bpm: Int,val repeats: Int,val range: BarRange,val rangeReset: Boolean = false)
class PhraseSettingsStore(private val prefs: SharedPreferences,private val resolve: (String)->Phrase = ::unavailablePhrase) {
    /** New stage speeds persist as integers. Read the former ratio once when migrating. */
    fun targetBpm(phrase: Phrase): Int {
        val reference = TempoControl.sourceBpm(phrase.sequence)
        val saved = runCatching { prefs.getInt(assetPrefix(phrase)+"targetBpm", 0) }.getOrDefault(0)
        val old = tempo(phrase)
        val migrated = if (old.first || phrase.sequence.runtimePlan?.tempos?.size?.let { it > 1 } == true)
            kotlin.math.round(reference * old.second / 100).toInt() else load(phrase).bpm
        return (if (saved > 0) saved else migrated).coerceIn(TempoControl.minimum(reference), TempoControl.maximum(reference))
    }
    fun saveTargetBpm(phrase: Phrase, bpm: Int) {
        TempoControl.ratio(bpm, TempoControl.sourceBpm(phrase.sequence))
        prefs.edit().putInt(assetPrefix(phrase)+"targetBpm", bpm).apply()
    }
    fun tempo(phrase: Phrase): Pair<Boolean,Double> {
        if(phrase.assetKey==null) return false to 100.0
        val prefix=assetPrefix(phrase)
        val default=if(phrase.unprepared) phrase.defaultSourceTempo else phrase.sequence.performance.practice.tempoMode!="fixed"
        val source=runCatching { prefs.getBoolean(prefix+"sourceTempo",default) }.getOrDefault(default)
        val multiplier=runCatching { if(prefs.contains(prefix+"speedPercent")) prefs.getFloat(prefix+"speedPercent",100f).toDouble() else prefs.getInt(prefix+"multiplier",100).toDouble() }.getOrDefault(100.0).coerceIn(25.0,200.0)
        return source to multiplier
    }
    fun saveTempo(phrase: Phrase,source: Boolean,multiplier: Double) {
        if(phrase.assetKey==null) return
        val prefix="asset."+phrase.assetKey.encoded+"."
        prefs.edit().putBoolean(prefix+"sourceTempo",source).putFloat(prefix+"speedPercent",multiplier.coerceIn(25.0,200.0).toFloat()).apply()
    }
    fun selected(): Phrase {
        val initial=com.a3322505a.guitarlearning.music.score.PracticeKey("local.teaching-native","FB-1").encoded
        val requested=runCatching { prefs.getString("phrase-selected",initial) }.getOrNull() ?: initial
        return resolve(requested)
    }
    fun select(phrase: Phrase) { prefs.edit().remove("phrase-pending-import").putString("phrase-selected",phrase.id).apply() }
    fun load(phrase: Phrase,fallbackBpm: Int=60,fallbackRepeats: Int=4): PhraseSettings {
        if(phrase.assetKey!=null) {
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
        val bpmRange = if (phrase.assetKey == null) 30..180 else TempoControl.sourceBpm(phrase.sequence).let {
            TempoControl.minimum(it)..TempoControl.maximum(it)
        }
        require(bpm in bpmRange && repeats in listOf(0,1,4,8) && range.valid(phrase.sequence))
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
    private fun loadAsset(phrase: Phrase): PhraseSettings {
        val prefix=assetPrefix(phrase)
        fun number(key: String,default: Int)=runCatching { prefs.getInt(prefix+key,default) }.getOrDefault(default)
        val maxBpm = maxOf(180, TempoControl.maximum(TempoControl.sourceBpm(phrase.sequence)))
        val bpm=number("bpm",phrase.suggestedBpm).takeIf { it in 20..maxBpm } ?: phrase.suggestedBpm
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
    private fun assetPrefix(phrase: Phrase) = "asset."+phrase.assetKey!!.encoded+"."
}
