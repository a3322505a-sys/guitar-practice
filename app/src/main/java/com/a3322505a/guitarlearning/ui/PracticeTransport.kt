package com.a3322505a.guitarlearning.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.alphatab.AlphaPlayback
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.audio.PlaybackMix
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
) {
    val colors = PracticeSkin.colors
    Surface(color = colors.background, modifier = Modifier.fillMaxWidth().testTag("transport-bar")) {
        BoxWithConstraints(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            val shortLandscape = maxWidth >= 600.dp && LocalConfiguration.current.screenHeightDp < 480
            @Composable fun progressRow(modifier: Modifier) {
                Row(modifier.heightIn(min = 22.dp).testTag("playback-progress"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable
internal fun VolumeSheet(
    mix: PlaybackMix,
    onMix: (PlaybackMix) -> Unit,
    onClose: () -> Unit,
) {
    val musicPct = (mix.music.coerceIn(0.0, 1.0) * 100).roundToInt()
    val effectiveMetronome = if (mix.metronome <= 0.0) 1.0 else mix.metronome.coerceIn(0.20, 1.0)
    val metronomePct = (effectiveMetronome * 100).roundToInt()
    AppDialog("音量", onClose, Modifier.testTag("volume-popup")) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("音乐音量", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$musicPct%",
                    modifier = Modifier.testTag("music-volume-value"),
                    style = MaterialTheme.typography.labelLarge,
                    color = PracticeSkin.colors.primaryText,
                )
            }
            Slider(
                value = mix.music.toFloat().coerceIn(0f, 1f),
                onValueChange = { value ->
                    onMix(mix.copy(music = value.toDouble().coerceIn(0.0, 1.0)))
                },
                valueRange = 0f..1f,
                modifier = Modifier.testTag("music-volume-slider"),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("节拍与预备拍音量", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$metronomePct%",
                    modifier = Modifier.testTag("metronome-volume-value"),
                    style = MaterialTheme.typography.labelLarge,
                    color = PracticeSkin.colors.primaryText,
                )
            }
            Slider(
                value = effectiveMetronome.toFloat().coerceIn(0.2f, 1f),
                onValueChange = { value ->
                    onMix(mix.copy(metronome = value.toDouble().coerceIn(0.20, 1.0)))
                },
                valueRange = 0.2f..1f,
                modifier = Modifier.testTag("metronome-volume-slider"),
            )
            Text(
                "关闭演奏中的节拍声请点击底部【节拍】按钮，预备拍始终保持发声。",
                style = MaterialTheme.typography.bodySmall,
                color = PracticeSkin.colors.textSecondary,
            )
            AppButton(
                "恢复默认",
                { onMix(PlaybackMix(1.0, 1.0)) },
                Modifier.testTag("volume-reset"),
                size = AppButtonSize.Sm,
                style = AppButtonStyle.Quiet,
            )
        }
    }
}

/** 方案 A：仅在预备拍与正式入拍瞬间浮于谱面上方的只读逐拍脉冲提示。 */
@Composable
internal fun StageCountInCue(
    cue: AlphaPlayback.CountInVisualState,
    modifier: Modifier = Modifier,
) {
    if (!cue.active && !cue.entryFlash) return
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    if (cue.entryFlash) {
        Surface(
            shape = RoundedCornerShape(50),
            color = colors.primary,
            contentColor = colors.onPrimary,
            shadowElevation = 4.dp,
            border = BorderStroke(metrics.borderWidth, colors.primary),
            modifier = modifier
                .testTag("count-in-cue")
                .semantics { contentDescription = "正式进拍" },
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "进拍",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary,
                )
            }
        }
        return
    }
    val plan = cue.plan ?: return
    val activeBeat = if (cue.currentClick > 0) {
        plan.beats.getOrNull((cue.currentClick - 1).coerceIn(0, plan.beats.lastIndex))
    } else {
        null
    }
    val activeBarIndex = activeBeat?.barIndex ?: 0
    val barBeats = plan.beats.filter { it.barIndex == activeBarIndex }
    val isLeadInBar = barBeats.firstOrNull()?.isLeadIn == true
    val hasTwoBaseBars = plan.beats.any { !it.isLeadIn && it.barIndex == 1 }
    val barSize = barBeats.firstOrNull()?.barSize ?: plan.meter.numerator.coerceAtLeast(1)
    val label = when {
        isLeadInBar -> "弱起 · 第${plan.entrySlotInBar}拍进"
        hasTwoBaseBars -> "预备 ${activeBarIndex + 1}/2"
        else -> "预备拍"
    }
    val description = if (isLeadInBar) {
        "弱起引拍 ${cue.currentClick}/${plan.totalClicks}，第${plan.entrySlotInBar}拍进"
    } else {
        "预备拍 ${cue.currentClick}/${plan.totalClicks}"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.surfaceLow.copy(alpha = 0.94f),
        contentColor = colors.textPrimary,
        shadowElevation = 4.dp,
        border = BorderStroke(metrics.borderWidth, colors.borderSubtle),
        modifier = modifier
            .testTag("count-in-cue")
            .semantics { contentDescription = description },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (slot in 1..barSize) {
                    if (slot > 1 && slot in plan.groupStarts) {
                        Spacer(Modifier.width(4.dp).testTag("count-in-group-gap-$slot"))
                    }
                    val isEntrySlot = isLeadInBar && slot == plan.entrySlotInBar
                    val isCurrent = activeBeat != null && slot == activeBeat.displayNumber
                    val isPast = activeBeat != null && slot < activeBeat.displayNumber
                    val isFinal = isCurrent && activeBeat?.isFinalBeat == true
                    val isAccent = slot in plan.groupStarts
                    val scale by animateFloatAsState(
                        targetValue = if (isCurrent) (if (isFinal) 1.18f else 1.08f) else 1f,
                        animationSpec = tween(durationMillis = 120),
                        label = "count-in-beat-$slot",
                    )
                    val bgColor = when {
                        isCurrent -> if (isAccent || isFinal) colors.primary else colors.secondary
                        isEntrySlot -> colors.surfaceSelected
                        isPast -> colors.surfaceSelected
                        else -> colors.surfaceHigh
                    }
                    val fgColor = when {
                        isCurrent -> if (isAccent || isFinal) colors.onPrimary else colors.onAccent
                        isEntrySlot -> colors.primaryText
                        isPast -> colors.primaryText
                        else -> colors.textSecondary
                    }
                    val border = when {
                        isFinal -> BorderStroke(metrics.borderWidth * 1.5f, colors.primaryText)
                        isEntrySlot -> BorderStroke(metrics.borderWidth * 1.5f, colors.primary)
                        else -> null
                    }
                    Surface(
                        shape = CircleShape,
                        color = bgColor,
                        border = border,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .testTag("count-in-slot-$slot"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isEntrySlot) "进" else "$slot",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCurrent || isAccent || isEntrySlot) FontWeight.Bold else FontWeight.Medium,
                                color = fgColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
