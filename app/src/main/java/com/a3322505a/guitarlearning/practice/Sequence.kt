package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.*
import kotlin.math.roundToLong

const val QUARTER = 12
const val BAR = 48
const val SAMPLE_RATE = 44100

data class Event(val tick: Int, val duration: Int, val positions: List<Position>, val chord: String? = null, val strum: Boolean = false, val ring: Boolean = false) {
    // An onset is unique within a finite sequence. All its chord tones share this identity.
    val id get() = tick
}
data class Section(val tick: Int, val chord: String)
data class Sequence(val events: List<Event>, val length: Int, val explanation: String, val sections: List<Section> = emptyList()) {
    init {
        require(length > 0)
        require(events.map { it.id }.distinct().size == events.size)
        require(events.all { it.tick >= 0 && it.duration > 0 && it.tick + it.duration <= length })
    }
    val route get() = events.flatMap { it.positions }.distinct()
    fun eventAt(tick: Double) = events.lastOrNull { tick >= it.tick && tick < it.tick + it.duration }
    fun sectionAt(tick: Double) = sections.indexOfLast { it.tick <= tick }
    // Let-ring audio lasts until the same string is picked or the grip changes.
    // Engraved duration stays the original rhythmic value; this is not a tied onset.
    fun soundDuration(event: Event, position: Position): Int {
        if(!event.ring) return event.duration
        val stringEnd = events.firstOrNull { it.tick > event.tick && it.positions.any { p -> p.string == position.string } }?.tick ?: length
        val gripEnd = sections.firstOrNull { it.tick > event.tick }?.tick ?: length
        return minOf(stringEnd, gripEnd) - event.tick
    }
}

enum class ScaleExercise(val title: String, val instruction: String) {
    // Keep the stored ordinals: ASCEND now performs a complete round trip.
    ASCEND("音阶往返", "一轮内完整往返，转折音只弹一次。"),
    ROOTS("主音与八度", "找出主音并依次弹响，留意同名音之间的八度。"),
    CHANGE_START("换起点", "从所选级数起，循音阶上行再回到起点；不用交答案。"),
    THREE("三音模进", "1–2–3，2–3–4，3–4–5……每组三个相邻音。"),
    THIRDS("三度跳进", "调内 1–3，2–4，3–5……听三度的宽窄变化。"),
    CONNECTION("指型连接", "经两个指型共享的主音连接一条短句；按路线跨指型弹奏。"),
}

data class ScaleOptions(val pattern: Int = 0, val key: Tonality = Tonality.C_MAJOR, val exercise: ScaleExercise = ScaleExercise.ASCEND,
    val firstString: Int = 1, val lastString: Int = 6, val octave: Boolean = true, val eighth: Boolean = false,
    val startDegree: Int = 1, val reverse: Boolean = false)
data class RouteResult(val sequence: Sequence?, val reason: String = "")

