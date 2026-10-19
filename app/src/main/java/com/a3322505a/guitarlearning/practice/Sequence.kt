package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.*
import kotlin.math.roundToLong
import com.a3322505a.guitarlearning.practice.compile.PerformancePlan
import com.a3322505a.guitarlearning.practice.compile.LegacySequenceAdapter

const val QUARTER = 12
const val BAR = 48

data class Event(val tick: Int, val duration: Int, val positions: List<Position>, val chord: String? = null, val strum: Boolean = false, val ring: Boolean = false,
    val velocity: Float = 1f, val accent: Boolean = false, val palmMute: Boolean = false,
    val deadStrings: Set<Int> = emptySet(), val pick: Pick = Pick.DEFAULT, val short: Boolean = false,
    val stringExpression: Map<Int,StringExpression> = emptyMap(), val vibrato: Boolean = false, val ringEndTick: Int? = null,
    val dynamicMark: String? = null) {
    // An onset is unique within a finite sequence. All its chord tones share this identity.
    val id get() = tick
}
data class Section(val tick: Int, val chord: String,val voicingId: String? = null,
    /** The selected loaded card owns its identity, grip, tuning, capo and teaching facts. */
    val sourceChord: Chord? = null)
data class Sequence(val events: List<Event>, val length: Int, val explanation: String, val sections: List<Section> = emptyList(), val beatsPerBar: Int = 4,
    val tuplets: List<Tuplet> = emptyList(), val links: List<Legato> = emptyList(), val originalBar: Int = 0, val capoFret: Int = 0,
    val barMeters: List<Int> = emptyList(), val swing: Swing = Swing.NONE,
    val ticksPerQuarter: Int = QUARTER, val sourceBars: List<Int> = emptyList(),
    val runtimePlan: PerformancePlan? = null, val openStringMidi: List<Int> = listOf(64,59,55,50,45,40),
    val catalogueOnly: Boolean = false) {
    init {
        require(length > 0)
        require(beatsPerBar in 2..6)
        require(ticksPerQuarter in listOf(12,24))
        require(capoFret in 0..12)
        require(barMeters.all { it in 2..6 } && (barMeters.isEmpty() || barMeters.sum()*ticksPerQuarter==length))
        require(sourceBars.isEmpty() || sourceBars.size==barCount && sourceBars.all { it>0 })
        require(events.map { it.id }.distinct().size == events.size)
        require(events.all { it.tick >= 0 && it.duration > 0 && it.tick + it.duration <= length })
    }
    val barTicks get() = beatsPerBar * ticksPerQuarter
    val performance: PerformancePlan by lazy {
        check(!catalogueOnly) { "曲目尚未准备" }
        runtimePlan ?: LegacySequenceAdapter.compile(this)
    }
    val barCount get() = runtimePlan?.visits?.size ?: if(barMeters.isEmpty()) (length+barTicks-1)/barTicks else barMeters.size
    val barStarts: List<Int> = runtimePlan?.let { it.visits.map { v -> (v.start.value()*QUARTER).roundToLong().toInt() }+length }
        ?: if(barMeters.isEmpty()) (0..length step barTicks).toList() else barMeters.runningFold(0) { at,beats -> at+beats*ticksPerQuarter }
    fun barNumber(bar: Int) = sourceBars.getOrElse(bar) { originalBar+bar+1 }
    fun barLabel(bar: Int) = runtimePlan?.visits?.get(bar)?.let { it.measure.label+" · 经过"+it.occurrence }
        ?: if(sourceBars.isEmpty()) "${barNumber(bar)}" else "${barNumber(bar)} · 展开${bar+1}"
    fun meter(bar: Int) = runtimePlan?.visits?.get(bar)?.measure?.meter?.beatGroups?.size ?: barMeters.getOrElse(bar) { beatsPerBar }
    fun barStart(bar: Int) = barStarts[bar]
    fun barEnd(bar: Int) = minOf(length,barStarts.getOrElse(bar+1) { length })
    fun barAt(tick: Double) = barStarts.indexOfLast { it<=tick }.coerceIn(0,barCount-1)
    val route get() = runtimePlan?.mainPositions ?: events.flatMap { it.positions }.distinct()
    fun midi(position: Position) = runtimePlan?.mainTrack?.midi(position.string,position.fret) ?: (openStringMidi[position.string-1]+position.fret+capoFret)
    fun eventAt(tick: Double) = events.lastOrNull { tick >= it.tick && tick < it.tick + it.duration }
    fun sectionAt(tick: Double) = runtimePlan?.harmonies?.indexOfLast { it.start.value()*QUARTER<=tick && (it.end==null || tick<it.end.value()*QUARTER) } ?: sections.indexOfLast { it.tick <= tick }
    fun chordAt(section: Section): Chord? {
        section.sourceChord?.let { return it.takeIf { chord -> chord.unavailableReason==null } }
        if(runtimePlan==null) return Chords.all.firstOrNull { it.name==section.chord }
        val v=runtimePlan.score.voicings.firstOrNull { it.id==section.voicingId && it.trackId==runtimePlan.practice.mainTrackId } ?: return null
        val track=runtimePlan.mainTrack
        return v.chord(track).copy(name=section.chord)
    }
    // Let-ring audio lasts until the same string is picked or the grip changes.
    // Engraved duration stays the original rhythmic value; this is not a tied onset.
    fun soundDuration(event: Event, position: Position): Int {
        val stringEnd = events.firstOrNull { it.tick > event.tick && position.string in it.strings }?.tick ?: length
        val gripEnd = sections.firstOrNull { it.tick > event.tick }?.tick ?: length
        val end=if(event.stringExpression[position.string]?.ring ?: event.ring) minOf(stringEnd,gripEnd,event.ringEndTick ?: length) else minOf(event.tick+event.duration,stringEnd,gripEnd)
        val duration=end-event.tick
        return if(event.short) minOf(duration,ticksPerQuarter/4) else duration
    }
}

