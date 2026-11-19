@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.Settings
import alphaTab.collections.DoubleList
import alphaTab.midi.AlphaTabMetronomeEvent
import alphaTab.midi.MidiEventType
import alphaTab.midi.MidiFile
import alphaTab.midi.MidiFileGenerator
import alphaTab.midi.AlphaSynthMidiFileHandler
import alphaTab.midi.MidiTickLookup
import alphaTab.model.*
import alphaTab.rendering.RenderFinishedEventArgs
import alphaTab.rendering.utils.BoundsLookup
import alphaTab.synth.MidiEventsPlayedEventArgs
import alphaTab.synth.PlaybackRange
import alphaTab.synth.PlayerState
import alphaTab.synth.PlayerStateChangedEventArgs
import alphaTab.synth.PositionChangedEventArgs
import android.os.Looper
import com.a3322505a.guitarlearning.SongTestLibrary
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.audio.PlaybackMix
import com.a3322505a.guitarlearning.music.score.Coordinate
import com.a3322505a.guitarlearning.music.score.Form
import com.a3322505a.guitarlearning.music.score.Measure
import com.a3322505a.guitarlearning.music.score.Meter
import com.a3322505a.guitarlearning.music.score.PracticeKey
import com.a3322505a.guitarlearning.music.score.PracticeSegment
import com.a3322505a.guitarlearning.music.score.PracticeSpec
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.music.score.ScoreDocument
import com.a3322505a.guitarlearning.music.score.ScoreEvent
import com.a3322505a.guitarlearning.music.score.ScoreNote
import com.a3322505a.guitarlearning.music.score.Tempo as ScoreTempo
import com.a3322505a.guitarlearning.music.score.Track as ScoreTrack
import com.a3322505a.guitarlearning.music.score.Voice as ScoreVoice
import com.a3322505a.guitarlearning.music.score.WrittenValue
import com.a3322505a.guitarlearning.practice.BarRange
import com.a3322505a.guitarlearning.practice.compile.PracticeCompiler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NativeCountInTest {
    private fun score(numerator: Int = 4, denominator: Int = 4, pickup: Boolean = false, firstRest: Boolean = false): Score {
        val score = Score()
        val track = Track().apply { playbackInfo.program = 24.0 }
        score.addTrack(track)
        val staff = Staff().apply { stringTuning = Tuning("", DoubleList(64.0,59.0,55.0,50.0,45.0,40.0)) }
        track.addStaff(staff)
        repeat(2) { barIndex ->
            score.addMasterBar(MasterBar().apply {
                timeSignatureNumerator = numerator.toDouble(); timeSignatureDenominator = denominator.toDouble()
                isAnacrusis = pickup && barIndex == 0
                if (barIndex == 0) tempoAutomations.push(Automation.buildTempoAutomation(false,0.0,120.0,2.0))
            })
            val bar = Bar(); staff.addBar(bar)
            val voice = Voice(); bar.addVoice(voice)
            repeat(if (pickup && barIndex == 0) 1 else numerator) { beatIndex ->
                voice.addBeat(Beat().apply {
                    duration = if (denominator == 8) Duration.Eighth else Duration.Quarter
                    if (firstRest && barIndex == 0 && beatIndex == 0) isEmpty = false
                    else addNote(Note().apply { string = 6.0; fret = 3.0 })
                })
            }
        }
        score.finish(AlphaScoreAdapter.settings())
        return score
    }
    @Test fun nativePreparationHasExactMeterBeatsAndAudiblePcmWithFormalClickOff() {
        for ((n,d) in listOf(4 to 4,3 to 4,6 to 8)) for (rest in listOf(false,true)) {
            NativeSynthFixture(score(n,d,firstRest=rest)).use { test ->
                val beats = mutableListOf<Int>()
                var entered = false
                test.engine.positionChanged.on { if (test.engine.state == alphaTab.synth.PlayerState.Playing) entered = true }
                test.engine.midiEventsPlayed.on { batch -> if (!entered) batch.events.filterIsInstance<AlphaTabMetronomeEvent>()
                    .forEach { beats += it.metronomeNumerator.toInt() + 1 } }
                test.engine.metronomeVolume = 0.0
                test.engine.countInVolume = AlphaTone.CLICK
                assertTrue(test.engine.play())
                test.until { entered }
                assertEquals("$n/$d rest=$rest",(1..n).toList(),beats)
                assertTrue(test.heard.any { abs(it) > .0001f })
                // Native buffer rounding is bounded; pickup/rests add no artificial preparation.
                assertEquals(n * 500.0 * 4 / d, test.frames * 1000.0 / 44100, 50.0)
            }
        }
    }
    @Test fun pickupStillUsesNativeFullMeterAndZeroVolumeReproducesSkippedPreparation() {
        for (volume in listOf(0.0,AlphaTone.CLICK)) NativeSynthFixture(score(pickup=true)).use { test ->
            var beats = 0
            var entered = false
            test.engine.positionChanged.on { if (test.engine.state == alphaTab.synth.PlayerState.Playing) entered = true }
            test.engine.midiEventsPlayed.on { if (!entered) beats += it.events.filterIsInstance<AlphaTabMetronomeEvent>().size }
            test.engine.countInVolume = volume
            assertTrue(test.engine.play()); test.until { entered }
            assertEquals(if (volume == 0.0) 0 else 4,beats)
        }
    }
    @Test fun pauseContinuesRemainingNativePreparationAndRestartRestoresFullPreparation() {
        NativeSynthFixture(score()).use { test ->
            val beats = mutableListOf<Int>()
            var entered = false
            test.engine.positionChanged.on { if (test.engine.state == alphaTab.synth.PlayerState.Playing) entered = true }
            test.engine.midiEventsPlayed.on { if (!entered) it.events.filterIsInstance<AlphaTabMetronomeEvent>()
                .forEach { event -> beats += event.metronomeNumerator.toInt() + 1 } }
            test.engine.metronomeVolume = 0.0; test.engine.countInVolume = AlphaTone.CLICK
            assertTrue(test.engine.play()); test.until { beats.size >= 2 }
            test.engine.pause()
            val heardBefore = test.heard.size
            test.engine.metronomeVolume = AlphaTone.CLICK // play() restores this native channel on resume
            test.engine.countInVolume = 0.0
            assertTrue(test.engine.play()); test.until { entered }
            assertEquals(listOf(1,2,3,4),beats)
            assertEquals("Preparation ticks must not seek the written music",0.0,test.engine.tickPosition,1.0)
            assertTrue(test.heard.drop(heardBefore).any { abs(it) > .0001f })
            test.engine.metronomeVolume = 0.0
            test.engine.pause(); val tick = test.engine.tickPosition
            test.engine.countInVolume = 0.0
            assertTrue(test.engine.play()); test.advance(200)
            assertTrue(test.engine.tickPosition > tick)
            test.engine.stop(); entered = false; beats.clear()
            test.engine.countInVolume = AlphaTone.CLICK
            assertTrue(test.engine.play()); test.until { entered }
            assertEquals(listOf(1,2,3,4),beats)
        }
    }
    @Test fun laterSelectedBarAndNativeLoopsNeverAddAnotherPreparationBar() {
        for (formalClick in listOf(0.0, AlphaTone.CLICK)) {
            val score=score(3,4)
            val selected=EngineProjection.generate(score).second.visits[1]
            NativeSynthFixture(score).use { test ->
                val beats=mutableListOf<Int>()
                val boundaries=mutableListOf<Long>()
                var entryFrame: Long?=null
                var entryTick: Double?=null
                test.engine.playbackRange=alphaTab.synth.PlaybackRange().apply {
                    startTick=selected.start; endTick=selected.end
                }
                test.engine.positionChanged.on { event ->
                    if(test.engine.state==alphaTab.synth.PlayerState.Playing && entryFrame==null) {
                        entryFrame=test.frames; entryTick=event.currentTick
                    }
                }
                test.engine.midiEventsPlayed.on { batch -> if(entryFrame==null)
                    batch.events.filterIsInstance<AlphaTabMetronomeEvent>().forEach { beats+=it.metronomeNumerator.toInt()+1 } }
                test.engine.finished.on { boundaries+=test.frames }
                test.engine.metronomeVolume=formalClick
                test.engine.countInVolume=AlphaTone.CLICK
                test.engine.isLooping=true
                assertTrue(test.engine.play())
                test.until { boundaries.size>=2 }
                assertEquals(listOf(1,2,3),beats)
                assertEquals(selected.start,entryTick!!,1.0)
                assertEquals(1500.0,(boundaries[0]-entryFrame!!)*1000.0/44100,50.0)
                assertEquals(1500.0,(boundaries[1]-boundaries[0])*1000.0/44100,50.0)
            }
        }
    }

    @Test fun countInPlanCoversAllMetersAndPickupAlignment() {
        val uniform44 = Meter(4, 4, listOf(1, 1, 1, 1))
        val plan44 = CountInPlan.forMeter(uniform44)
        assertEquals(4, plan44.totalClicks)
        assertEquals(setOf(1), plan44.groupStarts)
        assertEquals(1, plan44.entrySlotInBar)
        assertEquals(listOf(true, false, false, false), plan44.beats.map { it.isAccent })
        assertEquals(listOf(false, false, false, true), plan44.beats.map { it.isFinalBeat })

        val plan34 = CountInPlan.forMeter(Meter(3, 4, listOf(1, 1, 1)))
        assertEquals(3, plan34.totalClicks)
        assertEquals(setOf(1), plan34.groupStarts)

        val plan24 = CountInPlan.forMeter(Meter(2, 4, listOf(1, 1)))
        assertEquals(4, plan24.totalClicks)
        assertEquals(listOf(0, 0, 1, 1), plan24.beats.map { it.barIndex })
        assertEquals(listOf(1, 2, 1, 2), plan24.beats.map { it.displayNumber })

        val plan54 = CountInPlan.forMeter(Meter(5, 4, listOf(3, 2)))
        assertEquals(5, plan54.totalClicks)
        assertEquals(setOf(1, 4), plan54.groupStarts)

        val plan68Uniform = CountInPlan.forMeter(Meter(6, 8, listOf(1, 1, 1, 1, 1, 1)))
        val plan68Grouped = CountInPlan.forMeter(Meter(6, 8, listOf(3, 3)))
        assertEquals(6, plan68Uniform.totalClicks)
        assertEquals(setOf(1, 4), plan68Uniform.groupStarts)
        assertEquals(setOf(1, 4), plan68Grouped.groupStarts)

        assertEquals(setOf(1, 4, 7), CountInPlan.forMeter(Meter(9, 8, listOf(3, 3, 3))).groupStarts)
        assertEquals(setOf(1, 4, 7, 10), CountInPlan.forMeter(Meter(12, 8, listOf(3, 3, 3, 3))).groupStarts)
        assertEquals(setOf(1, 3, 5), CountInPlan.forMeter(Meter(7, 8, listOf(2, 2, 3))).groupStarts)

        val pickup2 = CountInPlan.forMeter(uniform44, Q(2))
        assertEquals(6, pickup2.totalClicks)
        assertEquals(2, pickup2.leadInBeats)
        assertEquals(3, pickup2.entrySlotInBar)
        assertEquals(listOf(1, 2, 3, 4, 1, 2), pickup2.beats.map { it.displayNumber })
        assertEquals(listOf(false, false, false, false, true, true), pickup2.beats.map { it.isLeadIn })

        val pickup3 = CountInPlan.forMeter(uniform44, Q(3))
        assertEquals(7, pickup3.totalClicks)
        assertEquals(3, pickup3.leadInBeats)
        assertEquals(4, pickup3.entrySlotInBar)

        assertEquals(0, CountInPlan.exactLeadInBeats(uniform44, Q(1, 2)))
        assertEquals(1, CountInPlan.exactLeadInBeats(Meter(6, 8, listOf(3, 3)), Q(1, 2)))
    }

    @Test fun bundledPackagesAndMidBarMeterChangesProduceExactNativeCountInOnAlphaSynth() {
        fun countBeats(score: Score, startTick: Double = 0.0, endTick: Double? = null): List<Int> {
            return NativeSynthFixture(score).use { test ->
                if (endTick != null) {
                    test.engine.playbackRange = PlaybackRange().apply {
                        this.startTick = startTick
                        this.endTick = endTick
                    }
                    test.engine.tickPosition = startTick
                }
                val beats = mutableListOf<Int>()
                var entered = false
                test.engine.positionChanged.on {
                    if (test.engine.state == alphaTab.synth.PlayerState.Playing) entered = true
                }
                test.engine.midiEventsPlayed.on { batch ->
                    if (!entered) {
                        batch.events.filterIsInstance<AlphaTabMetronomeEvent>()
                            .sortedBy { it.metronomeNumerator }
                            .forEach { beats += it.metronomeNumerator.toInt() + 1 }
                    }
                }
                test.engine.metronomeVolume = 0.0
                test.engine.countInVolume = AlphaTone.countInVolume(1.0)
                assertTrue(test.engine.play())
                test.until { entered }
                beats
            }
        }

        val mozart = SongTestLibrary.repository.resolve(PracticeKey("local.mozart", "K265-A").encoded)
        assertEquals(listOf(1, 2, 3, 4), countBeats(AlphaScoreAdapter.fromPlan(mozart.sequence.performance)))

        val himawariIntro = SongTestLibrary.repository.resolve(PracticeKey("local.himawari.complete", "lead-intro").encoded)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), countBeats(AlphaScoreAdapter.fromPlan(himawariIntro.sequence.performance)))

        val usotsukiP1 = SongTestLibrary.repository.resolve(PracticeKey("local.usotsuki-lead-chunks", "p1").encoded)
        val usotsukiScore = AlphaScoreAdapter.fromPlan(usotsukiP1.sequence.performance)
        val usotsukiProjection = EngineProjection.generate(usotsukiScore).second
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), countBeats(usotsukiScore))
        assertEquals(
            "Bar 2 after pickup must restore normal 4/4 count-in",
            listOf(1, 2, 3, 4),
            countBeats(usotsukiScore, usotsukiProjection.visits[1].start, usotsukiProjection.visits[1].end),
        )

        // Mid-score meter change 4/4 -> 2/4 -> 3/4: starting from bar 2 or bar 3 reads the exact bar meter.
        val doc = ScoreDocument(
            documentId = "meter-change-test",
            measures = listOf(
                Measure("m1", "1", Meter(4, 4, listOf(1, 1, 1, 1)), Q(4)),
                Measure("m2", "2", Meter(2, 4, listOf(1, 1)), Q(2)),
                Measure("m3", "3", Meter(3, 4, listOf(1, 1, 1)), Q(3)),
            ),
            tracks = listOf(ScoreTrack("t1", "Guitar", listOf(64, 59, 55, 50, 45, 40))),
            voices = listOf(
                ScoreVoice(
                    "v1",
                    "t1",
                    listOf(
                        ScoreEvent("e1", "m1", Q.ZERO, Q(4), WrittenValue(1), listOf(ScoreNote("n1", 6, 3, 43))),
                        ScoreEvent("e2", "m2", Q.ZERO, Q(2), WrittenValue(2), listOf(ScoreNote("n2", 5, 3, 48))),
                        ScoreEvent("e3", "m3", Q.ZERO, Q(3), WrittenValue(2, 1), listOf(ScoreNote("n3", 4, 2, 52))),
                    ),
                )
            ),
            tuplets = emptyList(),
            relations = emptyList(),
            tempos = listOf(ScoreTempo(Q.ZERO, Q.ONE, 120.0)),
            dynamics = emptyList(),
            swing = emptyList(),
            form = Form(),
            voicings = emptyList(),
            harmonies = emptyList(),
        )
        val spec = PracticeSpec(
            id = "meter-change",
            title = "Meter Change",
            goal = "test",
            mainTrackId = "t1",
            mixTrackIds = listOf("t1"),
            segments = listOf(PracticeSegment(Coordinate("m1"), Coordinate("m3", Q(3)))),
        )
        val mixedPlan = PracticeCompiler.compile(doc, spec)
        val mixedScore = AlphaScoreAdapter.fromPlan(mixedPlan)
        val mixedProjection = EngineProjection.generate(mixedScore).second
        assertEquals(listOf(1, 2, 3, 4), countBeats(mixedScore, mixedProjection.visits[0].start, mixedProjection.visits[0].end))
        assertEquals(listOf(1, 2, 3, 4), countBeats(mixedScore, mixedProjection.visits[1].start, mixedProjection.visits[1].end))
        assertEquals(listOf(1, 2, 3), countBeats(mixedScore, mixedProjection.visits[2].start, mixedProjection.visits[2].end))
    }

    @Test fun alphaPlaybackStateMachineHandlesReversedBatchesSetupSeeksDuplicateReadyPauseAndLoopHandoff() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        val context = RuntimeEnvironment.getApplication()
        val fake = FakeEngineHost()
        val player = AlphaPlayback(context)
        try {
            player.attachHost(fake)

            val phrase = SongTestLibrary.repository.resolve(PracticeKey("local.mozart", "K265-A").encoded)
            val sequence = phrase.sequence
            player.prepareSequence(sequence)
            val deadline = System.currentTimeMillis() + 5000L
            while (fake.score == null && System.currentTimeMillis() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
                Thread.sleep(10)
            }
            assertNotNull("Expected prepareSequence to load Score onto host", fake.score)
            fake.emitInitialMidiLoad()
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
            assertTrue(player.ready)
            val initialMidiLoads = fake.loadMidiCalls

            // Normal BPM inside 0.125..8x must NOT reload MIDI.
            player.setMix(PlaybackMix(0.8, 0.5))
            player.start(sequence, 90, follow = true, repeats = 0, range = BarRange(0, 1), sourceTempo = true, multiplier = 0.75)
            assertEquals("Normal start should reuse already-loaded MIDI", initialMidiLoads, fake.loadMidiCalls)
            assertEquals(Phase.PLAYING, player.progress.phase)
            assertTrue(player.countInCue.active)
            assertEquals(4, player.countInCue.plan?.totalClicks)
            assertFalse("Engine native looping must remain disabled for breath count-in", fake.isLooping)

            // Setup seek before PlayerState.Playing must NOT end count-in prematurely.
            fake.emitPosition(0.0, isSeek = true)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Setup seek prior to Playing must not clear count-in", player.countInCue.active)

            // Duplicate playerReady during active count-in must not overwrite metronomeVolume or restart.
            val countInMetronomeBefore = fake.metronomeVolume
            fake.emitPlayerState(PlayerState.Playing, stopped = false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            fake.emitPlayerReady()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(countInMetronomeBefore, fake.metronomeVolume, 1e-9)
            assertEquals(1, fake.playCalls)

            // Reversed midiEventsPlayed batch [3, 2, 1] must be sorted ascending so currentClick reaches 3.
            fake.emitMetronomeBeats(2, 1, 0)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(3, player.countInCue.currentClick)

            // Pause during count-in -> resume restarts full count-in without reloading MIDI.
            player.pause()
            assertEquals(Phase.PAUSED, player.progress.phase)
            assertFalse(player.countInCue.active)
            player.resume()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Phase.PLAYING, player.progress.phase)
            assertTrue(player.countInCue.active)
            assertEquals(0, player.countInCue.currentClick)
            assertEquals(initialMidiLoads, fake.loadMidiCalls)

            // Even if no midiEventsPlayed arrive after resume, native transition seek after Playing enters music.
            fake.emitPlayerState(PlayerState.Playing, stopped = false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            fake.emitPosition(0.0, isSeek = true)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse(player.countInCue.active)
            assertTrue(player.countInCue.entryFlash)
            assertEquals(0.0, fake.countInVolume, 1e-9)
            assertEquals(AlphaTone.clickVolume(0.5), fake.metronomeVolume, 1e-6)

            // Pass ends while looping is active: playerFinished keeps PLAYING, then Paused(stopped=true) triggers next count-in.
            val playCallsBeforeLoop = fake.playCalls
            fake.emitPlayerFinished()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Phase.PLAYING, player.progress.phase)
            fake.emitPlayerState(PlayerState.Paused, stopped = true)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(playCallsBeforeLoop + 1, fake.playCalls)
            assertTrue("Loop pass must enter fresh count-in", player.countInCue.active)
            assertTrue(fake.countInVolume > 0.0)
            assertEquals(initialMidiLoads, fake.loadMidiCalls)

            // Disabling looping mid-handoff ends cleanly at ENDED.
            fake.emitPlayerState(PlayerState.Playing, stopped = false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            fake.emitPosition(0.0, isSeek = true)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            fake.emitPlayerFinished()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            player.setLooping(false)
            assertEquals(Phase.ENDED, player.progress.phase)
        } finally {
            player.release()
            ShadowChoreographer.setPaused(false)
        }
    }

    private class FakeEngineHost : EngineHost {
        override var settings: Settings = AlphaScoreAdapter.settings()
        override var tracks: List<Track> = emptyList()
            set(value) { field = value }
        override var customCursorHandler: alphaTab.ICursorHandler? = null
        override var midiEventsPlayedFilter: alphaTab.collections.List<MidiEventType> = alphaTab.collections.List()
        override val score: Score? get() = tracks.firstOrNull()?.score
        override var isReadyForPlayback: Boolean = false
        override var playbackRange: PlaybackRange? = null
        override var playbackSpeed: Double = 1.0
        override var isLooping: Boolean = false
        override var tickPosition: Double = 0.0
        override var timePosition: Double = 0.0
        override val endTick: Double = 3840.0
        override var tickCache: alphaTab.midi.MidiTickLookup? = null
        override val boundsLookup: BoundsLookup? = null
        override var masterVolume: Double = 1.0
        override var metronomeVolume: Double = 1.0
        override var countInVolume: Double = 0.0
        var loadMidiCalls = 0
        var playCalls = 0
        private val metronomeListeners = mutableListOf<(alphaTab.synth.MidiEventsPlayedEventArgs) -> Unit>()
        private val midiLoadListeners = mutableListOf<(MidiFile) -> Unit>()
        private val readyListeners = mutableListOf<() -> Unit>()
        private val stateListeners = mutableListOf<(PlayerStateChangedEventArgs) -> Unit>()
        private val posListeners = mutableListOf<(PositionChangedEventArgs) -> Unit>()
        private val finishedListeners = mutableListOf<() -> Unit>()

        override fun changeTrackMute(tracks: alphaTab.collections.List<Track>, mute: Boolean) {}
        override fun changeTrackVolume(tracks: alphaTab.collections.List<Track>, volume: Double) {}
        override fun scrollToCursor() {}
        override fun loadMidiForScore() { loadMidiCalls++ }
        override fun play(): Boolean { playCalls++; return true }
        override fun pause() {}
        override fun stop() {}
        override fun playBeat(beat: Beat) {}
        override fun notifyScoreRendered() {}
        override fun resetScrollPosition() {}
        override fun onMidiEventsPlayed(listener: (alphaTab.synth.MidiEventsPlayedEventArgs) -> Unit) { metronomeListeners += listener }
        override fun onError(listener: (Throwable) -> Unit) {}
        override fun onRenderStarted(listener: (Boolean) -> Unit) {}
        override fun onBeatMouseDown(listener: (Beat) -> Unit) {}
        override fun onRenderFinished(listener: (RenderFinishedEventArgs) -> Unit) {}
        override fun onPostRenderFinished(listener: () -> Unit) {}
        override fun onMidiLoad(listener: (MidiFile) -> Unit) { midiLoadListeners += listener }
        override fun onPlayerReady(listener: () -> Unit) { readyListeners += listener }
        override fun onPlayerStateChanged(listener: (PlayerStateChangedEventArgs) -> Unit) { stateListeners += listener }
        override fun onPlayerPositionChanged(listener: (PositionChangedEventArgs) -> Unit) { posListeners += listener }
        override fun onPlayerFinished(listener: () -> Unit) { finishedListeners += listener }

        fun emitInitialMidiLoad() {
            val s = checkNotNull(score)
            val midi = MidiFile()
            val generator = MidiFileGenerator(s, AlphaScoreAdapter.settings(), AlphaSynthMidiFileHandler(midi))
            generator.applyTranspositionPitches = false
            generator.generate()
            tickCache = generator.tickLookup
            midiLoadListeners.toList().forEach { it(midi) }
            isReadyForPlayback = true
            emitPlayerReady()
        }
        fun emitPlayerReady() = readyListeners.toList().forEach { it() }
        fun emitPlayerState(state: PlayerState, stopped: Boolean) =
            stateListeners.toList().forEach { it(PlayerStateChangedEventArgs(state, stopped)) }
        fun emitPosition(tick: Double, isSeek: Boolean) =
            posListeners.toList().forEach { it(PositionChangedEventArgs(0.0, 1000.0, tick, endTick, isSeek, 120.0, 120.0)) }
        fun emitMetronomeBeats(vararg numerators: Int) {
            val events = alphaTab.collections.List<alphaTab.midi.MidiEvent>()
            numerators.forEach { n -> events.push(AlphaTabMetronomeEvent(0.0, 0.0, n.toDouble(), 960.0, 500.0)) }
            metronomeListeners.toList().forEach { it(alphaTab.synth.MidiEventsPlayedEventArgs(events)) }
        }
        fun emitPlayerFinished() = finishedListeners.toList().forEach { it() }
    }
}