object Routes {
    fun scale(o: ScaleOptions): RouteResult {
        val first = Patterns.all[o.pattern]
        val next = Patterns.all[(o.pattern + 1) % 5]
        if (o.exercise == ScaleExercise.CONNECTION && o.pattern == 4) return RouteResult(null, "re 是本次五型的最后一型；选择 mi、sol、la 或 si 连接下一型。")
        val available = (if(o.exercise == ScaleExercise.CONNECTION) first.positions + next.positions else first.positions)
            .distinct().filter { it.string in o.firstString..o.lastString }
        val ordered = available.sortedWith(compareBy<Position> { it.midi }.thenBy { it.fret }).distinctBy { it.midi }
        val roots = ordered.filter(o.key::isRoot)
        val octave = roots.firstOrNull { root -> (root.midi..root.midi+12).count { m -> ordered.any { it.midi == m } } == 8 }
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
                val allowedMidi = pool.map { it.midi }.toSet()
                val from = first.positions.filter { it.string in o.firstString..o.lastString && it.midi in allowedMidi }.sortedBy { it.midi }.distinctBy { it.midi }
                val into = next.positions.filter { it.string in o.firstString..o.lastString && it.midi in allowedMidi }.sortedBy { it.midi }.distinctBy { it.midi }
                val shared = first.positions.intersect(next.positions.toSet()).filter { it.midi in allowedMidi && it.string in o.firstString..o.lastString && o.key.isRoot(it) }
                    .firstOrNull { p ->
                        from.filter { it.midi < p.midi }.takeLast(3).any { it !in next.positions } &&
                            into.filter { it.midi > p.midi }.take(3).any { it !in first.positions }
                    }
                    ?: return RouteResult(null, "所选范围没有可跨入下一型的共享主音路线；试用整型音域或扩大弦范围。")
                val before = from.filter { it.midi < shared.midi }.takeLast(3)
                val after = into.filter { it.midi > shared.midi }.take(3)
                before + shared + after
            }
        }
        if(route.isEmpty()) return RouteResult(null, "所选范围没有这项练法需要的音；扩大范围后再试。")
        if(o.reverse && o.exercise != ScaleExercise.ASCEND) route = route.reversed()
        val step = if(o.eighth) 6 else 12
        val soundEnd = route.size * step
        val length = ((soundEnd + BAR - 1) / BAR) * BAR
        val events = route.mapIndexed { index, p -> Event(index * step, step, listOf(p)) }.toMutableList()
        if(length > soundEnd) events += Event(soundEnd, length-soundEnd, emptyList())
        val tail = if(length > soundEnd) " 尾部留 ${(length-soundEnd)/12.0} 拍，再开始下一轮。" else ""
        val scope = if(o.octave && o.exercise != ScaleExercise.ROOTS) "主音起的一八度" else "整型音域"
        val connection = if(o.exercise == ScaleExercise.CONNECTION) {
            if(o.reverse) " ${next.solfege} → ${first.solfege}。" else " ${first.solfege} → ${next.solfege}。"
        } else ""
        val stretch = if(o.pattern == 2) " la 指型三弦 4 品 B：可伸指或轻微移位。" else ""
        return RouteResult(Sequence(events, length, "${o.exercise.instruction} $scope，${o.firstString}–${o.lastString} 弦。$connection$tail$stretch"))
    }
}

enum class ChordExercise(val title: String, val instruction: String) {
    CHECK("逐弦检查", "逐根弹有效弦，听有没有闷音或杂音；X 弦不弹。"),
    CHANGE("两和弦转换", "每个和弦首拍弹一次，保持至换和弦；留意下一和弦提示。"),
    DOWN("规则下拨", "每拍下拨一次，按有效弦由低到高起音。"),
    ARPEGGIO("简单分解", "按图下方弦序弹奏，每根弦一拍；换和弦时更新低音弦。"),
}

fun chordSequence(first: Chord, second: Chord, kind: ChordExercise, beats: Int): Sequence {
    val chords = if(kind == ChordExercise.CHECK) listOf(first) else listOf(first, second)
    val events = mutableListOf<Event>()
    val sections = mutableListOf<Section>()
    var tick = 0
    for(chord in chords) {
        sections += Section(tick, chord.name)
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
    return Sequence(events, length, kind.instruction + extra, sections)
}

data class Session(val sequence: Sequence, val bpm: Int, val follow: Boolean, val repeats: Int, val preview: Boolean = false) {
    val preTicks = if(follow) BAR else 0
    val totalTicks: Long? = if(repeats == 0) null else preTicks.toLong() + sequence.length.toLong()*repeats
    fun frame(tick: Double) = (tick * SAMPLE_RATE * 60 / (bpm * QUARTER)).roundToLong()
    fun tick(frame: Long) = frame.toDouble() * bpm * QUARTER / (SAMPLE_RATE * 60)
    fun loopAt(tick: Double) = if(tick<preTicks) -1 else ((tick-preTicks)/sequence.length).toInt()
    fun localTick(tick: Double) = if(tick<preTicks) tick else (tick-preTicks)%sequence.length
    fun resumeTick(tick: Double): Double {
        if(tick < preTicks) return 0.0
        val loop = loopAt(tick)
        return preTicks + loop.toDouble()*sequence.length + (localTick(tick).toInt()/BAR)*BAR
    }
}
