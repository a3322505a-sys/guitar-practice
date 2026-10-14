package com.a3322505a.guitarlearning.assets.song

import com.a3322505a.guitarlearning.music.score.*

internal fun Map<String,Any?>.s(key: String, default: String = "") = this[key] as? String ?: default
internal fun Map<String,Any?>.i(key: String, default: Int = 0) = this[key]?.intValue() ?: default
internal fun Map<String,Any?>.b(key: String, default: Boolean = false) = this[key] as? Boolean ?: default
internal fun Map<String,Any?>.q(key: String, default: Q = Q.ZERO) = this[key]?.qValue() ?: default
internal fun Map<String,Any?>.num(key: String, default: Double = 0.0) = this[key]?.doubleValue() ?: default
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
                    m["incompleteReason"] as? String,m["sourceRef"] as? String,m.q("leadIn"))
            },
            root.objects("tracks").map { Track(it.s("id"),it.s("name"),
                it.list("openStringMidi").map { v -> v.intValue() },it.i("capo"),it.i("fretCount",24),it.s("instrument","guitar")) },
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
            root.objects("harmonies").map { HarmonySymbol(it.s("id"),it.s("measureId"),it.q("onset"),it.s("name"),it["voicingId"] as? String,condition(it["condition"])) })
    }
    fun voicing(v: Map<String,Any?>) = GuitarVoicing(v.s("id"),v.s("name"),v.s("trackId"),
        v.list("frets").map { it.intValue() },v.list("fingers").map { it.intValue() },
        v["teaching"]?.objectValue()?.let { t -> VoicingTeaching(t.i("root",-1),t["third"]?.intValue(),
            t.s("soundingName"),t.s("group"),t.s("source"),labels(t["intervalRoles"]),labels(t["toneNames"])) })
    private fun labels(value: Any?) = value?.objectValue()?.map { (k,v) -> k.toInt() to v.textValue() }?.toMap().orEmpty()
    fun teaching(value: Any?) = value?.objectValue()?.let { t -> PracticeTeaching(t.s("category"),t.s("passage"),
        t.s("source"),t.s("technique"),t.s("use"),t["version"] as? String,t.s("tempoLabel"),t.s("explanation")) }
    private fun event(e: Map<String,Any?>): ScoreEvent {
        val written=e["written"].objectValue()
        return ScoreEvent(e.s("id"),e.s("measureId"),e.q("onset"),e.q("duration"),
            WrittenValue(written.i("baseDenominator"),written.i("dots"),written.strings("tupletGroupIds")),
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
    fun practices(data: Any?) = data.objectValue().objects("practices").map { p ->
        fun coordinate(c: Any?)=c.objectValue().let { Coordinate(it.s("measureId"),it.q("offset"),it["occurrence"]?.intValue()) }
        PracticeSpec(p.s("id"),p.s("title"),p.s("goal"),p.s("mainTrackId"),p.strings("mixTrackIds"),
            p.objects("segments").map { PracticeSegment(coordinate(it["start"]),coordinate(it["end"]),it.s("formPolicy","written"),
                it.s("entry","reject"),it.s("exit","reject"),it.strings("affectedNoteIds")) },
            p.i("suggestedTempo",60),p["familyId"] as? String,p["variantOf"] as? String,p.strings("relatedIds"),
            p.s("visibility","core"),p.i("order"),p.strings("tags"),p.q("padding"),p["paddingReason"] as? String,
            p.s("tempoMode","source"),p.s("difficulty","自行调整"),teaching(p["teaching"]),p.strings("legacyIds"))
    }
}
