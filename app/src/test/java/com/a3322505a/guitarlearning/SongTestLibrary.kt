package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import java.io.File
import java.nio.file.Files

/** Tests read the same preinstalled files as the App; source CSV is an independent fixture. */
internal object SongTestLibrary {
    private val contract=File("../contracts/song-package/v1")
    val assets=File("src/main/assets/song-packages")
    fun repository(root: File=Files.createTempDirectory("bundled-songs").toFile())=SongRepository(root,
        SongReader(SongSchema { File(contract,it).readBytes() }),
        assets.listFiles()!!.filter { it.extension=="gpractice" }.associate { it.name to { it.inputStream() } })
    val repository by lazy { repository().also { it.reload() } }
    val mozart get()=listOf("K265-A","K265-B","K265-C").map(repository::resolve)
    // Original source-derived sequences remain useful to general legacy range/notation tests.
    val sourceMozart by lazy {
        val rows=File("src/test/resources/mozart-k265-v2.csv").readLines().drop(1).map { it.split(',').map(String::toInt) }
        rows.chunkBy { (it[0]-1)/8 }.mapIndexed { i,part ->
            val sequence=Sequence(part.map { r -> Event(((r[0]-1)%8)*24+r[1],r[2],
                if(r[3]==0) emptyList() else listOf(Position(r[3],r[4]))) },192,"来源CSV",beatsPerBar=2,originalBar=i*8)
            Phrase("source-$i","来源CSV","测试","8小节","独立来源CSV",120,"测试","单音","标准调弦 · 无变调夹",sequence)
        }
    }
    private fun <T,K> List<T>.chunkBy(key: (T)->K)=groupBy(key).values.toList()
}
