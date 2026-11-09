package com.a3322505a.guitarlearning.ui

import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.a3322505a.guitarlearning.R

/** Bundled Material Symbols Outlined, 24px / weight 400. See third_party license. */
internal enum class AppSymbol(@DrawableRes val resource: Int) {
    Back(R.drawable.ms_arrow_back), Previous(R.drawable.ms_chevron_left), Next(R.drawable.ms_chevron_right),
    Expand(R.drawable.ms_keyboard_arrow_down), Collapse(R.drawable.ms_keyboard_arrow_up),
    Play(R.drawable.ms_play_arrow), Pause(R.drawable.ms_pause), Star(R.drawable.ms_star),
    StarBorder(R.drawable.ms_star_border), Import(R.drawable.ms_folder_open), Restart(R.drawable.ms_restart_alt),
    Volume(R.drawable.ms_volume_up), Check(R.drawable.ms_check), Close(R.drawable.ms_close),
    Add(R.drawable.ms_add), Remove(R.drawable.ms_remove), More(R.drawable.ms_more_vert), Repeat(R.drawable.ms_repeat), Tempo(R.drawable.ms_speed), Beat(R.drawable.ms_timer),
}

@Composable
internal fun AppIcon(
    symbol: AppSymbol, contentDescription: String?, modifier: Modifier = Modifier,
    size: Dp = 20.dp, tint: Color = LocalContentColor.current,
) = Icon(painterResource(symbol.resource), contentDescription, modifier.size(size), tint)

/** Icon-only actions always reserve their own 48dp touch target. */
@Composable
internal fun AppIconButton(
    symbol: AppSymbol, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    IconButton(onClick, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled) {
        AppIcon(symbol, description)
    }
}

internal enum class AppButtonSize { Sm, Md, Lg }
internal enum class AppButtonStyle { Primary, Secondary, Quiet }
internal enum class AppCardVariant { Normal, Banner }

@Composable
internal fun AppButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: AppButtonSize = AppButtonSize.Md, style: AppButtonStyle = AppButtonStyle.Primary,
    symbol: AppSymbol? = null, enabled: Boolean = true,
) {
    val c = PracticeSkin.colors
    val m = PracticeSkin.metrics
    val height = when (size) {
        AppButtonSize.Sm -> m.buttonStyle.minHeightSmall
        AppButtonSize.Md -> m.buttonStyle.minHeight
        AppButtonSize.Lg -> m.transportButtonHeight
    }
    val target = modifier.minimumInteractiveComponentSize().defaultMinSize(minWidth = 1.dp, minHeight = height)
    val padding = PaddingValues(horizontal = m.buttonStyle.horizontalPadding, vertical = if (size == AppButtonSize.Sm) 4.dp else 8.dp)
    val shape = RoundedCornerShape(m.cornerSmall)
    val label: @Composable RowScope.() -> Unit = {
        if (symbol != null) {
            AppIcon(symbol, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    when (style) {
        AppButtonStyle.Primary -> Button(onClick, target, enabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary,
                disabledContainerColor = c.surfaceHigh, disabledContentColor = c.textSecondary), content = label)
        AppButtonStyle.Secondary -> OutlinedButton(onClick, target, enabled, shape = shape, contentPadding = padding,
            border = BorderStroke(m.borderWidth, c.borderSubtle),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.primaryText, disabledContentColor = c.textSecondary), content = label)
        AppButtonStyle.Quiet -> TextButton(onClick, target, enabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(contentColor = c.primaryText, disabledContentColor = c.textSecondary), content = label)
    }
}

/** Only navigation + page/song title live here. Section navigation belongs below it. */
@Composable
internal fun AppTopBar(
    title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null,
    backModifier: Modifier = Modifier, backDescription: String = "返回",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(modifier.fillMaxWidth(), color = PracticeSkin.colors.background) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onBack != null) AppIconButton(AppSymbol.Back, backDescription, onBack, backModifier)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            actions()
        }
    }
}

