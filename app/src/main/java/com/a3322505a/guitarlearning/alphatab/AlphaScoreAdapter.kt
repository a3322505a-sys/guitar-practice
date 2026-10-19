@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.Settings
import alphaTab.collections.DoubleList
import alphaTab.model.*
import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.practice.Sequence
import com.a3322505a.guitarlearning.practice.compile.PerformancePlan
import com.a3322505a.guitarlearning.practice.compile.PlanEvent
import com.a3322505a.guitarlearning.music.score.Articulation
import kotlin.math.abs

/** Source adapters end here. alphaTab owns subsequent MIDI, audio, timing and engraving. */
object AlphaScoreAdapter {
    private val sourcePlans = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Score, PerformancePlan>())
    data class Value(val duration: Duration, val dots: Int, val numerator: Int = 1, val denominator: Int = 1) {
        val quarters: Q get() = (0..dots).fold(Q.ZERO) { a, d ->
            a + Q(4, duration.value.toLong() * (1L shl d))
        } * Q(denominator.toLong(), numerator.toLong())
        fun beat() = Beat().also {
            it.duration = duration; it.dots = dots.toDouble()
            if (numerator != denominator) {
                it.tupletNumerator = numerator.toDouble(); it.tupletDenominator = denominator.toDouble()
            }
        }
    }

    fun settings() = Settings().apply {
        core.engine = "skia"
        core.enableLazyLoading = true
        player.playerMode = alphaTab.PlayerMode.EnabledSynthesizer
        player.enableCursor = true
        // Ranges are selected against engine visits, never an ambiguous repeated written bar.
        player.enableUserInteraction = false
        display.barCountPerPartial = 4.0
        // TAB keeps the written rests, stems, beams and tuplets when the score staff is hidden.
        notation.rhythmMode = alphaTab.TabRhythmMode.ShowWithBars
        listOf(alphaTab.NotationElement.ScoreTitle,alphaTab.NotationElement.ScoreSubTitle,
            alphaTab.NotationElement.ScoreArtist,alphaTab.NotationElement.ScoreAlbum,
            alphaTab.NotationElement.ScoreWords,alphaTab.NotationElement.ScoreMusic,
            alphaTab.NotationElement.ScoreCopyright).forEach { notation.elements.set(it,false) }
    }

    fun fromSequence(sequence: Sequence): Score = fromPlan(sequence.performance)

    /** One-time playback must use a loaded, unmuted channel/preset from the selected track. */
    fun audition(sequence: Sequence, selected: Track?): Beat {
        val model=fromSequence(sequence)
        val target=model.tracks.first()
        selected?.playbackInfo?.let { source ->
            target.playbackInfo.program=source.program
            target.playbackInfo.primaryChannel=source.primaryChannel
            target.playbackInfo.secondaryChannel=source.secondaryChannel
            target.playbackInfo.bank=source.bank
            target.playbackInfo.volume=source.volume
            target.playbackInfo.balance=source.balance
        }
        val beat=target.staves.first().bars.first().voices.first().beats.first()
        // finish() stamps the initial instrument on the first beat as well.
        beat.getAutomation(AutomationType.Instrument)?.value=target.playbackInfo.program
        return beat
    }

    fun fromPlan(plan: PerformancePlan): Score {
        val settings = settings()
        val score = Score().apply { title = plan.practice.title }
        require(plan.tempos.none { it.rampEnd != null }) { "渐变速度暂未迁移，请使用原资料核对" }
        require(plan.dynamics.none { it.rampEnd != null }) { "渐变力度暂未迁移，请使用原资料核对" }
        val heads = mutableMapOf<String, MutableList<Note>>()
        var previousTempo: Double? = null
        plan.visits.forEach { visit ->
            score.addMasterBar(MasterBar().apply {
                timeSignatureNumerator = visit.measure.meter.numerator.toDouble()
                timeSignatureDenominator = visit.measure.meter.denominator.toDouble()
                isAnacrusis = visit.duration != visit.measure.meter.duration
                val tempo = plan.tempos.last { it.at <= visit.start }
                val initialTempo = tempo.bpm * tempo.beatUnit.value()
                if (initialTempo != previousTempo) {
                    tempoAutomations.push(Automation.buildTempoAutomation(false, 0.0, initialTempo, 2.0))
                    previousTempo = initialTempo
                }
                plan.tempos.filter { it.at > visit.start && it.at < visit.start + visit.duration }.forEach {
                    val bpm = it.bpm * it.beatUnit.value()
                    if (bpm != previousTempo) {
                        tempoAutomations.push(Automation.buildTempoAutomation(false,
                            ((it.at - visit.start) / visit.duration).value(), bpm, 2.0))
                        previousTempo = bpm
                    }
                }
                val swing = plan.swing.firstOrNull { it.start <= visit.start && it.end > visit.start }
                if (swing != null) {
                    require(swing.ratio == listOf(2, 1) && swing.unit in listOf(Q(1, 2), Q(1, 4))) { "此摇摆比例暂未迁移" }
                    require(swing.trackIds.isEmpty() && swing.voiceIds.isEmpty()) { "局部声部摇摆暂未迁移" }
                    tripletFeel = if (swing.unit == Q(1, 2)) TripletFeel.Triplet8th else TripletFeel.Triplet16th
                }
            })
        }
        val sourceTracks = plan.score.tracks.filter { it.id in plan.practice.mixTrackIds }
        require(sourceTracks.size <= 7) { "迁移最多支持七条吉他轨" }
        sourceTracks.forEachIndexed { trackIndex, sourceTrack ->
            val track = Track().apply {
                name = sourceTrack.name
                playbackInfo.program = 24.0
                playbackInfo.primaryChannel = (trackIndex * 2).toDouble()
                playbackInfo.secondaryChannel = (trackIndex * 2 + 1).toDouble()
            }
            score.addTrack(track)
            val staff = Staff().apply {
                capo = sourceTrack.capo.toDouble()
                // The guitar staff is written one octave above its sounding pitch.
                displayTranspositionPitch = -12.0
                stringTuning = Tuning("", DoubleList(*sourceTrack.openStringMidi.map { it.toDouble() }.toDoubleArray()))
            }
            track.addStaff(staff)
            val voiceIds = plan.score.voices.filter { it.trackId == sourceTrack.id }.map { it.id }
            plan.visits.forEachIndexed { vi, visit ->
                val bar = Bar(); staff.addBar(bar)
                voiceIds.forEach { voiceId ->
                    val voice = Voice(); bar.addVoice(voice)
                    var at = visit.start
                    val spans = plan.events.filter { it.track.id == sourceTrack.id && it.voiceId == voiceId &&
                        it.start < visit.start + visit.duration && it.end > visit.start }.sortedBy { it.start }
                    spans.forEach { event ->
                        val start = maxOf(visit.start, event.start)
                        val end = minOf(visit.start + visit.duration, event.end)
                        require(start >= at) { "同声部事件重叠，不能猜测迁移" }
                        addRest(voice, start - at)
                        val groups = event.event.written.tupletGroupIds.map { id -> plan.score.tuplets.first { it.id == id } }
                        require(groups.size <= 1) { "嵌套连音暂未迁移" }
                        val preferred = Value(Duration.fromValue(event.event.written.baseDenominator.toDouble()),
                            event.event.written.dots, groups.firstOrNull()?.actual ?: 1, groups.firstOrNull()?.normal ?: 1)
                        val values = values(end - start, preferred)
                        values.forEachIndexed { piece, value ->
                            val beat = value.beat()
                            voice.addBeat(beat)
                            val art = event.event.articulation.over(Articulation.DEFAULT)
                            val notes = event.effectiveNotes
                            if(piece==0) plan.harmonies.firstOrNull { it.start==start }?.let { beat.text=it.symbol.name }
                            val picking=art.pick?.takeUnless { it=="default" } ?: notes.firstNotNullOfOrNull {
                                it.articulation.pick?.takeUnless { p -> p=="default" }
                            }
                            beat.pickStroke = when (picking) { "up" -> PickStroke.Up; "down" -> PickStroke.Down; else -> PickStroke.None }
                            notes.forEach { source ->
                                val note = Note()
                                note.string = (sourceTrack.openStringMidi.size + 1 - source.string).toDouble()
                                note.fret = source.fret.toDouble()
                                note.isDead = source.dead
                                plan.noteById[event.id+"/"+source.id]?.finger?.let { note.leftHandFinger=if(it==5) Fingers.Thumb else Fingers.fromValue(it.toDouble()) }
                                beat.addNote(note)
                                val merged = source.articulation.over(art)
                                applyArticulation(note, merged, event)
                                source.harmonic?.let {
                                    require(it.kind == "natural") { "此人工泛音暂未迁移" }
                                    note.harmonicType = HarmonicType.Natural; note.harmonicValue = it.touchFret.toDouble()
                                }
                                // Splitting exact rational durations introduces ties, never extra attacks.
                                note.isTieDestination = piece > 0 || start > event.start
                                heads.getOrPut(event.id + "/" + source.id) { mutableListOf() }.add(note)
                                if (piece == 0 && start == event.start) merged.ornament?.let { ornament ->
                                    require(ornament.notes.size == 1) { "复合装饰音暂未迁移" }
                                    val grace = ornament.notes.single()
                                    val graceBeat = Beat().apply {
                                        graceType = if (ornament.timing == "before") GraceType.BeforeBeat else GraceType.OnBeat
                                        duration = values(grace.duration).first().duration
                                    }
                                    val graceNote = Note().apply { string = note.string; fret = grace.fret.toDouble(); dynamics = note.dynamics }
                                    graceBeat.addNote(graceNote)
                                    voice.beats.pop()
                                    voice.addBeat(graceBeat); voice.addBeat(beat)
                                    if (grace.kind in listOf("hammer", "pull")) graceNote.isHammerPullOrigin = true
                                }
                            }
                            if (notes.size > 1 && notes.any { (it.articulation.strumDelayMs ?: art.strumDelayMs ?: 0.0) > 0 }) {
                                beat.brushType = if (picking == "up") BrushType.BrushUp else BrushType.BrushDown
                                val ms = notes.maxOf { it.articulation.strumDelayMs ?: art.strumDelayMs ?: 0.0 }
                                val tempo = plan.tempos.last { it.at <= start }.let { it.bpm * it.beatUnit.value() }
                                beat.brushDuration = ms * notes.size * tempo * 960 / 60000
                            }
                            if (beat.notes.length == 0.0) beat.isEmpty = false
                        }
                        at = end
                    }
                    addRest(voice, visit.start + visit.duration - at)
                    if (voice.beats.length == 0.0) addRest(voice, visit.duration)
                }
            }
        }
        plan.relations.forEach { relation ->
            val a = heads[relation.from]?.lastOrNull() ?: return@forEach
            val b = heads[relation.to]?.firstOrNull() ?: return@forEach
            when (relation.kind) {
                "tie" -> { b.isTieDestination = true; b.tieOrigin = a; a.tieDestination = b }
                "hammer", "pull", "tap" -> { a.isHammerPullOrigin = true; a.hammerPullDestination = b; b.hammerPullOrigin = a }
                "slide", "slide-pick" -> { a.slideOutType = if (relation.kind == "slide") SlideOutType.Legato else SlideOutType.Shift; a.slideTarget = b; b.slideOrigin = a }
                "slur" -> { a.slurDestination = b; b.slurOrigin = a }
                else -> error("未支持的连线：${relation.kind}")
            }
        }
        AlphaNotation.prepare(score)
        score.finish(settings)
        // Engraved time must match the accepted source route, including partial bars and tuplets.
        score.masterBars.forEachIndexed { index, master ->
            val actual = score.tracks.first().staves.first().bars[index].calculateDuration() / 960
            require(abs(actual - plan.visits[index].duration.value()) < 1e-7) {
                "第 ${index + 1} 小节迁移时值不一致：$actual / ${plan.visits[index].duration}"
            }
        }
        sourcePlans[score] = plan
        return score
    }

    /** Preserve accepted per-note gates/velocity in the MIDI about to enter the same synth.
     * GP files do not pass through this compatibility adapter. No audio clock is created. */
    fun adaptMidi(score: Score, midi: alphaTab.midi.MidiFile) {
        val plan = sourcePlans[score] ?: return
        val tracks = plan.score.tracks.filter { it.id in plan.practice.mixTrackIds }
        val byPitch=plan.notes.groupBy { it.track.id to it.note.midi }
        val connected=plan.relations.flatMap { listOf(it.from,it.to) }.toSet()
        val active = mutableMapOf<Triple<Int, Int, Int>, java.util.ArrayDeque<alphaTab.midi.NoteOnEvent>>()
        val events = midi.events.toList().sortedBy { it.tick }
        events.forEach { event ->
            if (event !is alphaTab.midi.NoteEvent) return@forEach
            val key = Triple(event.track.toInt(), event.channel.toInt(), event.noteKey.toInt())
            if (event is alphaTab.midi.NoteOnEvent && event.noteVelocity > 0) active.getOrPut(key) { java.util.ArrayDeque() }.add(event)
            else if (event is alphaTab.midi.NoteOffEvent) {
                val on = active[key]?.pollFirst() ?: return@forEach
                val sourceTrack = tracks.getOrNull(on.track.toInt()) ?: return@forEach
                val candidates = byPitch[sourceTrack.id to on.noteKey.toInt()].orEmpty().filter {
                    maxOf(it.start, it.event.activation).value() * 960 <= on.tick + 1 &&
                    on.tick < it.event.end.value() * 960 }
                val source = candidates.minByOrNull { abs(on.tick - maxOf(it.start, it.event.activation).value() * 960) } ?: return@forEach
                // Grace, bend, slides and dead notes retain the engine's own expressive MIDI.
                if (!source.note.dead && source.articulation.ornament == null && source.articulation.bend.orEmpty().isEmpty() &&
                    source.id !in connected) {
                    event.tick = maxOf(on.tick + 1, source.soundEnd.value() * 960)
                }
                on.noteVelocity = ((source.articulation.velocity ?: 1.0) * 96).toInt().coerceIn(1, 127).toDouble()
            }
        }
        midi.tracks.forEach { it.events.sort { a, b -> a.tick - b.tick } }
    }

    private fun applyArticulation(note: Note, art: Articulation, event: PlanEvent) {
        note.isPalmMute = art.palmMute == true
        note.isStaccato = art.short == true || art.cut == true
        note.isLetRing = art.letRing == true
        note.vibrato = if ((art.vibrato?.depth ?: 0.0) > 0) VibratoType.Slight else VibratoType.None
        note.dynamics = DynamicValue.fromValue(((art.velocity ?: 1.0) * 5).toInt().coerceIn(0, 7).toDouble())
        if (art.accent == true || ">" in event.notationMarks) note.accentuated = AccentuationType.Normal
        when (art.slide) {
            "out-down" -> note.slideOutType = SlideOutType.OutDown
            "out-up" -> note.slideOutType = SlideOutType.OutUp
            "in-up" -> note.slideInType = SlideInType.IntoFromBelow
            "in-down" -> note.slideInType = SlideInType.IntoFromAbove
            null, "none" -> Unit
            else -> error("未支持的滑音：${art.slide}")
        }
        art.bend.orEmpty().forEach { point ->
            note.addBendPoint(BendPoint((point.at / event.duration).value().coerceIn(0.0, 1.0) * 60, point.semitones * 2))
        }
        art.tremoloBar.orEmpty().forEach { point ->
            note.beat.addWhammyBarPoint(BendPoint((point.at / event.duration).value().coerceIn(0.0, 1.0) * 60, point.semitones * 2))
        }
        require(art.repluck == null) { "此重复拨弦暂未迁移" }
    }

    fun values(length: Q, preferred: Value? = null): List<Value> {
        if (length == Q.ZERO) return emptyList()
        require(length > Q.ZERO)
        if (preferred?.quarters == length) return listOf(preferred)
        val candidates = listOf(1 to 1, 3 to 2, 5 to 4, 7 to 4).flatMap { (n, d) ->
            listOf(1, 2, 4, 8, 16, 32, 64, 128, 256).flatMap { base ->
                (0..2).map { dots -> Value(Duration.fromValue(base.toDouble()), dots, n, d) }
            }
        }.sortedByDescending { it.quarters }
        var remaining = length
        val result = mutableListOf<Value>()
        while (remaining > Q.ZERO) {
            val value = candidates.firstOrNull { it.quarters <= remaining &&
                (remaining - it.quarters).d <= 256 } ?: error("无法无损表达时值 $length")
            result += value; remaining -= value.quarters
            require(result.size <= 2048) { "时值拆分超出上限" }
        }
        return result
    }

    private fun addRest(voice: Voice, duration: Q) {
        values(duration).forEach { voice.addBeat(it.beat().apply { isEmpty = false }) }
    }
}
