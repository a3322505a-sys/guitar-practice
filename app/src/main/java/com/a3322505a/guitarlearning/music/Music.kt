package com.a3322505a.guitarlearning.music

data class Position(val string: Int, val fret: Int) {
    val midi: Int get() = Music.openMidi[string - 1] + fret
}

object Music {
    val openMidi = listOf(64, 59, 55, 50, 45, 40) // 1 → 6
    private val names = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    fun name(midi: Int, octave: Boolean = false) = names[midi % 12] + if (octave) (midi / 12 - 1) else ""
}

enum class Tonality(val title: String, val tonic: String, val mode: ScaleDefinition) {
    C_MAJOR("C 大调", "C", ScaleDefinition.MAJOR), A_MINOR("A 自然小调", "A", ScaleDefinition.NATURAL_MINOR),
    G_MAJOR("G 大调", "G", ScaleDefinition.MAJOR), E_MINOR("E 自然小调", "E", ScaleDefinition.NATURAL_MINOR),
    D_MAJOR("D 大调", "D", ScaleDefinition.MAJOR), B_MINOR("B 自然小调", "B", ScaleDefinition.NATURAL_MINOR);
    val context get() = KeyContext(SpelledPitch.parse(tonic), mode)
    val root get() = context.tonic.pitchClass
    val pitchClasses get() = context.pitchClasses
    val intervals get() = mode.degrees
    fun degree(midi: Int) = pitchClasses.indexOf(Math.floorMod(midi,12)) + 1
    fun degreeLabel(midi: Int) = context.degree(midi) ?: "调外音"
    fun isRoot(p: Position) = p.midi % 12 == root
}

data class Pattern(val solfege: String, val caged: String, val frets: List<List<Int>>) {
    val title get() = if(caged.isBlank()) "$solfege · 音位范围" else "$solfege 指型 · CAGED $caged 形"
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
    /** Enumerate a verified pitch collection in each fixed viewport; never translate an open grip. */
    fun forKey(key: Tonality, pentatonic: Boolean = false): List<Pattern> {
        val pitches = if(pentatonic) key.context.pentatonic else key.pitchClasses.toSet()
        if(key in listOf(Tonality.C_MAJOR,Tonality.A_MINOR) && !pentatonic) return all
        return all.mapIndexed { index,source ->
            val range = if(source.positions.any { it.fret==0 } && key !in listOf(Tonality.C_MAJOR,Tonality.A_MINOR)) 0..4 else source.positions.minOf { it.fret }..source.positions.maxOf { it.fret }
            source.copy(frets = Music.openMidi.map { open -> range.filter { (open+it)%12 in pitches } },
                caged=if(key in listOf(Tonality.C_MAJOR,Tonality.A_MINOR)) source.caged else "",
                solfege=if(key in listOf(Tonality.C_MAJOR,Tonality.A_MINOR)) source.solfege else if(index==0) "开放把位" else "把位 ${index+1}")
        }
    }
    val s1 = listOf(Position(5,3), Position(4,0), Position(4,2), Position(4,3), Position(3,0), Position(3,2), Position(2,0), Position(2,1))
}