/** Independent surface / hit regions: previous | section picker | next. */
@Composable
internal fun SecondaryBar(
    title: String, onSelect: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    modifier: Modifier = Modifier, previousEnabled: Boolean = true, nextEnabled: Boolean = true,
    selectionEnabled: Boolean = true, previousModifier: Modifier = Modifier,
    selectionModifier: Modifier = Modifier, nextModifier: Modifier = Modifier, supportingText: String? = null,
) {
    Surface(modifier.fillMaxWidth(), color = PracticeSkin.colors.surfaceLow) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIconButton(AppSymbol.Previous, "上一段", onPrevious, previousModifier, enabled = previousEnabled)
            TextButton(onSelect, selectionModifier.weight(1f).heightIn(min = 48.dp), enabled = selectionEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = PracticeSkin.colors.textPrimary)) {
                Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!supportingText.isNullOrBlank()) Text(supportingText, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = PracticeSkin.colors.textSecondary)
                }
                Spacer(Modifier.width(4.dp))
                AppIcon(AppSymbol.Expand, null, size = 16.dp)
            }
            AppIconButton(AppSymbol.Next, "下一段", onNext, nextModifier, enabled = nextEnabled)
        }
    }
}

@Composable
internal fun FilterChips(
    labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(selectedIndex == index, { onSelect(index) }, label = { Text(label) },
                modifier = Modifier.minimumInteractiveComponentSize(), shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PracticeSkin.colors.surfaceSelected,
                    selectedLabelColor = PracticeSkin.colors.primaryText))
        }
    }
}

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier, variant: AppCardVariant = AppCardVariant.Normal,
    contentPadding: PaddingValues = PaddingValues(16.dp), contentSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PracticeSkin.colors
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(PracticeSkin.metrics.cornerMedium),
        color = if (variant == AppCardVariant.Banner) c.surfaceSelected else c.surfaceLow,
        contentColor = c.textPrimary, border = BorderStroke(PracticeSkin.metrics.borderWidth, c.borderSubtle)) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(contentSpacing), content = content)
    }
}

/** The trailing action is a separate control; it never shares the row callback. */
@Composable
internal fun AppListRow(
    title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null,
    enabled: Boolean = true, selected: Boolean = false, leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { this.selected = selected }, enabled = enabled,
        color = if (selected) PracticeSkin.colors.surfaceSelected else PracticeSkin.colors.surfaceLow, contentColor = PracticeSkin.colors.textPrimary) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (leading != null) leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium,
                    color = PracticeSkin.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppBottomSheet(
    title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PracticeSkin.colors.surfaceLow, contentColor = PracticeSkin.colors.textPrimary) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            AppIconButton(AppSymbol.Close, "关闭", onDismiss)
        }
        content()
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** Management tabs share the same independent secondary surface as section controls. */
@Composable
internal fun SecondaryBar(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    TabRow(selectedIndex, modifier.fillMaxWidth(), containerColor = PracticeSkin.colors.surfaceLow,
        contentColor = PracticeSkin.colors.primaryText) {
        labels.forEachIndexed { index, label ->
            Tab(selectedIndex == index, { onSelect(index) }, text = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
internal fun AppChoiceMenu(
    expanded: Boolean, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    onDismiss: () -> Unit, enabled: (Int) -> Boolean = { true },
) {
    DropdownMenu(expanded, onDismiss) {
        labels.forEachIndexed { index, label ->
            DropdownMenuItem(text = { Text(label) }, enabled = enabled(index),
                leadingIcon = { if (selectedIndex == index) AppIcon(AppSymbol.Check, null) },
                onClick = { onSelect(index); onDismiss() })
        }
    }
}

@Composable
internal fun AppDialog(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, modifier = modifier, title = { Text(title) }, text = content,
        confirmButton = { AppButton("完成", onDismiss, size = AppButtonSize.Sm, style = AppButtonStyle.Quiet) })
}

/** Full-screen management keeps the existing immersive window and dismissal behavior. */
@Composable
internal fun AppFullScreenDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersiveOverlayWindow()
        BackHandler(onBack = onDismiss)
        Surface(modifier.fillMaxSize(), color = PracticeSkin.colors.background, content = content)
    }
}
