package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.music.Chords

data class Phrase(
    val id: String, val title: String, val category: String, val passage: String,
    val source: String, val sourceBpm: Int, val difficulty: String, val technique: String,
    val conditions: String, val sequence: Sequence,
    val tempoLabel: String = "原速",
    val goal: String = "保持拍点，按谱面完整弹奏一轮。", val suggestedBpm: Int = 60,
    val use: String = "单音", val relatedId: String? = null, val version: String? = null,
    val assetKey: com.a3322505a.guitarlearning.music.score.PracticeKey? = null,
    val familyKey: String? = null,
    val unprepared: Boolean = false,val defaultSourceTempo: Boolean = true,
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
    val singleNote = Phrase(
        id="M01",title="单音短句 · C 大调小句",category="单音旋律",passage="1 小节",
        source="原创入门练习",sourceBpm=60,difficulty="入门",technique="单音拨弦",
        conditions="标准调弦 · 无变调夹",
        sequence=Sequence(listOf(Position(5,3),Position(4,0),Position(4,2),Position(5,3)).mapIndexed { i,p ->
            Event(i*12,12,listOf(p))
        },BAR,"原创单音小句 C3–D3–E3–C3；每音一拍，句尾回到 C3 后循环。"),
    )
    val mozart = MozartK265.phrases
    val shayou = Shayou.phrases
    val kafka = Kafka.phrases
    val all=(listOf(usotsuki.copy(use="和弦分解",goal="低音与高音交替，换指法时整齐收音。",suggestedBpm=40),singleNote)+mozart+KernelPhrases.all+shayou+kafka+KafkaPractice.all+YourTrip.phrases+SevenBlocks.phrases).also(::validatePhrases)
    val catalogue=all.filter { !it.id.startsWith("KAF-") }+KafkaPractice.core
    fun practiceNamed(id: String)=named(KafkaPractice.replacement(id) ?: id)
    fun family(phrase: Phrase)=if(phrase.id in KafkaPractice.all.map { it.id }) named(phrase.relatedId ?: phrase.id) else phrase
    fun versions(phrase: Phrase): List<Phrase> {
        val base=family(phrase)
        return if(base in KafkaPractice.core) listOf(base)+KafkaPractice.all.filter { it.relatedId==base.id } else emptyList()
    }
    fun named(id: String): Phrase {
        // Activity saved state from v61 can still contain any of the six former IDs.
        val current=when(id) {
            "K265-1","K265-2" -> "K265-A"
            "K265-3","K265-4" -> "K265-B"
            "K265-5","K265-6" -> "K265-C"
            else -> id
        }
        return all.firstOrNull { it.id==current } ?: all.first()
    }
}

/** Content registration is the boundary: no new player or notation path per phrase. */
fun validatePhrases(phrases: List<Phrase>) {
    val ids=mutableSetOf<String>()
    phrases.forEach { phrase ->
        fun valid(condition: Boolean, detail: String) { require(condition) { "${phrase.id}: $detail" } }
        valid(phrase.id.isNotBlank() && ids.add(phrase.id),"duplicate or blank ID")
        valid(phrase.title.isNotBlank() && phrase.category.isNotBlank() && phrase.source.isNotBlank(),"missing title/category/source")
        valid(phrase.sourceBpm>0,"invalid source BPM")
        val seq=phrase.sequence
        val conditions=if(seq.capoFret==0) "标准调弦 · 无变调夹" else "标准调弦 · 变调夹${seq.capoFret}品"
        valid(phrase.conditions==conditions,"unsupported tuning/capo label: does not match actual pitch")
        valid(phrase.goal.isNotBlank() && phrase.suggestedBpm in 30..180,"missing goal or invalid starting BPM")
        val expression=runCatching { seq.validateExpression() }
        valid(expression.isSuccess,expression.exceptionOrNull()?.message ?: "invalid expression")
        valid((seq.barMeters.isNotEmpty() || seq.length%seq.barTicks==0) && seq.events.isNotEmpty(),"length must contain complete bars and events")
        valid(seq.events.any { !it.rest },"no sounding notes")
        var end=0
        seq.events.forEachIndexed { index,event ->
            valid(event.tick==end,"event[$index] must start at tick $end (use explicit rests)")
            valid(event.positions.all { it.string in 1..6 && it.fret in 0..24 },"event[$index] invalid string/fret")
            valid(event.positions.distinctBy { it.string }.size==event.positions.size,"event[$index] duplicate string")
            valid(event.chord==null || Chords.all.any { it.name==event.chord },"event[$index] unknown chord ${event.chord}")
            if(event.strum) valid(event.strings.size>1,"event[$index] strum needs multiple strings")
            end=event.tick+event.duration
        }
        valid(end==seq.length,"events must cover the whole phrase")
        seq.sections.forEachIndexed { index,section ->
            valid(section.tick in 0 until seq.length,"section[$index] invalid tick")
            valid(seq.events.any { it.tick==section.tick },"section[$index] must begin at an event onset")
            valid(Chords.all.any { it.name==section.chord },"section[$index] unknown chord ${section.chord}")
            valid(if(index==0) section.tick==0 else section.tick>seq.sections[index-1].tick,"section[$index] order/start")
        }
        seq.events.forEachIndexed { index,event ->
            val section=seq.sections.getOrNull(seq.sectionAt(event.tick.toDouble()))
            valid(if(section==null) event.chord==null else event.chord==section.chord,"event[$index] harmony does not match its section")
            if(section!=null) valid(event.positions.all { it in Chords.named(section.chord).positions },"event[$index] note outside displayed chord")
        }
        valid(runCatching { seq.tabBars() }.isSuccess,"unsupported TAB rhythm")
    }
}
