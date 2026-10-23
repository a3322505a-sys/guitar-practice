@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.ICursorHandler
import alphaTab.model.Beat
import alphaTab.midi.MidiTickLookupFindBeatResultCursorMode
import alphaTab.platform.Cursors
import alphaTab.platform.IContainer
import alphaTab.rendering.utils.BeatBounds

/** Styles the SDK's own cursor. Positions, durations and line changes still come from alphaTab. */
internal class PracticeCursorHandler(private val scrollToStart: () -> Unit) : ICursorHandler {
    private var beatContainer: IContainer? = null
    private var barContainer: IContainer? = null
    var displaying = false
        private set
    private var holding = false
    private var scrollPending = false
    private var startBeat: Beat? = null
    private val width = 12.0

    fun prepare(countIn: Boolean, startBeat: Beat?) {
        displaying = false
        holding = countIn
        scrollPending = true
        this.startBeat = startBeat
        beatContainer?.stopAnimation()
    }

    fun follow() { holding = false }

    fun stop() {
        displaying = false
        holding = false
        scrollPending = false
        startBeat = null
        beatContainer?.stopAnimation()
    }

    override fun onAttach(cursors: Cursors) { beatContainer = cursors.beatCursor; barContainer = cursors.barCursor }
    override fun onDetach(cursors: Cursors) { stop(); beatContainer = null; barContainer = null }

    fun display(bounds: BeatBounds, x: Double) {
        displaying = true
        holding = false
        val bar = bounds.barBounds.masterBarBounds.visualBounds
        barContainer?.setBounds(bar.x, bar.y, bar.w, bar.h)
        beatContainer?.let { it.stopAnimation(); it.setBounds(x - width / 2, bar.y, width, bar.h) }
    }

    fun releaseDisplay() { displaying = false; beatContainer?.stopAnimation() }

    override fun placeBarCursor(barCursor: IContainer, beatBounds: BeatBounds) {
        if (displaying) return
        if (holding && startBeat != null && beatBounds.beat !== startBeat) return
        val bar = beatBounds.barBounds.masterBarBounds.visualBounds
        barCursor.setBounds(bar.x, bar.y, bar.w, bar.h)
    }

    override fun placeBeatCursor(beatCursor: IContainer, beatBounds: BeatBounds, startBeatX: Double) {
        beatContainer = beatCursor
        if (displaying) return
        if (holding && startBeat != null && beatBounds.beat !== startBeat) return
        val bar = beatBounds.barBounds.masterBarBounds.visualBounds
        val x = (if (holding) beatBounds.onNotesX else startBeatX) - width / 2
        beatCursor.stopAnimation()
        beatCursor.setBounds(x, bar.y, width, bar.h)
        if (scrollPending && (startBeat == null || beatBounds.beat === startBeat)) {
            scrollPending = false
            scrollToStart()
        }
    }

    override fun transitionBeatCursor(beatCursor: IContainer, beatBounds: BeatBounds, startBeatX: Double,
        nextBeatX: Double, duration: Double, cursorMode: MidiTickLookupFindBeatResultCursorMode) {
        if (displaying) return
        if (holding) {
            placeBeatCursor(beatCursor, beatBounds, beatBounds.onNotesX)
        } else {
            // Rebase each engine beat to prevent an old animation surviving a loop or line break.
            placeBeatCursor(beatCursor, beatBounds, startBeatX)
            beatCursor.transitionToX(duration.coerceAtLeast(0.0), nextBeatX - width / 2)
        }
    }
}
