package com.a3322505a.guitarlearning.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.PerformancePlan
import com.a3322505a.guitarlearning.practice.compile.IntervalIndex
import com.a3322505a.guitarlearning.audio.Phase
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.hypot

enum class Hint(val title: String) { FULL("完整提示"), ROOTS("仅主音／根音"), HIDDEN("隐藏音位／指法"), PENTATONIC("五声骨架"), CHORD_TONES("和弦音靶心") }

private val SINGLE_INLAY_FRETS = setOf(3, 5, 7, 9, 15, 17, 19, 21)
private val DOUBLE_INLAY_FRETS = setOf(12, 24)

private fun DrawScope.label(
    measurer: TextMeasurer,
    value: String,
    x: Float,
    y: Float,
    color: Color,
    size: Int = 13,
    bold: Boolean = false,
) {
    val layout = measurer.measure(
        value,
        TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
    )
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
}

@Composable
fun Fretboard(
    positions: List<Position>,
    key: Tonality?,
    hint: Hint,
    degrees: Boolean,
    current: List<Position>,
    enabled: Boolean,
    height: androidx.compose.ui.unit.Dp = 238.dp,
    onTap: (Position) -> Unit,
    moving: MovingPosition? = null,
    upcoming: List<Position> = emptyList(),
    showLegend: Boolean = true,
    capoFret: Int = 0,
    openStringMidi: List<Int> = Music.openMidi.toList(),
    movingPositions: List<MovingPosition> = moving?.let { listOf(it) }.orEmpty(),
    fretWindow: IntRange? = null,
    subtleContext: Boolean = false,
    reserveOpenString: Boolean = false,
    noteLabels: Map<Position, String> = emptyMap(),
    staticFocus: Boolean = false,
) {
    val teaching = LocalBoardTeaching.current
    if (positions.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("参照谱面演奏", color = PracticeSkin.colors.textSecondary)
        }
        return
    }
    if (fretWindow == null && positions.isNotEmpty() && positions.all { it.fret == 0 }) {
        OpenStringBoard(positions, key, hint, current, upcoming, enabled, onTap, capoFret, openStringMidi)
        return
    }
    val targetRange = fretWindow ?: (positions.filter { it.fret>0 }.minOfOrNull { it.fret } ?: 0)..positions.maxOf { it.fret }
    val expandedPositions = if(hint==Hint.CHORD_TONES && !teaching.chordTones.isNullOrEmpty())
        (positions + (1..6).flatMap { string -> targetRange.mapNotNull { fret ->
            Position(string,fret).takeIf { (openStringMidi[string-1]+fret+capoFret)%12 in teaching.chordTones.orEmpty() }
        } }).distinct() else positions
    val visible = when (hint) {
        Hint.FULL, Hint.PENTATONIC, Hint.CHORD_TONES -> expandedPositions
        Hint.ROOTS -> positions.filter { p -> (openStringMidi[p.string-1]+p.fret+capoFret)%12 == (key?.root ?: teaching.root) }
        Hint.HIDDEN -> emptyList()
    }.filter { fretWindow == null || it.fret == 0 || it.fret in fretWindow }
    val hasOpen = reserveOpenString || positions.any { it.fret == 0 }
    val low = fretWindow?.first ?: positions.filter { it.fret > 0 }.minOfOrNull { it.fret }?.let { if (hasOpen) 1 else it } ?: 1
    val high = fretWindow?.last ?: positions.maxOf { it.fret }.coerceAtLeast(low)
    val columns = (high - low + 1).coerceAtLeast(1)
    val animatedLow by animateFloatAsState(low.toFloat(), tween(if (fretWindow == null) 0 else 180), label = "fret-window-start")
    val touchLow by rememberUpdatedState(animatedLow)
    val measurer = rememberTextMeasurer()
    val diagramHeight = height
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxOf(maxWidth, (columns * 36 + 56 + if (hasOpen) 36 else 0).dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier.width(width).height(diagramHeight).clipToBounds().testTag("fretboard").semantics {
                    contentDescription = if (hint == Hint.HIDDEN) "指板，提示已隐藏" else buildString {
                        append("指板，上为 1 弦，下为 6 弦。")
                        if (capoFret > 0) append("变调夹${capoFret}品，品位相对变调夹，音名为实际发声。")
                        current.filter { it in visible }.forEach { append("当前 ${it.string} 弦 ${it.fret} 品；") }
                        upcoming.filter { it in visible }.forEach { append("下一 ${it.string} 弦 ${it.fret} 品；") }
                        movingPositions.forEach { move ->
                            append(when (move.mark) { "/", "\\" -> "滑音"; "h" -> "击弦"; "p" -> "勾弦"; else -> "技巧" })
                            append(" ${move.source.fret}${move.mark}${move.target.fret}；")
                        }
                    }
                }.pointerInput(visible, enabled, onTap) {
                    if (enabled) detectTapGestures { tap ->
                        val left = 46.dp.toPx()
                        val gridLeft = left + if (hasOpen) 36.dp.toPx() else 0f
                        val cell = (size.width - gridLeft - 10.dp.toPx()) / columns
                        fun x(fret: Int) = if (fret == 0) left + 18.dp.toPx() else gridLeft + (fret - touchLow + .5f) * cell
                        val vertical = size.height / 238.dp.toPx()
                        val top = 40.dp.toPx() * vertical
                        val row = 32.dp.toPx() * vertical
                        visible.minByOrNull { p -> hypot((tap.x - x(p.fret)).toDouble(), (tap.y - (top + (p.string - 1) * row)).toDouble()) }
                            ?.let { p ->
                                if (hypot((tap.x - x(p.fret)).toDouble(), (tap.y - (top + (p.string - 1) * row)).toDouble()) < 23.dp.toPx()) onTap(p)
                            }
                    }
                },
            ) {
                val left = 46.dp.toPx()
                val gridLeft = left + if (hasOpen) 36.dp.toPx() else 0f
                val cell = (size.width - gridLeft - 10.dp.toPx()) / columns
                fun x(fret: Float) = if (fret == 0f) left + 18.dp.toPx() else gridLeft + (fret - animatedLow + .5f) * cell
                val vertical = size.height / 238.dp.toPx()
                val top = 40.dp.toPx() * vertical
                val row = 32.dp.toPx() * vertical
                drawRoundRect(
                    colors.fretboardWood,
                    Offset(left, top - 12.dp.toPx() * vertical),
                    androidx.compose.ui.geometry.Size(size.width - left, 5 * row + 24.dp.toPx() * vertical),
                    androidx.compose.ui.geometry.CornerRadius(metrics.cornerSmall.toPx()),
                )
                for (fretNumber in floor(animatedLow).toInt()..ceil(animatedLow + columns - 1).toInt()) {
                    val cx = gridLeft + (fretNumber - animatedLow + 0.5f) * cell
                    if (fretNumber in SINGLE_INLAY_FRETS) {
                        drawCircle(colors.fretboardInlay, 4.5.dp.toPx() * vertical, Offset(cx, top + 2.5f * row))
                    } else if (fretNumber in DOUBLE_INLAY_FRETS) {
                        drawCircle(colors.fretboardInlay, 4.dp.toPx() * vertical, Offset(cx, top + 1.5f * row))
                        drawCircle(colors.fretboardInlay, 4.dp.toPx() * vertical, Offset(cx, top + 3.5f * row))
                    }
                }
                for (fretNumber in floor(animatedLow).toInt()..ceil(animatedLow + columns).toInt()) {
                    drawLine(
                        colors.fretboardWire,
                        Offset(gridLeft + (fretNumber - animatedLow) * cell, top - 12.dp.toPx() * vertical),
                        Offset(gridLeft + (fretNumber - animatedLow) * cell, top + 5 * row + 12.dp.toPx() * vertical),
                        metrics.borderWidth.toPx(),
                    )
                }
                for (fretNumber in floor(animatedLow).toInt()..ceil(animatedLow + columns - 1).toInt()) {
                    val isMarker = fretNumber in SINGLE_INLAY_FRETS || fretNumber in DOUBLE_INLAY_FRETS
                    label(
                        measurer,
                        "$fretNumber",
                        gridLeft + (fretNumber - animatedLow + 0.5f) * cell,
                        16.dp.toPx() * vertical,
                        if (isMarker) colors.primaryText else colors.textSecondary,
                        bold = isMarker,
                    )
                }
                if (hasOpen) {
                    label(measurer, "O", x(0f), 16.dp.toPx() * vertical, colors.textPrimary, bold = true)
                    drawLine(
                        colors.textPrimary,
                        Offset(gridLeft, top - 12.dp.toPx() * vertical),
                        Offset(gridLeft, top + 5 * row + 12.dp.toPx() * vertical),
                        (metrics.borderWidth * 3).toPx(),
                    )
                }
                for (s in 1..6) {
                    val y = top + (s - 1) * row
                    drawLine(colors.fretboardWire, Offset(left, y), Offset(size.width - 10.dp.toPx(), y), (0.45 + s * 0.13).dp.toPx())
                    label(measurer, "$s ${Music.name(openStringMidi[s - 1] + capoFret)}", 21.dp.toPx(), y, colors.textSecondary, size = 12)
                }
                for (p in visible) {
                    val center = Offset(x(p.fret.toFloat()), top + (p.string - 1) * row)
                    val root = (openStringMidi[p.string-1]+p.fret+capoFret)%12 == (if(hint==Hint.CHORD_TONES) teaching.emphasisRoot(hint) else key?.root ?: teaching.root)
                    val active = p in current
                    val emphasized = teaching.emphasized(hint,openStringMidi[p.string-1]+p.fret+capoFret)
                    val midi = openStringMidi[p.string - 1] + p.fret + capoFret
                    val characteristic = teaching.key?.scale == ScaleDefinition.BLUES && Math.floorMod(midi - teaching.key.tonic.pitchClass, 12) == 6
                    val fill = when {
                        root -> colors.noteRoot
                        characteristic -> colors.noteCharacteristic
                        midi % 12 in teaching.chordTones -> colors.noteChordTone
                        else -> colors.noteScalePassing
                    }
                    val ink = if (subtleContext && !active && !root) colors.noteInkLight else if (fill.luminance() > .18f) colors.noteInkDark else colors.noteInkLight
                    val radius = (if (root || active) 13.5f else 12.5f).dp.toPx() * vertical
                    drawCircle(
                        if (!root && !active && (!emphasized || subtleContext)) fill.copy(alpha = .35f) else fill,
                        radius,
                        center,
                    )
                    if (!active && !root) {
                        drawCircle(colors.fretboardNoteStroke, radius, center, style = Stroke((metrics.borderWidth * 1.4f).toPx()))
                    } else if (root && !active) {
                        drawCircle(colors.noteRoot, 16.dp.toPx() * vertical, center, style = Stroke((metrics.borderWidth * 2.5f).toPx()))
                    }
                    if (active) {
                        drawCircle(colors.fretboardWire, 16.5.dp.toPx() * vertical, center, style = Stroke((metrics.borderWidth * 2).toPx()))
                    }
                    if (p in upcoming && !active) {
                        drawCircle(colors.primary, 19.dp.toPx() * vertical, center, style = Stroke(metrics.borderWidth.toPx()))
                    }
                    if (!subtleContext || active || p in upcoming) label(
                        measurer,
                        if (degrees && key != null) key.degreeLabel(openStringMidi[p.string - 1] + p.fret + capoFret)
                        else noteLabels[p] ?: (key?.context ?: teaching.key)?.noteName(openStringMidi[p.string - 1] + p.fret + capoFret) ?: Music.name(openStringMidi[p.string - 1] + p.fret + capoFret),
                        center.x,
                        center.y,
                        ink.copy(alpha = if (emphasized || active || root) 1f else .6f),
                        bold = root || active,
                    )
                }
                movingPositions.forEachIndexed { moveIndex, movingItem ->
                    if (hint != Hint.HIDDEN) {
                        val y = top + (movingItem.source.string - 1) * row
                        val a = x(movingItem.source.fret.toFloat())
                        val b = x(movingItem.target.fret.toFloat())
                        drawLine(colors.primary, Offset(a, y), Offset(b, y), (metrics.borderWidth * 3).toPx())
                        val movingX = if (hasOpen && movingItem.fret < 1) x(0f) + (x(1f) - x(0f)) * movingItem.fret else x(movingItem.fret.toFloat())
                        drawCircle(colors.fretboardNoteActive, 9.dp.toPx(), Offset(movingX, y))
                        label(
                            measurer,
                            "${movingItem.source.fret}${movingItem.mark}${movingItem.target.fret}",
                            (moveIndex + 1) * size.width / (movingPositions.size + 1),
                            205.dp.toPx() * vertical,
                            color = colors.primaryText,
                            bold = true,
                        )
                    }
                }
                if (showLegend) label(measurer, "上为 1 弦，下为 6 弦 · 数字为品位", size.width / 2, 225.dp.toPx() * vertical, colors.textSecondary, size = 12)
            }
        }
    }
}