enum class ScaleExercise(val title: String, val instruction: String) {
    // Keep the stored ordinals: ASCEND now performs a complete round trip.
    ASCEND("音阶往返", "一轮内完整往返，转折音只弹一次。"),
    ROOTS("主音与八度", "找出主音并依次弹响，留意同名音之间的八度。"),
    CHANGE_START("换起点", "从所选级数起，循音阶上行再回到起点；不用交答案。"),
    THREE("三音模进", "1–2–3，2–3–4，3–4–5……每组三个相邻音。"),
    THIRDS("三度跳进", "调内 1–3，2–4，3–5……听三度的宽窄变化。"),
    CONNECTION("指型连接", "经共享主音换型，进入下一型后返回；A→B→A 才是一轮。"),
}

data class ScaleOptions(val pattern: Int = 0, val key: Tonality = Tonality.C_MAJOR, val exercise: ScaleExercise = ScaleExercise.ASCEND,
    val firstString: Int = 1, val lastString: Int = 6, val octave: Boolean = true, val notesPerBeat: Int = 1,
    val startDegree: Int = 1, val reverse: Boolean = false, val pentatonic: Boolean = false) {
    init { require(notesPerBeat in 1..3) }
}
data class RouteResult(val sequence: Sequence?, val reason: String = "")

