package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
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

@Composable
internal fun PracticeMixSheet(
    mix: PlaybackMix, onMix: (PlaybackMix) -> Unit,
    lead: Float, backing: Float, onLead: (Float) -> Unit, onBacking: (Float) -> Unit, onClose: () -> Unit,
) {
    StudioModal(onClose) {
        Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("混音", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) { Text("完成") }
            }
            Text("领奏 ${(lead * 100).roundToInt()}%")
            Slider(lead, onLead, modifier = Modifier.testTag("lead-volume"))
            Text("伴奏 ${(backing * 100).roundToInt()}%")
            Slider(backing, onBacking, modifier = Modifier.testTag("backing-volume"))
            PlaybackVolumeControls(mix, onMix)
        }
    }
}
