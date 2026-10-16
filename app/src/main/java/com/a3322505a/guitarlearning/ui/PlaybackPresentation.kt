package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.practice.Cursor
import com.a3322505a.guitarlearning.audio.Phase

internal fun notationCursor(cursor: Cursor?, phase: Phase) = cursor?.takeIf {
    phase==Phase.PREPARING || phase==Phase.PLAYING || phase==Phase.PAUSED
}
