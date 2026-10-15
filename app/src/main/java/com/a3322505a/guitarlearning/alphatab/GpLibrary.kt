@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning.alphatab

import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.importer.ScoreLoader
import alphaTab.model.Score
import alphaTab.model.Track
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class GpPreferences(val track: Int = 0, val first: Int = 0, val last: Int = -1,
    val speed: Int = 100, val repeats: Int = 2, val tab: Boolean = true,
    val solo: Boolean = true, val countIn: Boolean = true, val metronome: Boolean = true)
data class GpSong(val id: String, val title: String, val fileName: String, val bytes: Long,
    val imported: Long, val opened: Long = 0, val preferences: GpPreferences = GpPreferences())

/** Private copies and one atomic record per file. Existing files/songs and preference keys are untouched. */
class GpLibrary(private val root: File) {
    companion object {
        const val MAX_BYTES = 96 * 1024 * 1024
        private val ID = Regex("[0-9a-f]{64}")
        private val FORMATS = setOf("gp", "gp5", "gpx")
        fun guitarTracks(score: Score) = score.tracks.filter { track ->
            track.playbackInfo.program.toInt() in 24..31 && track.staves.any { !it.isPercussion && it.tuning.length == 6.0 }
        }.toList()
        fun parse(bytes: ByteArray): Score {
            require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "GP 文件须小于 96 MiB" }
            val score = ScoreLoader.loadScoreFromBytes(Uint8Array(bytes.asUByteArray()), AlphaScoreAdapter.settings())
            val guitars = guitarTracks(score)
            require(guitars.isNotEmpty()) { "未发现可用的六弦吉他轨" }
            guitars.forEach { track -> track.staves.forEach { staff -> staff.bars.forEach { bar -> bar.voices.forEach { voice ->
                voice.beats.forEach { beat -> require(beat.notes.none { it.string > 0 && it.fret < 0 && !it.isDead }) {
                    "${track.name} 第 ${bar.index.toInt() + 1} 小节有负品位，请核对原谱"
                } }
            } } } }
            require(score.masterBars.length > 0) { "乐谱没有小节" }
            return score
        }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun readLimited(input: InputStream): ByteArray = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "GP 文件超过 96 MiB" }
                output.write(buffer, 0, count)
                check(!Thread.currentThread().isInterrupted) { "读取已取消" }
            }
            output.toByteArray()
        }
    }
    @Synchronized fun list(): List<GpSong> {
        if (!root.exists()) return emptyList()
        val ids = root.listFiles().orEmpty().map { it.name.removeSuffix(".bak").removeSuffix(".json") }
            .filter { ID.matches(it) }.distinct()
        return ids.mapNotNull { id -> runCatching { readRecord(id) }.getOrNull() }
            .sortedByDescending { maxOf(it.opened, it.imported) }
    }
    @Synchronized fun import(fileName: String, input: InputStream): Pair<GpSong, Score> {
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\').take(200)
        require(safeName.substringAfterLast('.').lowercase() in FORMATS) { "目前仅开放已试读的 GP5、GPX、GP 文件" }
        val bytes = readLimited(input)
        val score = parse(bytes)
        val id = hash(bytes)
        // A duplicate returns the existing record with every preference intact.
        val record = File(root, "$id.json")
        if (record.exists() || File(root, "$id.json.bak").exists()) {
            val previous = readRecord(id)
            require(source(id).isFile && hash(source(id).inputStream().use(::readLimited)) == id) {
                "已有本地副本校验失败，请保留原 GP 并重新建立副本"
            }
            return previous to score
        }
        check(root.isDirectory || root.mkdirs()) { "无法创建本地曲库" }
        val copy = File(root, "$id.score")
        val temp = File.createTempFile("gp-import-", ".tmp", root)
        try {
            temp.outputStream().use { it.write(bytes); it.fd.sync() }
            if (copy.exists()) require(hash(copy.readBytes()) == id) { "已有曲库文件校验失败" }
            else check(temp.renameTo(copy)) { "无法保存乐谱副本" }
            val song = GpSong(id, score.title.ifBlank { safeName.substringBeforeLast('.') }, safeName, bytes.size.toLong(),
                System.currentTimeMillis(), preferences = GpPreferences(track = guitarTracks(score).first().index.toInt()))
            writeRecord(song)
            return song to score
        } finally { temp.delete() }
    }
    @Synchronized fun open(id: String): Pair<GpSong, Score> {
        val song = readRecord(id)
        val bytes = source(id).inputStream().use(::readLimited)
        require(bytes.size.toLong() == song.bytes && hash(bytes) == id) { "本地谱件校验失败；请保留原文件重新导入" }
        return song to parse(bytes)
    }
    @Synchronized fun save(song: GpSong) {
        val previous = readRecord(song.id)
        require(previous.bytes == song.bytes && previous.imported == song.imported)
        writeRecord(song)
    }
    @Synchronized fun delete(id: String) {
        readRecord(id)
        val copy = source(id)
        // Exact managed copy only. No paths from original filenames are followed.
        check(!copy.exists() || copy.delete()) { "无法删除本地副本" }
        AtomicFile(File(root, "$id.json")).delete()
    }
    private fun source(id: String): File { require(ID.matches(id)); return File(root, "$id.score") }
    private fun readRecord(id: String): GpSong {
        require(ID.matches(id))
        val json = JSONObject(AtomicFile(File(root, "$id.json")).openRead().bufferedReader().use { it.readText() })
        require(json.getInt("schema") == 1 && json.getString("id") == id)
        val p = json.optJSONObject("preferences") ?: JSONObject()
        return GpSong(id, json.getString("title"), json.getString("fileName"), json.getLong("bytes"),
            json.getLong("imported"), json.optLong("opened"), GpPreferences(p.optInt("track"),
                p.optInt("first").coerceAtLeast(0), p.optInt("last", -1), p.optInt("speed", 100).coerceIn(25, 200),
                p.optInt("repeats", 2).coerceIn(0, 16), p.optBoolean("tab", true), p.optBoolean("solo", true),
                p.optBoolean("countIn", true), p.optBoolean("metronome", true)))
    }
    private fun writeRecord(song: GpSong) {
        require(ID.matches(song.id))
        val p = song.preferences
        val json = JSONObject().put("schema", 1).put("id", song.id).put("title", song.title).put("fileName", song.fileName)
            .put("bytes", song.bytes).put("imported", song.imported).put("opened", song.opened)
            .put("preferences", JSONObject().put("track", p.track).put("first", p.first).put("last", p.last)
                .put("speed", p.speed).put("repeats", p.repeats).put("tab", p.tab).put("solo", p.solo)
                .put("countIn", p.countIn).put("metronome", p.metronome))
        val atomic = AtomicFile(File(root, "${song.id}.json"))
        val stream = atomic.startWrite()
        try { stream.write(json.toString().toByteArray()); atomic.finishWrite(stream) }
        catch (failure: Exception) { atomic.failWrite(stream); throw failure }
    }
}
