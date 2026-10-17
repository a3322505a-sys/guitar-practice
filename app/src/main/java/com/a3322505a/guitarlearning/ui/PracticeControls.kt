package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.ScaleOptions

/** Ordinary content actions retain larger touch bounds on tablets. */
@Composable
internal fun PracticeTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val large = LocalLargePracticeControls.current
    val style = PracticeSkin.metrics.buttonStyle
    TextButton(
        onClick = onClick,
        modifier = modifier.then(if (large) Modifier.sizeIn(minWidth = style.minHeight, minHeight = style.minHeight) else Modifier),
        enabled = enabled,
        colors = colors,
        shape = MaterialTheme.shapes.small,
        contentPadding = if (large) PaddingValues(horizontal = 14.dp, vertical = 8.dp) else contentPadding,
        content = content,
    )
}

@Composable
internal fun ChordSteps(first: Chord, onSelect: (Chord) -> Unit, chords: List<Chord> = Chords.all) {
    val large = LocalLargePracticeControls.current
    PracticeTextButton(
        onClick = { onSelect(chords[chords.indexOf(first) - 1]) },
        enabled = first != chords.first(),
        modifier = Modifier.width(if (large) 52.dp else 40.dp).testTag("previous-chord").semantics { contentDescription = "上一和弦" },
        contentPadding = PaddingValues(0.dp),
    ) { Text("‹", fontSize = if (large) 28.sp else 22.sp) }
    PracticeTextButton(
        onClick = { onSelect(chords[chords.indexOf(first) + 1]) },
        enabled = first != chords.last(),
        modifier = Modifier.width(if (large) 52.dp else 40.dp).testTag("next-chord").semantics { contentDescription = "下一和弦" },
        contentPadding = PaddingValues(0.dp),
    ) { Text("›", fontSize = if (large) 28.sp else 22.sp) }
}

