package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.immersiveStatusBar
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

internal data class PracticeSettingsState(
    val page: Int,
    val chordPhrases: Boolean,
    val tabletControls: Boolean,
    val scale: ScaleOptions,
    val first: Chord,
    val phrase: Phrase,
    val family: Phrase,
    val versions: List<Phrase>,
    val catalogueGroups: Map<String, List<Phrase>>,
    val chordGroups: Map<String, List<Chord>>,
    val relatedTitle: String?,
    val bpm: Int,
    val repeats: Int,
    val firstBar: Int,
    val lastBar: Int,
    val range: BarRange,
    val wholeRange: BarRange,
    val guitar: Boolean,
    val click: Boolean,
    val moreExercisesOpen: Boolean,
    val beatsPerBar: Int,
    val sequence: Sequence?,
    val displayedSequence: Sequence?,
    val scaleResult: RouteResult,
    val sourceTempo: Boolean = false,
    val multiplier: Int = 100,
    val mainTrackId: String? = null,
    val soloTrack: Boolean = false,
    val unavailableReason: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PracticeSettings(
    state: PracticeSettingsState,
    sidePanel: Boolean,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    onCategory: (Int) -> Unit,
    onRepeats: (Int) -> Unit,
    onGuitar: (Boolean) -> Unit,
    onClick: (Boolean) -> Unit,
    onMoreExercises: () -> Unit,
    onScale: (ScaleOptions) -> Unit,
    onPhrase: (Phrase) -> Unit,
    onChord: (Chord) -> Unit,
    onRange: (BarRange) -> Unit,
    onSourceTempo: (Boolean) -> Unit,
    onMainTrack: (String) -> Unit,
    onSoloTrack: (Boolean) -> Unit,
    themeManager: ThemeManager,
) {
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    val content: @Composable () -> Unit = {
        PracticeSettingsContent(
            state, tab, onTab, onClose, onCategory, onRepeats, onGuitar, onClick, onMoreExercises,
            onScale, onPhrase, onChord, onRange, onSourceTempo, onMainTrack, onSoloTrack, themeManager,
        )
    }
    if (sidePanel) {
        Surface(
            color = colors.background,
            shadowElevation = 4.dp,
            border = BorderStroke(metrics.borderWidth, colors.border),
            modifier = Modifier.fillMaxSize().testTag("practice-side-panel"),
        ) { content() }
    } else {
        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            scrimColor = colors.scrim,
            containerColor = colors.background,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxHeight(.92f),
        ) {
            ImmersiveOverlayWindow()
            content()
        }
    }
}

/** Keep the same cutout/fullscreen policy for every dialog-owned overlay. */
@Composable
internal fun ImmersiveOverlayWindow() {
    val sheetView = LocalView.current
    DisposableEffect(sheetView) {
        fun applyWindowPolicy() {
            generateSequence(sheetView) { it.parent as? android.view.View }
                .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window?.let(::immersiveStatusBar)
        }
        val attached = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) { applyWindowPolicy() }
            override fun onViewDetachedFromWindow(view: android.view.View) {}
        }
        sheetView.addOnAttachStateChangeListener(attached)
        if (sheetView.isAttachedToWindow) applyWindowPolicy()
        onDispose { sheetView.removeOnAttachStateChangeListener(attached) }
    }
}

