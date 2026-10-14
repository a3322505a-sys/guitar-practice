package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.Cursor
import com.a3322505a.guitarlearning.practice.compile.*
import kotlin.math.*

/** Coordinates are layout units, scaled once by the view. Playback never computes an X. */
internal data class TabNotePosition(val id: String, val note: ScoreNote, val x: Float, val y: Float, val label: String)
internal data class TabEventPosition(val glyph: PlanGlyph, val x: Float, val endX: Float,
    val beamY: Float, val notes: List<TabNotePosition>, val flags: Int)
internal data class TabMark(val label: String, val start: Q, val end: Q, val ranged: Boolean,
    val voice: String, val lane: Int = 0, val x: Float = 0f, val endX: Float = 0f)
internal data class TabBarPosition(val bar: PlanTabBar, val left: Float, val right: Float,
    val staffTop: Float, val events: List<TabEventPosition>, val marks: List<TabMark>) {
    val staffBottom get() = staffTop+90f
    fun x(quarter: Double) = left+((quarter-bar.visit.start.value())/bar.visit.duration.value()).toFloat()*(right-left)
}
internal data class TabSystemLayout(val bars: List<TabBarPosition>, val width: Float, val height: Float) {
    val notes by lazy { bars.flatMap { it.events }.flatMap { it.notes }.associateBy { it.id } }
}
internal data class TabPlayback(val barIndex: Int? = null, val eventIds: Set<String> = emptySet(),
    val soundingNoteIds: Set<String> = emptySet(), val playheadX: Float? = null)

/** Uses the existing Session cursor's written quarter position and stable plan IDs. */
internal fun TabSystemLayout.playback(cursor: Cursor?, ticksPerQuarter: Int): TabPlayback {
    if(cursor==null || cursor.pre) return TabPlayback()
    val bar=bars.firstOrNull { it.bar.index==cursor.bar && (cursor.visitId==null || it.bar.visit.id==cursor.visitId) }
        ?: return TabPlayback()
    val q=cursor.localTick/ticksPerQuarter
    if(q<bar.bar.visit.start.value() || q>= (bar.bar.visit.start+bar.bar.visit.duration).value()) return TabPlayback()
    return TabPlayback(bar.bar.index,bar.events.filter {
        q>=it.glyph.start.value() && q<it.glyph.end.value() && q>=it.glyph.event.activation.value()
    }.map { it.glyph.event.id }.toSet(),cursor.activeNoteIds.intersect(notes.keys),bar.x(q))
}

internal fun tabNoteLabel(n: ScoreNote) = if(n.dead) "x" else n.harmonic?.let {
    if(it.kind=="natural") "◇${it.touchFret}" else "${n.fret}◇${it.touchFret}"
} ?: n.fret.toString()

internal fun tabNoteVisible(plan: PerformancePlan, event: PlanEvent, note: ScoreNote, hint: Hint, key: Tonality?): Boolean {
    if(hint==Hint.HIDDEN) return false
    if(hint==Hint.FULL) return true
    if(note.dead) return false
    val harmony=plan.harmonies.lastOrNull { it.start<=event.start }
    val root=key?.root ?: harmony?.voicing?.teaching?.root
        ?: Chords.all.firstOrNull { it.name==harmony?.symbol?.name }?.root
    return root!=null && note.midi%12==root
}

