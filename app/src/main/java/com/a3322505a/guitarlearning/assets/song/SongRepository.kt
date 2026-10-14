package com.a3322505a.guitarlearning.assets.song

import android.util.AtomicFile
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.practice.compile.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.ceil
import java.util.Collections
import java.util.concurrent.CancellationException
import com.a3322505a.guitarlearning.music.*

data class ImportCandidate(val song: SongPackage,val stagingFile: File)
data class SongRevision(val identity: PackageIdentity,val title: String)
data class LibrarySnapshot(val phrases: List<Phrase>,val catalogue: List<Phrase>,val packages: List<SongRevision>,
    val diagnostics: List<String> = emptyList(),val aliases: Map<String,String> = emptyMap(),val chords: List<Chord> = Chords.all)

/** The cancel request and the final publication checkpoint share one lock. */
class ImportCancellation {
    private var cancelled=false
    @Volatile var committing=false
        private set
    @Synchronized fun cancel(): Boolean {
        if(committing) return false
        cancelled=true
        return true
    }
    @Synchronized fun check() { if(cancelled) throw CancellationException("已取消导入") }
    @Synchronized internal fun beginCommit(checkpoint: ()->Unit) { check();checkpoint();committing=true }
}

/** Single-writer repository. The immutable catalogue owns no player/session. */
class SongRepository(private val root: File,private val reader: SongReader) {
    companion object {
        // Process-local ownership also protects a preview during another repository's reload.
        private val candidates=Collections.synchronizedSet(mutableSetOf<String>())
        private val stagingName=Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.gpractice")
    }
    @Volatile var snapshot=LibrarySnapshot(Phrases.all,Phrases.catalogue,emptyList())
        private set
    private val index=AtomicFile(File(root,"active-index.json"))
    private data class Entry(val packageId: String,val revision: Int,val digest: String,val metadataHash: String = "") {
        val directory get() = packageId+"/"+revision+"-"+digest
        val pin get() = PackageIdentity(packageId,revision,digest)
    }
    @Volatile private var history=listOf<Entry>()
    private var active=listOf<Entry>()
    @Volatile private var prepared: Map<String,Phrase> = emptyMap()
    private fun entry(map: Map<String,Any?>): Entry {
        val id=map.s("packageId");val digest=map.s("digest")
        musicCheck(id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z0-9][a-z0-9-]*)+")) && !id.startsWith("builtin.") &&
            digest.matches(Regex("[0-9a-f]{64}")) && map.i("revision")>0,"INDEX","/index","Invalid stored identity")
        val metadataHash=map.s("metadataHash")
        musicCheck(metadataHash.isEmpty() || metadataHash.matches(Regex("[0-9a-f]{64}")),"INDEX","/index","Invalid catalogue hash")
        return Entry(id,map.i("revision"),digest,metadataHash)
    }
    private fun file(e: Entry): File {
        val target=File(root,e.directory+"/package.gpractice")
        musicCheck(target.canonicalPath.startsWith(root.canonicalPath+File.separator),"PATH","/index","Path leaves song storage")
        return target
    }
    /** Call off the UI thread. Broken packages are isolated; built-ins remain available. */
    @Synchronized fun reload(): LibrarySnapshot {
        root.mkdirs()
        cleanStaging()
        val diagnostics=mutableListOf<String>()
        if(index.baseFile.exists() || File(index.baseFile.path+".bak").exists()) {
            try {
                val data=StrictJson.parse(index.openRead().use { reader.readInput(it) }).objectValue()
                history=data.objects("history").mapNotNull { runCatching { entry(it) }.getOrElse { error ->
                    diagnostics+="修订索引损坏："+error.message;null } }
                active=data.objects("active").mapNotNull { runCatching { entry(it) }.getOrElse { error ->
                    diagnostics+="曲目索引损坏："+error.message;null } }
            } catch(e: Exception) { diagnostics+="曲库索引不可读："+e.message;active=emptyList();history=emptyList() }
        }
        val external=mutableListOf<Phrase>();val packages=mutableListOf<SongRevision>();val visible=mutableSetOf<String>()
        val aliases=mutableMapOf<String,String>();val chords=mutableListOf<Chord>()
        active.distinctBy { it.packageId }.forEach { e ->
            try {
                val bytes=file(e).inputStream().use { reader.readInput(it) }
                musicCheck(sha256(bytes)==e.digest,"HASH","/index","Stored revision digest mismatch")
                val metadataFile=File(file(e).parentFile,"catalogue.json")
                val metadata=if(metadataFile.exists() && e.metadataHash.isNotEmpty()) {
                    val data=metadataFile.readBytes()
                    musicCheck(sha256(data)==e.metadataHash,"HASH","/catalogue","Catalogue bytes changed")
                    StrictJson.parse(data).objectValue()
                }
                    else reader.read(bytes).let { song -> metadata(song).also { writeMetadata(e,it) } }
                        .let { StrictJson.parse(StrictJson.encode(it)).objectValue() }
                musicCheck(metadata.s("digest")==e.digest && metadata.s("packageId")==e.packageId && metadata.i("revision")==e.revision,
                    "IDENTITY","/index","Catalogue identity mismatch")
                val phrases=metadata.objects("practices").map { descriptor(e,it,metadata) }
                val teaching=if(metadata.containsKey("voicings")) metadata else StrictJson.parse(StrictJson.encode(metadata(reader.read(bytes)))).objectValue()
                val tracks=teaching.objects("tracks").map { Track(it.s("id"),it.s("name"),it.list("openStringMidi").map { n -> n.intValue() },it.i("capo")) }
                val songChords=teaching.objects("voicings").map { v ->
                    val voicing=GuitarVoicing(v.s("id"),v.s("name"),v.s("trackId"),v.list("frets").map { it.intValue() },v.list("fingers").map { it.intValue() })
                    voicing.chord(tracks.first { it.id==voicing.trackId },metadata.s("title"),metadata.s("source"),PracticeKey(e.packageId,voicing.id).encoded)
                }
                external+=phrases;chords+=songChords
                metadata.objects("aliases").forEach { alias -> aliases[PracticeKey(e.packageId,alias.s("from")).encoded]=PracticeKey(e.packageId,alias.s("to")).encoded }
                metadata.objects("practices").filter { it.s("visibility")=="core" }.forEach { visible+=PracticeKey(e.packageId,it.s("id")).encoded }
                packages+=SongRevision(e.pin,metadata.s("title"))
            } catch(error: Exception) { diagnostics+=e.packageId+" 已隔离："+error.message }
        }
        MigratedPracticeIds.targets.forEach { (old,target) -> if(external.any { it.id==target }) aliases[old]=target }
        snapshot=LibrarySnapshot(Collections.unmodifiableList(Phrases.all+external),
            Collections.unmodifiableList(Phrases.catalogue+external.filter { it.id in visible }),packages.toList(),diagnostics.toList(),aliases.toMap(),
            Collections.unmodifiableList(Chords.all+chords))
        return snapshot
    }
    @Synchronized fun prepare(input: java.io.InputStream,cancel: ()->Unit = {}): ImportCandidate {
        val bytes=reader.readInput(input,cancel); val song=reader.read(bytes,cancel=cancel)
        val stage=File(root,"staging/"+UUID.randomUUID()+".gpractice")
        stage.parentFile!!.mkdirs()
        musicCheck(stage.canonicalFile.parentFile?.parentFile==root.canonicalFile,"PATH","/staging","Wrong staging directory")
        candidates.add(stage.canonicalPath)
        try {
            FileOutputStream(stage).use { it.write(bytes);it.fd.sync() }
            cancel()
            return ImportCandidate(song,stage)
        } catch(e: Exception) { discard(stage);throw e }
    }
    @Synchronized fun cancel(candidate: ImportCandidate) {
        musicCheck(candidate.stagingFile.canonicalPath.startsWith(File(root,"staging").canonicalPath+File.separator),
            "PATH","/staging","Wrong candidate storage")
        discard(candidate.stagingFile)
    }
    private fun discard(stage: File) { stage.delete();candidates.remove(stage.canonicalPath) }
    private fun cleanStaging() {
        val staging=File(root,"staging").canonicalFile
        if(staging.parentFile!=root.canonicalFile) return
        staging.listFiles()?.filter { it.isFile && stagingName.matches(it.name) && it.canonicalFile.parentFile==staging && it.canonicalPath !in candidates }
            ?.forEach { it.delete() }
    }
    @Synchronized fun commit(candidate: ImportCandidate,rollback: Boolean = false,cancel: ()->Unit = {},
        cancellation: ImportCancellation = ImportCancellation(),onCommitStarted: ()->Unit = {}): LibrarySnapshot {
      try {
        fun checkpoint() { cancellation.check();cancel() }
        checkpoint()
        musicCheck(candidate.stagingFile.canonicalFile.parentFile==File(root,"staging").canonicalFile,
            "PATH","/staging","Wrong candidate storage")
        val song=candidate.song;val id=song.identity
        val existing=history.firstOrNull { it.packageId==id.packageId && it.revision==id.revision }
        musicCheck(existing==null || existing.digest==id.digest,"REVISION_CONFLICT","/manifest/revision","Same revision has different content")
        val current=active.firstOrNull { it.packageId==id.packageId }
        musicCheck(rollback || current==null || id.revision>=current.revision,"ROLLBACK_REQUIRED","/manifest/revision","Use explicit rollback for an older revision")
        val bytes=reader.readInput(candidate.stagingFile.inputStream(),::checkpoint)
        musicCheck(sha256(bytes)==id.digest,"HASH","/staging","Preview bytes changed")
        // Validate again before publication; a preview is not a write exemption.
        val verified=reader.read(bytes,cancel=::checkpoint)
        var e=Entry(id.packageId,id.revision,id.digest,sha256(StrictJson.encode(metadata(verified))))
        val dest=file(e)
        if(dest.exists()) musicCheck(sha256(reader.readInput(dest.inputStream(),::checkpoint))==id.digest,"HASH","/revision","Immutable revision changed")
        val catalogue=File(dest.parentFile,"catalogue.json")
        if(catalogue.exists()) {
            val hash=sha256(catalogue.readBytes())
            musicCheck(hash==e.metadataHash || hash==existing?.metadataHash,"HASH","/catalogue","Immutable catalogue changed")
            // Keep a v75 cache and its hash together; reload derives any missing teaching fields from the package.
            e=e.copy(metadataHash=hash)
        }
        cancellation.beginCommit(cancel)
        onCommitStarted()
        // No cancellation checks after this point. The caller must deliver the result even if disposed.
        dest.parentFile!!.mkdirs()
        if(!dest.exists()) {
            val pending=File(dest.parentFile,"pending-"+UUID.randomUUID())
            try {
                FileOutputStream(pending).use { it.write(bytes);it.fd.sync() }
                musicCheck(pending.renameTo(dest),"STORAGE","/revision","Cannot publish immutable file")
            } finally { pending.delete() }
        }
        writeMetadata(e,metadata(verified))
        val nextHistory=(history+e).distinct()
        val nextActive=active.filter { it.packageId!=id.packageId }+e
        writeIndex(nextActive,nextHistory)
        active=nextActive;history=nextHistory
        return reload()
      } finally { this.cancel(candidate) }
    }
    fun revisions(packageId: String) = history.filter { it.packageId==packageId }.map { it.pin }.sortedByDescending { it.revision }
    @Synchronized fun rollback(identity: PackageIdentity): LibrarySnapshot {
        val e=history.firstOrNull { it.pin==identity } ?: throw MusicError(Diagnostic("REVISION","/index","Revision missing"))
        val bytes=reader.readInput(file(e).inputStream());val song=reader.read(bytes)
        musicCheck(song.identity==identity,"HASH","/revision","Rollback bytes changed")
        writeIndex(active.filter { it.packageId!=e.packageId }+e,history)
        return reload()
    }
    @Synchronized fun disable(packageId: String): LibrarySnapshot {
        writeIndex(active.filter { it.packageId!=packageId },history)
        return reload()
    }
    private fun writeIndex(activated: List<Entry>,revisions: List<Entry>) {
        fun encode(e: Entry) = "{\"packageId\":\""+e.packageId+"\",\"revision\":"+e.revision+",\"digest\":\""+e.digest+"\",\"metadataHash\":\""+e.metadataHash+"\"}"
        val bytes=("{\"active\":["+activated.joinToString(",",transform=::encode)+"],\"history\":["+
            revisions.joinToString(",",transform=::encode)+"]}").toByteArray()
        val stream=index.startWrite()
        try { stream.write(bytes);index.finishWrite(stream) } catch(e: Exception) { index.failWrite(stream);throw e }
    }
    fun named(id: String): Phrase {
        var target=id;val seen=mutableSetOf<String>()
        while(target in snapshot.aliases && seen.add(target)) target=snapshot.aliases.getValue(target)
        val descriptor=snapshot.phrases.firstOrNull { it.id==target } ?: return Phrases.practiceNamed(target)
        val digest=descriptor.assetKey?.let { key -> snapshot.packages.firstOrNull { it.identity.packageId==key.packageId }?.identity?.digest }
        return prepared[descriptor.id+"@"+digest] ?: descriptor
    }
    /** Only a selected practice is compiled; cache size is independent of library/song count. */
    @Synchronized fun resolve(id: String,cancel: ()->Unit = {}): Phrase {
        val descriptor=named(id)
        if(!descriptor.unprepared) return descriptor
        val key=descriptor.assetKey!!
        val entry=active.first { it.packageId==key.packageId }
        val song=reader.read(reader.readInput(file(entry).inputStream(),cancel),cancel=cancel)
        val practice=song.practices.first { it.id==key.practiceId }
        val result=phrase(song,practice)
        val updated=prepared.toMutableMap()
        if(updated.size>=3) updated.remove(updated.keys.first())
        updated[result.id+"@"+song.identity.digest]=result
        while(updated.size>1 && updated.values.sumOf { it.sequence.performance.notes.size }>100000) updated.remove(updated.keys.first())
        prepared=updated.toMap()
        return result
    }
    fun family(phrase: Phrase): Phrase {
        if(phrase.assetKey==null) return Phrases.family(phrase)
        var current=phrase;val seen=mutableSetOf<String>()
        while(seen.add(current.id)) {
            val target=current.familyKey?.takeIf { it!=current.id } ?: current.relatedId ?: return current
            current=named(target)
        }
        return phrase
    }
    fun versions(phrase: Phrase): List<Phrase> {
        if(phrase.assetKey==null) return Phrases.versions(phrase)
        val base=family(phrase)
        return listOf(base)+snapshot.phrases.filter { it.assetKey?.packageId==base.assetKey?.packageId && it.id!=base.id && family(it).id==base.id }
    }
    private fun phrase(song: SongPackage,p: PracticeSpec): Phrase {
        val key=PracticeKey(song.identity.packageId,p.id)
        val plan=PracticeCompiler.compile(song.score,p,song.identity.packageId+"/"+song.identity.revision+"/"+song.identity.digest)
        val sections=plan.harmonies.map { Section((it.start.value()*QUARTER).toInt(),it.symbol.name,it.voicing?.id) }
        val sequence=Sequence(emptyList(),ceil(plan.length.value()*QUARTER).toInt(),
            p.goal+" "+song.summary+" "+song.warnings.joinToString(" "),sections=sections,capoFret=plan.mainTrack.capo,runtimePlan=plan)
        return Phrase(key.encoded,p.title,song.title,"曲包修订 "+song.identity.revision,song.sourceDescription,
            (song.score.tempos.first().bpm*song.score.tempos.first().beatUnit.value()).toInt(),p.difficulty,p.tags.joinToString(" · "),
            if(plan.mainTrack.openStringMidi==Music.openMidi) {
                if(plan.mainTrack.capo==0) "标准调弦 · 无变调夹" else "标准调弦 · 变调夹"+plan.mainTrack.capo+"品"
            } else plan.mainTrack.name+" · 调弦 "+plan.mainTrack.openStringMidi.joinToString()+" · 夹"+plan.mainTrack.capo+"品",sequence,
            goal=p.goal,suggestedBpm=p.suggestedTempo,use="本地曲库 · "+song.title,relatedId=p.variantOf?.let { PracticeKey(song.identity.packageId,it).encoded },
            version=if(p.variantOf!=null) p.title else null,assetKey=key,familyKey=p.familyId?.let { PracticeKey(song.identity.packageId,it).encoded },
            defaultSourceTempo=p.tempoMode=="source",tempoLabel="原谱 · 四分音符等值",legacyId=MigratedPracticeIds.legacy(key.encoded))
    }
    private fun metadata(song: SongPackage): Map<String,Any?> = mapOf(
        "packageId" to song.identity.packageId,"revision" to song.identity.revision,"digest" to song.identity.digest,
        "title" to song.title,"source" to song.sourceDescription,"sourceBpm" to song.score.tempos.first().bpm.toInt(),
        "warnings" to song.warnings.joinToString(" "),
        "summary" to song.summary,
        "tracks" to song.score.tracks.map { mapOf("id" to it.id,"name" to it.name,"openStringMidi" to it.openStringMidi,"capo" to it.capo) },
        "voicings" to song.score.voicings.map { mapOf("id" to it.id,"name" to it.name,"trackId" to it.trackId,"frets" to it.frets,"fingers" to it.fingers) },
        "aliases" to song.aliases.map { (from,to) -> mapOf("from" to from,"to" to to) },
        "practices" to song.practices.sortedBy { it.order }.map { p -> mapOf(
            "id" to p.id,"title" to p.title,"goal" to p.goal,"suggestedTempo" to p.suggestedTempo,
            "difficulty" to p.difficulty,"tags" to p.tags.joinToString(" · "),"visibility" to p.visibility,
            "variantOf" to p.variantOf,"familyId" to p.familyId,"tempoMode" to p.tempoMode) })
    private fun writeMetadata(e: Entry,value: Map<String,Any?>) {
        val target=File(file(e).parentFile,"catalogue.json")
        if(!target.exists()) {
            val pending=File(target.parentFile,"catalogue-"+UUID.randomUUID())
            try {
                FileOutputStream(pending).use { it.write(StrictJson.encode(value));it.fd.sync() }
                musicCheck(pending.renameTo(target),"STORAGE","/revision","Cannot publish catalogue")
            } finally { pending.delete() }
        }
    }
    private fun descriptor(e: Entry,p: Map<String,Any?>,metadata: Map<String,Any?>): Phrase {
        val key=PracticeKey(e.packageId,p.s("id"))
        return Phrase(key.encoded,p.s("title"),metadata.s("title"),"修订 "+e.revision,metadata.s("source"),
            metadata.i("sourceBpm",60),p.s("difficulty"),p.s("tags"),"准备后显示轨道与调弦",
            Sequence(emptyList(),48,p.s("goal")+" "+metadata.s("summary")+" "+metadata.s("warnings"),catalogueOnly=true),
            goal=p.s("goal"),suggestedBpm=p.i("suggestedTempo",60),use="本地曲库 · "+metadata.s("title"),
            relatedId=(p["variantOf"] as? String)?.let { PracticeKey(e.packageId,it).encoded },
            version=if(p["variantOf"]!=null) p.s("title") else null,assetKey=key,
            familyKey=(p["familyId"] as? String)?.let { PracticeKey(e.packageId,it).encoded },
            unprepared=true,defaultSourceTempo=p.s("tempoMode","source")=="source",legacyId=MigratedPracticeIds.legacy(key.encoded))
    }
}
