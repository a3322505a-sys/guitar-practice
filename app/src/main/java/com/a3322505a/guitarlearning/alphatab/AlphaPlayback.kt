@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.AlphaTabView
import alphaTab.model.*
import alphaTab.midi.AlphaTabMetronomeEvent
import alphaTab.midi.MidiEventType
import alphaTab.midi.MidiFile
import alphaTab.midi.MidiTickLookup
import alphaTab.rendering.RenderFinishedEventArgs
import alphaTab.rendering.utils.BoundsLookup
import alphaTab.synth.MidiEventsPlayedEventArgs
import alphaTab.synth.PlaybackRange
import alphaTab.synth.PlayerState
import alphaTab.synth.PlayerStateChangedEventArgs
import alphaTab.synth.PositionChangedEventArgs
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.*
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.music.score.Meter
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import java.util.concurrent.Executors

internal interface EngineHost {
    var settings: alphaTab.Settings
    var tracks: List<Track>
    var customCursorHandler: alphaTab.ICursorHandler?
    var midiEventsPlayedFilter: alphaTab.collections.List<MidiEventType>
    val score: Score?
    val isReadyForPlayback: Boolean
    var playbackRange: PlaybackRange?
    var playbackSpeed: Double
    var isLooping: Boolean
    var tickPosition: Double
    val timePosition: Double
    val endTick: Double
    val tickCache: MidiTickLookup?
    val boundsLookup: BoundsLookup?
    var masterVolume: Double
    var metronomeVolume: Double
    var countInVolume: Double
    fun changeTrackMute(tracks: alphaTab.collections.List<Track>, mute: Boolean)
    fun changeTrackVolume(tracks: alphaTab.collections.List<Track>, volume: Double)
    fun scrollToCursor()
    fun loadMidiForScore()
    fun play(): Boolean
    fun pause()
    fun stop()
    fun playBeat(beat: Beat)
    fun notifyScoreRendered()
    fun resetScrollPosition()
    fun onMidiEventsPlayed(listener: (MidiEventsPlayedEventArgs) -> Unit)
    fun onError(listener: (Throwable) -> Unit)
    fun onRenderStarted(listener: (Boolean) -> Unit)
    fun onBeatMouseDown(listener: (Beat) -> Unit)
    fun onRenderFinished(listener: (RenderFinishedEventArgs) -> Unit)
    fun onPostRenderFinished(listener: () -> Unit)
    fun onMidiLoad(listener: (MidiFile) -> Unit)
    fun onPlayerReady(listener: () -> Unit)
    fun onPlayerStateChanged(listener: (PlayerStateChangedEventArgs) -> Unit)
    fun onPlayerPositionChanged(listener: (PositionChangedEventArgs) -> Unit)
    fun onPlayerFinished(listener: () -> Unit)
}

private class AlphaTabEngineHost(private val view: AlphaTabView) : EngineHost {
    private val api get() = view.api
    override var settings: alphaTab.Settings
        get() = view.settings
        set(value) { view.settings = value }
    override var tracks: List<Track>
        get() = view.tracks?.toList().orEmpty()
        set(value) { view.tracks = value }
    override var customCursorHandler: alphaTab.ICursorHandler?
        get() = api.customCursorHandler
        set(value) { api.customCursorHandler = value }
    override var midiEventsPlayedFilter: alphaTab.collections.List<MidiEventType>
        get() = api.midiEventsPlayedFilter
        set(value) { api.midiEventsPlayedFilter = value }
    override val score: Score? get() = api.score
    override val isReadyForPlayback: Boolean get() = api.isReadyForPlayback
    override var playbackRange: PlaybackRange?
        get() = api.playbackRange
        set(value) { api.playbackRange = value }
    override var playbackSpeed: Double
        get() = api.playbackSpeed
        set(value) { api.playbackSpeed = value }
    override var isLooping: Boolean
        get() = api.isLooping
        set(value) { api.isLooping = value }
    override var tickPosition: Double
        get() = api.tickPosition
        set(value) { api.tickPosition = value }
    override val timePosition: Double get() = api.timePosition
    override val endTick: Double get() = api.endTick
    override val tickCache: MidiTickLookup? get() = api.tickCache
    override val boundsLookup: BoundsLookup? get() = api.renderer.boundsLookup
    override var masterVolume: Double
        get() = api.masterVolume
        set(value) { api.masterVolume = value }
    override var metronomeVolume: Double
        get() = api.metronomeVolume
        set(value) { api.metronomeVolume = value }
    override var countInVolume: Double
        get() = api.countInVolume
        set(value) { api.countInVolume = value }
    override fun changeTrackMute(tracks: alphaTab.collections.List<Track>, mute: Boolean) = api.changeTrackMute(tracks, mute)
    override fun changeTrackVolume(tracks: alphaTab.collections.List<Track>, volume: Double) = api.changeTrackVolume(tracks, volume)
    override fun scrollToCursor() = api.scrollToCursor()
    override fun loadMidiForScore() = api.loadMidiForScore()
    override fun play(): Boolean = api.play()
    override fun pause() = api.pause()
    override fun stop() = api.stop()
    override fun playBeat(beat: Beat) = api.playBeat(beat)
    override fun notifyScoreRendered() { (view.parent as? PracticeScoreHost)?.notifyScoreRendered() }
    override fun resetScrollPosition() { (view.parent as? PracticeScoreHost)?.resetScrollPosition() }
    override fun onMidiEventsPlayed(listener: (MidiEventsPlayedEventArgs) -> Unit) { api.midiEventsPlayed.on(listener) }
    override fun onError(listener: (Throwable) -> Unit) { api.error.on(listener) }
    override fun onRenderStarted(listener: (Boolean) -> Unit) { api.renderStarted.on(listener) }
    override fun onBeatMouseDown(listener: (Beat) -> Unit) { api.beatMouseDown.on(listener) }
    override fun onRenderFinished(listener: (RenderFinishedEventArgs) -> Unit) { api.renderFinished.on(listener) }
    override fun onPostRenderFinished(listener: () -> Unit) { api.postRenderFinished.on(listener) }
    override fun onMidiLoad(listener: (MidiFile) -> Unit) { api.midiLoad.on(listener) }
    override fun onPlayerReady(listener: () -> Unit) { api.playerReady.on(listener) }
    override fun onPlayerStateChanged(listener: (PlayerStateChangedEventArgs) -> Unit) { api.playerStateChanged.on(listener) }
    override fun onPlayerPositionChanged(listener: (PositionChangedEventArgs) -> Unit) { api.playerPositionChanged.on(listener) }
    override fun onPlayerFinished(listener: () -> Unit) { api.playerFinished.on(listener) }
}

