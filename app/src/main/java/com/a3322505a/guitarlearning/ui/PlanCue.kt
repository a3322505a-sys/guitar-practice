package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.music.score.Q
import com.a3322505a.guitarlearning.music.Music
import com.a3322505a.guitarlearning.practice.Cursor
import com.a3322505a.guitarlearning.practice.compile.PerformancePlan

internal fun beatUnitName(unit: Q) = when(unit) {
    Q.ONE -> "四分音符"
    Q(3,2) -> "附点四分音符"
    Q(1,2) -> "八分音符"
    Q(2) -> "二分音符"
    else -> "四分音符×"+unit
}
internal fun planCue(plan: PerformancePlan,cursor: Cursor?): String {
    if(cursor?.pre==true) return "预备拍"
    val notes=if(cursor!=null) cursor.activeNoteIds.mapNotNull { plan.noteById[it] }
        else plan.notes.filter { it.track.id==plan.practice.mainTrackId }.take(1)
    if(notes.isEmpty()) return "休止 · 留拍"
    val labels=notes.flatMap { n ->
        val a=n.articulation
        buildList {
            if(n.note.dead) add("死音")
            if(a.palmMute==true) add("P.M.")
            if(a.letRing==true) add("延响")
            if(a.bend.orEmpty().isNotEmpty()) add("推弦")
            if((a.vibrato?.depth ?: 0.0)>0) add("揉弦")
            if(a.tremoloBar.orEmpty().isNotEmpty()) add("摇把")
            if(a.tapping==true) add("点弦")
            n.note.harmonic?.let { h -> add("泛音 "+n.note.fret+"品基音 / "+h.touchFret+"品触点 / "+Music.name(h.soundMidi,true)) }
            if(a.ornament!=null) add("装饰音")
            if(a.repluck!=null) add(if(a.repluck.kind=="trill") "颤音" else "反复拨奏")
            plan.outgoingRelations[n.id].orEmpty().forEach { r ->
                add(when(r.kind) { "hammer" -> "击弦";"pull" -> "勾弦";"slide","slide-pick" -> "滑音";"tie" -> "延音线";"slur" -> "连线";else -> "点弦" })
            }
        }
    }.distinct()
    return (listOf(plan.mainTrack.name)+labels).joinToString(" · ")
}
