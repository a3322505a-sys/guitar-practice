@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.collections.DoubleList
import alphaTab.midi.AlphaTabMetronomeEvent
import alphaTab.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NativeCountInTest {
    private fun score(numerator: Int = 4, denominator: Int = 4, pickup: Boolean = false, firstRest: Boolean = false): Score {
        val score = Score()
        val track = Track().apply { playbackInfo.program = 24.0 }
        score.addTrack(track)
        val staff = Staff().apply { stringTuning = Tuning("", DoubleList(64.0,59.0,55.0,50.0,45.0,40.0)) }
        track.addStaff(staff)
        repeat(2) { barIndex ->
            score.addMasterBar(MasterBar().apply {
                timeSignatureNumerator = numerator.toDouble(); timeSignatureDenominator = denominator.toDouble()
                isAnacrusis = pickup && barIndex == 0
                if (barIndex == 0) tempoAutomations.push(Automation.buildTempoAutomation(false,0.0,120.0,2.0))
            })
            val bar = Bar(); staff.addBar(bar)
            val voice = Voice(); bar.addVoice(voice)
            repeat(if (pickup && barIndex == 0) 1 else numerator) { beatIndex ->
                voice.addBeat(Beat().apply {
                    duration = if (denominator == 8) Duration.Eighth else Duration.Quarter
                    if (firstRest && barIndex == 0 && beatIndex == 0) isEmpty = false
                    else addNote(Note().apply { string = 6.0; fret = 3.0 })
                })
            }
        }
        score.finish(AlphaScoreAdapter.settings())
        return score
    }
    @Test fun nativePreparationHasExactMeterBeatsAndAudiblePcmWithFormalClickOff() {
        for ((n,d) in listOf(4 to 4,3 to 4,6 to 8)) for (rest in listOf(false,true)) {
            NativeSynthFixture(score(n,d,firstRest=rest)).use { test ->
                val beats = mutableListOf<Int>()
                var entered = false
                test.engine.positionChanged.on { if (test.engine.state == alphaTab.synth.PlayerState.Playing) entered = true }
                test.engine.midiEventsPlayed.on { batch -> if (!entered) batch.events.filterIsInstance<AlphaTabMetronomeEvent>()
                    .forEach { beats += it.metronomeNumerator.toInt() + 1 } }
                test.engine.metronomeVolume = 0.0
                test.engine.countInVolume = AlphaTone.CLICK
                assertTrue(test.engine.play())
                test.until { entered }
                assertEquals("$n/$d rest=$rest",(1..n).toList(),beats)
                assertTrue(test.heard.any { abs(it) > .0001f })
                // Native buffer rounding is bounded; pickup/rests add no artificial preparation.
                assertEquals(n * 500.0 * 4 / d, test.frames * 1000.0 / 44100, 50.0)
            }
        }
    }
    @Test fun pickupStillUsesNativeFullMeterAndZeroVolumeReproducesSkippedPreparation() {
        for (volume in listOf(0.0,AlphaTone.CLICK)) NativeSynthFixture(score(pickup=true)).use { test ->
            var beats = 0
            var entered = false
            test.engine.positionChanged.on { if (test.engine.state == alphaTab.synth.PlayerState.Playing) entered = true }
            test.engine.midiEventsPlayed.on { if (!entered) beats += it.events.filterIsInstance<AlphaTabMetronomeEvent>().size }
            test.engine.countInVolume = volume
            assertTrue(test.engine.play()); test.until { entered }
            assertEquals(if (volume == 0.0) 0 else 4,beats)
        }
    }
    @Test fun pauseContinuesRemainingNativePreparationAndRestartRestoresFullPreparation() {
        NativeSynthFixture(score()).use { test ->
            val beats = mutableListOf<Int>()
            var entered = false
            test.engine.positionChanged.on { if (test.engine.state == alphaTab.synth.PlayerState.Playing) entered = true }
            test.engine.midiEventsPlayed.on { if (!entered) it.events.filterIsInstance<AlphaTabMetronomeEvent>()
                .forEach { event -> beats += event.metronomeNumerator.toInt() + 1 } }
            test.engine.metronomeVolume = 0.0; test.engine.countInVolume = AlphaTone.CLICK
            assertTrue(test.engine.play()); test.until { beats.size >= 2 }
            test.engine.pause()
            val heardBefore = test.heard.size
            test.engine.metronomeVolume = AlphaTone.CLICK // play() restores this native channel on resume
            test.engine.countInVolume = 0.0
            assertTrue(test.engine.play()); test.until { entered }
            assertEquals(listOf(1,2,3,4),beats)
            assertEquals("Preparation ticks must not seek the written music",0.0,test.engine.tickPosition,1.0)
            assertTrue(test.heard.drop(heardBefore).any { abs(it) > .0001f })
            test.engine.metronomeVolume = 0.0
            test.engine.pause(); val tick = test.engine.tickPosition
            test.engine.countInVolume = 0.0
            assertTrue(test.engine.play()); test.advance(200)
            assertTrue(test.engine.tickPosition > tick)
            test.engine.stop(); entered = false; beats.clear()
            test.engine.countInVolume = AlphaTone.CLICK
            assertTrue(test.engine.play()); test.until { entered }
            assertEquals(listOf(1,2,3,4),beats)
        }
    }
    @Test fun laterSelectedBarAndNativeLoopsNeverAddAnotherPreparationBar() {
        for (formalClick in listOf(0.0, AlphaTone.CLICK)) {
            val score=score(3,4)
            val selected=EngineProjection.generate(score).second.visits[1]
            NativeSynthFixture(score).use { test ->
                val beats=mutableListOf<Int>()
                val boundaries=mutableListOf<Long>()
                var entryFrame: Long?=null
                var entryTick: Double?=null
                test.engine.playbackRange=alphaTab.synth.PlaybackRange().apply {
                    startTick=selected.start; endTick=selected.end
                }
                test.engine.positionChanged.on { event ->
                    if(test.engine.state==alphaTab.synth.PlayerState.Playing && entryFrame==null) {
                        entryFrame=test.frames; entryTick=event.currentTick
                    }
                }
                test.engine.midiEventsPlayed.on { batch -> if(entryFrame==null)
                    batch.events.filterIsInstance<AlphaTabMetronomeEvent>().forEach { beats+=it.metronomeNumerator.toInt()+1 } }
                test.engine.finished.on { boundaries+=test.frames }
                test.engine.metronomeVolume=formalClick
                test.engine.countInVolume=AlphaTone.CLICK
                test.engine.isLooping=true
                assertTrue(test.engine.play())
                test.until { boundaries.size>=2 }
                assertEquals(listOf(1,2,3),beats)
                assertEquals(selected.start,entryTick!!,1.0)
                assertEquals(1500.0,(boundaries[0]-entryFrame!!)*1000.0/44100,50.0)
                assertEquals(1500.0,(boundaries[1]-boundaries[0])*1000.0/44100,50.0)
            }
        }
    }
}