object Routes {
    fun scale(o: ScaleOptions): RouteResult {
        val patterns = Patterns.forKey(o.key,o.pentatonic)
        val first = patterns[o.pattern.coerceIn(patterns.indices)]
        val next = patterns[(o.pattern + 1) % patterns.size]
        if (o.exercise == ScaleExercise.CONNECTION && o.pattern >= patterns.lastIndex) return RouteResult(null, "这是最后一个把位；选择前一个把位连接下一型。")
        val available = (if(o.exercise == ScaleExercise.CONNECTION) first.positions + next.positions else first.positions)
            .distinct().filter { it.string in o.firstString..o.lastString }
        val ordered = available.sortedWith(compareBy<Position> { it.midi }.thenBy { it.fret }).distinctBy { it.midi }
        val roots = ordered.filter(o.key::isRoot)
        val octave = roots.firstOrNull { root -> (root.midi..root.midi+12).count { m -> ordered.any { it.midi == m } } == (if(o.pentatonic) 6 else 8) }
        if (ordered.isEmpty()) return RouteResult(null, "所选弦范围没有音位。")
        if (o.octave && o.exercise != ScaleExercise.ROOTS && octave == null) return RouteResult(null, "所选弦范围不够组成主音起的一八度；扩大弦范围或选择整型音域。")
        val pool = if(o.octave && octave != null) ordered.filter { it.midi in octave.midi..octave.midi+12 } else ordered
        var route = when(o.exercise) {
            ScaleExercise.ASCEND -> {
                val outward = if(o.reverse) pool.reversed() else pool
                outward + outward.dropLast(1).reversed()
            }
            ScaleExercise.ROOTS -> available.filter(o.key::isRoot).sortedBy { it.midi }
            ScaleExercise.CHANGE_START -> {
                val at = pool.indexOfFirst { o.key.degree(it.midi) == o.startDegree }
                if (at < 0) return RouteResult(null, "所选范围没有 ${o.startDegree} 级音；请选择其他起点。")
                pool.drop(at) + pool.take(at) + pool[at]
            }
            ScaleExercise.THREE -> pool.windowed(3).flatten()
            ScaleExercise.THIRDS -> (0 until pool.size-2).flatMap { listOf(pool[it], pool[it+2]) }
            ScaleExercise.CONNECTION -> {
                connectionPlan(o,pool.map { it.midi }.toSet())?.route
                    ?: return RouteResult(null, "所选范围没有可经共享主音往返的相邻弦路线；试用整型音域或扩大弦范围。")
            }
        }
        if(route.isEmpty()) return RouteResult(null, "所选范围没有这项练法需要的音；扩大范围后再试。")
        if(o.reverse && o.exercise != ScaleExercise.ASCEND) route = route.reversed()
        val step = QUARTER / o.notesPerBeat
        val soundEnd = route.size * step
        val length = ((soundEnd + BAR - 1) / BAR) * BAR
        val events = route.mapIndexed { index, p -> Event(index * step, step, listOf(p)) }.toMutableList()
        // Finish a partial triplet with explicit members, then retain the existing
        // whole-bar loop boundary. Remaining whole beats use ordinary rests.
        val groupEnd=if(o.notesPerBeat==3) ((soundEnd+QUARTER-1)/QUARTER)*QUARTER else soundEnd
        if(groupEnd>soundEnd) for(tick in soundEnd until groupEnd step step) events += Event(tick,step,emptyList())
        if(length > groupEnd) events += Event(groupEnd, length-groupEnd, emptyList())
        val tuplets=if(o.notesPerBeat==3) (0 until groupEnd step QUARTER).map { Tuplet(it,4) } else emptyList()
        val tail = if(length > soundEnd) " 尾部留 ${(length-soundEnd)/12.0} 拍，再开始下一轮。" else ""
        val scope = if(o.octave && o.exercise != ScaleExercise.ROOTS) "主音起的一八度" else "整型音域"
        val connection = if(o.exercise == ScaleExercise.CONNECTION) {
            " ${first.solfege} → ${next.solfege} → ${first.solfege}；共享主音后调整手型，转折音只弹一次。"
        } else ""
        return RouteResult(Sequence(events, length, "${o.exercise.instruction} $scope，${o.firstString}–${o.lastString} 弦。$connection$tail",tuplets=tuplets).also { it.validateExpression() })
    }
}

data class ConnectionPlan(val outward: List<Position>, val returning: List<Position>, val pivot: Position) {
    val route get() = outward + returning
}

