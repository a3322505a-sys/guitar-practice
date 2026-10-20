package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.ScaleOptions

private val dictionaryRoots = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

@Composable
private fun DictionaryChoices(options: List<String>, selected: Int, tag: String, onSelect: (Int) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().testTag(tag), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options.indices.toList()) { i ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(options[i]) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
internal fun ChordDictionaryEntry(onSelect: (Chord) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { visible = true }, modifier = Modifier.testTag("chord-dictionary")) { Text("查和弦词典 · 12 主音与多配法") }
    if (visible) StudioModal({ visible = false }, wideContent = true) {
        DictionaryHeader("和弦词典") { visible = false }
        ChordDictionaryView(Chords.named("C"), Modifier.weight(1f)) { onSelect(it); visible = false }
    }
}

@Composable
internal fun ScaleDictionaryEntry(selected: Tonality, onSelect: (Tonality) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { visible = true }, modifier = Modifier.testTag("scale-dictionary")) { Text("完整音阶词典 · 12 主音 × 5 类") }
    if (visible) StudioModal({ visible = false }, wideContent = true) {
        DictionaryHeader("音阶词典") { visible = false }
        ScaleDictionaryView(ScaleOptions(key = selected), Modifier.weight(1f)) { onSelect(it.key); visible = false }
    }
}

@Composable
private fun DictionaryHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onClose) { Text("关闭") }
    }
}

