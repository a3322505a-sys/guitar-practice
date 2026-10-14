package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.assets.song.StrictJson
import com.a3322505a.guitarlearning.music.ShayouChords
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.practice.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/** One-time migration capture, removed before shipping; no App registration. */
class ExportLegacySongs {
    private fun json(value: Any?): Any? = when(value) {
        is Float -> value.toDouble()
        null, is String, is Number, is Boolean -> value
        is Q -> mapOf("n" to value.n,"d" to value.d)
        is Enum<*> -> value.name
        is Map<*,*> -> value.entries.associate { it.key.toString() to json(it.value) }
        is Iterable<*> -> value.map(::json)
        else -> value.javaClass.declaredFields.filter {
            !Modifier.isStatic(it.modifiers) && !it.isSynthetic && !it.name.contains('$')
        }.mapNotNull { field ->
            field.isAccessible=true
            field.get(value)?.let { field.name to json(it) }
        }.toMap()
    }
    @Test fun capture() {
        val phrases=Phrases.mozart+Phrases.shayou+YourTrip.phrases
        val records=phrases.map { phrase ->
            val plan=phrase.sequence.performance
            mapOf("phrase" to json(phrase),"score" to json(plan.score),"practice" to json(plan.practice),
                "notes" to json(plan.notes))
        }
        val out=File("../work/v77/legacy-snapshot.json");out.parentFile.mkdirs()
        out.writeBytes(StrictJson.encode(mapOf("phrases" to records,"chords" to json(ShayouChords.all))))
        println("Captured ${phrases.size} practices and ${ShayouChords.all.size} source chord cards")
    }
}
