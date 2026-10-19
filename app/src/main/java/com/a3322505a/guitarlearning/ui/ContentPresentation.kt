package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.assets.song.LibrarySnapshot
import com.a3322505a.guitarlearning.music.TeachingModule
import com.a3322505a.guitarlearning.music.Chord
import com.a3322505a.guitarlearning.music.Pattern
import com.a3322505a.guitarlearning.music.Tonality
import com.a3322505a.guitarlearning.practice.Phrase
import com.a3322505a.guitarlearning.practice.ScaleExercise

internal const val BASIC_CONTENT = "__basic__"
private const val MOZART_PACKAGE = "local.mozart"

internal data class ContentCardText(val primary: String, val tag: String? = null)
internal data class ContentSource(val id: String, val title: String)
internal data class ContentSection<T>(val title: String, val items: List<T>)

/** Display projection only: identities, ordering and business objects remain intact. */
internal class ContentPresentation(snapshot: LibrarySnapshot) {
    private val packageNames = snapshot.packages.associate { it.identity.packageId to sourceTitle(it.title) }
    private val phrases = snapshot.catalogue
    private val chords = snapshot.chords
    val sources = listOf(ContentSource(BASIC_CONTENT, "基础训练")) +
        (phrases.map(::sourceOf) + chords.map(::sourceOf)).distinct().filter { it != BASIC_CONTENT }
            .sorted().map { ContentSource(it, packageNames[it] ?: it) }

    fun sourceOf(phrase: Phrase): String = phrase.assetKey?.packageId
        ?.takeUnless { it == MOZART_PACKAGE || it.startsWith("local.teaching-") } ?: BASIC_CONTENT

    fun sourceOf(chord: Chord): String = encodedPackageId(chord.id)
        ?.takeUnless { it == MOZART_PACKAGE || it.startsWith("local.teaching-") } ?: BASIC_CONTENT

    fun restoredSource(requested: String, ready: Boolean): String =
        if (!ready || sources.any { it.id == requested }) requested else BASIC_CONTENT

    fun effectiveSource(requested: String, songs: Boolean): String = requested.takeIf { id ->
        if (songs) phrases.any { sourceOf(it) == id } else chords.any { sourceOf(it) == id }
    } ?: BASIC_CONTENT

    fun phraseSections(source: String, module: TeachingModule? = null): List<ContentSection<Phrase>> {
        val filtered = phrases.filter { sourceOf(it) == source && (module == null || moduleOf(it) == module) }
        if (source == BASIC_CONTENT) return teachingSections(filtered, ::phraseTeaching, Phrase::use)
        return filtered.groupBy { sectionTitle(it.use, source) }.map { ContentSection(it.key, it.value) }
    }

    fun moduleOf(phrase: Phrase) = phrase.lesson?.module ?: if(phrase.assetKey?.packageId == MOZART_PACKAGE) TeachingModule.FOUNDATIONS else TeachingModule.APPLICATION
    fun chordSections(source: String): List<ContentSection<Chord>> {
        val filtered = com.a3322505a.guitarlearning.music.ChordSlots.grouped(chords.filter { sourceOf(it) == source })
        if (source == BASIC_CONTENT) return teachingSections(filtered, { basicChords[it.id] }, Chord::group)
        return filtered.groupBy { sectionTitle(it.group, source) }
        .map { ContentSection(it.key, it.value) }
    }

    fun phraseText(phrase: Phrase): ContentCardText {
        val source = sourceOf(phrase)
        if (source == BASIC_CONTENT) phraseTeaching(phrase)?.let { return it.text }
        if (source != BASIC_CONTENT) return ContentCardText(stripPrefix(phrase.title, packageNames[source]))
        return ContentCardText(phrase.title)
    }

    fun chordText(chord: Chord): ContentCardText {
        val source = sourceOf(chord)
        if (source == BASIC_CONTENT) basicChords[chord.id]?.let { return it.text }
        val title = stripPrefix(chord.name, packageNames[source])
        val split = title.lastIndexOf(" · ")
        return if (split > 0 && title.substring(split + 3).contains("品"))
            ContentCardText(title.substring(0, split), title.substring(split + 3))
        else ContentCardText(title, if (source != BASIC_CONTENT) chordNatureTag(title) else null)
    }