data class Chord(val name: String, val root: Int, val third: Int?, val frets: List<Int?>, val fingers: List<Int>,
    val capoFret: Int = 0, val soundingName: String = name, val group: String = "基础和弦", val source: String = "",
    val intervalRoles: Map<Int,String> = emptyMap(), val toneNames: Map<Int,String> = emptyMap(),
    val openStringMidi: List<Int> = Music.openMidi,val conditionsLabel: String? = null,val id: String = name,
    val barres: List<Barre> = emptyList(), val quality: ChordQuality? = ChordDefinition.parse(soundingName)?.quality) {
    // Stored in 6 → 1 order. null = X; 0 = O.
    val positions = frets.mapIndexedNotNull { index, fret -> fret?.let { Position(6 - index, it) } }
    val unavailableReason: String? get() = when {
        frets.size!=6 || fingers.size!=6 -> "和弦数据不可用：缺少完整的六弦指法。"
        openStringMidi.size!=6 || openStringMidi.any { it !in 0..127 } || capoFret !in 0..12 -> "和弦数据不可用：调弦或变调夹数据不完整。"
        frets.any { it!=null && it !in 0..36 } || fingers.any { it !in 0..4 } -> "和弦数据不可用：弦品或手指数据无效。"
        positions.isEmpty() -> "和弦数据不可用：没有可弹奏的弦。"
        positions.any { midi(it) !in 0..127 } -> "和弦数据不可用：发声音高超出支持范围。"
        else -> null
    }
    val baseFret get() = if((positions.maxOfOrNull { it.fret } ?: 0)<=4) 1 else positions.filter { it.fret>0 }.minOf { it.fret }
    val conditions get() = conditionsLabel ?: buildList {
        if(openStringMidi==Music.openMidi) {
            if(capoFret==0) add("标准调弦")
        } else add(if(openStringMidi.size==6 && openStringMidi.all { it in 0..127 })
            "调弦（6→1）"+openStringMidi.reversed().joinToString(" / ") { Music.name(it,true) } else "调弦数据不完整")
        add(if(capoFret==0) "无变调夹" else "变调夹${capoFret}品")
        if(capoFret>0) add("实音 $soundingName")
    }.joinToString(" · ")
    fun midi(p: Position) = openStringMidi[p.string-1]+p.fret+capoFret
    fun isRoot(p: Position) = midi(p)%12==root
    fun noteName(p: Position, octave: Boolean = false) = (toneNames[midi(p)%12] ?: definition?.noteName(midi(p)) ?: Music.name(midi(p))) + if(octave) (midi(p)/12-1) else ""
    val arpeggio get() = positions.take(1) + positions.filter { it.string <= 3 }
    val definition get() = quality?.takeIf { root in 0..11 }?.let { q ->
        ChordDefinition.parse(soundingName)?.takeIf { it.root.pitchClass == root } ?: ChordDefinition(SpelledPitch.parse(Music.name(root)),q) }
    val toneSummary get() = definition?.let { d -> d.quality.intervals.joinToString(" ") { d.noteName(root+it) } + " / " + d.quality.degreeLabels.joinToString(" ") } ?: "构成音未提供"
    fun role(p: Position) = if(root<0) "按法音" else when (midi(p) % 12) {
        root -> "根音"
        third -> "三音"
        else -> intervalRoles[Math.floorMod(midi(p)-root,12)] ?: when (Math.floorMod(midi(p)-root,12)) {
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
    val taught=teaching?.let { t -> Chord(name,t.root,t.third,frets,fingers.reversed(),track.capo,t.soundingName,
        t.group,t.source,t.intervalRoles,t.toneNames,track.openStringMidi) }
    return (taught ?: known ?: Chord(name,-1,null,frets,fingers.reversed())).copy(
        frets=frets,fingers=fingers.reversed(),capoFret=track.capo,openStringMidi=track.openStringMidi,
        group=teaching?.group ?: ("本地曲库 · "+group),source=teaching?.source ?: source,id=id,
        barres=barres,quality=quality ?: (taught ?: known)?.quality,
        conditionsLabel=if(track.openStringMidi==Music.openMidi && (teaching!=null || track.capo==0)) null else buildList {
            add(track.name)
            if(track.openStringMidi!=Music.openMidi) add("调弦（6→1）"+track.openStringMidi.reversed().joinToString(" / ") { Music.name(it,true) })
            add(if(track.capo==0) "无变调夹" else "变调夹${track.capo}品")
            teaching?.let { add("实音 "+it.soundingName) }
        }.joinToString(" · "))
}

object Chords {
    val all = TeachingVoicings.all
    fun named(name: String) = all.first { it.name == name }
}
