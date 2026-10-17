package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.SongTestLibrary
import com.a3322505a.guitarlearning.assets.song.LibrarySnapshot
import com.a3322505a.guitarlearning.music.Chords
import com.a3322505a.guitarlearning.music.score.PracticeKey
import com.a3322505a.guitarlearning.practice.Phrases
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContentPresentationTest {
    private val snapshot by lazy { SongTestLibrary.repository().reload() }
    private val content by lazy { ContentPresentation(snapshot) }

    @Test fun everyVisibleIdentityAndObjectIsRetainedExactlyOnce() {
        val phrases = content.sources.flatMap { content.phraseSections(it.id) }.flatMap { it.items }
        val chords = content.sources.flatMap { content.chordSections(it.id) }.flatMap { it.items }
        assertEquals(snapshot.catalogue.map { it.id }.sorted(), phrases.map { it.id }.sorted())
        assertEquals(snapshot.chords.map { it.id }.sorted(), chords.map { it.id }.sorted())
        phrases.forEach { p -> assertSame(snapshot.catalogue.single { it.id == p.id }, p) }
        chords.forEach { c -> assertSame(snapshot.chords.single { it.id == c.id }, c) }
        assertEquals(listOf(4, 2), content.phraseSections("local.yourtrip").map { it.items.size })
        assertEquals(1, content.phraseSections("local.usotsuki").single().items.size)
        assertEquals(16, content.chordSections("local.shayou").sumOf { it.items.size })
    }

    @Test fun sourcesUsePackageIdentityAndPureNamesWithMozartInBasicSingleNotes() {
        assertEquals(listOf("基础训练", "斜阳", "嘘月", "Your Trip"), content.sources.map { it.title })
        assertEquals(content.sources.size, content.sources.map { it.id }.distinct().size)
        val single = content.phraseSections(BASIC_CONTENT).single { it.title == "单音" }.items
        assertEquals(4, single.size)
        assertEquals(3, single.count { it.assetKey?.packageId == "local.mozart" })
        assertEquals("嘘月", sourceTitle("本地曲库 · 嘘月"))
        assertEquals("斜阳", sourceTitle("斜阳 · 5段完整乐句"))
    }

    @Test fun crossTabFallbackRetainsRequestedSourceAndLoadingDoesNotDiscardRestoredState() {
        val selected = "local.yourtrip"
        assertEquals(BASIC_CONTENT, content.effectiveSource(selected, songs = false))
        assertEquals(selected, content.effectiveSource(selected, songs = true))
        assertEquals("local.shayou", content.effectiveSource("local.shayou", songs = false))
        val empty = ContentPresentation(LibrarySnapshot(Phrases.all, Phrases.catalogue, emptyList()))
        assertEquals(selected, empty.restoredSource(selected, ready = false))
        assertEquals(BASIC_CONTENT, empty.restoredSource(selected, ready = true))
        assertEquals(selected, content.restoredSource(selected, ready = true))
        assertEquals(BASIC_CONTENT, content.effectiveSource(BASIC_CONTENT, songs = true))
    }

    @Test fun singlePackageSectionHasNoHeadingButBasicAndMultipleSectionsDo() {
        assertFalse(showContentSectionTitles("local.usotsuki", 1))
        assertFalse(showContentSectionTitles("local.shayou", 1))
        assertTrue(showContentSectionTitles("local.shayou", 2))
        assertTrue(showContentSectionTitles(BASIC_CONTENT, 1))
        assertEquals(2, content.chordSections("local.shayou").size)
    }

    @Test fun entireBuiltinTextWhitelistIsAppliedIncludingTechniqueAndRhythmLabels() {
        val expected = mapOf(
            "单音短句 · C 大调小句" to ContentCardText("C 大调小句", "入门短句"),
            "莫扎特 K265 · 1 主题" to ContentCardText("1 主题", "莫扎特 K265"),
            "莫扎特 K265 · 2 变奏" to ContentCardText("2 变奏", "莫扎特 K265"),
            "莫扎特 K265 · 3 再现与结尾" to ContentCardText("3 再现与结尾", "莫扎特 K265"),
            "滑音 · 5/7 与 7\\5" to ContentCardText("5/7 与 7\\5", "滑音"),
            "击弦 · 5h7" to ContentCardText("5h7", "击弦"),
            "勾弦 · 7p5" to ContentCardText("7p5", "勾弦"),
            "节奏句 · 基础版" to ContentCardText("基础版", "简单和弦节奏"),
            "节奏句 · 完整版" to ContentCardText("完整版", "综合片段"),
        )
        expected.forEach { (raw, text) -> assertEquals(raw, text, content.phraseText(Phrases.singleNote.copy(title = raw))) }
        val mozart = content.phraseSections(BASIC_CONTENT).single { it.title == "单音" }.items.filter { it.assetKey != null }
        assertEquals(listOf("1 主题", "2 变奏", "3 再现与结尾"), mozart.map { content.phraseText(it).primary })
        assertEquals("莫扎特 K265 · 2 发展", mozart[1].title)
        assertEquals(setOf("简单和弦节奏", "综合片段"), content.phraseSections(BASIC_CONTENT)
            .single { it.title == "节奏与综合" }.items.map { it.use }.toSet())
    }

    @Test fun songCardsLoseOnlyKnownSourcePrefixAndUnknownFormatsRemainReadable() {
        content.sources.filter { it.id != BASIC_CONTENT }.forEach { source ->
            content.phraseSections(source.id).flatMap { it.items }.forEach {
                val text = content.phraseText(it)
                assertFalse(text.primary.startsWith(source.title + " · "))
                assertNull(text.tag)
            }
        }
        listOf("P.M. · 普通与制音对照", "三连音 · 八分与十六分").forEach {
            assertEquals(it.substringBefore(" · "), content.phraseText(Phrases.singleNote.copy(title = it)).tag)
        }
        assertEquals(ContentCardText("陌生限定 · 不能丢失"), content.phraseText(Phrases.singleNote.copy(title = "陌生限定 · 不能丢失")))
        assertEquals(ContentCardText("交替拨弦热身", "Block 01"), content.phraseText(Phrases.singleNote.copy(title = "七个Block · 1 交替拨弦热身")))
    }

    @Test fun chordVoicingsKeepTheirFretQualifierAndSourceParserUsesLengthPrefix() {
        val chord = Chords.all.first().copy(id = PracticeKey("local.shayou", "voicing:1").encoded, name = "斜阳 · D5/A · 高音10品")
        assertEquals(ContentCardText("D5/A", "高音10品"), content.chordText(chord))
        assertEquals(ContentCardText("D5/A", "低音7品"), content.chordText(chord.copy(name = "斜阳 · D5/A · 低音7品")))
        assertEquals("package:one", encodedPackageId(PracticeKey("package:one", "practice:two").encoded))
        listOf("C", "-1:a", "999999999999:a", "4:abc", "3:abc", "x:abcde").forEach { assertNull(it, encodedPackageId(it)) }
    }
}
