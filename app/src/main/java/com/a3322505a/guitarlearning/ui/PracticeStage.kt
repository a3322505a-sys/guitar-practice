package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.assets.song.LibraryPractice
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*

@Composable
internal fun PackageStageTopBar(
    title: String, sectionTitle: String, chord: String, measure: String,
    practices: List<LibraryPractice>, selectedId: String, expanded: Boolean,
    onExpanded: (Boolean) -> Unit, onPractice: (LibraryPractice) -> Unit, onBack: () -> Unit,
) {
    val selectedIndex = practices.indexOfFirst { it.key.encoded == selectedId }
    val shortWindow = LocalConfiguration.current.screenHeightDp < 480
    Column(Modifier.fillMaxWidth().testTag("stage-header")) {
        AppTopBar(title, Modifier.testTag("stage-top-bar"), onBack,
            backModifier = Modifier.testTag("back-to-library"), backDescription = "返回曲包库")
        SecondaryBar(
            sectionTitle + if (selectedIndex >= 0) " · ${selectedIndex + 1}/${practices.size}" else "",
            onSelect = { onExpanded(true) },
            onPrevious = { practices.getOrNull(selectedIndex - 1)?.let(onPractice) },
            onNext = { practices.getOrNull(selectedIndex + 1)?.let(onPractice) },
            modifier = Modifier.testTag("stage-section-bar"), previousEnabled = selectedIndex > 0,
            nextEnabled = selectedIndex >= 0 && selectedIndex < practices.lastIndex,
            selectionEnabled = practices.isNotEmpty(),
            previousModifier = Modifier.testTag("previous-practice"), selectionModifier = Modifier.testTag("practice-title"),
            nextModifier = Modifier.testTag("next-practice"),
            supportingText = if (shortWindow) null else listOf(chord, measure).filter(String::isNotBlank).joinToString(" · "),
        )
    }
    if (expanded && practices.isNotEmpty()) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
        AppBottomSheet("选择段落", { onExpanded(false) }, Modifier.testTag("practice-menu")) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), state = listState) {
                items(practices, key = { it.key.encoded }) { practice ->
                    val selected = practice.key.encoded == selectedId
                    AppListRow(practice.title, { onPractice(practice); onExpanded(false) },
                        Modifier.testTag("switch-${practice.key.encoded}"), subtitle = "${practice.suggestedTempo} BPM", selected = selected,
                        trailing = { if (selected) AppIcon(AppSymbol.Check, "当前段落") })
                }
            }
        }
    }
}

@Composable
internal fun StageFretboard(
    sequence: Sequence?, cursor: Cursor?, phase: Phase, current: List<Position>, range: BarRange,
    labelRoot: Int?, expanded: Boolean, onToggle: () -> Unit, height: Dp,
    onTap: (Position) -> Unit, enabled: Boolean, loopContinues: Boolean,
) {
    val preferences = LocalContext.current.getSharedPreferences("practice-settings", 0)
    var labels by rememberSaveable { mutableStateOf(if (preferences.getString("board-labels", null) == "degrees") "degrees" else "notes") }
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = PracticeSkin.colors.surfaceLow, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().height(height).testTag("stage-fretboard")) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("指板", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.weight(1f)) {
                    AppButton(if (labels == "degrees" && labelRoot != null) "度数" else "当前音位", { menuOpen = true },
                        Modifier.testTag("board-label-menu"), size = AppButtonSize.Sm,
                        style = AppButtonStyle.Quiet, symbol = AppSymbol.Expand)
                    AppChoiceMenu(menuOpen, listOf("当前音位", "度数"), if (labels == "degrees") 1 else 0,
                        onSelect = { index ->
                            labels = if (index == 0) "notes" else "degrees"
                            preferences.edit().putString("board-labels", labels).apply()
                        }, onDismiss = { menuOpen = false }, enabled = { it == 0 || labelRoot != null })
                }
                AppIconButton(if (expanded) AppSymbol.Collapse else AppSymbol.Expand,
                    if (expanded) "收起指板" else "展开指板", onToggle, Modifier.testTag("fold-fretboard"))
            }
            if (expanded && sequence != null) LinkedFretboardDock(sequence, cursor, phase, current, range,
                onTap, Modifier.weight(1f).fillMaxWidth(), enabled = enabled,
                loopContinues = loopContinues, labelRoot = labelRoot, labelMode = labels)
        }
    }
}
