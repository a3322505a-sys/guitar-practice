package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun BreakdownControls(ready: Boolean, previous: Boolean, next: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onAudition: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("breakdown-controls"),
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onPrevious,enabled=previous,modifier=Modifier.heightIn(min=48.dp)) { Text("上一步") }
        TextButton(onNext,enabled=next,modifier=Modifier.heightIn(min=48.dp)) { Text("下一步") }
        TextButton(onAudition,enabled=ready,modifier=Modifier.heightIn(min=48.dp)) { Text("听当前音") }
    }
}
