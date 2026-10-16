package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PhraseTest {
    @Test fun reducedChordGripsHaveCorrectTonesFingersAndRoles() {
        val expected=mapOf(
            "Gsus2" to listOf(43,50,57), "Asus4(no5)" to listOf(45,50,57),
            "Bm7(no5)" to listOf(47,50,57), "D/F♯" to listOf(42,50,57),
        )
        expected.forEach { (name,pitches) ->
            val chord=Chords.named(name)
            assertEquals(pitches,chord.positions.map { it.midi })
            chord.frets.forEachIndexed { index,fret ->
                assertEquals(fret!=null && fret>0,chord.fingers[index]>0)
                assertEquals(fret!=null,chord.positions.any { it.string==6-index })
            }
            val check=chordSequence(chord,chord,ChordExercise.CHECK,4)
            assertEquals(chord.positions,check.events.flatMap { it.positions })
            assertTrue(chord.arpeggio.all { it in chord.positions })
        }
        assertEquals("二音（挂留）",Chords.named("Gsus2").role(Position(3,2)))
        assertEquals("四音（挂留）",Chords.named("Asus4(no5)").role(Position(4,0)))
        assertEquals("小七音",Chords.named("Bm7(no5)").role(Position(3,2)))
        assertEquals("三音",Chords.named("Bm7(no5)").role(Position(4,0)))
        assertEquals("三音",Chords.named("D/F♯").role(Position(6,2)))
    }
    @Test fun phraseHasExactEighthNotesHarmonyAndNoCapoPitchShift() {
        val phrase=LegacyUsotsukiFixture.phrase
        val seq=phrase.sequence
        assertEquals("标准调弦 · 无变调夹",phrase.conditions)
        assertEquals(80,phrase.sourceBpm)
        assertEquals(96,seq.length)
        assertEquals(listOf(43,50,57,50,45,50,57,50,47,50,57,50,42,50,57,50),seq.events.flatMap { it.positions }.map { it.midi })
        assertEquals((0..15).map { it*6 },seq.events.map { it.tick })
        assertTrue(seq.events.all { it.duration==6 && it.ring && !it.strum })
        assertEquals(listOf(0,24,48,72),seq.sections.map { it.tick })
        seq.events.forEach { event ->
            val chord=Chords.named(seq.sections[seq.sectionAt(event.tick.toDouble())].chord)
            assertEquals(chord.name,event.chord)
            assertTrue(event.positions.all { it in chord.positions })
        }
        val bars=seq.tabBars()
        assertEquals(2,bars.size)
        assertTrue(bars.all { it.segments.size==8 && it.segments.sumOf { s -> s.value.ticks }==48 })
        assertTrue(bars.flatMap { it.segments }.all { it.value==RhythmValue.EIGHTH && !it.tieIn && !it.tieOut })
        assertEquals("D/F♯",seq.sections[seq.sectionAt(95.9)].chord)
        assertEquals(0.0,Session(seq,60,false,4).cursor(96.0).localTick,0.0)
    }
    @Test fun ringStopsAtRepickedStringGripChangeAndLoopEndAndPcmIsChunkInvariant() {
        val seq=LegacyUsotsukiFixture.phrase.sequence
        assertEquals(listOf(24,12,12,6,24,12,12,6,24,12,12,6,24,12,12,6),seq.events.map { seq.soundDuration(it,it.positions.single()) })
        val sampler=Sampler(SoundBank.NYLON) { root -> File("../assets/soundfont-source/guitar-nylon/$root.pcm").readBytes() }
        val session=Session(seq,80,false,1)
        val pcm=Pcm(session,sampler)
        val count=session.frame(96.0).toInt()
        val all=pcm.block(0,count,true,false)
        assertTrue(all.any { it!=0.toShort() })
        val chunks=ShortArray(count)
        for(start in 0 until count step 8191) pcm.block(start.toLong(),minOf(8191,count-start),true,false).copyInto(chunks,start)
        assertArrayEquals(all,chunks)
        assertTrue(pcm.block(count.toLong(),4096,true,false).all { it==0.toShort() })
        val output=File("../output/usotsuki-S01-demo-80bpm.wav")
        output.parentFile.mkdirs()
        val data=ByteBuffer.allocate(44+all.size*2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36+all.size*2).put("WAVEfmt ".toByteArray()).putInt(16)
        data.putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE*2).putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(all.size*2)
        all.forEach { data.putShort(it) }
        output.writeBytes(data.array())
    }
}
