@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class DailyPracticeTest {
    private fun daily(id: String)=SongTestLibrary.repository.resolve(PracticeKey("local.daily-practice",id).encoded)
    @Test fun everyVersionCompilesToOneNativeTimelineWithAllItsMeasuresAndNotes() {
        for(version in listOf("basic","advanced")) {
            for((index,count) in listOf(8,8,8,12).withIndex()) {
                val phrase=daily("DP-%02d-%s".format(index+1,version));val plan=phrase.sequence.performance
                assertEquals(count,plan.visits.size);assertEquals(Q(count*4L),plan.length)
                val score=AlphaScoreAdapter.fromPlan(plan);val (_,projection)=EngineProjection.generate(score)
                assertEquals(count,projection.visits.size);assertEquals(count*4*960.0,projection.visits.last().end,1e-6)
                assertTrue(plan.notes.all { it.note.fret==0 || it.finger in 1..4 })
            }
            val suite=daily("SUITE-$version").sequence
            assertEquals(36,suite.barCount)
            assertEquals(listOf("DP-04-m-3","DP-04-m-4"),daily("DP-04-join-$version").sequence.performance.visits.map { it.sourceMeasureId })
            assertEquals(listOf(BarRange(6,7),BarRange(4,7),BarRange(2,7),BarRange(0,7)),PracticeExtensions.backwardRanges(daily("DP-03-$version").sequence,BarRange(0,7)))
            assertTrue(PracticeExtensions.canFade(daily("DP-03-$version").sequence))
        }
    }
    @Test fun dailyPackListsFourModulesWithoutHidingTheOtherCardsAndVersionsChangeRealMusic() {
        val content=ContentPresentation(SongTestLibrary.repository.snapshot)
        val all=content.phraseSections("local.daily-practice",TeachingModule.FOUNDATIONS).flatMap { it.items }
        assertEquals(5,all.size) // Four independent cards and the optional suite.
        assertEquals(4,all.filter { it.title.startsWith("DP-") }.map { it.lesson!!.module }.distinct().size)
        assertNotEquals(daily("DP-02-basic").sequence.performance.notes.map { it.note.midi },daily("DP-02-advanced").sequence.performance.notes.map { it.note.midi })
        val plan=daily("DP-03-advanced").sequence.performance
        assertEquals(setOf("advanced-lead","advanced-backing"),plan.notes.map { it.track.id }.toSet())
        assertEquals(setOf("slide","hammer","pull"),plan.relations.map { it.kind }.toSet())
    }
    @Test fun v106NativeLibraryUpgradesWithoutChangingTheStorageRootOrLosingItsPracticeIds() {
        val root=kotlin.io.path.createTempDirectory("v106-upgrade").toFile()
        val reader=com.a3322505a.guitarlearning.assets.song.SongReader(com.a3322505a.guitarlearning.assets.song.SongSchema { File("../contracts/song-package/v1",it).readBytes() })
        val old=com.a3322505a.guitarlearning.assets.song.SongRepository(root,reader,mapOf("teaching-native.gpractice" to { File("src/test/resources/v106-teaching-native.gpractice").inputStream() }))
        val ids=old.reload().phrases.map { it.id }.toSet()
        val upgraded=SongTestLibrary.repository(root).reload()
        assertTrue(upgraded.phrases.map { it.id }.containsAll(ids))
        assertTrue(upgraded.packages.any { it.identity.packageId=="local.teaching-native" && it.identity.revision==2 })
    }
    @Test fun optionalThumbGripPreservesTThroughTheNativeScoreAdapter() {
        val grip=ChordDictionary.find("Fmaj7").first { 5 in it.chord.fingers }.chord
        val sequence=chordSequence(grip,grip,ChordExercise.CHECK,4)
        val plan=sequence.performance
        assertEquals(5,plan.notes.first { it.note.string==6 }.finger)
        val score=AlphaScoreAdapter.fromPlan(plan)
        val bass=score.tracks.first().staves.first().bars.first().voices.first().beats.first().notes.first()
        assertEquals(alphaTab.model.Fingers.Thumb,bass.leftHandFinger)
    }
    @Test fun allSixtyScalesReachTheNativePlayerAndKeepTheirExactTimeline() {
        Tonality.entries.forEach { key ->
            val sequence=Routes.scale(ScaleOptions(key=key)).sequence!!
            val native=AlphaScoreAdapter.fromSequence(sequence)
            val (_,projection)=EngineProjection.generate(native)
            assertEquals(key.title,sequence.performance.length.value()*960,projection.visits.last().end,1e-6)
        }
    }
}
