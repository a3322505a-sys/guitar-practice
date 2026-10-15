@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning

import alphaTab.TabRhythmMode
import alphaTab.collections.DoubleList
import alphaTab.exporter.Gp7Exporter
import alphaTab.midi.TempoChangeEvent
import alphaTab.model.*
import alphaTab.rendering.ScoreRenderer
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.music.score.Tempo
import com.a3322505a.guitarlearning.practice.Event
import com.a3322505a.guitarlearning.practice.Sequence
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AlphaNotationTest {
    private fun midi(score: Score) = EngineProjection.generate(score).first
    // alphaTab's real synth accepts per-note pitch bends (MIDI 2.0); exporting them
    // to an SMF 1.0 byte stream would discard this fixture's slide information.
    // Every event property in the pinned SDK is scalar, so compare all public
    // event getters, including channel/key/velocity, tempo, bend and controller data.
    private fun midiFacts(score: Score) = midi(score).events.toList().map { event ->
        event.javaClass.name to event.javaClass.methods.filter {
            it.parameterCount == 0 && it.name != "getClass" && (it.name.startsWith("get") || it.name.startsWith("is"))
        }.sortedBy { it.name }.map { it.name to it.invoke(event) }
    }

    private fun nativeScore(): Score {
        val score = Score()
        repeat(3) { index -> score.addMasterBar(MasterBar().apply {
            tempoAutomations.push(Automation.buildTempoAutomation(false, 0.0,
                if (index == 0) 120.0 else 90.0, 2.0))
            if (index == 0) {
                tempoAutomations.push(Automation.buildTempoAutomation(false, .125, 120.0, 2.0))
                tempoAutomations.push(Automation.buildTempoAutomation(false, .375, 90.0, 2.0))
            }
        }) }
        repeat(2) { index ->
            val track = Track().apply { name = "Guitar ${index + 1}"; playbackInfo.program = 24.0
                playbackInfo.primaryChannel = (index * 2).toDouble(); playbackInfo.secondaryChannel = (index * 2 + 1).toDouble() }
            score.addTrack(track)
            val staff = Staff().apply {
                capo = if (index == 0) 2.0 else 5.0
                stringTuning = Tuning("", DoubleList(64.0, 59.0, 55.0, 50.0, 45.0, if (index == 0) 40.0 else 38.0))
            }
            track.addStaff(staff)
            repeat(3) { barIndex ->
                val bar = Bar(); staff.addBar(bar)
                val voice = Voice(); bar.addVoice(voice)
                repeat(4) { beatIndex -> voice.addBeat(Beat().apply {
                    duration = if (beatIndex < 2) Duration.Eighth else if (beatIndex == 2) Duration.Half else Duration.Quarter
                    addNote(Note().apply {
                        string = 6.0; fret = beatIndex.toDouble(); isPalmMute = barIndex == 0
                        if (beatIndex == 0) slideOutType = SlideOutType.Shift
                        if (beatIndex == 2) isHammerPullOrigin = true
                    })
                }) }
            }
        }
        score.finish(AlphaScoreAdapter.settings())
        // Exercise the actual native GP import path, including its source visibility flags.
        return GpLibrary.parse(Gp7Exporter().export(score).buffer.toByteArray())
    }

    @Test fun acceptedPlanEmitsInitialTempoAndRealChangesAtExactMidBarPositions() {
        val sequence = Sequence((0 until 12).map { Event(it * 12, 12, listOf(Position(1, it % 4))) }, 144, "tempo")
        val plan = sequence.performance.copy(tempos = listOf(
            Tempo(Q.ZERO, Q.ONE, 120.0), Tempo(Q(1, 2), Q.ONE, 120.0),
            Tempo(Q(3, 2), Q.ONE, 90.0), Tempo(Q(4), Q.ONE, 90.0),
            Tempo(Q(13, 2), Q.ONE, 105.0)))
        val score = AlphaScoreAdapter.fromPlan(plan)
        val changes = score.masterBars.toList().flatMap { bar -> bar.tempoAutomations.toList().map { Triple(bar.index, it.ratioPosition, it.value) } }
        assertEquals(listOf(Triple(0.0, 0.0, 120.0), Triple(0.0, .375, 90.0), Triple(1.0, .625, 105.0)), changes)
        val tempos = midi(score).events.filterIsInstance<TempoChangeEvent>()
        assertEquals(listOf(0.0, 1440.0, 6240.0), tempos.map { it.tick })
        assertEquals(listOf(120.0, 90.0, 105.0), tempos.map { it.beatsPerMinute })
        assertEquals(TabRhythmMode.ShowWithBars, AlphaScoreAdapter.settings().notation.rhythmMode)
        assertTrue(score.tracks.all { it.staves.all { s -> s.showTablature && !s.showStandardNotation } })
    }

    @Test fun nativeGpDisplayCleanupPreservesMidiAndEachTracksCapoTuningAndTechniques() {
        val score = nativeScore()
        val before = midiFacts(score)
        val original = score.masterBars.toList().map { it.tempoAutomations }
        AlphaNotation.prepare(score)
        AlphaNotation.prepare(score)
        score.masterBars.forEachIndexed { index, bar -> assertSame(original[index], bar.tempoAutomations) }
        assertEquals(before, midiFacts(score))
        assertEquals(listOf(2.0, 5.0), score.tracks.toList().map { it.staves.first().capo })
        assertEquals(listOf(40.0, 38.0), score.tracks.toList().map { it.staves.first().tuning.last() })
        assertTrue(score.tracks.all { it.staves.all { s -> s.showTablature && !s.showStandardNotation && !s.showSlash && !s.showNumbered } })
        val visible = score.masterBars.toList().flatMap { bar -> bar.tempoAutomations.toList().filter { it.isVisible }.map { bar.index to it.ratioPosition } }
        assertEquals(listOf(0.0 to 0.0, 0.0 to .375), visible)
        assertTrue(score.tracks.first().staves.first().bars.first().voices.first().beats.first().notes.first().isPalmMute)
        assertEquals(SlideOutType.Shift, score.tracks.first().staves.first().bars.first().voices.first().beats.first().notes.first().slideOutType)
    }

    @Test fun fixedPracticeTempoRestoresPrintedSourceEventsEvenWhenGenerationFails() {
        val score = nativeScore()
        AlphaNotation.prepare(score)
        val original = score.masterBars.toList().map { it.tempoAutomations }
        val source = midiFacts(score)
        val fixed = AlphaNotation.withPlaybackTempo(score, false, 72) { midi(score) }
        assertEquals(listOf(72.0), fixed.events.filterIsInstance<TempoChangeEvent>().map { it.beatsPerMinute })
        score.masterBars.forEachIndexed { index, bar -> assertSame(original[index], bar.tempoAutomations) }
        assertEquals(source, midiFacts(score))
        val failure = IllegalStateException("probe")
        assertSame(failure, runCatching { AlphaNotation.withPlaybackTempo(score, false, 72) { throw failure } }.exceptionOrNull())
        score.masterBars.forEachIndexed { index, bar -> assertSame(original[index], bar.tempoAutomations) }
        assertEquals(source, midiFacts(score))
    }

    @Test fun implicitInitialTempoBecomesVisibleWithoutChangingSdkPlayback() {
        val score = nativeScore()
        score.masterBars.forEach { it.tempoAutomations = alphaTab.collections.List() }
        val before = midiFacts(score)
        AlphaNotation.prepare(score)
        assertEquals(120.0, score.masterBars.first().tempoAutomations.single().value, 0.0)
        assertTrue(score.masterBars.first().tempoAutomations.single().isVisible)
        assertEquals(before, midiFacts(score))
    }

    @Test fun tabOnlyEngineRendersSingleCapoAndTempoWithRhythmAtPhoneAndTabletWidths() {
        val score = nativeScore()
        AlphaNotation.prepare(score)
        listOf(600.0, 1200.0).forEach { width ->
            val settings = AlphaScoreAdapter.settings().apply { core.engine = "svg"; core.enableLazyLoading = false }
            val renderer = ScoreRenderer(settings)
            val parts = mutableListOf<String>()
            var failure: Throwable? = null
            renderer.width = width
            renderer.error.on { failure = it }
            renderer.partialRenderFinished.on { (it.renderResult as? String)?.let(parts::add) }
            renderer.renderScore(score, DoubleList(0.0), null)
            failure?.let { throw AssertionError("TAB render at $width", it) }
            assertTrue("No TAB SVG at $width", parts.isNotEmpty())
            val svg = parts.joinToString("")
            assertEquals(1, Regex("Capo\\. fret 2").findAll(svg).count())
            assertEquals(1, Regex(" = 120").findAll(svg).count())
            assertEquals(1, Regex(" = 90").findAll(svg).count())
            val bars = renderer.boundsLookup!!.staffSystems.toList().flatMap { it.bars.toList() }.flatMap { it.bars.toList() }
            // A score+TAB render creates two bounds per written bar; TAB alone creates one.
            assertEquals(3, bars.size)
            assertTrue(bars.all { it.beats.length == 4.0 })
            assertTrue(svg.contains("P.M."))
            renderer.destroy()
        }
    }
}
