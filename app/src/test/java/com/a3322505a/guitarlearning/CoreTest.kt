package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.audio.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class CoreTest {
    @Test fun tuningAndEightVoicings() {
        assertEquals(listOf(40,45,50,55,59,64),Music.openMidi.reversed())
        val expected=listOf(
            listOf(48,52,55,60,64), listOf(45,52,57,60,64), listOf(43,47,50,55,59,67),
            listOf(40,47,52,55,59,64), listOf(50,57,62,66), listOf(50,57,62,65),
            listOf(40,47,52,56,59,64), listOf(45,52,57,61,64))
        Chords.all.take(8).forEachIndexed { index,c ->
            assertEquals(c.name,expected[index],c.positions.map { it.midi })
            c.frets.forEachIndexed { i,fret ->
                assertEquals(fret!=null,c.positions.any { it.string==6-i })
                assertEquals(fret!=null && fret>0,c.fingers[i]>0)
            }
            assertTrue(c.arpeggio.all { it in c.positions })
        }
        assertEquals("F♯",Chords.named("D").label(5))
        assertEquals("C♯",Chords.named("A").label(4))
        assertEquals("X",Chords.named("C").label(0))
    }
    @Test fun fiveShapesMatchSourceTableIncludingEdges() {
        val expected=listOf(
            "0,1,3|0,1,3|0,2|0,2,3|0,2,3|0,1,3",
            "3,5|3,5,6|2,4,5|2,3,5|2,3,5|3,5",
            "5,7,8|5,6,8|4,5,7|5,7|5,7,8|5,7,8",
            "7,8,10|8,10|7,9,10|7,9,10|7,8,10|7,8,10",
            "10,12,13|10,12,13|9,10,12|9,10,12|10,12|10,12,13")
        assertEquals(listOf("mi C","sol A","la G","si E","re D"),Patterns.all.map { "${it.solfege} ${it.caged}" })
        Patterns.all.forEachIndexed { i,p ->
            assertEquals(expected[i],p.frets.joinToString("|") { it.joinToString(",") })
            assertTrue(p.positions.all { it.midi%12 in listOf(0,2,4,5,7,9,11) })
        }
        assertTrue(Position(2,6) in Patterns.all[1].positions)
        assertTrue(Position(3,4) in Patterns.all[2].positions)
        assertTrue(Position(2,13) in Patterns.all[4].positions)
        assertTrue(Position(1,13) in Patterns.all[4].positions)
    }
    @Test fun s1QuarterAndEighthAndMinorAreRealRoutes() {
        val s1=Routes.scale(ScaleOptions()).sequence!!
        assertEquals(Patterns.s1 + Patterns.s1.dropLast(1).reversed(),s1.events.flatMap { it.positions })
        assertEquals(listOf(48,50,52,53,55,57,59,60),s1.route.map { it.midi })
        assertEquals(listOf(48,50,52,53,55,57,59,60,59,57,55,53,52,50,48),s1.events.flatMap { it.positions }.map { it.midi })
        assertEquals(192,s1.length)
        assertEquals((0..15).map { it*12 },s1.events.map { it.tick })
        assertEquals(12,s1.events.last().duration)
        assertTrue(s1.events.last().positions.isEmpty())
        val eighth=Routes.scale(ScaleOptions(eighth=true)).sequence!!
        assertEquals(96,eighth.length)
        assertEquals((0..15).map { it*6 },eighth.events.map { it.tick })
        assertEquals(6,eighth.events.last().duration)
        assertTrue(eighth.events.last().positions.isEmpty())
        val reverse=Routes.scale(ScaleOptions(reverse=true)).sequence!!
        assertEquals(listOf(60,59,57,55,53,52,50,48,50,52,53,55,57,59,60),reverse.events.flatMap { it.positions }.map { it.midi })
        val minor=Routes.scale(ScaleOptions(key=Tonality.A_MINOR)).sequence!!
        assertEquals(listOf(45,47,48,50,52,53,55,57),minor.route.map { it.midi })
        assertEquals(listOf(45,47,48,50,52,53,55,57,55,53,52,50,48,47,45),minor.events.flatMap { it.positions }.map { it.midi })
        assertEquals(3,Tonality.A_MINOR.degree(60))
        assertFalse(Tonality.A_MINOR.isRoot(Position(2,1)))
    }
    @Test fun roundTripsRespectRangeDirectionAndSingleTurningNote() {
        for(key in Tonality.entries) for(pattern in 0..4) for(octave in listOf(false,true)) for(reverse in listOf(false,true)) for(eighth in listOf(false,true)) {
            val seq=Routes.scale(ScaleOptions(pattern=pattern,key=key,octave=octave,reverse=reverse,eighth=eighth)).sequence!!
            val pitches=seq.events.flatMap { it.positions }.map { it.midi }
            val turn=pitches.size/2
            assertEquals(pitches.first(),pitches.last())
            assertEquals(1,pitches.count { it==pitches[turn] })
            assertTrue(pitches.take(turn+1).zipWithNext().all { (a,b) -> if(reverse) a>b else a<b })
            assertTrue(pitches.drop(turn).zipWithNext().all { (a,b) -> if(reverse) a<b else a>b })
            assertEquals(pitches.take(turn).reversed(),pitches.drop(turn+1))
            assertEquals(seq.length,seq.events.sumOf { it.duration })
            assertTrue(seq.events.filter { it.positions.isNotEmpty() }.all { it.duration==if(eighth) 6 else 12 })
        }
        for(start in 1..6) for(end in start..6) for(reverse in listOf(false,true)) {
            val seq=Routes.scale(ScaleOptions(firstString=start,lastString=end,octave=false,reverse=reverse)).sequence!!
            assertTrue(seq.route.all { it.string in start..end })
        }
        assertNull(Routes.scale(ScaleOptions(firstString=1,lastString=1)).sequence)
    }
    @Test fun restrictedConnectionsEitherCrossShapesOrExplainUnavailable() {
        for(key in Tonality.entries) for(pattern in 0..4) for(start in 1..6) for(end in start..6) for(reverse in listOf(false,true)) {
            val result=Routes.scale(ScaleOptions(pattern=pattern,key=key,exercise=ScaleExercise.CONNECTION,octave=false,firstString=start,lastString=end,reverse=reverse))
            val seq=result.sequence
            if(seq==null) { assertTrue(result.reason.isNotBlank()); continue }
            val first=Patterns.all[pattern].positions
            val next=Patterns.all[pattern+1].positions
            assertTrue(seq.route.any { it in first && it !in next })
            assertTrue(seq.route.any { it in next && it !in first })
            assertTrue(seq.route.all { it.string in start..end })
        }
    }
    @Test fun allPracticeTypesAndAdjacentConnectionsHaveBoundedRoutes() {
        for(key in Tonality.entries) for(pattern in 0..4) for(kind in ScaleExercise.entries) {
            if(kind==ScaleExercise.CONNECTION && pattern==4) continue
            val o=ScaleOptions(pattern=pattern,key=key,exercise=kind,octave=false)
            val s=Routes.scale(o).sequence!!
            val valid=Patterns.all[pattern].positions + if(kind==ScaleExercise.CONNECTION) Patterns.all[pattern+1].positions else emptyList()
            assertTrue(s.route.all { it in valid })
            assertEquals(0,s.length%48)
            if(kind==ScaleExercise.ROOTS) assertTrue(s.route.all(key::isRoot))
            if(kind==ScaleExercise.CONNECTION) {
                assertTrue(s.route.any { it in Patterns.all[pattern].positions && it !in Patterns.all[pattern+1].positions })
                assertTrue("$key / $pattern : ${s.route}",s.route.any { it in Patterns.all[pattern+1].positions && it !in Patterns.all[pattern].positions })
            }
        }
        for(start in 1..6) for(end in start..6) {
            val s=Routes.scale(ScaleOptions(firstString=start,lastString=end,octave=false)).sequence!!
            assertTrue(s.route.all { it.string in start..end })
        }
        assertNull(Routes.scale(ScaleOptions(firstString=1,lastString=1)).sequence)
    }
    @Test fun thirdsAndThreeNoteSequencesAreOrderedMusicalPatterns() {
        val thirds=Routes.scale(ScaleOptions(exercise=ScaleExercise.THIRDS)).sequence!!
        assertEquals(listOf(48,52,50,53,52,55,53,57,55,59,57,60),thirds.events.flatMap { it.positions }.map { it.midi })
        val minorThirds=Routes.scale(ScaleOptions(key=Tonality.A_MINOR,exercise=ScaleExercise.THIRDS)).sequence!!
        assertEquals(listOf(45,48,47,50,48,52,50,53,52,55,53,57),minorThirds.events.flatMap { it.positions }.map { it.midi })
        val three=Routes.scale(ScaleOptions(exercise=ScaleExercise.THREE)).sequence!!
        assertEquals(listOf(48,50,52,50,52,53,52,53,55),three.events.take(9).flatMap { it.positions }.map { it.midi })
        assertTrue(three.events.last().positions.isEmpty())
        val changed=Routes.scale(ScaleOptions(exercise=ScaleExercise.CHANGE_START,startDegree=3)).sequence!!
        assertEquals(52,changed.events.first().positions.first().midi)
        assertEquals(52,changed.events.last { it.positions.isNotEmpty() }.positions.first().midi)
    }
    @Test fun chordEventsDistinguishHoldStrumAndArpeggio() {
        val c=Chords.named("C");val a=Chords.named("Am")
        val hold=chordSequence(c,a,ChordExercise.CHANGE,4)
        assertEquals(listOf(0,48),hold.events.map { it.tick })
        assertEquals(listOf(48,48),hold.events.map { it.duration })
        val down=chordSequence(c,a,ChordExercise.DOWN,4)
        assertEquals(8,down.events.size)
        assertTrue(down.events.all { it.strum })
        assertEquals(listOf(5,4,3,2,1),down.events.first().positions.map { it.string })
        val arp=chordSequence(Chords.named("D"),a,ChordExercise.ARPEGGIO,4)
        assertEquals(listOf(4,3,2,1,5,3,2,1),arp.events.flatMap { it.positions }.map { it.string })
    }
    @Test fun absoluteFramesNeverAccumulateLoopRoundingErrorAndResumeKeepsRound() {
        val s=Session(Routes.scale(ScaleOptions()).sequence!!,137,true,4)
        assertEquals(816L,s.totalTicks)
        assertEquals(48,s.preTicks)
        assertEquals(-1,s.loopAt(47.9))
        assertEquals(0,s.loopAt(48.0))
        assertEquals(0,s.loopAt(239.9))
        assertEquals(1,s.loopAt(240.0))
        assertEquals(0.0,s.resumeTick(27.0),0.0)
        assertEquals(288.0,s.resumeTick(311.0),0.0)
        for(loop in 0..1000) {
            val ticks=48.0+loop*192
            assertEquals(round(ticks*44100*60/(137*12)).toLong(),s.frame(ticks))
        }
    }
    private fun sampler() = Sampler(SoundBank.NYLON) { root -> File("src/main/assets/guitar-nylon/$root.pcm").readBytes() }
    private fun energy(p: ShortArray) = p.sumOf { it.toDouble()*it }
    @Test fun realPcmOnsetsRestsMuteAndLoopSeam() {
        val seq=Sequence(listOf(Event(0,6,listOf(Position(5,3))),Event(6,6,emptyList()),Event(12,12,listOf(Position(4,0))),Event(24,24,emptyList())),48,"test")
        val session=Session(seq,137,false,2)
        val pcm=Pcm(session,sampler())
        assertTrue(pcm.block(100,1024,true,false).count { abs(it.toInt()) > 64 } > 100)
        assertEquals(0.0,energy(pcm.block(session.frame(7.0),1024,true,false)),0.0)
        assertEquals(0.0,energy(pcm.block(session.frame(25.0),1024,true,false)),0.0)
        assertEquals(0.0,energy(pcm.block(0,4096,false,false)),0.0)
        assertTrue(energy(pcm.block(100,1024,false,true))>1e8)
        val boundary=session.frame(48.0)
        val around=pcm.block(boundary-64,256,true,false)
        assertTrue(around.take(64).all { it==0.toShort() })
        assertTrue(around.drop(64).any { it!=0.toShort() })
        // Rendering is independent of buffer chunk boundaries.
        val all=pcm.block(0,90000,true,true)
        val chunks=(0 until 90000 step 257).flatMap { start -> pcm.block(start.toLong(),minOf(257,90000-start),true,true).toList() }.toShortArray()
        assertArrayEquals(all,chunks)
    }
    @Test fun realPcmStrumActuallySeparatesStringAttacks() {
        val chord=Chords.named("C")
        val sampler=sampler()
        val together=Pcm(Session(Sequence(listOf(Event(0,12,chord.positions)),48,""),60,false,1),sampler)
        val strum=Pcm(Session(Sequence(listOf(Event(0,12,chord.positions,strum=true)),48,""),60,false,1),sampler)
        assertFalse(together.block(0,6000,true,false).contentEquals(strum.block(0,6000,true,false)))
        val single=Pcm(Session(Sequence(listOf(Event(0,12,listOf(chord.positions.first()))),48,""),60,false,1),sampler)
        val s=strum.block(0,400,true,false);val one=single.block(0,400,true,false)
        for(i in s.indices) assertEquals(one[i]/sqrt(5.0),s[i].toDouble(),1.0)
    }
}
