package com.a3322505a.guitarlearning.music

/** Physical grips are independent of theoretical definitions and of the teaching catalogue. */
data class ChordVoicing(val id: String, val definition: ChordDefinition, val chord: Chord,
    val label: String, val difficulty: String, val contexts: List<String> = emptyList()) {
    val soundingNotes get() = chord.frets.mapIndexed { i,f -> f?.let { chord.noteName(Position(6-i,it),true) } ?: "X" }
    val bassMidi get() = chord.positions.minOf(chord::midi)
    val bassName get() = chord.noteName(chord.positions.minBy(chord::midi),true)
    val exactName get() = definition.name + if(definition.bass==null && bassMidi%12!=definition.root.pitchClass)
        "/"+definition.noteName(bassMidi) else ""
    val omissions get() = when(definition.quality) {
        ChordQuality.MINOR_SEVENTH_NO_FIVE, ChordQuality.DOMINANT_SEVENTH_NO_FIVE, ChordQuality.SUS_FOUR_NO_FIVE -> listOf("5")
        else -> emptyList()
    }
}

enum class SubstitutionKind(val title: String) { SAME("同和弦不同配法"), COLOR("扩展／色彩替代"), CONDITIONAL("改变性质的条件替代") }
data class ChordSubstitution(val target: String, val alternative: String, val kind: SubstitutionKind, val instruction: String)