/** Choose positions for each direction independently; pitch reversal alone does not choose a fingering. */
fun connectionPlan(o: ScaleOptions, allowedMidi: Set<Int>? = null): ConnectionPlan? {
    val patterns=Patterns.forKey(o.key,o.pentatonic)
    if(o.pattern !in 0 until patterns.lastIndex) return null
    val a=patterns[o.pattern.coerceIn(patterns.indices)].positions
    val b=patterns[o.pattern+1].positions
    fun eligible(p: Position)=p.string in o.firstString..o.lastString && (allowedMidi==null || p.midi in allowedMidi)
    fun paths(slots: List<List<Position>>): List<List<Position>> = slots.fold(listOf(emptyList<Position>())) { paths,slot ->
        paths.flatMap { path -> slot.filter { p -> path.isEmpty() || kotlin.math.abs(path.last().string-p.string)<=1 }
            .map { p -> path+p } }
    }
    fun cost(path: List<Position>)=path.zipWithNext().sumOf { (p,q) ->
        3*kotlin.math.abs(p.fret-q.fret)+4*kotlin.math.abs(p.string-q.string)
    }
    val plans=mutableListOf<Pair<Int,ConnectionPlan>>()
    a.intersect(b.toSet()).filter { eligible(it) && o.key.isRoot(it) }.forEach { pivot ->
        val below=a.filter { eligible(it) && it.midi<pivot.midi }.map { it.midi }.distinct().sorted().takeLast(3)
        val above=b.filter { eligible(it) && it.midi>pivot.midi }.map { it.midi }.distinct().sorted().take(3)
        if(below.isEmpty() || above.isEmpty()) return@forEach
        val pitches=below+ pivot.midi + above
        val outwardSlots=below.map { pitch -> a.filter { eligible(it) && it.midi==pitch } } +
            listOf(listOf(pivot)) + above.map { pitch -> b.filter { eligible(it) && it.midi==pitch } }
        paths(outwardSlots).filter { path -> path.take(below.size).any { it !in b } && path.drop(below.size+1).any { it !in a } }.forEach outwardLoop@ { outward ->
            val backSlots=pitches.dropLast(1).reversed().mapIndexed { index,pitch ->
                when {
                    index==pitches.size-2 -> listOf(outward.first())
                    pitch==pivot.midi -> listOf(pivot)
                    pitch>pivot.midi -> b.filter { eligible(it) && it.midi==pitch }
                    else -> a.filter { eligible(it) && it.midi==pitch }
                }
            }
            // Include the turning position in the return cost, but never repeat its onset.
            val returning=paths(listOf(listOf(outward.last()))+backSlots).minByOrNull(::cost)?.drop(1) ?: return@outwardLoop
            val plan=ConnectionPlan(outward,returning,pivot)
            plans += (cost(outward)+cost(listOf(outward.last())+returning)+(7-outward.size)*40) to plan
        }
    }
    return plans.minByOrNull { it.first }?.second
}

fun suggestedFinger(position: Position, pattern: Pattern): Int {
    pattern.fingers[position]?.let { return it }
    if(position.fret==0) return 0
    val base=if(pattern.frets.any { 0 in it }) 1 else pattern.frets[position.string-1].first { it>0 }
    return (position.fret-base+1).coerceIn(1,4)
}

enum class ChordExercise(val title: String, val instruction: String) {
    CHECK("逐弦检查", "逐根弹有效弦，听有没有闷音或杂音；X 弦不弹。"),
    CHANGE("两和弦转换", "每个和弦首拍弹一次，保持至换和弦；留意下一和弦提示。"),
    DOWN("规则下拨", "每拍下拨一次，按有效弦由低到高起音。"),
    ARPEGGIO("简单分解", "按图下方弦序弹奏，每根弦一拍；换和弦时更新低音弦。"),
}

fun chordSequence(first: Chord, second: Chord, kind: ChordExercise, beats: Int): Sequence {
    val chords = if(kind == ChordExercise.CHECK) listOf(first) else listOf(first, second)
    chords.forEach { require(it.unavailableReason==null) { it.unavailableReason!! } }
    require(chords.map { it.capoFret }.distinct().size==1) { "chords must share the same capo" }
    require(chords.map { it.openStringMidi }.distinct().size==1) { "chords must share the same tuning" }
    val events = mutableListOf<Event>()
    val sections = mutableListOf<Section>()
    var tick = 0
    for(chord in chords) {
        sections += Section(tick, chord.name,chord.id,chord)
        when(kind) {
            ChordExercise.CHECK -> chord.positions.forEach { p -> events += Event(tick, QUARTER, listOf(p), chord.name); tick += QUARTER }
            ChordExercise.CHANGE -> { events += Event(tick, beats * QUARTER, chord.positions, chord.name); tick += beats * QUARTER }
            ChordExercise.DOWN -> repeat(beats) { events += Event(tick, QUARTER, chord.positions, chord.name, true); tick += QUARTER }
            ChordExercise.ARPEGGIO -> repeat(beats) { index -> events += Event(tick, QUARTER, listOf(chord.arpeggio[index%chord.arpeggio.size]), chord.name); tick += QUARTER }
        }
    }
    val length = ((tick+BAR-1)/BAR)*BAR
    if(length > tick) events += Event(tick, length-tick, emptyList(), chords.last().name)
    val extra = if(length>tick) " 尾部留 ${(length-tick)/12.0} 拍。" else ""
    return Sequence(events, length, kind.instruction + extra, sections,capoFret=first.capoFret,openStringMidi=first.openStringMidi)
}
