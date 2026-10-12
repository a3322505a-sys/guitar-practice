package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ShayouTest {
    @Test fun fiveWholePassagesKeepOriginalBoundariesAndDefaultWholePhraseRange() {
        assertEquals(listOf("SHA-INTRO","SHA-RIFF","SHA-VERSE","SHA-PRE","SHA-CHORUS"),Phrases.shayou.map { it.id })
        assertEquals(listOf(8,9,8,6,8),Phrases.shayou.map { it.sequence.tabBars().size })
        assertEquals(listOf(1,8,17,25,31),Phrases.shayou.map { it.sequence.originalBar })
        validatePhrases(Phrases.all)
        Phrases.shayou.forEach {
            assertEquals(124,it.sourceBpm)
            assertEquals("标准调弦 · 变调夹3品",it.conditions)
            assertEquals(3,it.sequence.capoFret)
            assertEquals(BarRange(0,it.sequence.length/48-1),Session(it.sequence,60,true,4).range)
        }
        val riff=Phrases.named("SHA-RIFF").sequence
        assertTrue(riff.eventAt(35.9)!!.rest)
        assertEquals(listOf(Position(4,7)),riff.eventAt(36.0)!!.positions)
        assertEquals(15,riff.links.size)
        assertTrue(riff.events.last().vibrato)
        assertEquals(1,Phrases.named("SHA-VERSE").sequence.tuplets.size)
    }

    @Test fun everyGpBeatRetainsStringFretRealMidiRestDeadStringsAndTieContinuation() {
        val rows=javaClass.getResourceAsStream("/shayou-gp.csv")!!.bufferedReader().use { it.readLines() }.drop(1).map { it.split(',') }
        rows.groupBy { Triple(it[0],it[1].toInt(),it[2].toInt()) }.forEach { (key,notes) ->
            val seq=Phrases.named(key.first).sequence
            val tick=(key.second-seq.originalBar-1)*48+key.third
            val event=seq.eventAt(tick.toDouble())!!
            assertTrue("${key.first} GP bar ${key.second} tick ${key.third}",event.tick<=tick && event.tick+event.duration>=tick+notes.first()[3].toInt())
            assertEquals(notes.filter { it[4]!="0" && it[7]=="0" }.map { Position(it[4].toInt(),it[5].toInt()) }.toSet(),event.positions.toSet())
            assertEquals(notes.filter { it[7]=="1" }.map { it[4].toInt() }.toSet(),event.deadStrings)
            notes.filter { it[4]!="0" && it[7]=="0" }.forEach { n ->
                assertEquals(n[6].toInt(),seq.midi(Position(n[4].toInt(),n[5].toInt())))
                assertEquals(n[8]=="1",event.tick<tick)
            }
            if(notes.all { it[4]=="0" }) assertTrue(event.rest)
        }
        Phrases.shayou.forEach { phrase ->
            val uniqueOnsets=rows.filter { it[0]==phrase.id && it[8]=="0" }.map { it[1] to it[2] }.distinct().size
            assertEquals(uniqueOnsets,phrase.sequence.events.size)
        }
    }

    @Test fun capoPcmMatchesActualPitchWithoutChangingRelativeFretNumbers() {
        val p=Position(3,7)
        val relative=Sequence(listOf(Event(0,48,listOf(p))),48,"capo",capoFret=3)
        val absolute=relative.copy(capoFret=0,events=listOf(Event(0,48,listOf(p.copy(fret=10)))))
        val sampler=Sampler(SoundBank.NYLON) { File("src/main/assets/guitar-nylon/$it.pcm").readBytes() }
        assertArrayEquals(Pcm(Session(relative,124,false,1),sampler).block(0,12000,true,false),
            Pcm(Session(absolute,124,false,1),sampler).block(0,12000,true,false))
        assertEquals(7,relative.tabBars().first().segments.first().positions.single().fret)
        assertEquals(65,relative.midi(p))
        val linkSeq=Phrases.named("SHA-RIFF").sequence
        val shifted=linkSeq.copy(capoFret=0,events=linkSeq.events.map { it.copy(positions=it.positions.map { p0->p0.copy(fret=p0.fret+3) }) })
        val a=Pcm(Session(linkSeq,124,false,1),sampler)
        val b=Pcm(Session(shifted,124,false,1),sampler)
        val start=Session(linkSeq,124,false,1).frame(48.0)
        assertArrayEquals(a.block(start,12000,true,false),b.block(start,12000,true,false))
    }

    @Test fun fullRiffLoopsAfterCompleteEndingAndClearsSoundAtRangeEnd() {
        val session=Session(Phrases.named("SHA-RIFF").sequence,124,true,2)
        assertEquals(432,session.practiceLength)
        assertEquals(912L,session.totalTicks)
        assertEquals(0,session.loopAt(479.9))
        assertEquals(1,session.loopAt(480.0))
        assertEquals(384.0,session.cursor(432.0).localTick,0.0)
        assertEquals(8,session.cursor(432.0).bar)
        val sampler=Sampler(SoundBank.NYLON) { File("src/main/assets/guitar-nylon/$it.pcm").readBytes() }
        val pcm=Pcm(session,sampler)
        assertTrue(pcm.block(session.frame(48.0),2048,true,false).all { it==0.toShort() })
        assertTrue(pcm.block(session.frame(84.0),2048,true,false).any { it!=0.toShort() })
        assertTrue(pcm.block(session.frame(912.0),2048,true,true).all { it==0.toShort() })
    }

    @Test fun parallelDoubleStopSlidesRetainBothStringsAndTripletTimingIsExact() {
        val chorus=Phrases.named("SHA-CHORUS").sequence
        val parallel=chorus.links.groupBy { it.sourceTick }.values.first { it.size==2 }
        assertEquals(setOf(1,2),parallel.map { it.string }.toSet())
        assertTrue(parallel.all { it.kind==Technique.SLIDE && chorus.position(it.sourceTick,it.string).fret==9 && chorus.position(it.targetTick,it.string).fret==10 })
        val verse=Phrases.named("SHA-VERSE").sequence
        val group=verse.tuplets.single()
        assertEquals(2,group.unit)
        assertEquals(listOf(2,2,2),verse.events.filter { it.tick>=group.tick && it.tick<group.end }.map { it.duration })
        assertTrue(chorus.tabBars().flatMap { it.segments }.any { it.expression!!.deadStrings==setOf(1,2) && !it.rest })
        assertTrue(runCatching { chorus.copy(links=chorus.links+parallel.first()).validateExpression() }.isFailure)
    }
}
