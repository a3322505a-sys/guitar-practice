package com.a3322505a.guitarlearning.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.a3322505a.guitarlearning.music.Tonality
import com.a3322505a.guitarlearning.practice.Cursor
import com.a3322505a.guitarlearning.practice.Sequence

/** All content uses one plan, layout and playback projection; legacy adapts at the data boundary. */
@Composable
fun Tablature(sequence: Sequence, hint: Hint, key: Tonality?, cursor: Cursor?, following: Boolean, modifier: Modifier = Modifier) {
    PlanTablature(sequence.performance,hint,cursor,following,modifier,sequence.ticksPerQuarter,key)
}
