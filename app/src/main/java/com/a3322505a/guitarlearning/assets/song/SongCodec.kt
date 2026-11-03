package com.a3322505a.guitarlearning.assets.song

import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.music.*

// Required scalar reads never invent musical facts. Optional reads name their defaults.
private fun Map<String,Any?>.required(key: String): Any {
    musicCheck(containsKey(key) && this[key]!=null,"INPUT","/"+key,"Missing required field "+key)
    return getValue(key)!!
}
internal fun Map<String,Any?>.s(key: String) = required(key).textValue()
internal fun Map<String,Any?>.s(key: String, default: String) = if(containsKey(key)) required(key).textValue() else default
internal fun Map<String,Any?>.i(key: String) = required(key).intValue()
internal fun Map<String,Any?>.i(key: String, default: Int) = if(containsKey(key)) required(key).intValue() else default
internal fun Map<String,Any?>.b(key: String, default: Boolean = false) = if(containsKey(key)) required(key) as Boolean else default
internal fun Map<String,Any?>.q(key: String) = required(key).qValue()
internal fun Map<String,Any?>.q(key: String, default: Q) = if(containsKey(key)) required(key).qValue() else default
internal fun Map<String,Any?>.num(key: String) = required(key).doubleValue()
internal fun Map<String,Any?>.list(key: String) = (this[key] as? List<Any?>).orEmpty()
internal fun Map<String,Any?>.strings(key: String) = list(key).map { it.textValue() }
internal fun Map<String,Any?>.objects(key: String) = list(key).map { it.objectValue() }

data class PackageIdentity(val packageId: String, val revision: Int, val digest: String)
data class SongPackage(val identity: PackageIdentity, val title: String, val author: String, val summary: String,
    val score: ScoreDocument, val practices: List<PracticeSpec>, val aliases: Map<String,String>,
    val requires: Set<String>, val warnings: List<String>, val sourceDescription: String,
    val files: Map<String,ByteArray>)

