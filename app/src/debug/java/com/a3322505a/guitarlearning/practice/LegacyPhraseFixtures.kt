package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.music.Chords

internal object LegacyPhraseFixtures {
    val singleNote = Phrase(
        id="M01",title="单音短句 · C 大调小句",category="单音旋律",passage="1 小节",
        source="原创入门练习",sourceBpm=60,difficulty="入门",technique="单音拨弦",
        conditions="标准调弦 · 无变调夹",
        sequence=Sequence(listOf(Position(5,3),Position(4,0),Position(4,2),Position(5,3)).mapIndexed { i,p ->
            Event(i*12,12,listOf(p))
        },BAR,"原创单音小句 C3–D3–E3–C3；每音一拍，句尾回到 C3 后循环。"),
    )
    val all=(listOf(singleNote)+KernelPhrases.all+SevenBlocks.phrases).also(::validatePhrases)
    val catalogue=all
    fun practiceNamed(id: String)=named(id)
    fun family(phrase: Phrase)=phrase
    fun versions(phrase: Phrase): List<Phrase> = emptyList()
    fun named(id: String)=all.firstOrNull { it.id==id } ?: all.first()
}