@Composable
private fun PracticeSettingsContent(
    state: PracticeSettingsState,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    onCategory: (Int) -> Unit,
    onRepeats: (Int) -> Unit,
    onGuitar: (Boolean) -> Unit,
    onClick: (Boolean) -> Unit,
    onMoreExercises: () -> Unit,
    onScale: (ScaleOptions) -> Unit,
    onPhrase: (Phrase) -> Unit,
    onChord: (Chord) -> Unit,
    onRange: (BarRange) -> Unit,
    onSourceTempo: (Boolean) -> Unit,
    onMainTrack: (String) -> Unit,
    onSoloTrack: (Boolean) -> Unit,
    themeManager: ThemeManager,
) = with(state) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp).navigationBarsPadding().testTag("practice-settings"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("练习配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            PracticeTextButton(onClick = onClose) { Text("完成", fontWeight = FontWeight.Bold) }
        }
        PracticeSegments(listOf("内容", "跟练", "声音", "外观"), tab, onTab, Modifier.fillMaxWidth())
        key(tab, page, chordPhrases) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    3 -> ThemePicker(themeManager)
                    2 -> Label("声音") {
                        Text("立即生效，保留当前播放位置。", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("吉他声", modifier = Modifier.weight(1f))
                            Switch(guitar, onCheckedChange = onGuitar, modifier = Modifier.testTag("guitar-switch"))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("节拍声", modifier = Modifier.weight(1f))
                            Switch(click, onCheckedChange = onClick, modifier = Modifier.testTag("click-switch"))
                        }
                    }
                    1 -> {
                        Text("调整速度模式或次数后，下次从头开始。速度在工作台控台区调整。", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                        Label("跟练次数") {
                            val values = listOf(1, 4, 8, 0)
                            Choices(listOf("1 次", "4 次", "8 次", "持续循环"), values.indexOf(repeats)) { onRepeats(values[it]) }
                        }
                        Label("速度模式") {
                            if (sequence?.runtimePlan != null) {
                                Choices(listOf("原谱速度 × 倍率", "固定四分音符速度"), if (sourceTempo) 0 else 1) { onSourceTempo(it == 0) }
                                if (sourceTempo) Text("保留原谱速度变化；倍率在工作台调整。", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("固定 BPM", style = MaterialTheme.typography.bodyMedium)
                            }
                            val meter = sequence?.runtimePlan?.visits?.getOrNull(range.first)?.measure?.meter
                            Text(
                                (meter?.let { it.numerator.toString() + "/" + it.denominator } ?: "$beatsPerBar/4") + " · 跟练预备 1 小节 · 示范只播 1 遍",
                                style = MaterialTheme.typography.bodySmall,
                                color = PracticeSkin.colors.textSecondary,
                            )
                        }
                    }
                    else -> {
                        Choices(listOf("音阶练习", "和弦学习", "短句练习"), if (page == 0) 0 else if (chordPhrases) 2 else 1) { onCategory(it) }
                        Text("选择后看舞台图示；更改内容后，下次从头开始。", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                        if (page == 0) {
                            Label("常用组合") {
                                val presets = listOf(ScaleOptions(), ScaleOptions(exercise = ScaleExercise.THIRDS), ScaleOptions(exercise = ScaleExercise.CONNECTION, octave = false))
                                Choices(listOf("基础往返", "三度练习", "相邻指型连接"), presets.indexOf(scale)) { onScale(presets[it]) }
                                if (scale !in presets) Text("自定义", style = MaterialTheme.typography.labelSmall, color = PracticeSkin.colors.textSecondary)
                            }
                            Label("选音阶与指型") {
                                Choices(Tonality.entries.map { it.title }, scale.key.ordinal) { onScale(scale.copy(key = Tonality.entries[it])) }
                                Choices(Patterns.all.map { "${it.solfege} · ${it.caged}" }, scale.pattern) { onScale(scale.copy(pattern = it)) }
                                if (scale.pattern == 2) Text("la 指型三弦 4 品 B：可伸指或轻微移位。", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                            }
                            Label("练习方式") {
                                val common = listOf(ScaleExercise.ASCEND, ScaleExercise.THIRDS, ScaleExercise.CONNECTION)
                                Choices(common.map { it.title }, common.indexOf(scale.exercise)) {
                                    val kind = common[it]
                                    onScale(scale.copy(exercise = kind, octave = if (kind == ScaleExercise.CONNECTION) false else scale.octave))
                                }
                                PracticeTextButton(onClick = onMoreExercises, modifier = Modifier.testTag("more-exercises-toggle")) {
                                    Text(if (moreExercisesOpen) "收起更多练法 ▴" else "更多练法 ▾")
                                }
                                if (moreExercisesOpen) {
                                    val more = listOf(ScaleExercise.ROOTS, ScaleExercise.THREE, ScaleExercise.CHANGE_START)
                                    Choices(more.map { it.title }, more.indexOf(scale.exercise)) { onScale(scale.copy(exercise = more[it])) }
                                }
                                PracticeTextButton(onClick = { advanced = !advanced }, modifier = Modifier.testTag("advanced-toggle")) {
                                    Text(if (advanced) "高级 ▴" else "高级 ▾")
                                }
                                if (advanced) {
                                    if (scale.exercise != ScaleExercise.ROOTS && scale.exercise != ScaleExercise.CONNECTION) {
                                        Choices(listOf("一八度", "整型音域"), if (scale.octave) 0 else 1) { onScale(scale.copy(octave = it == 0)) }
                                    }
                                    Choices(listOf("每拍1音", "每拍2音", "每拍3音"), scale.notesPerBeat - 1) { onScale(scale.copy(notesPerBeat = it + 1)) }
                                    if (scale.exercise == ScaleExercise.CHANGE_START) {
                                        Text("起点级数")
                                        Choices((1..7).map(Int::toString), scale.startDegree - 1) { onScale(scale.copy(startDegree = it + 1)) }
                                    }
                                }
                            }
                        } else if (chordPhrases) {
                            Label("选择短句") {
                                catalogueGroups.forEach { (use, items) ->
                                    Text(use, style = MaterialTheme.typography.labelLarge, color = PracticeSkin.colors.primary)
                                    Choices(items.map { it.title }, items.indexOfFirst { it.id == family.id }) { onPhrase(items[it]) }
                                }

                                PracticeTextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.testTag("phrase-details-toggle")) {
                                    Text(if (detailsOpen) "收起版本、选段与轨道 ▴" else "版本、选段与轨道 ▾")
                                }
                                if (detailsOpen) {
                                    if (versions.size > 1) {
                                        val base = family
                                        var versionsOpen by remember(base.id) { mutableStateOf(phrase.relatedId != null) }
                                        PracticeTextButton(onClick = { versionsOpen = !versionsOpen }, modifier = Modifier.testTag("phrase-versions-toggle")) {
                                            Text(if (versionsOpen) "收起更多版本 ▴" else "更多版本 ▾")
                                        }
                                        if (versionsOpen) Choices(versions.map { it.version ?: it.title }, versions.indexOfFirst { it.id == phrase.id }) { onPhrase(versions[it]) }
                                    }
                                    Text("${phrase.passage} · ${phrase.conditions}", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                                    Text("${phrase.technique} · ${phrase.tempoLabel} ${phrase.sourceBpm} BPM · ${phrase.difficulty}", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                                    Text("${phrase.category} · ${phrase.source}", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                                    Text(phrase.sequence.explanation, style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                                    Text("目标：${phrase.goal} 建议起步 ${phrase.suggestedBpm} BPM。", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.primary)
                                    phrase.relatedId?.let { Text("基础素材：${relatedTitle}", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary) }
                                }
                            }
                            if (detailsOpen) {
                                phrase.sequence.runtimePlan?.let { plan ->
                                    val tracks = plan.score.tracks.filter { it.id in plan.practice.mixTrackIds }
                                    if (tracks.size > 1) Label("主练轨与声音") {
                                        Choices(tracks.map { it.name }, tracks.indexOfFirst { it.id == (mainTrackId ?: plan.practice.mainTrackId) }) {
                                            onMainTrack(tracks[it].id)
                                        }
                                        Choices(listOf("混合参与轨", "仅听主练轨"), if (soloTrack) 1 else 0) { onSoloTrack(it == 1) }
                                    }
                                }
                                if (!phrase.unprepared) Label("练习小节") {
                                    Text("当前范围：${phrase.sequence.barLabel(range.first)}–${phrase.sequence.barLabel(range.last)} 小节；完整经过范围一次计一轮。", modifier = Modifier.testTag("phrase-range"))
                                    OutlinedButton(
                                        onClick = { if (range != wholeRange) onRange(wholeRange) },
                                        modifier = Modifier.then(if (tabletControls) Modifier.heightIn(min = 52.dp) else Modifier),
                                        shape = MaterialTheme.shapes.small,
                                    ) { Text("整句") }
                                    val bars = (0..wholeRange.last).toList()
                                    Text("起始小节")
                                    Choices(bars.map { phrase.sequence.barLabel(it) }, range.first) { if (it != firstBar) onRange(BarRange(it, maxOf(lastBar, it))) }
                                    Text("结束小节")
                                    val endings = (range.first..wholeRange.last).toList()
                                    Choices(endings.map { phrase.sequence.barLabel(it) }, endings.indexOf(range.last)) { if (endings[it] != lastBar) onRange(BarRange(firstBar, endings[it])) }
                                }
                            }
                        } else {
                            Label("选择和弦") {
                                chordGroups.forEach { (group, items) ->
                                    Text(group, fontWeight = FontWeight.Bold, color = PracticeSkin.colors.primary)
                                    Choices(items.map { it.name }, items.indexOf(first)) { onChord(items[it]) }
                                }
                                if (first.source.isNotBlank()) Text("${first.conditions}。${first.source}", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.primary)
                                Text("先看根音和组成音，点图中的音位试听，再听整和弦。", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
                            }
                        }
                        if (sequence == null) {
                            Text(
                                unavailableReason.ifBlank { if (page == 0) scaleResult.reason else "正在准备曲目…" },
                                color = PracticeSkin.colors.danger,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
