@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.AlphaTabView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View

/** Mounted in both views, so changing to the fretboard cannot destroy the session. */
@Composable
fun NativeScore(player: AlphaPlayback, modifier: Modifier, visible: Boolean) {
    AndroidView(factory = { context -> AlphaTabView(context, null).also(player::attach) },
        modifier = modifier, update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
        onRelease = { player.detach(it) })
}
