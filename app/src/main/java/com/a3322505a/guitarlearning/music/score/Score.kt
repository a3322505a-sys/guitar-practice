package com.a3322505a.guitarlearning.music.score

import com.a3322505a.guitarlearning.music.*
import java.math.BigInteger
import kotlin.math.abs

/** Quarter-note units. Every public value is reduced; intermediate arithmetic is exact. */
class Q(n: Long, d: Long = 1) : Comparable<Q> {
    val n: Long
    val d: Long
    init {
        musicCheck(d>0,"Q","/score/time","Rational denominator must be positive")
        if(n==Long.MIN_VALUE) {
            val a=BigInteger.valueOf(n); val b=BigInteger.valueOf(d); val gcd=a.gcd(b)
            this.n=a.divide(gcd).toLong(); this.d=b.divide(gcd).toLong()
        } else {
            var a=abs(n); var b=d
            while(b!=0L) { val remainder=a%b; a=b; b=remainder }
            this.n=n/a; this.d=d/a
        }
        musicCheck(this.n in -LIMIT..LIMIT && this.d<=LIMIT,
            "Q","/score/time","Rational arithmetic exceeds 10^12")
    }
    private fun calc(a: BigInteger, b: BigInteger): Q {
        val gcd = a.gcd(b)
        val n=a.divide(gcd);val d=b.divide(gcd);val limit=BigInteger.valueOf(LIMIT)
        musicCheck(n.abs()<=limit && d<=limit,"Q","/score/time","Rational arithmetic exceeds 10^12")
        return Q(n.toLong(),d.toLong())
    }
    // Exact Long operations avoid Android's JNI BigInteger path for ordinary score times.
    // Overflow falls back before reduction; large cancellable intermediates remain valid.
    private inline fun smallOrBig(small: ()->Q, big: ()->Q): Q = try { small() } catch(_: ArithmeticException) { big() }
    operator fun plus(other: Q) = smallOrBig({
        if(d==other.d) Q(Math.addExact(n,other.n),d) else Q(
            Math.addExact(Math.multiplyExact(n,other.d),Math.multiplyExact(other.n,d)),Math.multiplyExact(d,other.d))
    },{ calc(bn()*other.bd()+other.bn()*bd(), bd()*other.bd()) })
    operator fun minus(other: Q) = smallOrBig({
        if(d==other.d) Q(Math.subtractExact(n,other.n),d) else Q(
            Math.subtractExact(Math.multiplyExact(n,other.d),Math.multiplyExact(other.n,d)),Math.multiplyExact(d,other.d))
    },{ calc(bn()*other.bd()-other.bn()*bd(), bd()*other.bd()) })
    operator fun times(other: Q) = smallOrBig({ Q(Math.multiplyExact(n,other.n),Math.multiplyExact(d,other.d)) },
        { calc(bn()*other.bn(), bd()*other.bd()) })
    operator fun div(other: Q): Q {
        musicCheck(other.n!=0L,"Q","/score/time","Division by zero")
        val sign = if(other.n < 0) -1L else 1L
        return smallOrBig({ Q(Math.multiplyExact(Math.multiplyExact(n,other.d),sign),Math.multiplyExact(d,abs(other.n))) },
            { calc(bn()*other.bd()*BigInteger.valueOf(sign), bd()*other.bn()*BigInteger.valueOf(sign)) })
    }
    override fun compareTo(other: Q): Int {
        if(d==other.d) return n.compareTo(other.n)
        return try { Math.multiplyExact(n,other.d).compareTo(Math.multiplyExact(other.n,d)) }
            catch(_: ArithmeticException) { (bn()*other.bd()).compareTo(other.bn()*bd()) }
    }
    fun value() = n.toDouble()/d
    private fun bn() = BigInteger.valueOf(n)
    private fun bd() = BigInteger.valueOf(d)
    override fun equals(other: Any?) = other is Q && n == other.n && d == other.d
    override fun hashCode() = 31*n.hashCode()+d.hashCode()
    override fun toString() = n.toString() + "/" + d
    companion object {
        const val LIMIT = 1_000_000_000_000L
        val ZERO = Q(0)
        val ONE = Q(1)
    }
}

