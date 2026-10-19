package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.SongTestLibrary
import com.a3322505a.guitarlearning.assets.song.LibrarySnapshot
import com.a3322505a.guitarlearning.music.Chords
import com.a3322505a.guitarlearning.music.Patterns
import com.a3322505a.guitarlearning.music.Tonality
import com.a3322505a.guitarlearning.music.score.PracticeKey
import com.a3322505a.guitarlearning.practice.Phrases
import com.a3322505a.guitarlearning.practice.*
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
        val single = content.phraseSections(BASIC_CONTENT).single { it.title == "旋律与古典练习曲" }.items
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
            "M01" to ContentCardText("C 大调小句", "入门旋律 · 第一把位"),
            "T01" to ContentCardText("八分与十六分", "时值转换 · 三连音"),
            "PM01" to ContentCardText("普通与制音对照", "掌根闷音 · Palm Mute"),
            "SL01" to ContentCardText("滑音 5/7 与 7\\5", "左手滑音 · Slide"),
            "H01" to ContentCardText("击弦 5h7", "左手连奏 · Hammer-on"),
            "P01" to ContentCardText("勾弦 7p5", "左手连奏 · Pull-off"),
            "R01" to ContentCardText("节奏句（基础版）", "简单和弦节奏 · 伴奏入门"),
            "R02" to ContentCardText("节奏句（完整版）", "综合片段 · 律动进阶"),
        )
        expected.forEach { (id, text) -> assertEquals(id, text, content.phraseText(SongTestLibrary.repository.resolve(id).copy(title = "可变名称"))) }
        val mozart = content.phraseSections(BASIC_CONTENT).single { it.title == "旋律与古典练习曲" }.items.filter { it.assetKey?.packageId == "local.mozart" }
        assertEquals(listOf("1 主题", "2 变奏", "3 再现与结尾"), mozart.map { content.phraseText(it).primary })
        assertEquals("莫扎特 K265 · 2 发展", mozart[1].title)
        assertEquals(setOf("简单和弦节奏", "综合片段"), content.phraseSections(BASIC_CONTENT)
            .single { it.title == "节奏与综合实战" }.items.map { it.use }.toSet())
    }

    @Test fun songCardsLoseOnlyKnownSourcePrefixAndUnknownFormatsRemainReadable() {
        content.sources.filter { it.id != BASIC_CONTENT }.forEach { source ->
            content.phraseSections(source.id).flatMap { it.items }.forEach {
                val text = content.phraseText(it)
                assertFalse(text.primary.startsWith(source.title + " · "))
                assertNull(text.tag)
            }
        }
        assertEquals(ContentCardText("陌生限定 · 不能丢失"), content.phraseText(Phrases.singleNote.copy(id = "unknown", title = "陌生限定 · 不能丢失")))
        assertEquals(ContentCardText("交替拨弦热身", "Block 01 · 右手稳定度"), content.phraseText(SongTestLibrary.repository.resolve("SB-1")))
    }

    @Test fun chordVoicingsKeepTheirFretQualifierAndSourceParserUsesLengthPrefix() {
        val chord = Chords.all.first().copy(id = PracticeKey("local.shayou", "voicing:1").encoded, name = "斜阳 · D5/A · 高音10品")
        assertEquals(ContentCardText("D5/A", "高音10品"), content.chordText(chord))
        assertEquals(ContentCardText("D5/A", "低音7品"), content.chordText(chord.copy(name = "斜阳 · D5/A · 低音7品")))
        assertEquals("package:one", encodedPackageId(PracticeKey("package:one", "practice:two").encoded))
        listOf("C", "-1:a", "999999999999:a", "4:abc", "3:abc", "x:abcde").forEach { assertNull(it, encodedPackageId(it)) }
    }

    @Test fun basicTeachingGroupsAreOrderedByStableIdsAndNeverChangeSourceOrder() {
        val chordsBefore = snapshot.chords.toList()
        val phrasesBefore = snapshot.catalogue.toList()
        val groups = content.chordSections(BASIC_CONTENT)
        assertEquals(listOf(5, 3, 4), groups.take(3).map { it.items.size })
        assertEquals(listOf("C", "G", "D", "A", "E", "Am", "Em", "Dm", "Gsus2", "Asus4(no5)", "Bm7(no5)", "D/F♯"), groups.take(3).flatMap { it.items }.map { it.id })
        assertEquals(ContentCardText("D/F#", "第一转位 · G→Em 常用桥梁"), content.chordText(Chords.named("D/F♯")))
        assertEquals(listOf(7, 5, 4, 2), content.phraseSections(BASIC_CONTENT).take(4).map { it.items.size })
        assertEquals(listOf("T01", "PM01", "SL01", "H01", "P01"), content.phraseSections(BASIC_CONTENT)[1].items.map { it.legacyIds.single() })
        val packaged = Chords.named("C").copy(id = PracticeKey("other.song", "C").encoded)
        assertNull(content.chordText(packaged).tag)
        assertEquals(chordsBefore, snapshot.chords)
        assertEquals(phrasesBefore, snapshot.catalogue)
    }

    @Test fun scaleTagsUseTrueFretExtentsAndTripletTiming() {
        assertEquals(listOf(0 to 3, 2 to 6, 4 to 8, 7 to 10, 9 to 13), Patterns.all.map { it.positions.minOf { p -> p.fret } to it.positions.maxOf { p -> p.fret } })
        Patterns.all.forEach { pattern ->
            assertTrue(patternText(pattern).tag!!.contains("${pattern.positions.minOf { it.fret }}–${pattern.positions.maxOf { it.fret }}品"))
        }
        Tonality.entries.forEach { key ->
            val sequence = Routes.scale(ScaleOptions(key = key, notesPerBeat = 3)).sequence!!
            assertTrue(sequence.events.filter { it.positions.isNotEmpty() }.all { it.duration == QUARTER / 3 })
            assertTrue(sequence.tuplets.isNotEmpty())
        }
        assertEquals("三连音", rhythmCardTexts[2].tag)
        val tripletPlan=SongTestLibrary.repository.resolve("T01").sequence.performance
        val groupIds=tripletPlan.events.flatMap { it.event.written.tupletGroupIds }.toSet()
        assertEquals(listOf(3, 3),tripletPlan.score.tuplets.filter { it.id in groupIds }.map { it.actual })
        assertEquals(6, ScaleExercise.entries.map(::scaleExerciseText).size)
    }

    @Test fun packageNatureTagsAreAnchoredAndNativeFretQualifiersWin() {
        mapOf("D5" to "强力和弦 (Power Chord)", "G5" to "强力和弦 (Power Chord)", "Bbdim7" to "减七和弦",
            "Cdim" to "减三和弦", "D/F#" to "指定低音 · 斜杠和弦", "Dm/F" to "指定低音 · 斜杠和弦").forEach { (name, tag) ->
            assertEquals(name, tag, chordNatureTag(name))
        }
        listOf("Asus4(no5)", "Bm7(no5)", "D15", "notD5", "C/unknown").forEach { assertNull(it, chordNatureTag(it)) }
    }
}
