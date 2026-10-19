package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.music.*

@Composable
private fun DictionaryChoices(options: List<String>,selected: Int,tag: String,onSelect: (Int)->Unit) {
    LazyRow(Modifier.testTag(tag),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        items(options.indices.toList()) { i -> FilterChip(selected=i==selected,onClick={ onSelect(i) },label={ Text(options[i]) }) }
    }
}

@Composable
internal fun ChordDictionaryEntry(onSelect: (Chord) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var root by rememberSaveable { mutableStateOf(0) }
    var quality by rememberSaveable { mutableStateOf(0) }
    TextButton(onClick={ visible=true },modifier=Modifier.testTag("chord-dictionary")) { Text("查和弦词典 · 12 主音与多配法") }
    if(visible) AlertDialog(onDismissRequest={ visible=false },title={ Text("和弦词典") },text={
        Column(Modifier.fillMaxWidth().heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            DictionaryChoices(ChordDictionary.chordRoots,root,"dictionary-root-options") { root=it }
            DictionaryChoices(ChordQuality.entries.map { it.description },quality,"dictionary-quality-options") { quality=it }
            val definition=ChordDefinition(SpelledPitch.parse(ChordDictionary.chordRoots[root]),ChordQuality.entries[quality])
            Text(definition.name+" · "+definition.quality.intervals.joinToString(" / ") { definition.noteName(definition.root.pitchClass+it) })
            LazyColumn(Modifier.weight(1f).testTag("dictionary-results"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(ChordDictionary.find(definition.name),key={ it.id }) { v ->
                    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(v.exactName+" · "+v.label,style=MaterialTheme.typography.titleSmall)
                        Text("6→1 弦  "+v.chord.frets.joinToString(" ") { it?.toString() ?: "X" })
                        Text("左手  "+v.chord.frets.mapIndexed { i,f -> if(f==null) "X" else if(f==0) "O" else if(v.chord.fingers[i]==5) "T" else v.chord.fingers[i].toString() }.joinToString(" "))
                        Text(v.soundingNotes.joinToString(" · ")+"\n最低音 "+v.bassName,style=MaterialTheme.typography.bodySmall)
                        Text(if(v.chord.barres.isEmpty()) "无横按 · "+v.difficulty else v.chord.barres.joinToString("；") { "${it.finger}指 ${it.fret}品 ${it.fromString}→${it.toString}弦" },style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={ onSelect(v.chord);visible=false }) { Text("使用此配法") }
                    }
                }
                items(ChordDictionary.substitutions.filter { ChordDefinition.parse(it.target)?.let { d -> d.root.pitchClass==definition.root.pitchClass && d.quality==definition.quality }==true }) { sub ->
                    Text("${sub.alternative} · ${sub.kind.title}\n${sub.instruction}",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    },confirmButton={ TextButton(onClick={ visible=false }) { Text("关闭") } })
}

@Composable
internal fun ScaleDictionaryEntry(selected: Tonality,onSelect: (Tonality) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var root by rememberSaveable { mutableStateOf(ChordDictionary.roots.indexOf(selected.tonic).coerceAtLeast(0)) }
    var kind by rememberSaveable { mutableStateOf(selected.mode.ordinal) }
    TextButton(onClick={ visible=true },modifier=Modifier.testTag("scale-dictionary")) { Text("完整音阶词典 · 12 主音 × 5 类") }
    if(visible) AlertDialog(onDismissRequest={ visible=false },title={ Text("音阶与位置库") },text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            DictionaryChoices(ChordDictionary.roots,root,"dictionary-root-options") { root=it }
            DictionaryChoices(ScaleDefinition.entries.map { it.title },kind,"dictionary-scale-options") { kind=it }
            val key=KeyContext(SpelledPitch.parse(ChordDictionary.roots[root]),ScaleDefinition.entries[kind])
            Text(key.spelling.zip(key.scale.degrees).joinToString(" · ") { "${it.first.name} ${it.second}" })
            Text(if(key.scale in listOf(ScaleDefinition.MAJOR,ScaleDefinition.NATURAL_MINOR)) "5 个 CAGED 关联位置；开放把位单独列出" else "5 个封闭 Box；布鲁斯增加 ♭5 蓝音")
            TextButton(onClick={ onSelect(Tonality.entries.first { it.tonic==key.tonic.name && it.mode==key.scale });visible=false }) { Text("在指板与谱面中练习") }
        }
    },confirmButton={ TextButton(onClick={ visible=false }) { Text("关闭") } })
}
