package com.a3322505a.guitarlearning.music.score

import kotlin.math.log2
import kotlin.math.roundToInt

/** Structural JSON validation precedes this musical validation; neither trusts requires/review. */
object ScoreValidation {
    fun validate(score: ScoreDocument, practices: List<PracticeSpec>): Set<String> {
        val capabilities=sortedSetOf("score.rational.v1","guitar.six-string.v1")
        if(score.voicings.any { it.teaching!=null } || practices.any { it.teaching!=null })
            capabilities+="catalogue.teaching.v1"
        fun check(ok: Boolean, path: String, reason: String) = musicCheck(ok,"MUSIC",path,reason)
        val ids=mutableSetOf<String>()
        fun register(id: String,path: String) { check(ids.add(id),path,"Duplicate ID: "+id) }
        score.measures.forEachIndexed { i,m ->
            val path="/score/measures/"+i
            register(m.id,path+"/id")
            check(m.meter.beatGroups.sum()==m.meter.numerator,path+"/meter/beatGroups","Group sum must equal numerator")
            check(m.actualDuration>Q.ZERO && m.actualDuration<=m.meter.duration,path+"/actualDuration","Invalid measure length")
            check(m.actualDuration==m.meter.duration || !m.incompleteReason.isNullOrBlank(),path,"Incomplete measure requires reason")
            check(m.leadIn>=Q.ZERO && m.leadIn+m.actualDuration<=m.meter.duration,path+"/leadIn","Lead-in lies outside nominal meter")
            if(m.meter.denominator!=4) capabilities+="meter.compound.v1"
            if(m.meter.beatGroups.distinct().size>1) capabilities+="meter.irregular.v1"
        }
        check(score.length<=Q(100000),"/score/measures","Score duration budget")
        val measureIndex=score.measures.withIndex().associate { it.value.id to it.index }
        val tracks=score.tracks.associateBy { it.id }
        score.tracks.forEachIndexed { i,t ->
            register(t.id,"/score/tracks/"+i)
            check(t.openStringMidi.size==6 && t.capo in 0..12 && t.instrument=="guitar","/score/tracks/"+i,"Six-string guitar required")
            if(t.openStringMidi!=listOf(64,59,55,50,45,40) || t.capo!=0) capabilities+="guitar.tuning-capo.v1"
        }
        check(score.voices.groupBy { it.trackId }.values.all { it.size<=4 },"/score/voices","Four voices per track maximum")
        if(score.voices.size>1) capabilities+="score.polyphony.v1"
        val tuplets=score.tuplets.associateBy { it.id }
        val events=mutableMapOf<String,Triple<Voice,ScoreEvent,Q>>()
        val eventOrdinals=mutableMapOf<String,Int>()
        val notes=mutableMapOf<String,Triple<Voice,ScoreEvent,ScoreNote>>()
        var noteCount=0
        score.voices.forEachIndexed { vi,v ->
            val path="/score/voices/"+vi
            register(v.id,path+"/id")
            check(v.trackId in tracks,path+"/trackId","Missing track")
            val track=tracks.getValue(v.trackId)
            var covered=Q.ZERO
            v.events.forEachIndexed { ei,e ->
                val ep=path+"/events/"+ei
                register(e.id,ep+"/id")
                check(e.measureId in measureIndex,ep+"/measureId","Missing measure")
                val mi=measureIndex.getValue(e.measureId)
                val absolute=score.measureStarts[mi]+e.onset
                check(e.onset>=Q.ZERO && e.onset<score.measures[mi].actualDuration,ep+"/onset","Onset outside measure")
                check(absolute==covered,ep,"Voice must cover score without gaps or overlap")
                check(e.duration>Q.ZERO && absolute+e.duration<=score.length,ep+"/duration","Duration outside score")
                covered=absolute+e.duration
                var written=e.written.straight
                check(e.written.tupletGroupIds.distinct().size==e.written.tupletGroupIds.size,ep+"/written","Repeated tuplet")
                e.written.tupletGroupIds.forEach { id ->
                    check(id in tuplets,ep+"/written/tupletGroupIds","Missing tuplet")
                    val group=tuplets.getValue(id)
                    check(group.voiceId==v.id && e.id in group.eventIds,ep+"/written","Tuplet membership mismatch")
                    written*=Q(group.normal.toLong(),group.actual.toLong())
                }
                check(e.measureRest && e.notes.isEmpty() && e.onset==Q.ZERO && e.duration==score.measures[mi].actualDuration
                    || !e.measureRest && written==e.duration,ep+"/duration","Written and actual duration disagree")
                check(e.notes.map { it.string }.distinct().size==e.notes.size,ep+"/notes","Two notes on one string in event")
                events[e.id]=Triple(v,e,absolute)
                eventOrdinals[e.id]=ei
                e.notes.forEachIndexed { ni,n ->
                    val np=ep+"/notes/"+ni
                    register(n.id,np+"/id"); noteCount++
                    check(n.string in 1..6 && n.fret in 0..track.fretCount,np,"Position outside track")
                    val expected=track.midi(n.string,n.fret)
                    check(n.harmonic?.let { h ->
                        h.soundMidi==n.midi && h.soundMidi==expected+(12*log2(h.partial.toDouble())).roundToInt() &&
                            h.touchFret>n.fret && (h.kind!="natural" || n.fret==0)
                    } ?: (n.midi==expected),np+"/midi","Sound pitch disagrees with tuning/capo/harmonic")
                    if(n.harmonic!=null) capabilities+="technique.harmonic.v1"
                    n.harmonic?.let { h ->
                        val touchPoints=(1 until h.partial).filter { k -> gcd(k,h.partial)==1 }.map {
                            n.fret+(12*log2(h.partial.toDouble()/(h.partial-it))).roundToInt()
                        }
                        check(h.touchFret in touchPoints,np+"/harmonic/touchFret","Touch node disagrees with harmonic partial")
                    }
                    val art=n.articulation.over(e.articulation).over(Articulation.DEFAULT)
                    expression(art,e.duration,absolute,score.length,np,capabilities)
                    if(n.dead) capabilities+="technique.mute.v1"
                    notes[n.id]=Triple(v,e,n)
                }
                if(e.notes.isEmpty()) expression(e.articulation.over(Articulation.DEFAULT),e.duration,absolute,score.length,ep,capabilities)
            }
            check(covered==score.length,path,"Voice does not cover score")
        }
        check(noteCount<=200000,"/score/voices","Note budget")
        score.tuplets.forEachIndexed { i,g ->
            val p="/score/tuplets/"+i
            register(g.id,p+"/id")
            check(g.actual in 2..32 && g.normal in 1..32 && g.eventIds.isNotEmpty(),p,"Invalid ratio")
            check(g.eventIds.distinct().size==g.eventIds.size,p,"Repeated tuplet member")
            check(g.eventIds.all { events[it]?.first?.id==g.voiceId && g.id in events.getValue(it).second.written.tupletGroupIds },p,"Missing or cross-voice member")
            val memberIndices=g.eventIds.map { id -> eventOrdinals.getValue(id) }
            check(memberIndices.zipWithNext().all { (a,b) -> b==a+1 },p,"Tuplet members must be contiguous and ordered")
            var parent=g.parentId; val seen=mutableSetOf(g.id)
            while(parent!=null) {
                check(seen.add(parent) && parent in tuplets && seen.size<=4,p+"/parentId","Cycle, missing parent or nesting limit")
                val pg=tuplets.getValue(parent)
                check(pg.voiceId==g.voiceId && pg.eventIds.containsAll(g.eventIds),p,"Parent must enclose child")
                parent=pg.parentId
            }
            capabilities+="rhythm.tuplet.v1"
            if(g.parentId!=null) capabilities+="rhythm.nested-tuplet.v1"
        }
        val groupsByVoice=score.tuplets.groupBy { it.voiceId }
        groupsByVoice.values.forEach { groups ->
            fun depth(g: TupletGroup): Int = if(g.parentId==null) 0 else 1+depth(tuplets.getValue(g.parentId))
            val ordered=groups.sortedWith(compareBy<TupletGroup> { eventOrdinals.getValue(it.eventIds.first()) }
                .thenByDescending { eventOrdinals.getValue(it.eventIds.last()) }.thenBy { depth(it) })
            val stack=mutableListOf<TupletGroup>()
            ordered.forEach { g ->
                val first=eventOrdinals.getValue(g.eventIds.first());val last=eventOrdinals.getValue(g.eventIds.last())
                while(stack.isNotEmpty() && eventOrdinals.getValue(stack.last().eventIds.last())<first) stack.removeAt(stack.lastIndex)
                val parent=stack.lastOrNull()
                check(parent==null || last<=eventOrdinals.getValue(parent.eventIds.last()),"/score/tuplets","Crossing tuplet groups")
                check(g.parentId==parent?.id,"/score/tuplets","Nested group must identify its immediate parent")
                stack+=g
            }
        }
        val incoming=mutableSetOf<String>(); val outgoing=mutableSetOf<String>()
        score.relations.forEachIndexed { i,r ->
            val p="/score/relations/"+i
            register(r.id,p+"/id")
            check(r.fromNoteId in notes && r.toNoteId in notes,p,"Missing note")
            val a=notes.getValue(r.fromNoteId); val b=notes.getValue(r.toNoteId)
            val ae=events.getValue(a.second.id); val be=events.getValue(b.second.id)
            check(a.first.trackId==b.first.trackId && be.third>ae.third,p,"Relation track/time mismatch")
            if(r.kind!="slur") {
                check(a.third.string==b.third.string && ae.third+a.second.duration==be.third,p,"Relation must join adjacent same-string notes")
                check(!a.third.dead && !b.third.dead,p,"Dead note cannot sustain relation")
                check(incoming.add(r.toNoteId) && outgoing.add(r.fromNoteId),p,"Ambiguous acoustic relation")
                if(r.kind=="tie") check(a.third.midi==b.third.midi,p,"Tie pitch mismatch")
                if(r.kind=="tie") check(a.third.fret==b.third.fret && a.third.harmonic==b.third.harmonic,p,"Tie position/harmonic mismatch")
                if(r.kind=="hammer") check(a.third.fret<b.third.fret,p,"Hammer direction")
                if(r.kind=="pull") check(a.third.fret>b.third.fret,p,"Pull direction")
                val art=b.third.articulation.over(b.second.articulation).over(Articulation.DEFAULT)
                if(r.kind=="tie") check(art.pick=="none",p,"Tied target must suppress attack explicitly")
                if(r.kind=="slide-pick") check(art.pick!="none",p,"Re-picked slide requires target attack")
                else check(art.pick in listOf("none","default"),p,"Legato conflicts with explicit target picking")
                val sourceArt=a.third.articulation.over(a.second.articulation).over(Articulation.DEFAULT)
                check(sourceArt.repluck==null && art.repluck==null && art.ornament==null,p,"Relation conflicts with derived target attacks")
            }
            capabilities+=if(r.kind=="tie") "technique.tie.v1" else "technique.legato.v1"
        }
        notes.values.forEach { (_,e,n) ->
            val art=n.articulation.over(e.articulation).over(Articulation.DEFAULT)
            check(art.pick!="none" || n.id in incoming || n.sharedSoundId!=null,"/score/relations","Unpicked note needs acoustic relation/shared sound")
            check(n.harmonic==null || art.repluck?.kind!="trill","/score/voices","Harmonic trill needs explicit sounding-pitch adaptation")
        }
        // Written occupancy, rather than rest events, determines same-string conflicts.
        notes.values.filter { it.third.sharedSoundId!=null }.groupBy { it.third.sharedSoundId }.forEach { (_,heads) ->
            val first=heads.first()
            fun state(head: Triple<Voice,ScoreEvent,ScoreNote>) =
                head.third.articulation.over(head.second.articulation).over(Articulation.DEFAULT).copy(pick=null)
            check(heads.size>=2 && heads.all {
                it.first.trackId==first.first.trackId && it.third.string==first.third.string &&
                it.third.fret==first.third.fret && it.third.midi==first.third.midi &&
                it.third.dead==first.third.dead && it.third.harmonic==first.third.harmonic &&
                events.getValue(it.second.id).third==events.getValue(first.second.id).third &&
                it.second.duration==first.second.duration && state(it)==state(first) &&
                score.relations.none { r -> r.fromNoteId==it.third.id || r.toNoteId==it.third.id }
            } && heads.any { it.third.articulation.over(it.second.articulation).over(Articulation.DEFAULT).pick!="none" },
                "/score/voices","Shared sound requires matching simultaneous heads, one attack and no acoustic relations")
        }
        notes.values.groupBy { it.first.trackId to it.third.string }.values.forEach { stringNotes ->
            var active: Triple<Voice,ScoreEvent,ScoreNote>?=null; var until=Q.ZERO
            stringNotes.sortedBy { events.getValue(it.second.id).third }.forEach { next ->
                val start=events.getValue(next.second.id).third
                if(active!=null && start<until) {
                    val shared=active!!.third.sharedSoundId
                    check(shared!=null && shared==next.third.sharedSoundId && active!!.third.midi==next.third.midi &&
                        events.getValue(active!!.second.id).third==start && active!!.second.duration==next.second.duration,
                        "/score/voices","Conflicting voices on same track/string")
                }
                if(start+next.second.duration>until) { active=next; until=start+next.second.duration }
            }
        }
        check(score.tempos.firstOrNull()?.let { it.at==Q.ZERO && it.condition==null }==true,"/score/tempos","Unconditional initial tempo required")
        score.tempos.forEachIndexed { i,t ->
            val p="/score/tempos/"+i
            check(t.at>=Q.ZERO && t.at<score.length && t.beatUnit>Q.ZERO && t.bpm in 1.0..600.0,p,"Invalid tempo")
            check(i==0 || score.tempos[i-1].at<=t.at,p,"Tempos must be sorted")
            if(i>0 && score.tempos[i-1].at==t.at) check(t.condition!=null,p,"Conditional override must follow default")
            check((t.rampEnd==null)==(t.endBpm==null),p,"Ramp requires both endpoints")
            t.rampEnd?.let { check(it>t.at && it<=score.length,p,"Invalid ramp") }
        }
        if(score.tempos.size>1 || score.tempos.any { it.rampEnd!=null }) capabilities+="tempo.map.v1"
        score.dynamics.forEachIndexed { i,d ->
            check(d.at>=Q.ZERO && d.at<score.length && (d.trackId==null || d.trackId in tracks),"/score/dynamics/"+i,"Invalid dynamic")
            check((d.rampEnd==null)==(d.endVelocity==null) && (d.rampEnd==null || d.rampEnd>d.at && d.rampEnd<=score.length),"/score/dynamics/"+i,"Invalid dynamic ramp")
        }
        if(score.dynamics.isNotEmpty()) capabilities+="technique.dynamics.v1"
        val repeats=score.form.repeats.associateBy { it.id }
        val conditions=score.tempos.mapNotNull { it.condition }+score.dynamics.mapNotNull { it.condition }+
            score.harmonies.mapNotNull { it.condition }+score.form.jumps.mapNotNull { it.condition }
        conditions.forEach { c ->
            check(c.occurrence==null || c.occurrence in 1..50000,"/score/condition","Invalid occurrence")
            check(c.repeatPasses.all { (id,passes) -> id in repeats && passes.all { it in 1..repeats.getValue(id).times } },
                "/score/condition","Missing repeat or invalid pass")
        }
        if(conditions.isNotEmpty()) capabilities+="form.condition.v1"
        score.form.repeats.forEach { register(it.id,"/score/form/repeats") }
        score.form.markers.forEach { register(it.id,"/score/form/markers") }
        score.form.jumps.forEach { register(it.id,"/score/form/jumps") }
        score.swing.forEachIndexed { i,s ->
            check(s.start>=Q.ZERO && s.end<=score.length && s.start<s.end && s.unit>Q.ZERO && s.ratio.size==2 &&
                s.trackIds.all { it in tracks } && s.voiceIds.all { id -> score.voices.any { it.id==id } },"/score/swing/"+i,"Invalid swing range/scope")
            check(((s.end-s.start)/(s.unit*Q(2))).let { it.d==1L },"/score/swing/"+i,"Swing requires complete pairs")
            check(score.swing.take(i).none { other -> s.start<other.end && other.start<s.end &&
                (s.trackIds.isEmpty() && s.voiceIds.isEmpty() || other.trackIds.isEmpty() && other.voiceIds.isEmpty() ||
                    s.trackIds.intersect(other.trackIds.toSet()).isNotEmpty() || s.voiceIds.intersect(other.voiceIds.toSet()).isNotEmpty()) },
                "/score/swing/"+i,"Overlapping swing scopes")
        }
        if(score.swing.isNotEmpty()) capabilities+="rhythm.swing.v1"
        score.voicings.forEachIndexed { i,v ->
            register(v.id,"/score/voicings/"+i)
            check(v.trackId in tracks && v.frets.size==6 && v.fingers.size==6 && v.frets.all { it in -1..tracks.getValue(v.trackId).fretCount },
                "/score/voicings/"+i,"Invalid voicing")
            check(v.frets.any { it>=0 },"/score/voicings/"+i,"Voicing cannot have six muted strings")
        }
        score.harmonies.forEachIndexed { i,h ->
            register(h.id,"/score/harmonies/"+i)
            check(h.measureId in measureIndex && h.onset>=Q.ZERO && h.onset<score.measures[measureIndex.getValue(h.measureId)].actualDuration &&
                (h.voicingId==null || score.voicings.any { it.id==h.voicingId }),"/score/harmonies/"+i,"Invalid harmony reference")
        }
        practices.forEachIndexed { i,p ->
            register(p.id,"/practices/practices/"+i)
            check(p.mainTrackId in p.mixTrackIds && p.mixTrackIds.all { it in tracks } && p.mixTrackIds.distinct().size==p.mixTrackIds.size,
                "/practices/practices/"+i,"Invalid participating tracks")
            check(p.padding>=Q.ZERO && (p.padding==Q.ZERO || !p.paddingReason.isNullOrBlank()),"/practices/practices/"+i,"Padding requires reason")
            if(p.noteFingerings.isNotEmpty()) capabilities+="teaching.note-fingering.v1"
            val assignments=mutableSetOf<Pair<String,Int?>>()
            p.noteFingerings.forEach { f ->
                val n=notes[f.sourceNoteId]
                check(n!=null && n.first.trackId in p.mixTrackIds,"/practices/noteFingerings","Unknown or excluded source note")
                check(f.finger==null || f.finger in 1..5,"/practices/noteFingerings","Left hand finger must be 1..4, 5 (thumb) or null")
                check(!f.source.isBlank() && (f.occurrence==null || f.occurrence>0),"/practices/noteFingerings","Review source/occurrence required")
                check(assignments.add(f.sourceNoteId to f.occurrence),"/practices/noteFingerings","Duplicate fingering assignment")
                check(f.finger==null || n?.third?.fret?.let { it>0 }==true,
                    "/practices/noteFingerings","Finger number requires a fretted or touched source position")
            }
            p.segments.forEachIndexed { si,s ->
                listOf(s.start,s.end).forEach { c ->
                    check(c.measureId in measureIndex && c.offset>=Q.ZERO && c.offset<=score.measures[measureIndex.getValue(c.measureId)].actualDuration,
                        "/practices/practices/"+i+"/segments/"+si,"Invalid coordinate")
                }
                check(s.affectedNoteIds.all { it in notes },"/practices/practices/"+i+"/segments/"+si,"Unknown boundary note")
            }
        }
        val practiceIds=practices.map { it.id }.toSet()
        practices.forEach { p ->
            check(listOfNotNull(p.familyId,p.variantOf).plus(p.relatedIds).all { it in practiceIds },"/practices","Missing family/variant/related practice")
            var parent=p.variantOf; val seen=mutableSetOf(p.id)
            while(parent!=null) { check(seen.add(parent),"/practices","Variant cycle"); parent=practices.first { it.id==parent }.variantOf }
        }
        if(score.form.repeats.isNotEmpty()) capabilities+="form.repeat.v1"
        if(score.form.endings.isNotEmpty()) capabilities+="form.volta.v1"
        if(score.form.jumps.isNotEmpty()) capabilities+="form.jump.v1"
        if(score.voicings.any { 5 in it.fingers } || practices.any { p -> p.noteFingerings.any { it.finger==5 } }) capabilities += "teaching.thumb.v1"
        if(LessonValidation.validate(score,practices)) capabilities += "teaching.lesson.v1"
        return capabilities
    }
    private fun gcd(a: Int,b: Int): Int = if(b==0) a else gcd(b,a%b)
    private fun expression(a: Articulation, duration: Q, at: Q, length: Q, path: String, caps: MutableSet<String>) {
        fun check(ok: Boolean,reason: String)=musicCheck(ok,"EXPRESSION",path,reason)
        listOf(a.bend.orEmpty(),a.tremoloBar.orEmpty()).forEach { curve ->
            check(curve.all { it.at>=Q.ZERO && it.at<=duration } && curve.zipWithNext().all { (x,y) -> x.at<y.at },"Curve outside note or unordered")
        }
        check(a.ringUntil==null || a.ringUntil>at && a.ringUntil<=length,"Invalid ring cutoff")
        if(a.bend.orEmpty().isNotEmpty()) caps+="technique.bend.v1"
        if((a.vibrato?.depth ?: 0.0)>0) caps+="technique.vibrato.v1"
        if(a.tremoloBar.orEmpty().isNotEmpty()) caps+="technique.tremolo-bar.v1"
        if(a.slide!="none") caps+="technique.legato.v1"
        if(a.tapping==true) caps+="technique.tapping.v1"
        if(a.palmMute==true || a.short==true || a.cut==true) caps+="technique.mute.v1"
        if(a.letRing==true) caps+="technique.sustain.v1"
        if((a.strumDelayMs ?: 0.0)>0) caps+="technique.strum.v1"
        a.ornament?.let { o ->
            check(o.notes.isNotEmpty() && o.notes.all { it.duration>Q.ZERO },"Invalid grace group")
            val sum=o.notes.fold(Q.ZERO) { s,g -> s+g.duration }
            check(sum<duration && !(o.timing=="before" && at<sum),"Grace must fit a source interval")
            check(a.repluck==null && a.bend.orEmpty().isEmpty() && a.slide=="none","Unsupported grace combination")
            caps+="technique.grace.v1"
        }
        a.repluck?.let { r ->
            check(r.unit>Q.ZERO && duration/r.unit<=Q(1024) && (duration/r.unit).d==1L,"Repluck must divide note within budget")
            check(r.kind!="trill" || r.alternateFret!=null,"Trill requires alternate fret")
            check(a.letRing!=true && a.slide=="none" && a.ornament==null,"Unsupported repluck combination")
            caps+="technique.repluck.v1"
        }
    }
}
