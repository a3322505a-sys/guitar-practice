package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.assets.song.LibrarySnapshot
import com.a3322505a.guitarlearning.music.TeachingModule
import com.a3322505a.guitarlearning.music.Chord
import com.a3322505a.guitarlearning.music.Pattern
import com.a3322505a.guitarlearning.music.Tonality
import com.a3322505a.guitarlearning.practice.Phrase
import com.a3322505a.guitarlearning.practice.ScaleExercise

internal const val BASIC_CONTENT = "__basic__"

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
        ?.takeUnless { it.startsWith("local.teaching-") } ?: BASIC_CONTENT

    fun sourceOf(chord: Chord): String = encodedPackageId(chord.id)
        ?.takeUnless { it.startsWith("local.teaching-") } ?: BASIC_CONTENT

    fun restoredSource(requested: String, ready: Boolean): String =
        if (!ready || sources.any { it.id == requested }) requested else BASIC_CONTENT

    fun effectiveSource(requested: String, songs: Boolean): String = requested.takeIf { id ->
        if (songs) phrases.any { sourceOf(it) == id } else chords.any { sourceOf(it) == id }
    } ?: BASIC_CONTENT

    fun phraseSections(source: String, module: TeachingModule? = null): List<ContentSection<Phrase>> {
        val filtered = phrases.filter { sourceOf(it) == source && (source != BASIC_CONTENT || module == null || moduleOf(it) == module) }
        if (source == BASIC_CONTENT) return filtered.groupBy { it.lesson?.module?.title ?: it.use }.map { ContentSection(it.key,it.value) }
        return filtered.groupBy { sectionTitle(it.use, source) }.map { ContentSection(it.key, it.value) }
    }

    fun moduleOf(phrase: Phrase) = phrase.lesson?.module ?: TeachingModule.APPLICATION
    fun chordSections(source: String): List<ContentSection<Chord>> {
        val filtered = com.a3322505a.guitarlearning.music.ChordSlots.grouped(chords.filter { sourceOf(it) == source })
        if (source == BASIC_CONTENT) return filtered.groupBy { it.group }.map { ContentSection(it.key,it.value) }
        return filtered.groupBy { sectionTitle(it.group, source) }
        .map { ContentSection(it.key, it.value) }
    }

    fun phraseText(phrase: Phrase): ContentCardText {
        val source = sourceOf(phrase)
        if (source != BASIC_CONTENT) return ContentCardText(stripPrefix(phrase.title, packageNames[source]))
        return ContentCardText(phrase.title)
    }

    fun chordText(chord: Chord): ContentCardText {
        val source = sourceOf(chord)
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
