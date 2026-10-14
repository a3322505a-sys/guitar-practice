package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.audio.Phase
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import com.a3322505a.guitarlearning.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TabLayoutTest {
    private val contract=File("../contracts/song-package/v1")
    private fun song(path: String)=SongReader(SongSchema { File(contract,it).readBytes() }).read(File(path).readBytes())
    private fun fixture(name: String)=song("../contracts/song-package/v1/fixtures/$name.gpractice").let { PracticeCompiler.compile(it.score,it.practices.single()) }
    private fun rows(plan: PerformancePlan,width: Float=800f)=tabLayoutSystems(plan,plan.tabProjection(),width) { s,size -> s.length*size*.6f }
    private fun usotsuki()=song("src/main/assets/song-packages/usotsuki-v1.gpractice").let { PracticeCompiler.compile(it.score,it.practices.single()) }

    @Test fun truncatedTieMustAgreeWithPlaybackWithoutInventingNoteMarks() {
        val song=song("../contracts/song-package/v1/fixtures/ties.gpractice")
        val segment=PracticeSegment(Coordinate("tie-b"),Coordinate("tie-b",Q(4)),
            entry="truncate",affectedNoteIds=listOf("tie-origin","tie-target"))
        val plan=PracticeCompiler.compile(song.score,song.practices.single().copy(segments=listOf(segment)))
        assertTrue(plan.notes.isEmpty())
        val layouts=rows(plan)
        assertEquals("The silent plan must have no ghost fret digits",plan.notes.map { it.id }.toSet(),layouts.flatMap { it.notes.keys }.toSet())
        val marks=layouts.flatMap { it.bars }.flatMap { it.marks }.map { it.label }
        assertTrue(marks.contains("窗口：截音"))
        assertTrue("Suppressed bends and vibrato must not survive",marks.none { "B" in it || "~" in it })
    }
    @Test fun partialSuppressionKeepsOnlyTheAudibleChordMemberAndItsMarks() {
        val seq=Sequence(listOf(Event(0,48,listOf(Position(6,0),Position(1,3)),
            stringExpression=mapOf(6 to StringExpression(palmMute=true)))),48,"")
        val original=seq.performance
        val muted=original.notes.single { it.note.string==6 }
        val event=original.events.single().copy(suppressedNoteIds=setOf(muted.note.id),adaptation="truncate")
        val plan=original.copy(events=listOf(event),notes=original.notes.filter { it.id!=muted.id }.map { it.copy(event=event) })
        val layouts=rows(plan)
        assertEquals(plan.notes.map { it.id }.toSet(),layouts.flatMap { it.notes.keys }.toSet())
        assertEquals(listOf(1),layouts.flatMap { it.notes.values }.map { it.note.string })
        val marks=layouts.flatMap { it.bars }.flatMap { it.marks }.map { it.label }
        assertTrue(marks.none { it.startsWith("P.M.") })
        assertTrue(marks.contains("窗口：截音"))
        assertFalse(tabNoteVisible(plan,event,muted.note,Hint.FULL,null))
    }

    @Test fun continuousRingHasOneScopedRangePerBarAndEveryOriginalNoteRemains() {
        val plan=usotsuki();val layouts=rows(plan)
        assertEquals(16,layouts.sumOf { it.notes.size })
        val marks=layouts.flatMap { it.bars }.flatMap { it.marks }.filter { it.label=="let ring" }
        assertEquals(2,marks.size)
        assertTrue(marks.all { it.end-it.start==Q(4) && it.ranged })
        assertEquals(plan.notes.map { it.id }.toSet(),layouts.flatMap { it.notes.keys }.toSet())
    }
    @Test fun exceptionsRestsAndPartialChordMuteDoNotBecomeAnAllNoteInstruction() {
        val seq=Sequence(listOf(Event(0,6,listOf(Position(6,0),Position(3,2)),stringExpression=mapOf(6 to StringExpression(palmMute=true))),
            Event(6,6,listOf(Position(6,0)),palmMute=true),Event(12,6,emptyList()),Event(18,6,listOf(Position(6,0)),palmMute=true)),24,"",beatsPerBar=2)
        val marks=rows(seq.performance).flatMap { it.bars }.flatMap { it.marks }.filter { it.label.startsWith("P.M.") }
        assertEquals(3,marks.size)
        assertEquals("P.M. (6弦)",marks.first().label)
        assertEquals(Q(3,2),marks.last().start)
    }
    @Test fun pinkOnsetIsSeparateFromStillSoundingBassAndUsesTheLayoutAnchors() {
        val plan=usotsuki();val seq=Sequence(emptyList(),96,"",runtimePlan=plan)
        val cursor=Session(seq,80,false,1).cursor(6.0)
        val row=rows(plan).first();val state=row.playback(cursor,12)
        val event=plan.events[1]
        assertEquals(setOf(event.id),state.eventIds)
        assertTrue(state.soundingNoteIds.contains(plan.notes.first().id))
        assertEquals(row.bars.first().events[1].x,state.playheadX!!,.001f)
    }
    @Test fun countInPauseResumeRestartSpeedAndFinishUseOneSessionClock() {
        val seq=Sequence(listOf(Event(0,48,listOf(Position(6,0))),Event(48,48,listOf(Position(1,12)))),96,"")
        val rows=rows(seq.performance)
        val session=Session(seq,60,true,2)
        assertNull(rows.first().playback(session.cursor(0.0),12).barIndex)
        val during=session.cursor(session.preTicks+54.0)
        assertEquals(1,rows.single().playback(during,12).barIndex)
        val paused=session.cursor(session.resumeTick(session.preTicks+54.0))
        assertEquals(48.0,paused.localTick,.0001)
        assertEquals(rows.single().playback(paused,12),rows.single().playback(notationCursor(paused,Phase.PAUSED),12))
        assertNull(rows.single().playback(session.cursor(0.0),12).barIndex)
        val fast=Session(seq,120,true,2)
        assertTrue(fast.frame(fast.preTicks+54.0)<session.frame(session.preTicks+54.0))
        assertEquals(during.localTick,fast.cursor(fast.preTicks+54.0).localTick,.0001)
        listOf(Phase.IDLE,Phase.ENDED,Phase.ERROR).forEach { assertNull(notationCursor(during,it)) }
        assertEquals(0,rows.single().playback(session.cursor(session.preTicks+96.0),12).barIndex)
    }
    @Test fun highResolutionTicksAndCrossBarSustainKeepVisitAndNoteIdentity() {
        val seq=Sequence(listOf(Event(0,120,listOf(Position(6,10)))),120,"",beatsPerBar=4,ticksPerQuarter=24)
        val layouts=rows(seq.performance,200f)
        val c=Session(seq,60,false,1).cursor(100.0)
        val row=layouts.first { it.bars.any { b -> b.bar.index==1 } }
        assertEquals(1,row.playback(c,24).barIndex)
        assertEquals(c.activeNoteIds,row.playback(c,24).soundingNoteIds)
        assertTrue(row.bars.first().events.first().glyph.tieIn)
        val fragments=seq.performance.tabProjection().flatMap { it.glyphs }
        assertEquals(listOf(Q(4),Q.ONE),fragments.map { it.written.straight })
        assertEquals(1,fragments.map { it.event.id }.distinct().size)
    }
    @Test fun importedVoicesNestedTupletsHarmonicsAndAllSixStringsKeepReadableGeometry() {
        listOf("complex","nested","techniques","harmonics","pickup").forEach { name ->
            val plan=fixture(name)
            listOf(340f,794f,1280f).forEach { width ->
                rows(plan,width).forEach { row ->
                    assertTrue(row.width>=width)
                    row.bars.forEach { b ->
                        assertTrue(b.events.all { it.beamY>b.staffBottom })
                        assertTrue(b.events.all { it.x>=b.left && it.x<b.right })
                        b.marks.groupBy { it.lane }.values.forEach { marks ->
                            assertTrue(marks.zipWithNext().all { (a,b) -> a.endX+8<=b.x })
                        }
                        assertTrue(b.events.flatMap { it.notes }.all { it.y in b.staffTop..b.staffBottom })
                    }
                }
            }
        }
    }
    @Test fun hiddenAndRootHintsKeepTheExistingVisibilityContract() {
        val seq=Routes.scale(ScaleOptions()).sequence!!;val plan=seq.performance
        plan.notes.forEach { n ->
            assertFalse(tabNoteVisible(plan,n.event,n.note,Hint.HIDDEN,Tonality.C_MAJOR))
            assertEquals(n.note.midi%12==0,tabNoteVisible(plan,n.event,n.note,Hint.ROOTS,Tonality.C_MAJOR))
        }
    }
    @Test fun legacyAccentShortAndDynamicHintsRemainWithoutChangingAudioGainOrDuration() {
        val seq=Phrases.named("R02").sequence;val plan=seq.performance
        val marks=rows(plan).flatMap { it.bars }.flatMap { it.marks }.map { it.label }
        assertTrue(marks.any { ">" in it });assertTrue(marks.any { "·" in it });assertTrue(marks.any { "弱" in it })
        seq.events.forEach { e -> e.positions.forEach { p ->
            val n=plan.notes.single { it.note.id=="n-${e.tick}-${p.string}" }
            assertEquals(e.strength(p.string).toDouble(),n.articulation.velocity!!,0.0)
            assertEquals(Q((e.tick+seq.soundDuration(e,p)).toLong(),seq.ticksPerQuarter.toLong()),n.soundEnd)
        } }
    }
    @Test fun barlineContinuationsDoNotManufactureAnotherPickOrAccentInstruction() {
        val seq=Sequence(listOf(Event(0,120,listOf(Position(6,3)),pick=Pick.DOWN,accent=true)),120,"",ticksPerQuarter=24)
        val bars=seq.performance.tabProjection()
        assertTrue(tabMarks(seq.performance,bars[0]).any { "↓" in it.label && ">" in it.label })
        assertTrue(tabMarks(seq.performance,bars[1]).none { "↓" in it.label || ">" in it.label })
    }
}