/** One native engine instance for notation, practice, audition and the heard position. */
class AlphaPlayback(context: Context) : PracticePlayback {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener { if (it < 0) pause() }.build()
    private var view: AlphaTabView? = null
    private var host: EngineHost? = null
    private val scoreCursor = PracticeCursorHandler { host?.scrollToCursor() }
    private var cursorTempoMap: CursorTempoMap? = null
    private var sourceTempoMap: CursorTempoMap? = null
    private var displayClock: CursorDisplayClock? = null
    // Exactly one frame callback owns cursor placement. Player position callbacks only
    // update the observed clock; never cancel/restart the animation between screen frames.
    private var cursorFrameScheduled = false
    private val cursorFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!cursorFrameScheduled) return
            if (closed || displayClock == null || progress.phase != Phase.PLAYING || countIn) {
                cursorFrameScheduled = false
                return
            }
            renderDisplayCursor()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    var cursorOffsetMs: Int = 0
        set(value) {
            val next = value.coerceIn(-500, 500)
            if (field == next) return
            field = next
            // The next display frame applies the visual offset. Never seek/restart audio.
        }

    private fun stopDisplayCursor() {
        if (cursorFrameScheduled) {
            cursorFrameScheduled = false
            Choreographer.getInstance().removeFrameCallback(cursorFrame)
        }
        displayClock = null
        scoreCursor.releaseDisplay()
    }
    private fun sampleDisplayCursor(tick: Double, heardAt: Long, speed: Double) {
        val h = host ?: return
        val map = cursorTempoMap ?: return
        if (displayClock == null) {
            displayClock = CursorDisplayClock(map, h.playbackRange?.startTick ?: 0.0,
                h.playbackRange?.endTick ?: h.endTick)
        }
        displayClock?.sample(tick, heardAt, speed)
        if (!cursorFrameScheduled) {
            cursorFrameScheduled = true
            Choreographer.getInstance().postFrameCallback(cursorFrame)
        }
    }
    private fun renderDisplayCursor() {
        if (progress.phase != Phase.PLAYING || countIn) return
        val h = host ?: return
        val tick = displayClock?.tickAt(SystemClock.uptimeMillis(), cursorOffsetMs, false) ?: return
        val track = selectedTrack ?: return
        val lookup = h.tickCache?.findBeat(alphaTab.core.ecmaScript.Set(alphaTab.collections.DoubleList(track.index)), tick) ?: return
        val boundsLookup = h.boundsLookup ?: return
        val bounds = boundsLookup.findBeat(lookup.beat) ?: return
        var endX = bounds.realBounds.x + bounds.realBounds.w
        if (lookup.cursorMode == alphaTab.midi.MidiTickLookupFindBeatResultCursorMode.ToNextBext) {
            lookup.nextBeat?.beat?.let(boundsLookup::findBeat)?.takeIf {
                it.barBounds.masterBarBounds.staffSystemBounds === bounds.barBounds.masterBarBounds.staffSystemBounds
            }?.let { endX = it.onNotesX }
        }
        val fraction = if (lookup.tickDuration > 0) ((tick - lookup.start) / lookup.tickDuration).coerceIn(0.0, 1.0) else 0.0
        scoreCursor.display(bounds, bounds.onNotesX + (endX - bounds.onNotesX) * fraction)
    }
    private var generation = 0
    private var callbackEpoch = 0
    private var closed = false
    private var sequence: Sequence? = null
    private var pendingStart: (() -> Unit)? = null
    private var round by mutableIntStateOf(0)
    private var rounds = 1
    private var countIn = false
    private var beginning = false
    private var auditioning = false
    private var pausedTick: Double? = null
    private var awaitingPlayState = false
    private var pausedDuringCountIn = false
    private var pendingLoopRestart = false
    private var countInEntryVisit = 0
    private var activeCountInPlan: CountInPlan? = null
    private var lastNativeBeatOrdinal = 0
    private var entryFlashEndTick = 0.0
    private var entryFlashEndUptimeMs = 0L
    private data class MidiLoadKey(
        val score: Score,
        val sourceTempo: Boolean,
        val fixedBpm: Int,
        val tempoScale: Double,
    )
    private var loadedMidiKey: MidiLoadKey? = null
    private var pendingMidiKey: MidiLoadKey? = null

    /** 只读预备拍与入拍视觉状态，严禁用于控制混音或播放状态切换。 */
    data class CountInVisualState(
        val active: Boolean = false,
        val plan: CountInPlan? = null,
        val currentClick: Int = 0,
        val entryFlash: Boolean = false,
    )
    var countInCue by mutableStateOf(CountInVisualState()); private set
    private fun resetCountInVisual() {
        countInCue = CountInVisualState()
        activeCountInPlan = null
        lastNativeBeatOrdinal = 0
    }
    private fun planForEntry(plan: PerformancePlan?, masterBar: MasterBar, entryVisit: Int): CountInPlan {
        val visit = plan?.visits?.getOrNull(entryVisit)
        if (visit != null) {
            val leadIn = if (entryVisit == 0) CountInPlan.leadInForFirstVisit(plan) else Q.ZERO
            return CountInPlan.forMeter(visit.measure.meter, leadIn)
        }
        val meter = Meter(
            masterBar.timeSignatureNumerator.toInt().coerceAtLeast(1),
            masterBar.timeSignatureDenominator.toInt().coerceAtLeast(1),
            emptyList(),
        )
        return CountInPlan.forMeter(meter)
    }
    private data class FocusContext(val sequence: Sequence, val bpm: Int, val repeats: Int, val range: BarRange,
        val sourceTempo: Boolean, val multiplier: Double)
    private var focusContext: FocusContext? = null
    private var focusIndex: PracticeFocusIndex? = null
    var focus: PracticeFocus? by mutableStateOf(null); private set
    private var focusNoteIds: Set<String> = emptySet()
    private var cursorIntervals: IntervalIndex<PlanNote>? = null
    var renderStarts = 0; private set
    var modelLoads = 0; private set
    var midiLoads = 0; private set
    var attaches = 0; private set
    var detaches = 0; private set

    fun configurePractice(value: Sequence?, bpm: Int, repeats: Int, range: BarRange,
        sourceTempo: Boolean, multiplier: Double) {
        if(value==null) { focusContext=null; return }
        focusContext=FocusContext(value,bpm,repeats,range,sourceTempo,multiplier)
    }
    private fun selectBeat(beat: Beat) {
        val c=focusContext ?: return
        val index=focusIndex ?: return
        if(beat.voice.bar.staff.track !== selectedTrack || beat.voice.bar.staff.track.score !== score) return
        val visit=beat.voice.bar.index.toInt()
        val source=index.plan.visits.getOrNull(visit) ?: return
        index.at(source.start.value()+beat.displayStart/960,c.range)?.takeIf { it.visit==visit }?.let(::selectFocus)
    }
    private fun tickFor(selected: PracticeFocus): Double {
        val plan=focusIndex!!.plan
        val visit=plan.visits[selected.visit]
        val offset=(selected.at-visit.start).value()*960
        val beat=selectedTrack?.staves?.first()?.bars?.get(selected.visit)?.voices
            ?.flatMap { it.beats.toList() }?.firstOrNull { it.graceType==GraceType.None && kotlin.math.abs(it.displayStart-offset)<1e-5 }
        return (projection?.visits?.getOrNull(selected.visit)?.start ?: visit.start.value()*960)+(beat?.playbackStart ?: offset)
    }
    private fun selectFocus(selected: PracticeFocus) {
        val c=focusContext ?: return
        if(!ready || c.sequence !== sequence || selected.visit !in c.range.first..c.range.last) return
        if(active) pause()
        callbackEpoch++
        pendingStart=null; beginning=false; countIn=false
        awaitingPlayState=false; pausedDuringCountIn=false; pendingLoopRestart=false
        resetCountInVisual()
        if(auditioning) host?.stop()
        auditioning=false
        round=0
        focus=selected; focusNoteIds=selected.notes.map { it.id }.toSet()
        Log.i("AlphaPractice","focus visit=${selected.visit} q=${selected.at} events=${selected.events.map { it.id }}")
        session=Session(c.sequence,c.bpm,true,c.repeats,range=c.range,sourceTempo=c.sourceTempo,tempoMultiplier=c.multiplier)
        currentTick=tickFor(selected); currentVisit=selected.visit; pausedTick=currentTick
        progress=Progress(Phase.PAUSED,message="已定位 · 从本小节练")
        positions=emptyList()
        scoreCursor.prepare(false,null)
        host?.tickPosition=currentTick
        updateCursor()
        audio.abandonAudioFocusRequest(focusRequest)
    }
    var score: Score? by mutableStateOf(null); private set
    var selectedTrack: Track? by mutableStateOf(null); private set
    var projection: EngineProjection? by mutableStateOf(null); private set
    var ready by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var rendered by mutableStateOf(false); private set
    var positions: List<Position> by mutableStateOf(emptyList()); private set
    var currentVisit by mutableIntStateOf(0); private set
    var currentTick by mutableDoubleStateOf(0.0); private set
    var effectiveBpm by mutableDoubleStateOf(60.0); private set
    /** Native milliseconds already include synth speed. Only the MIDI tick bounds
     * need conversion; subtract the selected range's start without scaling again. */
    internal fun playbackTime(range: BarRange, multiplier: Double): PlaybackTime {
        if (loading) return PlaybackTime()
        val visits = projection?.visits ?: return PlaybackTime()
        val selected = session?.range ?: range
        val first = visits.getOrNull(selected.first) ?: return PlaybackTime()
        val last = visits.getOrNull(selected.last) ?: return PlaybackTime()
        val h = host ?: return PlaybackTime()
        val nativeClock = session != null && focus == null && ready && !beginning
        val map = (if (nativeClock) cursorTempoMap else sourceTempoMap) ?: return PlaybackTime()
        val rate = if (nativeClock) h.playbackSpeed else multiplier
        val timeline = PlaybackTimeline(map, first.start, last.end, rate)
        val position = when {
            progress.phase == Phase.ENDED -> timeline.durationMillis
            countIn || pausedDuringCountIn || beginning || progress.phase in listOf(Phase.IDLE, Phase.ERROR, Phase.PREPARING) -> 0.0
            nativeClock -> timeline.elapsedMillis(h.timePosition)
            else -> timeline.elapsedMillis(map.millisAt(currentTick) / rate)
        }
        return PlaybackTime(position, timeline.durationMillis)
    }
    override var progress by mutableStateOf(Progress()); private set
    override var session: Session? by mutableStateOf(null); private set
    override var cursor: Cursor? by mutableStateOf(null); private set
    override val active get() = progress.phase in listOf(Phase.PLAYING, Phase.PREPARING)
    override var guitar: Boolean = true
        set(value) { if (field != value) { field = value; applyMix() } }
    override var click: Boolean = true
        set(value) {
            if(field==value) return
            field = value
            applyMix()
        }
    private var mix = PlaybackMix()
    override fun setMix(mix: PlaybackMix) {
        if (this.mix != mix) { this.mix = mix; applyMix() }
    }
    override fun setLooping(enabled: Boolean) {
        val nextRounds = if (enabled) 0 else 1
        rounds = nextRounds
        session = session?.copy(repeats = nextRounds)
        if (!enabled && pendingLoopRestart) {
            pendingLoopRestart = false
            round = 0
            positions = emptyList(); cursor = null
            resetCountInVisual()
            stopDisplayCursor()
            scoreCursor.stop()
            progress = Progress(Phase.ENDED, progress.tick, "本次练习结束")
            audio.abandonAudioFocusRequest(focusRequest)
        }
    }

    fun attach(nativeView: AlphaTabView, acceptTap: () -> Boolean = { false }) {
        check(view == null || view === nativeView)
        if (view === nativeView) return
        view = nativeView
        attachHost(AlphaTabEngineHost(nativeView), acceptTap)
    }

    internal fun attachHost(engineHost: EngineHost, acceptTap: () -> Boolean = { false }) {
        check(host == null || host === engineHost)
        if (host === engineHost) return
        host = engineHost
        attaches++; Log.i("AlphaPractice", "attach=$attaches")
        engineHost.settings = AlphaScoreAdapter.settings()
        engineHost.customCursorHandler = scoreCursor
        fun ui(action: () -> Unit) {
            val epoch = callbackEpoch
            main.post { if (!closed && host === engineHost && epoch == callbackEpoch) action() }
        }
        // AlphaTab 1.8.4 official API emits these after their audio buffers
        // have played, including during the native count-in.
        engineHost.midiEventsPlayedFilter = alphaTab.collections.List<MidiEventType>(MidiEventType.AlphaTabMetronome)
        engineHost.onMidiEventsPlayed { played ->
            val beats = played.events.toList()
                .filterIsInstance<AlphaTabMetronomeEvent>()
                .sortedBy { it.metronomeNumerator }
            if (beats.isNotEmpty()) ui {
                if (countIn && !awaitingPlayState && progress.phase == Phase.PLAYING) {
                    val total = activeCountInPlan?.totalClicks ?: 0
                    beats.forEach { event ->
                        val ordinal = event.metronomeNumerator.toInt() + 1
                        if (ordinal in 1..total && ordinal > lastNativeBeatOrdinal) {
                            if (lastNativeBeatOrdinal == 0) Log.i("AlphaPractice", "count-in nativeBeat=$ordinal total=$total")
                            lastNativeBeatOrdinal = ordinal
                            countInCue = countInCue.copy(
                                active = true,
                                plan = activeCountInPlan,
                                currentClick = ordinal,
                                entryFlash = false,
                            )
                        }
                    }
                }
            }
        }
        engineHost.onError { failure -> ui { fail(failure) } }
        engineHost.onRenderStarted { ui { renderStarts++; Log.i("AlphaPractice", "renderStarted=$renderStarts") } }
        // Android 1.8.4 emits down on a confirmed tap even with default seek disabled.
        // The guarded view excludes scroll, long press and multitouch before accepting it.
        engineHost.onBeatMouseDown { beat ->
            if (acceptTap()) {
                val model = score
                ui { if (score === model) selectBeat(beat) }
            }
        }
        engineHost.onRenderFinished {
            ui {
                rendered = true
                host?.notifyScoreRendered()
                Log.i("AlphaPractice", "rendered bars=${score?.masterBars?.length}")
            }
        }
        engineHost.onPostRenderFinished {
            ui { host?.notifyScoreRendered() }
        }
        engineHost.onMidiLoad { midi ->
            midiLoads++
            Log.i("AlphaPractice", "midiLoad=$midiLoads")
            val current = score
            if (current != null && engineHost.score === current) {
                AlphaScoreAdapter.adaptMidi(current, midi)
                val projected = EngineProjection.fromMidi(current, midi, engineHost.tickCache!!.masterBars.toList())
                val displayMap = CursorTempoMap(midi)
                ui {
                    if (score === current) {
                        projection = projected; cursorTempoMap = displayMap
                        if (sourceTempoMap == null) sourceTempoMap = displayMap
                    }
                }
            }
        }
        engineHost.onPlayerReady { ui { onReady() } }
        // 1.8.4 Android's loadedMidiInfo getter recurses when midiLoaded's initial
        // subscription value is evaluated. playerReady is emitted after each MIDI load.
        engineHost.onPlayerStateChanged { event -> ui {
            if (auditioning) {
                if (event.state == PlayerState.Paused) {
                    auditioning = false
                    audio.abandonAudioFocusRequest(focusRequest)
                }
                return@ui
            }
            if (beginning) return@ui
            if (awaitingPlayState) {
                if (event.state == PlayerState.Playing) {
                    awaitingPlayState = false
                } else {
                    return@ui
                }
            }
            if (event.state == PlayerState.Paused) {
                stopDisplayCursor()
                if (event.stopped && pendingLoopRestart) {
                    pendingLoopRestart = false
                    launchLoopPass()
                    return@ui
                }
                resetCountInVisual()
            }
            if (progress.phase !in listOf(Phase.IDLE, Phase.ENDED, Phase.ERROR)) {
                progress = progress.copy(phase = if (event.state == PlayerState.Playing) Phase.PLAYING else Phase.PAUSED)
                // 1.8.4 snaps a paused beat to its first written occurrence. Keep the
                // heard tick instead, especially on a later repeat visit in a GP file.
                if (event.state == PlayerState.Paused && !event.stopped && !countIn) pausedTick?.let { engineHost.tickPosition = it }
            }
            Log.i("AlphaPractice", "state=${event.state} stopped=${event.stopped} audition=$auditioning")
        } }
        engineHost.onPlayerPositionChanged { event ->
            val heardAt = SystemClock.uptimeMillis()
            ui {
                if (auditioning || beginning || awaitingPlayState || progress.phase !in listOf(Phase.PLAYING, Phase.PAUSED)) return@ui
                if (event.isSeek && progress.phase == Phase.PAUSED && pausedTick?.let {
                    kotlin.math.abs(event.currentTick - it) > 1e-6
                } == true) return@ui
                // The native preparation-to-music transition is a seek event. Setup seeks
                // precede PlayerState.Playing and are filtered out by awaitingPlayState above.
                val nativeEntry = countIn && progress.phase == Phase.PLAYING
                if (event.isSeek && progress.phase == Phase.PLAYING && !nativeEntry) return@ui
                if (!event.isSeek && progress.phase == Phase.PLAYING) pausedTick = null
                if (nativeEntry) {
                    // First main playback position: the actual musical entry.
                    countIn = false
                    applyMix(countInOnPlay = false)
                    val now = SystemClock.uptimeMillis()
                    val ticksPerMs = (event.modifiedTempo.coerceAtLeast(20.0) * 960.0) / 60000.0
                    entryFlashEndUptimeMs = now + 350L
                    entryFlashEndTick = event.currentTick + maxOf(960.0, ticksPerMs * 350.0)
                    countInCue = CountInVisualState(
                        active = false,
                        plan = activeCountInPlan,
                        currentClick = activeCountInPlan?.totalClicks ?: lastNativeBeatOrdinal,
                        entryFlash = true,
                    )
                    val flashEpoch = callbackEpoch
                    main.postDelayed({
                        if (!closed && flashEpoch == callbackEpoch && countInCue.entryFlash &&
                            SystemClock.uptimeMillis() >= entryFlashEndUptimeMs) {
                            resetCountInVisual()
                        }
                    }, 360L)
                    Log.i("AlphaPractice", "music-entry tick=${event.currentTick} click=$click")
                } else if (countInCue.entryFlash && event.currentTick >= entryFlashEndTick &&
                    SystemClock.uptimeMillis() >= entryFlashEndUptimeMs) {
                    resetCountInVisual()
                }
                scoreCursor.follow()
                if (progress.phase == Phase.PLAYING) sampleDisplayCursor(event.currentTick, heardAt, event.modifiedTempo / event.originalTempo)
                if (focus != null && progress.phase == Phase.PAUSED) return@ui
                currentTick = event.currentTick; effectiveBpm = event.modifiedTempo
                currentVisit = projection?.visitAt(currentTick) ?: 0
                positions = if (guitar) projection?.active(currentTick, selectedTrack?.index?.toInt() ?: 0).orEmpty() else emptyList()
                updateCursor()
            }
        }
        engineHost.onPlayerFinished { ui {
            if (progress.phase != Phase.PLAYING) return@ui
            round++
            Log.i("AlphaPractice", "finished round=$round requested=$rounds")
            if (rounds > 0 && round >= rounds) {
                round = 0
                pendingLoopRestart = false
                positions = emptyList(); cursor = null
                resetCountInVisual()
                stopDisplayCursor()
                scoreCursor.stop()
                progress = Progress(Phase.ENDED, progress.tick, "本次练习结束")
                audio.abandonAudioFocusRequest(focusRequest)
            } else {
                // Keep api.isLooping = false so the current pass rings out cleanly and the
                // subsequent Paused(stopped = true) state transitions smoothly into the next count-in.
                pendingLoopRestart = true
                stopDisplayCursor()
                positions = emptyList()
            }
        } }
        if (score != null) render()
    }

    fun detach(nativeView: AlphaTabView) {
        if (view !== nativeView) return
        view = null
        detachHost()
    }

    internal fun detachHost() {
        if (host == null) return
        pause(); pendingStart = null; host = null; ready = false
        loadedMidiKey = null; pendingMidiKey = null
        detaches++; Log.i("AlphaPractice", "detach=$detaches")
        // AlphaTabView owns destroy in onDetachedFromWindow.
    }

    fun prepareSequence(value: Sequence?) {
        if (value == null || value.catalogueOnly || sequence === value) return
        stop()
        val request = ++generation
        sequence = value; loading = true; ready = false; error = ""; rendered = false
        worker.execute {
            try {
                val converted = AlphaScoreAdapter.fromSequence(value)
                val index = PracticeFocusIndex(value.performance)
                main.post {
                    if (!closed && request == generation) {
                        focusIndex = index; setModel(converted,
                            value.performance.score.tracks.filter { it.id in value.performance.practice.mixTrackIds }
                                .indexOfFirst { it.id == value.performance.practice.mainTrackId })
                        val cursorNotes = index.plan.notes.filter { it.track.id == index.plan.practice.mainTrackId }
                        cursorIntervals = IntervalIndex(cursorNotes, { it.start.value() }, { maxOf(it.soundEnd, it.event.end).value() })
                    }
                }
            } catch (failure: Exception) { main.post { if (request == generation && !closed) fail(failure) } }
        }
    }

    private fun setModel(value: Score, trackIndex: Int) {
        AlphaNotation.prepare(value)
        score = value
        modelLoads++; Log.i("AlphaPractice", "modelLoad=$modelLoads")
        selectedTrack = value.tracks.firstOrNull { it.index.toInt() == trackIndex } ?: value.tracks.first()
        projection = null; cursorTempoMap = null; sourceTempoMap = null; ready = false; loading = false; error = ""; rendered = false
        loadedMidiKey = null
        pendingMidiKey = MidiLoadKey(value, true, 0, 1.0)
        currentTick = 0.0; currentVisit = 0; positions = emptyList()
        resetCountInVisual()
        host?.resetScrollPosition()
        render()
    }
    private fun render() {
        host?.let { h ->
            h.tracks = listOf(checkNotNull(selectedTrack))
            applyMix()
        }
    }
    private fun applyMix(countInOnPlay: Boolean = countIn) {
        val h = host ?: return
        score?.tracks?.forEach { track ->
            val muteMain = sequence?.performance?.practice?.muteMainTrack == true && track === selectedTrack
            h.changeTrackMute(alphaTab.collections.List(track), !guitar || muteMain)
            h.changeTrackVolume(alphaTab.collections.List(track), AlphaTone.trackVolume(track.playbackInfo.volume, mix.music))
        }
        if (!guitar) positions = emptyList()
        h.masterVolume = AlphaTone.MASTER
        // 1.8.4's metronome setter also changes the active synth channel. While
        // counting in, defer formal-click changes until the native music entry.
        if (!countIn || beginning) {
            h.metronomeVolume = if (click) AlphaTone.clickVolume(mix.metronome) else 0.0
        }
        h.countInVolume = if (countInOnPlay) AlphaTone.countInVolume(mix.metronome) else 0.0
    }
    private fun onReady() {
        val h = host ?: return
        if (h.score !== score || !h.isReadyForPlayback) return
        ready = true
        loadedMidiKey = pendingMidiKey
        if (!countIn || beginning) applyMix()
        Log.i("AlphaPractice", "ready visits=${projection?.visits?.size}")
        pendingStart?.let { action -> pendingStart = null; action() }
    }
    override fun start(sequence: Sequence, bpm: Int, follow: Boolean, repeats: Int, range: BarRange,
        sourceTempo: Boolean, multiplier: Double) {
        if (this.sequence !== sequence || loading) {
            rejectStart("谱面尚未准备完成，请稍后开始")
            return
        }
        session = Session(sequence, bpm, follow, if (follow) repeats else 1, range = range,
            sourceTempo = sourceTempo, tempoMultiplier = multiplier)
        startVisits(range.first, range.last, if (follow) repeats else 1, follow, sourceTempo, bpm, multiplier)
    }

    private fun launchLoopPass() {
        val s = session ?: return
        val h = host ?: return
        val visits = projection?.visits ?: return
        if (!ready || s.range.first !in visits.indices || s.range.last !in visits.indices) return
        val entryVisit = s.range.first
        val plan = s.sequence.performance
        val countInPlan = planForEntry(plan, visits[entryVisit].masterBar, entryVisit)
        stopDisplayCursor()
        countIn = true
        beginning = true
        pausedDuringCountIn = false
        countInEntryVisit = entryVisit
        activeCountInPlan = countInPlan
        lastNativeBeatOrdinal = 0
        countInCue = CountInVisualState(active = true, plan = countInPlan, currentClick = 0, entryFlash = false)
        currentVisit = entryVisit
        currentTick = visits[entryVisit].start
        positions = emptyList()
        val trackLookup = alphaTab.core.ecmaScript.Set<Double>(
            alphaTab.collections.DoubleList(checkNotNull(selectedTrack).index)
        )
        val startBeat = h.tickCache?.findBeat(trackLookup, currentTick)?.beat
        scoreCursor.prepare(true, startBeat)
        h.isLooping = false
        h.tickPosition = currentTick
        updateCursor()
        applyMix(countInOnPlay = true)
        awaitingPlayState = true
        progress = progress.copy(phase = Phase.PLAYING)
        beginning = false
        val started = h.play()
        Log.i("AlphaPractice", "loop-pass round=$round first=$entryVisit tick=$currentTick accepted=$started")
        if (!started) {
            awaitingPlayState = false
            fail(IllegalStateException("引擎尚未能开始播放，请重试"))
        }
    }

    private fun startVisits(first: Int, last: Int, repeats: Int, pre: Boolean, sourceTempo: Boolean, bpm: Int, speed: Double, entryVisit: Int = first) {
        val model = score ?: return
        val h = host ?: return
        if (!ready || first < 0 || last < first || last >= (projection?.visits?.size ?: 0)) {
            rejectStart("谱面或选段尚未准备完成")
            return
        }
        val sourceBar = projection!!.visits[entryVisit].masterBar.index.toInt()
        if (entryVisit > 0 && selectedTrack?.staves?.any { staff -> staff.bars[sourceBar].voices.any { voice ->
            voice.beats.firstOrNull { !it.isRest }?.notes?.any { note -> note.isTieDestination &&
                note.tieOrigin?.beat?.voice?.bar?.index?.let { it < sourceBar } == true } == true
        } } == true) {
            rejectStart("选段起点含上一小节延音，请把起点前移")
            return
        }
        val engineSpeed = TempoControl.engineSpeed(speed)
        val tempoScale = speed / engineSpeed
        val desiredKey = MidiLoadKey(model, sourceTempo, if (sourceTempo) 0 else bpm, tempoScale)
        stopDisplayCursor()
        focus = null; focusNoteIds = emptySet()
        callbackEpoch++
        pendingStart = null; pendingLoopRestart = false; pausedDuringCountIn = false; awaitingPlayState = false
        h.stop()
        auditioning = false; pausedTick = null; round = 0; rounds = repeats
        countInEntryVisit = entryVisit
        val countInPlan = if (pre) planForEntry(sequence?.performance, projection!!.visits[entryVisit].masterBar, entryVisit) else null
        activeCountInPlan = countInPlan
        lastNativeBeatOrdinal = 0
        countIn = pre; positions = emptyList()
        countInCue = if (pre && countInPlan != null) {
            CountInVisualState(active = true, plan = countInPlan, currentClick = 0, entryFlash = false)
        } else {
            CountInVisualState()
        }
        val launch: () -> Unit = start@{
            val visits = checkNotNull(projection).visits
            h.playbackRange = PlaybackRange().apply { startTick = visits[first].start; endTick = visits[last].end }
            h.playbackSpeed = engineSpeed
            if (kotlin.math.abs(h.playbackSpeed - engineSpeed) > 1e-9) {
                fail(IllegalStateException("播放器未接受目标速度，请重试"))
                return@start
            }
            h.isLooping = false
            currentTick = visits[entryVisit].start; currentVisit = entryVisit
            val trackLookup = alphaTab.core.ecmaScript.Set<Double>(
                alphaTab.collections.DoubleList(checkNotNull(selectedTrack).index)
            )
            val startBeat = h.tickCache?.findBeat(trackLookup, currentTick)?.beat
            scoreCursor.prepare(countIn, startBeat)
            // Setting the range does not seek the SDK cursor. Seek while paused so
            // count-in holds the selected beat, including a later bar/repeated visit.
            h.tickPosition = currentTick
            updateCursor()
            beginning = true
            applyMix(countInOnPlay = pre)
            if (audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                awaitingPlayState = true
                progress = progress.copy(phase = Phase.PLAYING); beginning = false
                val started = h.play()
                Log.i("AlphaPractice", "play first=$first entry=$entryVisit last=$last repeats=$repeats speed=$speed pre=$pre countInVolume=${h.countInVolume} metronomeVolume=${h.metronomeVolume} ready=${h.isReadyForPlayback} accepted=$started")
                if (!started) {
                    awaitingPlayState = false
                    fail(IllegalStateException("引擎尚未能开始播放，请重试"))
                }
            } else {
                beginning = false
                fail(IllegalStateException("当前音频被其他应用占用，请稍后重试"))
            }
        }
        if (loadedMidiKey == desiredKey && ready) {
            launch()
        } else {
            beginning = true
            progress = Progress(Phase.PREPARING, message = "准备声音…")
            pendingStart = launch
            ready = false
            pendingMidiKey = desiredKey
            AlphaNotation.withPlaybackTempo(model, sourceTempo, bpm, tempoScale) { h.loadMidiForScore() }
        }
    }
    private fun updateCursor() {
        val s = session
        if (s == null) { progress = progress.copy(tick = currentTick); return }
        val local = (focus?.at?.value() ?: currentTick / 960) * s.quarter
        val visited = s.plan.visits.getOrNull(currentVisit) ?: return
        val beat = visited.measure.meter.groups.indexOfLast { it.value() <= currentTick / 960 - visited.start.value() }.coerceAtLeast(0)
        val uiTick = s.preTicks + round * s.rangeDuration.value() * s.quarter + local - s.startTick
        progress = progress.copy(tick = if (countIn) 0.0 else uiTick)
        val q = currentTick / 960
        val sourceIds = if (countIn) emptySet() else if (focus != null) focusNoteIds else cursorIntervals?.query(q, q + 1e-7).orEmpty().filter { n ->
            n.track.id == s.plan.practice.mainTrackId && n.start.value() <= q &&
                (n.position in positions && n.soundEnd.value() > q || n.note.dead && n.event.end.value() > q)
        }.map { it.id }.toSet()
        cursor = Cursor(countIn, round, local, currentVisit, beat, visitId = visited.id,
            measureId = visited.sourceMeasureId, activeNoteIds = sourceIds, positions = if (countIn) emptyList() else focus?.positions ?: positions)
    }
    override fun pause() {
        if (auditioning) {
            host?.pause(); auditioning = false; audio.abandonAudioFocusRequest(focusRequest); return
        }
        if (!active) return
        if (progress.phase == Phase.PREPARING) { stop(); return }
        stopDisplayCursor()
        callbackEpoch++
        pendingStart = null; beginning = false
        pendingLoopRestart = false; awaitingPlayState = false
        if (countIn) {
            pausedDuringCountIn = true
            countIn = false
            resetCountInVisual()
            pausedTick = projection?.visits?.getOrNull(countInEntryVisit)?.start ?: currentTick
            host?.stop()
            progress = progress.copy(phase = Phase.PAUSED)
            audio.abandonAudioFocusRequest(focusRequest)
            return
        }
        pausedDuringCountIn = false
        resetCountInVisual()
        pausedTick = host?.tickPosition ?: currentTick
        host?.pause(); progress = progress.copy(phase = Phase.PAUSED)
        audio.abandonAudioFocusRequest(focusRequest)
    }
    override fun resume() {
        if (progress.phase != Phase.PAUSED || !ready) return
        if (focus != null) {
            val selected = focus!!
            val c = focusContext ?: return
            session = Session(c.sequence, c.bpm, true, c.repeats, range = c.range, sourceTempo = c.sourceTempo, tempoMultiplier = c.multiplier)
            startVisits(c.range.first, c.range.last, c.repeats, true, c.sourceTempo, c.bpm, c.multiplier, selected.visit)
            return
        }
        if (pausedDuringCountIn) {
            val s = session ?: return
            pausedDuringCountIn = false
            startVisits(s.range.first, s.range.last, s.repeats, true, s.sourceTempo, s.bpm, s.tempoMultiplier, countInEntryVisit)
            return
        }
        if (auditioning) { host?.stop(); auditioning = false }
        if (audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            applyMix(countInOnPlay = false)
            scoreCursor.follow()
            pausedTick?.let { host?.tickPosition = it }
            awaitingPlayState = true
            progress = progress.copy(phase = Phase.PLAYING)
            val started = host?.play() == true
            Log.i("AlphaPractice", "resume tick=$pausedTick accepted=$started")
            if (!started) {
                awaitingPlayState = false
                fail(IllegalStateException("引擎尚未能继续播放"))
            }
        }
    }
    override fun restart() {
        val saved = session
        if (saved != null) start(saved.sequence, saved.bpm, true, saved.repeats, saved.range, saved.sourceTempo, saved.tempoMultiplier)
    }
    override fun invalidate() { stop(); progress = Progress(message = "设置已改变，下一次播放从头开始") }
    override fun stop() {
        stopDisplayCursor()
        round = 0; countIn = false; pausedDuringCountIn = false; pendingLoopRestart = false; awaitingPlayState = false
        applyMix()
        callbackEpoch++
        pendingStart = null; beginning = false; auditioning = false
        pausedTick = null; focus = null; focusNoteIds = emptySet(); scoreCursor.stop()
        resetCountInVisual()
        host?.stop(); positions = emptyList(); cursor = null; session = null
        currentTick = 0.0; currentVisit = 0
        progress = Progress(); audio.abandonAudioFocusRequest(focusRequest)
    }
    override fun audition(positions: List<Position>, strum: Boolean, capoFret: Int, openStringMidi: List<Int>) {
        if (active || !ready || positions.isEmpty()) return
        val preview = Sequence(listOf(Event(0, 12, positions, strum = strum)), 12, "试听",
            capoFret = capoFret, openStringMidi = openStringMidi)
        val beat = AlphaScoreAdapter.audition(preview, selectedTrack)
        if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        auditioning = true
        host?.playBeat(beat)
    }
    private fun rejectStart(message: String) {
        stopDisplayCursor()
        focus = null; focusNoteIds = emptySet(); countIn = false
        pausedDuringCountIn = false; pendingLoopRestart = false; awaitingPlayState = false
        resetCountInVisual()
        positions = emptyList(); cursor = null; scoreCursor.stop()
        progress = Progress(Phase.ERROR, message = message)
    }
    private fun fail(failure: Throwable) {
        stopDisplayCursor()
        pendingStart = null; beginning = false; ready = false; loading = false
        loadedMidiKey = null; pendingMidiKey = null
        countIn = false; pausedDuringCountIn = false; pendingLoopRestart = false; awaitingPlayState = false
        pausedTick = null; focus = null; focusNoteIds = emptySet(); scoreCursor.stop()
        resetCountInVisual()
        error = failure.message ?: "引擎加载失败"
        Log.e("AlphaPractice", error, failure)
        progress = Progress(Phase.ERROR, message = error); positions = emptyList(); cursor = null
        audio.abandonAudioFocusRequest(focusRequest)
    }
    override fun release() { stop(); closed = true; generation++; worker.shutdownNow(); view = null; host = null }
}
