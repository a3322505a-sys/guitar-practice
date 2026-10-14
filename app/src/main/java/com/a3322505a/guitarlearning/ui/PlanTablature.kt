package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.a3322505a.guitarlearning.music.Tonality
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*

@Composable
internal fun PlanTablature(plan: PerformancePlan,hint: Hint,cursor: Cursor?,following: Boolean,modifier: Modifier,
    ticksPerQuarter: Int = QUARTER,key: Tonality? = null) {
    val bars=remember(plan) { plan.tabProjection() }
    val list=rememberLazyListState()
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val scale=density.fontScale.coerceAtLeast(1f)
    val u=density.density*scale
    val measurements=remember(plan,density) { mutableMapOf<Pair<String,Int>,TextLayoutResult>() }
    val incoming=remember(plan) { plan.relations.groupBy { it.to } }
    val voices=remember(plan) { plan.score.voices.filter { it.trackId==plan.practice.mainTrackId }.map { it.id } }
    fun measure(value: String,size: Int)=measurements.getOrPut(value to size) {
        measurer.measure(value,TextStyle(fontSize=size.sp,fontWeight=FontWeight.Medium))
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag("tablature")) {
        val rows=remember(plan,bars,maxWidth,density) { tabLayoutSystems(plan,bars,maxWidth.value/scale) { text,size -> measure(text,size).size.width/u } }
        val target=rows.indexOfFirst { row -> row.bars.any { it.bar.index==cursor?.bar } }
        LaunchedEffect(plan,target,following,cursor?.loop) {
            if(following && target>=0) list.animateScrollToItem(target)
        }
        LazyColumn(state=list,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            itemsIndexed(rows) { _,layout ->
                val playback=layout.playback(cursor,ticksPerQuarter)
                val scroll=rememberScrollState()
                // The playhead X also comes from layout; dense bars follow horizontally.
                LaunchedEffect(plan,playback.barIndex,playback.eventIds,following,cursor?.pre,cursor?.loop) {
                    if(following && playback.barIndex!=null) {
                        val x=layout.bars.flatMap { it.events }.firstOrNull { it.glyph.event.id in playback.eventIds }?.x
                            ?: playback.playheadX ?: 0f
                        val viewport=maxWidth.value/scale
                        if(x*u<scroll.value+24*u || x*u>scroll.value+(viewport-36)*u)
                            scroll.animateScrollTo(((x-viewport*.3f)*u).toInt().coerceIn(0,scroll.maxValue))
                    } else if(following && cursor?.pre==true) scroll.scrollTo(0)
                }
                val description=remember(plan,layout,hint,key) { tabDescription(plan,layout,hint,key) }
                Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                    Canvas(Modifier.width((layout.width*scale).dp).height((layout.height*scale).dp).semantics {
                        contentDescription=description
                        stateDescription=playback.barIndex?.let { "当前小节 ${it+1}" } ?: "未播放"
                    }) {
                        tabHighlight(layout,playback,hint,u)
                        fun text(value: String,x: Float,y: Float,color: Color=Ink,size: Int=11,center: Boolean=false) {
                            val m=measure(value,size)
                            drawText(m,color,Offset(x*u-if(center) m.size.width/2f else 0f,y*u-m.size.height/2f))
                        }
                        layout.bars.forEach { b ->
                            val v=b.bar.visit;val meter=v.measure.meter
                            text(v.measure.label+if(v.occurrence>1) " · 经过${v.occurrence}" else "",b.left-28,11f,if(playback.barIndex==b.bar.index) Green else Muted)
                            val meterText="${meter.numerator}/${meter.denominator}"+if(meter.beatGroups.any { it!=1 }) " [${meter.beatGroups.joinToString("+")}]" else ""
                            text(meterText,b.right+8-measure(meterText,11).size.width/u,11f,Muted)
                            if(b.bar.index==0 && plan.mainTrack.capo>0) text("夹${plan.mainTrack.capo}品",b.left+40,11f,Muted)
                            for(string in 1..6) {
                                val y=b.staffTop+(string-1)*18
                                drawLine(Color(0xFFA0B4A9),Offset((b.left-10)*u,y*u),Offset((b.right+10)*u,y*u),.7f*u)
                                if(b==layout.bars.first()) text("$string",b.left-22,y,Muted,10)
                            }
                            drawLine(Ink,Offset((b.left-10)*u,b.staffTop*u),Offset((b.left-10)*u,b.staffBottom*u),u)
                            drawLine(Ink,Offset((b.right+10)*u,b.staffTop*u),Offset((b.right+10)*u,b.staffBottom*u),u)
                            if(hint!=Hint.HIDDEN) b.marks.forEach { mark ->
                                val y=26f+mark.lane*18
                                text(mark.label,mark.x,y,if(mark.voice=="harmony") PurpleInk else Muted)
                                if(mark.ranged) {
                                    val start=mark.x+measure(mark.label,11).size.width/u+4
                                    if(mark.endX>start) drawLine(Muted,Offset(start*u,y*u),Offset(mark.endX*u,y*u),u,pathEffect=PathEffect.dashPathEffect(floatArrayOf(3*u,3*u)))
                                    drawLine(Muted,Offset(mark.endX*u,y*u),Offset(mark.endX*u,(y+4)*u),u)
                                }
                            }
                            val voiceEvents=b.events.groupBy { it.glyph.event.voiceId }
                            voiceEvents.forEach { (voice,events) ->
                                if(voices.size>1) text("V${voices.indexOf(voice)+1}",b.left-22,events.first().beamY,Muted,9)
                                events.forEachIndexed { i,e ->
                                    val written=e.glyph.written
                                    val active=e.glyph.event.id in playback.eventIds
                                    val ink=if(active) RoseInk else Ink
                                    if(hint!=Hint.HIDDEN) {
                                        if(e.notes.isEmpty()) {
                                            rest(rhythmValue(written),e.x*u,(e.beamY-9)*u,u)
                                            repeat(written.dots) { dot -> drawCircle(ink,1.5f*u,Offset((e.x+13+dot*5)*u,(e.beamY-9)*u)) }
                                        } else {
                                            // Digits are the noteheads; no duplicate blue notehead row.
                                            val lowest=e.notes.maxOf { it.y }
                                            val strum=e.notes.map { it.note.articulation.over(e.glyph.event.event.articulation).over(Articulation.DEFAULT) }
                                            if(strum.any { (it.strumDelayMs ?: 0.0)>0 } && e.notes.size>1) {
                                                val down=strum.first().pick!="up"
                                                val a=if(down) lowest+4 else e.notes.minOf { it.y }-4
                                                val end=if(down) e.notes.minOf { it.y }-4 else lowest+4
                                                val ax=e.x-17;val d=if(down) 1 else -1
                                                drawLine(PurpleInk,Offset(ax*u,a*u),Offset(ax*u,end*u),1.3f*u)
                                                drawLine(PurpleInk,Offset(ax*u,end*u),Offset((ax-3)*u,(end+d*5)*u),1.3f*u)
                                                drawLine(PurpleInk,Offset(ax*u,end*u),Offset((ax+3)*u,(end+d*5)*u),1.3f*u)
                                            }
                                            if(written.baseDenominator!=1) {
                                                drawLine(ink,Offset(e.x*u,(lowest+8)*u),Offset(e.x*u,e.beamY*u),u)
                                                if(written.baseDenominator==2) drawLine(ink,Offset((e.x+3)*u,(lowest+8)*u),Offset((e.x+3)*u,e.beamY*u),u)
                                            }
                                            fun beams(a: TabEventPosition?,b: TabEventPosition?): Int {
                                                if(a==null || b==null || a.notes.isEmpty() || b.notes.isEmpty() || a.glyph.end!=b.glyph.start) return 0
                                                val offset=v.sourceStart-(plan.score.measureIndices[v.measure.id]?.let { plan.score.measureStarts[it] } ?: Q.ZERO)+v.measure.leadIn
                                                fun group(q: Q)=meter.groups.indexOfLast { it<=q-v.start+offset }
                                                return if(group(a.glyph.start)==group(b.glyph.start)) minOf(a.flags,b.flags) else 0
                                            }
                                            val before=beams(events.getOrNull(i-1),e);val after=beams(e,events.getOrNull(i+1))
                                            repeat(e.flags) { level ->
                                                val y=e.beamY-level*5
                                                if(after>level) drawLine(ink,Offset(e.x*u,y*u),Offset(events[i+1].x*u,y*u),2.5f*u)
                                                else if(before<=level) drawLine(ink,Offset(e.x*u,y*u),Offset((e.x+8*(if(before>0) -1 else 1))*u,(y-6)*u),2*u)
                                            }
                                        }
                                        written.tupletGroupIds.forEachIndexed { level,id ->
                                            val t=plan.score.tuplets.first { it.id==id };val members=events.filter { it.glyph.event.event.id in t.eventIds }
                                            if(members.firstOrNull()==e) {
                                                val a=members.first().x;val end=members.last().endX;val y=e.beamY+14+level*16
                                                drawLine(PurpleInk,Offset(a*u,y*u),Offset(end*u,y*u),u)
                                                drawLine(PurpleInk,Offset(a*u,y*u),Offset(a*u,(y-4)*u),u)
                                                drawLine(PurpleInk,Offset(end*u,y*u),Offset(end*u,(y-4)*u),u)
                                                text(if(t.actual==3 && t.normal==2) "3" else "${t.actual}:${t.normal}",(a+end)/2,y+7,PurpleInk,10,true)
                                            }
                                        }
                                    }
                                }
                            }
                            // Relations go behind the digit masks, including incoming barline continuations.
                            if(hint!=Hint.HIDDEN) b.events.forEach { e -> e.notes.forEach { n ->
                                fun arc(a: Float,end: Float,mark: String) {
                                    if(end<=a) return
                                    val y=n.y+8
                                    val path=Path().apply { moveTo(a*u,y*u);quadraticTo((a+end)/2*u,(y+8)*u,end*u,y*u) }
                                    drawPath(path,Ink,style=Stroke(u));if(mark.isNotEmpty()) text(mark,(a+end)/2,y+7,PurpleInk,9,true)
                                }
                                plan.outgoingRelations[n.id].orEmpty().forEach { r ->
                                    val target=layout.notes[r.to]
                                    arc(n.x+8,target?.x?.minus(8) ?: b.right,relationLabel(r.kind,n.note.fret,plan.noteById.getValue(r.to).note.fret))
                                }
                                incoming[n.id].orEmpty().filter { layout.notes[it.from]==null }.forEach { arc(b.left-9,n.x-8,relationLabel(it.kind,plan.noteById.getValue(it.from).note.fret,n.note.fret)) }
                                if(e.glyph.tieOut) arc(n.x+8,b.right,"")
                                if(e.glyph.tieIn) arc(b.left-9,n.x-8,"")
                            } }
                            // Last layer: measured opaque masks protect every fret digit from lines/stems.
                            if(hint!=Hint.HIDDEN) b.events.forEach { e -> e.notes.filter { tabNoteVisible(plan,e.glyph.event,it.note,hint,key) }.forEach { n ->
                                val active=e.glyph.event.id in playback.eventIds
                                val sounding=n.id in playback.soundingNoteIds
                                val m=measure(n.label,16);val w=m.size.width+10*u;val h=m.size.height+2*u
                                val background=if(active) Pink else if(playback.barIndex==b.bar.index) Mint else Color.White
                                drawRoundRect(background,Offset(n.x*u-w/2,n.y*u-h/2),Size(w,h),CornerRadius(3*u))
                                drawText(m,if(active || sounding) RoseInk else Ink,Offset(n.x*u-m.size.width/2,n.y*u-m.size.height/2))
                                repeat(e.glyph.written.dots) { dot -> drawCircle(Ink,1.5f*u,Offset(n.x*u+w/2+(3+dot*4)*u,n.y*u)) }
                            } }
                        }
                    }
                }
            }
        }
    }
}