data class Meter(val numerator: Int, val denominator: Int, val beatGroups: List<Int>) {
    val duration get() = Q(4L*numerator, denominator.toLong())
    val groups get() = beatGroups.runningFold(Q.ZERO) { at, beats -> at+Q(4L*beats, denominator.toLong()) }.dropLast(1)
}
data class Measure(val id: String, val label: String, val meter: Meter, val actualDuration: Q,
    val incompleteReason: String? = null, val sourceRef: String? = null,val leadIn: Q = Q.ZERO)
data class Track(val id: String, val name: String, val openStringMidi: List<Int>,
    val capo: Int = 0, val fretCount: Int = 24, val instrument: String = "guitar") {
    fun midi(string: Int, fret: Int) = openStringMidi[string-1]+capo+fret
}
data class WrittenValue(val baseDenominator: Int, val dots: Int = 0, val tupletGroupIds: List<String> = emptyList()) {
    val straight get() = (0..dots).fold(Q.ZERO) { total, i -> total+Q(4, baseDenominator.toLong()*(1L shl i)) }
}
data class CurvePoint(val at: Q, val semitones: Double)
data class Oscillation(val depth: Double, val rateHz: Double)
data class Harmonic(val kind: String, val partial: Int, val touchFret: Int, val soundMidi: Int)
data class GraceNote(val fret: Int, val duration: Q, val kind: String = "pick")
data class Ornament(val timing: String, val notes: List<GraceNote>)
data class Repluck(val kind: String, val unit: Q, val alternateFret: Int? = null)

/** Nullable fields are note overrides. False explicitly overrides an event's true. */
data class Articulation(
    val velocity: Double? = null, val accent: Boolean? = null, val pick: String? = null,
    val palmMute: Boolean? = null, val short: Boolean? = null, val letRing: Boolean? = null,
    val ringUntil: Q? = null, val cut: Boolean? = null, val strumDelayMs: Double? = null,
    val bend: List<CurvePoint>? = null, val vibrato: Oscillation? = null,
    val tremoloBar: List<CurvePoint>? = null, val slide: String? = null,
    val tapping: Boolean? = null, val ornament: Ornament? = null, val repluck: Repluck? = null
) {
    fun over(base: Articulation) = Articulation(
        velocity ?: base.velocity, accent ?: base.accent, pick ?: base.pick,
        palmMute ?: base.palmMute, short ?: base.short, letRing ?: base.letRing,
        ringUntil ?: base.ringUntil, cut ?: base.cut, strumDelayMs ?: base.strumDelayMs,
        bend ?: base.bend, vibrato ?: base.vibrato, tremoloBar ?: base.tremoloBar,
        slide ?: base.slide, tapping ?: base.tapping, ornament ?: base.ornament, repluck ?: base.repluck)
    companion object { val DEFAULT = Articulation(1.0,false,"default",false,false,false,null,false,0.0,
        emptyList(),Oscillation(0.0,5.0),emptyList(),"none",false) }
}
data class ScoreNote(val id: String, val string: Int, val fret: Int, val midi: Int,
    val dead: Boolean = false, val harmonic: Harmonic? = null,
    val articulation: Articulation = Articulation(), val sourceRef: String? = null,
    val sharedSoundId: String? = null)
data class ScoreEvent(val id: String, val measureId: String, val onset: Q, val duration: Q,
    val written: WrittenValue, val notes: List<ScoreNote>, val articulation: Articulation = Articulation(),
    val measureRest: Boolean = false, val practicePadding: String? = null, val sourceRef: String? = null)
data class Voice(val id: String, val trackId: String, val events: List<ScoreEvent>)
data class Relation(val id: String, val kind: String, val fromNoteId: String, val toNoteId: String)
data class TupletGroup(val id: String, val voiceId: String, val eventIds: List<String>,
    val actual: Int, val normal: Int, val parentId: String? = null)
data class VisitCondition(val occurrence: Int? = null,val repeatPasses: Map<String,List<Int>> = emptyMap()) {
    fun matches(occurrence: Int,passes: Map<String,Int>) = (this.occurrence==null || this.occurrence==occurrence) &&
        repeatPasses.all { (id,allowed) -> passes[id] in allowed }
}
data class Tempo(val at: Q, val beatUnit: Q, val bpm: Double, val rampEnd: Q? = null, val endBpm: Double? = null,
    val condition: VisitCondition? = null)
data class Dynamic(val at: Q, val velocity: Double, val trackId: String? = null, val rampEnd: Q? = null, val endVelocity: Double? = null,
    val condition: VisitCondition? = null)
