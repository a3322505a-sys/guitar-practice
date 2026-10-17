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
import androidx.compose.runtime.Composable
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
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge
    val widths = options.map { title ->
        with(density) { measurer.measure(title, style = textStyle.copy(fontWeight = FontWeight.Bold), maxLines = 1).size.width.toDp() }
            .plus(style.inset * 2 + 10.dp).coerceAtLeast(style.height)
    }
    val minimumWidth = widths.fold(0.dp) { sum, width -> sum + width }
    BoxWithConstraints(
        modifier
            .height(style.height)
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
                            modifier = Modifier.weight(1f).heightIn(min = if (large) 52.dp else 42.dp),
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
@Composable
internal fun ViewSwitch(tab: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    PracticeSegments(
        listOf("指板", "六线谱"),
        if (tab) 1 else 0,
        onSelect,
        modifier.width(148.dp).testTag("view-switch"),
    )
}

/**
 * L1 + L2 Studio Deck header:
 * - L1 exposes primary modes (音阶 / 和弦 / 短句) directly on the home screen, plus separated GP / 曲库 destination capsules and ⚙ management.
 * - L2 exposes the structured context summary trigger card (`config-summary`) without repeating titles inside the stage.
 */
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
            if (hasNativeGp && onOpenGp != null) {
                Surface(
                    onClick = { onOpenGp(false) },
                    modifier = Modifier.height(metrics.segmentStyle.height).testTag("gp-entry"),
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surface,
                    border = BorderStroke(metrics.borderWidth, colors.border),
                ) {
                    Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text("GP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.primaryText, maxLines = 1)
                    }
                }
                Surface(
                    onClick = { onOpenGp(true) },
                    modifier = Modifier.height(metrics.segmentStyle.height).testTag("gp-library-entry"),
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surface,
                    border = BorderStroke(metrics.borderWidth, colors.border),
                ) {
                    Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text("曲库", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = colors.textPrimary, maxLines = 1)
                    }
                }
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
