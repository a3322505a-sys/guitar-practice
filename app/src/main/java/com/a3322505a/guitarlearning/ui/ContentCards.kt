package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ContentCards(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=(maxWidth/(144.dp*fontScale)).toInt().coerceIn(1,6)
        val width=(maxWidth-8.dp*(columns-1))/columns
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            titles.forEachIndexed { i,title ->
                OutlinedButton({onSelect(i)},modifier=Modifier.width(width).heightIn(min=56.dp),
                    shape=MaterialTheme.shapes.medium,
                    border=BorderStroke(1.dp,if(i==selected) PracticeSkin.colors.primary else PracticeSkin.colors.border),
                    contentPadding=PaddingValues(10.dp)) { Text(title) }
            }
        }
    }
}
