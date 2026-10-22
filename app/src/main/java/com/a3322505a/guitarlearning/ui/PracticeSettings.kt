package com.a3322505a.guitarlearning.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.a3322505a.guitarlearning.immersiveStatusBar
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

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
