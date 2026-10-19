package com.a3322505a.guitarlearning.ui

import androidx.compose.runtime.*
import com.a3322505a.guitarlearning.music.*

internal data class BoardTeachingContext(val key: KeyContext? = null, val chord: Chord? = null) {
    val root get() = key?.tonic?.pitchClass ?: chord?.root?.takeIf { it >= 0 }
    val chordTones get() = chord?.positions?.map { chord.midi(it)%12 }?.toSet()
    fun emphasisRoot(hint: Hint) = if(hint==Hint.CHORD_TONES) chord?.root?.takeIf { it >= 0 } else root
    val available get() = buildList {
        add(Hint.FULL)
        if(key != null) add(Hint.PENTATONIC)
        if(!chordTones.isNullOrEmpty()) add(Hint.CHORD_TONES)
        if(root != null) add(Hint.ROOTS)
        add(Hint.HIDDEN)
    }
    fun title(hint: Hint) = when(hint) {
        Hint.ROOTS -> if(key != null) "调式主音 · ${key.tonic.name}" else "和弦根音 · ${chord?.soundingName}"
        Hint.CHORD_TONES -> "和弦音 · ${chord?.soundingName}"
        else -> hint.title
    }
    fun emphasized(hint: Hint, midi: Int) = when(hint) {
        Hint.PENTATONIC -> midi%12 in key?.pentatonic.orEmpty()
        Hint.CHORD_TONES -> midi%12 in chordTones.orEmpty()
        else -> true
    }
}
internal val LocalBoardTeaching = staticCompositionLocalOf { BoardTeachingContext() }
internal val LocalSelectHint = staticCompositionLocalOf<(Hint)->Unit> { {} }
