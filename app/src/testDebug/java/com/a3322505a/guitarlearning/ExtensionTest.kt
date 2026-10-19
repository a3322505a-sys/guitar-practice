package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.practice.LegacyPhraseFixtures as Phrases

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.audio.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExtensionTest {
    @Test fun allEightConnectionsReturnOnlyAfterCrossingAndKeepActualPitches() {
        val rows=mutableListOf("key,pair,direction,event,string,fret,midi,finger,shift")
        for(key in Tonality.entries) for(i in 0..3) {
            val options=ScaleOptions(pattern=i,key=key,exercise=ScaleExercise.CONNECTION,octave=false)
            val plan=connectionPlan(options)!!
            val sequence=Routes.scale(options).sequence!!
            val route=sequence.events.flatMap { it.positions }
            val a=Patterns.forKey(key)[i]; val b=Patterns.forKey(key)[i+1]
            assertEquals(plan.route,route)
            assertEquals(route.first(),route.last())
            assertTrue(plan.outward.any { it in b.positions && it !in a.positions })
            assertTrue(plan.returning.last() in a.positions)
            assertTrue("Return contains an A-only position",plan.returning.any { it in a.positions && it !in b.positions })
            assertTrue(plan.outward.zipWithNext().all { (p,q)->p.midi<q.midi })
            assertTrue((listOf(plan.outward.last())+plan.returning).zipWithNext().all { (p,q)->p.midi>q.midi })
            assertTrue(route.zipWithNext().all { (p,q)->kotlin.math.abs(p.string-q.string)<=1 })
            assertEquals(1,route.count { it.midi==plan.outward.last().midi })
            val arrival=plan.outward.indexOfFirst { it in b.positions && it !in a.positions }*12
            assertTrue("First arrival in B is not the end",sequence.length>arrival+12)
            assertEquals(0,sequence.length%BAR)
            assertEquals(route,Routes.scale(options.copy(notesPerBeat=2)).sequence!!.events.flatMap { it.positions })
            assertEquals(route.reversed(),Routes.scale(options.copy(reverse=true)).sequence!!.events.flatMap { it.positions })
            route.forEachIndexed { at,p ->
                val outward=at<plan.outward.size
                val shape=if(outward) { if(p.midi<=plan.pivot.midi) a else b } else { if(p.midi>=plan.pivot.midi) b else a }
                rows += "${key.name},${a.solfege}-${b.solfege},${if(outward) "out" else "return"},$at,${p.string},${p.fret},${p.midi},${suggestedFinger(p,shape)},${if(p==plan.pivot) "shared-root" else ""}"
            }
        }
        File("../output/connections-v60.csv").apply { parentFile!!.mkdirs();writeText(rows.joinToString("\n")) }
    }
    @Test fun phrasesValidateAndErrorsLocateContentAndEvent() {
        validatePhrases(Phrases.all)
        val phrase=Phrases.singleNote
        fun rejects(value: List<Phrase>,text: String) {
            val error=runCatching { validatePhrases(value) }.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error!!.message.orEmpty(),error.message!!.contains(text))
        }
        rejects(listOf(phrase,phrase),"M01: duplicate")
        rejects(listOf(phrase.copy(sequence=phrase.sequence.copy(events=phrase.sequence.events.mapIndexed { i,e -> if(i==2) e.copy(positions=listOf(Position(7,2))) else e }))),"M01: event[2]")
        rejects(listOf(phrase.copy(sequence=phrase.sequence.copy(sections=listOf(Section(0,"missing"))))),"M01: section[0]")
        rejects(listOf(phrase.copy(sequence=Sequence(listOf(Event(0,2,listOf(Position(1,0))),Event(2,46,listOf(Position(1,0)))),BAR,"bad rhythm"))),"M01: event[0]")
        rejects(listOf(phrase.copy(conditions="变调夹 2 品")),"M01: unsupported")
        assertTrue(phrase.sequence.sections.isEmpty())
        assertEquals(listOf(48,50,52,48),phrase.sequence.events.flatMap { it.positions }.map { it.midi })
        assertEquals(4,phrase.sequence.tabBars().single().segments.size)
    }
    @Test fun singleNotesProduceSoundAndTerminateWithoutHarmony() {
        val sampler=Sampler(SoundBank.NYLON) { root -> File("../assets/soundfont-source/guitar-nylon/$root.pcm").readBytes() }
        val sequence=Phrases.singleNote.sequence
        val session=Session(sequence,60,false,1)
        val pcm=Pcm(session,sampler)
        assertTrue(pcm.block(0,4096,true,false).any { it!=0.toShort() })
        assertTrue(pcm.block(session.frame(sequence.length.toDouble()),4096,true,false).all { it==0.toShort() })
    }
}
