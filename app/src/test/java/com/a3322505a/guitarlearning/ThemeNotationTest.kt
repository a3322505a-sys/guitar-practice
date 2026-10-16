@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
package com.a3322505a.guitarlearning

import alphaTab.rendering.ScoreRenderer
import com.a3322505a.guitarlearning.alphatab.*
import com.a3322505a.guitarlearning.ui.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ThemeNotationTest {
    // The SDK uses MIDI 2.0 per-note bends; SMF export rejects these events.
    // Preserve every scalar event getter instead of losing facts in a MIDI 1.0 conversion.
    private fun midiFacts(score: alphaTab.model.Score) = EngineProjection.generate(score).first.events.toList().map { event ->
        event.javaClass.name to event.javaClass.methods.filter {
            it.parameterCount == 0 && it.name != "getClass" && (it.name.startsWith("get") || it.name.startsWith("is"))
        }.sortedBy { it.name }.map { it.name to it.invoke(event) }
    }

    @Test fun allSkinsColorActualAlphaTabResourcesWithoutChangingMusic() {
        val score=AlphaScoreAdapter.fromSequence(Routes.scale(ScaleOptions()).sequence!!)
        val original=midiFacts(score)
        ThemeRegistry.all.forEach { theme ->
            val settings=AlphaScoreAdapter.settings().apply {core.engine="svg";core.enableLazyLoading=false}
            applyScoreResources(settings.display.resources,theme.colors)
            val renderer=ScoreRenderer(settings)
            val parts=mutableListOf<String>()
            renderer.width=600.0
            renderer.partialRenderFinished.on { (it.renderResult as? String)?.let(parts::add) }
            renderer.renderScore(score,alphaTab.collections.DoubleList(0.0),null)
            assertTrue(theme.id,parts.isNotEmpty())
            assertTrue(theme.id,parts.joinToString("").contains(settings.display.resources.mainGlyphColor.rgba))
            assertEquals(original,midiFacts(score))
            renderer.destroy()
        }
    }

    @Test fun updatingOneRendererRecolorsItsExistingScoreWithoutChangingMidi() {
        val score=AlphaScoreAdapter.fromSequence(Routes.scale(ScaleOptions()).sequence!!)
        val original=midiFacts(score)
        val settings=AlphaScoreAdapter.settings().apply {core.engine="svg";core.enableLazyLoading=false}
        val renderer=ScoreRenderer(settings)
        val parts=mutableListOf<String>()
        renderer.width=600.0
        renderer.partialRenderFinished.on { (it.renderResult as? String)?.let(parts::add) }
        try {
            renderer.renderScore(score,alphaTab.collections.DoubleList(0.0),null)
            ThemeRegistry.all.forEach { theme ->
                // Distinct fixture inks expose a stale cached resource, even when two
                // production themes deliberately share the same high-contrast text ink.
                val probe=theme.colors.copy(notationInk=theme.colors.primary)
                applyScoreResources(settings.display.resources,probe)
                parts.clear()
                renderer.updateSettings(settings)
                renderer.render(null)
                assertSame(theme.id,score,renderer.score)
                assertTrue(theme.id,parts.isNotEmpty())
                assertTrue(theme.id,parts.joinToString("").contains(settings.display.resources.mainGlyphColor.rgba))
                assertEquals(theme.id,original,midiFacts(score))
            }
        } finally { renderer.destroy() }
    }
}
