package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import com.a3322505a.guitarlearning.audio.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class SongKernelTest {
    private val contract=File("../contracts/song-package/v1")
    private fun reader()=SongReader(SongSchema { File(contract,it).readBytes() })
    private fun fixture(name: String)=reader().read(File(contract,"fixtures/"+name+".gpractice").readBytes())
    private fun sampler()=Sampler(SoundBank.NYLON) { File("src/main/assets/guitar-nylon/"+it+".pcm").readBytes() }
    @Test fun originalSharedFixturesRetainPolyphonyTupletsAndTechniques() {
        listOf("simple","complex","nested","techniques").forEach { name ->
            val song=fixture(name)
            assertEquals(1,song.identity.revision)
            val plan=PracticeCompiler.compile(song.score,song.practices.single())
            assertTrue(plan.tabProjection().all { bar -> bar.glyphs.all { it.start>=bar.visit.start && it.end<=bar.visit.start+bar.visit.duration } })
            val pcm=Pcm(Session(Sequence(emptyList(),ceil(plan.length.value()*12).toInt(),"test",runtimePlan=plan),60,false,1),sampler())
            assertTrue(pcm.block(0,4096,true,false).any { it!=0.toShort() })
        }
        val complex=fixture("complex")
        val plan=PracticeCompiler.compile(complex.score,complex.practices.single())
        assertEquals(listOf("triple","compound","irregular","five","triple","compound","irregular","five"),plan.visits.map { it.measure.id })
        assertEquals(Q(29),plan.length)
        assertEquals(2,complex.score.voices.size)
        assertEquals(Q(2,35),fixture("nested").score.voices.single().events.first().duration)
        val legacy=Sequence(emptyList(),348,"import",runtimePlan=plan)
        val session=Session(legacy,60,false,1)
        assertEquals(3*ln(2.0),session.clock.seconds(3.0),1e-10)
        assertEquals(2.0,session.clock.seconds(6.0)-session.clock.seconds(3.0),1e-10)
        assertEquals(2,session.cursor(session.tick(session.clock.frame(14.6)) ).let { plan.visits[it.bar].occurrence })
    }
    @Test fun producerAndAppUseExactlyTheSamePositiveAndNegativeCorpus() {
        val cases=StrictJson.parse(File(contract,"fixtures/cases.json").readBytes()).arrayValue()
        cases.forEach { value ->
            val c=value.objectValue()
            val result=runCatching { reader().read(File(contract,"fixtures/"+c.s("file")).readBytes()) }
            assertEquals(c.s("file"),c.b("valid"),result.isSuccess)
            if(!c.b("valid")) assertEquals(c.s("file"),c.s("code"),(result.exceptionOrNull() as MusicError).diagnostic.code)
        }
        val advertised=StrictJson.parse(File(contract,"capabilities.json").readBytes()).objectValue().strings("capabilities").toSet()
        assertEquals(SongCapabilities.supported,advertised)
    }
    @Test fun exactQAndAnalyticTempoNeverQuantizeTupletsOrAccumulateLoopDrift() {
        assertEquals(Q(1,7),Q(2,14))
        assertEquals(Q.ONE,(0..6).fold(Q.ZERO) { a,_ -> a+Q(1,7) })
        assertTrue(runCatching { Q(Long.MIN_VALUE) }.isFailure)
        assertTrue(runCatching { Q(Q.LIMIT)*Q(Q.LIMIT) }.isFailure)
        val ramp=TempoMap(4.0,listOf(Tempo(Q.ZERO,Q.ONE,60.0,Q(4),120.0)))
        assertEquals(4*ln(2.0),ramp.seconds(4.0),1e-12)
        for(i in 0..400) { val q=i/100.0;val frame=ramp.frame(q);assertTrue(abs(ramp.frame(ramp.atFrame(frame))-frame)<=1) }
        val compound=TempoMap(3.0,listOf(Tempo(Q.ZERO,Q(3,2),60.0)))
        assertEquals(88200L,compound.frame(3.0))
        val song=fixture("complex");val plan=PracticeCompiler.compile(song.score,song.practices.single())
        val session=Session(Sequence(emptyList(),348,"clock",runtimePlan=plan),60,true,0)
        for(loop in listOf(0,1,7,10000)) {
            val tick=(session.preDuration+session.rangeDuration*Q(loop.toLong())).value()*12
            assertEquals(round((session.preSeconds+loop*session.loopSeconds)*44100).toLong(),session.frame(tick))
        }
    }
    @Test fun boundedFormSupportsVoltaNestedAndExplicitDaCapoFine() {
        val source=fixture("simple").score
        val measures=(0..3).map { source.measures[0].copy(id="m"+it,label=it.toString()) }
        val score=source.copy(measures=measures,form=Form(
            repeats=listOf(Repeat("repeat","m0","m1",2)),
            endings=listOf(Ending("repeat","m1","m1",listOf(1)),Ending("repeat","m2","m2",listOf(2)))))
        assertEquals(listOf(0,1,0,2,3),FormCompiler.compile(score).map { it.measureIndex })
        val nested=score.copy(form=Form(repeats=listOf(Repeat("outer","m0","m3",2),Repeat("inner","m1","m2",2))))
        assertEquals(listOf(0,1,2,1,2,3,0,1,2,1,2,3),FormCompiler.compile(nested).map { it.measureIndex })
        val dc=score.copy(form=Form(markers=listOf(Marker("fine","m1","fine")),jumps=listOf(Jump("dc","m3","m0","dc"))))
        assertEquals(listOf(0,1,2,3,0,1),FormCompiler.compile(dc).map { it.measureIndex })
        val ds=score.copy(form=Form(markers=listOf(Marker("segno","m1","segno"),Marker("coda","m3","coda")),
            jumps=listOf(Jump("ds","m2","segno","ds"),Jump("tocoda","m1","coda","to-coda"))))
        assertEquals(listOf(0,1,2,1,3),FormCompiler.compile(ds).map { it.measureIndex })
        assertTrue(runCatching { FormCompiler.compile(score.copy(form=Form(repeats=listOf(Repeat("r","missing","m1",2))))) }.isFailure)
    }
    @Test fun legacyAdapterKeepsEveryOriginalPositionTimingGestureAndCatalogue() {
        Phrases.all.forEach { phrase ->
            val seq=phrase.sequence;val plan=seq.performance
            assertEquals(Q(seq.length.toLong(),seq.ticksPerQuarter.toLong()),plan.length)
            assertEquals(seq.sections.map { it.chord },plan.harmonies.map { it.symbol.name })
            seq.sections.forEach { section ->
                val grip=plan.score.voicings.single { it.name==section.chord }
                assertEquals(com.a3322505a.guitarlearning.music.Chords.named(section.chord).frets.reversed().map { it ?: -1 },grip.frets)
            }
            plan.visits.forEachIndexed { i,v ->
                assertEquals("legacy-source-"+seq.barNumber(i),v.sourceMeasureId)
                assertEquals((0..i).count { seq.barNumber(it)==seq.barNumber(i) },v.occurrence)
            }
            seq.events.forEach { e ->
                val mapped=plan.events.single { it.event.id=="e-"+e.tick }
                assertEquals(Q(e.tick.toLong(),seq.ticksPerQuarter.toLong()),mapped.start)
                assertEquals(Q(e.duration.toLong(),seq.ticksPerQuarter.toLong()),mapped.duration)
                e.positions.forEach { p ->
                    val n=plan.notes.single { it.note.id=="n-"+e.tick+"-"+p.string }
                    assertEquals(p,n.position);assertEquals(seq.midi(p),n.note.midi)
                    assertEquals(e.strength(p.string).toDouble(),n.articulation.velocity!!,0.0)
                    val expected=Q(e.tick.toLong(),seq.ticksPerQuarter.toLong())+
                        Q(seq.soundDuration(e,p).toLong(),seq.ticksPerQuarter.toLong())*Q(1,if(e.stringExpression[p.string]?.short==true) 2 else 1)
                    assertEquals(phrase.id+" "+n.id,expected,n.soundEnd)
                }
            }
            assertEquals(seq.links.size,plan.relations.size)
        }
        File("../work/music-package/legacy-inventory.csv").apply {
            parentFile!!.mkdirs()
            writeText("id,title,events,notes,bars,visible\n"+Phrases.all.joinToString("\n") {
                it.id+","+it.title.replace(","," ")+","+it.sequence.events.size+","+it.sequence.events.sumOf { e -> e.positions.size }+
                    ","+it.sequence.barCount+","+(it in Phrases.catalogue)
            })
        }
    }
    @Test fun multivoiceRestsDoNotStopBassAndStreamingHasBoundedRandomAccess() {
        val song=fixture("complex");val plan=PracticeCompiler.compile(song.score,song.practices.single())
        val session=Session(Sequence(emptyList(),348,"poly",runtimePlan=plan),60,false,1)
        assertEquals(listOf(6),session.cursor(2.5*12).positions.map { it.string })
        val pcm=Pcm(session,sampler())
        val total=160000
        val whole=pcm.block(0,total,true,false);val chunks=ShortArray(total)
        for(start in 0 until total step 997) pcm.block(start.toLong(),minOf(997,total-start),true,false).copyInto(chunks,start)
        assertArrayEquals(whole,chunks)
        assertTrue(pcm.block(session.totalFrames!!,4096,true,true).all { it==0.toShort() })
        val counters=FrameCounter()
        assertEquals(0xfffffff0L,counters.extend(-16))
        assertEquals(0x100000005L,counters.extend(5))
    }
    @Test fun strictJsonRejectsDuplicateUnknownMusicalFieldsAndNoncanonicalQ() {
        listOf("{\"x\":1,\"x\":2}","[NaN]","[1e9999]","[\"\\ud800\"]").forEach {
            assertTrue(runCatching { StrictJson.parse(it.toByteArray()) }.isFailure)
        }
        val schema=SongSchema { File(contract,it).readBytes() }
        val score=File(contract,"fixtures/simple/score.json").readText()
        assertTrue(runCatching { schema.validate("score",StrictJson.parse(score.replace("\"documentId\":", "\"unknown\": 1, \"documentId\":").toByteArray())) }.isFailure)
        assertTrue(runCatching { schema.validate("score",StrictJson.parse(score.replace("\"d\": 1","\"d\": 2").toByteArray())) }.isFailure)
    }
}
