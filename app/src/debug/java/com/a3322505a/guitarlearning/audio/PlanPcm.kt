package com.a3322505a.guitarlearning.audio

import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import kotlin.math.*

internal data class SampleSource(val root: Int,val samples: FloatArray,val sampleRate: Int,val attackMs: Double,val releaseMs: Int)
internal data class TonePart(val note: PlanNote,val start: Double,val end: Double,val pitch: Double,
    val incoming: String?,val curve: List<Pair<Double,Double>>,val barCurve: List<Pair<Double,Double>>,
    val articulation: Articulation = note.articulation,val vibratoStart: Double = start,val muteStart: Double = start)
internal data class Tone(val id: String,val parts: List<TonePart>,val start: Double,val end: Double,val sample: SampleSource,
    val activation: Double = start)

/** Streaming sampler with at most 256 active phase states and no whole-song/loop wave cache. */
class Pcm(val session: Session, private val sampler: Sampler) {
    private val plan=session.plan
    private val timing=LegacySessionClock(session)
    private val historyClocks=mutableMapOf<Int,TempoMap>()
    private fun seconds(q: Q,e: PlanEvent): Double {
        if((e.activation>e.start || e.sourceOffset>Q.ZERO) && q<e.activation) {
            val visit=plan.visits[e.visit]
            val sourceClock=historyClocks.getOrPut(e.visit) {
                TempoMap(plan.score.length.value(),plan.score.tempos.filter { it.condition?.matches(visit.occurrence,visit.repeatContext) ?: true }
                    .groupBy { it.at }.values.map { it.last().copy(condition=null) }.sortedBy { it.at },
                    if(session.sourceTempo) session.tempoMultiplier else 1.0,
                    if(session.sourceTempo) TempoMode.SOURCE else TempoMode.FIXED,session.bpm.toDouble(),
                    plan.score.swing.filter { it.trackIds.isEmpty() && it.voiceIds.isEmpty() })
            }
            val sourceEntry=e.sourceStart+e.activation-e.start+e.sourceOffset
            val original=e.sourceStart+q-e.start+e.sourceOffset
            return timing.clock.seconds(e.activation.value())+sourceClock.seconds(original.value())-sourceClock.seconds(sourceEntry.value())
        }
        return timing.clock.performedSeconds(timing.performed(q.value(),e))
    }
    private val tones: List<Tone>
    private val intervalIndex: IntervalIndex<Tone>
    private class PhaseState {
        var frame=0L;var phase=0.0;var low=0f;var seed=0L;var part=0
    }
    private val phases=object : LinkedHashMap<String,PhaseState>(32,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,PhaseState>?) = size>256
    }
    private data class DynamicMark(val time: Double,val position: Double,val velocity: Double,val end: Double?,val endVelocity: Double?)
    private val dynamicMarks=plan.score.tracks.associate { track ->
        track.id to plan.dynamics.filter { it.trackId==null || it.trackId==track.id }
            .sortedWith(compareBy<Dynamic> { it.at }.thenBy { it.trackId!=null }).map {
                DynamicMark(timing.clock.seconds(it.at.value()),timing.clock.performed(it.at.value()),it.velocity,
                    it.rampEnd?.let { q -> timing.clock.performed(q.value()) },it.endVelocity)
            }
    }
    private fun dynamicGain(track: String,time: Double): Double {
        val marks=dynamicMarks.getValue(track)
        if(marks.isEmpty() || time<marks.first().time) return 1.0
        var lo=0;var hi=marks.lastIndex
        while(lo<hi) { val mid=(lo+hi+1)/2;if(marks[mid].time<=time) lo=mid else hi=mid-1 }
        val m=marks[lo]
        return m.velocity+(if(m.end!=null) (m.endVelocity!!-m.velocity)*
            ((timing.clock.performedAtSeconds(time)-m.position)/(m.end-m.position)).coerceIn(0.0,1.0) else 0.0)
    }
    init {
        if(session.sequence.runtimePlan==null) session.sequence.validateExpression()
        val outgoing=plan.relations.filter { it.kind !in listOf("slur","slide-pick") }.associateBy { it.from }
        val incoming=outgoing.values.associateBy { it.to }
        val output=mutableListOf<Tone>()
        val shared=mutableSetOf<String>()
        fun part(n: PlanNote,kind: String? = null,pitch: Double=n.soundPitch,start: Q=n.start,end: Q=n.soundEnd): TonePart {
            fun curve(points: List<CurvePoint>)=points.map { seconds(n.start-n.event.sourceOffset+it.at,n.event) to it.semitones }
            val delay=if(kind==null) (n.articulation.strumDelayMs ?: 0.0)*n.event.event.notes.indexOf(n.note)/1000 else 0.0
            return TonePart(n,seconds(start,n.event)+delay,seconds(end,n.event),pitch,kind,curve(n.articulation.bend.orEmpty()),curve(n.articulation.tremoloBar.orEmpty()))
        }
        plan.notes.filter { it.track.id in plan.practice.mixTrackIds }.forEach { n ->
            val sharedId=n.note.sharedSoundId?.let { n.event.start.toString()+"/"+it }
            if(sharedId!=null && !shared.add(sharedId)) return@forEach
            if(n.id in incoming) return@forEach
            val parts=mutableListOf<TonePart>()
            val repluck=n.articulation.repluck
            val ornament=n.articulation.ornament
            if(repluck!=null) {
                val count=(n.event.duration/repluck.unit).n.toInt()
                repeat(count) { i ->
                    val begin=n.start+repluck.unit*Q(i.toLong()); val end=minOf(n.soundEnd,begin+repluck.unit)
                    val pitch=if(repluck.kind=="trill" && i%2==1) n.track.midi(n.note.string,repluck.alternateFret!!).toDouble() else n.soundPitch
                    if(end>begin) {
                        val p=part(n,pitch=pitch,start=begin,end=end)
                        output+=Tone(n.id+"/repluck/"+i,listOf(p),p.start,p.end,sampler.source(pitch.roundToInt()))
                    }
                }
                return@forEach
            } else if(ornament!=null) {
                val total=ornament.notes.fold(Q.ZERO) { a,g -> a+g.duration }
                var begin=if(ornament.timing=="before") n.start-total else n.start
                ornament.notes.forEachIndexed { i,g ->
                    val end=begin+g.duration
                    parts+=part(n,if(i==0) null else ornament.notes[i-1].kind,n.track.midi(n.note.string,g.fret).toDouble(),begin,end)
                    begin=end
                }
                parts+=part(n,ornament.notes.last().kind,start=begin)
            } else parts+=part(n)
            var current=n
            val visited=mutableSetOf(n.id)
            while(true) {
                val link=outgoing[current.id] ?: break
                musicCheck(visited.add(link.to),"RELATION_CYCLE","/score/relations","Acoustic cycle")
                val next=plan.noteById.getValue(link.to)
                parts+=part(next,link.kind)
                current=next
            }
            plan.outgoingRelations[current.id].orEmpty().firstOrNull { it.kind=="slide-pick" }?.let { link ->
                val target=plan.noteById.getValue(link.to)
                parts+=part(target,"slide-pick",end=target.start)
            }
            for(i in 1..parts.lastIndex) {
                val p=parts[i];val previous=parts[i-1]
                if(p.incoming=="tie") {
                    val raw=p.note.note.articulation.over(p.note.event.event.articulation)
                    val inherited=raw.over(previous.articulation)
                    parts[i]=p.copy(articulation=inherited,
                        curve=if(raw.bend==null) listOf(p.start to (previous.curve.lastOrNull()?.second ?: 0.0)) else p.curve,
                        barCurve=if(raw.tremoloBar==null) listOf(p.start to (previous.barCurve.lastOrNull()?.second ?: 0.0)) else p.barCurve,
                        vibratoStart=if(raw.vibrato==null) previous.vibratoStart else p.start)
                }
                val next=parts[i]
                if(next.articulation.palmMute==true && previous.articulation.palmMute==true)
                    parts[i]=next.copy(muteStart=previous.muteStart)
            }
            // Picked grace transitions start another sample; slur/legato transitions retain phase.
            val groups=mutableListOf<MutableList<TonePart>>()
            parts.forEach { p ->
                if(groups.isEmpty() || p.incoming=="pick") groups+=mutableListOf<TonePart>()
                groups.last()+=p
            }
            groups.forEachIndexed { i,group ->
                val first=group.first()
                val end=groups.getOrNull(i+1)?.first()?.start ?: group.maxOf { it.end }
                musicCheck(end>first.start,"TIMING","/score/voices/"+n.event.voiceId+"/"+n.note.id,
                    "Strum delay or release leaves no sound; reduce tempo or stroke delay")
                output+=Tone(n.id+"/attack/"+i,group,first.start,end,sampler.source(first.pitch.roundToInt()),timing.clock.seconds(n.event.activation.value()))
            }
        }
        // Before-beat ornaments steal from the previous sound on their own string.
        val cutoff=mutableMapOf<String,Double>()
        output.groupBy { it.parts.first().note.track.id to it.parts.first().note.note.string }.values.forEach { string ->
            string.sortedBy { it.start }.zipWithNext().forEach { (a,b) -> cutoff[a.id]=minOf(a.end,b.start) }
        }
        tones=output.map { it.copy(end=cutoff[it.id] ?: it.end) }.filter { it.end>it.start }.sortedBy { it.start }
        tones.forEach {
            musicCheck((it.end-maxOf(it.start,it.activation))*SAMPLE_RATE>=1,"TIMING_RESOLUTION","/practices/"+plan.practice.id,
                "A note is shorter than one audio frame; reduce tempo")
        }
        musicCheck(tones.size<=1_000_000,"PLAN_LIMIT","/practices","Derived acoustic onset limit")
        intervalIndex=IntervalIndex(tones,{ maxOf(it.start,it.activation) },{ it.end })
    }
    private fun candidates(a: Double,b: Double): List<Tone> {
        return intervalIndex.query(a,b)
    }
    fun activeNotes(tick: Double): List<PlanNote> {
        if(tick<session.preDuration.value()*session.quarter) return emptyList()
        val local=timing.localTick(tick)/session.quarter
        val time=timing.clock.seconds(local)
        return candidates(time,time+1e-9).flatMap { tone ->
            val part=tone.parts.lastOrNull { it.start<=time } ?: tone.parts.first()
            listOf(part.note)
        }.filter { it.track.id==plan.practice.mainTrackId }
    }
    fun block(start: Long,count: Int,guitar: Boolean,click: Boolean): ShortArray {
        require(count>=0)
        val mix=FloatArray(count)
        val startTick=timing.tick(start); val endTick=timing.tick(start+count)
        if(guitar) {
            val first=max(0,timing.loopAt(startTick)-1); val last=max(0,timing.loopAt(endTick))
            for(loop in first..last) {
                if(session.repeats>0 && loop>=session.repeats) break
                val rangeStart=timing.loopStartFrame(loop)
                val rangeEnd=timing.loopStartFrame(loop+1)
                val base=timing.preSeconds+loop*timing.loopSeconds-timing.sourceStartSeconds
                val a=start.toDouble()/SAMPLE_RATE-base;val b=(start+count).toDouble()/SAMPLE_RATE-base
                candidates(maxOf(a,timing.sourceStartSeconds),minOf(b,timing.sourceStartSeconds+timing.loopSeconds)).forEach { tone ->
                    val onset=((base+tone.start)*SAMPLE_RATE).roundToLong()
                    val stop=((base+tone.end)*SAMPLE_RATE).roundToLong()
                    val gate=((base+tone.activation)*SAMPLE_RATE).roundToLong()
                    val from=maxOf(start,onset,rangeStart,gate);val until=minOf(start+count,stop,rangeEnd)
                    if(from<until) {
                        val key=tone.id+"/"+loop
                        var state=phases.getOrPut(key) { PhaseState().also { it.seed=7919L*tone.parts.first().note.note.string } }
                        if(state.frame>from-onset) { state=PhaseState().also { it.seed=7919L*tone.parts.first().note.note.string };phases[key]=state }
                        while(state.frame<from-onset) sample(tone,state,stop-onset,renderGain=false)
                        for(frame in from until until) {
                            var value=sample(tone,state,stop-onset,frame.toDouble()/SAMPLE_RATE-base)
                            if(rangeStart>onset) value*=((frame-rangeStart)/(SAMPLE_RATE*.005f)).coerceIn(0f,1f)
                            if(rangeEnd<stop) value*=((rangeEnd-frame-1)/(SAMPLE_RATE*.005f)).coerceIn(0f,1f)
                            mix[(frame-start).toInt()]+=value
                        }
                    }
                }
            }
        }
        if(click && !session.preview) clicks(start,start+count,mix)
        return ShortArray(count) { (mix[it].coerceIn(-.96f,.96f)*32767).roundToInt().toShort() }
    }
    private fun contour(points: List<Pair<Double,Double>>,time: Double): Double {
        if(points.isEmpty()) return 0.0
        val i=points.indexOfLast { it.first<=time }.coerceAtLeast(0)
        val a=points[i];val b=points.getOrElse(i+1) { a }
        return if(a.first==b.first) a.second else a.second+(b.second-a.second)*((time-a.first)/(b.first-a.first)).coerceIn(0.0,1.0)
    }
    private fun sample(tone: Tone,state: PhaseState,totalFrames: Long,timelineTime: Double? = null,renderGain: Boolean = true): Float {
        val time=tone.start+state.frame.toDouble()/SAMPLE_RATE
        while(state.part<tone.parts.lastIndex && tone.parts[state.part+1].start<=time) state.part++
        val current=tone.parts[state.part]
        val next=tone.parts.getOrNull(state.part+1)
        var pitch=current.pitch.toDouble()
        if(next!=null && next.incoming in listOf("slide","slide-pick","hammer","pull","tap")) {
            val progress=if(next.incoming=="slide" || next.incoming=="slide-pick") ((time-current.start)/(next.start-current.start)).coerceIn(0.0,1.0)
                else ((time-next.start+.003)/.003).coerceIn(0.0,1.0)
            pitch+=(next.pitch-current.pitch)*progress
        }
        val art=current.articulation
        pitch+=contour(current.curve,time)+contour(current.barCurve,time)
        val vib=art.vibrato
        if(vib!=null) pitch+=vib.depth*sin(2*PI*vib.rateHz*(time-current.vibratoStart))
        val local=(time-current.start)/max(1e-9,current.end-current.start)
        pitch+=when(art.slide) {
            "in-up" -> -3*(1-local*4).coerceAtLeast(0.0)
            "in-down" -> 3*(1-local*4).coerceAtLeast(0.0)
            "out-up" -> 3*((local-.75)*4).coerceAtLeast(0.0)
            "out-down" -> -3*((local-.75)*4).coerceAtLeast(0.0)
            else -> 0.0
        }
        val source=tone.sample
        var value: Float
        if(current.note.note.dead) {
            state.seed=(state.seed*1664525+1013904223) and 0xffffffffL
            val noise=(state.seed ushr 8)/8388608f-1f
            state.low+=.25f*(noise-state.low)
            value=(.40f*(noise-state.low)*exp(-state.frame/(SAMPLE_RATE*.035))*min(1f,state.frame/(SAMPLE_RATE*.002f))).toFloat()
        } else {
            val index=state.phase.toLong()
            value=if(index>=source.samples.lastIndex || index<0) 0f else source.samples[index.toInt()]+
                (source.samples[index.toInt()+1]-source.samples[index.toInt()])*(state.phase-index).toFloat()
            state.phase+=source.sampleRate.toDouble()/SAMPLE_RATE*2.0.pow((pitch-source.root)/12)
            val release=maxOf(1L,minOf(totalFrames/3,SAMPLE_RATE.toLong()*source.releaseMs/1000))
            value*=(min(1.0,state.frame/(SAMPLE_RATE*source.attackMs/1000))*min(1.0,(totalFrames-state.frame-1).toDouble()/release)).toFloat()
            if(art.palmMute==true) { state.low+=.16f*(value-state.low);value=state.low*exp(-(time-current.muteStart)/.10).toFloat() }
        }
        state.frame++
        if(!renderGain) return 0f
        val dynamicGain=dynamicGain(current.note.track.id,timelineTime ?: time)
        val gain=.72/sqrt(current.note.event.event.notes.size.toDouble())*(art.velocity ?: 1.0)*
            (if(art.accent==true) 1.25 else 1.0)*dynamicGain*(if(art.tapping==true) .65 else 1.0)
        return value*gain.toFloat()
    }
    private fun clicks(a: Long,b: Long,mix: FloatArray) {
        val pre=session.preDuration.value()
        if(pre>0 && a<timing.loopStartFrame(0)) {
            session.preMeter.groups.forEachIndexed { i,q -> addClick(a,b,mix,timing.frame(q.value()*session.quarter),i==0) }
            if(session.leadIn>Q.ZERO) session.preMeter.groups.filter { it<session.leadIn }.forEachIndexed { i,q ->
                addClick(a,b,mix,timing.frame((session.preMeter.duration+q).value()*session.quarter),i==0)
            }
        }
        val first=max(0,timing.loopAt(timing.tick(a))-1);val last=max(0,timing.loopAt(timing.tick(b)))
        for(loop in first..last) {
            if(session.repeats>0 && loop>=session.repeats) break
            val local=timing.localTick(timing.tick(a))/session.quarter
            val from=plan.visitAt(local)-1
            val until=plan.visitAt(timing.localTick(timing.tick(b))/session.quarter)+1
            val indices=if(from<=until) max(0,from)..min(plan.visits.lastIndex,until) else plan.visits.indices
            for(i in indices) {
                val v=plan.visits[i]
                v.measure.meter.groups.forEachIndexed { group,q ->
                    val sourceOffset=plan.score.measureIndices[v.measure.id]?.let { v.sourceStart-plan.score.measureStarts[it] } ?: Q.ZERO
                    val local=q-v.measure.leadIn-sourceOffset
                    val written=v.start+local
                    if(local>=Q.ZERO && written>=session.rangeStart && written<session.rangeEnd && local<v.duration)
                        addClick(a,b,mix,timing.noteFrame(loop,timing.clock.performed(written.value())),group==0)
                }
            }
        }
    }
    private fun addClick(a: Long,b: Long,mix: FloatArray,onset: Long,accent: Boolean) {
        val first=max(a,onset);val end=min(b,onset+SAMPLE_RATE*40/1000)
        for(f in first until end) {
            val i=f-onset
            mix[(f-a).toInt()]+=(sin(2*PI*(if(accent) 1800 else 1100)*i/SAMPLE_RATE)*exp(-i/(SAMPLE_RATE*.008))*.20).toFloat()
        }
    }
}
