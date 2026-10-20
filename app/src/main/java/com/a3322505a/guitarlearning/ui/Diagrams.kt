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
    if (positions.isNotEmpty() && positions.all { it.fret == 0 }) {
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
                    val ink = if (fill.luminance() > .18f) colors.noteInkDark else colors.noteInkLight
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

@Composable
fun ChordDiagram(
    chord: Chord,
    hint: Hint,
    current: List<Position>,
    enabled: Boolean,
    height: androidx.compose.ui.unit.Dp = 300.dp,
    onTap: (Position) -> Unit,
) {
    chord.unavailableReason?.let { Text(it, color = PracticeSkin.colors.danger); return }
    val measurer = rememberTextMeasurer()
    val diagramHeight = height
    val teaching = LocalBoardTeaching.current
    val shownRoot = if(hint==Hint.CHORD_TONES) teaching.chord.root else if(hint==Hint.ROOTS) teaching.key?.tonic?.pitchClass ?: if(teaching.reference) teaching.chord.root else chord.root else chord.root
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    val barres = remember(chord, hint) { if (hint == Hint.FULL) deriveBarreSegments(chord) else emptyList() }
    val visible = when (hint) {
        Hint.FULL, Hint.CHORD_TONES, Hint.PENTATONIC -> chord.positions
        Hint.ROOTS -> chord.positions.filter { chord.midi(it)%12 == shownRoot }
        Hint.HIDDEN -> emptyList()
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(diagramHeight)) {
    Canvas(
        Modifier.fillMaxSize().testTag("chord-diagram").semantics {
            contentDescription = if (hint == Hint.HIDDEN) "和弦图，提示已隐藏" else buildString {
                append("${chord.name}，${chord.conditions}，起始相对品位${chord.baseFret}。")
                visible.forEach { append("${it.string} 弦 ${it.fret} 品，${chord.noteName(it, true)} ${chord.role(it)}；") }
                if (hint == Hint.FULL) {
                    append("指法数字：1 食指，2 中指，3 无名指，4 小指。")
                    barres.forEach { append(it.description + "。") }
                }
            }
        }.pointerInput(chord, hint, enabled, onTap) {
            if (enabled) detectTapGestures { tap ->
                val geometry = ChordGeometry(size.width.toFloat(), size.height.toFloat(), chord.baseFret)
                val stringIndex = (0..5).minBy { kotlin.math.abs(tap.x - (geometry.left + it * geometry.stringStep)) }
                visible.firstOrNull { it.string == 6 - stringIndex }?.let { p ->
                    if ((tap - geometry.position(p)).getDistance() <= maxOf(22.dp.toPx(), geometry.unit * 20)) onTap(p)
                }
            }
        },
    ) {
        val geometry = ChordGeometry(size.width, size.height, chord.baseFret)
        val left = geometry.left
        val step = geometry.stringStep
        val top = geometry.top
        val fretStep = geometry.fretStep
        val right = left + 5 * step
        // One scale for both axes: the grid stays square, even inside a wide card.
        drawRoundRect(
            colors.diagramSurface,
            Offset(left, top),
            androidx.compose.ui.geometry.Size(5 * step, 4 * fretStep),
            androidx.compose.ui.geometry.CornerRadius(metrics.cornerSmall.toPx().coerceAtMost(5 * geometry.unit)),
        )
        for (i in 0..4) {
            drawLine(
                if (i == 0 && chord.baseFret == 1) colors.textPrimary else colors.diagramGrid,
                Offset(left, top + i * fretStep),
                Offset(right, top + i * fretStep),
                if (i == 0 && chord.baseFret == 1) (metrics.borderWidth * 4).toPx() else metrics.borderWidth.toPx(),
            )
        }
        for (i in 0..5) {
            val x = left + i * step
            drawLine(colors.diagramGrid, Offset(x, top), Offset(x, top + 4 * fretStep), (1.1 - i * 0.11).dp.toPx())
            label(measurer, "${6 - i}", x, geometry.stringY, colors.textSecondary, size = 12)
            if (hint == Hint.FULL) {
                label(measurer, chord.label(i), x, geometry.noteY, colors.textPrimary, size = 13)
                if (chord.frets[i] == null) label(measurer, "X", x, geometry.openY, color = colors.textSecondary, bold = true)
            }
        }
        barres.forEach { barre ->
            drawLine(colors.noteChordTone,
                geometry.position(Position(barre.fromString, barre.fret)),
                geometry.position(Position(barre.toString, barre.fret)),
                strokeWidth = 23 * geometry.unit, cap = StrokeCap.Round)
        }
        for (p in visible) {
            val center = geometry.position(p)
            val root = chord.midi(p)%12 == shownRoot
            val active = p in current
            val emphasized = teaching.emphasized(hint, chord.midi(p))
            val toneAlpha = if(emphasized || active) 1f else .22f
            drawCircle(
                (if (active) colors.fretboardNoteActive else if (root) colors.fretboardRoot else colors.fretboardNoteNormal).copy(alpha=toneAlpha),
                13 * geometry.unit,
                center,
            )
            if (!active && !root) {
                drawCircle(colors.fretboardNoteStroke.copy(alpha=toneAlpha), 13 * geometry.unit, center, style = Stroke((metrics.borderWidth * 1.4f).toPx()))
            } else if (root && !active) {
                drawCircle(colors.noteRoot, 15.5f * geometry.unit, center, style = Stroke((metrics.borderWidth * 1.5f).toPx()))
            }
            label(
                measurer,
                if (hint == Hint.ROOTS) (if(teaching.key!=null) "1" else "根") else if (p.fret == 0) "O" else chord.fingers[6 - p.string].takeIf { it in 1..5 }?.let { if(it==5) "T" else it.toString() } ?: chord.noteName(p,true),
                center.x,
                center.y,
                (if (active) colors.onFretboardNoteActive else if (root) colors.onFretboardRoot else colors.onFretboardNoteNormal).copy(alpha=toneAlpha),
                bold = true,
            )
            if (active) drawCircle(colors.textPrimary, 17 * geometry.unit, center, style = Stroke((metrics.borderWidth * 2).toPx()))
        }
        for (i in 1..4) label(measurer, "${chord.baseFret + i - 1}", left - 23 * geometry.unit, top + (i - 0.5f) * fretStep, colors.textSecondary, size = 12)
    }
    // Use existing blank space only: the Canvas, geometry and touch area keep their full size.
    val density = LocalDensity.current
    val geometry = with(density) { ChordGeometry(maxWidth.toPx(), maxHeight.toPx(), chord.baseFret) }
    val blankTop = with(density) { (geometry.openY - 17 * geometry.unit).toDp() }
    if (hint == Hint.FULL && blankTop >= 14.dp * density.fontScale) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("指法：1食 · 2中 · 3无 · 4小"+if(5 in chord.fingers) " · T拇" else "", fontSize = 10.sp, lineHeight = 12.sp,
                color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (barres.isNotEmpty() && blankTop >= 28.dp * density.fontScale) {
                Text(barres.joinToString("；") { it.caption }, fontSize = 10.sp, lineHeight = 12.sp,
                    color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    }
}

internal data class BarreSegment(val finger: Int, val fret: Int, val fromString: Int, val toString: Int) {
    private val fingerName get() = listOf("", "食指", "中指", "无名指", "小指")[finger]
    val description get() = "${fingerName}在${fret}品横按${fromString}弦至${toString}弦"
    val caption get() = "${fingerName}横按 · ${fret}品 · ${fromString}～${toString}弦"
}

/** A barre is display evidence from a complete grip, never an inference from its chord name. */
internal fun deriveBarreSegments(chord: Chord): List<BarreSegment> {
    if (chord.unavailableReason != null) return emptyList()
    return chord.barres.filter { b ->
        listOf(b.fromString,b.toString).all { chord.frets[6-it]==b.fret && chord.fingers[6-it]==b.finger } &&
            (b.toString..b.fromString).all { string ->
                val fret=chord.frets[6-string]
                fret!=null && fret>=b.fret && (fret!=b.fret || chord.fingers[6-string]==b.finger)
            }
    }.map { BarreSegment(it.finger,it.fret,it.fromString,it.toString) }
}

internal class ChordGeometry(width: Float, height: Float, private val baseFret: Int = 1) {
    val unit = minOf(width / 260f, height / 300f)
    val stringStep = 36 * unit
    val fretStep = 45 * unit
    val left = width / 2 - 90 * unit
    val top = (height - 300 * unit) / 2 + 54 * unit
    val openY = top - 26 * unit
    val noteY = top + 207 * unit
    val stringY = top + 236 * unit
    fun position(p: Position) = Offset(left + (6 - p.string) * stringStep, if (p.fret == 0) openY else top + (p.fret - baseFret + .5f) * fretStep)
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
internal fun BoardLabelMenu(
    hint: Hint,
    onHint: (() -> Unit)?,
    title: String = "提示",
    status: String? = null,
    labels: String? = null,
    labelRoot: Int? = null,
    onLabel: ((String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var referencePicker by remember { mutableStateOf(false) }
    val teaching = LocalBoardTeaching.current
    val selectHint = LocalSelectHint.current
    val selectReference = LocalSelectReferenceChord.current
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("board-label-menu"),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (status != null) Text(status, style = MaterialTheme.typography.labelSmall, color = PracticeSkin.colors.textSecondary, maxLines = 1)
                Text((if (hint == Hint.FULL) title else teaching.title(hint)) + " ▾", maxLines = 1)
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            if (labels != null && onLabel != null) {
                listOf("fingers" to "可靠指法", "notes" to "实际音名", "degrees" to (if(teaching.key!=null) "调内音级 · ${teaching.key.tonic.name}" else "和弦内音程")).forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, enabled = id != "degrees" || labelRoot != null, onClick = {
                        onLabel(id)
                        expanded = false
                    })
                }
            }
            teaching.available.forEach { mode ->
                DropdownMenuItem(text = { Text((if(mode==hint) "✓ " else "") + teaching.title(mode)) }, enabled = selectHint != null, onClick = {
                    selectHint?.invoke(mode); expanded = false
                })
            }
            DropdownMenuItem(text={ Text("选择参照和弦…") },enabled=selectReference!=null,onClick={ expanded=false; referencePicker=true })
        }
    }
    if(referencePicker) AlertDialog(onDismissRequest={ referencePicker=false },title={ Text("和弦音参照") },
        text={ LazyColumn(Modifier.heightIn(max=360.dp)) {
            item { TextButton(onClick={ selectReference?.invoke("auto");referencePicker=false }) { Text("自动跟随当前和弦／教材主和弦") } }
            items(Chords.all,key={it.id}) { chord ->
                TextButton(onClick={ selectReference?.invoke(chord.name);referencePicker=false },modifier=Modifier.fillMaxWidth()) { Text("参照 ${chord.name}") }
            }
        } },confirmButton={ TextButton(onClick={ referencePicker=false }) { Text("完成") } })
}

@Composable
internal fun LinkedFretboardDock(
    sequence: Sequence,
    cursor: Cursor?,
    phase: Phase,
    current: List<Position>,
    range: BarRange,
    hint: Hint,
    onHint: () -> Unit,
    onTap: (Position) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
    loopContinues: Boolean = false,
    labelRoot: Int? = null,
    controls: @Composable () -> Unit = {},
) {
    val preferences=LocalContext.current.getSharedPreferences("practice-settings",0)
    var labels by rememberSaveable { mutableStateOf(preferences.getString("board-labels","fingers") ?: "fingers") }
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
    val noteLabels=index.labelsAt(q,next.first,labels,labelRoot,LocalBoardTeaching.current.key)
    val techniques = if (cursor?.pre == true || phase == Phase.ENDED || phase == Phase.ERROR) emptyList()
        else cursor?.movingPositions?.takeIf { it.isNotEmpty() } ?: index.techniquesAt(q)
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { controls() }
                BoardLabelMenu(
                    hint = hint, onHint = onHint,
                    title = when (labels) { "notes" -> "音名"; "degrees" -> "级数"; else -> "指法" },
                    status = "第 ${sequence.barLabel(visit)} 小节",
                    labels = labels, labelRoot = labelRoot,
                    onLabel = { labels = it; preferences.edit().putString("board-labels", it).apply() },
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                Fretboard(
                    positions = positions,
                    key = null,
                    hint = hint,
                    degrees = false,
                    current = active,
                    enabled = enabled,
                    height = maxHeight.coerceAtLeast(1.dp),
                    onTap = onTap,
                    upcoming = upcoming,
                    showLegend = false,
                    capoFret = sequence.capoFret,
                    openStringMidi = sequence.openStringMidi,
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
