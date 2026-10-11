package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position

data class Phrase(
    val id: String, val title: String, val category: String, val passage: String,
    val source: String, val sourceBpm: Int, val difficulty: String, val technique: String,
    val conditions: String, val sequence: Sequence,
)

object Phrases {
    // Printed bars 10–11 = zero-based GP master bars 10–11 (un-numbered pickup).
    // Rhythm Guitar, standard tuning, capo 0. Straight eighths: Triplet16th does
    // not swing these eighth notes. Chord names describe these reduced grips.
    val usotsuki = Phrase(
        id="S01", title="嘘月 · 低音与高音分解", category="伴奏短句", passage="10–11 小节",
        source="嘘月.gp · Rhythm Guitar · master index 10–11", sourceBpm=80,
        difficulty="入门（暂定）", technique="低音分解", conditions="标准调弦 · 无变调夹",
        sequence=Sequence(
            events=listOf(
                Position(6,3), Position(4,0), Position(3,2), Position(4,0),
                Position(5,0), Position(4,0), Position(3,2), Position(4,0),
                Position(5,2), Position(4,0), Position(3,2), Position(4,0),
                Position(6,2), Position(4,0), Position(3,2), Position(4,0),
            ).mapIndexed { index, position ->
                val chord=listOf("Gsus2","Asus4(no5)","Bm7(no5)","D/F♯")[index/4]
                Event(index*6,6,listOf(position),chord,ring=true)
            },
            length=2*BAR,
            explanation="《嘘月》10–11 小节 · 伴奏改编练习。八分音符低音与高音交替；让音延续至同弦再拨或换指法，句尾收音后循环。no5 表示省略五音，只弹图中有效弦。标准调弦，无变调夹；原速 80 BPM，练习速度自行调整。",
            sections=listOf(Section(0,"Gsus2"),Section(24,"Asus4(no5)"),Section(48,"Bm7(no5)"),Section(72,"D/F♯")),
        ),
    )
    val all=listOf(usotsuki)
    fun named(id: String) = all.first { it.id==id }
}
