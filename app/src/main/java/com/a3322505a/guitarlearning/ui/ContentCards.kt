package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ExerciseCards(models: List<ExerciseCardUiModel>, selected: Int, onSelect: (Int) -> Unit) {
    ContentCards(models.map { it.title }, selected, uiModels = models, onSelect = onSelect)
}

/** Cards reflow at window breakpoints; labels never shrink to fit their container. */
@Composable
internal fun ContentCards(
    titles: List<String>, selected: Int, compact: Boolean = false,
    cardTexts: List<ContentCardText>? = null, singleLinePrimary: Boolean = false,
    uiModels: List<ExerciseCardUiModel>? = null, onSelect: (Int) -> Unit,
) {
    val colors = PracticeSkin.colors
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = studioWidth(maxWidth).columns.coerceAtMost(titles.size.coerceAtLeast(1))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            titles.indices.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { i ->
                        val checked = i == selected
                        val model = uiModels?.get(i)
                        val text = cardTexts?.get(i) ?: ContentCardText(model?.title ?: titles[i], model?.subtitle)
                        Surface(
                            onClick = { onSelect(i) },
                            modifier = Modifier.weight(1f).heightIn(min = 68.dp)
                                .then(if (model != null) Modifier.testTag("exercise-${model.key.encoded}") else Modifier)
                                .semantics { this.selected = checked; role = Role.RadioButton },
                            shape = MaterialTheme.shapes.medium,
                            color = if (checked) colors.surfaceSelected else colors.surfaceLow,
                        ) {
                            Row(Modifier.heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(4.dp).height(44.dp).background(if (checked) colors.primary else colors.surfaceLow))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (model != null) Text(
                                        listOfNotNull(model.categoryBadge, model.versionBadge, "重点".takeIf { model.isPinnedFocus }).joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium, color = colors.textSecondary,
                                    )
                                    Text(text.primary, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = if (fontScale > 1.2f) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                                        color = colors.textPrimary)
                                    text.tag?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                                    }
                                    model?.metaSummary?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary) }
                                }
                                if (checked) Text("✓", Modifier.padding(end = 12.dp), color = colors.primaryText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
