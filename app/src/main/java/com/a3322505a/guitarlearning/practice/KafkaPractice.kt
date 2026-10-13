package com.a3322505a.guitarlearning.practice

/** Practice goals, independent of the source score's rehearsal letters and instrumentation. */
internal object KafkaPractice {
    private fun source(id: String)=Kafka.phrases.single { it.id==id }
    private const val ACCOMPANIMENT="卡夫卡 · 伴奏练习"
    private const val MELODY="卡夫卡 · 旋律与 solo"

    private fun exercise(id: String, sourceId: String, name: String, group: String, range: String,
        goal: String, sequence: Sequence=source(sourceId).sequence, relatedId: String?=null,
        version: String="标准段"): Phrase {
        val original=source(sourceId)
        return original.copy(id=id,title="卡夫卡 · $name",category=name.substringBefore("（"),use=group,
            passage="$range 小节 · 完整练习",goal=goal,sequence=sequence.copy(explanation=
                "$range 小节，${original.source.substringAfter(" · ")}；$goal 保留原弦品、时值、休止与奏法。技巧声音使用采样近似。"),
            relatedId=relatedId,version=version)
    }

    // Only whole silent bars after a completed musical line may be removed here.
    private fun withoutSilentTail(sequence: Sequence, bars: Int): Sequence {
        require(bars in 1..sequence.barCount)
        val end=sequence.barEnd(bars-1)
        require(sequence.events.filter { it.tick>=end || it.tick+it.duration>end }.all { it.rest })
        require(sequence.links.all { it.sourceTick<end && it.targetTick<end })
        return sequence.copy(events=sequence.events.filter { it.tick<end }.map { it.copy(duration=minOf(it.duration,end-it.tick)) },
            length=end,sections=sequence.sections.filter { it.tick<end },tuplets=sequence.tuplets.filter { it.end<=end },
            barMeters=(0 until bars).map(sequence::meter))
    }

    private fun joined(first: Sequence, second: Sequence): Sequence {
        require(first.originalBar+first.barCount==second.originalBar)
        require(first.capoFret==second.capoFret && first.swing==second.swing && first.beatsPerBar==second.beatsPerBar)
        val offset=first.length
        return first.copy(events=first.events+second.events.map { it.copy(tick=it.tick+offset) },length=offset+second.length,
            sections=first.sections+second.sections.map { it.copy(tick=it.tick+offset) },
            tuplets=first.tuplets+second.tuplets.map { it.copy(tick=it.tick+offset) },
            links=first.links+second.links.map { it.copy(sourceTick=it.sourceTick+offset,targetTick=it.targetTick+offset) },
            barMeters=(0 until first.barCount).map(first::meter)+(0 until second.barCount).map(second::meter))
    }

