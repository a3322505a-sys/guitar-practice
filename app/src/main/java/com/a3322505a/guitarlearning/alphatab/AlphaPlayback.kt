@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.AlphaTabView
import alphaTab.model.*
import alphaTab.midi.AlphaTabMetronomeEvent
import alphaTab.midi.MidiEventType
import alphaTab.synth.PlaybackRange
import alphaTab.synth.PlayerState
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
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import java.util.concurrent.Executors

/** One native engine instance for notation, practice, audition and the heard position. */
class AlphaPlayback(context: Context) : PracticePlayback {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener { if (it < 0) pause() }.build()
    private var view: AlphaTabView? = null
    private val scoreCursor = PracticeCursorHandler { view?.api?.scrollToCursor() }
    private var cursorTempoMap: CursorTempoMap? = null
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
        val api = view?.api ?: return
        val map = cursorTempoMap ?: return
        if (displayClock == null) {
            displayClock = CursorDisplayClock(map, api.playbackRange?.startTick ?: 0.0,
                api.playbackRange?.endTick ?: api.endTick)
        }
        displayClock?.sample(tick, heardAt, speed)
        if (!cursorFrameScheduled) {
            cursorFrameScheduled = true
            Choreographer.getInstance().postFrameCallback(cursorFrame)
        }
    }
    private fun renderDisplayCursor() {
        if (progress.phase != Phase.PLAYING || countIn) return
        val api = view?.api ?: return
        val tick = displayClock?.tickAt(SystemClock.uptimeMillis(), cursorOffsetMs, api.isLooping) ?: return
        val track = selectedTrack ?: return
        val lookup = api.tickCache?.findBeat(alphaTab.core.ecmaScript.Set(alphaTab.collections.DoubleList(track.index)), tick) ?: return
        val boundsLookup = api.renderer.boundsLookup ?: return
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
    // Observational-only cues from alphaTab's public played-MIDI event stream.
    // 0 = dark, 1 = red, 2 = amber (last preparation beat),
    // 3 = green (first written beat). No separate clock or audio modification.
    var entryLight by mutableIntStateOf(0); private set
    var entryBeatNumber by mutableIntStateOf(0); private set
    var entryBeatTotal by mutableIntStateOf(0); private set
    private var entryGreenEndTick = 0.0
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
        entryLight=0; entryBeatNumber=0; entryBeatTotal=0
        if(auditioning) view?.api?.stop()
        auditioning=false
        round=0
        focus=selected; focusNoteIds=selected.notes.map { it.id }.toSet()
        Log.i("AlphaPractice","focus visit=${selected.visit} q=${selected.at} events=${selected.events.map { it.id }}")
        session=Session(c.sequence,c.bpm,true,c.repeats,range=c.range,sourceTempo=c.sourceTempo,tempoMultiplier=c.multiplier)
        currentTick=tickFor(selected); currentVisit=selected.visit; pausedTick=currentTick
        progress=Progress(Phase.PAUSED,message="已定位 · 从本小节练")
        positions=emptyList()
        scoreCursor.prepare(false,null)
        view?.api?.tickPosition=currentTick
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
    // Read-only telemetry for the stage progress display; the engine remains the sole clock.
    val positionMillis get() = view?.api?.timePosition ?: 0.0
    val durationMillis get() = view?.api?.endTime ?: 0.0
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
    private val clickGain get() = AlphaTone.CLICK * mix.metronome

    fun attach(nativeView: AlphaTabView, acceptTap: () -> Boolean = { false }) {
        check(view == null || view === nativeView)
        if (view === nativeView) return
        view = nativeView
        attaches++; Log.i("AlphaPractice","attach=$attaches")
        nativeView.settings = AlphaScoreAdapter.settings()
        val api = nativeView.api
        api.customCursorHandler = scoreCursor
        fun ui(action: () -> Unit) {
            val epoch=callbackEpoch
            main.post { if (!closed && view === nativeView && epoch==callbackEpoch) action() }
        }
        // AlphaTab 1.8.4 official API emits these after their audio buffers
        // have played, including during the native count-in.
        api.midiEventsPlayedFilter = alphaTab.collections.List<MidiEventType>(MidiEventType.AlphaTabMetronome)
        api.midiEventsPlayed.on { played ->
            val beats = played.events.toList().filterIsInstance<AlphaTabMetronomeEvent>()
            if (beats.isNotEmpty()) ui {
                if (countIn && progress.phase == Phase.PLAYING) {
                    beats.forEach { event ->
                        val ordinal = event.metronomeNumerator.toInt() + 1
                        if (ordinal in 1..entryBeatTotal && ordinal > entryBeatNumber) {
                            if (entryBeatNumber == 0) Log.i("AlphaPractice", "count-in nativeBeat=$ordinal total=$entryBeatTotal")
                            entryBeatNumber = ordinal
                            entryLight = if (ordinal == entryBeatTotal) 2 else 1
                        }
                    }
                }
            }
        }
        api.error.on { failure -> ui { fail(failure) } }
        api.renderStarted.on { ui { renderStarts++; Log.i("AlphaPractice","renderStarted=$renderStarts") } }
        // Android 1.8.4 emits down on a confirmed tap even with default seek disabled.
        // The guarded view excludes scroll, long press and multitouch before accepting it.
        api.beatMouseDown.on { beat ->
            if(acceptTap()) {
                val model=score
                ui { if(score===model) selectBeat(beat) }
            }
        }
        api.renderFinished.on { ui { rendered = true; Log.i("AlphaPractice","rendered bars=${score?.masterBars?.length}") } }
        api.midiLoad.on { midi ->
            midiLoads++
            Log.i("AlphaPractice","midiLoad=$midiLoads")
            val current = score
            if (current != null && api.score === current) {
                AlphaScoreAdapter.adaptMidi(current, midi)
                val projected = EngineProjection.fromMidi(current, midi, api.tickCache!!.masterBars.toList())
                val displayMap = CursorTempoMap(midi)
                ui { if (score === current) { projection = projected; cursorTempoMap = displayMap } }
            }
        }
        api.playerReady.on { ui { onReady() } }
        // 1.8.4 Android's loadedMidiInfo getter recurses when midiLoaded's initial
        // subscription value is evaluated. playerReady is emitted after each MIDI load.
        api.playerStateChanged.on { event -> ui {
            if (event.state == PlayerState.Paused) {
                stopDisplayCursor()
                if (!beginning) entryLight = 0
            }
            if (!auditioning && !beginning && progress.phase !in listOf(Phase.IDLE, Phase.ENDED, Phase.ERROR)) {
                progress = progress.copy(phase = if (event.state == PlayerState.Playing) Phase.PLAYING else Phase.PAUSED)
                // 1.8.4 snaps a paused beat to its first written occurrence. Keep the
                // heard tick instead, especially on a later repeat visit in a GP file.
                if (event.state == PlayerState.Paused && !event.stopped && !countIn) pausedTick?.let { api.tickPosition = it }
            }
            Log.i("AlphaPractice","state=${event.state} audition=$auditioning")
            if (auditioning && event.state == PlayerState.Paused) { auditioning = false; audio.abandonAudioFocusRequest(focusRequest) }
        } }
        api.playerPositionChanged.on { event ->
            val heardAt = SystemClock.uptimeMillis()
            ui {
            if (auditioning || beginning || progress.phase !in listOf(Phase.PLAYING, Phase.PAUSED)) return@ui
            if (event.isSeek && progress.phase == Phase.PAUSED && pausedTick?.let {
                kotlin.math.abs(event.currentTick - it) > 1e-6
            } == true) return@ui
            // The native preparation-to-music transition is a seek event. Initial
            // setup seeks precede native beats; they must not end preparation.
            val nativeEntry = countIn && progress.phase == Phase.PLAYING && (!event.isSeek || entryBeatNumber > 0)
            if (event.isSeek && progress.phase == Phase.PLAYING && !nativeEntry) return@ui
            if (countIn && !nativeEntry) return@ui
            if (!event.isSeek && progress.phase == Phase.PLAYING) pausedTick = null
            if (nativeEntry) {
                // First main playback position: the actual musical entry.
                entryLight = if (event.currentTick < entryGreenEndTick) 3 else 0
                countIn = false
                applyMix()
                Log.i("AlphaPractice", "music-entry tick=${event.currentTick} click=$click")
            } else if (entryLight == 3 && event.currentTick >= entryGreenEndTick) {
                entryLight = 0
            }
            scoreCursor.follow()
            if (progress.phase == Phase.PLAYING) sampleDisplayCursor(event.currentTick, heardAt, event.modifiedTempo / event.originalTempo)
            if (focus!=null && progress.phase==Phase.PAUSED) return@ui
            currentTick = event.currentTick; effectiveBpm = event.modifiedTempo
            currentVisit = projection?.visitAt(currentTick) ?: 0
            positions = if (guitar) projection?.active(currentTick, selectedTrack?.index?.toInt() ?: 0).orEmpty() else emptyList()
            updateCursor()
        } }
        api.playerFinished.on { ui {
            if (progress.phase != Phase.PLAYING) return@ui
            round++
            Log.i("AlphaPractice","finished round=$round requested=$rounds")
            if (rounds > 0 && round >= rounds) {
                round = 0
                positions = emptyList(); cursor = null
                entryLight = 0
                stopDisplayCursor()
                scoreCursor.stop()
                progress = Progress(Phase.ENDED, progress.tick, "本次练习结束")
                audio.abandonAudioFocusRequest(focusRequest)
            } else {
                // Disable looping before the final pass. The engine performs every boundary.
                api.isLooping = rounds == 0 || round < rounds - 1
            }
        } }
        if (score != null) render()
    }

    fun detach(nativeView: AlphaTabView) {
        if (view !== nativeView) return
        pause(); pendingStart = null; view = null; ready = false
        detaches++; Log.i("AlphaPractice","detach=$detaches")
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
                val index=PracticeFocusIndex(value.performance)
                main.post { if (!closed && request == generation) { focusIndex=index; setModel(converted,
                    value.performance.score.tracks.filter { it.id in value.performance.practice.mixTrackIds }
                        .indexOfFirst { it.id == value.performance.practice.mainTrackId });
                    val cursorNotes=index.plan.notes.filter { it.track.id==index.plan.practice.mainTrackId };
                    cursorIntervals=IntervalIndex(cursorNotes,{ it.start.value() },{ maxOf(it.soundEnd,it.event.end).value() }) } }
            } catch (failure: Exception) { main.post { if (request == generation && !closed) fail(failure) } }
        }
    }

    private fun setModel(value: Score, trackIndex: Int) {
        AlphaNotation.prepare(value)
        score = value
        modelLoads++; Log.i("AlphaPractice","modelLoad=$modelLoads")
        selectedTrack = value.tracks.firstOrNull { it.index.toInt() == trackIndex } ?: value.tracks.first()
        projection = null; cursorTempoMap = null; ready = false; loading = false; error = ""; rendered = false
        currentTick = 0.0; currentVisit = 0; positions = emptyList()
        render()
    }
    private fun render() {
        view?.let { native ->
            native.tracks = listOf(checkNotNull(selectedTrack))
            applyMix()
        }
    }
    private fun applyMix(countInOnPlay: Boolean = countIn) {
        val api = view?.api ?: return
        score?.tracks?.forEach { track ->
            api.changeTrackMute(alphaTab.collections.List(track), !guitar)
            api.changeTrackVolume(alphaTab.collections.List(track), AlphaTone.trackVolume(track.playbackInfo.volume, mix.music))
        }
        if (!guitar) positions = emptyList()
        api.masterVolume = AlphaTone.MASTER
        // 1.8.4's metronome setter also changes the active synth channel. While
        // counting in, defer formal-click changes until the native music entry.
        if (!countIn || beginning || !countInOnPlay) {
            // play() also reapplies this channel on resume. Keep the remaining
            // native preparation audible; music-entry restores the formal mix.
            api.metronomeVolume = if (countIn && !countInOnPlay) AlphaTone.CLICK
                else if (click) clickGain else 0.0
        }
        // Preparation has no mute control. Stored formal-click gain (even an
        // explicit zero) and the stage click switch apply only to written music.
        api.countInVolume = if (countInOnPlay) AlphaTone.CLICK else 0.0
    }
    private fun onReady() {
        val api = view?.api ?: return
        if (api.score !== score || !api.isReadyForPlayback) return
        ready = true; applyMix(); Log.i("AlphaPractice","ready visits=${projection?.visits?.size}")
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

    private fun startVisits(first: Int, last: Int, repeats: Int, pre: Boolean, sourceTempo: Boolean, bpm: Int, speed: Double, entryVisit: Int = first) {
        val model = score ?: return
        val api = view?.api ?: return
        if (!ready || first < 0 || last < first || last >= (projection?.visits?.size ?: 0)) {
            rejectStart("谱面或选段尚未准备完成")
            return
        }
        val sourceBar = projection!!.visits[entryVisit].masterBar.index.toInt()
        if(entryVisit > 0 && selectedTrack?.staves?.any { staff -> staff.bars[sourceBar].voices.any { voice ->
            voice.beats.firstOrNull { !it.isRest }?.notes?.any { note -> note.isTieDestination &&
                note.tieOrigin?.beat?.voice?.bar?.index?.let { it < sourceBar } == true } == true
        } } == true) {
            rejectStart("选段起点含上一小节延音，请把起点前移")
            return
        }
        require(repeats >= 0 && speed in TempoControl.ENGINE_MIN_SPEED..TempoControl.MAX_SPEED)
        stopDisplayCursor()
        focus=null; focusNoteIds=emptySet()
        callbackEpoch++; api.stop(); auditioning=false; pausedTick=null; round = 0; rounds = repeats
        countIn = pre; beginning = true; positions = emptyList()
        entryLight = 0; entryBeatNumber = 0; entryBeatTotal = 0
        progress = Progress(Phase.PREPARING, message = "准备声音…")
        pendingStart = {
            val visits = checkNotNull(projection).visits
            api.playbackRange = PlaybackRange().apply { startTick = visits[first].start; endTick = visits[last].end }
            api.playbackSpeed = speed
            check(kotlin.math.abs(api.playbackSpeed - speed) < 1e-9) { "播放器未接受目标速度" }
            api.isLooping = repeats == 0 || repeats > 1
            applyMix(); currentTick = visits[entryVisit].start; currentVisit = entryVisit
            val trackLookup = alphaTab.core.ecmaScript.Set<Double>(
                alphaTab.collections.DoubleList(checkNotNull(selectedTrack).index))
            val startBeat = api.tickCache?.findBeat(trackLookup, currentTick)?.beat
            scoreCursor.prepare(countIn, startBeat)
            // Setting the range does not seek the SDK cursor. Seek while paused so
            // count-in holds the selected beat, including a later bar/repeated visit.
            api.tickPosition = currentTick
            updateCursor()
            val bar = visits[entryVisit].masterBar
            entryBeatTotal = if (pre) bar.timeSignatureNumerator.toInt().coerceAtLeast(1) else 0
            entryGreenEndTick = currentTick + 960.0 * 4.0 / bar.timeSignatureDenominator
            if (audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                progress = progress.copy(phase = Phase.PLAYING); beginning = false
                val started = api.play()
                Log.i("AlphaPractice","play first=$first entry=$entryVisit last=$last repeats=$repeats speed=$speed pre=$pre countInVolume=${api.countInVolume} metronomeVolume=${api.metronomeVolume} ready=${api.isReadyForPlayback} accepted=$started")
                if(!started) fail(IllegalStateException("引擎尚未能开始播放，请重试"))
            } else fail(IllegalStateException("当前音频被其他应用占用，请稍后重试"))
        }
        ready = false
        AlphaNotation.withPlaybackTempo(model, sourceTempo, bpm) { api.loadMidiForScore() }
    }
    private fun updateCursor() {
        val s = session
        if (s == null) { progress = progress.copy(tick = currentTick); return }
        val local = (focus?.at?.value() ?: currentTick / 960) * s.quarter
        val visited = s.plan.visits.getOrNull(currentVisit) ?: return
        val beat = visited.measure.meter.groups.indexOfLast { it.value() <= currentTick / 960 - visited.start.value() }.coerceAtLeast(0)
        val uiTick = s.preTicks + round * s.rangeDuration.value() * s.quarter + local - s.startTick
        progress = progress.copy(tick = if (countIn) 0.0 else uiTick)
        val q=currentTick/960
        val sourceIds=if(countIn) emptySet() else if(focus!=null) focusNoteIds else cursorIntervals?.query(q,q+1e-7).orEmpty().filter { n ->
            n.track.id==s.plan.practice.mainTrackId && n.start.value()<=q &&
                (n.position in positions && n.soundEnd.value()>q || n.note.dead && n.event.end.value()>q)
        }.map { it.id }.toSet()
        cursor = Cursor(countIn, round, local, currentVisit, beat, visitId = visited.id,
            measureId = visited.sourceMeasureId,activeNoteIds=sourceIds, positions = if (countIn) emptyList() else focus?.positions ?: positions)
    }
    override fun pause() {
        if(auditioning) {
            view?.api?.pause();auditioning=false;audio.abandonAudioFocusRequest(focusRequest);return
        }
        if (!active) return
        if(progress.phase==Phase.PREPARING) { stop();return }
        stopDisplayCursor()
        callbackEpoch++
        pendingStart = null; beginning = false
        pausedTick = view?.api?.tickPosition ?: currentTick
        view?.api?.pause(); progress = progress.copy(phase = Phase.PAUSED)
        audio.abandonAudioFocusRequest(focusRequest)
    }
    override fun resume() {
        if (progress.phase != Phase.PAUSED || !ready) return
        if(focus!=null) {
            val selected=focus!!
            val c=focusContext ?: return
            session=Session(c.sequence,c.bpm,true,c.repeats,range=c.range,sourceTempo=c.sourceTempo,tempoMultiplier=c.multiplier)
            startVisits(c.range.first,c.range.last,c.repeats,true,c.sourceTempo,c.bpm,c.multiplier,selected.visit)
            return
        }
        if(auditioning) { view?.api?.stop(); auditioning=false }
        if (audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // The SDK starts a new count-in on every play() when this is nonzero.
            // Continue at its paused position without replaying the preparation bar.
            applyMix(countInOnPlay = false)
            // During preparation the SDK retains its count-in sequencer. Do not
            // seek it to a written-music tick or start another preparation bar.
            if (!countIn) {
                scoreCursor.follow()
                pausedTick?.let { view?.api?.tickPosition = it }
            }
            progress = progress.copy(phase = Phase.PLAYING)
            val started = view?.api?.play() == true
            Log.i("AlphaPractice","resume tick=$pausedTick preparing=$countIn accepted=$started")
            if(!started) fail(IllegalStateException("引擎尚未能继续播放"))
        }
    }
    override fun restart() {
        val saved = session
        if (saved != null) start(saved.sequence, saved.bpm, true, saved.repeats, saved.range, saved.sourceTempo, saved.tempoMultiplier)
    }
    override fun invalidate() { stop(); progress = Progress(message = "设置已改变，下一次播放从头开始") }
    override fun stop() {
        stopDisplayCursor()
        round=0; countIn=false; applyMix()
        callbackEpoch++
        pendingStart = null; beginning = false; auditioning = false
        pausedTick = null; focus=null; focusNoteIds=emptySet(); scoreCursor.stop()
        entryLight = 0; entryBeatNumber = 0; entryBeatTotal = 0
        view?.api?.stop(); positions = emptyList(); cursor = null; session = null
        currentTick=0.0; currentVisit=0
        progress = Progress(); audio.abandonAudioFocusRequest(focusRequest)
    }
    override fun audition(positions: List<Position>, strum: Boolean, capoFret: Int, openStringMidi: List<Int>) {
        if (active || !ready || positions.isEmpty()) return
        val preview = Sequence(listOf(Event(0, 12, positions, strum = strum)), 12, "试听",
            capoFret = capoFret, openStringMidi = openStringMidi)
        val beat = AlphaScoreAdapter.audition(preview,selectedTrack)
        if(audio.requestAudioFocus(focusRequest)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        auditioning = true
        view?.api?.playBeat(beat)
    }
    private fun rejectStart(message: String) {
        stopDisplayCursor()
        focus=null; focusNoteIds=emptySet(); countIn=false
        entryLight=0; entryBeatNumber=0; entryBeatTotal=0
        positions=emptyList(); cursor=null; scoreCursor.stop()
        progress=Progress(Phase.ERROR,message=message)
    }
    private fun fail(failure: Throwable) {
        stopDisplayCursor()
        pendingStart = null; beginning = false; ready = false; loading = false
        countIn = false; pausedTick = null; focus=null; focusNoteIds=emptySet(); scoreCursor.stop()
        entryLight = 0; entryBeatNumber = 0; entryBeatTotal = 0
        error = failure.message ?: "引擎加载失败"
        Log.e("AlphaPractice",error,failure)
        progress = Progress(Phase.ERROR, message = error); positions = emptyList(); cursor = null
        audio.abandonAudioFocusRequest(focusRequest)
    }
    override fun release() { stop(); closed = true; generation++; worker.shutdownNow(); view = null }
}
