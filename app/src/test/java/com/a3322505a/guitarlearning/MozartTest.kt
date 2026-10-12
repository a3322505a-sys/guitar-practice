package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MozartTest {
    @Test fun threePhrasesPreserveEveryNativeGpNoteRestAndDuration() {
        // This fixture was read from the native GP ID graph independently of the JSON import.
        val expected=javaClass.getResourceAsStream("/mozart-k265-v2.csv")!!.bufferedReader().use { it.readLines() }
            .drop(1).map { row -> row.split(',').map(String::toInt) }
        val actual=Phrases.mozart.flatMapIndexed { index,phrase ->
            val seq=phrase.sequence
            assertEquals(2,seq.beatsPerBar)
            assertEquals(192,seq.length)
            assertEquals(8,seq.tabBars().size)
            assertTrue(seq.sections.isEmpty())
            assertEquals("参考速度",phrase.tempoLabel)
            seq.events.map { e ->
                val p=e.positions.singleOrNull()
                listOf(index*8+e.tick/24+1,e.tick%24,e.duration,p?.string ?: 0,p?.fret ?: 0,p?.midi ?: 0)
            }
        }
        assertEquals(3,Phrases.mozart.size)
        (1..6).forEach { old -> assertSame(Phrases.mozart[(old-1)/2],Phrases.named("K265-$old")) }
        assertEquals(expected,actual)
        assertEquals(165,actual.count { it[3]!=0 })
        assertEquals(2,actual.count { it[3]==0 })
        assertEquals(59,actual.filter { it[3]!=0 }.minOf { it[5] })
        assertEquals(76,actual.maxOf { it[5] })
    }

    @Test fun twoBeatCountInCursorResumeAndLoopsUseRealBarBoundaries() {
        val session=Session(Phrases.mozart.first().sequence,60,true,2)
        assertEquals(24,session.preTicks)
        assertEquals(408L,session.totalTicks)
        assertTrue(session.cursor(23.9).pre)
        assertEquals(1,session.cursor(23.9).beat)
        val c=session.cursor(63.0)
        assertFalse(c.pre)
        assertEquals(1,c.bar)
        assertEquals(1,c.beat)
        assertEquals(48.0,session.resumeTick(63.0),0.0)
        // The former four-bar cut now continues directly into bar 5 of the same loop.
        assertEquals(0,session.cursor(120.0).loop)
        assertEquals(4,session.cursor(120.0).bar)
        assertFalse(session.cursor(120.0).pre)
        assertNotNull(session.sequence.eventAt(96.0))
        assertEquals(1,session.cursor(216.0).loop)
        assertEquals(0,session.cursor(216.0).bar)
        assertEquals(0.0,session.cursor(216.0).localTick,0.0)
    }

    @Test fun sixteenthBeamsDottedEighthAndRestsKeepTheOriginalOnsets() {
        Phrases.mozart.forEach { phrase ->
            val seq=phrase.sequence
            val bars=seq.tabBars()
            bars.forEach { bar -> assertEquals(24,bar.segments.sumOf { it.value.ticks }) }
            for(tick in 0 until seq.length) {
                val e=seq.eventAt(tick.toDouble())!!
                val s=bars[tick/24].segments.single { tick>=it.tick && tick<it.end }
                assertEquals(e.id,s.eventId)
                assertEquals(e.positions,s.positions)
                assertFalse(s.tieIn)
                assertFalse(s.tieOut)
            }
        }
        val sixteenths=Phrases.mozart.first().sequence.tabBars().first().segments
        assertTrue(sixteenths.all { it.value==RhythmValue.SIXTEENTH })
        assertEquals(listOf(2,2,2,0,2,2,2),sixteenths.zipWithNext().map { (a,b)->beamCount(a,b) })
        val ending=Phrases.mozart[1].sequence.tabBars().last().segments
        assertEquals(listOf(RhythmValue.DOTTED_EIGHTH,RhythmValue.SIXTEENTH,RhythmValue.QUARTER),ending.map { it.value })
        assertEquals(1,beamCount(ending[0],ending[1]))
        assertEquals(0,beamCount(ending[1],ending[2]))
        val cadence=Phrases.mozart.last().sequence.tabBars().last().segments
        assertTrue(cadence.last().rest)
        assertEquals(0,beamCount(cadence.first(),cadence.last()))
    }

    @Test fun pcmUsesTwoBeatAccentsAndEachPhraseHasAnExactSilentEnd() {
        val sampler=Sampler(SoundBank.NYLON) { root -> File("src/main/assets/guitar-nylon/$root.pcm").readBytes() }
        Phrases.mozart.forEach { phrase ->
            val session=Session(phrase.sequence,80,true,1)
            val pcm=Pcm(session,sampler)
            val first=pcm.block(0,2000,false,true)
            assertArrayEquals(first,pcm.block(session.frame(24.0),2000,false,true))
            assertFalse(first.contentEquals(pcm.block(session.frame(12.0),2000,false,true)))
            assertTrue(pcm.block(0,4096,true,false).all { it==0.toShort() })
            assertTrue(pcm.block(session.frame(24.0),4096,true,false).any { it!=0.toShort() })
            assertTrue(pcm.block(session.frame(session.totalTicks!!.toDouble()),4096,true,true).all { it==0.toShort() })
        }
    }
}
