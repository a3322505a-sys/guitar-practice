package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.audio.PracticePlayback

/** Explicit historical fixture binding; never included in release or p0. */
@Composable
fun PracticeApp(player: PracticePlayback) {
    PracticeWorkbench(player, null) { state, modifier, onView -> LegacyTabContent(state, modifier, onView) }
}

@Composable
private fun LegacyTabContent(state: PracticeContentState, modifier: Modifier, onView: (Int) -> Unit) = with(state) {
    val colors = PracticeSkin.colors
    Box(modifier.fillMaxWidth()) {
        PracticeContentCard(
            title = "练习谱面",
            actions = { ViewSwitch(true, onView) },
            modifier = Modifier.fillMaxSize(),
            footer = {
                PracticeStatus(status, phase, beatsPerBar, cursor, showingSession, compact = true, note = note)
            },
        ) {
            if (displayedSequence != null) {
                DiagramContainer(Modifier.weight(1f).fillMaxWidth()) {
                    Tablature(
                        displayedSequence,
                        renderHint,
                        if (page == 0) scale.key else null,
                        renderCursor,
                        active || phase == Phase.PAUSED,
                        Modifier.fillMaxSize(),
                    )
                }
            } else {
                Text(scaleResult.reason, color = colors.danger)
            }
        }
    }
}
