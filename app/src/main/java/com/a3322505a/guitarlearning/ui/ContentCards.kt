package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun ContentCards(titles: List<String>, selected: Int, compact: Boolean = false, onSelect: (Int) -> Unit) {
    val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if (compact) when {
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
                        OutlinedButton({onSelect(i)},modifier=Modifier.weight(1f).heightIn(min=if (compact) 48.dp else 56.dp),
                            shape=MaterialTheme.shapes.medium,
                            colors=ButtonDefaults.outlinedButtonColors(contentColor=PracticeSkin.colors.primaryText),
                            border=BorderStroke(1.dp,if(i==selected) PracticeSkin.colors.primary else PracticeSkin.colors.border),
                            contentPadding=if (compact) PaddingValues(horizontal=6.dp,vertical=4.dp) else PaddingValues(10.dp)) {
                            Text(titles[i], style=MaterialTheme.typography.labelLarge)
                        }
                    }
                    repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
