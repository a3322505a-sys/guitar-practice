package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.a3322505a.guitarlearning.audio.PlaybackMix
import kotlin.math.roundToInt

@Composable
internal fun PlaybackVolumeControls(mix: PlaybackMix, onMix: (PlaybackMix) -> Unit) {
    Text("节拍音量 ${(mix.metronome * 100).roundToInt()}%")
    Slider(mix.metronome.toFloat(), { onMix(mix.copy(metronome = it.toDouble())) },
        modifier = Modifier.testTag("metronome-volume"))
    Text("音乐音量 ${(mix.music * 100).roundToInt()}%")
    Slider(mix.music.toFloat(), { onMix(mix.copy(music = it.toDouble())) },
        modifier = Modifier.testTag("music-volume"))
}

@Composable
internal fun PlaybackVolumeDialog(mix: PlaybackMix, onMix: (PlaybackMix) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("音量") },
        text = { Column { PlaybackVolumeControls(mix, onMix) } },
        confirmButton = { TextButton(onClick = onClose) { Text("完成") } })
}