    private fun sectionTitle(raw: String, source: String): String {
        val stripped = stripPrefix(raw.removePrefix("本地曲库 · "), packageNames[source])
        return if (stripped == packageNames[source] || stripped.matches(Regex("\\d+段完整乐句"))) "练习片段" else stripped
    }
}

internal fun showContentSectionTitles(source: String, sectionCount: Int) = source == BASIC_CONTENT || sectionCount >= 2

/** PracticeKey uses a length prefix; ':' can occur inside both identity components. */
internal fun encodedPackageId(id: String): String? {
    val colon = id.indexOf(':').takeIf { it > 0 } ?: return null
    if (!id.take(colon).all(Char::isDigit)) return null
    val length = id.take(colon).toIntOrNull()?.takeIf { it > 0 } ?: return null
    val start = colon + 1
    if (length >= id.length - start) return null
    return id.substring(start, start + length)
}

internal fun sourceTitle(raw: String): String = raw.removePrefix("本地曲库 · ").trim()
    .replace(Regex(" · (\\d+段完整乐句|节奏与伴奏|旋律与连奏)$"), "")

private fun stripPrefix(raw: String, prefix: String?): String =
    if (prefix != null && raw.startsWith("$prefix · ")) raw.removePrefix("$prefix · ").takeIf { it.isNotBlank() } ?: raw else raw

private data class TeachingCard(val section: String, val text: ContentCardText, val order: Int)

private fun cards(section: String, vararg values: Triple<String, String, String>) = values.mapIndexed { i, (id, primary, tag) ->
    id to TeachingCard(section, ContentCardText(primary, tag), i)
}.toMap()

private val basicChords =
    cards("大三和弦 · 明亮主干",
        Triple("C", "C", "C调 I级 · 明亮主和弦"),
        Triple("G", "G", "G调 I级 / C调 V级 · 开阔推进"),
        Triple("D", "D", "D调 I级 · 清脆高亢"),
        Triple("A", "A", "A调 I级 · 饱满坚定"),
        Triple("E", "E", "E调 I级 · 摇滚基石")) +
    cards("小三和弦 · 柔和叙事",
        Triple("Am", "Am", "C调 vi级 · 忧郁主干"),
        Triple("Em", "Em", "G调 vi级 / C调 iii级 · 深沉极简"),
        Triple("Dm", "Dm", "C调 ii级 · 伤感铺垫")) +
    cards("色彩与转位 · 进阶修饰",
        Triple("Gsus2", "Gsus2", "挂二和弦 · 空灵悬浮 · 嘘月"),
        Triple("Asus4(no5)", "Asus4(no5)", "挂四省五 · 现代张力 · 嘘月"),
        Triple("Bm7(no5)", "Bm7(no5)", "小七省五 · 免横按过渡 · 嘘月"),
        Triple("D/F♯", "D/F#", "第一转位 · G→Em 常用桥梁"))

private val basicPhrases =
    cards("基本功热身 · 七个Block",
        Triple("SB-1", "交替拨弦热身", "Block 01 · 右手稳定度"),
        Triple("SB-2", "换弦练习", "Block 02 · 跨弦准确度"),
        Triple("SB-3", "手指组合练习", "Block 03 · 左手独立性"),
        Triple("SB-4", "节奏型练习", "Block 04 · 律动控制"),
        Triple("SB-5", "跳弦练习", "Block 05 · 大跨度拨弦"),
        Triple("SB-6", "时值切换练习", "Block 06 · 时值切换稳定性"),
        Triple("SB-7", "音阶综合热身", "Block 07 · 双手同步率")) +
    cards("专项技巧 · 触弦与控制",
        Triple("T01", "八分与十六分", "时值转换 · 三连音"),
        Triple("PM01", "普通与制音对照", "掌根闷音 · Palm Mute"),
        Triple("SL01", "滑音 5/7 与 7\\5", "左手滑音 · Slide"),
        Triple("H01", "击弦 5h7", "左手连奏 · Hammer-on"),
        Triple("P01", "勾弦 7p5", "左手连奏 · Pull-off")) +
    cards("旋律与古典练习曲", Triple("M01", "C 大调小句", "入门旋律 · 第一把位")) +
    cards("节奏与综合实战",
        Triple("R01", "节奏句（基础版）", "简单和弦节奏 · 伴奏入门"),
        Triple("R02", "节奏句（完整版）", "综合片段 · 律动进阶"))