/** Merge only adjacent instructions with identical scope; rests and exceptions break the range. */
internal fun tabMarks(plan: PerformancePlan, bar: PlanTabBar): List<TabMark> {
    val result=mutableListOf<TabMark>()
    bar.glyphs.groupBy { it.event.voiceId }.forEach { (voice,glyphs) ->
        val last=mutableMapOf<String,Int>()
        glyphs.forEach { g ->
            val notes=g.event.event.notes
            val arts=notes.map { it.articulation.over(g.event.event.articulation).over(Articulation.DEFAULT) }
            fun scoped(label: String, indices: List<Int>): String = label+if(indices.size==notes.size) "" else
                " (${indices.map { notes[it].string }.sorted().joinToString(",")}弦)"
            listOf("let ring" to { a: Articulation -> a.letRing==true },"P.M." to { a: Articulation -> a.palmMute==true }).forEach { (label,test) ->
                val indices=arts.indices.filter { test(arts[it]) }
                if(indices.isNotEmpty()) {
                    val text=scoped(label,indices);val previous=last[text]?.let { result[it] }
                    if(previous!=null && previous.end==g.start) result[last.getValue(text)]=previous.copy(end=g.end)
                    else { last[text]=result.size;result+=TabMark(text,g.start,g.end,true,voice) }
                } else last.keys.filter { it.startsWith(label) }.toList().forEach(last::remove)
            }
            val labels=linkedMapOf<String,MutableList<Int>>()
            if(!g.tieIn || g.event.adaptation=="reattack") arts.forEachIndexed { i,a ->
                val marks=buildList {
                    if(a.pick=="down") add("↓");if(a.pick=="up") add("↑")
                    if(a.short==true) add("·");if(a.accent==true) add(">")
                    if(a.bend.orEmpty().isNotEmpty()) add("B${a.bend!!.maxOf { it.semitones }}")
                    if((a.vibrato?.depth ?: 0.0)>0) add("~")
                    if(a.tremoloBar.orEmpty().isNotEmpty()) add("bar")
                    if(a.slide!="none") add(a.slide!!)
                    if(a.tapping==true) add("T");if(a.cut==true) add("cut")
                    a.repluck?.let { add(if(it.kind=="trill") "tr" else "trem") }
                    a.ornament?.let { add("gr "+it.notes.joinToString("/") { n -> n.fret.toString() }) }
                }
                marks.forEach { labels.getOrPut(it) { mutableListOf() }.add(i) }
            }
            val text=(labels.entries.map { scoped(it.key,it.value) }+
                if(!g.tieIn || g.event.adaptation=="reattack") g.event.notationMarks else emptyList()).distinct().joinToString(" ")
            if(text.isNotEmpty()) result+=TabMark(text,g.start,g.start,false,voice)
            g.event.adaptation?.let { result+=TabMark("窗口："+when(it) { "carryIn" -> "恢复持续状态";"reattack" -> "重新起音";else -> "截音" },g.start,g.start,false,voice) }
        }
    }
    val end=bar.visit.start+bar.visit.duration
    plan.swing.filter { it.start<end && it.end>bar.visit.start }.forEach {
        result+=TabMark("摇摆 ${it.ratio.joinToString(":")}",maxOf(it.start,bar.visit.start),minOf(it.end,end),true,"rhythm")
    }
    plan.harmonies.filter { it.start>=bar.visit.start && it.start<end }.forEach {
        result+=TabMark(it.symbol.name,it.start,it.start,false,"harmony")
    }
    // Carry a harmony over the barline without manufacturing a new event.
    if(result.none { it.voice=="harmony" && it.start==bar.visit.start }) plan.harmonies.lastOrNull { it.start<bar.visit.start }?.let {
        result+=TabMark(it.symbol.name,bar.visit.start,bar.visit.start,false,"harmony")
    }
    val voices=plan.score.voices.filter { it.trackId==plan.practice.mainTrackId }.map { it.id }
    return result.map { if(voices.size>1 && it.voice in voices) it.copy(label="V${voices.indexOf(it.voice)+1} ${it.label}") else it }
        .sortedWith(compareBy<TabMark> { it.start }.thenBy { it.voice }.thenBy { it.label })
}

internal fun tabMinimumWidth(bar: PlanTabBar, textWidth: (String,Int)->Float): Float {
    val columns=bar.glyphs.groupBy { it.start }
    val heads=columns.mapValues { (_,gs) -> maxOf(22f,gs.flatMap { it.event.event.notes }.maxOfOrNull { textWidth(tabNoteLabel(it),16)+12 } ?: 22f) }
    val onsets=(columns.keys+listOf(bar.visit.start+bar.visit.duration)).distinct().sorted()
    val needed=onsets.zipWithNext().maxOfOrNull { (a,b) ->
        bar.visit.duration.value().toFloat()/(b-a).value().toFloat()*((heads[a] ?: 22f)/2+(heads[b] ?: 22f)/2+10f)
    } ?: 112f
    return maxOf(184f,tabLeftPadding(bar,textWidth)+30+needed)
}