/** Open strings have no fret grid. Reserve six compact, labeled audition targets. */
@Composable
private fun OpenStringBoard(
    positions: List<Position>,
    key: Tonality?,
    hint: Hint,
    current: List<Position>,
    upcoming: List<Position>,
    enabled: Boolean,
    onTap: (Position) -> Unit,
    capoFret: Int = 0,
    openStringMidi: List<Int> = Music.openMidi.toList(),
) {
    val teaching = LocalBoardTeaching.current
    val visible = when (hint) {
        Hint.FULL, Hint.PENTATONIC, Hint.CHORD_TONES -> positions
        Hint.ROOTS -> positions.filter { p -> (openStringMidi[p.string-1]+p.fret+capoFret)%12 == (key?.root ?: teaching.root) }
        Hint.HIDDEN -> emptyList()
    }
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    Column(
        Modifier.fillMaxWidth().testTag("fretboard").semantics {
            contentDescription = if (hint == Hint.HIDDEN) "指板，提示已隐藏" else buildString {
                append("空弦指板，0 品，无需按品。左为 6 弦，右为 1 弦。")
                current.filter { it in visible }.forEach { append("当前 ${it.string} 弦 0 品；") }
                upcoming.filter { it in visible }.forEach { append("下一 ${it.string} 弦 0 品；") }
            }
        }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (hint == Hint.HIDDEN) "按节拍演奏" else "空弦 · 无需按品", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Row(Modifier.widthIn(max = 600.dp).fillMaxWidth().testTag("open-string-board"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (string in 6 downTo 1) {
                val position = Position(string, 0)
                val shown = position in visible
                val active = shown && position in current
                val next = shown && position in upcoming
                Surface(
                    onClick = { onTap(position) },
                    enabled = enabled && shown,
                    modifier = Modifier.weight(1f).height(76.dp * fontScale).testTag("open-string-$string"),
                    shape = RoundedCornerShape(metrics.cornerSmall),
                    color = if (active) colors.fretboardNoteActive else if (shown) colors.primaryContainer else colors.surfaceSecondary,
                    border = BorderStroke(if (active || next) 2.dp else metrics.borderWidth, if (active || next) colors.primary else colors.border),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        val ink = if (active) colors.onFretboardNoteActive else colors.textPrimary
                        Text("$string 弦", style = MaterialTheme.typography.labelSmall, color = if (active) ink else colors.textSecondary)
                        Text(
                            if (shown) Music.name(openStringMidi[position.string - 1] + position.fret + capoFret, true) else "—",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (shown) ink else colors.textSecondary,
                        )
                        Text(if (shown) "O" else "", style = MaterialTheme.typography.labelSmall, color = if (active) ink else colors.primaryText)
                    }
                }
            }
        }
    }
}

