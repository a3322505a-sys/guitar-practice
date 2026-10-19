package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.selected
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
                            modifier = Modifier.weight(1f).heightIn(min = if (large) 52.dp else 48.dp).semantics { this.selected = checked },
                            shape = MaterialTheme.shapes.small,
                            color = if (checked) colors.primaryContainer else colors.surface,
                            border = BorderStroke(if (checked) 1.5.dp else metrics.borderWidth, if (checked) colors.primary else colors.border),
                        ) {
                            Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    options[i],
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                                    color = if (checked) colors.primaryText else colors.textSecondary,
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
                            color = colors.primaryText,
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
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text("调整 ▾", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.primaryText)
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
    extraActions: @Composable () -> Unit = {},
) {
    val colors = PracticeSkin.colors
    var moreOpen by remember { mutableStateOf(false) }
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val actions: @Composable (Boolean) -> Unit = { compact ->
        TextButton(
            onClick = {
                onStageLayoutChange(if (stageLayout == StageLayout.TAB_ONLY) { if (linkedAvailable) StageLayout.LINKED else StageLayout.FRETBOARD_ONLY } else StageLayout.TAB_ONLY)
            },
            modifier = Modifier.heightIn(min = 48.dp).testTag("dock-toggle"),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(when (stageLayout) {
                StageLayout.LINKED -> "收起指板"
                StageLayout.TAB_ONLY -> if (linkedAvailable) "展开指板" else "指板"
                StageLayout.FRETBOARD_ONLY -> "六线谱"
            }, maxLines = 1)
        }
        if (!compact) TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp).testTag("preferences-button")) { Text("偏好") }
        extraActions()
        Box {
            IconButton(onClick = { moreOpen = true }, modifier = Modifier.size(48.dp).testTag("management-button").semantics { contentDescription = if (compact) "偏好与更多" else "更多" }) {
                Text(if (compact) "⚙" else "⋯", fontSize = 22.sp)
            }
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                if (compact) DropdownMenuItem(text = { Text("练习偏好") }, onClick = { moreOpen = false; onOpenSettings() })
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
        val compact = maxWidth < 520.dp * scale
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            summary(Modifier.weight(1f))
            actions(compact)
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
    mix: com.a3322505a.guitarlearning.audio.PlaybackMix = com.a3322505a.guitarlearning.audio.PlaybackMix(),
    onMix: (com.a3322505a.guitarlearning.audio.PlaybackMix) -> Unit = {},
    onSourceFilter: (String) -> Unit = {},
    onManagePackages: () -> Unit = {},
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().padding(14.dp).navigationBarsPadding().testTag("practice-settings"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (contentOnly) "练习内容" else "练习偏好", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("完成") }
            }
            if (contentOnly) {
                WorkbenchContentChoices(state, onCategory, onScale, onChord, onPhrase, onRange, Modifier.weight(1f), onSourceFilter, onManagePackages)
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
                            if (state.sequence?.runtimePlan?.tempos?.size == 1) Label("速度模式") {
                                Choices(listOf("保留原谱变速", "固定 BPM"), if (state.sourceTempo) 0 else 1) { onSourceTempo(it == 0) }
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
                            PlaybackVolumeControls(mix, onMix)
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
internal fun WorkbenchContentChoices(
    state: PracticeSettingsState,
    onCategory: (Int) -> Unit,
    onScale: (ScaleOptions) -> Unit,
    onChord: (Chord) -> Unit,
    onPhrase: (com.a3322505a.guitarlearning.practice.Phrase) -> Unit,
    onRange: (com.a3322505a.guitarlearning.practice.BarRange) -> Unit,
    modifier: Modifier,
    onSourceFilter: (String) -> Unit = {},
    onManagePackages: () -> Unit = {},
) = with(state) {
    val effectiveSource = content.effectiveSource(activeSourceFilter, chordPhrases)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PracticeSegments(TeachingModule.entries.map { it.title }, module.ordinal, onCategory, Modifier.fillMaxWidth())
        if(module==TeachingModule.APPLICATION) {
            val demo=content.phraseSections(BASIC_CONTENT,TeachingModule.APPLICATION).flatMap { it.items }.firstOrNull { it.lesson?.leadTrackIds?.isNotEmpty()==true }
            if(demo!=null) TextButton(onClick={ onSourceFilter(BASIC_CONTENT);onPhrase(demo) },modifier=Modifier.testTag("p5-direct-entry")) {
                Text("领奏渐隐与倒序接龙 · 双轨练习")
            }
        }
        if (page != 0) {
            val sourceScroll = rememberLazyListState()
            LaunchedEffect(effectiveSource, content.sources) {
                val index = content.sources.indexOfFirst { it.id == effectiveSource }
                if (index >= 0) sourceScroll.animateScrollToItem(index)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(Modifier.weight(1f).testTag("content-source-filters"), state = sourceScroll, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(content.sources, key = { it.id }) { source ->
                        FilterChip(
                            selected = effectiveSource == source.id,
                            onClick = { onSourceFilter(source.id) },
                            label = { Text(source.title, maxLines = 1) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("content-source-${source.id}"),
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
                AssistChip(
                    onClick = onManagePackages,
                    label = { Text("管理") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("content-package-management")
                        .semantics { contentDescription = "管理导入曲包" },
                    shape = RoundedCornerShape(50),
                )
            }
        }
        key(page, chordPhrases, effectiveSource) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("content-card-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page == 0) {
                    val lessons=content.phraseSections(BASIC_CONTENT,TeachingModule.SCALES).flatMap { it.items }
                    if(lessons.isNotEmpty()) ScaleContentSection("标准短句 · 单八度、五声与封闭 Box") {
                        ContentCards(lessons.map { it.title },-1,cardTexts=lessons.map(content::phraseText)) { onPhrase(lessons[it]) }
                    }
                    ScaleContentSection("1. 调性 · 关系大小调") {
                        ContentCards(Tonality.entries.map { it.title }, scale.key.ordinal,
                            cardTexts = Tonality.entries.map(::tonalityText), singleLinePrimary = true) { onScale(scale.copy(key = Tonality.entries[it])) }
                    }
                    Label("和弦音靶心 · 明确选择") {
                        Choices(listOf("C","Am","G","D","Em","Bm","Fmaj7"),listOf("C","Am","G","D","Em","Bm","Fmaj7").indexOf(first.name)) {
                            onChord(Chords.named(listOf("C","Am","G","D","Em","Bm","Fmaj7")[it]))
                        }
                    }
                    Choices(listOf("完整七声", "五声骨架"), if(scale.pentatonic) 1 else 0) { onScale(scale.copy(pentatonic=it==1)) }
                    ScaleContentSection("2. 把位指型 · 五指型串联") {
                        ContentCards(Patterns.forKey(scale.key,scale.pentatonic).map { it.title }, scale.pattern,
                            cardTexts = Patterns.forKey(scale.key,scale.pentatonic).map(::patternText), singleLinePrimary = true) { onScale(scale.copy(pattern = it)) }
                    }
                    ScaleContentSection("3. 练习模式 · 模进与走向") {
                        ContentCards(com.a3322505a.guitarlearning.practice.ScaleExercise.entries.map { it.title }, scale.exercise.ordinal,
                            cardTexts = com.a3322505a.guitarlearning.practice.ScaleExercise.entries.map(::scaleExerciseText), singleLinePrimary = true) {
                            val exercise = com.a3322505a.guitarlearning.practice.ScaleExercise.entries[it]
                            onScale(scale.copy(exercise = exercise, octave = if (exercise == com.a3322505a.guitarlearning.practice.ScaleExercise.CONNECTION) false else scale.octave))
                        }
                        if (scale.exercise == com.a3322505a.guitarlearning.practice.ScaleExercise.CHANGE_START) Choices(scale.key.mode.degrees, scale.startDegree - 1) { onScale(scale.copy(startDegree = it + 1)) }
                    }
                    ScaleContentSection("4. 音域范围") {
                        ContentCards(listOf("整型音域", "一八度"), if (scale.octave) 1 else 0,
                            cardTexts = rangeCardTexts, singleLinePrimary = true) { onScale(scale.copy(octave = it == 1)) }
                    }
                    ScaleContentSection("5. 节奏细分") {
                        ContentCards(rhythmCardTexts.map { it.primary }, scale.notesPerBeat - 1,
                            cardTexts = rhythmCardTexts, singleLinePrimary = true) { onScale(scale.copy(notesPerBeat = it + 1)) }
                    }
                } else if (module == TeachingModule.CHORDS && !chordPhrases) {
                    val pairSections = content.phraseSections(effectiveSource,TeachingModule.CHORDS)
                    pairSections.forEach { section ->
                        ContentSectionTitle("双和弦转换", section.items.size)
                        ContentCards(section.items.map { it.title }, -1, cardTexts=section.items.map(content::phraseText)) { onPhrase(section.items[it]) }
                    }
                    val sections = content.chordSections(effectiveSource)
                    sections.forEach { (group, items) ->
                        if (showContentSectionTitles(effectiveSource, sections.size)) ContentSectionTitle(group, items.size)
                        ContentCards(items.map { it.name }, items.indexOfFirst { it.id == first.id || ChordSlots.slot(first)?.original==it.name }, compact = true,
                            cardTexts = items.map(content::chordText)) { onChord(items[it]) }
                    }
                } else {
                    val sections = content.phraseSections(effectiveSource,module)
                    sections.forEach { (group, items) ->
                        if (showContentSectionTitles(effectiveSource, sections.size)) ContentSectionTitle(group, items.size)
                        ContentCards(items.map { it.title }, items.indexOfFirst { it.id == family.id },
                            cardTexts = items.map(content::phraseText)) { onPhrase(items[it]) }
                    }
                    if (content.sourceOf(family) == effectiveSource) {
                        if (versions.size > 1) Choices(versions.map { it.version ?: it.title }, versions.indexOfFirst { it.id == phrase.id }) { onPhrase(versions[it]) }
                        var details by rememberSaveable(phrase.id) { mutableStateOf(false) }
                        TextButton(onClick = { details = !details }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (details) "收起段落与版本" else "段落与版本 ▾") }
                        if (details && !phrase.unprepared) {
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
                }
                if (sequence == null) Text(unavailableReason.ifBlank { "正在准备内容…" }, color = PracticeSkin.colors.danger)
            }
        }
    }
}

@Composable
private fun ScaleContentSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("scale-section-$title"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PracticeSkin.colors.textPrimary)
        content()
    }
}

@Composable
private fun ContentSectionTitle(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().testTag("content-section-$title"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PracticeSkin.colors.textPrimary)
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = PracticeSkin.colors.textSecondary)
    }
}