private val mozartCards = listOf(
    "K265-A" to ContentCardText("1 主题", "莫扎特 K265 · 小星星变奏"),
    "K265-B" to ContentCardText("2 变奏", "莫扎特 K265 · 密集十六分"),
    "K265-C" to ContentCardText("3 再现与结尾", "莫扎特 K265 · 终章乐段"),
).mapIndexed { i, (id, text) -> id to TeachingCard("旋律与古典练习曲", text, i + 1) }.toMap()

private fun phraseTeaching(phrase: Phrase): TeachingCard? = when {
    phrase.assetKey?.packageId == MOZART_PACKAGE -> mozartCards[phrase.assetKey.practiceId]
    phrase.assetKey == null || phrase.assetKey.packageId == "local.teaching-legacy" -> basicPhrases[phrase.legacyIds.firstOrNull() ?: phrase.id]
    else -> null
}

private fun <T> teachingSections(items: List<T>, teaching: (T) -> TeachingCard?, fallback: (T) -> String): List<ContentSection<T>> {
    val order = listOf("大三和弦 · 明亮主干", "小三和弦 · 柔和叙事", "色彩与转位 · 进阶修饰",
        "基本功热身 · 七个Block", "专项技巧 · 触弦与控制", "旋律与古典练习曲", "节奏与综合实战")
    return items.groupBy { teaching(it)?.section ?: fallback(it) }.entries
        .sortedBy { order.indexOf(it.key).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        .map { (title, group) -> ContentSection(title, group.sortedBy { teaching(it)?.order ?: Int.MAX_VALUE }) }
}

internal fun tonalityText(key: Tonality) = ContentCardText(key.title, when (key) {
    Tonality.C_MAJOR -> "自然大调 · 明亮开阔"
    Tonality.A_MINOR -> "自然小调 · 1 2 ♭3 4 5 ♭6 ♭7"
    else -> key.context.spelling.joinToString(" ") { it.name }
})

internal fun patternText(pattern: Pattern): ContentCardText {
    val low = pattern.positions.minOf { it.fret }
    val high = pattern.positions.maxOf { it.fret }
    val region = when (pattern.solfege) { "mi" -> "开放把位"; "sol" -> "低把位"; "la" -> "中把位"; "si" -> "中高把位"; "re" -> "高把位"; else -> "把位范围" }
    return if(pattern.caged.isBlank()) ContentCardText(pattern.solfege,"固定音位范围 · $low–${high}品") else ContentCardText("${pattern.solfege} 指型", "$region · $low–${high}品 (${pattern.caged}手型)")
}

internal fun scaleExerciseText(exercise: ScaleExercise) = ContentCardText(exercise.title, when (exercise) {
    ScaleExercise.ASCEND -> "基础记忆 · 顺阶上下行"
    ScaleExercise.ROOTS -> "听感建立 · 锁定主音位置"
    ScaleExercise.CHANGE_START -> "打破惯性 · 任意音级起奏"
    ScaleExercise.THREE -> "Solo语汇 · 三音一组推进"
    ScaleExercise.THIRDS -> "音程跨度 · 隔音交替跳进"
    ScaleExercise.CONNECTION -> "横向贯通 · 相邻把位串联"
})

internal val rangeCardTexts = listOf(ContentCardText("完整把位", "全六弦音域"), ContentCardText("一八度", "单八度核心区"))
internal val rhythmCardTexts = listOf(ContentCardText("每拍1音", "四分音符"), ContentCardText("每拍2音", "八分音符"), ContentCardText("每拍3音", "三连音"))

internal fun chordNatureTag(name: String): String? {
    val root = "[A-G][#♯b♭]?"
    return when {
        name.matches(Regex("${root}5")) -> "强力和弦 (Power Chord)"
        name.matches(Regex("${root}dim7")) -> "减七和弦"
        name.matches(Regex("${root}dim")) -> "减三和弦"
        name.matches(Regex("$root(?:m|maj|dim|aug|sus[24])?(?:[2-9]|11|13)?(?:\\((?:no[35]|add[249])\\))?/$root")) -> "指定低音 · 斜杠和弦"
        else -> null
    }
}
