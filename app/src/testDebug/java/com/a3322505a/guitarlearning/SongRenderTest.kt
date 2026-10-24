@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
package com.a3322505a.guitarlearning
import alphaTab.midi.*
import alphaTab.model.*
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class SongRenderTest {
    @Test fun nativeRendererDrawsPolyphonyNestedTupletsHarmonicsPickupAtPhoneAndTabletWidths() {
        NativeContent.unsupported.keys.forEach(NativeContent::assertRejected)
        for(name in listOf("multi-track","strum","ties","conditional","cross-tuplet")) for(width in listOf(360.0,1200.0)) {
            val score=NativeContent.score(name)
            val renderer=NativeContent.render(score,width)
            val systems=renderer.boundsLookup!!.staffSystems.toList()
            assertTrue(name,systems.isNotEmpty())
            val bars=systems.flatMap { it.bars.toList() }.flatMap { it.bars.toList() }
            assertEquals(name,score.masterBars.length.toInt(),bars.size)
            assertTrue(bars.all { it.realBounds.w>0 && it.realBounds.h>0 })
        }
    }
}
