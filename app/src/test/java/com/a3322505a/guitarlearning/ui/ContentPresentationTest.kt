package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.SongTestLibrary
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.music.score.PracticeKey
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ContentPresentationTest {
    private val snapshot by lazy { SongTestLibrary.repository().reload() }
    private val content by lazy { ContentPresentation(snapshot) }

    @Test fun everyVisibleNativePracticeAppearsOnceInItsSourceAndModule() {
        assertTrue(snapshot.diagnostics.joinToString(),snapshot.diagnostics.isEmpty())
        val shown=content.sources.flatMap { content.phraseSections(it.id) }.flatMap { it.items }
        assertEquals(snapshot.catalogue.map { it.id }.sorted(),shown.map { it.id }.sorted())
        assertEquals(shown.size,shown.map { it.id }.distinct().size)
        assertEquals(4,content.sources.size)
        TeachingModule.entries.forEach { module ->
            val lessons=content.phraseSections(BASIC_CONTENT,module).flatMap { it.items }
            assertTrue(module.title,lessons.isNotEmpty())
            assertTrue(lessons.all { it.lesson!!.module==module })
        }
        assertFalse(snapshot.phrases.any { it.id.startsWith("SB-") })
    }

    @Test fun demonstrationSourcesExposeTwoSectionsAndEachVersionRemainsInItsFamily() {
        content.sources.filter { it.id!=BASIC_CONTENT }.forEach { source ->
            val cards=content.phraseSections(source.id).flatMap { it.items }
            assertEquals(2,cards.size)
            cards.forEach { base ->
                val versions=SongTestLibrary.repository.versions(base)
                assertEquals(listOf("原谱版","新手简化版"),versions.map { it.version })
                assertTrue(versions.all { it.assetKey!!.packageId==source.id })
            }
            assertTrue(content.chordSections(source.id).flatMap { it.items }.isNotEmpty())
        }
    }

    @Test fun sourceFallbackAndLoadingPreserveKnownSelections() {
        assertEquals("local.demo-c",content.effectiveSource("local.demo-c",songs=true))
        assertEquals(BASIC_CONTENT,content.effectiveSource("local.usotsuki",songs=true))
        assertEquals("loading",content.restoredSource("loading",ready=false))
        assertEquals(BASIC_CONTENT,content.restoredSource("loading",ready=true))
    }

    @Test fun nativePatternsHaveExplicitFingerMapsAndPitchCorrectFiveHintInputs() {
        Tonality.entries.forEach { key ->
            val patterns=Patterns.forKey(key)
            assertEquals(listOf("开放把位","5品封闭 Box","7品封闭 Box"),patterns.map { it.solfege })
            patterns.forEach { pattern ->
                assertEquals(pattern.positions.toSet(),pattern.fingers.keys)
                assertTrue(pattern.positions.all { it.midi%12 in key.pitchClasses })
                assertTrue(pattern.positions.any(key::isRoot))
                assertTrue(pattern.positions.any { it.midi%12 in key.context.pentatonic })
                assertTrue(pattern.positions.all { p -> if(p.fret==0) pattern.fingers[p]==0 else pattern.fingers[p] in 1..4 })
            }
            assertEquals(5,BoardTeachingContext(key.context,Chords.named(key.tonic+if(key.mode==ScaleDefinition.NATURAL_MINOR) "m" else "")).available.size)
            assertTrue(Routes.scale(ScaleOptions(key=key,notesPerBeat=3)).sequence!!.tuplets.isNotEmpty())
        }
    }

    @Test fun identityParserHandlesColonsAndRejectsMalformedKeys() {
        assertEquals("package:one",encodedPackageId(PracticeKey("package:one","practice:two").encoded))
        listOf("C","-1:a","999999999999:a","4:abc","3:abc","x:abcde").forEach { assertNull(encodedPackageId(it)) }
        assertEquals("强力和弦 (Power Chord)",chordNatureTag("D5"))
        assertEquals("指定低音 · 斜杠和弦",chordNatureTag("D/F#"))
    }
}
