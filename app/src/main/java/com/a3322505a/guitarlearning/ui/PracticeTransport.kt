package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.practice.TempoControl
import kotlin.math.roundToInt

internal fun stageTime(seconds: Double): String {
    val value = seconds.coerceAtLeast(0.0).toInt()
    return "%02d:%02d".format(java.util.Locale.ROOT, value / 60, value % 60)
}

/** The only four stage controls: BPM, play/pause, loop and metronome. */
@Composable
internal fun StageTransport(
    bpm: Int, phase: Phase, enabled: Boolean, loop: Boolean, click: Boolean,
    elapsed: Double, duration: Double, onTempo: () -> Unit, onPlay: () -> Unit,
    onLoop: () -> Unit, onClick: () -> Unit,
) {
    val colors = PracticeSkin.colors
    Surface(color = colors.background, modifier = Modifier.fillMaxWidth().testTag("transport-bar")) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 22.dp).testTag("playback-progress"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stageTime(elapsed), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                LinearProgressIndicator(progress = { if (duration > 0) (elapsed / duration).toFloat().coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.weight(1f).height(4.dp), color = colors.primary, trackColor = colors.surfaceSelected)
                Text(stageTime(duration), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val icons = maxWidth >= 600.dp
                val largeText = LocalDensity.current.fontScale > 1.2f
                Row(Modifier.fillMaxWidth().testTag("transport"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable fun RowScope.control(title: String, tag: String, icon: String, selected: Boolean = false,
                        primary: Boolean = false, available: Boolean = true, action: () -> Unit) {
                        Button(onClick = action, enabled = available, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag(tag)
                                .semantics { this.selected = selected; contentDescription = title },
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (primary) colors.primary else if (selected) colors.toggleActiveBackground else colors.surfaceHigh,
                                contentColor = if (primary) colors.onPrimary else if (selected) colors.toggleActiveContent else colors.textPrimary,
                                disabledContainerColor = colors.surfaceHigh, disabledContentColor = colors.textSecondary)) {
                            if (icons) {
                                StageIcon(icon, Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            if (tag == "tempo-button" && largeText) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$bpm", fontWeight = FontWeight.Bold)
                                Text("BPM", style = MaterialTheme.typography.labelMedium)
                            } else Text(title, maxLines = 1, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    control("$bpm BPM", "tempo-button", "tempo", action = onTempo)
                    control(if (phase == Phase.PLAYING) "暂停" else "播放", "play-button",
                        if (phase == Phase.PLAYING) "pause" else "play", primary = true,
                        available = enabled && phase != Phase.PREPARING, action = onPlay)
                    control("循环", "loop-button", "loop", selected = loop, action = onLoop)
                    control("节拍", "metronome-button", "click", selected = click, action = onClick)
                }
            }
        }
    }
}

@Composable
internal fun BpmSheet(bpm: Int, minBpm: Int, maxBpm: Int, onBpm: (Int) -> Unit, onClose: () -> Unit,
    cursorOffsetMs: Int = 0, onCursorOffset: (Int) -> Unit = {}) {
    var draft by remember(bpm) { mutableIntStateOf(bpm) }
    AlertDialog(onDismissRequest = onClose, modifier = Modifier.testTag("tempo-popup"),
        title = { Text("速度 · BPM") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$draft BPM", style = MaterialTheme.typography.headlineSmall)
                Slider(draft.coerceIn(minBpm, maxBpm).toFloat(), { draft = it.roundToInt() },
                    onValueChangeFinished = { onBpm(draft) }, enabled = maxBpm > minBpm,
                    valueRange = minBpm.toFloat()..maxBpm.toFloat(), modifier = Modifier.testTag("tempo-slider"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(-5, -1, 1, 5).forEach { step ->
                        TextButton(onClick = { onBpm((bpm + step).coerceIn(minBpm, maxBpm)) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("tempo-step-$step")) {
                            Text(if (step > 0) "+$step" else "$step")
                        }
                    }
                }
                if (minBpm > TempoControl.TARGET_MIN_BPM) Text(
                    "此曲引擎最低 $minBpm BPM（最低倍率 12.5%）", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("光标同步微调", style = MaterialTheme.typography.titleMedium)
                Text("正值延后，负值提前；仅调整光标显示。", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { onCursorOffset((cursorOffsetMs - 10).coerceAtLeast(-500)) },
                        enabled = cursorOffsetMs > -500,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("cursor-offset-minus")) { Text("−") }
                    Text("${if (cursorOffsetMs > 0) "+" else ""}$cursorOffsetMs ms",
                        modifier = Modifier.testTag("cursor-offset-value"), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onCursorOffset((cursorOffsetMs + 10).coerceAtMost(500)) },
                        enabled = cursorOffsetMs < 500,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("cursor-offset-plus")) { Text("+") }
                }
                TextButton(onClick = { onCursorOffset(0) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("cursor-offset-reset")) { Text("恢复默认值") }
            }
        }, confirmButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("完成") } })
}
