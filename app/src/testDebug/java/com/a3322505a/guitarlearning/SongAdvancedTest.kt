package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.practice.Phrases as Phrases

import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import com.a3322505a.guitarlearning.audio.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class SongAdvancedTest {
    private val contract=File("../contracts/song-package/v1")
    private fun fixture(name: String)=SongReader(SongSchema { File(contract,it).readBytes() }).read(File(contract,"fixtures/"+name+".gpractice").readBytes())
    private fun sequence(plan: PerformancePlan)=Sequence(emptyList(),ceil(plan.length.value()*12).toInt(),"test",runtimePlan=plan)
    private fun sine()=Sampler(SoundBank.NYLON) { root ->
        val data=ByteBuffer.allocate(22050*20).order(ByteOrder.LITTLE_ENDIAN)
        repeat(22050*10) { data.putShort((sin(2*PI*440*2.0.pow((root-69)/12.0)*it/22050)*12000).toInt().toShort()) }
        data.array()
    }
    private fun pitch(wave: ShortArray): Double {
        val crossings=(1 until wave.size).filter { wave[it-1]<=0 && wave[it]>0 }
        assertTrue(crossings.size>3)
        return (crossings.size-1)*44100.0/(crossings.last()-crossings.first())
    }
    @Test fun allExpandedFixturesAndConditionalTempoUseTheSameRuntime() {
        listOf("ties","harmonics","swing","strum","pickup","conditional","cross-tuplet","multi-track").forEach { name ->
            val song=fixture(name);val plan=PracticeCompiler.compile(song.score,song.practices.single())
            assertEquals(song.score.voices.flatMap { it.events }.sumOf { it.notes.size }*
                (if(name=="conditional") 2 else 1),plan.notes.size)
            assertEquals(plan.visits.size,plan.tabProjection().size)
        }
        val conditional=fixture("conditional")
        val plan=PracticeCompiler.compile(conditional.score,conditional.practices.single())
        assertEquals(12.0,plan.map().seconds(16.0),1e-12)
        val pickup=fixture("pickup");val session=Session(sequence(PracticeCompiler.compile(pickup.score,pickup.practices.single())),60,true,1)
        assertEquals(Q(31,4),session.preDuration)
        assertTrue(session.cursor(48.1).leadIn)
        assertEquals(3,session.cursor(93.01).beat)
        assertEquals(Q(4),session.rangeDuration)
    }
    @Test fun harmonicPlaybackUsesPartialRatioRatherThanTheTouchedFretOrRoundedLabel() {
        val song=fixture("harmonics");val plan=PracticeCompiler.compile(song.score,song.practices.single())
        val session=Session(sequence(plan),60,false,1)
        val pcm=Pcm(session,sine())
        assertEquals(440*2.0.pow((64-69)/12.0)*2,pitch(pcm.block(12000,4000,true,false)),1.0)
        assertEquals(440*2.0.pow((62-69)/12.0)*3,pitch(pcm.block(44100+12000,4000,true,false)),1.0)
        assertEquals(440*2.0.pow((60-69)/12.0)*4,pitch(pcm.block(88200+12000,4000,true,false)),1.0)
    }
    @Test fun tiedBendCarriesPitchAndPhaseWithoutAnotherAttack() {
        val song=fixture("ties");val plan=PracticeCompiler.compile(song.score,song.practices.single())
        val session=Session(sequence(plan),60,false,1)
        val pcm=Pcm(session,sine())
        val target=pitch(pcm.block(4*44100+6000,2000,true,false))
        // End bend +2 is inherited across the source tie; vibrato remains continuous.
        assertEquals(440*2.0.pow((66-69)/12.0),target,4.0)
        assertEquals(1,plan.notes.count { n -> plan.relations.none { it.to==n.id && it.kind=="tie" } })
    }
    @Test fun boundaryRejectCarryReattackAndTruncateAreDistinctAndTraceable() {
        val song=fixture("ties");val p=song.practices.single()
        val selected=PracticeSegment(Coordinate("tie-b"),Coordinate("tie-b",Q(4)))
        assertTrue(runCatching { PracticeCompiler.compile(song.score,p.copy(segments=listOf(selected))) }.isFailure)
        val affected=listOf("tie-origin","tie-target")
        val carry=PracticeCompiler.compile(song.score,p.copy(segments=listOf(selected.copy(entry="carryIn",affectedNoteIds=affected))))
        val attack=PracticeCompiler.compile(song.score,p.copy(segments=listOf(selected.copy(entry="reattack",affectedNoteIds=affected))))
        val cut=PracticeCompiler.compile(song.score,p.copy(segments=listOf(selected.copy(entry="truncate",affectedNoteIds=affected))))
        assertEquals(1,carry.relations.size)
        assertTrue(carry.notes.first().start<Q.ZERO)
        assertEquals(0,attack.relations.size)
        assertTrue(cut.notes.isEmpty())
        val carryPcm=Pcm(Session(sequence(carry),60,false,1),sine())
        assertTrue(carryPcm.block(1000,2000,true,false).any { it!=0.toShort() })
        assertTrue(Pcm(Session(sequence(cut),60,false,1),sine()).block(0,4096,true,false).all { it==0.toShort() })
        assertEquals("tie-b",carry.visits.first().measure.id)
    }
    @Test fun multitrackMainChoiceAndSoloDoNotConfuseTuningOrNoteIdentity() {
        val song=fixture("multi-track");val original=PracticeCompiler.compile(song.score,song.practices.single())
        val solo=original.copy(practice=original.practice.copy(mainTrackId="second-guitar",mixTrackIds=listOf("second-guitar")))
        val session=Session(sequence(solo),60,false,1)
        val pcm=Pcm(session,sine())
        assertEquals(65,session.activeNotes(.1).single().note.midi)
        assertEquals(440*2.0.pow((65-69)/12.0),pitch(pcm.block(4000,4000,true,false)),1.0)
        val strum=fixture("strum");val grip=PracticeCompiler.compile(strum.score,strum.practices.single())
        assertEquals("local-grip",grip.harmonies.single().voicing!!.id)
    }
    @Test fun sharedVoiceHeadsRetainVisualIdentityAndProduceOneAcousticAttack() {
        val song=fixture("shared");val plan=PracticeCompiler.compile(song.score,song.practices.single())
        val single=fixture("simple");val baseline=PracticeCompiler.compile(single.score,single.practices.single())
        assertEquals(8,plan.notes.size)
        val pcm=Pcm(Session(sequence(plan),60,false,1),sine())
        val original=Pcm(Session(sequence(baseline),60,false,1),sine())
        assertArrayEquals(original.block(0,180000,true,false),pcm.block(0,180000,true,false))
    }
    @Test fun independentlyAuditedLocalGpAndPdfPackagesEnterAndroidWithoutSongRegistration() {
        val files=listOf(File("../work/music-package/gp-audited-final.gpractice"),File("../work/music-package/pdf-audited-final.gpractice"))
        // Real sources and review packets remain local, outside Git/CI.
        assumeTrue(files.all { it.isFile })
        val generated=File("../output/v75/packages").listFiles()?.filter { it.extension=="gpractice" }.orEmpty()
        (files+generated).forEach { file ->
            val song=SongReader(SongSchema { File(contract,it).readBytes() }).read(file.readBytes())
            val plan=PracticeCompiler.compile(song.score,song.practices.single(),song.identity.digest)
            assertTrue(plan.notes.isNotEmpty() && plan.tabProjection().all { it.glyphs.isNotEmpty() })
            val session=Session(sequence(plan),60,false,1)
            val pcm=Pcm(session,sine())
            val first=plan.notes.first { !it.note.dead }
            val onset=session.clock.frame(first.start.value())
            if(onset>4096) assertTrue(file.name+" source opening rests",pcm.block(0,4096,true,false).all { it==0.toShort() })
            assertTrue(file.name+" source first sounding note",pcm.block(onset+128,2048,true,false).any { it!=0.toShort() })
            assertEquals(song.identity.digest,plan.revisionPin)
        }
    }
    @Test fun tenMinuteFourTrackTwoVoicePlanHasBoundedPreparationAndSteadyBlocks() {
        val seed=fixture("simple").score
        val measures=(0 until 300).map { seed.measures.first().copy(id="m-"+it,label=(it+1).toString()) }
        val tracks=(0..3).map { seed.tracks.single().copy(id="track-"+it) }
        val voices=tracks.flatMapIndexed { ti,t ->
            listOf(Voice("high-"+ti,t.id,measures.flatMapIndexed { mi,m ->
                (0..3).map { beat -> ScoreEvent("e-"+ti+"-"+mi+"-"+beat,m.id,Q(beat.toLong()),Q.ONE,WrittenValue(4),
                    listOf(ScoreNote("n-"+ti+"-"+mi+"-"+beat,1,beat,64+beat))) }
            }),Voice("low-"+ti,t.id,measures.mapIndexed { mi,m ->
                ScoreEvent("bass-e-"+ti+"-"+mi,m.id,Q.ZERO,Q(4),WrittenValue(1),listOf(ScoreNote("bass-n-"+ti+"-"+mi,6,0,40)))
            }))
        }
        val score=seed.copy(measures=measures,tracks=tracks,voices=voices,tempos=listOf(Tempo(Q.ZERO,Q.ONE,120.0)))
        val spec=PracticeSpec("long","原创长曲","资源测量",tracks.first().id,tracks.map { it.id },
            listOf(PracticeSegment(Coordinate(measures.first().id),Coordinate(measures.last().id,Q(4)))))
        fun heap()=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()
        fun measure(label: String,compile: ()->PerformancePlan): Map<String,Any> {
            val before=heap();var peak=before;val prepare=System.nanoTime()
            val plan=compile();peak=maxOf(peak,heap())
            val session=Session(sequence(plan),120,false,0)
            val pcm=Pcm(session,sine());peak=maxOf(peak,heap())
            val prepareMs=(System.nanoTime()-prepare)/1e6
            val timings=mutableListOf<Double>()
            repeat(400) { i ->
                val time=System.nanoTime();pcm.block(i*512L,512,true,true)
                timings+=(System.nanoTime()-time)/1e6;peak=maxOf(peak,heap())
            }
            val sorted=timings.drop(10).sorted()
            val p99=sorted[(sorted.size*.99).toInt().coerceAtMost(sorted.lastIndex)]
            assertTrue(prepareMs.isFinite() && p99.isFinite())
            return mapOf("scenario" to label,"durationSeconds" to session.loopSeconds,"tracks" to plan.score.tracks.size,
                "voices" to plan.score.voices.size,"notes" to plan.notes.size,"prepareMs" to prepareMs,
                "firstBlockMs" to timings.first(),"blockP99Ms" to p99,"heapBeforeBytes" to before,
                "sampledPeakHeapBytes" to peak,"heapAfterBytes" to heap())
        }
        val dense=Phrases.singleNote.copy(id="notation-fixture", sequence=NotationFixtures.legato(Technique.HAMMER))
        val scenarios=listOf(
            measure("legacy-short-M01") { Phrases.singleNote.sequence.performance },
            measure("legacy-dense-"+dense.id) { LegacySequenceAdapter.compile(dense.sequence) },
            measure("ten-minute-four-tracks") { PracticeCompiler.compile(score,spec) })
        assertEquals(600.0,scenarios.last().getValue("durationSeconds") as Double,1e-9)
        assertEquals(6000,scenarios.last().getValue("notes"))
        File("../work/music-package/host-performance.json").apply { parentFile!!.mkdirs();writeBytes(StrictJson.encode(
            mapOf("sampleRate" to 44100,"blockFrames" to 512,"blockDurationMs" to 512000.0/44100,"scenarios" to scenarios,
                "memoryMeasure" to "sampled JVM used heap; GC-sensitive; excludes native/audio buffers",
                "deviceUnderrunsVerified" to false))) }
    }
}
