@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.midi.*
import alphaTab.model.Score
import alphaTab.model.Note
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.compile.IntervalIndex
import kotlin.math.abs

/** Engine-generated note-on/off intervals, including all voices and sustained notes.
 * UI queries by the engine's heard tick; it never advances time on its own. */
class EngineProjection(val visits: List<MasterBarTickLookup>, private val sounds: List<Sound>) {
    data class Sound(val track: Int, val start: Double, val end: Double, val positions: List<Position>)
    private val index = IntervalIndex(sounds, { it.start }, { it.end })
    fun active(tick: Double, track: Int) = index.query(tick, tick + 1e-7).filter { it.track == track }.flatMap { it.positions }.distinct()
    fun visitAt(tick: Double): Int {
        var lo=0; var hi=visits.size
        while(lo<hi) { val mid=(lo+hi)/2; if(visits[mid].start<=tick) lo=mid+1 else hi=mid }
        return (lo-1).coerceAtLeast(0)
    }
    fun label(visit: Int): String {
        val current = visits.getOrNull(visit) ?: return ""
        val number = current.masterBar.index.toInt() + 1
        val occurrence = visits.take(visit + 1).count { it.masterBar === current.masterBar }
        return "第 $number 小节 · 第 $occurrence 次经过"
    }
    companion object {
        fun generate(score: Score): Pair<MidiFile, EngineProjection> {
            val midi = MidiFile()
            val generator = MidiFileGenerator(score, AlphaScoreAdapter.settings(), AlphaSynthMidiFileHandler(midi))
            generator.applyTranspositionPitches = false
            generator.generate()
            AlphaScoreAdapter.adaptMidi(score, midi)
            return midi to fromMidi(score, midi, generator.tickLookup.masterBars.toList())
        }
        fun fromMidi(score: Score, midi: MidiFile, visits: List<MasterBarTickLookup>): EngineProjection {
            data class Head(val track: Int, val tick: Double, val duration: Double, val pitch: Int, val note: Note)
            val heads = visits.flatMap { visit -> score.tracks.toList().flatMap { track ->
                track.staves.toList().filter { it.tuning.length == 6.0 }.flatMap { staff ->
                    staff.bars[visit.masterBar.index.toInt()].voices.toList().flatMap { voice -> voice.beats.toList().flatMap { beat ->
                        beat.notes.toList().filter { !it.isDead && it.string in 1.0..6.0 && it.fret >= 0 }.map {
                            Head(track.index.toInt(), visit.start + beat.playbackStart, beat.playbackDuration,
                                it.calculateRealValue(false, true).toInt(), it)
                        }
                    } }
                }
            } }
            val byPitch = heads.groupBy { it.track to it.pitch }
            data class On(val event: NoteOnEvent, val positions: List<Position>)
            val pending = mutableMapOf<Triple<Int, Int, Int>, java.util.ArrayDeque<On>>()
            val sounds = mutableListOf<Sound>()
            midi.events.toList().sortedBy { it.tick }.forEach { event ->
                if (event !is NoteEvent) return@forEach
                val key = Triple(event.track.toInt(), event.channel.toInt(), event.noteKey.toInt())
                if (event is NoteOnEvent && event.noteVelocity > 0) {
                    val eligible = byPitch[event.track.toInt() to event.noteKey.toInt()].orEmpty().filter {
                        it.tick <= event.tick + 2 && event.tick < it.tick + maxOf(2.0, it.duration)
                    }
                    val nearest = eligible.minOfOrNull { abs(it.tick - event.tick) }
                    val positions = eligible.filter { nearest != null && abs(abs(it.tick - event.tick) - nearest) < 2 }
                        .map { Position(7 - it.note.string.toInt(), it.note.fret.toInt()) }.distinct()
                    pending.getOrPut(key) { java.util.ArrayDeque() }.add(On(event, positions))
                } else if (event is NoteOffEvent) {
                    val on = pending[key]?.pollFirst() ?: return@forEach
                    sounds += Sound(event.track.toInt(), on.event.tick, event.tick, on.positions)
                }
            }
            val end = visits.lastOrNull()?.end ?: 0.0
            pending.values.forEach { queue -> queue.forEach { sounds += Sound(it.event.track.toInt(), it.event.tick, end, it.positions) } }
            return EngineProjection(visits, sounds)
        }
    }
}