/** One shared segmented control for page modes and short, mutually exclusive settings. */
@Composable
internal fun PracticeSegments(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentSelected: Boolean = false,
) {
    val colors = PracticeSkin.colors
    val style = PracticeSkin.metrics.segmentStyle
    val segmentHeight = style.height.coerceAtLeast(48.dp)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge
    val widths = options.map { title ->
        with(density) { measurer.measure(title, style = textStyle.copy(fontWeight = FontWeight.Bold), maxLines = 1).size.width.toDp() }
            .plus(style.inset * 2 + 10.dp).coerceAtLeast(segmentHeight)
    }
    val minimumWidth = widths.fold(0.dp) { sum, width -> sum + width }
    BoxWithConstraints(
        modifier
            .height(segmentHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceSecondary),
    ) {
        val contentWidth = if (constraints.hasBoundedWidth) maxOf(maxWidth, minimumWidth) else minimumWidth
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).width(contentWidth).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, title ->
                val checked = selected == index
                val selectedBg = if (accentSelected) colors.primary else colors.surface
                val selectedFg = if (accentSelected) colors.onPrimary else colors.textPrimary
                Box(
                    Modifier.weight(widths[index].value).fillMaxHeight()
                        .selectable(selected = checked, enabled = enabled, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(style.inset),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
                            .background(if (checked) selectedBg else Color.Transparent)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            title,
                            color = if (checked) selectedFg else colors.textSecondary,
                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                            style = textStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

/** High-density capsule grid for settings options. */
@Composable
internal fun Choices(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    val large = LocalLargePracticeControls.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val longest = options.maxOfOrNull { it.length } ?: 1
        val target = (if (longest > 8) 148 else if (longest > 4) 104 else 68) * if (large) 1.2f else 1f
        val columns = (maxWidth.value / target).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.indices.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { i ->
                        val checked = selected == i
                        Surface(
                            onClick = { onSelect(i) },
                            modifier = Modifier.weight(1f).heightIn(min = if (large) 52.dp else 48.dp),
                            shape = MaterialTheme.shapes.small,
                            color = if (checked) colors.primaryContainer else colors.surface,
                            border = BorderStroke(if (checked) 1.5.dp else metrics.borderWidth, if (checked) colors.primary else colors.border),
                        ) {
                            Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    options[i],
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                                    color = if (checked) colors.primary else colors.textSecondary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun Label(title: String, content: @Composable ColumnScope.() -> Unit) {
    PracticeContentCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = PracticeSkin.colors.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

/** Dedicated canvas renderer toggle placed in the top-right of the practice stage. */
internal val LocalStageViewSwitch = staticCompositionLocalOf { true }

@Composable
internal fun ViewSwitch(tab: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (!LocalStageViewSwitch.current) return
    PracticeSegments(
        listOf("指板", "六线谱"),
        if (tab) 1 else 0,
        onSelect,
        modifier.width(148.dp).testTag("view-switch"),
    )
}

/** Shared navigation retained for existing score screens. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PracticeNavigation(
    summary: String,
    onSettings: () -> Unit,
    onManagement: () -> Unit,
    compact: Boolean = false,
    views: @Composable () -> Unit = {},
    selectedCategory: Int = 0,
    onCategory: ((Int) -> Unit)? = null,
    specBadge: String = "",
    hasNativeGp: Boolean = false,
    onOpenGp: ((Boolean) -> Unit)? = null,
    verticalDeck: Boolean = false,
) {
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    val modeRow: @Composable () -> Unit = {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (onCategory != null) {
                PracticeSegments(
                    listOf("音阶", "和弦", "短句"),
                    selectedCategory,
                    onCategory,
                    Modifier.width(if (verticalDeck) 186.dp else 174.dp).testTag("mode-switch"),
                    accentSelected = true,
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = onManagement,
                modifier = Modifier.size(metrics.segmentStyle.height).testTag("management-button").semantics { contentDescription = "设置与帮助" },
                shape = MaterialTheme.shapes.medium,
                color = colors.surface,
                border = BorderStroke(metrics.borderWidth, colors.border),
            ) {
                Box(contentAlignment = Alignment.Center) { Text("⚙", fontSize = 21.sp, color = colors.textSecondary) }
            }
        }
    }
    val summaryTrigger: @Composable (Modifier) -> Unit = { triggerModifier ->
        Surface(
            onClick = onSettings,
            modifier = triggerModifier.heightIn(min = metrics.segmentStyle.height).testTag("config-summary"),
            shape = MaterialTheme.shapes.medium,
            color = colors.primaryContainer,
            border = BorderStroke(metrics.borderWidth, colors.primary.copy(alpha = 0.45f)),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.textPrimary,
                    )
                    if (specBadge.isNotBlank() && verticalDeck) {
                        Text(
                            specBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (specBadge.isNotBlank() && !verticalDeck) {
                    Surface(
                        color = colors.surface,
                        shape = RoundedCornerShape(metrics.cornerSmall),
                        border = BorderStroke(metrics.borderWidth, colors.border),
                    ) {
                        Text(
                            specBadge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text("调整 ▾", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.primary)
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().testTag("practice-navigation"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onCategory != null) {
            modeRow()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                summaryTrigger(Modifier.weight(1f))
                views()
            }
        } else {
            // Used by GpPracticeActivity top deck
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                summaryTrigger(Modifier.weight(1f))
                views()
                Surface(
                    onClick = onManagement,
                    modifier = Modifier.size(metrics.segmentStyle.height).testTag("management-button").semantics { contentDescription = "设置与帮助" },
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surface,
                    border = BorderStroke(metrics.borderWidth, colors.border),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("⚙", fontSize = 21.sp, color = colors.textSecondary) }
                }
            }
        }
    }
}


/** The workbench keeps navigation separate from playback and content state. */
@Composable
internal fun CompactTopBar(
    title: String,
    subtitle: String,
    stageLayout: StageLayout,
    onStageLayoutChange: (StageLayout) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onManagement: () -> Unit,
    linkedAvailable: Boolean,
) {
    val colors = PracticeSkin.colors
    var moreOpen by remember { mutableStateOf(false) }
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val actions: @Composable () -> Unit = {
        TextButton(
            onClick = {
                onStageLayoutChange(if (stageLayout == StageLayout.TAB_ONLY) { if (linkedAvailable) StageLayout.LINKED else StageLayout.FRETBOARD_ONLY } else StageLayout.TAB_ONLY)
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("dock-toggle"),
        ) {
            Text(when (stageLayout) {
                StageLayout.LINKED -> "收起指板"
                StageLayout.TAB_ONLY -> if (linkedAvailable) "展开指板" else "指板"
                StageLayout.FRETBOARD_ONLY -> "六线谱"
            }, maxLines = 1)
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp).testTag("preferences-button")) { Text("偏好") }
        Box {
            TextButton(onClick = { moreOpen = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("management-button")) { Text("⋯", fontSize = 22.sp) }
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                DropdownMenuItem(text = { Text("仅指板") }, onClick = { moreOpen = false; onStageLayoutChange(StageLayout.FRETBOARD_ONLY) })
                DropdownMenuItem(text = { Text("仅六线谱") }, onClick = { moreOpen = false; onStageLayoutChange(StageLayout.TAB_ONLY) })
                if (linkedAvailable) DropdownMenuItem(text = { Text("指板 + 六线谱") }, onClick = { moreOpen = false; onStageLayoutChange(StageLayout.LINKED) })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("曲包与帮助") }, onClick = { moreOpen = false; onManagement() })
            }
        }
    }
    val summary: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            onClick = onOpenLibrary,
            modifier = modifier.heightIn(min = 48.dp).testTag("config-summary"),
            shape = MaterialTheme.shapes.medium,
            color = colors.primaryContainer,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(" ▾")
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("practice-navigation")) {
        if (maxWidth >= 520.dp * scale) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                summary(Modifier.weight(1f))
                actions()
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                summary(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { actions() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkbenchPanel(
    state: PracticeSettingsState,
    contentOnly: Boolean,
    sidePanel: Boolean,
    onClose: () -> Unit,
    onCategory: (Int) -> Unit,
    onScale: (ScaleOptions) -> Unit,
    onChord: (Chord) -> Unit,
    onPhrase: (com.a3322505a.guitarlearning.practice.Phrase) -> Unit,
    onRange: (com.a3322505a.guitarlearning.practice.BarRange) -> Unit,
    onRepeats: (Int) -> Unit,
    onSourceTempo: (Boolean) -> Unit,
    onGuitar: (Boolean) -> Unit,
    onClick: (Boolean) -> Unit,
    themeManager: ThemeManager,
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().padding(14.dp).navigationBarsPadding().testTag("practice-settings"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (contentOnly) "练习内容" else "练习偏好", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("完成") }
            }
            if (contentOnly) {
                WorkbenchContentChoices(state, onCategory, onScale, onChord, onPhrase, onRange, Modifier.weight(1f))
            } else {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                PracticeSegments(listOf("跟练", "声音", "外观"), tab, { tab = it }, Modifier.fillMaxWidth())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (tab) {
                        0 -> {
                            Label("跟练次数") {
                                val values = listOf(1, 4, 8, 0)
                                Choices(listOf("1 次", "4 次", "8 次", "持续循环"), values.indexOf(state.repeats)) { onRepeats(values[it]) }
                            }
                            if (state.sequence?.runtimePlan != null) Label("速度模式") {
                                Choices(listOf("原谱速度 × 倍率", "固定 BPM"), if (state.sourceTempo) 0 else 1) { onSourceTempo(it == 0) }
                            }
                            if(state.page==1 && state.chordPhrases) Label("小节范围") {
                                Text("${state.phrase.sequence.barLabel(state.range.first)}–${state.phrase.sequence.barLabel(state.range.last)} 小节")
                                Choices((0..state.wholeRange.last).map { state.phrase.sequence.barLabel(it) },state.range.first) {
                                    onRange(com.a3322505a.guitarlearning.practice.BarRange(it,maxOf(it,state.range.last)))
                                }
                                val ends=(state.range.first..state.wholeRange.last).toList()
                                Choices(ends.map { state.phrase.sequence.barLabel(it) },ends.indexOf(state.range.last)) {
                                    onRange(com.a3322505a.guitarlearning.practice.BarRange(state.range.first,ends[it]))
                                }
                            }
                            Text("速度在底部调整；跟练预备一小节，示范播放一遍。", style = MaterialTheme.typography.bodySmall)
                        }
                        1 -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("吉他声", Modifier.weight(1f)); Switch(state.guitar, onGuitar, Modifier.testTag("guitar-switch"))
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("节拍声", Modifier.weight(1f)); Switch(state.click, onClick, Modifier.testTag("click-switch"))
                            }
                        }
                        else -> ThemePicker(themeManager)
                    }
                }
            }
        }
    }
    if (sidePanel) {
        Surface(Modifier.fillMaxSize().testTag("practice-side-panel"), color = PracticeSkin.colors.background, shadowElevation = 4.dp) { body() }
    } else {
        ModalBottomSheet(onDismissRequest = onClose, containerColor = PracticeSkin.colors.background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), modifier = Modifier.fillMaxHeight(.92f)) {
            ImmersiveOverlayWindow()
            body()
        }
    }
}

@Composable
private fun WorkbenchContentChoices(
    state: PracticeSettingsState,
    onCategory: (Int) -> Unit,
    onScale: (ScaleOptions) -> Unit,
    onChord: (Chord) -> Unit,
    onPhrase: (com.a3322505a.guitarlearning.practice.Phrase) -> Unit,
    onRange: (com.a3322505a.guitarlearning.practice.BarRange) -> Unit,
    modifier: Modifier,
) = with(state) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PracticeSegments(listOf("音阶", "和弦", "歌曲与短句"), if (page == 0) 0 else if (chordPhrases) 2 else 1, onCategory, Modifier.fillMaxWidth())
        if (page == 0) {
            Label("音阶与指型") {
                Choices(Tonality.entries.map { it.title }, scale.key.ordinal) { onScale(scale.copy(key = Tonality.entries[it])) }
                Choices(Patterns.all.map { "${it.solfege} · ${it.caged}" }, scale.pattern) { onScale(scale.copy(pattern = it)) }
            }
            Label("练习方式") {
                Choices(com.a3322505a.guitarlearning.practice.ScaleExercise.entries.map { it.title }, scale.exercise.ordinal) {
                    val exercise = com.a3322505a.guitarlearning.practice.ScaleExercise.entries[it]
                    onScale(scale.copy(exercise = exercise, octave = if (exercise == com.a3322505a.guitarlearning.practice.ScaleExercise.CONNECTION) false else scale.octave))
                }
                Choices(listOf("一八度", "整型音域"), if (scale.octave) 0 else 1) { onScale(scale.copy(octave = it == 0)) }
                Choices(listOf("每拍1音", "每拍2音", "每拍3音"), scale.notesPerBeat - 1) { onScale(scale.copy(notesPerBeat = it + 1)) }
                if (scale.exercise == com.a3322505a.guitarlearning.practice.ScaleExercise.CHANGE_START) Choices((1..7).map(Int::toString), scale.startDegree - 1) { onScale(scale.copy(startDegree = it + 1)) }
            }
        } else if (!chordPhrases) {
            chordGroups.forEach { (group, items) ->
                Label(group) { ContentCards(items.map { it.name }, items.indexOf(first)) { onChord(items[it]) } }
            }
        } else {
            catalogueGroups.forEach { (group, items) ->
                Label(group) { ContentCards(items.map { it.title }, items.indexOfFirst { it.id == family.id }) { onPhrase(items[it]) } }
            }
            var details by rememberSaveable(phrase.id) { mutableStateOf(false) }
            TextButton(onClick = { details = !details }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (details) "收起段落与版本" else "段落与版本 ▾") }
            if (details && !phrase.unprepared) {
                if (versions.size > 1) Choices(versions.map { it.version ?: it.title }, versions.indexOfFirst { it.id == phrase.id }) { onPhrase(versions[it]) }
                Text(phrase.passage + " · " + phrase.conditions, style = MaterialTheme.typography.bodySmall)
                Label("练习范围") {
                    Text("${phrase.sequence.barLabel(range.first)}–${phrase.sequence.barLabel(range.last)} 小节")
                    TextButton(onClick = { onRange(wholeRange) }) { Text("整句") }
                    Text("起始")
                    Choices((0..wholeRange.last).map { phrase.sequence.barLabel(it) }, range.first) { onRange(com.a3322505a.guitarlearning.practice.BarRange(it, maxOf(it, range.last))) }
                    Text("结束")
                    val endings = (range.first..wholeRange.last).toList()
                    Choices(endings.map { phrase.sequence.barLabel(it) }, endings.indexOf(range.last)) { onRange(com.a3322505a.guitarlearning.practice.BarRange(range.first, endings[it])) }
                }
            }
        }
        if (sequence == null) Text(unavailableReason.ifBlank { "正在准备内容…" }, color = PracticeSkin.colors.danger)
    }
}
