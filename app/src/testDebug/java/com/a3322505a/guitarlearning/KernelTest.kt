package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.practice.LegacyPhraseFixtures as Phrases

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class KernelTest {
    private fun sampler()=Sampler(SoundBank.NYLON) { File("../assets/soundfont-source/guitar-nylon/$it.pcm").readBytes() }
    private fun energy(data: ShortArray)=data.sumOf { it.toDouble()*it }
    private fun rejects(seq: Sequence)=assertTrue(runCatching { seq.tabBars() }.isFailure)
    private fun pcm(e: Event)=Pcm(Session(Sequence(listOf(e.copy(tick=0,duration=48)),48,"test"),60,false,1),sampler())
    @Test fun tripletsKeepExactDurationsRestsAndGroupsInBothMeters() {
        val two=Phrases.named("T01").sequence
        val four=two.copy(length=48,beatsPerBar=4,events=two.events+Event(24,24,emptyList()))
        listOf(two,four).forEach { seq ->
            val segments=seq.tabBars().flatMap { it.segments }
            assertEquals(seq.length,segments.sumOf { it.value.ticks })
            assertEquals(listOf(4,4,4,2,2,2),segments.take(6).map { it.value.ticks })
            assertTrue(segments[2].rest)
            assertEquals(listOf(0,12),segments.filter { it.tuplet?.tick==it.tick }.map { it.tick })
            val session=Session(seq,137,true,4)
            assertEquals(seq.barTicks,session.preTicks)
            assertEquals(seq.barTicks.toDouble(),session.resumeTick(seq.barTicks+14.0),0.0)
            val notes=Pcm(session,sampler())
            assertTrue(notes.block(session.frame((seq.barTicks+8).toDouble()),512,true,false).all { it==0.toShort() })
            for(loop in 0..10) assertEquals(round((seq.barTicks+loop*seq.length)*44100.0*60/(137*12)).toLong(),session.frame((seq.barTicks+loop*seq.length).toDouble()))
        }
        rejects(two.copy(tuplets=emptyList()))
        rejects(two.copy(tuplets=listOf(Tuplet(1,4))))
        rejects(two.copy(tuplets=two.tuplets+Tuplet(0,4)))
        rejects(two.copy(events=two.events.mapIndexed { i,e -> if(i==1) e.copy(duration=2) else e }))
    }
    @Test fun expressionHasDistinctAudioAndDeadStringsStopRingingNotes() {
        val p=Position(6,0);val normal=pcm(Event(0,48,listOf(p)))
        val pm=pcm(Event(0,48,listOf(p),palmMute=true))
        val dead=pcm(Event(0,48,emptyList(),deadStrings=setOf(6)))
        val short=pcm(Event(0,48,listOf(p),short=true))
        val weak=pcm(Event(0,48,listOf(p),velocity=.5f))
        val accent=pcm(Event(0,48,listOf(p),accent=true))
        val base=energy(normal.block(100,4000,true,false))
        assertEquals(.25,energy(weak.block(100,4000,true,false))/base,.002)
        assertEquals(1.5625,energy(accent.block(100,4000,true,false))/base,.003)
        assertTrue(energy(dead.block(100,4000,true,false))>0)
        assertFalse(pm.block(100,4000,true,false).contentEquals(normal.block(100,4000,true,false)))
        assertTrue(energy(pm.block(20000,4000,true,false))<energy(normal.block(20000,4000,true,false))*.05)
        assertTrue(short.block(15000,1000,true,false).all { it==0.toShort() })
        val seq=Sequence(listOf(Event(0,12,listOf(p),ring=true),Event(12,12,emptyList(),deadStrings=setOf(6)),Event(24,24,emptyList())),48,"dead cut")
        assertEquals(12,seq.soundDuration(seq.events[0],p))
        assertFalse(seq.tabBars().single().segments[1].rest)
        assertTrue(seq.tabBars().single().segments[1].positions.isEmpty())
        val mixed=Phrases.named("R02").sequence.events.first { it.positions.isNotEmpty() && it.deadStrings.isNotEmpty() }
        assertEquals(setOf(5,4,3),mixed.strings.toSet())
        assertTrue(pcm(mixed).block(100,4000,true,false).any { it!=0.toShort() })
    }
    @Test fun upstrokeStartsWithTheHighStringAndKeepsLegacyDownstroke() {
        val positions=listOf(Position(6,0),Position(3,0),Position(1,0))
        val up=pcm(Event(0,48,positions,strum=true,pick=Pick.UP))
        val down=pcm(Event(0,48,positions,strum=true))
        val high=pcm(Event(0,48,listOf(positions.last())))
        val low=pcm(Event(0,48,listOf(positions.first())))
        for(i in 0 until 400) {
            assertEquals(high.block(0,400,true,false)[i]/sqrt(3.0),up.block(0,400,true,false)[i].toDouble(),1.0)
            assertEquals(low.block(0,400,true,false)[i]/sqrt(3.0),down.block(0,400,true,false)[i].toDouble(),1.0)
        }
    }
    @Test fun selectedBarsUseOriginalEventsResumeWithinRangeAndClearTheirEnd() {
        val phrase=SongTestLibrary.sourceMozart[1];val seq=phrase.sequence
        val session=Session(seq,137,true,4,range=BarRange(2,3))
        assertSame(seq,session.sequence)
        assertEquals(24,session.preTicks)
        assertEquals(48,session.practiceLength)
        assertEquals(48.0,session.cursor(24.0).localTick,0.0)
        assertEquals(2,session.cursor(24.0).bar)
        assertEquals(0,session.cursor(71.9).loop)
        assertEquals(1,session.cursor(72.0).loop)
        assertEquals(48.0,session.resumeTick(60.0),0.0)
        val sound=Pcm(session,sampler())
        assertTrue(sound.block(0,4096,true,false).all { it==0.toShort() })
        assertTrue(sound.block(session.frame(24.0),4096,true,false).any { it!=0.toShort() })
        assertTrue(sound.block(session.frame(session.totalTicks!!.toDouble()),4096,true,true).all { it==0.toShort() })
        assertTrue(runCatching { Session(seq,60,true,4,range=BarRange(5,4)) }.isFailure)
        val p=Position(6,0)
        val ringing=Sequence(listOf(Event(0,12,listOf(p),ring=true),Event(12,84,emptyList())),96,"carry")
        val carry=Session(ringing,60,true,1,range=BarRange(1,1))
        assertTrue(Pcm(carry,sineSampler()).block(carry.frame(48.0)+300,2048,true,false).any { it!=0.toShort() })
        assertEquals(48.0,carry.resumeTick(70.0),0.0)
    }
    private fun sineSampler()=Sampler(SoundBank.NYLON) { root ->
        val bytes=ByteBuffer.allocate(22050*20).order(ByteOrder.LITTLE_ENDIAN)
        val hz=440*2.0.pow((root-69)/12.0)
        repeat(22050*10) { bytes.putShort((sin(2*PI*hz*it/22050)*12000).toInt().toShort()) }
        bytes.array()
    }
    private fun pitch(wave: FloatArray,start: Int,count: Int): Double {
        val crossings=(start+1 until start+count).filter { wave[it-1]<=0 && wave[it]>0 }
        return (crossings.size-1)*44100.0/(crossings.last()-crossings.first())
    }
    @Test fun continuousSlideArrivesAtTargetPitchWithoutAnotherPickedAttack() {
        val sampler=sineSampler()
        val slide=sampler.linked(60,62,44100,88200,Technique.SLIDE)
        assertEquals(261.63,pitch(slide,1000,2000),3.0)
        assertEquals(277.18,pitch(slide,21000,2000),3.0)
        assertEquals(293.66,pitch(slide,46000,2000),1.0)
        assertTrue((43000..45000).maxOf { abs(slide[it]-slide[it-1]) }<.02f)
        for(kind in listOf(Technique.HAMMER,Technique.PULL)) {
            val from=if(kind==Technique.HAMMER) 60 else 62;val to=if(kind==Technique.HAMMER) 62 else 60
            val wave=sampler.linked(from,to,44100,88200,kind)
            assertEquals(440*2.0.pow((from-69)/12.0),pitch(wave,20000,2000),1.0)
            assertEquals(440*2.0.pow((to-69)/12.0),pitch(wave,46000,2000),1.0)
            assertTrue((44000..44500).maxOf { abs(wave[it]-wave[it-1]) }<.02f)
        }
    }
    @Test fun legatoSurvivesBarSelectionAndRejectsMissingOrIllegalRelations() {
        listOf("SL01","H01","P01").forEach { id ->
            val seq=Phrases.named(id).sequence
            assertEquals(2,seq.links.size)
            assertEquals(Pick.NONE,seq.events.first { it.tick==48 }.stroke)
            val session=Session(seq,60,true,1,range=BarRange(1,1))
            val pcm=Pcm(session,sineSampler())
            assertTrue(pcm.block(0,4096,true,false).all { it==0.toShort() })
            assertTrue(pcm.block(session.frame(48.0)+300,2048,true,false).any { it!=0.toShort() })
            assertTrue(pcm.block(session.frame(96.0),2048,true,false).all { it==0.toShort() })
            assertEquals(48.0,session.resumeTick(70.0),0.0)
            assertEquals(seq.links[1].targetTick,seq.events.first { it.tick==48 }.tick)
            rejects(seq.copy(links=emptyList()))
            rejects(seq.copy(links=listOf(seq.links[0].copy(targetTick=11))))
            rejects(seq.copy(links=listOf(seq.links[0].copy(string=2))))
        }
        val seq=Phrases.named("H01").sequence
        rejects(seq.copy(links=listOf(seq.links[0].copy(kind=Technique.PULL),seq.links[1])))
    }
    @Test fun allNewContentRendersInChunksAndExportsDemonstrations() {
        val bank=sampler()
        listOf("T01","R01","R02","PM01","SL01","H01","P01").forEach { id ->
            val session=Session(Phrases.named(id).sequence,60,false,1)
            val pcm=Pcm(session,bank);val frames=session.frame(session.totalTicks!!.toDouble()).toInt()
            val all=pcm.block(0,frames,true,false)
            val chunks=ShortArray(frames)
            for(start in 0 until frames step 997) pcm.block(start.toLong(),minOf(997,frames-start),true,false).copyInto(chunks,start)
            assertArrayEquals(all,chunks)
            assertTrue(pcm.block(frames.toLong(),2048,true,false).all { it==0.toShort() })
            val data=ByteBuffer.allocate(44+frames*2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36+frames*2).put("WAVEfmt ".toByteArray()).putInt(16)
            data.putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE*2).putShort(2).putShort(16)
            data.put("data".toByteArray()).putInt(frames*2);all.forEach { data.putShort(it) }
            File("../output/kernel-v63/$id-60bpm.wav").apply { parentFile!!.mkdirs();writeBytes(data.array()) }
        }
    }
}