object ChordDictionary {
    private fun key(text: String)=text.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 255) }
    val roots = listOf("C","C♯","D","E♭","E","F","F♯","G","A♭","A","B♭","B")
    val chordRoots = roots+listOf("D♯","G♯","A♯","D♭","G♭")
    val definitions = chordRoots.flatMap { root -> ChordQuality.entries.map { ChordDefinition(SpelledPitch.parse(root),it) } }
    private data class Grip(val quality: ChordQuality,val rootString: Int,val frets: List<Int>,val fingers: List<Int>,val barres: List<Barre> = emptyList())
    private fun bar(f: Int,a: Int,b: Int) = Barre(1,f,a,b)
    private val grips = listOf(
        Grip(ChordQuality.MAJOR,6,listOf(0,2,2,1,0,0),listOf(1,3,4,2,1,1),listOf(bar(1,6,1))),
        Grip(ChordQuality.MAJOR,5,listOf(-1,0,2,2,2,0),listOf(0,1,2,3,4,1),listOf(bar(1,5,1))),
        Grip(ChordQuality.MINOR,6,listOf(0,2,2,0,0,0),listOf(1,3,4,1,1,1),listOf(bar(1,6,1))),
        Grip(ChordQuality.MINOR,5,listOf(-1,0,2,2,1,0),listOf(0,1,3,4,2,1),listOf(bar(1,5,1))),
        Grip(ChordQuality.MAJOR_SEVENTH,6,listOf(0,2,1,1,0,-1),listOf(1,4,2,3,1,0),listOf(bar(1,6,2))),
        Grip(ChordQuality.MAJOR_SEVENTH,5,listOf(-1,0,2,1,2,0),listOf(0,1,3,2,4,1),listOf(bar(1,5,1))),
        Grip(ChordQuality.MINOR_SEVENTH,6,listOf(0,2,0,0,0,0),listOf(1,3,1,1,1,1),listOf(bar(1,6,1))),
        Grip(ChordQuality.MINOR_SEVENTH,5,listOf(-1,0,2,0,1,0),listOf(0,1,3,1,2,1),listOf(bar(1,5,1))),
        Grip(ChordQuality.DOMINANT_SEVENTH,6,listOf(0,2,0,1,0,0),listOf(1,3,1,2,1,1),listOf(bar(1,6,1))),
        Grip(ChordQuality.DOMINANT_SEVENTH,5,listOf(-1,0,2,0,2,0),listOf(0,1,3,1,4,1),listOf(bar(1,5,1))),
        Grip(ChordQuality.SUS_TWO,5,listOf(-1,0,2,2,0,0),listOf(0,1,3,4,1,1),listOf(bar(1,5,1))),
        Grip(ChordQuality.SUS_FOUR,6,listOf(0,2,2,2,0,0),listOf(1,2,3,4,1,1),listOf(bar(1,6,1))),
        Grip(ChordQuality.ADD_NINE,5,listOf(-1,0,2,4,2,0),listOf(0,1,2,4,3,1),listOf(bar(1,5,1))),
        Grip(ChordQuality.SIXTH,5,listOf(-1,0,2,2,2,2),listOf(0,1,3,3,3,3),listOf(Barre(3,3,4,1))),
        Grip(ChordQuality.DIMINISHED,5,listOf(-1,0,1,2,1,-1),listOf(0,1,2,4,3,0)),
        Grip(ChordQuality.DIMINISHED_SEVENTH,5,listOf(-1,0,1,-1,1,2),listOf(0,1,2,0,3,4)),
        Grip(ChordQuality.HALF_DIMINISHED,5,listOf(-1,0,1,0,1,-1),listOf(0,1,2,1,3,0),listOf(bar(1,5,3))),
        Grip(ChordQuality.MINOR_SEVENTH_NO_FIVE,5,listOf(-1,0,-1,0,1,-1),listOf(0,1,0,2,3,0)),
        Grip(ChordQuality.DOMINANT_SEVENTH_NO_FIVE,5,listOf(-1,0,-1,0,2,-1),listOf(0,1,0,2,4,0)),
        Grip(ChordQuality.SUS_FOUR_NO_FIVE,5,listOf(-1,0,0,2,-1,-1),listOf(0,1,1,3,0,0),listOf(bar(1,5,4))),
        Grip(ChordQuality.MINOR_SEVENTH_ADD_ELEVEN,6,List(6){0},List(6){1},listOf(bar(1,6,1))),
    ) + listOf(6,5).flatMap { root -> listOf(false,true).map { octave ->
        val f=MutableList(6){-1};val fingers=MutableList(6){0};val i=6-root
        f[i]=0;f[i+1]=2;fingers[i]=1;fingers[i+1]=3
        if(octave) { f[i+2]=2;fingers[i+2]=4 }
        Grip(ChordQuality.POWER,root,f,fingers)
    } } + listOf(6,5).map { root ->
        val f=MutableList(6){-1};val fingers=MutableList(6){0};val i=6-root
        f[i]=0;f[i+2]=2;fingers[i]=1;fingers[i+2]=4
        Grip(ChordQuality.OCTAVE,root,f,fingers)
    }

    val voicings: List<ChordVoicing> by lazy {
        val closed=definitions.flatMap { d -> grips.withIndex().filter { it.value.quality==d.quality }.map { (index,g) ->
            // Only these explicitly closed templates move. Open strings are never shifted implicitly.
            val offset=Math.floorMod(d.root.pitchClass-Music.openMidi[g.rootString-1],12).let { if(it==0) 12 else it }
            val id="dictionary-${key(d.root.name)}-${d.quality.name}-$index"
            val c=Chord(d.name,d.root.pitchClass,d.pitchClasses.firstOrNull { Math.floorMod(it-d.root.pitchClass,12) in listOf(3,4) },
                g.frets.map { if(it<0) null else it+offset },g.fingers,id=id,group="和弦词典",source="经校验的封闭可移动配法",
                barres=g.barres.map { it.copy(fret=it.fret-1+offset) },quality=d.quality)
            ChordVoicing(id,d,c,"${g.rootString}弦根音 · ${if(d.quality==ChordQuality.POWER) c.positions.size.toString()+"音" else if(d.quality==ChordQuality.OCTAVE) "中间弦 X" else "封闭配法"}",if(g.barres.isEmpty()) "局部按弦" else "横按",listOf("标准调弦"))
        } }
        val authored=TeachingVoicings.all.mapNotNull { c -> c.definition?.let { d -> ChordVoicing("dictionary-open-"+key(c.id),d,c.copy(id="dictionary-open-"+key(c.id)),"开放／专项配法",if(c.fingers.contains(5)) "含拇指 T" else if(c.barres.isEmpty()) "局部按弦" else "横按") } }
        fun extra(name: String,fs: List<Int?>,fingers: List<Int>,label: String,bars: List<Barre> = emptyList()): ChordVoicing {
            val d=checkNotNull(ChordDefinition.parse(name));val id="dictionary-extra-"+key(name)+"-"+fs.joinToString("-")
            val c=Chord(name,d.root.pitchClass,d.pitchClasses.firstOrNull { Math.floorMod(it-d.root.pitchClass,12) in listOf(3,4) },fs,fingers,id=id,barres=bars,quality=d.quality)
            return ChordVoicing(id,d,c,label,if(5 in fingers) "拇指可选 · 不强制初学者" else "局部按弦")
        }
        closed+authored+listOf(
            extra("Fmaj7",listOf(1,3,3,2,1,0),listOf(5,3,4,2,1,0),"含 F2 的拇指 T 配法"),
            extra("F",listOf(null,null,3,2,1,1),listOf(0,0,3,2,1,1),"局部小横按",listOf(Barre(1,1,2,1))),
            extra("Bm7(no5)",listOf(null,2,0,2,3,null),listOf(0,1,0,2,3,0),"省五音，四根发声弦"),
            extra("Bm7",listOf(null,2,4,2,3,2),listOf(0,1,3,1,2,1),"完整小七横按",listOf(Barre(1,2,5,1))),
            extra("G/B",listOf(null,2,0,0,3,3),listOf(0,1,0,0,3,4),"B2 低音的开放配法"),
            extra("Cmaj7",listOf(null,3,2,0,0,0),listOf(0,3,2,0,0,0),"开放大七"),
            extra("Dmaj7",listOf(null,null,0,2,2,2),listOf(0,0,0,1,1,1),"开放局部横按",listOf(Barre(1,2,3,1))),
            extra("Emaj7",listOf(0,2,1,1,0,0),listOf(0,3,1,2,0,0),"开放大七"),
            extra("Gmaj7",listOf(3,2,0,0,0,2),listOf(3,1,0,0,0,2),"开放大七"),
            extra("Amaj7",listOf(null,0,2,1,2,0),listOf(0,0,2,1,3,0),"开放大七"),
            extra("Dm7",listOf(null,null,0,2,1,1),listOf(0,0,0,2,1,1),"开放小七",listOf(Barre(1,1,2,1))),
            extra("C7(no5)",listOf(null,3,2,3,1,0),listOf(0,3,2,4,1,0),"开放属七 · 明确省五音"),
            extra("D7",listOf(null,null,0,2,1,2),listOf(0,0,0,2,1,3),"开放属七"),
            extra("E7",listOf(0,2,0,1,0,0),listOf(0,2,0,1,0,0),"开放属七"),
            extra("G7",listOf(3,2,0,0,0,1),listOf(3,2,0,0,0,1),"开放属七"),
            extra("A7",listOf(null,0,2,0,2,0),listOf(0,0,1,0,2,0),"开放属七"),
            extra("Dadd9",listOf(null,5,4,2,3,0),listOf(0,4,3,1,2,0),"含三音 F♯ 的加九"),
            extra("Gadd9",listOf(3,2,0,2,0,3),listOf(2,1,0,3,0,4),"含三音 B 的加九"),
            extra("Aadd9",listOf(null,0,2,4,2,0),listOf(0,0,1,4,2,0),"含三音 C♯ 的加九"),
            extra("Fm",listOf(null,null,3,1,1,1),listOf(0,0,3,1,1,1),"四弦局部按法",listOf(Barre(1,1,3,1))),
            extra("B/F♯",listOf(null,null,4,4,4,2),listOf(0,0,2,3,4,1),"四弦配法 · 明示 F♯3 低音"),
            extra("B♭m/F",listOf(null,null,3,3,2,1),listOf(0,0,3,4,2,1),"四弦配法 · 明示 F3 低音"),
            extra("F♯",listOf(null,null,4,3,2,2),listOf(0,0,3,2,1,1),"四弦局部按法",listOf(Barre(1,2,2,1))),
            extra("F♯m",listOf(null,null,4,2,2,2),listOf(0,0,3,1,1,1),"四弦局部按法",listOf(Barre(1,2,3,1))),
            extra("G♯m",listOf(null,null,6,4,4,4),listOf(0,0,3,1,1,1),"四弦局部按法",listOf(Barre(1,4,3,1))))
    }
    fun find(name: String) = ChordDefinition.parse(name)?.let { d -> voicings.filter { it.definition.root==d.root && it.definition.quality==d.quality && (d.bass==null || it.bassMidi%12==d.bass.pitchClass) }
        .sortedWith(compareBy<ChordVoicing> { if(5 in it.chord.fingers) 2 else if(it.chord.barres.isEmpty()) 0 else 1 }.thenBy { it.chord.baseFret }) }.orEmpty()
    val substitutions = listOf(
        ChordSubstitution("F","Fmaj7",SubstitutionKind.COLOR,"增加大七音 E；按实际和声选择"),
        ChordSubstitution("Bm","Bm7(no5)",SubstitutionKind.COLOR,"加入 A 并省五音 F♯；保持真实名称"),
        ChordSubstitution("B","B7",SubstitutionKind.CONDITIONAL,"属七功能不同，须结合后续和弦"),
        ChordSubstitution("Bm","Bsus2",SubstitutionKind.CONDITIONAL,"三音改为二音，仍可能需要横按"),
        ChordSubstitution("F♯m7","F♯m7(add11)",SubstitutionKind.COLOR,"额外包含 B，不能标成普通 F♯m7"),
        ChordSubstitution("C♯m7","C♯m7",SubstitutionKind.SAME,"x46600 含 B；x46650 属于 C♯m"),
        ChordSubstitution("C♯m","C♯m7",SubstitutionKind.COLOR,"增加小七音 B"),
        ChordSubstitution("G","Gsus2",SubstitutionKind.CONDITIONAL,"不含三音 B，按语境选择"),
        ChordSubstitution("B♭","B♭/F",SubstitutionKind.SAME,"构成音相同，最低低音改为 F"),
        ChordSubstitution("Bm","Bm/F♯",SubstitutionKind.SAME,"构成音相同，最低低音改为 F♯"),
        ChordSubstitution("B","B/F♯",SubstitutionKind.SAME,"保留大三性质，四弦配法的最低音为 F♯3"),
        ChordSubstitution("B♭m","B♭m/F",SubstitutionKind.SAME,"保留小三性质，四弦配法的最低音为 F3"),
    )
}

