package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.assets.song.StrictJson
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.practice.Phrases
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Test

/** Offline migration input. Not an App producer, and does not assert an independent musical audit. */
class LegacyTeachingExportTest {
    private fun json(value: Any?): Any? = when(value) {
        null, is String, is Int, is Long, is Double, is Boolean -> value
        is Float -> value.toDouble()
        is Q -> mapOf("n" to value.n, "d" to value.d)
        is List<*> -> value.map(::json)
        is Set<*> -> value.map(::json)
        is Map<*,*> -> value.entries.associate { it.key.toString() to json(it.value) }
        is Enum<*> -> value.name
        else -> value.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) && '$' !in it.name }
            .mapNotNull { field -> field.isAccessible = true; field.get(value)?.let { field.name to json(it) } }.toMap()
    }
    @Test fun exportLegacyPlansForOfflineConversion() {
        if (System.getenv("EXPORT_LEGACY_TEACHING") != "1") return
        val result = Phrases.all.map { phrase -> mapOf("phrase" to json(phrase.copy(sequence = phrase.sequence.copy(runtimePlan = null))),
            "score" to json(phrase.sequence.performance.score), "practice" to json(phrase.sequence.performance.practice)) }
        File("../work/teaching-v3-1/legacy-export.json").also { it.parentFile.mkdirs(); it.writeBytes(StrictJson.encode(result)) }
    }
}
