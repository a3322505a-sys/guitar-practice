package com.a3322505a.guitarlearning.practice.compile

import com.a3322505a.guitarlearning.music.score.*
import kotlin.math.*

data class PlanGlyph(val event: PlanEvent,val start: Q,val end: Q,val tieIn: Boolean,val tieOut: Boolean)
data class PlanTabBar(val index: Int,val visit: MeasureVisit,val glyphs: List<PlanGlyph>) {
    val onsets = glyphs.map { (it.start-visit.start).value() }.distinct().sorted()
    val readableWidth: Int get() {
        val gaps=onsets.zipWithNext().map { it.second-it.first }.filter { it>0 }
        val headWidth=if(glyphs.any { g -> g.event.event.notes.any { it.harmonic!=null } }) 54.0 else 28.0
        return ceil(maxOf(330.0,90+onsets.size*34.0,if(gaps.isEmpty()) 0.0 else visit.duration.value()*headWidth/gaps.min())).toInt()
    }
}
fun PerformancePlan.tabProjection(): List<PlanTabBar> {
    val index=IntervalIndex(events.filter { it.track.id==practice.mainTrackId },{ it.start.value() },{ it.end.value() })
    return visits.mapIndexed { i,v ->
        val end=v.start+v.duration
        PlanTabBar(i,v,index.query(v.start.value(),end.value()).map { e ->
            PlanGlyph(e,maxOf(e.start,v.start),minOf(e.end,end),(e.start < v.start),(e.end > end))
        }.sortedWith(compareBy<PlanGlyph> { it.start }.thenBy { it.event.voiceId }))
    }
}
