package com.a3322505a.guitarlearning

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class KafkaPracticeTest {
    @Test fun catalogueHasSevenPurposefulExercisesAndKeepsVariationsWithinTheirFamily() {
        val kafka=Phrases.catalogue.filter { it.id.startsWith("KAF-") }
        assertEquals(7,kafka.size)
        assertEquals(mapOf("卡夫卡 · 伴奏练习" to 4,"卡夫卡 · 旋律与 solo" to 3),kafka.groupingBy { it.use }.eachCount())
        assertEquals(listOf(8,8,16,16,8,16,8),kafka.map { it.sequence.barCount })
        assertTrue(kafka.all { it.relatedId==null && it.sequence.events.count { e->e.positions.isNotEmpty() }>=30 })
        val solo=Phrases.named("KAF-P-SOLO").sequence
        assertEquals(81,solo.originalBar)
        assertEquals(384,solo.length)
        assertEquals(4,solo.events.last { it.positions.isNotEmpty() }.stringExpression.values.single().slide)
        assertEquals(9,Phrases.named("KAF-V-RIFF-END").sequence.barCount)
        assertEquals(12,Phrases.named("KAF-V-POWER-END").sequence.barCount)
        assertTrue(Phrases.named("KAF-V-RIFF-END").sequence.events.any { it.tick==384 && it.positions==listOf(Position(3,14)) })
        val melody=Phrases.named("KAF-P-MELODY")
        assertEquals(listOf("标准段","第二次收尾","转调完整版"),Phrases.versions(melody).map { it.version })
        Phrases.kafka.forEach { old ->
            val current=Phrases.practiceNamed(old.id)
            assertTrue(Phrases.family(current) in kafka)
            assertTrue(current in Phrases.versions(current))
        }
    }

    @Test fun allCuratedNotesAndRestsAgreeWithTheIndependentGpFixtureIncludingJoinedEndings() {
        val rows=javaClass.getResourceAsStream("/kafka-gp.csv")!!.bufferedReader().use { it.readLines() }.drop(1).map { it.split(',') }
        val coverage=mapOf(
            "KAF-P-POWER" to listOf(Triple("KAF-A-1-G1",1,8)),
            "KAF-P-ARPEGGIO" to listOf(Triple("KAF-B-9-G2",9,16)),
            "KAF-P-STRUM" to listOf(Triple("KAF-E-29-G1",29,44)),
            "KAF-P-BASS" to listOf(Triple("KAF-J-100-G1",100,115)),
            "KAF-P-RIFF" to listOf(Triple("KAF-B-9-G3",9,16)),
            "KAF-P-MELODY" to listOf(Triple("KAF-E-29-G2",29,44)),
            "KAF-P-SOLO" to listOf(Triple("KAF-I-82-G3",82,89)),
            "KAF-V-POWER-END" to listOf(Triple("KAF-L-137-G1",137,144),Triple("KAF-M-145-G1",145,148)),
            "KAF-V-STRUM-REPEAT" to listOf(Triple("KAF-E-61-G1",61,76)),
            "KAF-V-STRUM-KEY" to listOf(Triple("KAF-K-117-G1",117,136)),
            "KAF-V-RIFF-END" to listOf(Triple("KAF-L-137-G3",137,144),Triple("KAF-M-145-G3",145,145)),
            "KAF-V-MELODY-REPEAT" to listOf(Triple("KAF-E-61-G2",61,76)),
            "KAF-V-MELODY-KEY" to listOf(Triple("KAF-K-117-G2",117,136)),
        )
        coverage.forEach { (id,ranges) ->
            val sequence=Phrases.named(id).sequence
            val selected=rows.filter { row -> ranges.any { (source,first,last)->row[0]==source && row[1].toInt() in first..last } }
            assertTrue(id,selected.isNotEmpty())
            selected.groupBy { it[1].toInt() to it[2].toInt() }.forEach { (at,notes) ->
                val tick=sequence.barStart(at.first-sequence.originalBar-1)+at.second
                val event=sequence.eventAt(tick.toDouble())!!
                val where="$id bar ${at.first} tick ${at.second}"
                assertTrue(where,event.tick<=tick && event.tick+event.duration>=tick+notes.first()[3].toInt())
                assertEquals(where,notes.filter { it[4]!="0" && it[7]=="0" }.map { Position(it[4].toInt(),it[5].toInt()) }.toSet(),event.positions.toSet())
                assertEquals(where,notes.filter { it[7]=="1" }.map { it[4].toInt() }.toSet(),event.deadStrings)
                notes.filter { it[4]!="0" && it[7]=="0" }.forEach { n -> assertEquals(where,n[6].toInt(),sequence.midi(Position(n[4].toInt(),n[5].toInt()))) }
                if(notes.all { it[4]=="0" }) assertTrue(where,event.rest)
            }
        }
    }

    @Test fun oldSelectionRetainsSpeedAndRepeatSettingsAndFallsBackToTheCompleteNewLine() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("kafka-curation-test",0)
        prefs.edit().clear().putString("phrase-selected","KAF-I-82-G3")
            .putInt("phrase.KAF-I-82-G3.bpm",72).putInt("phrase.KAF-I-82-G3.repeats",8)
            .putInt("phrase.KAF-I-82-G3.first",0).putInt("phrase.KAF-I-82-G3.last",17).commit()
        val store=PhraseSettingsStore(prefs)
        val phrase=store.selected()
        assertEquals("KAF-P-SOLO",phrase.id)
        val saved=store.load(phrase)
        assertEquals(72,saved.bpm)
        assertEquals(8,saved.repeats)
        assertEquals(BarRange(0,7),saved.range)
        store.save(phrase,saved.bpm,saved.repeats,saved.range)
        assertEquals(17,prefs.getInt("phrase.KAF-I-82-G3.last",-1))
        assertEquals("KAF-P-SOLO",store.selected().id)
        assertEquals(saved,store.load(phrase))
    }
}