private fun tabLeftPadding(bar: PlanTabBar,textWidth: (String,Int)->Float) = maxOf(34f,
    (bar.glyphs.firstOrNull()?.event?.event?.notes?.maxOfOrNull { textWidth(tabNoteLabel(it),16)/2+27 } ?: 34f))

internal fun tabLayoutSystems(plan: PerformancePlan, projection: List<PlanTabBar>, viewport: Float,
    textWidth: (String,Int)->Float): List<TabSystemLayout> {
    val rows=mutableListOf<MutableList<PlanTabBar>>()
    val widths=projection.associate { bar -> bar.index to maxOf(tabMinimumWidth(bar,textWidth),
        (tabMarks(plan,bar).maxOfOrNull { textWidth(it.label,11)+44 } ?: 0f)) }
    var used=0f
    projection.forEach { bar ->
        if(rows.isEmpty() || used+widths.getValue(bar.index)>viewport && rows.last().isNotEmpty()) {
            rows+=mutableListOf<PlanTabBar>();used=0f
        }
        rows.last()+=bar;used+=widths.getValue(bar.index)
    }
    val voices=plan.score.voices.filter { it.trackId==plan.practice.mainTrackId }.map { it.id }
    val depth=plan.score.tuplets.maxOfOrNull { t ->
        var level=1;var parent=t.parentId
        while(parent!=null) { level++;parent=plan.score.tuplets.first { it.id==parent }.parentId }
        level
    } ?: 0
    return rows.map { row ->
        val minimum=row.sumOf { widths.getValue(it.index).toDouble() }.toFloat()
        val width=maxOf(viewport,minimum)
        var offset=0f
        val prelim=row.map { bar ->
            val w=width*widths.getValue(bar.index)/minimum
            val left=offset+tabLeftPadding(bar,textWidth);val right=offset+w-20
            fun x(q: Q)=left+((q-bar.visit.start).value()/bar.visit.duration.value()).toFloat()*(right-left)
            val laneEnds=mutableListOf<Float>()
            val marks=tabMarks(plan,bar).map { mark ->
                val labelWidth=textWidth(mark.label,11)
                val at=x(mark.start).coerceIn(offset+6, maxOf(offset+6,offset+w-labelWidth-6))
                val end=maxOf(at+labelWidth,if(mark.ranged) x(mark.end)-5 else at+labelWidth)
                val lane=laneEnds.indexOfFirst { it+8<=at }.takeIf { it>=0 } ?: laneEnds.size.also { laneEnds+=0f }
                laneEnds[lane]=end
                mark.copy(lane=lane,x=at,endX=end)
            }
            offset+=w
            TabBarPosition(bar,left,right,36f,emptyList(),marks)
        }
        val staffTop=30f+18*(prelim.flatMap { it.marks }.maxOfOrNull { it.lane+1 } ?: 0)
        val voiceHeight=32f+max(0,depth-1)*16
        val bars=prelim.map { b -> b.copy(staffTop=staffTop,events=b.bar.glyphs.map { g ->
            val at=b.x(g.start.value());val voice=voices.indexOf(g.event.voiceId).coerceAtLeast(0)
            TabEventPosition(g,at,b.x(g.end.value()),staffTop+90+22+voice*voiceHeight,
                g.event.event.notes.map { n -> TabNotePosition(g.event.id+"/"+n.id,n,at,staffTop+(n.string-1)*18,tabNoteLabel(n)) },
                max(0,log2(g.written.baseDenominator.toDouble()).toInt()-2))
        }) }
        TabSystemLayout(bars,width,staffTop+90+22+max(0,voices.size-1)*voiceHeight+14+depth*16)
    }
}
