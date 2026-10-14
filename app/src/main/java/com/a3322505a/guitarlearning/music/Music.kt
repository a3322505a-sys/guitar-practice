package com.a3322505a.guitarlearning.music

data class Position(val string: Int, val fret: Int) {
    val midi: Int get() = Music.openMidi[string - 1] + fret
}

object Music {
    val openMidi = listOf(64, 59, 55, 50, 45, 40) // 1 → 6
    private val names = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    fun name(midi: Int, octave: Boolean = false) = names[midi % 12] + if (octave) (midi / 12 - 1) else ""
}

enum class Tonality(val title: String, val root: Int, val pitchClasses: List<Int>, val intervals: List<String>) {
    C_MAJOR("C 大调", 0, listOf(0, 2, 4, 5, 7, 9, 11), listOf("纯一度", "大二度", "大三度", "纯四度", "纯五度", "大六度", "大七度")),
    A_MINOR("A 自然小调", 9, listOf(9, 11, 0, 2, 4, 5, 7), listOf("纯一度", "大二度", "小三度", "纯四度", "纯五度", "小六度", "小七度"));
    fun degree(midi: Int) = pitchClasses.indexOf(midi % 12) + 1
    fun isRoot(p: Position) = p.midi % 12 == root
}

data class Pattern(val solfege: String, val caged: String, val frets: List<List<Int>>) {
    val title get() = "$solfege 指型 · CAGED $caged 形"
    val positions = frets.flatMapIndexed { index, row -> row.map { Position(index + 1, it) } }
}

object Patterns {
    // Transcribed from 001 MajorScalePatterns.kt, fc7d446b; exact edge notes retained.
    val all = listOf(
        Pattern("mi", "C", listOf(listOf(0,1,3), listOf(0,1,3), listOf(0,2), listOf(0,2,3), listOf(0,2,3), listOf(0,1,3))),
        Pattern("sol", "A", listOf(listOf(3,5), listOf(3,5,6), listOf(2,4,5), listOf(2,3,5), listOf(2,3,5), listOf(3,5))),
        Pattern("la", "G", listOf(listOf(5,7,8), listOf(5,6,8), listOf(4,5,7), listOf(5,7), listOf(5,7,8), listOf(5,7,8))),
        Pattern("si", "E", listOf(listOf(7,8,10), listOf(8,10), listOf(7,9,10), listOf(7,9,10), listOf(7,8,10), listOf(7,8,10))),
        Pattern("re", "D", listOf(listOf(10,12,13), listOf(10,12,13), listOf(9,10,12), listOf(9,10,12), listOf(10,12), listOf(10,12,13))),
    )
    val s1 = listOf(Position(5,3), Position(4,0), Position(4,2), Position(4,3), Position(3,0), Position(3,2), Position(2,0), Position(2,1))
}

data class Chord(val name: String, val root: Int, val third: Int?, val frets: List<Int?>, val fingers: List<Int>,
    val capoFret: Int = 0, val soundingName: String = name, val group: String = "基础和弦", val source: String = "",
    val intervalRoles: Map<Int,String> = emptyMap(), val toneNames: Map<Int,String> = emptyMap(),
    val openStringMidi: List<Int> = Music.openMidi,val conditionsLabel: String? = null,val id: String = name) {
    // Stored in 6 → 1 order. null = X; 0 = O.
    val positions = frets.mapIndexedNotNull { index, fret -> fret?.let { Position(6 - index, it) } }
    val baseFret get() = if(positions.maxOf { it.fret }<=4) 1 else positions.filter { it.fret>0 }.minOf { it.fret }
    val conditions get() = conditionsLabel ?: if(capoFret==0) "标准调弦 · 无变调夹" else "变调夹${capoFret}品 · 实音 $soundingName"
    fun midi(p: Position) = openStringMidi[p.string-1]+p.fret+capoFret
    fun isRoot(p: Position) = midi(p)%12==root
    fun noteName(p: Position, octave: Boolean = false) = (toneNames[midi(p)%12] ?: Music.name(midi(p))) + if(octave) (midi(p)/12-1) else ""
    val arpeggio get() = listOf(positions.first()) + positions.filter { it.string <= 3 }
    fun role(p: Position) = if(root<0) "按法音" else when (midi(p) % 12) {
        root -> "根音"
        third -> "三音"
        else -> intervalRoles[(midi(p)-root)%12] ?: when ((midi(p) - root) % 12) {
            2 -> "二音（挂留）"
            5 -> "四音（挂留）"
            7 -> "五音"
            10 -> "小七音"
            11 -> "大七音"
            else -> "非和弦音"
        }
    }
    fun label(index: Int) = frets[index]?.let { noteName(Position(6-index,it)) } ?: "X"
}

/** Package frets/fingers are authoritative; reuse known analysis only for the exact same grip. */
fun com.a3322505a.guitarlearning.music.score.GuitarVoicing.chord(
    track: com.a3322505a.guitarlearning.music.score.Track,group: String = "本地曲包",source: String = "",id: String = this.id): Chord {
    val frets=frets.reversed().map { it.takeIf { fret -> fret>=0 } }
    val known=Chords.all.firstOrNull { it.name==name && it.frets==frets && it.capoFret==track.capo && it.openStringMidi==track.openStringMidi }
    return (known ?: Chord(name,-1,null,frets,fingers.reversed())).copy(
        frets=frets,fingers=fingers.reversed(),capoFret=track.capo,openStringMidi=track.openStringMidi,
        group="本地曲库 · "+group,source=source,id=id,
        conditionsLabel=if(track.openStringMidi==Music.openMidi && track.capo==0) "标准调弦 · 无变调夹" else track.name+" · 夹"+track.capo+"品")
}

object Chords {
    val all = listOf(
        Chord("C", 0, 4, listOf(null,3,2,0,1,0), listOf(0,3,2,0,1,0)),
        Chord("Am", 9, 0, listOf(null,0,2,2,1,0), listOf(0,0,2,3,1,0)),
        Chord("G", 7, 11, listOf(3,2,0,0,0,3), listOf(2,1,0,0,0,3)),
        Chord("Em", 4, 7, listOf(0,2,2,0,0,0), listOf(0,2,3,0,0,0)),
        Chord("D", 2, 6, listOf(null,null,0,2,3,2), listOf(0,0,0,1,3,2)),
        Chord("Dm", 2, 5, listOf(null,null,0,2,3,1), listOf(0,0,0,2,3,1)),
        Chord("E", 4, 8, listOf(0,2,2,1,0,0), listOf(0,2,3,1,0,0)),
        Chord("A", 9, 1, listOf(null,0,2,2,2,0), listOf(0,0,1,2,3,0)),
        // Minimal accompaniment grips: X strings stay silent, no unplayed tones added.
        Chord("Gsus2", 7, null, listOf(3,null,0,2,null,null), listOf(3,0,0,1,0,0)),
        Chord("Asus4(no5)", 9, null, listOf(null,0,0,2,null,null), listOf(0,0,0,2,0,0)),
        Chord("Bm7(no5)", 11, 2, listOf(null,2,0,2,null,null), listOf(0,1,0,2,0,0)),
        Chord("D/F♯", 2, 6, listOf(2,null,0,2,null,null), listOf(1,0,0,2,0,0)),
    ) + ShayouChords.all + KafkaChords.all
    fun named(name: String) = all.first { it.name == name }
}
