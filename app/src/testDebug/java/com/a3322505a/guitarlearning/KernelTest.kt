@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
package com.a3322505a.guitarlearning
import alphaTab.midi.*
import alphaTab.model.*
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class KernelTest {
    @Test fun tripletsRestsAndBothMetersReachNativeNotationAndMidi() {
        for (beats in listOf(2,3,4,6)) {
            val seq=Sequence((0 until beats*3).map { Event(it*4,4,if(it%3==2) emptyList() else listOf(Position(3,5))) },beats*12,"triplets",beatsPerBar=beats,
                tuplets=(0 until beats).map { Tuplet(it*12,4) })
            val score=NativeContent.score(seq)
            val engraved=score.tracks.first().staves.first().bars.first().voices.first().beats.toList()
            assertEquals(beats*3,engraved.size)
            assertEquals(beats,engraved.count { it.isRest })
            assertTrue(engraved.all { it.tupletNumerator==3.0 && it.tupletDenominator==2.0 })
            assertEquals(beats*960.0,EngineProjection.generate(score).second.visits.last().end,1e-6)
        }
    }
    @Test fun deadStringsPalmMuteAndLegatoAreNativeTechniquesWithActualSound() {
        for ((name,seq) in NotationFixtures.demonstrations) {
            seq.validateExpression()
            val score=NativeContent.score(seq)
            assertTrue(name,NativeContent.pcm(score).any { kotlin.math.abs(it)>.0001f })
            val notes=score.tracks.first().staves.first().bars.flatMap { it.voices.toList() }.flatMap { it.beats.toList() }.flatMap { it.notes.toList() }
            if(name=="mixed") assertTrue(notes.any { it.isDead })
            if(name=="palm-mute") assertTrue(notes.all { it.isPalmMute })
            if(name==Technique.HAMMER.name || name==Technique.PULL.name) assertTrue(notes.any { it.isHammerPullOrigin })
            if(name==Technique.SLIDE.name) assertTrue(notes.any { it.slideOutType!=SlideOutType.None })
        }
    }
    @Test fun legatoRejectsMissingIllegalAndWrongDirectionRelations() {
        fun rejects(seq: Sequence) = assertTrue(runCatching { seq.validateTabRhythm() }.isFailure)
        Technique.entries.forEach { kind ->
            val seq=NotationFixtures.legato(kind)
            assertEquals(2,seq.performance.relations.size)
            rejects(seq.copy(links=emptyList()))
            rejects(seq.copy(links=listOf(seq.links[0].copy(targetTick=11))))
            rejects(seq.copy(links=listOf(seq.links[0].copy(string=2))))
        }
        val seq=NotationFixtures.legato(Technique.HAMMER)
        rejects(seq.copy(links=listOf(seq.links[0].copy(kind=Technique.PULL),seq.links[1])))
    }
    @Test fun strumOrderIsPreservedByNativeMidi() {
        fun starts(pick: Pick): List<Double> {
            val seq=Sequence(listOf(Event(0,48,listOf(Position(6,0),Position(3,0),Position(1,0)),strum=true,pick=pick)),48,"strum")
            return EngineProjection.generate(NativeContent.score(seq)).first.events.filterIsInstance<NoteOnEvent>().sortedBy { it.tick }.map { it.noteKey }
        }
        assertEquals(listOf(40.0,55.0,64.0),starts(Pick.DOWN))
        assertEquals(listOf(64.0,55.0,40.0),starts(Pick.UP))
    }
}
