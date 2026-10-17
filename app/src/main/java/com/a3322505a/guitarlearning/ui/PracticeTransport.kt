package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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

/** Studio transport console supporting both bottom dock (portrait) and left deck pod (landscape/tablet). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PracticeTransport(
    bpm: Int,
    sourceTempo: Boolean,
    multiplier: Int,
    meter: String,
    hint: Hint,
    mode: TransportMode,
    hasSequence: Boolean,
    onBpm: (Int) -> Unit,
    onMultiplier: (Int) -> Unit,
    onHint: () -> Unit,
    onPauseResume: () -> Unit,
    onRestart: () -> Unit,
    onDemo: () -> Unit,
    onFollow: () -> Unit,
    onStop: () -> Unit,
    availableHeight: Dp = Dp.Infinity,
    showHintAction: Boolean = true,
    verticalDeck: Boolean = false,
    pausedTitle: String = "继续",
) {
    PracticeTheme(theme = PracticeSkin.theme) {
        val colors = PracticeSkin.colors
        val metrics = PracticeSkin.metrics
        var expanded by rememberSaveable { mutableStateOf(false) }
        var draft by remember(bpm) { mutableFloatStateOf(bpm.toFloat()) }
        val multipliers = listOf(50, 75, 100, 125, 150)
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("transport-bar")) {
            val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val popupWidth = maxWidth.coerceAtMost(360.dp).coerceAtLeast(240.dp)
            val singleRow = !verticalDeck && maxWidth >= 600.dp * scale && (availableHeight < 600.dp * scale || maxWidth >= 1000.dp)

            val slowerAction: @Composable () -> Unit = {
                TextButton(
                    onClick = {
                        if (sourceTempo) onMultiplier(multipliers.lastOrNull { it < multiplier } ?: 50)
                        else onBpm((bpm - 5).coerceAtLeast(30))
                    },
                    enabled = if (sourceTempo) multiplier > 50 else bpm > 30,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("tempo-slower"),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textPrimary),
                ) { Text(if (sourceTempo) "−" else "−5", fontWeight = FontWeight.Bold, maxLines = 1) }
            }

            val tempoValue: @Composable () -> Unit = {
                Box {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("tempo-button"),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.primary),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            (if (sourceTempo) "原谱 $multiplier%" else "$bpm BPM") + " ⌃",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    if (expanded) {
                        Popup(
                            popupPositionProvider = TempoPopupPosition,
                            onDismissRequest = { expanded = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = colors.surface,
                                shadowElevation = 4.dp,
                                border = BorderStroke(metrics.borderWidth, colors.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.width(popupWidth).testTag("tempo-popup"),
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (sourceTempo) "原谱速度" else "${draft.toInt()} BPM · 松手应用",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.textPrimary,
                                        )
                                        TextButton(onClick = { expanded = false }, shape = MaterialTheme.shapes.small) { Text("收起") }
                                    }
                                    if (sourceTempo) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            multipliers.forEach { value ->
                                                OutlinedButton(
                                                    onClick = { onMultiplier(value) },
                                                    shape = MaterialTheme.shapes.small,
                                                    border = BorderStroke(metrics.borderWidth, if (value == multiplier) colors.primary else colors.border),
                                                    contentPadding = PaddingValues(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = if (value == multiplier) colors.primaryContainer else colors.surface,
                                                        contentColor = if (value == multiplier) colors.primary else colors.textSecondary,
                                                    ),
                                                ) { Text("$value%") }
                                            }
                                        }
                                    } else {
                                        Slider(
                                            value = draft,
                                            onValueChange = { draft = it },
                                            onValueChangeFinished = { onBpm(draft.toInt()) },
                                            valueRange = 30f..180f,
                                            modifier = Modifier.testTag("tempo-slider"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val fasterAction: @Composable () -> Unit = {
                TextButton(
                    onClick = {
                        if (sourceTempo) onMultiplier(multipliers.firstOrNull { it > multiplier } ?: 150)
                        else onBpm((bpm + 5).coerceAtMost(180))
                    },
                    enabled = if (sourceTempo) multiplier < 150 else bpm < 180,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("tempo-faster"),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textPrimary),
                ) { Text(if (sourceTempo) "+" else "+5", fontWeight = FontWeight.Bold, maxLines = 1) }
            }

            val meterBadge: @Composable () -> Unit = {
                Surface(color = colors.surfaceSecondary, shape = MaterialTheme.shapes.small,
                    border = BorderStroke(metrics.borderWidth, colors.border)) {
                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(meter, color = colors.textSecondary, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            // Narrow studio decks give the value its own row so speed controls stay readable at large fonts.
            val stackedTempo = verticalDeck || maxWidth < 300.dp * scale
            val tempoBay: @Composable (Boolean) -> Unit = { stretch ->
                if (stackedTempo) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(modifier = Modifier.fillMaxWidth(), color = colors.surfaceSecondary,
                            shape = MaterialTheme.shapes.small, border = BorderStroke(metrics.borderWidth, colors.border)) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { tempoValue() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            slowerAction(); meterBadge(); fasterAction()
                        }
                    }
                } else {
                    Row(modifier = if (stretch) Modifier.fillMaxWidth() else Modifier,
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = colors.surfaceSecondary, shape = MaterialTheme.shapes.small,
                            border = BorderStroke(metrics.borderWidth, colors.border),
                            modifier = if (stretch) Modifier.weight(1f) else Modifier) {
                            Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                slowerAction()
                                Box(if (stretch) Modifier.weight(1f) else Modifier, contentAlignment = Alignment.Center) { tempoValue() }
                                fasterAction()
                            }
                        }
                        meterBadge()
                    }
                }
            }

            val actionsFlow: @Composable (Boolean) -> Unit = { fullWidthPrimary ->
                val primaryTitle = when (mode) {
                    TransportMode.IDLE -> "开始跟练"
                    TransportMode.ENDED -> "再练一次"
                    TransportMode.PLAYING -> "暂停"
                    TransportMode.PAUSED -> pausedTitle
                    TransportMode.PREPARING -> "准备中…"
                    TransportMode.PREVIEW -> "试听中"
                    TransportMode.ERROR -> "播放失败"
                }
                val primaryEnabled = when (mode) {
                    TransportMode.IDLE, TransportMode.ENDED -> hasSequence
                    TransportMode.PLAYING, TransportMode.PAUSED -> true
                    else -> false
                }
                val primaryClick: () -> Unit = when (mode) {
                    TransportMode.IDLE -> onFollow
                    TransportMode.ENDED -> onRestart
                    TransportMode.PLAYING, TransportMode.PAUSED -> onPauseResume
                    else -> ({})
                }

                if (fullWidthPrimary) {
                    Column(Modifier.fillMaxWidth().testTag("transport"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            when (mode) {
                                TransportMode.IDLE, TransportMode.ENDED ->
                                    TransportAction("听示范", enabled = hasSequence, modifier = Modifier.weight(1f), onClick = onDemo)
                                TransportMode.PLAYING, TransportMode.PAUSED ->
                                    TransportAction("本轮重来", modifier = Modifier.weight(1f), onClick = onRestart)
                                else -> Unit
                            }
                            if (mode != TransportMode.IDLE) {
                                TransportAction("结束", modifier = Modifier.weight(1f), onClick = onStop)
                            }
                            if (showHintAction) {
                                TransportAction(
                                    if (hint == Hint.HIDDEN) "恢复提示" else "撤提示",
                                    modifier = Modifier.weight(1f),
                                    onClick = onHint,
                                )
                            }
                        }
                        TransportAction(
                            primaryTitle,
                            primary = true,
                            enabled = primaryEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = primaryClick,
                        )
                    }
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth().testTag("transport"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when (mode) {
                            TransportMode.IDLE, TransportMode.ENDED ->
                                TransportAction("听示范", enabled = hasSequence, modifier = Modifier.weight(1f, fill = !singleRow), onClick = onDemo)
                            TransportMode.PLAYING, TransportMode.PAUSED ->
                                TransportAction("本轮重来", modifier = Modifier.weight(1f, fill = !singleRow), onClick = onRestart)
                            else -> Unit
                        }
                        if (mode != TransportMode.IDLE) {
                            TransportAction("结束", modifier = Modifier.weight(1f, fill = !singleRow), onClick = onStop)
                        }
                        if (showHintAction) {
                            TransportAction(
                                if (hint == Hint.HIDDEN) "恢复提示" else "撤提示",
                                modifier = Modifier.weight(1f, fill = !singleRow),
                                onClick = onHint,
                            )
                        }
                        TransportAction(
                            primaryTitle,
                            primary = true,
                            enabled = primaryEnabled,
                            modifier = Modifier.weight(1.35f, fill = !singleRow),
                            onClick = primaryClick,
                        )
                    }
                }
            }

            Surface(
                color = colors.surface,
                shadowElevation = metrics.shadowLevel,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(metrics.borderWidth, colors.border),
            ) {
                val containerModifier = if (verticalDeck) {
                    Modifier.fillMaxWidth().padding(8.dp)
                } else {
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)
                }
                Column(containerModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (verticalDeck) {
                        tempoBay(true)
                        actionsFlow(true)
                    } else if (singleRow) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tempoBay(false)
                            Box(Modifier.weight(1f)) { actionsFlow(false) }
                        }
                    } else {
                        tempoBay(true)
                        actionsFlow(stackedTempo)
                    }
                }
            }
        }
    }
}

/** Center over the tempo button, then keep the complete editor inside the usable window. */
internal object TempoPopupPosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
        (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        (anchorBounds.bottom - popupContentSize.height).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}

@Composable
private fun TransportAction(
    title: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    val content: @Composable RowScope.() -> Unit = {
        if (primary) {
            TransportMotif(if (title == "暂停") "pause" else "play", Modifier.size(15.dp), if (enabled) colors.onPrimary else colors.textSecondary)
            Spacer(Modifier.width(6.dp))
        }
        Text(title, fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.surfaceSecondary,
                disabledContentColor = colors.textSecondary,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surfaceSecondary, contentColor = colors.textPrimary),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            border = BorderStroke(metrics.borderWidth, colors.border),
            content = content,
        )
    }
}
