package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*

/** Small independent notation fixtures, with no dependency on retired catalogue IDs. */
internal object NotationFixtures {
    val triplets = Sequence(listOf(
        Event(0, 4, listOf(Position(3, 5))), Event(4, 4, listOf(Position(3, 7))), Event(8, 4, emptyList()),
        Event(12, 2, listOf(Position(2, 5))), Event(14, 2, listOf(Position(2, 6))), Event(16, 2, listOf(Position(2, 8))),
        Event(18, 6, emptyList()),
    ), 24, "Triplets and rests", beatsPerBar = 2, tuplets = listOf(Tuplet(0, 4), Tuplet(12, 2)))
    val mixedStrings = Sequence(listOf(
        Event(0, 12, listOf(Position(5, 3), Position(3, 0)), strum = true, deadStrings = setOf(4), accent = true, short = true, velocity = .5f),
        Event(12, 36, emptyList()),
    ), 48, "Dead strings and dynamic marks")
    fun legato(kind: Technique): Sequence {
        val frets = if (kind == Technique.PULL) listOf(9, 7, 5) else listOf(5, 7, 9)
        return Sequence(listOf(
            Event(0, 24, listOf(Position(3, frets[0]))),
            Event(24, 24, listOf(Position(3, frets[1])), pick = Pick.NONE),
            Event(48, 48, listOf(Position(3, frets[2])), pick = Pick.NONE),
        ), 96, "Cross-bar legato", links = listOf(Legato(0, 24, 3, kind), Legato(24, 48, 3, kind)))
    }
    val demonstrations get() = mapOf("triplets" to triplets, "mixed" to mixedStrings,
        "palm-mute" to Sequence(listOf(Event(0, 48, listOf(Position(6, 0)), palmMute = true)), 48, "Palm mute")) +
        Technique.entries.associate { it.name to legato(it) }
}