    val core=listOf(
        exercise("KAF-P-POWER","KAF-A-1-G1","断奏与换和弦",ACCOMPANIMENT,"1–8",
            "完整走完力量和弦进行，让每次断奏后的空拍保持清楚。"),
        exercise("KAF-P-ARPEGGIO","KAF-B-9-G2","低音与高音分解",ACCOMPANIMENT,"9–16",
            "让低音带出和弦变化，高音延响，连续弹完八小节分解伴奏。"),
        exercise("KAF-P-STRUM","KAF-E-29-G1","连续和弦节奏",ACCOMPANIMENT,"29–44",
            "保持八分摇摆和换和弦节奏，完整经过两轮推进与收尾。"),
        exercise("KAF-P-BASS","KAF-J-100-G1","低音与和弦交替",ACCOMPANIMENT,"100–115",
            "把低音与高音和弦连成完整伴奏，保留最后两小节的分解收束。",
            sequence=withoutSilentTail(source("KAF-J-100-G1").sequence,16)),
        exercise("KAF-P-RIFF","KAF-B-9-G3","主题 riff",MELODY,"9–16",
            "完整弹完主题、回应和尾句，保持十六分摇摆及装饰滑音。"),
        exercise("KAF-P-MELODY","KAF-E-29-G2","完整旋律段",MELODY,"29–44",
            "把十六小节旋律连贯弹完，保留长短音、重复与不同尾句。"),
        exercise("KAF-P-SOLO","KAF-I-82-G3","完整 solo",MELODY,"82–89",
            "从起句连续弹到第89小节滑出，保持推弦与连续跑动的拍点。",
            sequence=withoutSilentTail(source("KAF-I-82-G3").sequence,8)),
    )
    val variants=listOf(
        exercise("KAF-V-POWER-END","KAF-L-137-G1","断奏与换和弦（结尾版）",ACCOMPANIMENT,"137–148",
            "完整经过再现和最后四小节的和弦收尾。",
            sequence=joined(source("KAF-L-137-G1").sequence,source("KAF-M-145-G1").sequence),
            relatedId="KAF-P-POWER",version="再现与收尾"),
        exercise("KAF-V-STRUM-REPEAT","KAF-E-61-G1","连续和弦节奏（再现版）",ACCOMPANIMENT,"61–76",
            "保留第二次出现的完整进行和不同结束，不在内部拆句。",relatedId="KAF-P-STRUM",version="第二次收尾"),
        exercise("KAF-V-STRUM-KEY","KAF-K-117-G1","连续和弦节奏（转调版）",ACCOMPANIMENT,"117–136",
            "完整经过转调进入、三连音和后续和声推进。",relatedId="KAF-P-STRUM",version="转调完整版"),
        exercise("KAF-V-RIFF-END","KAF-L-137-G3","主题 riff（结尾版）",MELODY,"137–145",
            "保留主题再现及第145小节的最后长音滑出，收尾不另列一个音的练习。",
            sequence=joined(source("KAF-L-137-G3").sequence,withoutSilentTail(source("KAF-M-145-G3").sequence,1)),
            relatedId="KAF-P-RIFF",version="再现与收尾"),
        exercise("KAF-V-MELODY-REPEAT","KAF-E-61-G2","完整旋律段（再现版）",MELODY,"61–76",
            "保留第二次旋律的完整结束，与标准段对照练习。",relatedId="KAF-P-MELODY",version="第二次收尾"),
        exercise("KAF-V-MELODY-KEY","KAF-K-117-G2","完整旋律段（转调版）",MELODY,"117–136",
            "完整经过转调旋律、三连音和独立尾句。",relatedId="KAF-P-MELODY",version="转调完整版"),
    )
    val all=core+variants
    private val replacements=mapOf(
        "KAF-A-1-G1" to "KAF-P-POWER", "KAF-B-9-G2" to "KAF-P-ARPEGGIO", "KAF-B-9-G3" to "KAF-P-RIFF",
        "KAF-C-17-G1" to "KAF-P-POWER", "KAF-C-49-G2" to "KAF-P-RIFF", "KAF-D-25-G1" to "KAF-P-STRUM",
        "KAF-D-25-G2" to "KAF-P-RIFF", "KAF-E-29-G1" to "KAF-P-STRUM", "KAF-E-29-G2" to "KAF-P-MELODY",
        "KAF-E-61-G1" to "KAF-V-STRUM-REPEAT", "KAF-E-61-G2" to "KAF-V-MELODY-REPEAT", "KAF-F-45-G1" to "KAF-P-POWER",
        "KAF-F-45-G2" to "KAF-P-SOLO", "KAF-F-45-G3" to "KAF-P-RIFF", "KAF-G-57-G2" to "KAF-V-MELODY-REPEAT",
        "KAF-H-77-G1" to "KAF-P-STRUM", "KAF-H-77-G2" to "KAF-P-SOLO", "KAF-I-82-G1" to "KAF-P-STRUM",
        "KAF-I-82-G2" to "KAF-P-SOLO", "KAF-I-82-G3" to "KAF-P-SOLO", "KAF-J-100-G1" to "KAF-P-BASS",
        "KAF-J-100-G2" to "KAF-P-BASS", "KAF-K-117-G1" to "KAF-V-STRUM-KEY", "KAF-K-117-G2" to "KAF-V-MELODY-KEY",
        "KAF-L-137-G1" to "KAF-V-POWER-END", "KAF-L-137-G3" to "KAF-V-RIFF-END",
        "KAF-M-145-G1" to "KAF-V-POWER-END", "KAF-M-145-G3" to "KAF-V-RIFF-END",
    )
    fun replacement(id: String)=replacements[id]
    fun previousSelection(id: String)=replacements.entries.firstOrNull { it.value==id }?.key
}