private fun rhythmValue(w: WrittenValue)=when(w.baseDenominator) {
    1 -> RhythmValue.WHOLE;2 -> RhythmValue.HALF;4 -> RhythmValue.QUARTER;8 -> RhythmValue.EIGHTH
    16 -> RhythmValue.SIXTEENTH;32 -> RhythmValue.THIRTY_SECOND;else -> RhythmValue.SIXTY_FOURTH
}
private fun relationLabel(kind: String,from: Int=0,to: Int=0)=when(kind) { "tie" -> "";"hammer" -> "h";"pull" -> "p";"slide","slide-pick" -> if(from>to) "\\" else "/";"tap" -> "T";else -> "slur" }
private fun tabDescription(plan: PerformancePlan,layout: TabSystemLayout,hint: Hint,key: Tonality?): String = buildString {
    fun label(b: TabBarPosition)=b.bar.visit.measure.label.substringBefore(" ·")
    append("六线谱，第 ${label(layout.bars.first())} 至 ${label(layout.bars.last())} 小节。上方 1 弦，下方 6 弦。")
    append("${plan.score.voices.count { it.trackId==plan.practice.mainTrackId }}声部。")
    if(hint!=Hint.HIDDEN) layout.bars.forEach { b ->
        b.events.forEach { e ->
            if(e.notes.isEmpty()) append("休止；")
            val articulations=e.notes.map { it.note.articulation.over(e.glyph.event.event.articulation).over(Articulation.DEFAULT) }
            if(e.notes.size>1 && articulations.any { (it.strumDelayMs ?: 0.0)>0 }) {
                append(if(articulations.first().pick=="up") "上拨，从有效高音弦到低音弦；" else "下拨，从有效低音弦到高音弦；")
            }
            e.notes.filter { tabNoteVisible(plan,e.glyph.event,it.note,hint,key) }.forEach { n -> append("${n.note.string} 弦 ${n.label}${if(n.note.dead) " 死音" else " 品"}；") }
            e.glyph.event.event.written.tupletGroupIds.forEach { id ->
                val t=plan.score.tuplets.first { it.id==id };append(if(t.actual==3 && t.normal==2) "三连音 3；" else "连音 ${t.actual}:${t.normal}；")
            }
            e.notes.forEach { n -> plan.outgoingRelations[n.id].orEmpty().forEach { r ->
                val target=plan.noteById.getValue(r.to).note.fret
                append("${n.note.fret}${relationLabel(r.kind,n.note.fret,target)}$target；")
            } }
        }
        b.marks.forEach { append("${it.label}；") }
    }
}
