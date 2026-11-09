package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
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
    onLoop: () -> Unit, onClick: () -> Unit, onRestart: () -> Unit,
    entryLight: Int = 0, entryBeat: Int = 0, entryBeatTotal: Int = 0,
) {
    val colors = PracticeSkin.colors
    Surface(color = colors.background, modifier = Modifier.fillMaxWidth().testTag("transport-bar")) {
        BoxWithConstraints(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            val shortLandscape = maxWidth >= 600.dp && LocalConfiguration.current.screenHeightDp < 480
            @Composable fun progressRow(modifier: Modifier) {
                Row(modifier.heightIn(min = 22.dp).testTag("playback-progress"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Compact audio-following traffic light; always reserves the same
                    // width, never overlays the score or controls audio playback.
                    val cueText = when (entryLight) {
                        1 -> "预备拍 ${entryBeat}/${entryBeatTotal}"
                        2 -> "预备拍最后一拍 ${entryBeat}/${entryBeatTotal}"
                        3 -> "正式第一拍"
                        else -> "入拍提示待机"
                    }
                    Row(
                        modifier = Modifier.testTag("entry-signal")
                            .semantics { contentDescription = cueText },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple(1, Color(0xFFDC5751), "entry-light-red"),
                            Triple(2, Color(0xFFE3B341), "entry-light-amber"),
                            Triple(3, Color(0xFF3EAE76), "entry-light-green")
                        ).forEach { (light, tint, tag) ->
                            Box(Modifier.size(12.dp)
                                .background(tint.copy(alpha = if (entryLight == light) 1f else .18f), CircleShape)
                                .testTag(tag))
                        }
                    }
                    Text(stageTime(elapsed), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    LinearProgressIndicator(progress = { if (duration > 0) (elapsed / duration).toFloat().coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.weight(1f).height(4.dp), color = colors.primary, trackColor = colors.surfaceSelected)
                    Text(stageTime(duration), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    AppIconButton(AppSymbol.Restart, "重新开始", onRestart, Modifier.testTag("restart-button"),
                        enabled = enabled && phase != Phase.PREPARING)
                }
            }
            @Composable fun controls(modifier: Modifier) {
                BoxWithConstraints(modifier) {
                    val icons = maxWidth >= 600.dp
                    val largeText = LocalDensity.current.fontScale > 1.2f
                    Row(Modifier.fillMaxWidth().testTag("transport"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        @Composable fun RowScope.control(title: String, tag: String, icon: AppSymbol, selected: Boolean = false,
                            primary: Boolean = false, available: Boolean = true, action: () -> Unit) {
                            Button(onClick = action, enabled = available, shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).heightIn(min = PracticeSkin.metrics.transportButtonHeight).testTag(tag)
                                    .semantics { this.selected = selected; contentDescription = title },
                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (primary) colors.primary else if (selected) colors.toggleActiveBackground else colors.surfaceHigh,
                                    contentColor = if (primary) colors.onPrimary else if (selected) colors.toggleActiveContent else colors.textPrimary,
                                    disabledContainerColor = colors.surfaceHigh, disabledContentColor = colors.textSecondary)) {
                                if (icons) {
                                    AppIcon(icon, null)
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (tag == "tempo-button" && largeText) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$bpm", fontWeight = FontWeight.Bold)
                                    Text("BPM", style = MaterialTheme.typography.labelMedium)
                                } else Text(title, maxLines = 1, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                        control("$bpm BPM", "tempo-button", AppSymbol.Tempo, action = onTempo)
                        control(if (phase == Phase.PLAYING) "暂停" else "播放", "play-button",
                            if (phase == Phase.PLAYING) AppSymbol.Pause else AppSymbol.Play, primary = true,
                            available = enabled && phase != Phase.PREPARING, action = onPlay)
                        control("循环", "loop-button", AppSymbol.Repeat, selected = loop, action = onLoop)
                        control("节拍", "metronome-button", AppSymbol.Beat, selected = click, action = onClick)
                    }
                }
            }
            if (shortLandscape) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                progressRow(Modifier.weight(.34f))
                controls(Modifier.weight(.66f))
            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                progressRow(Modifier.fillMaxWidth())
                controls(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun BpmSheet(bpm: Int, minBpm: Int, maxBpm: Int, onBpm: (Int) -> Unit, onClose: () -> Unit,
    cursorOffsetMs: Int = 0, onCursorOffset: (Int) -> Unit = {}) {
    var draft by remember(bpm) { mutableIntStateOf(bpm) }
    AppDialog("速度 · BPM", onClose, Modifier.testTag("tempo-popup")) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$draft BPM", style = MaterialTheme.typography.headlineSmall)
                Slider(draft.coerceIn(minBpm, maxBpm).toFloat(), { draft = it.roundToInt() },
                    onValueChangeFinished = { onBpm(draft) }, enabled = maxBpm > minBpm,
                    valueRange = minBpm.toFloat()..maxBpm.toFloat(), modifier = Modifier.testTag("tempo-slider"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(-5, -1, 1, 5).forEach { step ->
                        AppButton(if (step > 0) "+$step" else "$step", { onBpm((bpm + step).coerceIn(minBpm, maxBpm)) },
                            Modifier.testTag("tempo-step-$step"), size = AppButtonSize.Sm, style = AppButtonStyle.Quiet)
                    }
                }
                HorizontalDivider()
                Text("光标同步微调", style = MaterialTheme.typography.titleMedium)
                Text("正值延后，负值提前；仅调整光标显示。", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    AppIconButton(AppSymbol.Remove, "光标提前 10 毫秒", { onCursorOffset((cursorOffsetMs - 10).coerceAtLeast(-500)) },
                        Modifier.testTag("cursor-offset-minus"), enabled = cursorOffsetMs > -500)
                    Text("${if (cursorOffsetMs > 0) "+" else ""}$cursorOffsetMs ms",
                        modifier = Modifier.testTag("cursor-offset-value"), style = MaterialTheme.typography.titleMedium)
                    AppIconButton(AppSymbol.Add, "光标延后 10 毫秒", { onCursorOffset((cursorOffsetMs + 10).coerceAtMost(500)) },
                        Modifier.testTag("cursor-offset-plus"), enabled = cursorOffsetMs < 500)
                }
                AppButton("恢复默认值", { onCursorOffset(0) }, Modifier.testTag("cursor-offset-reset"),
                    size = AppButtonSize.Sm, style = AppButtonStyle.Quiet)
            }
    }
}