object SongCodec {
    private fun condition(value: Any?): VisitCondition? = value?.objectValue()?.let { c ->
        VisitCondition(c["occurrence"]?.intValue(),c["repeatPasses"]?.objectValue()?.mapValues { (_,v) -> v.arrayValue().map { it.intValue() } }.orEmpty())
    }
    fun score(data: Any?): ScoreDocument = data.objectValue().let { root ->
        ScoreDocument(root.s("documentId"),
            root.objects("measures").map { m ->
                val meter=m["meter"].objectValue()
                Measure(m.s("id"),m.s("label"),Meter(meter.i("numerator"),meter.i("denominator"),
                    meter.list("beatGroups").map { it.intValue() }),m.q("actualDuration"),
                    m["incompleteReason"] as? String,m["sourceRef"] as? String,m.q("leadIn",Q.ZERO))
            },
            root.objects("tracks").map { Track(it.s("id"),it.s("name"),
                it.list("openStringMidi").map { v -> v.intValue() },it.i("capo",0),it.i("fretCount",24),it.s("instrument","guitar")) },
            root.objects("voices").map { v -> Voice(v.s("id"),v.s("trackId"),v.objects("events").map(::event)) },
            root.objects("tuplets").map { TupletGroup(it.s("id"),it.s("voiceId"),it.strings("eventIds"),
                it.i("actual"),it.i("normal"),it["parentId"] as? String) },
            root.objects("relations").map { Relation(it.s("id"),it.s("kind"),it.s("fromNoteId"),it.s("toNoteId")) },
            root.objects("tempos").map { Tempo(it.q("at"),it.q("beatUnit"),it.num("bpm"),it["rampEnd"]?.qValue(),it["endBpm"]?.doubleValue(),condition(it["condition"])) },
            root.objects("dynamics").map { Dynamic(it.q("at"),it.num("velocity"),it["trackId"] as? String,
                it["rampEnd"]?.qValue(),it["endVelocity"]?.doubleValue(),condition(it["condition"])) },
            root.objects("swing").map { SwingSpec(it.q("start"),it.q("end"),it.q("unit"),it.list("ratio").map { v -> v.intValue() },
                it.strings("trackIds"),it.strings("voiceIds")) },
            form(root["form"].objectValue()),
            root.objects("voicings").map(::voicing),
            root.objects("harmonies").map { HarmonySymbol(it.s("id"),it.s("measureId"),it.q("onset"),it.s("name"),it["voicingId"] as? String,condition(it["condition"]),it["validUntil"]?.qValue()) })
    }
    fun voicing(v: Map<String,Any?>) = GuitarVoicing(v.s("id"),v.s("name"),v.s("trackId"),
        v.list("frets").map { it.intValue() },v.list("fingers").map { it.intValue() },
        v["teaching"]?.objectValue()?.let { t -> VoicingTeaching(t.i("root",-1),t["third"]?.intValue(),
            t.s("soundingName"),t.s("group"),t.s("source",""),labels(t["intervalRoles"]),labels(t["toneNames"])) },
        v.objects("barres").map { Barre(it.i("finger"),it.i("fret"),it.i("fromString"),it.i("toString")) },
        (v["quality"] as? String)?.let(ChordQuality::valueOf))
    fun lesson(value: Any?): LessonMetadata? = value?.objectValue()?.let { t ->
        LessonMetadata(TeachingModule.valueOf(t.s("module")), t["key"]?.objectValue()?.let {
            KeyContext(SpelledPitch.parse(it.s("tonic")), ScaleDefinition.valueOf(it.s("scale"))) },
            t.strings("warmupIds"), t.strings("transitionIds"), t.strings("leadTrackIds"), t.strings("chainStarts"),
            t.objects("actions").map { TeachingAction(it.s("eventId"),it.s("kind"),it.s("text"),it["voicingId"] as? String,it["fromVoicingId"] as? String) },t.s("referenceChordName","C"))
    }
    fun lessonMap(value: LessonMetadata?): Map<String,Any?>? = value?.let { t -> mapOf(
        "module" to t.module.name,"referenceChordName" to t.referenceChordName, "key" to t.key?.let { mapOf("tonic" to it.tonic.name,"scale" to it.scale.name) },
        "warmupIds" to t.warmupIds,"transitionIds" to t.transitionIds,"leadTrackIds" to t.leadTrackIds,
        "chainStarts" to t.chainStarts,"actions" to t.actions.map { mapOf("eventId" to it.eventId,"kind" to it.kind,"text" to it.text,"voicingId" to it.voicingId,"fromVoicingId" to it.fromVoicingId) }) }
    private fun labels(value: Any?) = value?.objectValue()?.map { (k,v) -> k.toInt() to v.textValue() }?.toMap().orEmpty()
    fun teaching(value: Any?) = value?.objectValue()?.let { t -> PracticeTeaching(t.s("category",""),t.s("passage",""),
        t.s("source",""),t.s("technique",""),t.s("use",""),t["version"] as? String,t.s("tempoLabel",""),t.s("explanation","")) }
    private fun event(e: Map<String,Any?>): ScoreEvent {
        val written=e["written"].objectValue()
        return ScoreEvent(e.s("id"),e.s("measureId"),e.q("onset"),e.q("duration"),
            WrittenValue(written.i("baseDenominator"),written.i("dots",0),written.strings("tupletGroupIds")),
            e.objects("notes").map { n ->
                val harmonic=n["harmonic"]?.objectValue()?.let { Harmonic(it.s("kind"),it.i("partial"),it.i("touchFret"),it.i("soundMidi")) }
                ScoreNote(n.s("id"),n.i("string"),n.i("fret"),n.i("midi"),n.b("dead"),harmonic,
                    articulation(n["articulation"]),n["sourceRef"] as? String,n["sharedSoundId"] as? String)
            },articulation(e["articulation"]),e.b("measureRest"),e["practicePadding"] as? String,e["sourceRef"] as? String)
    }
    private fun articulation(value: Any?): Articulation {
        if(value==null) return Articulation()
        val e=value.objectValue()
        fun curve(key: String)=e[key]?.let { e.objects(key).map { p -> CurvePoint(p.q("at"),p.num("semitones")) } }
        return Articulation(e["velocity"]?.doubleValue(),e["accent"] as? Boolean,e["pick"] as? String,
            e["palmMute"] as? Boolean,e["short"] as? Boolean,e["letRing"] as? Boolean,
            e["ringUntil"]?.qValue(),e["cut"] as? Boolean,e["strumDelayMs"]?.doubleValue(),
            curve("bend"),e["vibrato"]?.objectValue()?.let { Oscillation(it.num("depth"),it.num("rateHz")) },
            curve("tremoloBar"),e["slide"] as? String,e["tapping"] as? Boolean,
            e["ornament"]?.objectValue()?.let { o -> Ornament(o.s("timing"),o.objects("notes").map {
                GraceNote(it.i("fret"),it.q("duration"),it.s("kind","pick")) }) },
            e["repluck"]?.objectValue()?.let { Repluck(it.s("kind"),it.q("unit"),it["alternateFret"]?.intValue()) })
    }
    private fun form(f: Map<String,Any?>) = Form(
        f.objects("repeats").map { Repeat(it.s("id"),it.s("start"),it.s("end"),it.i("times")) },
        f.objects("endings").map { Ending(it.s("repeatId"),it.s("start"),it.s("end"),it.list("passes").map { n -> n.intValue() }) },
        f.objects("markers").map { Marker(it.s("id"),it.s("measureId"),it.s("kind")) },
        f.objects("jumps").map { Jump(it.s("id"),it.s("at"),it.s("target"),it.s("kind"),it.i("occurrence",1),condition(it["condition"])) },
        f.b("repeatsAfterJump"))
    fun practices(data: Any?): List<PracticeSpec> {
        val root=data.objectValue()
        val muteMainTrack=root["extensions"]?.objectValue()?.get("playback.mute-main-track")=="true"
        return root.objects("practices").map { p ->
        fun coordinate(c: Any?)=c.objectValue().let { Coordinate(it.s("measureId"),it.q("offset",Q.ZERO),it["occurrence"]?.intValue()) }
        PracticeSpec(p.s("id"),p.s("title"),p.s("goal"),p.s("mainTrackId"),p.strings("mixTrackIds"),
            p.objects("segments").map { PracticeSegment(coordinate(it["start"]),coordinate(it["end"]),it.s("formPolicy","written"),
                it.s("entry","reject"),it.s("exit","reject"),it.strings("affectedNoteIds")) },
            p.i("suggestedTempo",60),p["familyId"] as? String,p["variantOf"] as? String,p.strings("relatedIds"),
            p.s("visibility","core"),p.i("order",0),p.strings("tags"),p.q("padding",Q.ZERO),p["paddingReason"] as? String,
            p.s("tempoMode","source"),p.s("difficulty","自行调整"),teaching(p["teaching"]),
            p.objects("noteFingerings").map { NoteFingering(it.s("sourceNoteId"),it["finger"]?.intValue(),
                it.s("source",""),it["occurrence"]?.intValue()) }, lesson(p["lesson"]),muteMainTrack)
        }
    }
}

