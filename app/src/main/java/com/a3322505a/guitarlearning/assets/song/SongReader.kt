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
    val CORE = setOf("manifest.json","score.json","practices.json")
    fun ignored(name: String) = name.endsWith("/") || name==".DS_Store" || name.startsWith("__MACOSX/")
    val FILES = setOf("manifest.json","score.json","practices.json","provenance.json","review.json")
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
        val central=centralDirectory(bytes)
        val entries=mutableListOf<String>()
        val files=linkedMapOf<String,ByteArray>();var total=0
        ZipInputStream(ByteArrayInputStream(bytes),Charsets.UTF_8).use { archive ->
            while(true) {
                cancel();val entry=archive.nextEntry ?: break
                entries+=entry.name
                musicCheck(entries.size<=128,"LIMIT","/container","Entry count exceeds 128")
                val ignored=SongLimits.ignored(entry.name)
                musicCheck(ignored || entry.name in SongLimits.FILES && !files.containsKey(entry.name),
                    "ZIP_ENTRY","/container/"+entry.name,"Unexpected/duplicate core file")
                val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {
                    cancel();val n=archive.read(buffer);if(n<0) break
                    total+=n
                    musicCheck(out.size()+n<=SongLimits.JSON && total<=SongLimits.TOTAL,"LIMIT","/container/"+entry.name,"Inflated byte limit")
                    out.write(buffer,0,n)
                }
                if(!ignored) files[entry.name]=out.toByteArray()
            }
        }
        musicCheck(entries.sorted()==central.sorted(),"ZIP","/container","Local/central entries differ")
        musicCheck(SongLimits.CORE.all { it in files },"ZIP_FILES","/container","Missing core JSON file")
        // Read only the three musical documents; ignore retired v1 audit metadata.
        val rawManifest=StrictJson.parse(files.getValue("manifest.json")).objectValue()
        val manifest=rawManifest.filterKeys { it in setOf("format","scoreSchemaVersion","packageId","revision","display","extensions") }.toMutableMap()
        manifest["formatVersion"]=mapOf("major" to java.math.BigDecimal(2), "minor" to java.math.BigDecimal.ZERO)
        schema.validate("manifest",manifest)
        val warnings=mutableListOf<String>()
        var roots: Map<String,Map<String,Any?>> = linkedMapOf(
            "manifest.json" to manifest,
            "score.json" to StrictJson.parse(files.getValue("score.json")).objectValue(),
            "practices.json" to StrictJson.parse(files.getValue("practices.json")).objectValue())
        roots.forEach { (name,data) -> schema.validate(name.removeSuffix(".json"),data,normalizing=name!="manifest.json") }
        roots=normalizeSong(roots,warnings)
        roots.forEach { (name,data) -> schema.validate(name.removeSuffix(".json"),data) }
        val packageId=manifest.s("packageId")
        musicCheck(builtin || !packageId.startsWith("builtin."),"NAMESPACE","/manifest/packageId","Reserved built-in namespace")
        val score=SongCodec.score(roots.getValue("score.json"));val practices=SongCodec.practices(roots.getValue("practices.json"))
        val derived=ScoreValidation.validate(score,practices)
        cancel()
        val routes=FormCompiler.routes(score)
        practices.forEach { cancel();PracticeCompiler.compile(score,it,routes=routes) }
        val ids=practices.map { it.id }.toSet()
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
            "本地曲包",files.filterKeys { it in SongLimits.CORE })
    }
    private fun centralDirectory(bytes: ByteArray): List<String> {
        fun u16(at: Int)=(bytes[at].toInt() and 255) or ((bytes[at+1].toInt() and 255) shl 8)
        fun u32(at: Int)=ByteBuffer.wrap(bytes,at,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        var end=-1
        for(i in bytes.size-22 downTo maxOf(0,bytes.size-65557)) {
            if(u32(i)==0x06054b50L && i+22+u16(i+20)==bytes.size) { end=i;break }
        }
        musicCheck(end>=0,"ZIP","/container","Missing ZIP end record")
        musicCheck(u16(end+4)==0 && u16(end+6)==0 && u16(end+8)==u16(end+10) && u16(end+10) in 3..128,
            "ZIP","/container","Entry budget; no multi-disk or ZIP64")
        val offset=u32(end+16);val size=u32(end+12)
        musicCheck(offset+size==end.toLong(),"ZIP","/container","Invalid directory bounds")
        var at=offset.toInt();val names=mutableListOf<String>();val core=mutableSetOf<String>()
        repeat(u16(end+10)) {
            musicCheck(at>=0 && at+46<=end && u32(at)==0x02014b50L,"ZIP","/container","Invalid central entry")
            val flags=u16(at+8);val method=u16(at+10)
            val len=u16(at+28);val extra=u16(at+30);val comment=u16(at+32)
            musicCheck(at+46+len+extra+comment<=end,"ZIP","/container","Truncated central entry")
            val name=String(bytes,at+46,len,Charsets.UTF_8)
            val mode=(u32(at+38) ushr 16).toInt()
            musicCheck((SongLimits.ignored(name) || name in SongLimits.FILES && core.add(name)) && flags and 1==0 && method in listOf(0,8) &&
                mode and 0xf000!=0xa000,"ZIP_ENTRY","/container/"+name,"Unknown, duplicate, encrypted or symlink entry")
            names+=name
            at+=46+len+extra+comment
        }
        musicCheck(at==end,"ZIP","/container","Extra central entries")
        return names
    }
}
