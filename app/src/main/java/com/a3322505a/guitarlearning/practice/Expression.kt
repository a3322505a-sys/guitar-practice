package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position

enum class Pick { DEFAULT, DOWN, UP, NONE }
enum class Swing(val unit: Int, val title: String) { NONE(0,"直拍"), EIGHTH(6,"八分摇摆 2:1"), SIXTEENTH(3,"十六分摇摆 2:1") }
data class PitchPoint(val tick: Double, val semitones: Double)
data class Grace(val fret: Int, val writtenTicks: Double = 1.5)
data class StringExpression(val velocity: Float? = null, val palmMute: Boolean? = null, val ring: Boolean? = null,
    val accent: Boolean = false, val bend: List<PitchPoint> = emptyList(), val slide: Int = 0, val grace: Grace? = null,
    val short: Boolean = false)
data class Tuplet(val tick: Int, val unit: Int) { val end get()=tick+3*unit }
enum class Technique(val mark: String) { SLIDE("/"), HAMMER("h"), PULL("p") }
data class Legato(val sourceTick: Int, val targetTick: Int, val string: Int, val kind: Technique)
data class BarRange(val first: Int, val last: Int) {
    fun valid(seq: Sequence)=first>=0 && last>=first && last<seq.barCount
}
val Event.strings get()=(positions.map { it.string }+deadStrings).distinct()
val Event.stroke get()=if(pick==Pick.DEFAULT && strum) Pick.DOWN else pick
val Event.rest get()=strings.isEmpty()
fun Event.strength(string: Int)=(stringExpression[string]?.velocity ?: velocity)*(if(accent || stringExpression[string]?.accent==true) 1.25f else 1f)
fun Event.muted(string: Int)=stringExpression[string]?.palmMute ?: palmMute
fun Sequence.position(tick: Int,string: Int)=events.first { it.tick==tick }.positions.single { it.string==string }
fun Sequence.linkMark(link: Legato)=if(link.kind==Technique.SLIDE && position(link.sourceTick,link.string).fret>position(link.targetTick,link.string).fret) "\\" else link.kind.mark
data class MovingPosition(val source: Position,val target: Position,val fret: Float,val mark: String)
fun Sequence.movingAt(tick: Double): MovingPosition? = links.firstOrNull { tick>=it.sourceTick && tick<it.targetTick }?.let {
    val a=position(it.sourceTick,it.string);val b=position(it.targetTick,it.string)
    val fraction=((tick-it.sourceTick)/(it.targetTick-it.sourceTick)).toFloat()
    MovingPosition(a,b,if(it.kind==Technique.SLIDE) a.fret+(b.fret-a.fret)*fraction else a.fret.toFloat(),linkMark(it))
}
fun Event.expressionLabel()=buildList {
    if(accent) add(">")
    if(velocity<1f) add("弱")
    if(positions.any { muted(it.string) }) add("P.M.")
    if(short || stringExpression.values.any { it.short }) add("短促")
    if(vibrato) add("~ 揉弦")
    if(stringExpression.values.any { it.bend.isNotEmpty() }) add("推弦／保持")
    if(stringExpression.values.any { it.slide==4 }) add("滑出↘")
    if(stringExpression.values.any { it.slide==8 }) add("滑出↗")
    if(stringExpression.values.any { it.slide==16 }) add("滑入↗")
    stringExpression.values.mapNotNull { it.grace }.forEach { add("装饰${it.fret}/") }
    when(stroke) { Pick.DOWN -> add("↓"); Pick.UP -> add("↑"); Pick.NONE -> add("连奏"); else -> {} }
    if(deadStrings.isNotEmpty()) add(deadStrings.sorted().joinToString { "$it 弦 x" })
}.joinToString(" ")

/** Validate one timeline before notation, PCM or diagrams consume it. */
fun Sequence.validateExpression() {
    tuplets.forEach { group ->
        require(group.unit in listOf(2,4) && group.tick>=0 && group.end<=length && group.tick%(3*group.unit)==0) { "invalid triplet position/unit" }
        require(barAt(group.tick.toDouble())==barAt((group.end-1).toDouble())) { "triplet crosses a bar" }
        val members=events.filter { it.tick>=group.tick && it.tick<group.end }
        require(members.map { it.tick }==listOf(group.tick,group.tick+group.unit,group.tick+2*group.unit) && members.all { it.duration==group.unit }) { "triplet needs three equal members (rests are explicit)" }
    }
    require(tuplets.sortedBy { it.tick }.zipWithNext().all { (a,b)->a.end<=b.tick }) { "overlapping triplets" }
    events.forEach { e ->
        val group=tuplets.firstOrNull { e.tick>=it.tick && e.tick<it.end }
        require(if(group==null) e.tick%3==0 && e.duration%3==0 else e.duration==group.unit) { "event[${events.indexOf(e)}] unsupported TAB subdivision" }
        require(e.velocity.isFinite() && e.velocity in .1f..1.2f) { "invalid velocity" }
        require(e.deadStrings.all { it in 1..6 } && e.positions.none { it.string in e.deadStrings }) { "dead strings must be separate from pitched positions" }
        require(e.stringExpression.keys.all { it in e.strings }) { "expression for absent string" }
        require(e.stringExpression.values.all { it.velocity==null || it.velocity.isFinite() && it.velocity in .1f..1.2f }) { "invalid string velocity" }
        e.stringExpression.values.forEach { x ->
            require(x.slide in listOf(0,4,8,16)) { "unsupported slide gesture" }
            require(x.grace==null || x.grace.fret in 0..24 && x.grace.writtenTicks>0) { "invalid grace" }
            require(x.bend.all { it.tick.isFinite() && it.tick>=0 && it.tick<=e.duration && it.semitones.isFinite() && it.semitones in 0.0..4.0 } && x.bend.zipWithNext().all { (a,b)->a.tick<=b.tick }) { "invalid bend curve" }
        }
        require(e.stroke!=Pick.NONE || e.positions.isNotEmpty() && e.deadStrings.isEmpty() && e.positions.all { p->links.any { it.targetTick==e.tick && it.string==p.string } }) { "unpicked note needs a legato source" }
    }
    require(links.map { it.targetTick to it.string }.distinct().size==links.size) { "duplicate legato target" }
    require(links.map { it.sourceTick to it.string }.distinct().size==links.size) { "duplicate legato source string" }
    links.forEach { link ->
        require(link.sourceTick<link.targetTick) { "invalid legato time" }
        val source=events.firstOrNull { it.tick==link.sourceTick } ?: error("missing legato source")
        val target=events.firstOrNull { it.tick==link.targetTick } ?: error("missing legato target")
        val a=source.positions.singleOrNull { it.string==link.string } ?: error("missing source string")
        val b=target.positions.singleOrNull { it.string==link.string } ?: error("missing target string")
        require(a.fret!=b.fret && target.stroke==Pick.NONE) { "legato needs different frets and an unpicked target" }
        require(source.muted(link.string)==target.muted(link.string)) { "changing P.M. within a legato is unsupported" }
        require(!source.short && source.stroke!=Pick.NONE && (source.ring || source.tick+source.duration==target.tick)) { "unsupported nested or interrupted legato" }
        require(events.none { it.tick>source.tick && it.tick<target.tick && link.string in it.strings }) { "legato interrupted on same string" }
        require(sections.none { it.tick>source.tick && it.tick<=target.tick }) { "legato crosses a grip change" }
        require(link.kind!=Technique.HAMMER || b.fret>a.fret) { "hammer must ascend" }
        require(link.kind!=Technique.PULL || b.fret<a.fret) { "pull must descend" }
    }
}