/** Shift only when a required fretted note leaves the window; leave room after a shift. */
internal fun stableFretWindow(previous: IntRange, requiredFrets: List<Int>): IntRange {
    val required = requiredFrets.filter { it in 1..36 }
    if (required.isEmpty() || required.all { it in previous }) return previous
    val low = required.min()
    val high = required.max()
    val span = high - low + 1
    val columns = maxOf(7, previous.count(), span + 2).coerceAtMost(36)
    val first = (low - (columns - span) / 2).coerceIn(1, 37 - columns)
    return first..(first + columns - 1)
}

internal data class BoardTarget(val at: Double, val positions: List<Position>, val rest: Boolean, val dead: Boolean)

/** Immutable display index: it never advances audio time or changes accepted source notes. */
internal class LessonBoardIndex(private val plan: PerformancePlan) {
    private val notes = plan.notes.filter { it.track.id == plan.practice.mainTrackId && !it.note.dead }
    private val intervals = IntervalIndex(notes, { it.start.value() }, { maxOf(it.soundEnd, it.event.end).value() })
    private val tiedTargets = plan.relations.filter { it.kind == "tie" }.map { it.to }.toSet()
    private val byEvent = notes.groupBy { it.event.id }
    private val byOnset = notes.groupBy { it.event.activation.value() }
    private val techniques = plan.relations.mapNotNull { relation ->
        if (relation.kind !in listOf("slide", "slide-pick", "hammer", "pull", "tap")) return@mapNotNull null
        val from = plan.noteById[relation.from] ?: return@mapNotNull null
        val to = plan.noteById[relation.to] ?: return@mapNotNull null
        if (from.track.id != plan.practice.mainTrackId || to.track.id != from.track.id ||
            from.position.string != to.position.string || from.note.dead || to.note.dead || to.start <= from.start) return@mapNotNull null
        Triple(from, to, when (relation.kind) { "hammer" -> "h"; "pull" -> "p"; "tap" -> "T"; else -> if (to.position.fret >= from.position.fret) "/" else "\\" })
    }
    private val techniqueIntervals = IntervalIndex(techniques, { it.first.start.value() }, { it.second.start.value() })
    fun techniquesAt(q: Double): List<MovingPosition> = techniqueIntervals.query(q, q + 1e-9).map { (from, to, mark) ->
        val fraction = if (mark == "/" || mark == "\\") ((q - from.start.value()) / (to.start - from.start).value()).toFloat() else 0f
        MovingPosition(from.position, to.position, from.position.fret + (to.position.fret - from.position.fret) * fraction, mark)
    }
    val targets = plan.events.filter { it.track.id == plan.practice.mainTrackId }
        .groupBy { it.activation.value() }.toSortedMap().map { (at, events) ->
            BoardTarget(at, events.flatMap { byEvent[it.id].orEmpty() }.filter { it.id !in tiedTargets }.map { it.position }.distinct(), events.all { it.effectiveNotes.isEmpty() }, events.flatMap { it.effectiveNotes }.let { it.isNotEmpty() && it.all { note -> note.dead } })
        }
    private val onsets = targets.filter { it.positions.isNotEmpty() }
    val hasOpen = notes.any { it.position.fret == 0 }
    val labels = notes.groupBy { it.position }.mapValues { (position, values) ->
        val pitches = values.map { it.note.harmonic?.soundMidi ?: it.note.midi }.distinct()
        if (pitches.size == 1) Music.name(pitches.single(), true) else "${position.fret}品"
    }
    fun labelsAt(q: Double, next: BoardTarget?, mode: String, root: Int?, keyContext: KeyContext? = null): Map<Position,String> {
        val heads=intervals.query(q,q+1e-7)+next?.let { t -> byOnset[t.at].orEmpty() }.orEmpty()
        return heads.groupBy { it.position }.mapValues { (position,values) ->
            when {
                mode=="fingers" && position.fret==0 -> "O"
                mode=="fingers" && values.map { it.finger }.distinct().singleOrNull()!=null -> if(values.first().finger==5) "T" else values.first().finger.toString()
                mode=="degrees" && root!=null -> keyContext?.degree(values.first().note.midi) ?: listOf("1","♭2","2","♭3","3","4","♯4","5","♭6","6","♭7","7")[Math.floorMod(values.first().note.midi-root,12)]
                else -> values.map { val midi=it.note.harmonic?.soundMidi ?: it.note.midi; (keyContext?.noteName(midi) ?: Music.name(midi))+(midi/12-1) }.distinct().singleOrNull() ?: "${position.fret}品"
            }
        }
    }
    fun pool(visit: Int): List<Position> {
        val v = plan.visits.getOrNull(visit) ?: return emptyList()
        return intervals.query(v.start.value(), (v.start + v.duration).value()).map { it.position }.distinct()
    }
    fun targetAt(q: Double): BoardTarget? {
        var lo = 0
        var hi = targets.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (targets[mid].at <= q + 1e-8) lo = mid + 1 else hi = mid
        }
        return targets.getOrNull(lo - 1)
    }
    fun next(q: Double, first: Double, end: Double, loop: Boolean): Pair<BoardTarget?, Boolean> {
        fun after(value: Double, inclusive: Boolean): Int {
            var lo = 0
            var hi = onsets.size
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (onsets[mid].at < value || (!inclusive && onsets[mid].at <= value + 1e-8)) lo = mid + 1 else hi = mid
            }
            return lo
        }
        val next = onsets.getOrNull(maxOf(after(q, false), after(first, true)))?.takeIf { it.at < end }
        if (next != null) return next to false
        val head = if (loop) onsets.getOrNull(after(first, true))?.takeIf { it.at < end } else null
        return head to (head != null)
    }
}

