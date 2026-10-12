package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position

/** v2 mid-position arrangement: actual sounding pitches, standard tuning, no capo.
 * Source GP SHA-256: 437901aaf1f310740f18366c7a079f2e503c438cde364dcf97804db430110e91.
 * Each eight-bar phrase is practised independently; source repeats are not baked into its events.
 */
internal object MozartK265 {
    private fun n(string: Int, fret: Int, ticks: Int = 3) = Event(0,ticks,listOf(Position(string,fret)))
    private fun rest(ticks: Int) = Event(0,ticks,emptyList())
    private val bars = listOf(
        listOf(n(3,7),n(3,5),n(3,4),n(3,5),n(3,4),n(3,5),n(3,4),n(3,5)),
        listOf(n(2,10),n(2,8),n(2,7),n(2,8),n(2,7),n(2,8),n(2,7),n(2,8)),
        listOf(n(2,9),n(2,10),n(1,8),n(1,7),n(1,10),n(1,8),n(1,7),n(2,10)),
        listOf(n(2,10),n(2,8),n(1,12),n(1,10),n(1,8),n(1,7),n(2,10),n(2,8)),
        listOf(n(2,8),n(2,6),n(1,10),n(1,8),n(1,7),n(1,5),n(2,8),n(2,6)),
        listOf(n(2,6),n(2,5),n(1,8),n(1,7),n(1,5),n(2,8),n(2,6),n(2,5)),
        listOf(n(3,7,6),n(1,5,6),n(2,8,6),n(3,4,6)),
        listOf(n(3,5,12),rest(12)),
        listOf(n(2,10),n(2,8),n(2,7),n(2,8),n(2,7),n(2,8),n(2,10),n(2,8)),
        listOf(n(2,8),n(2,6),n(2,5),n(2,6),n(2,5),n(2,6),n(2,8),n(2,6)),
        listOf(n(2,6),n(2,5),n(2,4),n(2,5),n(2,4),n(2,5),n(2,6),n(2,5)),
        listOf(n(2,5),n(3,7),n(3,6),n(3,7),n(3,6),n(3,7),n(2,5),n(3,7)),
        listOf(n(2,10),n(2,8),n(2,7),n(2,8),n(1,12),n(1,8),n(2,10),n(2,8)),
        listOf(n(2,8),n(2,6),n(2,5),n(2,6),n(1,10),n(1,7),n(2,8),n(2,6)),
        listOf(n(2,6),n(2,5),n(2,4),n(2,5),n(1,8),n(2,8),n(2,6),n(2,5)),
        listOf(n(2,8,9),n(2,5),n(3,7,12)),
        listOf(n(3,7),n(3,5),n(3,4),n(3,5),n(3,4),n(3,5),n(3,4),n(3,5)),
        listOf(n(2,10),n(2,8),n(2,7),n(2,8),n(2,7),n(2,8),n(2,7),n(2,8)),
        listOf(n(2,9),n(2,10),n(1,8),n(1,7),n(1,10),n(1,8),n(1,7),n(2,10)),
        listOf(n(2,10),n(2,8),n(1,12),n(1,10),n(1,8),n(1,7),n(2,10),n(2,8)),
        listOf(n(2,8),n(2,6),n(1,10),n(1,8),n(1,7),n(1,5),n(2,8),n(2,6)),
        listOf(n(2,6),n(2,5),n(1,8),n(1,7),n(1,5),n(2,8),n(2,6),n(2,5)),
        listOf(n(3,7,6),n(1,5,6),n(2,8,6),n(3,4,6)),
        listOf(n(3,5,12),rest(12)),
    )
    private val names=listOf("主题","发展","再现与结尾")
    private val ids=listOf("K265-A","K265-B","K265-C")
    val phrases=bars.chunked(8).mapIndexed { index,part ->
        val events=mutableListOf<Event>()
        var tick=0
        part.forEach { bar ->
            require(bar.sumOf { it.duration }==2*QUARTER)
            bar.forEach { note -> events+=note.copy(tick=tick);tick+=note.duration }
        }
        val first=index*8+1
        Phrase(
            id=ids[index],title="莫扎特 K265 · ${index+1} ${names[index]}",
            category="古典单音短句",passage="$first–${first+7} 小节",
            source="莫扎特 K265 变奏一 · 中把位 v2 播放修复.gp",
            sourceBpm=120,tempoLabel="参考速度",difficulty="进阶（暂定）",technique="逐音拨弦",suggestedBpm=40,
            goal="均匀弹奏每拍四音，保持八小节乐句连续。",
            conditions="标准调弦 · 无变调夹",
            sequence=Sequence(events,tick,
                "变奏一 · ${names[index]}，原谱 $first–${first+7} 小节。前后半句连续弹奏，共 8 小节。2/4 拍，十六分音符每拍 4 音；保留句尾长音与休止。1–3 弦、4–12 品，实际发声比钢琴高音声部低八度。逐音拨弦，按当前速度与循环次数练习；120 BPM 仅为 GP 参考速度。",
                beatsPerBar=2,originalBar=first-1),
        )
    }
}
