package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.SongTestLibrary
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.LessonMetadata
import com.a3322505a.guitarlearning.music.TeachingModule
import com.a3322505a.guitarlearning.music.score.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PackageLibraryTest {
    @Test fun libraryIncludesEveryPracticeInSourceOrderIncludingVariantsAndTransitions() {
        val snapshot = SongTestLibrary.repository().reload()
        assertTrue(snapshot.diagnostics.joinToString(), snapshot.diagnostics.isEmpty())
        val reader = SongReader(SongSchema { File("../contracts/song-package/v1", it).readBytes() })
        val songs = SongTestLibrary.assets.listFiles()!!.filter { it.extension == "gpractice" }
            .map { reader.read(it.readBytes()) }.associateBy { it.identity.packageId }
        val cards = packageCards(snapshot)
        assertEquals(songs.keys, cards.map { it.id }.toSet())
        cards.forEach { card ->
            val source = songs.getValue(card.id).practices.sortedBy { it.order }
            assertEquals(source.map { it.id }, card.practices.map { it.key.practiceId })
            assertEquals(source.map { it.goal }, card.practices.map { it.goal })
            assertEquals(card.practices.size, card.practices.map { it.key }.distinct().size)
        }
        assertEquals(snapshot.phrases.size, cards.sumOf { it.practices.size })
        assertTrue(cards.sumOf { it.practices.size } > snapshot.catalogue.size)
    }

    @Test fun metadataClassifiesMixedPackagesWithoutSplittingOrInventingLessonsFromTitles() {
        fun practice(id: String, teaching: PracticeTeaching? = null, lesson: LessonMetadata? = null) =
            LibraryPractice(PracticeKey("test.mixed", id), id, "目标", 60, lesson, teaching)
        val revision = SongRevision(PackageIdentity("test.mixed", 1, "digest"), "基础教学这个标题不是分类依据")
        fun cards(practices: List<LibraryPractice>) = packageCards(LibrarySnapshot(emptyList(), emptyList(),
            listOf(revision), packagePractices = mapOf("test.mixed" to practices)))
        assertFalse(cards(listOf(practice("song"))).single().isTeaching)
        assertFalse(cards(listOf(practice("source", PracticeTeaching(source = "来源", passage = "主歌")))).single().isTeaching)
        val mixed = listOf(practice("song"), practice("lesson", lesson = LessonMetadata(TeachingModule.FOUNDATIONS)))
        assertTrue(cards(mixed).single().isTeaching)
        assertEquals(mixed, cards(mixed).single().practices)
        assertTrue(cards(listOf(practice("teaching", PracticeTeaching(technique = "交替拨弦")))).single().isTeaching)
    }
}
