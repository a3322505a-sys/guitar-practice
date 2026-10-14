package com.a3322505a.guitarlearning.practice.compile

import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.music.Chords
import com.a3322505a.guitarlearning.practice.*

/** Trusted legacy mapping only. Imported data never passes through the single-stream Event model. */
object LegacySequenceAdapter {
    fun compile(sequence: Sequence): PerformancePlan {
        val quarter=sequence.ticksPerQuarter.toLong()
        fun q(tick: Int)=Q(tick.toLong(),quarter)
        val measures=(0 until sequence.barCount).map { bar ->
            val meter=Meter(sequence.meter(bar),4,List(sequence.meter(bar)) { 1 })
            val duration=q(sequence.barEnd(bar)-sequence.barStart(bar))
            Measure("m-"+bar,sequence.barLabel(bar),meter,duration,
                if(duration!=meter.duration) "legacy partial bar" else null,"legacy/source-bar/"+sequence.barNumber(bar))
        }
        val track=Track("guitar","吉他",sequence.openStringMidi,sequence.capoFret,36)
        val events=sequence.events.map { e ->
            val bar=sequence.barAt(e.tick.toDouble())
            val strings=when(e.stroke) { Pick.DOWN -> e.strings.sortedDescending();Pick.UP -> e.strings.sorted();else -> e.strings }
            val notes=strings.map { string ->
                val p=e.positions.firstOrNull { it.string==string }
                val expression=e.stringExpression[string]
                val art=Articulation(velocity=e.strength(string).toDouble(),pick=e.stroke.name.lowercase(),
                    palmMute=e.muted(string),short=expression?.short==true,
                    letRing=expression?.ring ?: e.ring,ringUntil=if(p!=null) q(e.tick)+q(sequence.soundDuration(e,p))*Q(1,if(expression?.short==true) 2 else 1) else null,
                    strumDelayMs=if(e.strum) 12.0 else 0.0,
                    bend=expression?.bend?.map { CurvePoint(Q((it.tick*1000000).toLong(),quarter*1000000),it.semitones) },
                    vibrato=if(e.vibrato) Oscillation(.15,5.0) else null,
                    slide=when(expression?.slide) { 4 -> "out-down";8 -> "out-up";16 -> "in-up";else -> "none" },
                    ornament=expression?.grace?.let { g -> Ornament("on",listOf(GraceNote(g.fret,
                        Q((g.writtenTicks*1000000).toLong(),quarter*1000000),g.kind.name.lowercase()))) })
                ScoreNote("n-"+e.tick+"-"+string,string,p?.fret ?: 0,p?.let { sequence.midi(it) } ?: track.midi(string,0),
                    dead=p==null,articulation=art,sourceRef="legacy/event/"+e.tick+"/string/"+string)
            }
            ScoreEvent("e-"+e.tick,"m-"+bar,q(e.tick-sequence.barStart(bar)),q(e.duration),
                WrittenValue(4),notes,sourceRef="legacy/event/"+e.tick)
        }
        val tuplets=sequence.tuplets.mapIndexed { i,t -> TupletGroup("tuplet-"+i,"voice",
            events.filter { e -> val tick=e.sourceRef!!.substringAfterLast('/').toInt(); tick in t.tick until t.end }.map { it.id },3,2) }
        val writtenEvents=events.map { e ->
            val tick=e.sourceRef!!.substringAfterLast('/').toInt()
            val group=sequence.tuplets.indexOfFirst { tick in it.tick until it.end }
            val values=listOf(1,2,4,8,16,32,64).flatMap { base -> (0..3).map { dots -> WrittenValue(base,dots) } }
            val expected=if(group>=0) e.duration*Q(3,2) else e.duration
            val written=values.firstOrNull { it.straight==expected } ?: WrittenValue(4)
            e.copy(written=written.copy(tupletGroupIds=if(group>=0) listOf("tuplet-"+group) else emptyList()))
        }
        val relations=sequence.links.mapIndexed { i,l -> Relation("relation-"+i,l.kind.name.lowercase(),
            "n-"+l.sourceTick+"-"+l.string,"n-"+l.targetTick+"-"+l.string) }
        val swing=if(sequence.swing==Swing.NONE) emptyList() else listOf(SwingSpec(Q.ZERO,q(sequence.length),
            Q(sequence.swing.unit.toLong(),12),listOf(2,1)))
        val grips=sequence.sections.map { it.chord }.distinct().mapIndexed { i,name ->
            val chord=Chords.named(name)
            GuitarVoicing("legacy-grip-"+i,name,track.id,chord.frets.reversed().map { it ?: -1 },chord.fingers.reversed())
        }
        val harmonies=sequence.sections.mapIndexed { i,s ->
            val bar=sequence.barAt(s.tick.toDouble())
            HarmonySymbol("legacy-harmony-"+i,"m-"+bar,q(s.tick-sequence.barStart(bar)),s.chord,grips.first { it.name==s.chord }.id)
        }
        val score=ScoreDocument("legacy",measures,listOf(track),listOf(Voice("voice","guitar",writtenEvents)),
            tuplets,relations,listOf(Tempo(Q.ZERO,Q.ONE,60.0)),emptyList(),swing,Form(),grips,harmonies)
        val last=measures.last()
        val practice=PracticeSpec("legacy-practice","旧练习",sequence.explanation,"guitar",listOf("guitar"),
            listOf(PracticeSegment(Coordinate(measures.first().id),Coordinate(last.id,last.actualDuration),
                entry="carryIn",exit="truncate",affectedNoteIds=writtenEvents.flatMap { it.notes }.map { it.id })),tempoMode="fixed")
        val plan=PracticeCompiler.compile(score,practice)
        val occurrences=mutableMapOf<Int,Int>()
        val originalEvents=sequence.events.associateBy { "e-"+it.tick }
        val notationEvents=plan.events.map { mapped ->
            val e=originalEvents.getValue(mapped.event.id)
            val previous=sequence.events.lastOrNull { it.tick<e.tick && !it.rest }
            mapped.copy(notationMarks=buildList {
                if(e.accent) add(">")
                else e.stringExpression.filterValues { it.accent }.keys.takeIf { it.isNotEmpty() }?.let { add("> (${it.sorted().joinToString(",")}弦)") }
                if(e.short) add("·")
                e.dynamicMark?.let { add(it) } ?: if(e.velocity<(previous?.velocity ?: 1f)) add("弱") else Unit
            })
        }
        val notationById=notationEvents.associateBy { it.id }
        return plan.copy(events=notationEvents,notes=plan.notes.map { it.copy(event=notationById.getValue(it.event.id)) },visits=plan.visits.mapIndexed { i,v ->
            val original=sequence.barNumber(i)
            val occurrence=occurrences.getOrDefault(original,0)+1
            occurrences[original]=occurrence
            v.copy(occurrence=occurrence,sourceMeasureId="legacy-source-"+original)
        })
    }
}
