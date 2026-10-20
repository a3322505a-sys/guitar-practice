package com.a3322505a.guitarlearning.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.music.*

internal data class BoardTeachingContext(val key: KeyContext? = null, val chord: Chord = Chords.named("C"), val reference: Boolean = false) {
    val root get() = key?.tonic?.pitchClass ?: chord.root.takeIf { it >= 0 }
    val chordTones get() = chord.positions.map { chord.midi(it)%12 }.toSet()
    fun emphasisRoot(hint: Hint) = if(hint==Hint.CHORD_TONES) chord.root.takeIf { it >= 0 } else root
    val available get() = buildList {
        add(Hint.FULL)
        if(key != null) add(Hint.PENTATONIC)
        add(Hint.CHORD_TONES)
        if(root != null) add(Hint.ROOTS)
        add(Hint.HIDDEN)
    }
    fun title(hint: Hint) = when(hint) {
        Hint.ROOTS -> if(key != null) "调式主音 · ${key.tonic.name}" else "和弦根音 · ${chord.soundingName}"
        Hint.CHORD_TONES -> "${if(reference) "参照和弦音" else "和弦音"} · ${chord.soundingName}"
        else -> hint.title
    }
    fun emphasized(hint: Hint, midi: Int) = when(hint) {
        Hint.PENTATONIC -> midi%12 in key?.pentatonic.orEmpty()
        Hint.CHORD_TONES -> midi%12 in chordTones
        else -> true
    }
}
internal val LocalBoardTeaching = staticCompositionLocalOf { BoardTeachingContext() }
internal val LocalSelectHint = staticCompositionLocalOf<((Hint)->Unit)?> { null }
internal val LocalSelectReferenceChord = staticCompositionLocalOf<((String)->Unit)?> { null }

@androidx.compose.runtime.Composable
internal fun FretboardHeaderStrip(hint: Hint, nextChord: String) {
    val teaching = LocalBoardTeaching.current
    val onHint = LocalSelectHint.current
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).heightIn(min = 48.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.Text(
            (teaching.key?.let { "${it.tonic.name} ${it.scale.title}" } ?: "当前和弦：${teaching.chord.soundingName}") +
                nextChord.takeIf { it.isNotBlank() }?.let { " · 即将进入：$it" }.orEmpty(),
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 10.dp),
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        )
        teaching.available.forEach { mode ->
            androidx.compose.material3.FilterChip(selected = mode == hint, onClick = { onHint?.invoke(mode) }, enabled = onHint != null,
                label = { androidx.compose.material3.Text(when (mode) {
                    Hint.FULL -> "全部"; Hint.PENTATONIC -> "五声"; Hint.CHORD_TONES -> "和弦音"; Hint.ROOTS -> "根音"; Hint.HIDDEN -> "隐藏"
                }) }, modifier = androidx.compose.ui.Modifier.heightIn(min = 48.dp))
        }
    }
}
