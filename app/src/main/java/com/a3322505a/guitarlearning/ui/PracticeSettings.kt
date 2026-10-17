package com.a3322505a.guitarlearning.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.a3322505a.guitarlearning.immersiveStatusBar
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

internal data class PracticeSettingsState(
    val page: Int,
    val chordPhrases: Boolean,
    val tabletControls: Boolean,
    val scale: ScaleOptions,
    val first: Chord,
    val phrase: Phrase,
    val family: Phrase,
    val versions: List<Phrase>,
    val catalogueGroups: Map<String, List<Phrase>>,
    val chordGroups: Map<String, List<Chord>>,
    val relatedTitle: String?,
    val bpm: Int,
    val repeats: Int,
    val firstBar: Int,
    val lastBar: Int,
    val range: BarRange,
    val wholeRange: BarRange,
    val guitar: Boolean,
    val click: Boolean,
    val moreExercisesOpen: Boolean,
    val beatsPerBar: Int,
    val sequence: Sequence?,
    val displayedSequence: Sequence?,
    val scaleResult: RouteResult,
    val sourceTempo: Boolean = false,
    val multiplier: Int = 100,
    val mainTrackId: String? = null,
    val soloTrack: Boolean = false,
    val unavailableReason: String = "",
)

@Composable
internal fun ImmersiveOverlayWindow() {
    val sheetView = LocalView.current
    DisposableEffect(sheetView) {
        fun applyWindowPolicy() {
            generateSequence(sheetView) { it.parent as? android.view.View }
                .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window?.let(::immersiveStatusBar)
        }
        val attached = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) { applyWindowPolicy() }
            override fun onViewDetachedFromWindow(view: android.view.View) {}
        }
        sheetView.addOnAttachStateChangeListener(attached)
        if (sheetView.isAttachedToWindow) applyWindowPolicy()
        onDispose { sheetView.removeOnAttachStateChangeListener(attached) }
    }
}
