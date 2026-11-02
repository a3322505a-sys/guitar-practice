@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.midi.MidiFile
import alphaTab.midi.TempoChangeEvent

/** Read-only copy of the generated MIDI tempo map, including repeated visits.
 * No synth/player setters are used to translate display milliseconds into ticks. */
internal class CursorTempoMap(midi: MidiFile) {
    private data class Point(val tick: Double, val millis: Double, val millisPerTick: Double)
    private val points: List<Point>
    init {
        val entries = mutableListOf(Point(0.0, 0.0, 500.0 / midi.division))
        midi.events.filterIsInstance<TempoChangeEvent>().sortedBy { it.tick }.forEach { tempo ->
            val previous = entries.last()
            val point = Point(tempo.tick, previous.millis + (tempo.tick - previous.tick) * previous.millisPerTick,
                tempo.microSecondsPerQuarterNote / 1000 / midi.division)
            if (previous.tick == point.tick) entries[entries.lastIndex] = point else entries += point
        }
        points = entries
    }
    private fun pointAt(value: Double, key: (Point) -> Double): Point {
        var low = 0; var high = points.size
        while (low < high) {
            val middle = (low + high) / 2
            if (key(points[middle]) <= value) low = middle + 1 else high = middle
        }
        return points[(low - 1).coerceAtLeast(0)]
    }
    fun millisAt(tick: Double): Double = pointAt(tick) { it.tick }.let { it.millis + (tick - it.tick) * it.millisPerTick }
    fun tickAt(millis: Double): Double = pointAt(millis) { it.millis }.let { it.tick + (millis - it.millis) / it.millisPerTick }
}

/** Extrapolates only the visual clock between heard-position callbacks.
 * Positive offset delays display. Unwrapped time preserves that delay across a loop. */
internal class CursorDisplayClock(private val map: CursorTempoMap, firstTick: Double, lastTick: Double) {
    private val first = firstTick
    private val last = lastTick
    private val start = map.millisAt(firstTick)
    private val end = map.millisAt(lastTick)
    private val length = end - start
    private var hasSample = false
    private var anchorTime = start
    private var anchorWall = 0L
    private var playbackSpeed = 1.0
    private var displayRate = 1.0
    private var cycle = 0
    private var lastObservedTick: Double? = null

    fun sample(tick: Double, wallMillis: Long, playbackSpeed: Double) {
        if (hasSample && wallMillis < anchorWall) return // stale cross-thread callback
        val previous = lastObservedTick
        if (previous != null && tick + 1e-5 < previous) {
            val range = last - first
            // A small backwards position update is jitter, not a whole new loop.
            // Only a transition from the range's end half to its start half can wrap.
            val wrapped = range > 0 && previous > first + range * .6 &&
                tick < first + range * .4 && previous - tick > range * .5
            if (!wrapped) return
            cycle++
        }
        lastObservedTick = tick
        val observedTime = map.millisAt(tick) + cycle * length
        val actualSpeed = playbackSpeed.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        if (!hasSample) {
            hasSample = true
            anchorTime = observedTime
            displayRate = actualSpeed
        } else {
            // Preserve position continuity when a new SDK sample arrives. Previously
            // replacing the anchor with every sample caused a visible sawtooth.
            val elapsed = (wallMillis - anchorWall).coerceAtLeast(0L)
            val predictedTime = anchorTime + elapsed * displayRate
            anchorTime = predictedTime
            // Gently converge to the synth position by adjusting velocity, never
            // jumping the cursor backwards to the newest (possibly delayed) sample.
            val correction = ((observedTime - predictedTime) / 1000.0).coerceIn(-.06, .06)
            displayRate = actualSpeed * (1.0 + correction)
        }
        anchorWall = wallMillis
        this.playbackSpeed = actualSpeed
    }

    fun tickAt(wallMillis: Long, offsetMs: Int, canLoopAhead: Boolean): Double {
        if (length <= 0) return map.tickAt(start)
        val elapsed = (wallMillis - anchorWall).coerceAtLeast(0L)
        val absolute = (anchorTime + elapsed * displayRate - offsetMs * playbackSpeed)
            .coerceAtLeast(start)
        val ceiling = end + cycle * length
        val bounded = if (canLoopAhead) absolute else absolute.coerceAtMost(ceiling - 1e-7)
        val relative = (bounded - start) % length
        return map.tickAt(start + relative)
    }
}
