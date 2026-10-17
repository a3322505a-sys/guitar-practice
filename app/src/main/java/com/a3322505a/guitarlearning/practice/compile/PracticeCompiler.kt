package com.a3322505a.guitarlearning.practice.compile

import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.music.Position
import java.security.MessageDigest
import kotlin.math.log2

data class MeasureVisit(val id: String, val measure: Measure, val occurrence: Int, val start: Q,
    val duration: Q, val sourceStart: Q, val repeatContext: Map<String,Int>, val segment: Int,
    val sourceMeasureId: String = measure.id)
data class PlanEvent(val id: String, val event: ScoreEvent, val track: Track, val voiceId: String,
    val start: Q, val duration: Q, val sourceStart: Q, val visit: Int,
    val activation: Q = start,val sourceOffset: Q = Q.ZERO,val adaptation: String? = null,
    val suppressedNoteIds: Set<String> = emptySet(),
    /** Trusted legacy display hints that were already folded into velocity/soundEnd. */
    val notationMarks: List<String> = emptyList()) {
    val end get() = start+duration
    /** Shared by playback compilation and notation, including practice boundary suppression. */
    val effectiveNotes get() = if(suppressedNoteIds.isEmpty()) event.notes else event.notes.filter { it.id !in suppressedNoteIds }
}
data class PlanNote(val id: String, val note: ScoreNote, val event: PlanEvent, val start: Q, val soundEnd: Q,
    val articulation: Articulation, val finger: Int? = null) {
    val track get() = event.track
    val position get() = Position(note.string,note.fret)
    val soundPitch get() = note.harmonic?.let { track.midi(note.string,note.fret)+12*log2(it.partial.toDouble()) } ?: note.midi.toDouble()
}
data class PlanRelation(val from: String, val to: String, val kind: String)
data class PlanHarmony(val start: Q,val symbol: HarmonySymbol,val voicing: GuitarVoicing?)
data class PerformancePlan(val score: ScoreDocument, val practice: PracticeSpec, val visits: List<MeasureVisit>,
    val events: List<PlanEvent>, val notes: List<PlanNote>, val relations: List<PlanRelation>,
    val tempos: List<Tempo>, val dynamics: List<Dynamic>, val swing: List<SwingSpec>, val length: Q,
    val revisionPin: String? = null) {
    val mainTrack get() = score.tracks.first { it.id==practice.mainTrackId }
    val routeFingerprint: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(visits.joinToString("|") {
            it.measure.id+":"+it.occurrence+":"+it.sourceStart+":"+it.duration
        }.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    val eventById by lazy { events.associateBy { it.id } }
    val noteById by lazy { notes.associateBy { it.id } }
    val mainPositions by lazy { notes.filter { it.track.id==practice.mainTrackId && !it.note.dead }.map { it.position }.distinct() }
    val harmonies: List<PlanHarmony> by lazy {
        val voicings=score.voicings.associateBy { it.id }
        val measureIndex=score.measures.withIndex().associate { it.value.id to it.index }
            val source=score.harmonies.map { score.measureStarts[measureIndex.getValue(it.measureId)]+it.onset to it }
            .filter { (_,h) -> h.voicingId==null || voicings[h.voicingId]?.trackId==practice.mainTrackId }.sortedBy { it.first }
        val result=mutableListOf<PlanHarmony>()
        visits.forEach { v ->
            val a=v.sourceStart;val b=a+v.duration
            val applicable=source.filter { it.second.condition?.matches(v.occurrence,v.repeatContext) ?: true }
            applicable.lastOrNull { it.first<=a }?.let { (_,h) ->
                if(result.lastOrNull()?.symbol?.id!=h.id) result+=PlanHarmony(v.start,h,voicings[h.voicingId])
            }
            applicable.filter { it.first>a && it.first<b }.forEach { (q,h) -> result+=PlanHarmony(v.start+q-a,h,voicings[h.voicingId]) }
        }
        result
    }
    val outgoingRelations by lazy { relations.groupBy { it.from } }
    fun visitAt(q: Double): Int {
        var lo=0; var hi=visits.lastIndex
        while(lo<hi) { val mid=(lo+hi+1)/2; if(visits[mid].start.value()<=q) lo=mid else hi=mid-1 }
        return lo
    }

}

object PracticeCompiler {
    fun compile(score: ScoreDocument, practice: PracticeSpec, pin: String? = null,
        routes: ScoreRoutes = FormCompiler.routes(score)): PerformancePlan {
        val tracks=score.tracks.associateBy { it.id }
        val visits=mutableListOf<MeasureVisit>(); var at=Q.ZERO
        val routeTempos=mutableListOf<Tempo>(); val routeDynamics=mutableListOf<Dynamic>(); val routeSwing=mutableListOf<SwingSpec>()
        val events=mutableListOf<PlanEvent>()
        var plannedHeads=0
        val measureIndex=score.measures.withIndex().associate { it.value.id to it.index }
        data class Span(val voice: Voice,val event: ScoreEvent,val start: Q)
        val spans=score.voices.filter { it.trackId in practice.mixTrackIds }.flatMap { v -> v.events.map { e ->
            Span(v,e,score.measureStarts[measureIndex.getValue(e.measureId)]+e.onset)
        } }
        val spanIndex=IntervalIndex(spans,{ it.start.value() },{ (it.start+it.event.duration).value() })
        val noteSpans=spans.flatMap { span -> span.event.notes.map { it.id to span } }.toMap()
        val eventSpans=spans.associateBy { it.event.id }
        val acousticIncoming=score.relations.filter { it.kind !in listOf("slur","slide-pick") }.associateBy { it.toNoteId }
        fun sourceTempo(q: Q,visit: SourceVisit): Tempo {
            val t=score.tempos.last { it.at<=q && (it.condition?.matches(visit.occurrence,visit.repeatContext) ?: true) }
            val rate=if(t.rampEnd!=null && q<t.rampEnd) t.bpm+(t.endBpm!!-t.bpm)*(q-t.at).value()/(t.rampEnd-t.at).value()
                else if(t.rampEnd!=null) t.endBpm!! else t.bpm
            return Tempo(q,t.beatUnit,rate,t.rampEnd?.takeIf { it>q },t.endBpm?.takeIf { t.rampEnd!!>q })
        }
        practice.segments.forEachIndexed { segmentIndex,segment ->
            val route=if(segment.formPolicy=="performed") routes.performed else routes.written
            fun point(c: Coordinate,end: Boolean): Pair<Int,Q> {
                val candidates=route.withIndex().filter { it.value.measureIndex==measureIndex.getValue(c.measureId) &&
                    (c.occurrence==null || it.value.occurrence==c.occurrence) }
                musicCheck(candidates.isNotEmpty(),"PRACTICE_REF","/practices/"+practice.id+"/segments/"+segmentIndex,"Missing visit")
                musicCheck(candidates.size==1 || c.occurrence!=null,"PRACTICE_AMBIGUOUS","/practices/"+practice.id,"Repeated measure requires occurrence")
                return candidates.first().index to c.offset
            }
            val (first,firstOffset)=point(segment.start,false); val (last,lastOffset)=point(segment.end,true)
            musicCheck(first<last || first==last && firstOffset<lastOffset,"PRACTICE_RANGE","/practices/"+practice.id,"Empty/reversed segment")
            val segmentPlanStart=at
            val segmentEventStart=events.size
            val segmentVisitStart=visits.size
            val sourceEntry=score.measureStarts[route[first].measureIndex]+firstOffset
            for(ri in first..last) {
                val source=route[ri]; val mi=source.measureIndex; val m=score.measures[mi]
                val localStart=if(ri==first) firstOffset else Q.ZERO
                val localEnd=if(ri==last) lastOffset else m.actualDuration
                if(localEnd==localStart) continue
                val a=score.measureStarts[mi]+localStart; val b=score.measureStarts[mi]+localEnd
                val visit=MeasureVisit("visit-"+visits.size,m,source.occurrence,at,b-a,a,source.repeatContext,segmentIndex)
                val vi=visits.size; visits+=visit
                musicCheck(visits.size<=50000,"PLAN_LIMIT","/practices/"+practice.id,"Visit budget")
                musicCheck(visits.size.toLong()*(score.tempos.size+score.dynamics.size+score.swing.size+score.harmonies.size)<=5_000_000,
                    "PLAN_LIMIT","/practices/"+practice.id,"State reconstruction work budget")
                val initial=sourceTempo(a,source)
                val rampEnd=initial.rampEnd?.let { minOf(it,b) }
                val rampBpm=rampEnd?.let { initial.bpm+(initial.endBpm!!-initial.bpm)*(it-a).value()/(initial.rampEnd!!-a).value() }
                routeTempos+=Tempo(at,initial.beatUnit,initial.bpm,rampEnd?.let { at+it-a },rampBpm)
                score.tempos.filter { it.at>a && it.at<b && (it.condition?.matches(source.occurrence,source.repeatContext) ?: true) }.forEach { t ->
                    val end=t.rampEnd?.let { minOf(it,b) }
                    routeTempos+=Tempo(at+t.at-a,t.beatUnit,t.bpm,end?.let { at+it-a },
                        end?.let { t.bpm+(t.endBpm!!-t.bpm)*(it-t.at).value()/(t.rampEnd!!-t.at).value() })
                }
                score.dynamics.filter { it.condition?.matches(source.occurrence,source.repeatContext) ?: true }.groupBy { it.trackId }.values.forEach { marks ->
                    fun copied(d: Dynamic, position: Q): Dynamic {
                        val velocity=if(d.rampEnd!=null && position<d.rampEnd) d.velocity+(d.endVelocity!!-d.velocity)*(position-d.at).value()/(d.rampEnd-d.at).value()
                            else if(d.rampEnd!=null) d.endVelocity!! else d.velocity
                        val end=d.rampEnd?.takeIf { it>position }?.let { minOf(it,b) }
                        val endVelocity=end?.let { d.velocity+(d.endVelocity!!-d.velocity)*(it-d.at).value()/(d.rampEnd!!-d.at).value() }
                        return Dynamic(at+position-a,velocity,d.trackId,end?.let { at+it-a },endVelocity)
                    }
                    marks.lastOrNull { it.at<=a }?.let { routeDynamics+=copied(it,a) }
                    marks.filter { it.at>a && it.at<b }.forEach { routeDynamics+=copied(it,it.at) }
                }
                score.swing.filter { it.start<b && a<it.end }.forEach { s ->
                    val begin=maxOf(s.start,a); val end=minOf(s.end,b)
                    musicCheck(((begin-s.start)/(s.unit*Q(2))).d==1L && ((end-s.start)/(s.unit*Q(2))).d==1L,
                        "SWING_BOUNDARY","/practices/"+practice.id,"Selection/form cuts a swing pair")
                    routeSwing+=s.copy(start=at+begin-a,end=at+end-a)
                }
                // Crossing sustained events are emitted once on a continuous source route.
                val prior=visits.getOrNull(vi-1)
                val continuous=prior!=null && prior.segment==segmentIndex && prior.sourceStart+prior.duration==a
                spanIndex.query(a.value(),b.value()).forEach { span ->
                    if(span.start<a && continuous) return@forEach
                    val start=at+span.start-a
                    if(span.start<a && !continuous) musicCheck(segment.entry!="reject" &&
                        span.event.notes.all { it.id in segment.affectedNoteIds },"BOUNDARY","/practices/"+practice.id,"Entry cuts event")
                    val end=at+span.start+span.event.duration-a
                    val clipped=span.start<a && !continuous
                    val reattack=clipped && segment.entry in listOf("reattack","truncate")
                    events+=PlanEvent("event-"+vi+"-"+span.event.id,span.event,tracks.getValue(span.voice.trackId),span.voice.id,
                        if(span.start<a && segment.entry in listOf("reattack","truncate")) at else start,
                        if(reattack) end-at else span.event.duration,span.start,vi,
                        if(clipped) at else start,if(reattack) a-span.start else Q.ZERO,
                        if(clipped) segment.entry else null,if(clipped && segment.entry=="truncate") span.event.notes.map { it.id }.toSet() else emptySet())
                    plannedHeads+=span.event.notes.size
                    musicCheck(plannedHeads<=100_000 && events.size<=200_000,"PLAN_LIMIT","/practices/"+practice.id,"Practice note/event budget")
                }
                at+=b-a
            }
            val boundaryEvents=events.drop(segmentEventStart).filter { it.end>at }
            val boundaryNotes=boundaryEvents.flatMap { it.event.notes }
            musicCheck(segment.exit!="reject" || boundaryEvents.isEmpty(),"BOUNDARY","/practices/"+practice.id,"Exit cuts event")
            if(boundaryNotes.isNotEmpty()) musicCheck(boundaryNotes.all { it.id in segment.affectedNoteIds },
                "BOUNDARY","/practices/"+practice.id,"Truncation must list affected note IDs")
            musicCheck(at>segmentPlanStart,"PRACTICE_RANGE","/practices/"+practice.id,"No selected duration")
            val selected=events.drop(segmentEventStart).flatMap { it.event.notes }.map { it.id }.toSet()
            val suppress=mutableSetOf<String>()
            score.relations.forEach { r ->
                val sourceSelected=r.fromNoteId in selected;val targetSelected=r.toNoteId in selected
                if(sourceSelected==targetSelected) return@forEach
                val policy=if(targetSelected) segment.entry else segment.exit
                musicCheck(policy!="reject","BOUNDARY","/practices/"+practice.id,"Selection cuts relation "+r.id)
                musicCheck(listOf(r.fromNoteId,r.toNoteId).all { it in segment.affectedNoteIds },"BOUNDARY",
                    "/practices/"+practice.id,"Affected relation endpoints must be listed")
                if(targetSelected && r.kind !in listOf("slur","slide-pick")) {
                    when(policy) {
                        "carryIn" -> {
                            var origin=r.fromNoteId;val seen=mutableSetOf<String>()
                            while(seen.add(origin)) {
                                val span=noteSpans.getValue(origin)
                                if(events.drop(segmentEventStart).none { it.event.id==span.event.id }) {
                                    val begin=segmentPlanStart+span.start-sourceEntry
                                    events+=PlanEvent("history-"+segmentIndex+"-"+span.event.id,span.event,tracks.getValue(span.voice.trackId),
                                        span.voice.id,begin,span.event.duration,span.start,segmentVisitStart,
                                        segmentPlanStart,adaptation="carryIn")
                                }
                                origin=acousticIncoming[origin]?.fromNoteId ?: break
                            }
                        }
                        "truncate" -> suppress+=r.toNoteId
                    }
                }
            }
            if(suppress.isNotEmpty()) {
                var changed=true
                while(changed) {
                    changed=false
                    score.relations.filter { it.kind !in listOf("slur","slide-pick") && it.fromNoteId in suppress && it.toNoteId in selected }
                        .forEach { if(suppress.add(it.toNoteId)) changed=true }
                }
                musicCheck(suppress.all { it in segment.affectedNoteIds },"BOUNDARY","/practices/"+practice.id,"List all truncated descendants")
                for(i in segmentEventStart..events.lastIndex) events[i]=events[i].copy(suppressedNoteIds=events[i].suppressedNoteIds+suppress,adaptation="truncate")
            }
            val chosenEvents=events.drop(segmentEventStart).map { it.event.id }.toSet()
            score.tuplets.forEach { group ->
                val members=group.eventIds.toSet()
                val chosen=chosenEvents.intersect(members)
                if(chosen.isNotEmpty() && chosen.size<members.size) {
                    val start=group.eventIds.minOf { id -> eventSpans.getValue(id).start }
                    val cutEntry=sourceEntry>start
                    val policy=if(cutEntry) segment.entry else segment.exit
                    musicCheck(policy!="reject","BOUNDARY","/practices/"+practice.id,"Selection cuts tuplet "+group.id)
                    val noteIds=members.flatMap { eventSpans.getValue(it).event.notes }.map { it.id }
                    musicCheck(noteIds.all { it in segment.affectedNoteIds },"BOUNDARY","/practices/"+practice.id,"List affected tuplet notes")
                }
            }
            musicCheck(events.size<=200_000 && events.sumOf { it.event.notes.size }<=100_000,
                "PLAN_LIMIT","/practices/"+practice.id,"Practice note/event budget including history")
        }
        val musicEnd=at
        if(practice.padding>Q.ZERO) {
            val meter=visits.last().measure.meter
            visits+=MeasureVisit("padding",Measure("padding","补白",meter,practice.padding,"practicePadding"),1,at,practice.padding,
                score.length,emptyMap(),practice.segments.size)
            at+=practice.padding
        }
        musicCheck(visits.size<=50000,"PLAN_LIMIT","/practices/"+practice.id,"Visit budget including padding")
        val fingerings=practice.noteFingerings.groupBy { it.sourceNoteId }
        val voicingsById=score.voicings.associateBy { it.id }
        val sourceHarmonies=score.harmonies.mapNotNull { h -> voicingsById[h.voicingId]?.let {
            Triple(score.measureStarts[score.measureIndices.getValue(h.measureId)]+h.onset,h,it)
        } }.groupBy { it.third.trackId }.mapValues { (_,v) -> v.sortedBy { it.first } }
        val chordByEvent=mutableMapOf<String,GuitarVoicing?>()
        fun activeVoicing(e: PlanEvent): GuitarVoicing? = chordByEvent.getOrPut(e.id) {
            val entries=sourceHarmonies[e.track.id].orEmpty()
            var lo=0; var hi=entries.size
            while(lo<hi) { val mid=(lo+hi)/2; if(entries[mid].first<=e.sourceStart) lo=mid+1 else hi=mid }
            var i=lo-1
            var result: GuitarVoicing?=null
            while(i>=0) {
                val candidate=entries[i--]
                if(candidate.second.condition?.matches(visits[e.visit].occurrence,visits[e.visit].repeatContext)!=false) {
                    result=candidate.third; break
                }
            }
            result
        }
        val notes=events.flatMap { e -> e.effectiveNotes.map { n ->
            val art=n.articulation.over(e.event.articulation).over(Articulation.DEFAULT)
            val teaching=fingerings[n.id].orEmpty()
            val explicit=teaching.firstOrNull { it.occurrence==visits[e.visit].occurrence }
                ?: teaching.firstOrNull { it.occurrence==null }
            val finger=if(explicit!=null) explicit.finger else {
                activeVoicing(e)
                    ?.takeIf { it.frets.getOrNull(n.string-1)==n.fret && n.fret>0 && !n.dead }
                    ?.fingers?.getOrNull(n.string-1)?.takeIf { it in 1..4 }
            }
            PlanNote(e.id+"/"+n.id,n,e,e.start,minOf(e.end,musicEnd),art,finger)
        } }.sortedBy { it.start }
        val fingeringRefs=notes.map { it.note.id to visits[it.event.visit].occurrence }.toSet()
        val fingeringSources=notes.map { it.note.id }.toSet()
        practice.noteFingerings.forEach { f ->
            musicCheck(if(f.occurrence==null) f.sourceNoteId in fingeringSources else (f.sourceNoteId to f.occurrence) in fingeringRefs,
                "FINGERING_REF","/practices/noteFingerings","Fingering does not refer to a note on this practice route")
        }
        // Resolve cutoff per track and string, never by another voice's rest.
        val onString=notes.groupBy { it.track.id to it.note.string }
        val nextStart=mutableMapOf<String,Q>()
        onString.values.forEach { stringNotes ->
            val byStart=stringNotes.groupBy { maxOf(it.start,it.event.activation) }.entries.sortedBy { it.key }
            byStart.zipWithNext().forEach { (current,next) -> current.value.forEach { nextStart[it.id]=next.key } }
        }
        val nextDiscontinuity=arrayOfNulls<Q>(visits.size)
        var boundary: Q?=null
        for(i in visits.lastIndex downTo 0) {
            nextDiscontinuity[i]=boundary
            if(i>0 && (visits[i].segment!=visits[i-1].segment || visits[i].sourceStart!=visits[i-1].sourceStart+visits[i-1].duration))
                boundary=visits[i].start
        }
        val sustained=notes.map { n ->
            val later=nextStart[n.id]
            var end=if(n.articulation.letRing==true && n.articulation.cut!=true) later ?: musicEnd else n.soundEnd
            n.articulation.ringUntil?.let { sourceCut ->
                end=minOf(end,n.event.start+sourceCut-n.event.sourceStart)
            }
            nextDiscontinuity[n.event.visit]?.let { end=minOf(end,it) }
            if(later!=null) end=minOf(end,later)
            if(n.articulation.short==true) end=minOf(end,n.start+n.event.duration*Q(1,2))
            n.copy(soundEnd=maxOf(n.start,minOf(end,musicEnd)))
        }
        val bySource=sustained.groupBy { it.note.id }
        data class RelationTarget(val noteId: String,val start: Q,val sourceStart: Q,val segment: Int)
        val targets=sustained.associateBy { RelationTarget(it.note.id,it.start,it.event.sourceStart,visits[it.event.visit].segment) }
        val relations=score.relations.flatMap { r ->
            bySource[r.fromNoteId].orEmpty().mapNotNull { a ->
                targets[RelationTarget(r.toNoteId,a.event.end,a.event.sourceStart+a.event.event.duration,
                    visits[a.event.visit].segment)]?.let { PlanRelation(a.id,it.id,r.kind) }
            }
        }
        return PerformancePlan(score,practice,visits.toList(),events.sortedWith(compareBy<PlanEvent> { it.start }.thenBy { it.voiceId }),
            sustained,relations,routeTempos.groupBy { it.at }.values.map { it.last() }.sortedBy { it.at },routeDynamics,routeSwing,at,pin)
    }
}
