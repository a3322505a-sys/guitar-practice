package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val selectedIndex = practices.indexOfFirst { it.key.encoded == selectedId }
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
            TextButton(onClick = { practices.getOrNull(selectedIndex - 1)?.let(onPractice) }, enabled = selectedIndex > 0,
                contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp).testTag("previous-practice")
                    .semantics { contentDescription = "上一段" }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(enabled = practices.size > 1) { onExpanded(true) }
                    .testTag("practice-title").padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text(title + if (selectedIndex >= 0) " · ${selectedIndex + 1}/${practices.size} ▾" else "", maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge)
                    Text(if (compact) "$subtitle · $measure" else subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
            TextButton(onClick = { practices.getOrNull(selectedIndex + 1)?.let(onPractice) },
                enabled = selectedIndex >= 0 && selectedIndex < practices.lastIndex,
                contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp).testTag("next-practice")
                    .semantics { contentDescription = "下一段" }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
            if (!compact) Column(Modifier.widthIn(max = 200.dp).padding(start = 8.dp),
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
                onTap, Modifier.weight(1f).fillMaxWidth(), enabled = enabled,
                loopContinues = loopContinues, labelRoot = labelRoot, labelMode = labels)
        }
    }
}

/** Existing stage callers share the same bundled Material Symbols entry point. */
@Composable
internal fun StageIcon(kind: String, modifier: Modifier = Modifier) {
    AppIcon(when (kind) {
        "back" -> AppSymbol.Back
        "up" -> AppSymbol.Collapse
        "down" -> AppSymbol.Expand
        "pause" -> AppSymbol.Pause
        "play" -> AppSymbol.Play
        "loop" -> AppSymbol.Repeat
        "restart" -> AppSymbol.Restart
        "click" -> AppSymbol.Beat
        else -> AppSymbol.Tempo
    }, contentDescription = null, modifier = modifier)
}
