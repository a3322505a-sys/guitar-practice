package com.a3322505a.guitarlearning.assets.song

import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.compile.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SongLimits {
    const val INPUT = 16*1024*1024
    const val JSON = 32*1024*1024
    const val TOTAL = 64*1024*1024
    val FILES = setOf("manifest.json","score.json","practices.json","provenance.json","review.json")
}
object SongCapabilities {
    val supported = setOf("score.rational.v1","guitar.six-string.v1","meter.compound.v1","meter.irregular.v1",
        "guitar.tuning-capo.v1","score.polyphony.v1","rhythm.tuplet.v1","rhythm.nested-tuplet.v1",
        "rhythm.swing.v1","tempo.map.v1","form.repeat.v1","form.volta.v1","form.jump.v1","form.condition.v1",
        "technique.harmonic.v1","technique.tie.v1","technique.legato.v1","technique.bend.v1",
        "technique.vibrato.v1","technique.tremolo-bar.v1","technique.tapping.v1","technique.mute.v1",
        "technique.sustain.v1","technique.strum.v1","technique.grace.v1","technique.repluck.v1","technique.dynamics.v1",
        "catalogue.teaching.v1","teaching.note-fingering.v1","teaching.lesson.v1")
}
fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

class SongReader(private val schema: SongSchema) {
    fun readInput(input: InputStream,cancel: ()->Unit = {}): ByteArray = input.use { stream ->
        val result=ByteArrayOutputStream(); val buffer=ByteArray(8192)
        while(true) { cancel();val n=stream.read(buffer);if(n<0) break
            musicCheck(result.size()+n<=SongLimits.INPUT,"LIMIT","/container","Compressed input exceeds 16 MiB")
            result.write(buffer,0,n)
        };result.toByteArray()
    }
    fun read(bytes: ByteArray,builtin: Boolean = false,cancel: ()->Unit = {}): SongPackage {
        musicCheck(bytes.size<=SongLimits.INPUT,"LIMIT","/container","Compressed input exceeds 16 MiB")
        centralDirectory(bytes)
        val files=linkedMapOf<String,ByteArray>();var total=0
        ZipInputStream(ByteArrayInputStream(bytes),Charsets.UTF_8).use { archive ->
            while(true) {
                cancel();val entry=archive.nextEntry ?: break
                musicCheck(entry.name in SongLimits.FILES && !files.containsKey(entry.name) && !entry.isDirectory,
                    "ZIP_ENTRY","/container/"+entry.name,"Unexpected/duplicate/path entry")
                val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {
                    cancel();val n=archive.read(buffer);if(n<0) break
                    total+=n
                    musicCheck(out.size()+n<=SongLimits.JSON && total<=SongLimits.TOTAL,"LIMIT","/container/"+entry.name,"Inflated byte limit")
                    out.write(buffer,0,n)
                }
                files[entry.name]=out.toByteArray()
            }
        }
        musicCheck(files.keys==SongLimits.FILES,"ZIP_FILES","/container","Exactly five JSON files required")
        val declaredInput=StrictJson.parse(files.getValue("manifest.json")).objectValue().strings("requires")
        musicCheck(declaredInput.all { it in SongCapabilities.supported },"CAPABILITY","/manifest/requires","App upgrade required")
        val roots=files.mapValues { (name,data) -> cancel();StrictJson.parse(data).also { schema.validate(name.removeSuffix(".json"),it) }.objectValue() }
        val manifest=roots.getValue("manifest.json")
        val packageId=manifest.s("packageId")
        val nativeTeaching=packageId=="local.teaching-native" || packageId.startsWith("local.demo-")
        musicCheck(builtin || !packageId.startsWith("builtin."),"NAMESPACE","/manifest/packageId","Reserved built-in namespace")
        manifest["files"].objectValue().forEach { (name,entry) ->
            val expected=entry.objectValue()
            musicCheck(expected.i("bytes")==files.getValue(name).size && expected.s("sha256")==sha256(files.getValue(name)),
                "HASH","/manifest/files/"+name,"Raw bytes/hash mismatch")
        }
        val review=roots.getValue("review.json")
        review["hashes"].objectValue().forEach { (name,expected) ->
            musicCheck(expected==sha256(files.getValue(name)),"REVIEW_HASH","/review/hashes/"+name,"Review targets different bytes")
        }
        val score=SongCodec.score(roots.getValue("score.json"));val practices=SongCodec.practices(roots.getValue("practices.json"))
        if(nativeTeaching) {
            roots.getValue("score.json").objects("voicings").forEach { v ->
                musicCheck(v.keys.containsAll(listOf("fingers","barres","quality","teaching")),"TEACHING","/score/voicings","Native voicing must be fully authored")
            }
            musicCheck(score.voicings.all { v -> v.frets.indices.all { i ->
                if(v.frets[i]>0) v.fingers[i] in 1..4 else v.fingers[i]==0
            } } && score.harmonies.all { it.validUntil!=null && it.voicingId!=null },"TEACHING","/score","Native fingers and harmony intervals are required")
        }
        val derived=ScoreValidation.validate(score,practices)
        val declared=manifest.strings("requires").toSet()
        musicCheck(declared==derived,"CAPABILITY_DECLARATION","/manifest/requires","Expected capabilities: "+derived.joinToString())
        musicCheck(derived.all { it in SongCapabilities.supported },"CAPABILITY","/manifest/requires","App upgrade required")
        cancel()
        val routes=FormCompiler.routes(score)
        var plannedNotes=0;var plannedVisits=0;var onsets=0
        practices.forEach {
            cancel()
            PracticeCompiler.compile(score,it,routes=routes).also { plan ->
                if(nativeTeaching) {
                    val lesson=it.lesson
                    musicCheck(lesson?.key!=null && lesson.actions.isNotEmpty() && it.teaching?.version?.isNotBlank()==true &&
                        plan.notes.all { n -> n.note.fret==0 || n.finger in 1..4 } &&
                        lesson.actions.all { action -> plan.events.any { e -> e.event.id==action.eventId && e.track.id==it.mainTrackId } },
                        "TEACHING","/practices/"+it.id,"Native lessons require complete fingering and main-track anticipatory actions")
                }
                plannedNotes+=plan.notes.size
                plannedVisits+=plan.visits.size
                musicCheck(plannedNotes<=1_000_000 && plannedVisits<=50000,"PLAN_LIMIT","/practices","Aggregate planned note/visit budget")
                val linked=plan.relations.filter { r -> r.kind !in listOf("slur","slide-pick") }.map { r -> r.to }.toSet()
                onsets+=plan.notes.filter { n -> n.id !in linked }.distinctBy { n ->
                    n.note.sharedSoundId?.let { id -> n.event.start.toString()+"/"+id } ?: n.id
                }.sumOf { n -> n.articulation.repluck?.let { r -> (n.event.duration/r.unit).n.toInt() } ?:
                    (1+(n.articulation.ornament?.notes?.count { g -> g.kind=="pick" } ?: 0)) }
            }
        }
        val warnings=mutableListOf<String>()
        val assumptions=review.strings("assumptions")
        musicCheck(review.s("method").isNotBlank() && review.s("reviewer").isNotBlank() && !review.s("method").contains("TODO"),
            "REVIEW_NEEDED","/review/method","Independent review not completed")
        val ids=practices.map { it.id }.toSet()
        review.objects("diagnostics").forEachIndexed { i,d ->
            val p="/review/diagnostics/"+i
            musicCheck(d.strings("practiceIds").all { it in ids },"REVIEW_REF",p,"Unknown affected practice")
            musicCheck(d.s("category")!="unsupported-blocker","REVIEW_BLOCKER",p,d.s("reason"))
            if(d.s("category")=="review-needed") {
                musicCheck(review.s("status")=="conditional" && assumptions.isNotEmpty() && d.s("handling")=="assumption" &&
                    d.s("featureId") in setOf("source.repeat-count.v1","source.coverage.v1") && d.strings("practiceIds").isNotEmpty(),
                    "REVIEW_NEEDED",p,"Unresolved musical fact must be reviewed")
            }
            if(d.s("category") in listOf("approximation","review-needed")) warnings+=d.s("reason")
        }
        musicCheck(review.s("status")!="conditional" || assumptions.isNotEmpty(),"CONDITIONAL","/review/assumptions","Conditional requires concrete assumptions")
        warnings+=assumptions
        val provenance=roots.getValue("provenance.json")
        val sources=provenance.objects("sources")
        musicCheck(sources.map { it.s("id") }.distinct().size==sources.size,"SOURCE_ID","/provenance/sources","Duplicate source")
        val sourceIds=sources.map { it.s("id") }.toSet()
        val allNotes=score.voices.flatMap { v -> v.events.flatMap { it.notes } }
        val noteIds=allNotes.map { it.id }.toSet()
        val mappings=provenance.objects("mappings")
        musicCheck(mappings.all { it.s("noteId") in noteIds && it.s("sourceId") in sourceIds } &&
            mappings.map { it.s("noteId") }.toSet()==noteIds,"SOURCE_MAPPING","/provenance/mappings","Every source note requires a valid locator")
        val allEvents=score.voices.flatMap { it.events }
        val coverage=review["coverage"].objectValue()
        val expected=mapOf("tracks" to score.tracks.size,"voices" to score.voices.size,"measures" to score.measures.size,
            "events" to allEvents.size,"notes" to allNotes.size,"rests" to allEvents.count { it.notes.isEmpty() },
            "relations" to score.relations.size,"techniques" to allEvents.sumOf { e -> e.notes.count {
                it.dead || it.harmonic!=null || it.articulation!=Articulation() || e.articulation!=Articulation() } },
            "visits" to plannedVisits,"onsets" to onsets)
        expected.forEach { (key,count) -> musicCheck(coverage.i(key)==count,"COVERAGE","/review/coverage/"+key,"Expected "+count) }
        val aliases=roots.getValue("practices.json").objects("aliases").associate { it.s("from") to it.s("to") }
        musicCheck(aliases.size==roots.getValue("practices.json").objects("aliases").size,"ALIAS","/practices/aliases","Duplicate alias")
        aliases.forEach { (from,to) ->
            musicCheck(from !in ids && to in ids+aliases.keys,"ALIAS","/practices/aliases","Alias shadows practice or target missing")
            val seen=mutableSetOf(from);var target=to
            while(target in aliases) { musicCheck(seen.add(target),"ALIAS_CYCLE","/practices/aliases","Alias cycle");target=aliases.getValue(target) }
        }
        warnings+="使用既有吉他采样；泛音、推弦、连奏、揉弦和摇把的音色为采样近似。"
        val display=manifest["display"].objectValue()
        return SongPackage(PackageIdentity(packageId,manifest.i("revision"),sha256(bytes)),display.s("title"),
            display.s("author"),display.s("summary"),score,practices,aliases,derived,warnings.distinct(),
            sources.joinToString("；") { it.s("name")+" · "+it.s("license")+" · "+it.s("origin") },files.toMap())
    }
    private fun centralDirectory(bytes: ByteArray) {
        fun u16(at: Int)=(bytes[at].toInt() and 255) or ((bytes[at+1].toInt() and 255) shl 8)
        fun u32(at: Int)=ByteBuffer.wrap(bytes,at,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        var end=-1
        for(i in bytes.size-22 downTo maxOf(0,bytes.size-65557)) {
            if(u32(i)==0x06054b50L && i+22+u16(i+20)==bytes.size) { end=i;break }
        }
        musicCheck(end>=0,"ZIP","/container","Missing ZIP end record")
        musicCheck(u16(end+4)==0 && u16(end+6)==0 && u16(end+8)==5 && u16(end+10)==5,
            "ZIP","/container","Five entries; no multi-disk or ZIP64")
        val offset=u32(end+16);val size=u32(end+12)
        musicCheck(offset+size==end.toLong(),"ZIP","/container","Invalid directory bounds")
        var at=offset.toInt();val names=mutableSetOf<String>()
        repeat(5) {
            musicCheck(at>=0 && at+46<=end && u32(at)==0x02014b50L,"ZIP","/container","Invalid central entry")
            val flags=u16(at+8);val method=u16(at+10)
            val len=u16(at+28);val extra=u16(at+30);val comment=u16(at+32)
            musicCheck(at+46+len+extra+comment<=end,"ZIP","/container","Truncated central entry")
            val name=String(bytes,at+46,len,Charsets.UTF_8)
            val mode=(u32(at+38) ushr 16).toInt()
            musicCheck(name in SongLimits.FILES && names.add(name) && flags and 1==0 && method in listOf(0,8) &&
                mode and 0xf000!=0xa000,"ZIP_ENTRY","/container/"+name,"Unknown, duplicate, encrypted or symlink entry")
            at+=46+len+extra+comment
        }
        musicCheck(at==end,"ZIP","/container","Extra central entries")
    }
}
