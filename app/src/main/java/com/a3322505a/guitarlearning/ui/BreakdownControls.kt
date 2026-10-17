package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun BreakdownControls(ready: Boolean, previous: Boolean, next: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onAudition: () -> Unit) {
    Surface(color=PracticeSkin.colors.surfaceSecondary,shape=RoundedCornerShape(24.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).testTag("breakdown-controls"),
            verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onPrevious,enabled=previous,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=6.dp)) {
                Text("◀ 上一步",style=MaterialTheme.typography.labelMedium,maxLines=1)
            }
            VerticalDivider(Modifier.height(16.dp))
            TextButton(onAudition,enabled=ready,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=6.dp)) {
                Text("🔊 听当前",style=MaterialTheme.typography.labelMedium,maxLines=1)
            }
            VerticalDivider(Modifier.height(16.dp))
            TextButton(onNext,enabled=next,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=6.dp)) {
                Text("下一步 ▶",style=MaterialTheme.typography.labelMedium,maxLines=1)
            }
        }
    }
}
