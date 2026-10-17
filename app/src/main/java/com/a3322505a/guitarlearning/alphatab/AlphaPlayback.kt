@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.AlphaTabView
import alphaTab.model.*
import alphaTab.synth.PlaybackRange
import alphaTab.synth.PlayerState
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private var generation = 0
    private var callbackEpoch = 0
    private var closed = false
    private var sequence: Sequence? = null
    private var pendingStart: (() -> Unit)? = null
    private var round by mutableIntStateOf(0)
    val roundNumber get() = round + 1
    val countingIn get() = countIn
    private var rounds = 1
    private var fromVisit = 0
    private var toVisit = 0
    private var countIn = false
    private var beginning = false
    private var auditioning = false
    private var pausedTick: Double? = null
    private data class FocusContext(val sequence: Sequence, val bpm: Int, val repeats: Int, val range: BarRange,
        val sourceTempo: Boolean, val multiplier: Double)
    private var focusContext: FocusContext? = null
    private var focusIndex: PracticeFocusIndex? = null
    var focus: PracticeFocus? by mutableStateOf(null); private set
    val fromMeasure get() = focus != null
    private var focusNoteIds: Set<String> = emptySet()
    private var cursorNotes: List<PlanNote> = emptyList()
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
    fun canStep(direction: Int): Boolean {
        val c=focusContext ?: return false
        return ready && !loading && focusIndex?.step(focus?.at?.value() ?: currentTick/960,direction,c.range)!=null
    }
    fun step(direction: Int) {
        require(direction==-1 || direction==1)
        val c=focusContext ?: return
        focusIndex?.step(focus?.at?.value() ?: currentTick/960,direction,c.range)?.let(::selectFocus)
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
    fun auditionFocus() {
        val selected=focus ?: focusIndex?.let { i -> focusContext?.let {
            i.at(if(session==null) i.plan.visits[it.range.first].start.value() else currentTick/960,it.range)
        } } ?: return
        if(selected.rest) return
        val c=focusContext ?: return
        audition(selected.positions,capoFret=c.sequence.capoFret,openStringMidi=c.sequence.openStringMidi)
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
            view?.api?.metronomeVolume = if (value) AlphaTone.CLICK else 0.0
            if(sequence!=null) view?.api?.countInVolume = if(value && session?.follow==true) AlphaTone.CLICK else 0.0
        }
    var solo = false
        set(value) { field = value; applyMix() }

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
                ui { if (score === current) projection = projected }
            }
        }
        api.playerReady.on { ui { onReady() } }
        // 1.8.4 Android's loadedMidiInfo getter recurses when midiLoaded's initial
        // subscription value is evaluated. playerReady is emitted after each MIDI load.
        api.playerStateChanged.on { event -> ui {
            if (!auditioning && progress.phase !in listOf(Phase.IDLE, Phase.ENDED, Phase.ERROR)) {
                progress = progress.copy(phase = if (event.state == PlayerState.Playing) Phase.PLAYING else Phase.PAUSED)
                // 1.8.4 snaps a paused beat to its first written occurrence. Keep the
                // heard tick instead, especially on a later repeat visit in a GP file.
                if (event.state == PlayerState.Paused && !event.stopped) pausedTick?.let { api.tickPosition = it }
            }
            Log.i("AlphaPractice","state=${event.state} audition=$auditioning")
            if (auditioning && event.state == PlayerState.Paused) { auditioning = false; audio.abandonAudioFocusRequest(focusRequest) }
        } }
        api.playerPositionChanged.on { event -> ui {
            if (auditioning || beginning || progress.phase !in listOf(Phase.PLAYING, Phase.PAUSED)) return@ui
            if (event.isSeek && progress.phase == Phase.PAUSED && pausedTick?.let {
                kotlin.math.abs(event.currentTick - it) > 1e-6
            } == true) return@ui
            if (event.isSeek && progress.phase == Phase.PLAYING) return@ui
            if (!event.isSeek && progress.phase == Phase.PLAYING) pausedTick = null
            countIn = false
            scoreCursor.follow()
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
                positions = emptyList(); cursor = null
                scoreCursor.stop()
                progress = Progress(Phase.ENDED, progress.tick, "本次练习结束")
                audio.abandonAudioFocusRequest(focusRequest)
            } else {
                // Disable looping before the final pass. The engine performs every boundary.
                api.isLooping = rounds == 0 || round < rounds - 1
            }
        } }
        score?.let { render(it) }
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
                    cursorNotes=index.plan.notes.filter { it.track.id==index.plan.practice.mainTrackId };
                    cursorIntervals=IntervalIndex(cursorNotes,{ it.start.value() },{ maxOf(it.soundEnd,it.event.end).value() }) } }
            } catch (failure: Exception) { main.post { if (request == generation && !closed) fail(failure) } }
        }
    }

    fun loadScore(value: Score, trackIndex: Int) {
        generation++; stop(); sequence = null; setModel(value, trackIndex)
    }

    private fun setModel(value: Score, trackIndex: Int) {
        AlphaNotation.prepare(value)
        score = value
        modelLoads++; Log.i("AlphaPractice","modelLoad=$modelLoads")
        selectedTrack = value.tracks.firstOrNull { it.index.toInt() == trackIndex } ?: value.tracks.first()
        projection = null; ready = false; loading = false; error = ""; rendered = false
        currentTick = 0.0; currentVisit = 0; positions = emptyList()
        render(value)
    }
    private fun render(value: Score) {
        view?.let { native ->
            native.tracks = listOf(checkNotNull(selectedTrack))
            applyMix()
        }
    }
    fun selectTrack(track: Track) {
        require(track.score === score)
        stop(); selectedTrack = track; rendered = false
        view?.tracks = listOf(track); applyMix()
    }
    private fun applyMix() {
        val api = view?.api ?: return
        score?.tracks?.forEach { track ->
            api.changeTrackMute(alphaTab.collections.List(track), !guitar || solo && track !== selectedTrack)
            api.changeTrackVolume(alphaTab.collections.List(track), AlphaTone.trackVolume(track.playbackInfo.volume))
        }
        if (!guitar) positions = emptyList()
        api.masterVolume = AlphaTone.MASTER
        api.metronomeVolume = if (click) AlphaTone.CLICK else 0.0
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

    fun startGp(first: Int, last: Int, repeats: Int, pre: Boolean, multiplier: Double) {
        session = null; startVisits(first, last, repeats, pre, true, 60, multiplier)
    }
    private fun startVisits(first: Int, last: Int, repeats: Int, pre: Boolean, sourceTempo: Boolean, bpm: Int, speed: Double, entryVisit: Int = first, forceCountIn: Boolean = false) {
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
        require(repeats >= 0 && speed in 0.25..2.0)
        focus=null; focusNoteIds=emptySet()
        api.stop(); callbackEpoch++; auditioning=false; pausedTick=null; round = 0; rounds = repeats; fromVisit = first; toVisit = last
        countIn = pre; beginning = true; positions = emptyList()
        progress = Progress(Phase.PREPARING, message = "准备声音…")
        pendingStart = {
            val visits = checkNotNull(projection).visits
            api.playbackRange = PlaybackRange().apply { startTick = visits[first].start; endTick = visits[last].end }
            api.playbackSpeed = speed
            api.countInVolume = if (pre && (forceCountIn || sequence==null || click)) AlphaTone.CLICK else 0.0
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
            if (audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                progress = progress.copy(phase = Phase.PLAYING); beginning = false
                Log.i("AlphaPractice","play first=$first entry=$entryVisit last=$last repeats=$repeats speed=$speed pre=$pre")
                if(!api.play()) fail(IllegalStateException("引擎尚未能开始播放，请重试"))
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
            startVisits(c.range.first,c.range.last,c.repeats,true,c.sourceTempo,c.bpm,c.multiplier,selected.visit,true)
            return
        }
        if(auditioning) { view?.api?.stop(); auditioning=false }
        if (audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // The SDK starts a new count-in on every play() when this is nonzero.
            // Continue at its paused position without replaying the preparation bar.
            view?.api?.countInVolume = 0.0
            Log.i("AlphaPractice","resume ordinary tick=$pausedTick countIn=0")
            countIn = false; scoreCursor.follow()
            pausedTick?.let { view?.api?.tickPosition = it }
            progress = progress.copy(phase = Phase.PLAYING)
            if(view?.api?.play()!=true) fail(IllegalStateException("引擎尚未能继续播放"))
        }
    }
    override fun restart() {
        val saved = session
        if (saved != null) start(saved.sequence, saved.bpm, saved.follow, saved.repeats, saved.range, saved.sourceTempo, saved.tempoMultiplier)
        else if (score != null) startGp(fromVisit, toVisit, rounds, view?.api?.countInVolume != 0.0, view?.api?.playbackSpeed ?: 1.0)
    }
    override fun invalidate() { stop(); progress = Progress(message = "设置已改变，下一次播放从头开始") }
    override fun stop() {
        callbackEpoch++
        pendingStart = null; beginning = false; auditioning = false
        countIn = false; pausedTick = null; focus=null; focusNoteIds=emptySet(); scoreCursor.stop()
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
    fun setSpeed(multiplier: Double) { view?.api?.playbackSpeed = multiplier.coerceIn(0.25, 2.0) }
    private fun rejectStart(message: String) {
        focus=null; focusNoteIds=emptySet(); countIn=false
        positions=emptyList(); cursor=null; scoreCursor.stop()
        progress=Progress(Phase.ERROR,message=message)
    }
    private fun fail(failure: Throwable) {
        pendingStart = null; beginning = false; ready = false; loading = false
        countIn = false; pausedTick = null; focus=null; focusNoteIds=emptySet(); scoreCursor.stop()
        error = failure.message ?: "引擎加载失败"
        Log.e("AlphaPractice",error,failure)
        progress = Progress(Phase.ERROR, message = error); positions = emptyList(); cursor = null
        audio.abandonAudioFocusRequest(focusRequest)
    }
    override fun release() { stop(); closed = true; generation++; worker.shutdownNow(); view = null }
}
