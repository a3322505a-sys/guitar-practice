package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ContentCards(titles: List<String>, selected: Int, compact: Boolean = false, cardTexts: List<ContentCardText>? = null, onSelect: (Int) -> Unit) {
    val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if (cardTexts != null) (maxWidth / ((if (compact) 96.dp else 152.dp) * fontScale)).toInt().coerceIn(1, if (compact) 5 else 6)
        else if (compact) when {
            maxWidth < 400.dp*fontScale -> 3
            maxWidth < 480.dp*fontScale -> 4
            else -> 5
        }
            else (maxWidth/(144.dp*fontScale)).toInt().coerceIn(1,6)
        val gap=if (compact) 6.dp else 8.dp
        Column(verticalArrangement=Arrangement.spacedBy(gap)) {
            titles.indices.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
                    row.forEach { i ->
                        if (cardTexts != null) {
                            val checked = i == selected
                            val colors = PracticeSkin.colors
                            Surface(
                                onClick = { onSelect(i) },
                                modifier = Modifier.weight(1f).heightIn(min = if (compact) 60.dp else 76.dp).semantics { this.selected = checked },
                                shape = MaterialTheme.shapes.medium,
                                color = if (checked) colors.primary.copy(alpha = .10f).compositeOver(colors.surface) else colors.surface,
                                border = BorderStroke(if (checked) 1.5.dp else 1.dp, if (checked) colors.primary else colors.border),
                            ) {
                                val text = cardTexts[i]
                                Column(
                                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                                ) {
                                    if (!compact && text.tag != null) Text(text.tag, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                    if (compact) {
                                        // Measure once per layout. Never shrink through a recomposition loop.
                                        val measurer = rememberTextMeasurer()
                                        val style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal)
                                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                                            val width = with(LocalDensity.current) { maxWidth.toPx() }
                                            val measured = measurer.measure(text.primary, style = style, maxLines = 1).size.width
                                            val size = (style.fontSize.value * (width / measured.coerceAtLeast(1)).coerceAtMost(1f)).coerceAtLeast(12f).sp
                                            Text(text.primary, style = style.copy(fontSize = size), color = colors.textPrimary)
                                        }
                                    } else Text(text.primary, style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal, color = colors.textPrimary)
                                    if (compact && text.tag != null) Text(text.tag, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                }
                            }
                        } else {
                        OutlinedButton({onSelect(i)},modifier=Modifier.weight(1f).heightIn(min=if (compact) 48.dp else 56.dp),
                            shape=MaterialTheme.shapes.medium,
                            colors=ButtonDefaults.outlinedButtonColors(contentColor=PracticeSkin.colors.primaryText),
                            border=BorderStroke(1.dp,if(i==selected) PracticeSkin.colors.primary else PracticeSkin.colors.border),
                            contentPadding=if (compact) PaddingValues(horizontal=6.dp,vertical=4.dp) else PaddingValues(10.dp)) {
                            Text(titles[i], style=MaterialTheme.typography.labelLarge)
                        }
                        }
                    }
                    repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
