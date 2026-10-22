package com.a3322505a.guitarlearning.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.a3322505a.guitarlearning.audio.PracticePlayback

/** Explicit debug renderer binding; release always uses the single native alphaTab host. */
@Composable
fun PracticeApp(player: PracticePlayback) {
    PracticeWorkbench(player, null) { sequence, cursor, modifier ->
        if (sequence != null) Tablature(sequence, Hint.FULL, null, cursor, player.active, modifier)
    }
}
