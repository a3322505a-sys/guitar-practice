package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.ui.ChordGeometry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ShayouChordTest {
    @Test fun everyPitchedGpGripIsAvailableWithoutAddingUnplayedStrings() {
        val rows=javaClass.getResourceAsStream("/shayou-gp.csv")!!.bufferedReader().use { it.readLines() }.drop(1).map { it.split(',') }
        val expected=rows.groupBy { Triple(it[0],it[1],it[2]) }.values.map { beat ->
            beat.filter { it[4]!="0" && it[7]=="0" }.map { Position(it[4].toInt(),it[5].toInt()) }.toSet()
        }.filter { it.size>1 }.toSet()
        assertEquals(16,expected.size)
        assertEquals(expected,ShayouChords.all.map { it.positions.toSet() }.toSet())
        assertEquals(11,ShayouChords.all.count { it.group=="斜阳 · 伴奏指法" })
        assertEquals(5,ShayouChords.all.count { it.group=="斜阳 · 双音 riff" })
        assertEquals(28,Chords.all.size)
        assertEquals(28,Chords.all.map { it.name }.distinct().size)
        ShayouChords.all.forEach { chord ->
            assertSame(chord,Chords.named(chord.name))
            chord.frets.forEachIndexed { i,fret ->
                assertEquals(fret!=null && fret>0,chord.fingers[i]>0)
                assertTrue(chord.fingers[i] in 0..4)
            }
        }
    }

    @Test fun actualChordNamesRootsThirdsAndDimSeventhMatchCapoPitchClasses() {
        val expected=listOf(
            "Dm7(no5)" to setOf(0,2,5), "B♭sus2" to setOf(0,5,10), "Csus4" to setOf(0,5,7), "F5" to setOf(0,5),
            "A7(no5)" to setOf(1,7,9), "B♭5" to setOf(5,10), "C5" to setOf(0,7), "C♯dim7" to setOf(1,4,7,10),
            "F/A" to setOf(0,5,9), "Fm/A♭" to setOf(0,5,8), "Gm7" to setOf(2,5,7,10),
            "F5/C" to setOf(0,5), "E5/B" to setOf(4,11), "G5/D" to setOf(2,7), "A5/E" to setOf(4,9), "F5/C" to setOf(0,5))
        ShayouChords.all.zip(expected).forEach { (chord,want) ->
            assertEquals(want.first,chord.soundingName)
            assertEquals(want.second,chord.positions.map { chord.midi(it)%12 }.toSet())
            assertTrue(chord.positions.any(chord::isRoot))
            chord.positions.forEach { assertNotEquals("非和弦音",chord.role(it)) }
        }
        val dim=ShayouChords.all[7]
        assertEquals("减五音",dim.role(Position(4,2)))
        assertEquals("减七音",dim.role(Position(3,0)))
        assertEquals("B♭",dim.noteName(Position(3,0)))
        assertEquals("A♭",ShayouChords.all[9].noteName(Position(6,1)))
        assertEquals(9,ShayouChords.all[8].midi(ShayouChords.all[8].positions.first())%12)
        assertEquals(5,ShayouChords.all[8].root)
    }

    @Test fun chordLearningAndRootOnlyTabKeepTheSourceCapoAndPositions() {
        ShayouChords.all.forEach { chord ->
            val seq=chordSequence(chord,chord,ChordExercise.CHECK,4)
            assertEquals(3,seq.capoFret)
            assertEquals(chord.positions,seq.events.flatMap { it.positions })
            assertEquals(chord.positions.map(chord::midi),seq.events.flatMap { it.positions }.map(seq::midi))
            assertTrue(seq.tabBars().flatMap { it.segments }.all { it.chord==chord.name })
            assertTrue(chord.arpeggio.all { it in chord.positions })
        }
        assertEquals(0,chordSequence(Chords.named("C"),Chords.named("Am"),ChordExercise.CHANGE,4).capoFret)
        assertTrue(runCatching { chordSequence(Chords.named("C"),ShayouChords.all.first(),ChordExercise.CHANGE,4) }.isFailure)
    }

    @Test fun highPositionGeometryKeepsEveryPlayedNoteInsideTheFourFretGrid() {
        ShayouChords.all.forEach { chord ->
            val g=ChordGeometry(390f,300f,chord.baseFret)
            chord.positions.forEach { p ->
                val at=g.position(p)
                assertTrue(at.x in g.left..g.left+5*g.stringStep)
                assertTrue(at.y in 0f..300f)
                if(p.fret>0) assertTrue(at.y>g.top && at.y<g.top+4*g.fretStep)
                else assertEquals(g.openY,at.y,0f)
            }
        }
        assertEquals(listOf(10,9,12,14,7),ShayouChords.all.takeLast(5).map { it.baseFret })
    }

    @Test fun chordCheckPcmMatchesUncapoedEquivalentPitchWithoutMovingTheShownFrets() {
        val sampler=Sampler(SoundBank.NYLON) { File("src/main/assets/guitar-nylon/$it.pcm").readBytes() }
        listOf(ShayouChords.all.first(),ShayouChords.all[7],ShayouChords.all[14]).forEach { chord ->
            val relative=chordSequence(chord,chord,ChordExercise.CHECK,4)
            val absolute=relative.copy(capoFret=0,events=relative.events.map { e->e.copy(positions=e.positions.map { p->p.copy(fret=p.fret+3) }) })
            val a=Pcm(Session(relative,90,false,1),sampler)
            val b=Pcm(Session(absolute,90,false,1),sampler)
            assertArrayEquals(a.block(0,16000,true,false),b.block(0,16000,true,false))
        }
    }
}
