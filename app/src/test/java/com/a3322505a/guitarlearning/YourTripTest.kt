package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class YourTripTest {
    @Test fun catalogueKeepsSixCompleteExercisesAndNoRepeatVariants() {
        val phrases=Phrases.catalogue.filter { it.id.startsWith("YT-") }
        assertEquals(YourTrip.phrases,phrases)
        assertEquals(listOf(10,16,8,17,19,6),phrases.map { it.sequence.barCount })
        assertEquals(listOf(0,9,25,32,97,135),phrases.map { it.sequence.originalBar })
        assertTrue(phrases.all { Phrases.versions(it).isEmpty() && it.sourceBpm==142 && it.sequence.capoFret==2 })
        assertEquals(40,phrases.sumOf { it.sequence.links.size })
        assertEquals(2,phrases.sumOf { it.sequence.tuplets.size })
        validatePhrases(phrases)
    }

    @Test fun everyWrittenPdfEventMatchesTheAppAfterTiesAndExplicitBoundaryPadding() {
        val rows=javaClass.getResourceAsStream("/your-trip-pdf.csv")!!.bufferedReader().use { it.readLines() }.drop(1).map { it.split(',').map(String::toInt) }
        val windows=mapOf("YT-GROOVE" to (0 to 444),"YT-ARPEGGIO" to (18 to 768),"YT-LEGATO" to (0 to 360),
            "YT-RIFF" to (24 to 816),"YT-RING" to (0 to 912),"YT-END" to (0 to 288))
        YourTrip.phrases.forEach { phrase ->
            val seq=phrase.sequence;val (first,stop)=windows.getValue(phrase.id)
            val selected=rows.filter { it[0] in seq.originalBar+1..seq.originalBar+seq.barCount }
            selected.groupBy { it[0] to it[1] }.forEach { (at,notes) ->
                val tick=(at.first-seq.originalBar-1)*48+at.second
                val event=seq.eventAt(tick.toDouble())!!
                val where="${phrase.id} PDF bar ${at.first}, tick ${at.second}"
                if(tick<first || tick>=stop) {
                    assertTrue(where,event.rest)
                } else {
                    assertTrue(where,event.tick<=tick && event.tick+event.duration>=tick+notes.first()[2])
                    assertEquals(where,notes.filter { it[3]!=0 && it[5]==0 }.map { Position(it[3],it[4]) }.toSet(),event.positions.toSet())
                    assertEquals(where,notes.filter { it[5]==1 }.map { it[3] }.toSet(),event.deadStrings)
                    if(notes.all { it[6]==1 }) assertTrue(where,event.tick<tick)
                    else assertEquals(where,tick,event.tick)
                    event.positions.forEach { assertEquals(it.midi+2,seq.midi(it)) }
                }
            }
            assertEquals(seq.barCount,seq.tabBars().size)
            assertEquals(seq.length,seq.tabBars().sumOf { b->b.segments.sumOf { it.value.ticks } })
        }
    }

    @Test fun independentlyReadPhraseEndingsAndOrnamentsRemainIntact() {
        val groove=Phrases.named("YT-GROOVE").sequence
        assertEquals(Event(432,12,listOf(Position(3,7)),vibrato=true),groove.events.first { it.tick==432 })
        assertEquals(1.5,groove.events.first { it.tick==120 }.stringExpression.getValue(3).grace!!.writtenTicks,0.0)
        assertEquals(2.0,groove.events.first { it.tick==228 }.stringExpression.getValue(3).bend.last().semitones,0.0)
        val arpeggio=Phrases.named("YT-ARPEGGIO").sequence
        val cadence=arpeggio.events.first { it.tick==738 }
        assertEquals(listOf(Position(2,3),Position(3,2),Position(4,0)),cadence.positions)
        assertEquals(30,cadence.duration)
        assertEquals(Technique.HAMMER,arpeggio.events.first { it.tick==162 }.stringExpression.getValue(2).grace!!.kind)
        val legato=Phrases.named("YT-LEGATO").sequence
        assertEquals(30,legato.events.first { it.tick==330 }.duration)
        assertEquals(listOf(Position(2,3),Position(3,2),Position(4,0),Position(5,2)),legato.eventAt(359.0)!!.positions)
        assertTrue(legato.eventAt(360.0)!!.rest)
        val riff=Phrases.named("YT-RIFF").sequence
        assertTrue(riff.eventAt(23.0)!!.rest)
        assertEquals(listOf(Position(3,6)),riff.eventAt(24.0)!!.positions)
        assertEquals(listOf(Tuplet(222,2),Tuplet(606,2)),riff.tuplets)
        assertTrue(riff.tabBars()[12].readableWidth()>=48*26/2)
        val ending=Phrases.named("YT-END").sequence
        assertEquals(listOf(Position(1,7),Position(3,4)),ending.events.last().positions)
        assertEquals(6,ending.events.last().duration)
    }

    private fun sineSampler()=Sampler(SoundBank.NYLON) { root ->
        val bytes=ByteBuffer.allocate(22050*12).order(ByteOrder.LITTLE_ENDIAN)
        val hz=440*2.0.pow((root-69)/12.0)
        repeat(22050*6) { bytes.putShort((sin(2*PI*hz*it/22050)*12000).toInt().toShort()) }
        bytes.array()
    }
    private fun pitch(wave: ShortArray): Double {
        val crossings=(1 until wave.size).filter { wave[it-1]<=0 && wave[it]>0 }
        assertTrue(crossings.size>4)
        return (crossings.size-1)*44100.0/(crossings.last()-crossings.first())
    }
    @Test fun hammerPullChainReachesAllThreePitchesAndKeepsSoundAfterTheSecondTarget() {
        val seq=Sequence(listOf(Event(0,12,listOf(Position(2,1))),Event(12,12,listOf(Position(2,3)),pick=Pick.NONE),
            Event(24,24,listOf(Position(2,1)),pick=Pick.NONE)),48,"chain",
            links=listOf(Legato(0,12,2,Technique.HAMMER),Legato(12,24,2,Technique.PULL)))
        val session=Session(seq,60,false,1);val pcm=Pcm(session,sineSampler())
        for((tick,hz) in listOf(6 to 261.63,18 to 293.66,30 to 261.63)) {
            assertEquals(hz,pitch(pcm.block(session.frame(tick.toDouble()),4000,true,false)),1.0)
        }
        val around=pcm.block(session.frame(24.0)-300,600,true,false)
        assertTrue((1 until around.size).maxOf { abs(around[it].toInt()-around[it-1].toInt()) }<800)
        assertTrue(pcm.block(session.frame(48.0),1000,true,false).all { it==0.toShort() })
        assertTrue(runCatching { seq.copy(events=seq.events.map { if(it.tick==12) it.copy(short=true) else it }).validateExpression() }.isFailure)
    }

    @Test fun letRingStopsAtEachPrintedBracketAndDoesNotLeakIntoTheNextGroup() {
        val seq=Phrases.named("YT-RING").sequence
        val openHigh=seq.events.first { it.tick==18 }
        assertEquals(192,openHigh.ringEndTick)
        assertEquals(174,seq.soundDuration(openHigh,Position(1,0)))
        assertEquals(setOf(192,384,576,768),seq.events.mapNotNull { it.ringEndTick }.toSet())
        assertEquals(60,seq.events.first { it.tick==36 }.duration)
        val closing=seq.events.last()
        assertEquals(48,closing.duration)
        assertEquals(setOf(5),closing.deadStrings)
        val isolated=Sequence(listOf(Event(0,12,listOf(Position(1,0)),ring=true,ringEndTick=48),Event(12,84,emptyList())),96,"scope")
        val session=Session(isolated,120,false,1);val pcm=Pcm(session,sineSampler())
        assertTrue(pcm.block(session.frame(30.0),2048,true,false).any { it!=0.toShort() })
        assertTrue(pcm.block(session.frame(48.0),2048,true,false).all { it==0.toShort() })
    }

    @Test fun eachExerciseCanRenderSelectedBarsAndEndsAtItsLoopBoundary() {
        val sampler=Sampler(SoundBank.NYLON) { root -> java.io.File("src/main/assets/guitar-nylon/$root.pcm").readBytes() }
        YourTrip.phrases.forEach { phrase ->
            val seq=phrase.sequence;val range=BarRange(seq.barCount-1,seq.barCount-1)
            val session=Session(seq,142,true,1,range=range);val pcm=Pcm(session,sampler)
            assertEquals(48,session.practiceLength)
            assertTrue(pcm.block(session.frame(session.totalTicks!!.toDouble()),2048,true,false).all { it==0.toShort() })
            assertEquals(session.preTicks.toDouble(),session.resumeTick(session.preTicks+24.0),0.0)
        }
    }
}
