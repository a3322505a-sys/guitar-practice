package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.audio.Phase

/** Only real sessions get pause/restart controls; auditions retain their own meaning. */
internal enum class TransportMode { IDLE, PREPARING, PLAYING, PAUSED, ENDED, ERROR, PREVIEW }

internal fun transportMode(phase: Phase, hasSession: Boolean, preview: Boolean): TransportMode = when {
    preview && phase in listOf(Phase.PREPARING, Phase.PLAYING, Phase.PAUSED) -> TransportMode.PREVIEW
    !hasSession -> if (phase == Phase.ERROR) TransportMode.ERROR else TransportMode.IDLE
    else -> when (phase) {
        Phase.IDLE -> TransportMode.IDLE
        Phase.PREPARING -> TransportMode.PREPARING
        Phase.PLAYING -> TransportMode.PLAYING
        Phase.PAUSED -> TransportMode.PAUSED
        Phase.ENDED -> if (preview) TransportMode.IDLE else TransportMode.ENDED
        Phase.ERROR -> TransportMode.ERROR
    }
}

@Composable
internal fun PracticeTransportDock(
    bpm: Int, multiplier: Double, meter: String, mode: TransportMode, hasSequence: Boolean,
    loop: Boolean, rangeLabel: String,
    onTempo: () -> Unit, onLoop: () -> Unit, onRange: () -> Unit, onVolume: () -> Unit,
    onPauseResume: () -> Unit, onRestart: () -> Unit, onDemo: () -> Unit,
    onFollow: () -> Unit, onStop: () -> Unit,
    strategy: @Composable RowScope.() -> Unit,
    pausedTitle: String,
) {
    val colors = PracticeSkin.colors
    val primaryTitle = when (mode) {
        TransportMode.PLAYING -> "暂停"
        TransportMode.PAUSED -> pausedTitle
        TransportMode.ENDED -> "再练一次"
        TransportMode.PREPARING -> "准备中…"
        TransportMode.PREVIEW -> "试听中"
        TransportMode.ERROR -> "重试跟练"
        else -> "开始跟练"
    }
    val primaryClick = when (mode) {
        TransportMode.PLAYING, TransportMode.PAUSED -> onPauseResume
        TransportMode.ENDED -> onRestart
        else -> onFollow
    }
    val enabled = hasSequence && mode != TransportMode.PREPARING && mode != TransportMode.PREVIEW
    Surface(color = colors.surfaceHigh, modifier = Modifier.fillMaxWidth().testTag("transport-bar")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            PracticeStrategyStrip {
                strategy()
                TextButton(onClick = onRange, modifier = Modifier.heightIn(min = 48.dp).testTag("range-button")) { Text(rangeLabel) }
                if (mode != TransportMode.IDLE) TextButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text("结束") }
            }
            HorizontalDivider(color = colors.borderSubtle)
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).testTag("transport")) {
                val tempo: @Composable (Modifier) -> Unit = { modifier ->
                    TextButton(onClick = onTempo, modifier = modifier.heightIn(min = 52.dp).testTag("tempo-button"), contentPadding = PaddingValues(4.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$bpm BPM", fontWeight = FontWeight.Bold)
                            Text("${"%.2f".format(java.util.Locale.ROOT, multiplier)}× · $meter", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                val loopAndMix: @Composable RowScope.() -> Unit = {
                    IconButton(onClick = onLoop, modifier = Modifier.size(48.dp).testTag("loop-button")
                        .semantics { contentDescription = if (loop) "关闭持续循环" else "开启持续循环" }) {
                        Text("↻", color = if (loop) colors.primaryText else colors.textSecondary, style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onVolume, modifier = Modifier.size(48.dp).testTag("volume-button")
                        .semantics { contentDescription = "混音" }) { Text("≋", style = MaterialTheme.typography.titleLarge) }
                }
                val demo: @Composable (Modifier) -> Unit = { modifier ->
                    TextButton(onClick = if (mode == TransportMode.PLAYING || mode == TransportMode.PAUSED) onRestart else onDemo,
                        enabled = hasSequence, modifier = modifier.heightIn(min = 52.dp), contentPadding = PaddingValues(4.dp)) {
                        Text(if (mode == TransportMode.PLAYING || mode == TransportMode.PAUSED) "本轮重来" else "听示范")
                    }
                }
                val primary: @Composable (Modifier) -> Unit = { modifier ->
                    Button(onClick = primaryClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(primaryTitle, fontWeight = FontWeight.Bold)
                    }
                }
                if (maxWidth < 340.dp) {
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { tempo(Modifier.weight(1f)); loopAndMix() }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { demo(Modifier.weight(1f)); primary(Modifier.weight(1.4f)) }
                    }
                } else Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    tempo(Modifier.width(80.dp)); demo(Modifier.weight(1f)); primary(Modifier.weight(1.6f)); loopAndMix()
                }
            }
        }
    }
}

@Composable
private fun PracticeStrategyStrip(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).heightIn(min = 48.dp).testTag("practice-strategy"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
internal fun SpeedAndTempoSheet(
    bpm: Int, multiplier: Double, minBpm: Int, maxBpm: Int, detail: String,
    onBpm: (Int) -> Unit, onMultiplier: (Double) -> Unit, onClose: () -> Unit,
) {
    var draft by remember(bpm) { mutableFloatStateOf(bpm.toFloat()) }
    StudioModal(onClose) {
        Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()).testTag("tempo-popup"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("速度与节拍", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) { Text("收起") }
            }
            Text("${draft.toInt()} BPM · 松手应用", style = MaterialTheme.typography.titleLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            Slider(value = draft.coerceIn(minBpm.toFloat(), maxBpm.toFloat()), onValueChange = { draft = it },
                onValueChangeFinished = { onBpm(draft.toInt()) }, valueRange = minBpm.toFloat()..maxBpm.toFloat(), modifier = Modifier.testTag("tempo-slider"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf(-10, -5, -1, 1, 5, 10).forEach { step ->
                    TextButton(onClick = { onBpm((bpm + step).coerceIn(minBpm, maxBpm)) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag(if (step == -5) "tempo-slower" else if (step == 5) "tempo-faster" else "tempo-step-$step")) {
                        Text(if (step > 0) "+$step" else "$step")
                    }
                }
            }
            Text("倍速", style = MaterialTheme.typography.titleSmall)
            val values = listOf(.5, .75, .9, 1.0, 1.1)
            Choices(values.map { "${it}x" }, values.indexOfFirst { kotlin.math.abs(it - multiplier) < .005 }) { onMultiplier(values[it]) }
        }
    }
}
