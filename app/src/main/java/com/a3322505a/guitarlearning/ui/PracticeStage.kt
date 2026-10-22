package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.assets.song.LibraryPractice
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*

@Composable
internal fun PackageStageTopBar(
    title: String, subtitle: String, chord: String, measure: String,
    practices: List<LibraryPractice>, selectedId: String, expanded: Boolean,
    onExpanded: (Boolean) -> Unit, onPractice: (LibraryPractice) -> Unit, onBack: () -> Unit,
) {
    val colors = PracticeSkin.colors
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("stage-header")) {
        val compact = maxWidth < 600.dp
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(8.dp),
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag("back-to-library")
                    .semantics { contentDescription = "返回曲包库" }) {
                StageIcon("back", Modifier.size(24.dp))
                if (!compact) Text("曲包库", modifier = Modifier.padding(start = 8.dp))
            }
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(enabled = practices.size > 1) { onExpanded(true) }
                    .testTag("practice-title").padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text(title + if (practices.size > 1) " ⌄" else "", maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge)
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(expanded && practices.size > 1, { onExpanded(false) },
                    modifier = Modifier.heightIn(max = 360.dp).widthIn(max = 420.dp).testTag("practice-menu")) {
                    practices.forEach { practice ->
                        DropdownMenuItem(text = { Text((if (practice.key.encoded == selectedId) "✓ " else "") + practice.title) },
                            onClick = { onPractice(practice); onExpanded(false) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("switch-${practice.key.encoded}"))
                    }
                }
            }
            Column(Modifier.widthIn(max = if (compact) 112.dp else 200.dp).padding(start = 8.dp),
                horizontalAlignment = Alignment.End) {
                if (chord.isNotBlank()) Text(chord, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(measure, maxLines = 1, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
        }
    }
}

@Composable
internal fun StageFretboard(
    sequence: Sequence?, cursor: Cursor?, phase: Phase, current: List<Position>, range: BarRange,
    labelRoot: Int?, expanded: Boolean, onToggle: () -> Unit, height: Dp,
    onTap: (Position) -> Unit, enabled: Boolean, loopContinues: Boolean,
) {
    val preferences = LocalContext.current.getSharedPreferences("practice-settings", 0)
    var labels by rememberSaveable { mutableStateOf(if (preferences.getString("board-labels", null) == "degrees") "degrees" else "notes") }
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = PracticeSkin.colors.surfaceLow, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().height(height).testTag("stage-fretboard")) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("指板", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { menuOpen = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("board-label-menu")) {
                        Text((if (labels == "degrees" && labelRoot != null) "度数" else "当前音位") + " ⌄")
                    }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        listOf("notes" to "当前音位", "degrees" to "度数").forEach { (id, title) ->
                            DropdownMenuItem(text = { Text(title) }, enabled = id != "degrees" || labelRoot != null,
                                onClick = { labels = id; preferences.edit().putString("board-labels", id).apply(); menuOpen = false })
                        }
                    }
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(48.dp).testTag("fold-fretboard")
                    .semantics { contentDescription = if (expanded) "收起指板" else "展开指板" }) {
                    StageIcon(if (expanded) "up" else "down", Modifier.size(20.dp))
                }
            }
            if (expanded && sequence != null) LinkedFretboardDock(sequence, cursor, phase, current, range,
                Hint.FULL, {}, onTap, Modifier.weight(1f).fillMaxWidth(), enabled = enabled,
                loopContinues = loopContinues, labelRoot = labelRoot, labelMode = labels, showHeader = false)
        }
    }
}

/** Small vector controls use the same stroke and inherit the active theme. */
@Composable
internal fun StageIcon(kind: String, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color,
            Offset(w*x1,h*y1), Offset(w*x2,h*y2), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        when (kind) {
            "back" -> { line(.65f,.18f,.3f,.5f); line(.3f,.5f,.65f,.82f) }
            "up" -> { line(.2f,.65f,.5f,.35f); line(.5f,.35f,.8f,.65f) }
            "down" -> { line(.2f,.35f,.5f,.65f); line(.5f,.65f,.8f,.35f) }
            "pause" -> { line(.35f,.2f,.35f,.8f); line(.65f,.2f,.65f,.8f) }
            "play" -> drawPath(Path().apply { moveTo(w*.28f,h*.16f); lineTo(w*.8f,h*.5f); lineTo(w*.28f,h*.84f); close() }, color, style = Stroke(2.dp.toPx()))
            "loop" -> {
                drawPath(Path().apply { moveTo(w*.78f,h*.3f); cubicTo(w*.62f,h*.02f,w*.12f,h*.15f,w*.18f,h*.57f)
                    moveTo(w*.22f,h*.7f); cubicTo(w*.38f,h*.98f,w*.88f,h*.85f,w*.82f,h*.43f) }, color, style = Stroke(2.dp.toPx()))
                line(.78f,.3f,.52f,.3f); line(.78f,.3f,.78f,.08f); line(.22f,.7f,.48f,.7f); line(.22f,.7f,.22f,.92f)
            }
            "click" -> {
                drawPath(Path().apply { moveTo(w*.22f,h*.86f); lineTo(w*.42f,h*.12f); lineTo(w*.58f,h*.12f); lineTo(w*.78f,h*.86f); close() }, color, style = Stroke(2.dp.toPx()))
                line(.46f,.66f,.78f,.27f)
            }
            else -> repeat(5) { i -> line(.15f+i*.17f,.5f-(if(i%2==0) .18f else .34f),.15f+i*.17f,.5f+(if(i%2==0) .18f else .34f)) }
        }
    }
}
