package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import org.junit.Test

class LinkedBoardIndexTest {
    @Test fun fretWindowKeepsTargetsVisibleWithoutOscillating() {

    check(stableFretWindow(4..10, listOf(4, 10)) == 4..10)
    val shifted = stableFretWindow(4..10, listOf(10, 11))
    check(10 in shifted && 11 in shifted && shifted.last > 11)
    check(stableFretWindow(shifted, listOf(10)) == shifted)
    check(stableFretWindow(4..10, listOf(0)) == 4..10)
    check(36 in stableFretWindow(1..7, listOf(36)))
    check((2..20).all { it in stableFretWindow(1..7, listOf(2, 20)) })
    }

    @Test fun targetsPreserveRestsRepeatsLoopBoundsAndSustains() {
    val p = Position(1, 5)
    val r = Position(2, 5)
    val sequence = Sequence(listOf(Event(0,3,listOf(p)), Event(3,3,listOf(p)), Event(6,6,emptyList()), Event(12,12,listOf(r)), Event(24,24,listOf(p))),48,"checks")
    val index = LessonBoardIndex(sequence.performance)
    check(index.next(0.0,0.0,4.0,false).first!!.at == .25)
    check(index.targetAt(.5)!!.rest)
    check(index.next(.25,0.0,4.0,false).first!!.at == 1.0)
    check(index.next(2.0,0.0,4.0,false).first == null)
    val loop = index.next(2.0,1.0,4.0,true)
    check(loop.second && loop.first!!.at == 1.0)
    val held = Sequence(listOf(Event(0,12,listOf(p),ring=true,ringEndTick=72), Event(12,36,emptyList()), Event(48,48,listOf(r))),96,"sustain")
    check(p in LessonBoardIndex(held.performance).pool(1))
    val deadSequence = Sequence(listOf(Event(0,48,emptyList(),deadStrings=setOf(1))),48,"dead")
    check(LessonBoardIndex(deadSequence.performance).targetAt(0.0)!!.dead)
    check(!LessonBoardIndex(deadSequence.performance).targetAt(0.0)!!.rest)
    }
}
