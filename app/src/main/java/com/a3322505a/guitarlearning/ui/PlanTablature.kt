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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import kotlin.math.*

@Composable
internal fun PlanTablature(plan: PerformancePlan,hint: Hint,cursor: Cursor?,following: Boolean,modifier: Modifier) {
    val bars=remember(plan) { plan.tabProjection() }
    val voices=remember(plan) { plan.score.voices.filter { it.trackId==plan.practice.mainTrackId }.map { it.id } }
    val state=rememberLazyListState()
    val measurer=rememberTextMeasurer()
    val scale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    LaunchedEffect(plan,cursor?.bar,following) {
        if(following && cursor?.pre==false) state.animateScrollToItem(cursor.bar.coerceIn(bars.indices))
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag("tablature")) {
        val viewport=maxWidth
        LazyColumn(state=state,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            itemsIndexed(bars) { _,bar ->
                val v=bar.visit;val meter=v.measure.meter
                val description="六线谱 "+v.measure.label+" · 经过"+v.occurrence+" · "+
                    meter.numerator+"/"+meter.denominator+" · "+voices.size+"声部。"+
                    bar.glyphs.joinToString("；") { g -> g.event.voiceId+" "+g.event.event.notes.joinToString { n ->
                        n.id+" "+n.string+"弦"+n.fret+"品"+if(n.dead) "死音" else "" }+
                        g.event.event.written.tupletGroupIds.joinToString { id -> plan.score.tuplets.first { it.id==id }.let { "连音 "+it.actual+":"+it.normal } } }
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Canvas(Modifier.width(maxOf(viewport,(bar.readableWidth*scale).dp)).height((365+max(0,voices.size-2)*28).dp*scale)
                        .semantics { contentDescription=description }) {
                        val u=scale*density
                        fun text(value: String,x: Float,y: Float,color: Color=Ink,font: Int=13) {
                            val result=measurer.measure(value,TextStyle(color=color,fontSize=font.sp))
                            drawText(result,topLeft=Offset(x,y))
                        }
                        val left=58*u;val right=size.width-26*u;val span=right-left
                        val stringY=FloatArray(6) { (170+it*21)*u }
                        val sourceOffset=plan.score.measureIndices[v.measure.id]?.let { v.sourceStart-plan.score.measureStarts[it] } ?: Q.ZERO
                        val beatOffset=sourceOffset+v.measure.leadIn
                        fun x(q: Q)=left+((q-v.start).value()/v.duration.value()).toFloat()*span
                        text(v.measure.label+" · 经过"+v.occurrence,12*u,4*u,PurpleInk)
                        text(meter.numerator.toString()+"/"+meter.denominator+" ["+meter.beatGroups.joinToString("+")+"]",12*u,26*u,BlueInk)
                        text(plan.mainTrack.name+" · "+if(plan.mainTrack.capo>0) "夹"+plan.mainTrack.capo+"品" else "无夹",180*u,26*u,Muted)
                        stringY.forEachIndexed { i,y ->
                            drawLine(Muted,Offset(left,y),Offset(right,y),.7f*u);text((i+1).toString(),20*u,y-8*u,Muted,12)
                        }
                        meter.groups.map { it-beatOffset }.filter { it>Q.ZERO && it<v.duration }.forEach { q ->
                            drawLine(Lavender,Offset(x(v.start+q),120*u),Offset(x(v.start+q),312*u),u)
                        }
                        voices.forEachIndexed { voiceIndex,voice ->
                            val up=voiceIndex%2==0
                            val stemY=(if(up) 140-voiceIndex*12 else 306+(voiceIndex-1)*12)*u
                            val events=bar.glyphs.filter { it.event.voiceId==voice }
                            events.forEachIndexed { i,g ->
                                val e=g.event.event;val at=x(g.start);val stop=x(g.end)
                                val flags=max(0,log2(e.written.baseDenominator.toDouble()).toInt()-2)
                                if(e.notes.isEmpty()) rest(when(e.written.baseDenominator) {
                                    1 -> RhythmValue.WHOLE;2 -> RhythmValue.HALF;4 -> RhythmValue.QUARTER;
                                    8 -> RhythmValue.EIGHTH;16 -> RhythmValue.SIXTEENTH;
                                    32 -> RhythmValue.THIRTY_SECOND;else -> RhythmValue.SIXTY_FOURTH },at,stemY,u)
                                else {
                                    val noteYs=e.notes.map { stringY[it.string-1] }
                                    val near=if(up) noteYs.min() else noteYs.max()
                                    if(e.written.baseDenominator!=1) drawLine(BlueInk,Offset(at,near),Offset(at,stemY),u)
                                    if(e.written.baseDenominator<=2) drawCircle(Color.White,3.5f*u,Offset(at,stemY))
                                    drawCircle(BlueInk,3.5f*u,Offset(at,stemY),
                                        style=if(e.written.baseDenominator<=2) Stroke(u) else androidx.compose.ui.graphics.drawscope.Fill)
                                    val next=events.getOrNull(i+1)
                                    val local=(g.start-v.start+beatOffset).value()
                                    val group=meter.groups.indexOfLast { it.value()<=local }
                                    val nextGroup=next?.let { meter.groups.indexOfLast { q -> q.value()<=(it.start-v.start+beatOffset).value() } }
                                    val beam=next!=null && next.event.event.notes.isNotEmpty() && group==nextGroup && g.end==next.start &&
                                        flags>0 && next.event.event.written.baseDenominator>=8
                                    repeat(flags) { flag ->
                                        val y=stemY+(if(up) flag*5 else -flag*5)*u
                                        drawLine(BlueInk,Offset(at,y),Offset(if(beam) x(next!!.start) else at+10*u,if(beam) y else y+(if(up) 8 else -8)*u),2*u)
                                    }
                                    e.notes.forEach { n ->
                                        val y=stringY[n.string-1]
                                        if(hint!=Hint.HIDDEN) {
                                            val label=if(n.dead) "×" else n.harmonic?.let { h ->
                                                if(h.kind=="natural") "◇"+h.touchFret else n.fret.toString()+"◇"+h.touchFret
                                            } ?: n.fret.toString()
                                            drawRect(Color.White,Offset(at-8*u,y-10*u),Size((label.length*10+6)*u,19*u))
                                            val active=cursor?.activeNoteIds?.contains(g.event.id+"/"+n.id)==true
                                            text(label,at-6*u,y-10*u,
                                                if(active) Green else Ink,15)
                                            repeat(e.written.dots) { dot -> drawCircle(Ink,1.6f*u,Offset(at+(13+dot*5)*u,y)) }
                                        }
                                        val art=n.articulation.over(e.articulation).over(Articulation.DEFAULT)
                                        val labels=buildList {
                                            if(art.pick=="down") add("↓");if(art.pick=="up") add("↑")
                                            if(art.palmMute==true) add("P.M.");if(art.letRing==true) add("let ring")
                                            if(art.short==true) add("·");if(art.accent==true) add(">")
                                            if(art.bend.orEmpty().isNotEmpty()) add("B"+art.bend!!.maxOf { it.semitones })
                                            if((art.vibrato?.depth ?: 0.0)>0) add("~")
                                            if(art.tremoloBar.orEmpty().isNotEmpty()) add("bar")
                                            if(art.slide!="none") add(art.slide!!)
                                            if(art.tapping==true) add("T");if(art.cut==true) add("cut")
                                            art.repluck?.let { add(if(it.kind=="trill") "tr" else "trem") }
                                            art.ornament?.let { add("gr "+it.notes.joinToString("/") { note -> note.fret.toString() }) }
                                        }
                                        if(labels.isNotEmpty() && hint!=Hint.HIDDEN) text(labels.joinToString(" "),at-6*u,
                                            (if(up) 149-voiceIndex*12 else 328+voiceIndex*12)*u,BlueInk,11)
                                        val noteId=g.event.id+"/"+n.id
                                        val relations=plan.outgoingRelations[noteId].orEmpty()
                                        relations.forEach { r ->
                                            val target=plan.noteById.getValue(r.to)
                                            val end=if(target.event.visit==bar.index) x(target.start) else right
                                            val path=Path().apply { moveTo(at+9*u,y-9*u);quadraticTo((at+end)/2,y-28*u,end-7*u,y-9*u) }
                                            drawPath(path,BlueInk,style=Stroke(u))
                                            text(when(r.kind) { "tie" -> "⌒";"hammer" -> "h";"pull" -> "p";"slide","slide-pick" -> "/";"tap" -> "T";else -> "slur" },
                                                (at+end)/2,y-28*u,BlueInk,10)
                                        }
                                        if(g.tieIn || g.tieOut) text("⌒",if(g.tieIn) left-10*u else stop-8*u,y-22*u,BlueInk,13)
                                    }
                                }
                                e.written.tupletGroupIds.forEachIndexed { level,id ->
                                    val t=plan.score.tuplets.first { it.id==id }
                                    val members=events.filter { it.event.event.id in t.eventIds }
                                    if(members.firstOrNull()==g) {
                                        val a=x(members.first().start);val b=x(members.last().end)
                                        val y=(if(up) 110-level*14 else 318+level*14)*u
                                        drawLine(PurpleInk,Offset(a,y),Offset(b,y),u)
                                        drawLine(PurpleInk,Offset(a,y),Offset(a,y+4*u),u)
                                        drawLine(PurpleInk,Offset(b,y),Offset(b,y+4*u),u)
                                        text(t.actual.toString()+":"+t.normal,(a+b)/2,y-13*u,PurpleInk,11)
                                    }
                                }
                                if(e.notes.isEmpty()) repeat(e.written.dots) { dot ->
                                    drawCircle(BlueInk,1.6f*u,Offset(at+(14+dot*5)*u,stemY))
                                }
                                g.event.adaptation?.let { action ->
                                    text("窗口："+when(action) { "carryIn" -> "恢复持续状态";"reattack" -> "重新起音";else -> "截音" },
                                        12*u,350*u,Muted,11)
                                }
                            }
                        }
                        if(cursor?.pre==false && cursor.bar==bar.index) {
                            val at=left+((cursor.localTick/12-v.start.value())/v.duration.value()).toFloat()*span
                            drawLine(Green,Offset(at,120*u),Offset(at,312*u),2*u)
                        }
                        drawLine(Ink,Offset(right,170*u),Offset(right,275*u),1.5f*u)
                    }
                }
            }
        }
    }
}
