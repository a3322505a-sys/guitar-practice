package com.a3322505a.guitarlearning.practice.compile

import com.a3322505a.guitarlearning.music.score.*

data class SourceVisit(val measureIndex: Int, val occurrence: Int, val repeatContext: Map<String,Int>, val afterJump: Boolean)
data class ScoreRoutes(val written: List<SourceVisit>,val performed: List<SourceVisit>)

/** Only this compiler expands form. Practice loops remain unexpanded in Session. */
object FormCompiler {
    fun routes(score: ScoreDocument)=ScoreRoutes(score.measures.indices.map { SourceVisit(it,1,emptyMap(),false) },compile(score))
    fun compile(score: ScoreDocument): List<SourceVisit> {
        val measures=score.measures.mapIndexed { i,m -> m.id to i }.toMap()
        fun index(id: String,path: String): Int {
            musicCheck(id in measures,"FORM_REF",path,"Missing measure: "+id)
            return measures.getValue(id)
        }
        data class Range(val repeat: Repeat, val first: Int, val last: Int)
        val repeats=score.form.repeats.mapIndexed { i,r ->
            Range(r,index(r.start,"/score/form/repeats/"+i+"/start"),index(r.end,"/score/form/repeats/"+i+"/end")).also {
                musicCheck(it.first<=it.last,"FORM_RANGE","/score/form/repeats/"+i,"Reversed repeat")
            }
        }
        musicCheck(repeats.map { it.repeat.id }.distinct().size==repeats.size,"FORM_ID","/score/form/repeats","Duplicate repeat ID")
        repeats.forEachIndexed { i,a -> repeats.drop(i+1).forEach { b ->
            musicCheck(a.last<b.first || b.last<a.first || a.first<=b.first && b.last<=a.last || b.first<=a.first && a.last<=b.last,
                "FORM_CROSSING","/score/form/repeats","Crossing repeats")
            musicCheck(a.first!=b.first || a.last!=b.last,"FORM_DUPLICATE","/score/form/repeats","Identical repeat ranges")
        } }
        musicCheck(score.measures.indices.all { at -> repeats.count { at in it.first..it.last }<=4 },
            "FORM_LIMIT","/score/form/repeats","Repeat nesting exceeds four")
        data class Volta(val ending: Ending,val first: Int,val last: Int)
        val endings=score.form.endings.mapIndexed { i,e ->
            val parent=repeats.firstOrNull { it.repeat.id==e.repeatId }
            musicCheck(parent!=null,"FORM_REF","/score/form/endings/"+i,"Missing repeat")
            Volta(e,index(e.start,"/score/form/endings/"+i),index(e.end,"/score/form/endings/"+i)).also {
                musicCheck(it.first<=it.last && it.first>=parent!!.first &&
                    e.passes.all { pass -> pass in 1..parent.repeat.times },"FORM_ENDING","/score/form/endings/"+i,"Invalid ending")
            }
        }
        val markers=score.form.markers.associateBy { it.id }
        musicCheck(markers.size==score.form.markers.size,"FORM_ID","/score/form/markers","Duplicate marker")
        score.form.markers.forEach { index(it.measureId,"/score/form/markers") }
        score.form.jumps.forEachIndexed { i,j ->
            index(j.at,"/score/form/jumps/"+i)
            musicCheck(j.kind=="dc" && j.target==score.measures.first().id ||
                j.target in markers && (j.kind!="ds" || markers.getValue(j.target).kind=="segno") &&
                    (j.kind!="to-coda" || markers.getValue(j.target).kind=="coda"),
                "FORM_REF","/score/form/jumps/"+i,"Missing or wrong-kind jump target")
        }
        musicCheck(score.form.jumps.map { it.id }.distinct().size==score.form.jumps.size,"FORM_ID","/score/form/jumps","Duplicate jump")
        val result=mutableListOf<SourceVisit>()
        val counts=IntArray(score.measures.size)
        val pass=mutableMapOf<String,Int>()
        val usedJumps=mutableSetOf<String>()
        var at=0; var afterJump=false; var steps=0
        while(at<score.measures.size) {
            musicCheck(++steps<=200000 && result.size<50000,"FORM_LIMIT","/score/form","Form exceeds visit/step budget")
            val activeRepeats=if(afterJump && !score.form.repeatsAfterJump) emptyList() else repeats.filter { at in it.first..it.last }
            activeRepeats.forEach { pass.putIfAbsent(it.repeat.id,1) }
            val skipped=endings.firstOrNull { at in it.first..it.last && (pass[it.ending.repeatId] ?: 1) !in it.ending.passes }
            if(skipped!=null) {
                // A repeat barline in a skipped first ending still routes the next pass.
                val endRepeat=activeRepeats.filter { it.last in at..skipped.last }.maxByOrNull { it.first }
                if(endRepeat!=null && (pass[endRepeat.repeat.id] ?: 1)<endRepeat.repeat.times) {
                    pass[endRepeat.repeat.id]=(pass[endRepeat.repeat.id] ?: 1)+1
                    repeats.filter { it.first>=endRepeat.first && it.last<=endRepeat.last && it!=endRepeat }.forEach { pass.remove(it.repeat.id) }
                    at=endRepeat.first
                } else at=skipped.last+1
                continue
            }
            counts[at]++
            val relevant=activeRepeats.map { it.repeat.id }.toSet()+endings.filter { at in it.first..it.last }.map { it.ending.repeatId }
            result+=SourceVisit(at,counts[at],pass.filterKeys { it in relevant },afterJump)
            if(afterJump && score.form.markers.any { it.measureId==score.measures[at].id && it.kind=="fine" }) break
            val jump=score.form.jumps.firstOrNull { it.at==score.measures[at].id && it.id !in usedJumps &&
                counts[at]>=it.occurrence && (it.kind!="to-coda" || afterJump) && (it.condition?.matches(counts[at],pass) ?: true) }
            if(jump!=null) {
                usedJumps+=jump.id; afterJump=true; pass.clear()
                at=if(jump.kind=="dc") 0 else index(markers.getValue(jump.target).measureId,"/score/form/jumps")
                continue
            }
            val end=activeRepeats.filter { it.last==at && (pass[it.repeat.id] ?: 1)<it.repeat.times }.maxByOrNull { it.first }
            if(end!=null) {
                pass[end.repeat.id]=(pass[end.repeat.id] ?: 1)+1
                repeats.filter { it.first>=end.first && it.last<=end.last && it!=end }.forEach { pass.remove(it.repeat.id) }
                at=end.first
            } else at++
        }
        return result
    }
}
