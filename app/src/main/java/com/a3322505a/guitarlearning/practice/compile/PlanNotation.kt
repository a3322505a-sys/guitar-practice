package com.a3322505a.guitarlearning.practice.compile

import com.a3322505a.guitarlearning.music.score.*

data class PlanGlyph(val event: PlanEvent,val start: Q,val end: Q,val tieIn: Boolean,val tieOut: Boolean,
    val written: WrittenValue = event.event.written)
data class PlanTabBar(val index: Int,val visit: MeasureVisit,val glyphs: List<PlanGlyph>)
fun PerformancePlan.tabProjection(): List<PlanTabBar> {
    val index=IntervalIndex(events.filter { it.track.id==practice.mainTrackId },{ it.start.value() },{ it.end.value() })
    return visits.mapIndexed { i,v ->
        val end=v.start+v.duration
        PlanTabBar(i,v,index.query(v.start.value(),end.value()).map { e ->
            val a=maxOf(e.start,v.start);val b=minOf(e.end,end)
            val original=e.event.written
            val factor=original.tupletGroupIds.fold(Q.ONE) { q,id ->
                val t=score.tuplets.first { it.id==id };q*Q(t.actual.toLong(),t.normal.toLong())
            }
            val fragment=(b-a)*factor
            val written=if(a==e.start && b==e.end) original else
                listOf(1,2,4,8,16,32,64).flatMap { base -> (0..3).map { dots -> WrittenValue(base,dots,original.tupletGroupIds) } }
                    .firstOrNull { it.straight==fragment } ?: original
            PlanGlyph(e,a,b,(e.start < v.start),(e.end > end),written)
        }.sortedWith(compareBy<PlanGlyph> { it.start }.thenBy { it.event.voiceId }))
    }
}