@Composable
internal fun LinkedFretboardDock(
    sequence: Sequence,
    cursor: Cursor?,
    phase: Phase,
    current: List<Position>,
    range: BarRange,
    onTap: (Position) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
    loopContinues: Boolean = false,
    labelRoot: Int? = null,
    labelMode: String = "notes",
) {
    val plan = remember(sequence) { sequence.performance }
    val index = remember(plan) { LessonBoardIndex(plan) }
    val visit = (cursor?.bar ?: range.first).coerceIn(range.first, range.last)
    val q = cursor?.localTick?.div(sequence.ticksPerQuarter) ?: plan.visits[range.first].start.value()
    val first = plan.visits[range.first].start.value()
    val end = plan.visits[range.last].let { (it.start + it.duration).value() }
    val target = index.targetAt(q)
    val next = if (cursor?.pre == true || phase == Phase.ENDED || phase == Phase.ERROR) null to false else index.next(q, first, end, loopContinues)
    val pool = remember(index, visit) { index.pool(visit) }
    val active = when {
        cursor?.pre == true || phase == Phase.ENDED || phase == Phase.ERROR -> emptyList()
        cursor != null -> if(phase==Phase.PAUSED) current.ifEmpty { target?.positions.orEmpty() } else current
        else -> target?.positions.orEmpty()
    }
    val upcoming = next.first?.positions.orEmpty()
    val required = (active + upcoming).map { it.fret }.distinct().sorted()
    var window by remember(sequence) { mutableStateOf(stableFretWindow(1..7, required)) }
    LaunchedEffect(required) { window = stableFretWindow(window, required) }
    val positions = (pool + active + upcoming).distinct()
    val noteLabels=index.labelsAt(q,next.first,labelMode,labelRoot,LocalBoardTeaching.current.key)
    val techniques = if (cursor?.pre == true || phase == Phase.ENDED || phase == Phase.ERROR) emptyList()
        else cursor?.movingPositions?.takeIf { it.isNotEmpty() } ?: index.techniquesAt(q)
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                Fretboard(
                    positions = positions,
                    key = null,
                    hint = Hint.FULL,
                    degrees = false,
                    current = active,
                    enabled = enabled,
                    height = maxHeight.coerceAtLeast(1.dp),
                    onTap = onTap,
                    upcoming = upcoming,
                    showLegend = false,
                    capoFret = sequence.capoFret,
                    openStringMidi = sequence.runtimePlan?.mainTrack?.openStringMidi ?: sequence.openStringMidi,
                    fretWindow = window,
                    subtleContext = true,
                    reserveOpenString = index.hasOpen,
                    noteLabels = noteLabels,
                    staticFocus = phase==Phase.PAUSED,
                    movingPositions = techniques,
                )
            }
        }
    }
}
