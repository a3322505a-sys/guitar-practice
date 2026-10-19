package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.practice.LegacyPhraseFixtures as Phrases

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test

class SevenBlocksTest {
    @Test fun allSevenBlocksKeepEveryWrittenEventAndEveryUnfoldedRepeat() {
        // Independently retained PDF transcription, not emitted by the GP importer.
        val rows=javaClass.getResourceAsStream("/seven-blocks-source.csv")!!.bufferedReader().use { it.readLines() }.drop(1).map { it.split(',') }
        assertEquals(991,rows.size) // 988 noteheads plus three explicit rests.
        val phrases=SevenBlocks.phrases
        assertEquals(phrases,Phrases.catalogue.filter { it.id.startsWith("SB-") })
        assertEquals(listOf(12,32,20,16,20,20,20),phrases.map { it.sequence.barCount })
        assertEquals(38,phrases.sumOf { it.sequence.sourceBars.distinct().size })
        validatePhrases(phrases)
        phrases.forEachIndexed { index,phrase ->
            val seq=phrase.sequence
            assertSame(phrase,Phrases.practiceNamed(phrase.id))
            assertEquals(24,seq.ticksPerQuarter)
            seq.sourceBars.forEachIndexed { bar,number ->
                val expected=rows.filter { it[0].toInt()==index+1 && it[1].toInt()==number }.groupBy { it[2].toInt() }
                assertEquals(expected.size,seq.events.count { it.tick in seq.barStart(bar) until seq.barEnd(bar) })
                expected.forEach { (tick,notes) ->
                    val e=seq.events.single { it.tick==seq.barStart(bar)+tick }
                    val where="${phrase.id} source $number unfolded ${bar+1} tick $tick"
                    assertEquals(where,notes[0][3].toInt(),e.duration)
                    assertEquals(where,notes.filter { it[4]!="0" && it[7]=="0" }.map { Position(it[4].toInt(),it[5].toInt()) }.toSet(),e.positions.toSet())
                    assertEquals(where,notes.filter { it[7]=="1" }.map { it[4].toInt() }.toSet(),e.deadStrings)
                    assertEquals(where,Pick.valueOf(notes[0][8]),e.pick)
                    assertEquals(where,notes[0][9]=="1",seq.tuplets.any { e.tick in it.tick until it.end })
                    notes.filter { it[4]!="0" && it[7]=="0" }.forEach { assertEquals(it[6].toInt(),seq.midi(Position(it[4].toInt(),it[5].toInt()))) }
                }
            }
            val engraved=seq.tabBars()
            assertEquals(seq.length,engraved.sumOf { b -> b.segments.sumOf { it.duration } })
            engraved.forEach { b ->
                assertEquals(seq.barStart(b.index),b.segments.first().tick)
                assertEquals(seq.barEnd(b.index),b.segments.last().end)
            }
        }
    }

    @Test fun sourceRepeatsAndUnknownCountsKeepTheirActualBoundaries() {
        assertEquals(listOf(1,1,1,1,2,2,2,2,3,3,3,3),SevenBlocks.phrases[0].sequence.sourceBars)
        assertEquals(List(4) { listOf(5,6) }.flatten()+List(4) { listOf(7,8,9,10) }.flatten()+List(2) { listOf(11,12,13,14) }.flatten(),SevenBlocks.phrases[1].sequence.sourceBars)
        assertEquals(List(2) { listOf(16,17) }.flatten()+List(2) { listOf(18,19) }.flatten()+List(4) { listOf(20,21,22) }.flatten(),SevenBlocks.phrases[2].sequence.sourceBars)
        assertEquals((24..27).flatMap { bar -> List(4) { bar } },SevenBlocks.phrases[3].sequence.sourceBars)
        assertEquals((29..33).flatMap { bar -> List(4) { bar } },SevenBlocks.phrases[4].sequence.sourceBars)
        assertEquals(List(4) { listOf(35,36) }.flatten()+List(4) { 37 }+List(8) { 38 },SevenBlocks.phrases[5].sequence.sourceBars)
        assertEquals(List(4) { listOf(40,41,42) }.flatten()+List(4) { 43 }+List(4) { 44 },SevenBlocks.phrases[6].sequence.sourceBars)
        assertTrue(SevenBlocks.phrases[1].sequence.explanation.contains("次数待核"))
        assertTrue(SevenBlocks.phrases[2].sequence.explanation.contains("次数待核"))
        val all=SevenBlocks.phrases.flatMap { it.sequence.sourceBars }.toSet()
        assertTrue(listOf(4,15,23,28,34,39).none { it in all })
    }

    @Test fun thirtySecondsAndMixedMetersKeepAudioCursorAndCountInInQuarterNoteTime() {
        val sampler=Sampler(SoundBank.NYLON) { root -> java.io.File("../assets/soundfont-source/guitar-nylon/$root.pcm").readBytes() }
        val seq=SevenBlocks.phrases[3].sequence
        val notation=seq.tabBars().flatMap { it.segments }
        assertTrue(notation.any { it.value==RhythmValue.THIRTY_SECOND && it.duration==3 && it.value.flags==3 })
        assertTrue(notation.any { it.value==RhythmValue.TRIPLET_THIRTY_SECOND && it.duration==2 && it.value.flags==3 })
        assertEquals(3,notation.zipWithNext().maxOf { (a,b)->beamCount(a,b) })
        SevenBlocks.phrases.forEach { phrase ->
            val s=phrase.sequence
            val bar=s.barCount-1
            val session=Session(s,60,true,2,range=BarRange(bar,bar))
            val pcm=Pcm(session,sampler)
            assertEquals(s.meter(bar)*24,session.preTicks)
            assertEquals(s.meter(bar)*44100L,session.frame(session.preTicks.toDouble()))
            for(beat in 0 until s.meter(bar)) {
                val tick=session.preTicks+beat*24.0
                assertEquals(beat,session.cursor(tick).beat)
                assertEquals(tick,session.tick(session.frame(tick)),.001)
                assertTrue(pcm.block(session.frame(beat*24.0),500,false,true).any { it!=0.toShort() })
                assertTrue(pcm.block(session.frame(beat*24.0+12),500,false,true).all { it==0.toShort() })
            }
            assertEquals(session.preTicks.toDouble(),session.resumeTick(session.preTicks+3.0),0.0)
            assertTrue(pcm.block(session.frame(session.totalTicks!!.toDouble()),1000,true,true).all { it==0.toShort() })
        }
        val mixed=SevenBlocks.phrases[2].sequence
        assertEquals(setOf(3,5,6),mixed.barMeters.toSet())
        val legacy=Phrases.named("M01").sequence
        assertEquals(12,legacy.ticksPerQuarter)
        assertEquals(44100L,Session(legacy,60,false,1).frame(12.0))
    }
}
