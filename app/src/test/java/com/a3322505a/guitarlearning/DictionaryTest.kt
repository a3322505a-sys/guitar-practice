package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test

class DictionaryTest {
    @Test fun everyRootAndQualityHasAnAccuratePlayableGrip() {
        assertEquals(12,ChordDictionary.definitions.map { it.root.pitchClass }.distinct().size)
        assertEquals(ChordDictionary.chordRoots.size*ChordQuality.entries.size,ChordDictionary.definitions.size)
        ChordDictionary.definitions.forEach { definition -> assertTrue(definition.name,ChordDictionary.find(definition.name).isNotEmpty()) }
        ChordDictionary.voicings.forEach { v ->
            assertTrue(v.id,v.id.matches(Regex("[A-Za-z0-9._-]+")))
            val c=v.chord
            assertNull(v.id,c.unavailableReason)
            val expected=v.definition.pitchClasses+listOfNotNull(v.definition.bass?.pitchClass)
            assertEquals(v.id,expected,c.positions.map { c.midi(it)%12 }.toSet())
            assertEquals(v.id,6,v.soundingNotes.size)
            c.positions.forEach { p -> if(p.fret>0) assertTrue(v.id,c.fingers[6-p.string] in 1..5) }
            c.barres.forEach { b ->
                assertTrue(v.id,(b.toString..b.fromString).all { s -> c.frets[6-s]?.let { it>=b.fret && (it!=b.fret || c.fingers[6-s]==b.finger) }==true })
            }
            (1..4).forEach { finger ->
                val ps=c.positions.filter { it.fret>0 && c.fingers[6-it.string]==finger }
                if(ps.size>1) assertTrue(v.id,c.barres.any { b -> b.finger==finger && ps.all { it.fret==b.fret && it.string in b.toString..b.fromString } })
            }
        }
        java.io.File("../docs/validation/v107-dictionary.json").writeText("""{"pitchClasses":12,"chordDefinitions":${ChordDictionary.definitions.size},"physicalVoicings":${ChordDictionary.voicings.size},"scaleDefinitions":${ScaleDictionary.definitions.size},"closedScalePositions":300,"spellingAndPitchAndBarresChecked":true}"""+"\n")
    }
    @Test fun namedCorrectionsKeepBassExtensionsAndOpenStringsHonest() {
        val cm7=ChordDictionary.find("C#m7")
        assertTrue(cm7.any { it.chord.frets==listOf(null,4,6,6,0,0) })
        assertFalse(cm7.any { it.chord.frets==listOf(null,4,6,6,5,0) })
        assertTrue(ChordDictionary.find("F#m7(add11)").any { it.chord.frets==listOf(2,4,4,2,0,0) })
        assertTrue(ChordDictionary.find("Bm/F#").any { it.bassMidi==54 })
        assertTrue(ChordDictionary.find("Bb/F").any { it.bassMidi==53 })
        val f=ChordDictionary.find("Fmaj7")
        assertTrue(f.any { it.chord.frets==listOf(null,null,3,2,1,0) && it.bassMidi==53 })
        assertTrue(f.any { it.chord.frets==listOf(1,3,3,2,1,0) && it.bassMidi==41 && it.chord.fingers[0]==5 })
        assertEquals(setOf(3,6,9,0),ChordDefinition.parse("D#dim7")!!.pitchClasses)
        assertEquals(setOf(11,2,5,9),ChordDefinition.parse("Bm7b5")!!.pitchClasses)
        assertTrue(ChordDictionary.find("Am5").isEmpty())
        listOf("C/G","C/E","G/B","D/F#","Am/G","E/G#","F/G").forEach { assertTrue(it,ChordDictionary.find(it).isNotEmpty()) }
        listOf("F","Fm","B","Bm","Bb","Bbm","F#","F#m","C#m","G#m").forEach { name ->
            assertTrue(name,ChordDictionary.find(name).any { v -> v.chord.barres.all { it.fromString-it.toString<4 } })
        }
    }
    @Test fun allSixtyScalesKeepSpellingRegistersBoxesAndDegreesSeparate() {
        assertEquals(60,ScaleDictionary.definitions.size)
        assertEquals(60,Tonality.entries.size)
        ScaleDictionary.definitions.forEach { key ->
            assertEquals(key.pitchClasses,key.spelling.map { it.pitchClass })
            val positions=ScaleDictionary.positions(key)
            assertEquals(key.pitchClasses.toSet(),positions.map { it.midi%12 }.toSet())
            val patterns=ScaleDictionary.patterns(key)
            val closed=patterns.filterNot { it.solfege=="开放把位" }
            assertEquals(key.toString(),5,closed.size)
            closed.forEach { p ->
                assertTrue(key.toString()+p.title,p.positions.all { it.fret>0 && it.midi%12 in key.pitchClasses })
                assertTrue(p.title,p.positions.all { p.fingers[it] in 1..4 })
                val midi=p.positions.map { it.midi }.toSet()
                assertEquals(key.toString()+p.title,(midi.min()..midi.max()).filter { it%12 in key.pitchClasses }.toSet(),midi)
            }
        }
        assertNotEquals(KeyContext(SpelledPitch.parse("C"),ScaleDefinition.MAJOR).degree(60),KeyContext(SpelledPitch.parse("A"),ScaleDefinition.NATURAL_MINOR).degree(60))
    }
    @Test fun everyScaleCanStartAnOctaveExerciseInEveryPosition() {
        Tonality.entries.forEach { key -> Patterns.forKey(key).indices.forEach { index ->
            val result=Routes.scale(ScaleOptions(key=key,pattern=index))
            assertNotNull(key.title+" "+index+" "+result.reason,result.sequence)
        } }
    }
}
