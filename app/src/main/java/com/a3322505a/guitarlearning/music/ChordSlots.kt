package com.a3322505a.guitarlearning.music

/** One switch slot, distinct voicings and truthful names. No harmonic equivalence is implied. */
data class ChordSlot(val original: String,val beginner: String)
object ChordSlots {
    val all=listOf(ChordSlot("F","Fmaj7"),ChordSlot("Bm","Bm7(no5)"),ChordSlot("G","Gsus2"))
    fun slot(chord: Chord)=all.firstOrNull { chord.name.substringAfterLast(" · ")==it.original || chord.name.substringAfterLast(" · ")==it.beginner }
    fun choices(chord: Chord,library: List<Chord> = Chords.all): List<Chord> = slot(chord)?.let { slot ->
        listOf(slot.original,slot.beginner).mapNotNull { name ->
            chord.takeIf { it.name.substringAfterLast(" · ")==name } ?: library.firstOrNull {
                it.name.substringAfterLast(" · ")==name && it.capoFret==chord.capoFret && it.openStringMidi==chord.openStringMidi
            }
        }
    } ?: listOf(chord)
    fun grouped(chords: List<Chord>) = chords.filter { chord ->
        val slot=slot(chord)
        slot==null || chord.name.substringAfterLast(" · ")==slot.original || chords.none { it.name.substringAfterLast(" · ")==slot.original && it.capoFret==chord.capoFret && it.openStringMidi==chord.openStringMidi }
    }
}
