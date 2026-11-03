package com.a3322505a.guitarlearning

import android.content.Context
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.score.PracticeKey
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.log2
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BundledSongsTest {
    @Test fun retainedPackagesOpenThroughAndroidAssetManager() {
        val assets = RuntimeEnvironment.getApplication().assets
        val reader = SongReader(SongSchema { File("../contracts/song-package/v1", it).readBytes() })
        listOf("shayou-v4.gpractice", "usotsuki-v1.gpractice", "your-trip-v3.gpractice",
            "canon-c-clean-pick-v1.gpractice", "Doraemon_no_Uta_r1.gpractice", "hana-bourei-lead-v2.gpractice").forEach { name ->
            val song = reader.read(assets.open("song-packages/$name").use { it.readBytes() })
            assertTrue(name, song.practices.isNotEmpty())
        }
    }

    @Test fun nativePackagesCompileAndPracticeSettingsSurviveReloadDisableAndRollback() {
        val root = java.nio.file.Files.createTempDirectory("bundle-restart").toFile()
        val repo = SongTestLibrary.repository(root)
        val library = repo.reload()
        assertTrue(library.diagnostics.toString(), library.diagnostics.isEmpty())
        assertEquals(setOf("local.teaching-native", "local.daily-practice", "local.demo-c", "local.demo-capo", "local.demo-rock",
            "local.shayou", "local.usotsuki", "local.yourtrip", "user.canon-c.clean-pick", "local.user.doraemon-no-uta",
            "personal.hana-bourei-lead"),
            library.packages.map { it.identity.packageId }.toSet())
        assertFalse(library.aliases.keys.any { it.startsWith("SB-") || it.startsWith("K265-") })
        val selected = repo.resolve(PracticeKey("local.daily-practice", "DP-03-basic").encoded)
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("bundle-settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PhraseSettingsStore(prefs, repo::named)
        store.select(selected); store.save(selected, 73, 8, BarRange(2, 3))
        val reloaded = SongTestLibrary.repository(root).also { it.reload() }
        val restored = PhraseSettingsStore(prefs, reloaded::named)
        assertEquals(selected.id, restored.selected().id)
        assertEquals(PhraseSettings(73, 8, BarRange(2, 3)), restored.load(reloaded.resolve(selected.id)))
        val revision = repo.revisions("local.daily-practice").single()
        repo.disable("local.daily-practice")
        assertNotEquals(selected.id, store.selected().id)
        repo.rollback(revision)
        assertEquals(selected.id, store.selected().id)
        assertEquals(73, store.load(repo.resolve(selected.id)).bpm)
    }

    @Test fun independentPackageReadsKeepAllPracticeIdsScoresAndExplicitFingerings() {
        val reader = SongReader(SongSchema { File("../contracts/song-package/v1", it).readBytes() })
        val repo = SongTestLibrary.repository().also { it.reload() }
        var count = 0
        SongTestLibrary.assets.listFiles()!!.filter { it.extension == "gpractice" }.forEach { file ->
            val source = reader.read(file.readBytes())
            source.practices.forEach { practice ->
                val phrase = repo.resolve(PracticeKey(source.identity.packageId, practice.id).encoded)
                val plan = phrase.sequence.performance
                assertEquals(practice.id, plan.practice.id)
                assertEquals(source.score, plan.score)
                assertTrue(phrase.id, plan.notes.isNotEmpty())
                plan.notes.forEach { note ->
                    val fundamental = note.track.midi(note.note.string, note.note.fret)
                    val harmonicOffset = note.note.harmonic?.let { (12 * log2(it.partial.toDouble())).roundToInt() } ?: 0
                    assertEquals(fundamental + harmonicOffset, note.note.midi)
                }
                count++
            }
        }
        assertEquals(repo.snapshot.phrases.size, count)
        repo.snapshot.chords.forEach { chord ->
            assertNull(chord.id, chord.unavailableReason)
            chord.positions.filter { it.fret > 0 }.forEach { assertTrue(chord.fingers[6 - it.string] in 1..5) }
        }
    }
}
