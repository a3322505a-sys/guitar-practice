@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.alphatab.AlphaScoreAdapter
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PracticeFocusTest {
    @Test fun repeatedVisitsAndPolyphonicHeadsKeepTheirOwnIdentity() {
        val root=File("../contracts/song-package/v1")
        val song=SongReader(SongSchema { File(root,it).readBytes() }).read(File(root,"fixtures/complex.gpractice").readBytes())
        val plan=PracticeCompiler.compile(song.score,song.practices.single(),"test/revision/2")
        val index=PracticeFocusIndex(plan)
        val repeated=index.groups.first { plan.visits[it.visit].occurrence==2 }
        val first=index.groups.first { plan.visits[it.visit].sourceMeasureId==plan.visits[repeated.visit].sourceMeasureId }
        assertNotEquals(first.events.first().id,repeated.events.first().id)
        assertTrue(repeated.notes.all { it.event.visit==repeated.visit })
        assertTrue(index.groups.any { it.events.map { e -> e.voiceId }.distinct().size>1 })
        assertSame(repeated,index.at(repeated.at.value(),BarRange(repeated.visit,repeated.visit)))
        assertEquals("test/revision/2",index.plan.revisionPin)
    }
    @Test fun stepIncludesRestsAndReplucksAndStopsAtSelection() {
        val p=Position(1,3)
        val sequence=Sequence(listOf(Event(0,3,listOf(p)),Event(3,3,listOf(p)),Event(6,6,emptyList()),Event(12,36,listOf(p)),Event(48,48,listOf(p))),96,"focus")
        val index=PracticeFocusIndex(sequence.performance)
        val range=BarRange(0,0)
        assertEquals(Q(1,4),index.step(0.0,1,range)!!.at)
        val rest=index.step(.25,1,range)!!
        assertTrue(rest.rest)
        assertEquals(Q(1),index.step(rest.at.value(),1,range)!!.at)
        assertNull(index.step(1.0,1,range))
        assertNull(index.step(0.0,-1,range))
        assertNotEquals(index.groups[0].notes.single().id,index.groups[1].notes.single().id)
    }
    @Test fun reviewedFingeringsRoundTripToPlanAndSdkWithoutChangingSourceMusic() {
        val root=File("../contracts/song-package/v1")
        val reader=SongReader(SongSchema { File(root,it).readBytes() })
        val song=reader.read(File(root,"fixtures/note-fingering.gpractice").readBytes())
        assertTrue("teaching.note-fingering.v1" in song.requires)
        val practice=song.practices.single()
        val plan=PracticeCompiler.compile(song.score,practice)
        assertEquals(listOf(null,1,2,3),plan.notes.map { it.finger })
        val unknown=PracticeCompiler.compile(song.score,practice.copy(noteFingerings=emptyList()))
        assertTrue(unknown.notes.all { it.finger==null })
        assertEquals(unknown.notes.map { Triple(it.start,it.note.midi,it.position) },plan.notes.map { Triple(it.start,it.note.midi,it.position) })
        val native=AlphaScoreAdapter.fromPlan(plan)
        val fingers=native.tracks.first().staves.first().bars.first().voices.first().beats.toList().flatMap { it.notes.toList() }.map { it.leftHandFinger.value }
        assertEquals(listOf(-2,1,2,3),fingers)
        val thumb=practice.copy(noteFingerings=listOf(NoteFingering("simple-n1",5,"audit")))
        assertTrue("teaching.thumb.v1" in ScoreValidation.validate(song.score,listOf(thumb)))
        val bad=practice.copy(noteFingerings=listOf(NoteFingering("simple-n1",6,"audit")))
        assertTrue(runCatching { ScoreValidation.validate(song.score,listOf(bad)) }.exceptionOrNull() is MusicError)
        val missing=practice.copy(noteFingerings=listOf(NoteFingering("missing",1,"audit")))
        assertTrue(runCatching { ScoreValidation.validate(song.score,listOf(missing)) }.isFailure)
        val absentVisit=practice.copy(noteFingerings=listOf(NoteFingering("simple-n1",1,"audit",2)))
        assertTrue(runCatching { PracticeCompiler.compile(song.score,absentVisit) }.isFailure)
    }
}