/** One v2 preprocessing entry. Schema shape is checked before this function. */
@Suppress("UNCHECKED_CAST")
internal fun normalizeSong(roots: Map<String,Map<String,Any?>>, summary: MutableList<String>): Map<String,Map<String,Any?>> {
    fun mutable(value: Any?) = value as MutableMap<String,Any?>
    fun qmap(value: Q): Map<String,Any?> = mapOf("n" to value.n.toBigDecimal(),"d" to value.d.toBigDecimal())
    fun canonical(value: Any?) {
        when(value) {
            is Map<*,*> -> {
                val map=mutable(value)
                if(map.keys==setOf("n","d")) map.putAll(qmap(map.qValue()))
                else map.filterKeys { it!="extensions" }.values.forEach(::canonical)
            }
            is List<*> -> value.forEach(::canonical)
        }
    }
    canonical(roots)
    val score=mutable(roots.getValue("score.json"));val cards=mutable(roots.getValue("practices.json"))
    listOf("tuplets","relations","dynamics","swing","voicings","harmonies").forEach { if(!score.containsKey(it)) score[it]=emptyList<Any?>() }
    if(!score.containsKey("form")) score["form"]=emptyMap<String,Any?>()
    if(!score.containsKey("tempos") || score.list("tempos").isEmpty()) {
        score["tempos"]=listOf(mapOf("at" to qmap(Q.ZERO),"beatUnit" to qmap(Q.ONE),"bpm" to 120))
        summary+="采用默认速度 120 BPM"
    }
    if(!cards.containsKey("aliases")) cards["aliases"]=emptyList<Any?>()
    val measures=score.objects("measures");val byMeasure=measures.associateBy { it.s("id") }
    val starts=mutableMapOf<String,Q>();var length=Q.ZERO
    measures.forEach { starts[it.s("id")]=length;length+=it.q("actualDuration") }
    val tracks=score.objects("tracks").associateBy { it.s("id") }
    val ids=mutableSetOf<String>()
    fun collect(value: Any?) {
        when(value) {
            is Map<*,*> -> { (value["id"] as? String)?.let { ids+=it };value.filterKeys { it!="extensions" }.values.forEach(::collect) }
            is List<*> -> value.forEach(::collect)
        }
    }
    collect(roots)
    val values=listOf(1,2,4,8,16,32,64).flatMap { base -> (0..3).map { dots ->
        WrittenValue(base,dots).straight to mapOf<String,Any?>("baseDenominator" to base,"dots" to dots)
    } }
    fun written(duration: Q,path: String): Map<String,Any?> {
        val value=values.firstOrNull { it.first==duration }?.second
        musicCheck(value!=null,"WRITTEN",path,"Cannot infer an exact ordinary/dotted value")
        return value!!
    }
    val tupletMembers=score.objects("tuplets").flatMap { it.strings("eventIds") }.toSet()
    var serial=0;var eventCount=score.objects("voices").sumOf { it.objects("events").size }
    val notes=mutableMapOf<String,Pair<Map<String,Any?>,MutableMap<String,Any?>>>()
    score.objects("voices").forEachIndexed { vi,voice ->
        val path="/score/voices/"+vi
        val track=tracks[voice.s("trackId")]
        musicCheck(track!=null,"MUSIC",path+"/trackId","Missing track")
        voice.objects("events").forEachIndexed { ei,event ->
            val ep=path+"/events/"+ei
            if(!event.containsKey("written")) {
                musicCheck(event.s("id") !in tupletMembers,"WRITTEN",ep,"Tuplet member requires explicit written value")
                mutable(event)["written"]=written(event.q("duration"),ep+"/written")
            }
            event.objects("notes").forEach { note ->
                if(!note.containsKey("midi")) {
                    var pitch=track!!.list("openStringMidi")[note.i("string")-1].intValue()+track.i("capo",0)+note.i("fret")
                    note["harmonic"]?.objectValue()?.let { pitch+=(12*kotlin.math.log2(it.i("partial").toDouble())).let { value -> kotlin.math.floor(value+0.5).toInt() } }
                    mutable(note)["midi"]=pitch
                }
                notes[note.s("id")]=event to mutable(note)
            }
        }
        val completed=mutableListOf<Map<String,Any?>>()
        var covered=Q.ZERO;var mi=0
        fun fill(end: Q) {
            while(covered<end) {
                while(mi<measures.size && covered>=starts.getValue(measures[mi].s("id"))+measures[mi].q("actualDuration")) mi++
                musicCheck(mi<measures.size,"MUSIC",path,"Gap outside score")
                val measure=measures[mi];val start=starts.getValue(measure.s("id"))
                val remaining=minOf(end,start+measure.q("actualDuration"))-covered
                val value=values.firstOrNull { it.first==remaining } ?: listOf(1,2,4,8,16,32,64)
                    .firstOrNull { Q(4,it.toLong())<=remaining }?.let { Q(4,it.toLong()) to mapOf<String,Any?>("baseDenominator" to it,"dots" to 0) }
                musicCheck(value!=null,"WRITTEN",path,"Gap cannot be expressed without ambiguous tuplets")
                val (duration,w)=value!!
                do { serial++ } while("auto-rest-"+serial in ids)
                val id="auto-rest-"+serial;ids+=id
                eventCount++;musicCheck(eventCount<=200000,"PLAN_LIMIT",path,"Event budget after rest completion")
                completed+=mapOf("id" to id,"measureId" to measure.s("id"),"onset" to qmap(covered-start),
                    "duration" to qmap(duration),"written" to w,"notes" to emptyList<Any?>())
                summary+="补休止 "+voice.s("id")+"/"+measure.s("id")+" @ "+(covered-start)+" + "+duration
                covered+=duration
            }
        }
        voice.objects("events").forEach { event ->
            val start=starts[event.s("measureId")]
            musicCheck(start!=null,"MUSIC",path,"Missing measure")
            val absolute=start!!+event.q("onset")
            musicCheck(absolute>=covered && absolute<length,"MUSIC",path,"Overlapping/unordered events or onset outside score")
            fill(absolute);completed+=event;covered=absolute+event.q("duration")
        }
        fill(length);mutable(voice)["events"]=completed
    }
    score.objects("relations").filter { it.s("kind") in listOf("tie","hammer","pull","slide","tap") }.forEach { relation ->
        notes[relation.s("toNoteId")]?.let { (event,note) ->
            if("pick" !in (note["articulation"]?.objectValue().orEmpty()) && "pick" !in (event["articulation"]?.objectValue().orEmpty())) {
                if(!note.containsKey("articulation")) note["articulation"]=mutableMapOf<String,Any?>()
                mutable(note["articulation"])["pick"]="none"
            }
        }
    }
    cards.objects("practices").forEach { card ->
        if(!card.containsKey("mixTrackIds")) mutable(card)["mixTrackIds"]=listOf(card.s("mainTrackId"))
        card.objects("segments").forEach { segment ->
            val start=mutable(segment["start"]);val end=mutable(segment["end"])
            if(!start.containsKey("offset")) start["offset"]=qmap(Q.ZERO)
            val measure=byMeasure[end.s("measureId")]
            musicCheck(measure!=null,"MUSIC","/practices","Missing end measure")
            if(!end.containsKey("offset")) end["offset"]=measure!!.getValue("actualDuration")
        }
    }
    return StrictJson.parse(StrictJson.encode(roots)).objectValue().mapValues { it.value.objectValue() }
}
