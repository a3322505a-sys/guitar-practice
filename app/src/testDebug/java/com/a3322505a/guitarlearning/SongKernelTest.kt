@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
package com.a3322505a.guitarlearning
import alphaTab.midi.*
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class SongKernelTest {
    private val contract=File("../contracts/song-package/v1")
    private fun reader()=SongReader(SongSchema { File(contract,it).readBytes() })
    private fun fixture(name: String)=NativeContent.fixture(name)
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
    @Test fun strictJsonRejectsDuplicateUnknownMusicalFieldsAndNoncanonicalQ() {
        listOf("{\"x\":1,\"x\":2}","[NaN]","[1e9999]","[\"\\ud800\"]").forEach {
            assertTrue(runCatching { StrictJson.parse(it.toByteArray()) }.isFailure)
        }
        val schema=SongSchema { File(contract,it).readBytes() }
        val score=File(contract,"fixtures/simple/score.json").readText()
        assertTrue(runCatching { schema.validate("score",StrictJson.parse(score.replace("\"documentId\":", "\"unknown\": 1, \"documentId\":").toByteArray())) }.isFailure)
        assertTrue(runCatching { schema.validate("score",StrictJson.parse(score.replace("\"d\": 1","\"d\": 2").toByteArray())) }.isFailure)
    }
    @Test fun fixturesKeepNativeEventPitchTimingPolyphonyAndMeter() {
        for(name in listOf("simple","complex","nested","techniques","harmonics","pickup","ties","shared","multi-track")) {
            val plan=NativeContent.plan(name)
            val score=AlphaScoreAdapter.fromPlan(plan)
            val (midi,projection)=EngineProjection.generate(score)
            assertEquals(name,plan.visits.size,projection.visits.size)
            assertEquals(name,plan.length.value()*960,projection.visits.last().end,1e-6)
            assertTrue(name,midi.events.any { it is NoteOnEvent })
            assertTrue(name,NativeContent.pcm(score).any { kotlin.math.abs(it)>.0001f })
        }
    }

}
