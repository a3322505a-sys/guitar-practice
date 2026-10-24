@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
package com.a3322505a.guitarlearning

import alphaTab.collections.DoubleList
import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.model.Score
import alphaTab.rendering.ScoreRenderer
import alphaTab.synth.AlphaSynthAudioExporter
import alphaTab.synth.AudioExportOptions
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import org.junit.Assert.*
import java.io.File
import kotlin.math.ceil

/** Only public, shipped native conversion/render/synthesis. No legacy audio or TAB clock. */
internal object NativeContent {
    val unsupported = mapOf("complex" to "渐变速度暂未迁移", "techniques" to "渐变力度暂未迁移",
        "nested" to "嵌套连音暂未迁移", "harmonics" to "此人工泛音暂未迁移",
        "pickup" to "小节迁移时值不一致")
    fun assertRejected(name: String) {
        val error=runCatching { score(name) }.exceptionOrNull()
        assertTrue("$name must retain its explicit native boundary",error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty(),error.message.orEmpty().contains(unsupported.getValue(name)))
    }
    private val contract = File("../contracts/song-package/v1")
    fun fixture(name: String): SongPackage = SongReader(SongSchema { File(contract,it).readBytes() })
        .read(File(contract,"fixtures/$name.gpractice").readBytes())
    fun plan(name: String): PerformancePlan {
        if (!name.startsWith("boundary-")) return fixture(name).let { PracticeCompiler.compile(it.score,it.practices.first()) }
        val song=fixture("ties")
        val policy=when(name.removePrefix("boundary-")) { "carry" -> "carryIn"; "reattack" -> "reattack"; else -> "truncate" }
        val segment=com.a3322505a.guitarlearning.music.score.PracticeSegment(
            com.a3322505a.guitarlearning.music.score.Coordinate("tie-b"),
            com.a3322505a.guitarlearning.music.score.Coordinate("tie-b",com.a3322505a.guitarlearning.music.score.Q(4)),
            entry=policy,affectedNoteIds=listOf("tie-origin","tie-target"))
        return PracticeCompiler.compile(song.score,song.practices.first().copy(segments=listOf(segment)))
    }
    fun sequence(plan: PerformancePlan) = Sequence(emptyList(),ceil(plan.length.value()*12).toInt(),"native test",runtimePlan=plan)
    fun score(sequence: Sequence) = AlphaScoreAdapter.fromSequence(sequence)
    fun score(name: String) = AlphaScoreAdapter.fromPlan(plan(name))
    fun render(score: Score, width: Double = 600.0, scale: Double = 1.0): ScoreRenderer {
        val settings = AlphaScoreAdapter.settings().apply {
            core.engine="svg"; core.enableLazyLoading=false; display.scale=scale
        }
        AlphaNotation.prepare(score)
        val renderer = ScoreRenderer(settings)
        val fragments = mutableListOf<String>()
        var failure: Throwable? = null
        renderer.width=width
        renderer.error.on { failure=it }
        renderer.partialRenderFinished.on { (it.renderResult as? String)?.let(fragments::add) }
        renderer.renderScore(score,DoubleList(0.0),null)
        failure?.let { throw AssertionError("native render $width",it) }
        assertTrue("No native SVG",fragments.isNotEmpty())
        assertNotNull(renderer.boundsLookup)
        return renderer
    }
    fun pcm(score: Score, milliseconds: Double = 500.0, music: Double = 1.0, click: Double = 0.0): FloatArray {
        val exporter = AlphaSynthAudioExporter(AudioExportOptions().apply {
            sampleRate=44100.0; masterVolume=AlphaTone.MASTER;metronomeVolume=AlphaTone.CLICK*click
        })
        exporter.loadMidiFile(EngineProjection.generate(score).first)
        exporter.loadSoundFont(Uint8Array(File("src/main/assets/sonivox.sf2").readBytes().asUByteArray()))
        score.tracks.forEach { track ->
            val gain=AlphaTone.trackVolume(track.playbackInfo.volume,music)
            exporter.channelSetMixVolume(track.playbackInfo.primaryChannel,gain)
            exporter.channelSetMixVolume(track.playbackInfo.secondaryChannel,gain)
        }
        exporter.setup()
        val result=exporter.render(milliseconds)?.samples?.data?.toList()?.toFloatArray() ?: floatArrayOf()
        assertTrue(result.isNotEmpty() && result.all { it.isFinite() })
        return result
    }
}
