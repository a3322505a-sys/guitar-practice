package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
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
import com.a3322505a.guitarlearning.practice.MovingPosition
import kotlin.math.hypot

enum class Hint(val title: String) { FULL("完整提示"), ROOTS("仅主音／根音"), HIDDEN("隐藏音位／指法") }

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
) {
    if (positions.isNotEmpty() && positions.all { it.fret == 0 }) {
        OpenStringBoard(positions, key, hint, current, upcoming, enabled, onTap, capoFret, openStringMidi)
        return
    }
    val visible = when (hint) {
        Hint.FULL -> positions
        Hint.ROOTS -> if (key == null) positions else positions.filter(key::isRoot)
        Hint.HIDDEN -> emptyList()
    }
    val hasOpen = positions.any { it.fret == 0 }
    val low = positions.filter { it.fret > 0 }.minOf { it.fret }.let { if (hasOpen) 1 else it }
    val high = positions.maxOf { it.fret }
    val columns = high - low + 1
    val measurer = rememberTextMeasurer()
    val diagramHeight = height
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxOf(maxWidth, (columns * 36 + 56 + if (hasOpen) 36 else 0).dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier.width(width).height(diagramHeight).testTag("fretboard").semantics {
                    contentDescription = if (hint == Hint.HIDDEN) "指板，提示已隐藏" else buildString {
                        append("指板，上为 1 弦，下为 6 弦。")
                        if (capoFret > 0) append("变调夹${capoFret}品，品位相对变调夹，音名为实际发声。")
                        current.filter { it in visible }.forEach { append("当前 ${it.string} 弦 ${it.fret} 品；") }
                        upcoming.filter { it in visible }.forEach { append("下一 ${it.string} 弦 ${it.fret} 品；") }
                    }
                }.pointerInput(visible, enabled, onTap) {
                    if (enabled) detectTapGestures { tap ->
                        val left = 46.dp.toPx()
                        val gridLeft = left + if (hasOpen) 36.dp.toPx() else 0f
                        val cell = (size.width - gridLeft - 10.dp.toPx()) / columns
                        fun x(fret: Int) = if (fret == 0) left + 18.dp.toPx() else gridLeft + (fret - low + .5f) * cell
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
                fun x(fret: Float) = if (fret == 0f) left + 18.dp.toPx() else gridLeft + (fret - low + .5f) * cell
                val vertical = size.height / 238.dp.toPx()
                val top = 40.dp.toPx() * vertical
                val row = 32.dp.toPx() * vertical
                drawRoundRect(
                    colors.diagramSurface,
                    Offset(left, top - 12.dp.toPx() * vertical),
                    androidx.compose.ui.geometry.Size(size.width - left, 5 * row + 24.dp.toPx() * vertical),
                    androidx.compose.ui.geometry.CornerRadius(metrics.cornerSmall.toPx()),
                )
                for (i in 0 until columns) {
                    val fretNumber = low + i
                    val cx = gridLeft + (i + 0.5f) * cell
                    if (fretNumber in SINGLE_INLAY_FRETS) {
                        drawCircle(colors.border.copy(alpha = 0.55f), 4.5.dp.toPx() * vertical, Offset(cx, top + 2.5f * row))
                    } else if (fretNumber in DOUBLE_INLAY_FRETS) {
                        drawCircle(colors.border.copy(alpha = 0.55f), 4.dp.toPx() * vertical, Offset(cx, top + 1.5f * row))
                        drawCircle(colors.border.copy(alpha = 0.55f), 4.dp.toPx() * vertical, Offset(cx, top + 3.5f * row))
                    }
                }
                for (i in 0..columns) {
                    drawLine(
                        colors.diagramGrid,
                        Offset(gridLeft + i * cell, top - 12.dp.toPx() * vertical),
                        Offset(gridLeft + i * cell, top + 5 * row + 12.dp.toPx() * vertical),
                        metrics.borderWidth.toPx(),
                    )
                }
                for (i in 0 until columns) {
                    val fretNumber = low + i
                    val isMarker = fretNumber in SINGLE_INLAY_FRETS || fretNumber in DOUBLE_INLAY_FRETS
                    label(
                        measurer,
                        "$fretNumber",
                        gridLeft + (i + 0.5f) * cell,
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
                    drawLine(colors.diagramGrid, Offset(left, y), Offset(size.width - 10.dp.toPx(), y), (0.45 + s * 0.13).dp.toPx())
                    label(measurer, "$s ${Music.name(openStringMidi[s - 1] + capoFret)}", 21.dp.toPx(), y, colors.textSecondary, size = 12)
                }
                for (p in visible) {
                    val center = Offset(x(p.fret.toFloat()), top + (p.string - 1) * row)
                    val root = key?.isRoot(p) == true
                    val active = p in current
                    val radius = (if (root || active) 13.5f else 12.5f).dp.toPx() * vertical
                    drawCircle(
                        if (active) colors.fretboardNoteActive else if (root) colors.fretboardRoot else colors.fretboardNoteNormal,
                        radius,
                        center,
                    )
                    if (!active && !root) {
                        drawCircle(colors.fretboardNoteStroke, radius, center, style = Stroke((metrics.borderWidth * 1.4f).toPx()))
                    } else if (root && !active) {
                        drawCircle(colors.primary.copy(alpha = 0.32f), 16.dp.toPx() * vertical, center, style = Stroke((metrics.borderWidth * 1.5f).toPx()))
                    }
                    if (active) {
                        drawCircle(colors.textPrimary, 16.5.dp.toPx() * vertical, center, style = Stroke((metrics.borderWidth * 2).toPx()))
                    }
                    if (p in upcoming) {
                        drawCircle(colors.primary, 19.dp.toPx() * vertical, center, style = Stroke((metrics.borderWidth * 2).toPx()))
                    }
                    label(
                        measurer,
                        if (degrees && key != null) key.degree(openStringMidi[p.string - 1] + p.fret + capoFret).toString()
                        else Music.name(openStringMidi[p.string - 1] + p.fret + capoFret),
                        center.x,
                        center.y,
                        if (active) colors.onFretboardNoteActive else if (root) colors.onFretboardRoot else colors.onFretboardNoteNormal,
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
    val visible = when (hint) {
        Hint.FULL -> positions
        Hint.ROOTS -> if (key == null) positions else positions.filter(key::isRoot)
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
                        Text(if (shown) "0" else "", style = MaterialTheme.typography.labelSmall, color = if (active) ink else colors.primaryText)
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
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    val visible = when (hint) {
        Hint.FULL -> chord.positions
        Hint.ROOTS -> chord.positions.filter(chord::isRoot)
        Hint.HIDDEN -> emptyList()
    }
    Canvas(
        Modifier.fillMaxWidth().height(diagramHeight).testTag("chord-diagram").semantics {
            contentDescription = if (hint == Hint.HIDDEN) "和弦图，提示已隐藏" else buildString {
                append("${chord.name}，${chord.conditions}，起始相对品位${chord.baseFret}。")
                visible.forEach { append("${it.string} 弦 ${it.fret} 品，${chord.noteName(it, true)} ${chord.role(it)}；") }
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
        for (p in visible) {
            val center = geometry.position(p)
            val root = chord.isRoot(p)
            val active = p in current
            drawCircle(
                if (active) colors.fretboardNoteActive else if (root) colors.fretboardRoot else colors.fretboardNoteNormal,
                13 * geometry.unit,
                center,
            )
            if (!active && !root) {
                drawCircle(colors.fretboardNoteStroke, 13 * geometry.unit, center, style = Stroke((metrics.borderWidth * 1.4f).toPx()))
            } else if (root && !active) {
                drawCircle(colors.primary.copy(alpha = 0.35f), 15.5f * geometry.unit, center, style = Stroke((metrics.borderWidth * 1.5f).toPx()))
            }
            label(
                measurer,
                if (hint == Hint.ROOTS) "根" else if (p.fret == 0) "O" else chord.fingers[6 - p.string].toString(),
                center.x,
                center.y,
                if (active) colors.onFretboardNoteActive else if (root) colors.onFretboardRoot else colors.onFretboardNoteNormal,
                bold = true,
            )
            if (active) drawCircle(colors.textPrimary, 17 * geometry.unit, center, style = Stroke((metrics.borderWidth * 2).toPx()))
        }
        for (i in 1..4) label(measurer, "${chord.baseFret + i - 1}", left - 23 * geometry.unit, top + (i - 0.5f) * fretStep, colors.textSecondary, size = 12)
    }
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
