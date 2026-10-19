package com.a3322505a.guitarlearning.music

/** Sound, theoretical spelling, scale degree and a physical guitar position are separate facts. */
data class SpelledPitch(val letter: Char, val accidental: Int = 0) {
    init { require(letter in "CDEFGAB" && accidental in -2..2) }
    val pitchClass get() = Math.floorMod(listOf(0, 2, 4, 5, 7, 9, 11)["CDEFGAB".indexOf(letter)] + accidental, 12)
    val name get() = letter + when (accidental) { -2 -> "♭♭"; -1 -> "♭"; 1 -> "♯"; 2 -> "♯♯"; else -> "" }
    companion object {
        fun parse(text: String): SpelledPitch {
            require(text.isNotEmpty() && text.first() in "CDEFGAB")
            val rest = text.drop(1).replace("#", "♯").replace("b", "♭")
            require(rest.all { it == '♯' } || rest.all { it == '♭' })
            return SpelledPitch(text.first(), rest.count { it == '♯' } - rest.count { it == '♭' })
        }
    }
}

enum class ScaleDefinition(val title: String, val semitones: List<Int>, val degrees: List<String>, val letters: List<Int>) {
    MAJOR("大调", listOf(0,2,4,5,7,9,11), listOf("1","2","3","4","5","6","7"), (0..6).toList()),
    NATURAL_MINOR("自然小调", listOf(0,2,3,5,7,8,10), listOf("1","2","♭3","4","5","♭6","♭7"), (0..6).toList()),
    MAJOR_PENTATONIC("大调五声", listOf(0,2,4,7,9), listOf("1","2","3","5","6"), listOf(0,1,2,4,5)),
    MINOR_PENTATONIC("小调五声", listOf(0,3,5,7,10), listOf("1","♭3","4","5","♭7"), listOf(0,2,3,4,6));
    val pentatonic get() = when(this) {
        MAJOR, MAJOR_PENTATONIC -> MAJOR_PENTATONIC
        NATURAL_MINOR, MINOR_PENTATONIC -> MINOR_PENTATONIC
    }
}

data class KeyContext(val tonic: SpelledPitch, val scale: ScaleDefinition) {
    val pitchClasses get() = scale.semitones.map { (tonic.pitchClass + it) % 12 }
    val spelling: List<SpelledPitch> get() = scale.semitones.mapIndexed { i, interval ->
        val letter = "CDEFGAB"[("CDEFGAB".indexOf(tonic.letter) + scale.letters[i]) % 7]
        val delta = Math.floorMod(tonic.pitchClass + interval - SpelledPitch(letter).pitchClass + 6, 12) - 6
        SpelledPitch(letter, delta)
    }
    fun degree(midi: Int) = scale.degrees.getOrNull(pitchClasses.indexOf(Math.floorMod(midi,12)))
    fun noteName(midi: Int) = spelling.getOrNull(pitchClasses.indexOf(Math.floorMod(midi,12)))?.name ?: Music.name(midi)
    val pentatonic get() = KeyContext(tonic, scale.pentatonic).pitchClasses.toSet()
}

data class TuningContext(val openStringMidi: List<Int> = Music.openMidi, val capo: Int = 0) {
    init { require(openStringMidi.size == 6 && openStringMidi.all { it in 0..127 } && capo in 0..12) }
    fun midi(position: Position) = (openStringMidi[position.string - 1] + capo + position.fret).also { require(it in 0..127) }
}

/** Always authored explicitly. Repeated finger numbers do not create a barre. */
data class Barre(val finger: Int, val fret: Int, val fromString: Int, val toString: Int) {
    init { require(finger in 1..4 && fret in 1..36 && fromString in 1..6 && toString in 1..6 && fromString > toString) }
}

data class Fingering(val position: Position, val finger: Int?, val source: String)

enum class ChordQuality(val suffix: String, val intervals: List<Int>, val degreeLabels: List<String>, val description: String) {
    MAJOR("", listOf(0,4,7), listOf("1","3","5"), "大三和弦"),
    MINOR("m", listOf(0,3,7), listOf("1","♭3","5"), "小三和弦"),
    MAJOR_SEVENTH("maj7", listOf(0,4,7,11), listOf("1","3","5","7"), "大七和弦"),
    ADD_NINE("add9", listOf(0,2,4,7), listOf("1","9","3","5"), "加九和弦"),
    SUS_TWO("sus2", listOf(0,2,7), listOf("1","2","5"), "挂二和弦"),
    SUS_FOUR_NO_FIVE("sus4(no5)", listOf(0,5), listOf("1","4"), "挂四省五"),
    MINOR_SEVENTH_NO_FIVE("m7(no5)", listOf(0,3,10), listOf("1","♭3","♭7"), "小七省五"),
    POWER("5", listOf(0,7), listOf("1","5"), "强力和弦 · 不含三音");
}

data class ChordDefinition(val root: SpelledPitch, val quality: ChordQuality, val bass: SpelledPitch? = null) {
    val name get() = root.name + quality.suffix + (bass?.let { "/" + it.name } ?: "")
    val pitchClasses get() = quality.intervals.map { (root.pitchClass + it) % 12 }.toSet()
    fun interval(midi: Int) = quality.degreeLabels.getOrNull(quality.intervals.indexOf(Math.floorMod(midi-root.pitchClass,12)))
    fun noteName(midi: Int): String {
        val interval = interval(midi) ?: return Music.name(midi)
        val degree = interval.filter(Char::isDigit).toInt()
        val letter = "CDEFGAB"[("CDEFGAB".indexOf(root.letter) + (degree-1)%7)%7]
        val delta = Math.floorMod(midi - SpelledPitch(letter).pitchClass + 6,12)-6
        return SpelledPitch(letter,delta).name
    }
    companion object {
        fun parse(name: String): ChordDefinition? {
            val match = Regex("^([A-G][#♯b♭]?)([^/]*)(?:/([A-G][#♯b♭]?))?$").matchEntire(name) ?: return null
            val quality = ChordQuality.entries.firstOrNull { it.suffix == match.groupValues[2] } ?: return null
            return ChordDefinition(SpelledPitch.parse(match.groupValues[1]),quality,
                match.groupValues[3].takeIf(String::isNotBlank)?.let(SpelledPitch::parse))
        }
    }
}

/** Transition advice is valid only for the exact two fingerings. */
fun retainedFingers(from: Chord, to: Chord): List<Fingering> = from.positions.mapNotNull { p ->
    val finger = from.fingers[6-p.string]
    if (p.fret > 0 && finger in 1..4 && p in to.positions && to.fingers[6-p.string] == finger)
        Fingering(p, finger, from.id + " → " + to.id) else null
}
