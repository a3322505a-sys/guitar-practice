package com.a3322505a.guitarlearning

import android.content.Context
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class BundledSongsTest {
    @Test fun preinstalledPackagesResolveOldIdsCompileAllPracticesAndPreserveSettingsAcrossRestart() {
        val root=java.nio.file.Files.createTempDirectory("bundle-restart").toFile()
        val repo=SongTestLibrary.repository(root);val library=repo.reload()
        assertTrue(library.diagnostics.toString(),library.diagnostics.isEmpty())
        assertEquals(4,library.packages.size)
        assertEquals(15,library.phrases.count { it.assetKey!=null })
        assertEquals(12,Chords.all.size)
        assertEquals(32,library.chords.size) // 12 foundation + 4 accepted S01 grips + 16 Shayou grips.
        assertTrue(Phrases.all.none { it.id.startsWith("SHA-") || it.id.startsWith("YT-") || it.id.startsWith("KAF-") || it.id.startsWith("K265-") })
        assertEquals(7,Phrases.all.count { it.id.startsWith("SB-") })
        assertEquals("M01",repo.named("KAF-B-9-G3").id)
        val expectedBars=mapOf("local.usotsuki" to listOf(2),"local.mozart" to listOf(8,8,8),
            "local.shayou" to listOf(8,9,8,6,8),"local.yourtrip" to listOf(10,16,8,17,19,6))
        expectedBars.forEach { (packageId,bars) ->
            val phrases=library.phrases.filter { it.assetKey?.packageId==packageId }.map { repo.resolve(it.id) }
            assertEquals(bars,phrases.map { it.sequence.barCount })
            phrases.forEach { phrase ->
                assertTrue(phrase.sequence.performance.notes.isNotEmpty())
                assertEquals(listOf(phrase.id),repo.versions(phrase).map { it.id })
                val session=Session(phrase.sequence,60,true,2)
                assertEquals(2,session.repeats)
                assertTrue(session.frame(session.totalTicks!!.toDouble())>0)
            }
        }
        (1..6).forEach { old -> assertEquals(repo.named("K265-"+('A'+(old-1)/2)).id,repo.named("K265-$old").id) }
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("bundle-settings",Context.MODE_PRIVATE)
        prefs.edit().clear().putString("phrase-selected","SHA-RIFF").putInt("phrase.SHA-RIFF.bpm",73)
            .putInt("phrase.SHA-RIFF.repeats",8).putInt("phrase.SHA-RIFF.first",2).putInt("phrase.SHA-RIFF.last",3).commit()
        val selected=repo.resolve("SHA-RIFF")
        val store=PhraseSettingsStore(prefs,repo::named)
        assertEquals(selected.id,store.selected().id)
        assertEquals(PhraseSettings(73,8,BarRange(2,3)),store.load(selected))
        assertEquals(false,store.tempo(selected).first)
        assertEquals(73,prefs.getInt("phrase.SHA-RIFF.bpm",0))
        assertTrue(selected.sequence.explanation.contains("第9小节前三拍休止"))
        val index=repo.revisions("local.shayou")
        repo.disable("local.shayou");assertTrue(SongTestLibrary.repository(root).reload().packages.none { it.identity.packageId=="local.shayou" })
        assertEquals("M01",store.selected().id)
        repo.rollback(index.single());assertEquals(selected.id,store.selected().id)
        assertEquals(73,store.load(repo.resolve("SHA-RIFF")).bpm)
        assertEquals(12,Chords.all.size)
        assertNotNull(Routes.scale(ScaleOptions()).sequence)
        assertTrue(chordSequence(Chords.named("C"),Chords.named("Am"),ChordExercise.CHANGE,4).performance.notes.isNotEmpty())
    }

    @Test fun sourceRowsAndChordTeachingSurvivePackageReaderAndPlanCompilation() {
        val repo=SongTestLibrary.repository();repo.reload()
        val expected=File("src/test/resources/mozart-k265-v2.csv").readLines().drop(1).map { it.split(',').map(String::toInt) }
        val actual=listOf("K265-A","K265-B","K265-C").flatMap { id ->
            val plan=repo.resolve(id).sequence.performance
            plan.events.map { e ->
                val n=e.event.notes.singleOrNull()
                listOf(plan.score.measures.first { it.id==e.event.measureId }.label.toInt(),(e.event.onset.value()*12).toInt(),
                    (e.duration.value()*12).toInt(),n?.string ?: 0,n?.fret ?: 0,n?.midi ?: 0)
            }
        }
        assertEquals(expected,actual)
        val grips=repo.snapshot.chords.filter { it.group.startsWith("斜阳") }
        assertEquals(16,grips.size)
        assertEquals(mapOf("斜阳 · 伴奏指法" to 11,"斜阳 · 双音 riff" to 5),grips.groupingBy { it.group }.eachCount())
        grips.forEach { grip -> assertTrue(grip.source.contains("用指为参考"));assertEquals(3,grip.capoFret) }
        val dim=grips[7]
        assertEquals("减五音",dim.role(Position(4,2)))
        assertEquals("减七音",dim.role(Position(3,0)))
        assertEquals("B♭",dim.noteName(Position(3,0)))
        assertEquals("A♭",grips[9].noteName(Position(6,1)))
        val dead=repo.resolve("SHA-CHORUS").sequence.performance.notes.filter { it.note.dead }
        assertTrue(dead.isNotEmpty())
        assertTrue(dead.all { it.note.fret==10 && it.note.midi==it.track.midi(it.note.string,10) })
        val trip=repo.resolve("YT-RING").sequence.performance
        assertTrue(trip.notes.any { it.articulation.letRing==true })
        assertTrue(trip.score.relations.any { it.kind=="tie" })
        assertTrue(repo.resolve("YT-GROOVE").sequence.performance.notes.any { it.articulation.bend.orEmpty().isNotEmpty() })
        assertTrue(repo.resolve("YT-LEGATO").sequence.performance.relations.any { it.kind=="hammer" || it.kind=="pull" })
    }
}
