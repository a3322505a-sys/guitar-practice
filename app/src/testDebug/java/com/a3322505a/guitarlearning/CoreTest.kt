package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun scaleTripletsHaveTwelveEquallySpacedOnsetsInAFullBarAt60Bpm() {
        val seq=Routes.scale(ScaleOptions(exercise=ScaleExercise.THIRDS,notesPerBeat=3)).sequence!!
        seq.validateExpression()
        assertEquals(48,seq.length)
        assertEquals(12,seq.events.size)
        assertTrue(seq.events.none { it.rest })
        assertEquals((0..11).map { it*4 },seq.events.map { it.tick })
        assertEquals(listOf(Tuplet(0,4),Tuplet(12,4),Tuplet(24,4),Tuplet(36,4)),seq.tuplets)
        val notes=seq.performance.notes
        assertEquals((0..11).map { com.a3322505a.guitarlearning.music.score.Q(it.toLong(),3) }, notes.map { it.start })
        assertEquals(com.a3322505a.guitarlearning.music.score.Q(4),seq.performance.length)
    }
    @Test fun partialTripletTailsKeepTheExistingWholeBarBoundary() {
        // Two roots leave two sounding members; the full sol round trip leaves one.
        val cases=listOf(ScaleOptions(exercise=ScaleExercise.ROOTS),ScaleOptions(pattern=1,octave=false))
        val remainders=mutableSetOf<Int>()
        cases.forEach { options ->
            val old=Routes.scale(options).sequence!!
            val seq=Routes.scale(options.copy(notesPerBeat=3)).sequence!!
            val pitches=old.events.flatMap { it.positions }
            assertEquals(pitches,seq.events.flatMap { it.positions })
            val n=pitches.size;remainders+=n%3
            val expectedLength=((n*4+47)/48)*48
            assertEquals(expectedLength,seq.length)
            val group=seq.tuplets.last()
            val members=seq.events.filter { it.tick in group.tick until group.end }
            assertEquals(3,members.size)
            assertEquals(3-n%3,members.count { it.rest })
            assertTrue(members.all { it.duration==4 })
            val tail=seq.events.filter { it.tick>=group.end }
            assertEquals(seq.length-group.end,tail.sumOf { it.duration })
            assertTrue(tail.all { it.rest && it.duration%12==0 })
            assertEquals(seq.length,seq.events.sumOf { it.duration })
            assertEquals(seq.length.toDouble()/12,seq.performance.length.value(),0.0)
            seq.validateExpression()
        }
        assertEquals(setOf(1,2),remainders)
    }
    @Test fun everyScaleRhythmKeepsTheSamePitchRouteAndLegalNotation() {
        for(key in Tonality.entries) for(pattern in 0..4) for(exercise in ScaleExercise.entries) for(octave in listOf(false,true)) {
            val options=ScaleOptions(pattern=pattern,key=key,exercise=exercise,octave=octave)
            val baseline=Routes.scale(options).sequence ?: continue
            for(density in 1..3) {
                val seq=Routes.scale(options.copy(notesPerBeat=density)).sequence!!
                assertEquals(baseline.events.flatMap { it.positions },seq.events.flatMap { it.positions })
                seq.validateExpression()
                assertEquals(seq.length,seq.events.sumOf { it.duration })
                assertTrue(seq.events.filterNot { it.rest }.all { it.duration==12/density })
            }
        }
    }
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
    @Test fun fiveClosedBoxesAndTheSeparateOpenPositionCoverOnlyTheirNativeScalePitches() {
        val c = Patterns.forKey(Tonality.C_MAJOR)
        assertEquals(6, c.size)
        assertEquals(setOf("C", "A", "G", "E", "D"), c.filter { it.caged.isNotBlank() }.map { it.caged }.toSet())
        assertTrue(c.first().positions.containsAll(listOf(Position(5, 3), Position(2, 1))))
        Tonality.entries.forEach { key ->
            val patterns = Patterns.forKey(key)
            val closed = patterns.filter { it.solfege != "开放把位" }
            assertEquals(key.title, 5, closed.size)
            assertTrue(closed.all { it.positions.none { p -> p.fret == 0 } })
            patterns.forEach { pattern ->
                assertEquals(pattern.positions.toSet(), pattern.fingers.keys)
                assertTrue(pattern.positions.all { it.midi % 12 in key.pitchClasses })
                assertTrue(pattern.positions.all { it.string in 1..6 && it.fret in 0..24 })
            }
        }
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
        val eighth=Routes.scale(ScaleOptions(notesPerBeat=2)).sequence!!
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
            val seq=Routes.scale(ScaleOptions(pattern=pattern,key=key,octave=octave,reverse=reverse,notesPerBeat=if(eighth) 2 else 1)).sequence!!
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
            val first=Patterns.forKey(key)[pattern].positions
            val next=Patterns.forKey(key)[pattern+1].positions
            assertTrue(seq.route.any { it in first && it !in next })
            assertTrue(seq.route.any { it in next && it !in first })
            assertTrue(seq.route.all { it.string in start..end })
        }
    }
    @Test fun allPracticeTypesAndAdjacentConnectionsHaveBoundedRoutes() {
        for(key in Tonality.entries) for(pattern in 0..4) for(kind in ScaleExercise.entries) {
            if(kind==ScaleExercise.CONNECTION && pattern==4) continue
            val o=ScaleOptions(pattern=pattern,key=key,exercise=kind,octave=false)
            val result = Routes.scale(o)
            val s = result.sequence
            if (s == null) { assertTrue("$key / $pattern / $kind", result.reason.isNotBlank()); continue }
            val valid=Patterns.forKey(key)[pattern].positions + if(kind==ScaleExercise.CONNECTION) Patterns.forKey(key)[pattern+1].positions else emptyList()
            assertTrue(s.route.all { it in valid })
            assertEquals(0,s.length%48)
            if(kind==ScaleExercise.ROOTS) assertTrue(s.route.all(key::isRoot))
            if(kind==ScaleExercise.CONNECTION) {
                assertTrue(s.route.any { it in Patterns.forKey(key)[pattern].positions && it !in Patterns.forKey(key)[pattern+1].positions })
                assertTrue("$key / $pattern : ${s.route}",s.route.any { it in Patterns.forKey(key)[pattern+1].positions && it !in Patterns.forKey(key)[pattern].positions })
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
}