object ScaleDictionary {
    val definitions = ChordDictionary.roots.flatMap { root -> ScaleDefinition.entries.map { KeyContext(SpelledPitch.parse(root),it) } }
    fun positions(key: KeyContext, maxFret: Int = 24) = (6 downTo 1).flatMap { string -> (0..maxFret).filter { (Music.openMidi[string-1]+it)%12 in key.pitchClasses }.map { Position(string,it) } }
    // Each row is string 6 → 1; the five closed minor-pentatonic boxes in A.
    private val pent = listOf(
        listOf(listOf(5,8),listOf(5,7),listOf(5,7),listOf(5,7),listOf(5,8),listOf(5,8)),
        listOf(listOf(8,10),listOf(7,10),listOf(7,10),listOf(7,9),listOf(8,10),listOf(8,10)),
        listOf(listOf(10,12),listOf(10,12),listOf(10,12),listOf(9,12),listOf(10,13),listOf(10,12)),
        listOf(listOf(12,15),listOf(12,15),listOf(12,14),listOf(12,14),listOf(13,15),listOf(12,15)),
        listOf(listOf(15,17),listOf(15,17),listOf(14,17),listOf(14,17),listOf(15,17),listOf(15,17)))
    private val caged = listOf(
        listOf(listOf(12,13,15),listOf(12,14,15),listOf(12,14,15),listOf(12,14),listOf(12,13,15),listOf(12,13,15)),
        listOf(listOf(3,5),listOf(2,3,5),listOf(2,3,5),listOf(2,4,5),listOf(3,5,6),listOf(3,5)),
        listOf(listOf(5,7,8),listOf(5,7,8),listOf(5,7),listOf(4,5,7),listOf(5,6,8),listOf(5,7,8)),
        listOf(listOf(7,8,10),listOf(7,8,10),listOf(7,9,10),listOf(7,9,10),listOf(8,10),listOf(7,8,10)),
        listOf(listOf(10,12,13),listOf(10,12),listOf(9,10,12),listOf(9,10,12),listOf(10,12,13),listOf(10,12,13)))
    fun patterns(key: KeyContext): List<Pattern> {
        val minor=key.scale in listOf(ScaleDefinition.NATURAL_MINOR,ScaleDefinition.MINOR_PENTATONIC,ScaleDefinition.BLUES)
        val five=key.scale in listOf(ScaleDefinition.MAJOR_PENTATONIC,ScaleDefinition.MINOR_PENTATONIC,ScaleDefinition.BLUES)
        val referenceRoot=if(minor) 9 else 0
        val closed=(if(five) pent else caged).mapIndexed { index,template ->
            var delta=Math.floorMod(key.tonic.pitchClass-referenceRoot,12)
            if(template.flatten().min()+delta>12) delta-=12
            var rows=template.mapIndexed { lowIndex,row ->
                val shifted=row.map { it+delta }
                if(key.scale==ScaleDefinition.BLUES) (shifted+(shifted.min()..shifted.max()).filter { (Music.openMidi[5-lowIndex]+it)%12 in key.pitchClasses }).distinct().sorted() else shifted
            }.reversed()
            if(key.scale==ScaleDefinition.BLUES) {
                val midi=rows.flatMapIndexed { i,row -> row.map { Music.openMidi[i]+it } }.toSet()
                for(missing in (midi.min()..midi.max()).filter { it%12 in key.pitchClasses && it !in midi }) {
                    val candidate=rows.indices.mapNotNull { i ->
                        val f=missing-Music.openMidi[i];val row=rows[i]
                        if(f>0 && maxOf(row.max(),f)-minOf(row.min(),f)<=3) i to f else null
                    }.minByOrNull { (i,f) -> kotlin.math.abs(f-rows[i].min()) }
                    checkNotNull(candidate) { "No closed blues location for $key / $index / $missing" }
                    rows=rows.mapIndexed { i,row -> if(i==candidate.first) (row+candidate.second).distinct().sorted() else row }
                }
            }
            val fingers=rows.flatMapIndexed { i,row -> row.map { Position(i+1,it) to (it-row.min()+1) } }.toMap()
            Pattern(if(five) "Box ${index+1}" else "位置 ${index+1}",if(five) "" else listOf("C","A","G","E","D")[index],rows,fingers)
        }
        val ordered=closed.sortedBy { it.positions.minOf { p -> p.fret } }
        return if(key.tonic.name in (if(minor) listOf("A","E","B","F♯","C♯") else listOf("C","G","D","A","E"))) listOf(open(key))+ordered else ordered
    }
    fun open(key: KeyContext): Pattern {
        val rows=(1..6).map { string -> (0..4).filter { (Music.openMidi[string-1]+it)%12 in key.pitchClasses } }
        return Pattern("开放把位","",rows,rows.flatMapIndexed { i,row -> row.map { Position(i+1,it) to it } }.toMap())
    }
}
