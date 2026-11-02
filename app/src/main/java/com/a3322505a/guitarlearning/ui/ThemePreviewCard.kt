package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A preview uses the candidate's tokens; selecting it changes the enclosing app immediately. */
@Composable
fun ThemePreviewCard(theme: AppThemeSpec, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val c = theme.colors
    val m = theme.metrics
    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth().testTag("theme-${theme.id}").semantics { this.selected = selected },
        shape = RoundedCornerShape(m.cornerLarge),
        color = c.surfaceSecondary,
        contentColor = c.textPrimary,
        border = BorderStroke(if (selected) 2.dp else m.borderWidth, if (selected) c.primary else c.border),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(theme.displayName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = c.textPrimary)
                if (selected) {
                    Surface(color = c.primaryContainer, shape = RoundedCornerShape(m.cornerSmall), border = BorderStroke(m.borderWidth, c.primary)) {
                        Text("✓ 当前主题", color = c.primaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Text(theme.description, color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(c.background, c.diagramSurface, c.primary, c.accent).forEach { color ->
                    Surface(
                        Modifier.weight(1f).height(22.dp),
                        color = color,
                        shape = RoundedCornerShape(m.cornerSmall),
                        border = BorderStroke(m.borderWidth, c.border),
                    ) {}
                }
            }
            Surface(color = c.surface, shape = RoundedCornerShape(m.cornerMedium), border = BorderStroke(m.borderWidth, c.border)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(color = c.diagramSurface, shape = RoundedCornerShape(m.cornerSmall), modifier = Modifier.weight(1f)) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(14.dp).background(c.fretboardRoot, CircleShape), contentAlignment = Alignment.Center) {}
                            Box(Modifier.size(12.dp).background(c.fretboardNoteNormal, CircleShape).border(1.dp, c.fretboardNoteStroke, CircleShape))
                            Box(Modifier.size(14.dp).background(c.fretboardNoteActive, CircleShape).border(1.5.dp, c.textPrimary, CircleShape))
                            Text("C · 60 BPM", color = c.textPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Surface(color = c.primary, contentColor = c.onPrimary, shape = RoundedCornerShape(m.cornerSmall)) {
                        Text("播放", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThemePicker(manager: ThemeManager) {
    Text("主题 / 外观", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("点击即刻应用；下次启动保留选择。", style = MaterialTheme.typography.bodySmall, color = PracticeSkin.colors.textSecondary)
    ThemeRegistry.all.forEach { theme ->
        ThemePreviewCard(theme, manager.current.id == theme.id, { manager.select(theme.id) })
    }
    OutlinedButton(onClick = manager::reset, modifier = Modifier.fillMaxWidth().testTag("theme-reset"), shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PracticeSkin.colors.primaryText)) {
        Text("恢复默认主题")
    }
}
