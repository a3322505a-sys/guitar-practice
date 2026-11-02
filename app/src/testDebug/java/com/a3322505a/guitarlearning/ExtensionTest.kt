package com.a3322505a.guitarlearning


import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExtensionTest {
    @Test fun allEightConnectionsReturnOnlyAfterCrossingAndKeepActualPitches() {
        val rows=mutableListOf("key,pair,direction,event,string,fret,midi,finger,shift")
        for(key in Tonality.entries) for(i in 0..3) {
            val options=ScaleOptions(pattern=i,key=key,exercise=ScaleExercise.CONNECTION,octave=false)
            val plan=connectionPlan(options)
            if (plan == null) { assertTrue(Routes.scale(options).reason.isNotBlank()); continue }
            val sequence=Routes.scale(options).sequence!!
            val route=sequence.events.flatMap { it.positions }
            val a=Patterns.forKey(key)[i]; val b=Patterns.forKey(key)[i+1]
            assertEquals(plan.route,route)
            assertEquals(route.first(),route.last())
            assertTrue(plan.outward.any { it in b.positions && it !in a.positions })
            assertTrue(plan.returning.last() in a.positions)
            if (route.first() !in b.positions) assertTrue("Return reaches its exclusive starting position", plan.returning.any { it in a.positions && it !in b.positions })
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
        val phrase = Phrase("fixture-single", "单音", "测试", "一小节", "独立谱例", 60, "测试", "交替拨弦", "标准调弦 · 无变调夹",
            Sequence(listOf(Position(5,3),Position(4,0),Position(4,2),Position(5,3)).mapIndexed { i, p -> Event(i*12,12,listOf(p)) },48,"独立谱例"))
        validatePhrases(listOf(phrase))
        fun rejects(value: List<Phrase>,text: String) {
            val error=runCatching { validatePhrases(value) }.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error!!.message.orEmpty(),error.message!!.contains(text))
        }
        rejects(listOf(phrase,phrase),"fixture-single: duplicate")
        rejects(listOf(phrase.copy(sequence=phrase.sequence.copy(events=phrase.sequence.events.mapIndexed { i,e -> if(i==2) e.copy(positions=listOf(Position(7,2))) else e }))),"fixture-single: event[2]")
        rejects(listOf(phrase.copy(sequence=phrase.sequence.copy(sections=listOf(Section(0,"missing"))))),"fixture-single: section[0]")
        rejects(listOf(phrase.copy(sequence=Sequence(listOf(Event(0,2,listOf(Position(1,0))),Event(2,46,listOf(Position(1,0)))),BAR,"bad rhythm"))),"fixture-single: event[0]")
        rejects(listOf(phrase.copy(conditions="变调夹 2 品")),"fixture-single: unsupported")
        assertTrue(phrase.sequence.sections.isEmpty())
        assertEquals(listOf(48,50,52,48),phrase.sequence.events.flatMap { it.positions }.map { it.midi })
        assertEquals(4,phrase.sequence.performance.events.size)
    }
}