data class SwingSpec(val start: Q, val end: Q, val unit: Q, val ratio: List<Int>,
    val trackIds: List<String> = emptyList(), val voiceIds: List<String> = emptyList())
data class Repeat(val id: String, val start: String, val end: String, val times: Int)
data class Ending(val repeatId: String, val start: String, val end: String, val passes: List<Int>)
data class Marker(val id: String, val measureId: String, val kind: String)
data class Jump(val id: String, val at: String, val target: String, val kind: String, val occurrence: Int = 1,
    val condition: VisitCondition? = null)
data class Form(val repeats: List<Repeat> = emptyList(), val endings: List<Ending> = emptyList(),
    val markers: List<Marker> = emptyList(), val jumps: List<Jump> = emptyList(),
    val repeatsAfterJump: Boolean = false)
data class VoicingTeaching(val root: Int,val third: Int?,val soundingName: String,val group: String,val source: String,
    val intervalRoles: Map<Int,String> = emptyMap(),val toneNames: Map<Int,String> = emptyMap())
data class GuitarVoicing(val id: String, val name: String, val trackId: String, val frets: List<Int>, val fingers: List<Int>,
    val teaching: VoicingTeaching? = null, val barres: List<Barre> = emptyList(), val quality: ChordQuality? = null)
data class HarmonySymbol(val id: String, val measureId: String, val onset: Q, val name: String, val voicingId: String? = null,
    val condition: VisitCondition? = null, val validUntil: Q? = null)
data class ScoreDocument(val documentId: String, val measures: List<Measure>, val tracks: List<Track>,
    val voices: List<Voice>, val tuplets: List<TupletGroup>, val relations: List<Relation>,
    val tempos: List<Tempo>, val dynamics: List<Dynamic>, val swing: List<SwingSpec>,
    val form: Form, val voicings: List<GuitarVoicing>, val harmonies: List<HarmonySymbol>) {
    val measureStarts by lazy { measures.runningFold(Q.ZERO) { at, measure -> at+measure.actualDuration } }
    val measureIndices by lazy { measures.withIndex().associate { it.value.id to it.index } }
    val length get() = measureStarts.last()
}
data class Coordinate(val measureId: String, val offset: Q = Q.ZERO, val occurrence: Int? = null)
data class PracticeSegment(val start: Coordinate, val end: Coordinate, val formPolicy: String = "written",
    val entry: String = "reject", val exit: String = "reject", val affectedNoteIds: List<String> = emptyList())
data class PracticeTeaching(val category: String = "",val passage: String = "",val source: String = "",
    val technique: String = "",val use: String = "",val version: String? = null,val tempoLabel: String = "",
    val explanation: String = "")
/** Reviewed teaching belongs to a practice and a source note, optionally one written occurrence. */
data class NoteFingering(val sourceNoteId: String, val finger: Int?, val source: String,
    val occurrence: Int? = null)

data class PracticeSpec(val id: String, val title: String, val goal: String, val mainTrackId: String,
    val mixTrackIds: List<String>, val segments: List<PracticeSegment>,
    val suggestedTempo: Int = 60, val familyId: String? = null, val variantOf: String? = null,
    val relatedIds: List<String> = emptyList(), val visibility: String = "core", val order: Int = 0,
    val tags: List<String> = emptyList(), val padding: Q = Q.ZERO, val paddingReason: String? = null,
    val tempoMode: String = "source", val difficulty: String = "自行调整",
    val teaching: PracticeTeaching? = null,val legacyIds: List<String> = emptyList(),
    val noteFingerings: List<NoteFingering> = emptyList(), val lesson: LessonMetadata? = null)
data class PracticeKey(val packageId: String, val practiceId: String) {
    val encoded get() = packageId.length.toString()+":"+packageId+practiceId
}
data class Diagnostic(val code: String, val pointer: String, val reason: String, val sourceRef: String? = null,
    val category: String = "unsupported-blocker")
class MusicError(val diagnostic: Diagnostic) : IllegalArgumentException(
    diagnostic.code+" "+diagnostic.pointer+": "+diagnostic.reason)
fun musicCheck(ok: Boolean, code: String, pointer: String, reason: String, sourceRef: String? = null) {
    if(!ok) throw MusicError(Diagnostic(code,pointer,reason,sourceRef))
}