/** Preview and selector never share the filter list's vertical scroll. */
@Composable
internal fun ChordDictionaryView(selected: Chord, modifier: Modifier = Modifier, onSelect: (Chord) -> Unit) {
    var root by rememberSaveable { mutableIntStateOf(selected.root.coerceIn(0, 11)) }
    var quality by rememberSaveable { mutableIntStateOf((selected.quality ?: ChordQuality.MAJOR).ordinal) }
    var voicingId by rememberSaveable { mutableStateOf(selected.id) }
    val definition = remember(root, quality) { ChordDefinition(SpelledPitch.parse(dictionaryRoots[root]), ChordQuality.entries[quality]) }
    val voicings = remember(definition) { ChordDictionary.find(definition.name) }
    val current = voicings.firstOrNull { it.id == voicingId }
        ?: voicings.firstOrNull { voicingId == selected.id && it.chord.frets == selected.frets && selected.root == root && selected.quality?.ordinal == quality }
        ?: voicings.first()
    val preview: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().testTag("dictionary-results"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(current.exactName + " · " + current.label, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { ChordDiagram(current.chord, Hint.FULL, emptyList(), false, height = 200.dp, onTap = { onSelect(current.chord) }) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(definition.quality.degreeLabels.joinToString(" – "), style = MaterialTheme.typography.titleSmall)
                    Text(current.soundingNotes.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    Text("最低音 " + current.bassName, style = MaterialTheme.typography.titleSmall)
                    Text(if (current.chord.barres.isEmpty()) "无横按 · ${current.difficulty}" else current.chord.barres.joinToString("；") { "${it.finger}指 ${it.fret}品 ${it.fromString}→${it.toString}弦" }, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { onSelect(current.chord) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("使用此配法") }
        }
    }
    val selector: @Composable () -> Unit = {
        DictionaryChoices(voicings.mapIndexed { i, v ->
            val frets = v.chord.positions.filter { it.fret > 0 }.map { it.fret }
            "配法 ${i + 1}（${frets.minOrNull() ?: 0}–${frets.maxOrNull() ?: 0} 品 · ${v.label}）"
        }, voicings.indexOf(current), "dictionary-voicings") { voicingId = voicings[it].id }
    }
    val filters: @Composable (Modifier) -> Unit = { filterModifier ->
        Column(filterModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DictionaryChoices(dictionaryRoots, root, "dictionary-root-options") { root = it; voicingId = "" }
            LazyColumn(Modifier.weight(1f).testTag("dictionary-quality-options"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val groups = ChordQuality.entries.groupBy { qualityGroup(it) }
                items(groups.entries.toList(), key = { it.key }) { (title, entries) ->
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Choices(entries.map { it.description }, entries.indexOf(ChordQuality.entries[quality])) { quality = entries[it].ordinal; voicingId = "" }
                }
                items(ChordDictionary.substitutions.filter { ChordDefinition.parse(it.target)?.let { d -> d.root.pitchClass == definition.root.pitchClass && d.quality == definition.quality } == true }) {
                    Text("${it.alternative} · ${it.kind.title}\n${it.instruction}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    DictionaryLayout(modifier, preview, selector, filters)
}

private fun qualityGroup(quality: ChordQuality) = when (quality) {
    ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED -> "三和弦"
    ChordQuality.MAJOR_SEVENTH, ChordQuality.MINOR_SEVENTH, ChordQuality.DOMINANT_SEVENTH -> "七和弦"
    ChordQuality.SUS_TWO, ChordQuality.SUS_FOUR, ChordQuality.ADD_NINE -> "挂留与加音"
    ChordQuality.DIMINISHED_SEVENTH, ChordQuality.HALF_DIMINISHED, ChordQuality.SIXTH, ChordQuality.MINOR_SEVENTH_ADD_ELEVEN -> "扩展与色彩"
    ChordQuality.POWER, ChordQuality.OCTAVE -> "强力与八度"
    else -> "省略配法"
}

@Composable
internal fun ScaleDictionaryView(selected: ScaleOptions, modifier: Modifier = Modifier, onSelect: (ScaleOptions) -> Unit) {
    var root by rememberSaveable { mutableIntStateOf(selected.key.root) }
    var kind by rememberSaveable { mutableIntStateOf(selected.key.mode.ordinal) }
    var box by rememberSaveable { mutableIntStateOf(selected.pattern) }
    val tonality = remember(root, kind) { Tonality.entries.first { it.root == root && it.mode == ScaleDefinition.entries[kind] } }
    val patterns = remember(tonality) { Patterns.forKey(tonality) }
    val pattern = patterns[box.coerceIn(patterns.indices)]
    val preview: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tonality.title, style = MaterialTheme.typography.titleMedium)
            Text(tonality.context.spelling.zip(tonality.mode.degrees).joinToString(" · ") { "${it.first.name} ${it.second}" }, style = MaterialTheme.typography.bodySmall)
            if (tonality.mode == ScaleDefinition.BLUES) Text("♭5 · 蓝调特征音", color = PracticeSkin.colors.noteCharacteristic, style = MaterialTheme.typography.titleSmall)
            CompositionLocalProvider(LocalBoardTeaching provides BoardTeachingContext(tonality.context)) {
                Fretboard(pattern.positions, tonality, Hint.FULL, true, emptyList(), false, height = 180.dp,
                    onTap = { onSelect(selected.copy(key = tonality, pattern = box, pentatonic = false)) }, showLegend = false)
            }
            Button(onClick = { onSelect(selected.copy(key = tonality, pattern = box.coerceIn(patterns.indices), pentatonic = false)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("在指板与谱面中练习")
            }
        }
    }
    val selector: @Composable () -> Unit = {
        DictionaryChoices(patterns.map { p ->
            "${p.solfege}${if (p.caged.isNotBlank()) " / ${p.caged}" else ""}（${p.positions.minOf { it.fret }}–${p.positions.maxOf { it.fret }} 品）"
        }, box.coerceIn(patterns.indices), "dictionary-boxes") { box = it }
    }
    val filters: @Composable (Modifier) -> Unit = { filterModifier ->
        Column(filterModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DictionaryChoices(dictionaryRoots, root, "dictionary-root-options") { root = it; box = 0 }
            LazyColumn(Modifier.weight(1f).testTag("dictionary-scale-options"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ScaleDefinition.entries) { scale ->
                    FilterChip(selected = kind == scale.ordinal, onClick = { kind = scale.ordinal; box = 0 },
                        label = { Text(scale.title) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                }
            }
        }
    }
    DictionaryLayout(modifier, preview, selector, filters)
}

@Composable
private fun DictionaryLayout(modifier: Modifier, preview: @Composable () -> Unit, selector: @Composable () -> Unit, filters: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(12.dp)) {
        if (studioWidth(maxWidth) == StudioWidth.Compact) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview(); selector(); HorizontalDivider(color = PracticeSkin.colors.borderSubtle)
                filters(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                filters(Modifier.weight(.45f).fillMaxHeight())
                Column(Modifier.weight(.55f), verticalArrangement = Arrangement.spacedBy(8.dp)) { preview(); selector() }
            }
        }
    }
}
