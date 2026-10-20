package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal sealed class WorkbenchDestination(val id: String, val title: String) {
    data object Curriculum : WorkbenchDestination("curriculum", "课程与曲库")
    data object Chords : WorkbenchDestination("chords", "和弦词典")
    data object Scales : WorkbenchDestination("scales", "音阶词典")
    companion object {
        val all = listOf(Curriculum, Chords, Scales)
        fun fromId(id: String) = all.firstOrNull { it.id == id } ?: Curriculum
    }
}

/** One modal window owns scrim hit testing; panel gestures cannot reach the native score. */
@Composable
internal fun StudioModal(onDismiss: () -> Unit, wideContent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = PracticeSkin.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersiveOverlayWindow()
        BoxWithConstraints(Modifier.fillMaxSize().testTag("studio-modal")) {
            Box(Modifier.fillMaxSize().background(colors.scrim).testTag("modal-scrim")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            val width = when (studioWidth(maxWidth)) {
                StudioWidth.Compact -> maxWidth
                StudioWidth.Medium -> if (wideContent) maxWidth * .94f else minOf(maxWidth, 420.dp)
                StudioWidth.Expanded -> if (wideContent) minOf(960.dp, maxWidth * .82f) else 420.dp
            }
            Surface(
                modifier = Modifier.align(if (maxWidth < 600.dp) Alignment.BottomCenter else Alignment.CenterEnd)
                    .width(width).fillMaxHeight(if (maxWidth < 600.dp) .88f else 1f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    },
                color = colors.background,
            ) {
                Column(Modifier.fillMaxSize().navigationBarsPadding().displayCutoutPadding(), content = content)
            }
        }
    }
}
