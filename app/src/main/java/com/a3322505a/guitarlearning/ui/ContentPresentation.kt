package com.a3322505a.guitarlearning.ui

import com.a3322505a.guitarlearning.assets.song.LibrarySnapshot
import com.a3322505a.guitarlearning.music.Chord
import com.a3322505a.guitarlearning.practice.Phrase

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
        ?.takeUnless { it == MOZART_PACKAGE } ?: BASIC_CONTENT

    fun sourceOf(chord: Chord): String = encodedPackageId(chord.id)
        ?.takeUnless { it == MOZART_PACKAGE } ?: BASIC_CONTENT

    fun restoredSource(requested: String, ready: Boolean): String =
        if (!ready || sources.any { it.id == requested }) requested else BASIC_CONTENT

    fun effectiveSource(requested: String, songs: Boolean): String = requested.takeIf { id ->
        if (songs) phrases.any { sourceOf(it) == id } else chords.any { sourceOf(it) == id }
    } ?: BASIC_CONTENT

    fun phraseSections(source: String): List<ContentSection<Phrase>> = phrases.filter { sourceOf(it) == source }
        .groupBy { phrase ->
            when {
                phrase.assetKey?.packageId == MOZART_PACKAGE -> "单音"
                source == BASIC_CONTENT && phrase.use in listOf("简单和弦节奏", "综合片段") -> "节奏与综合"
                source == BASIC_CONTENT -> phrase.use
                else -> sectionTitle(phrase.use, source)
            }
        }.map { ContentSection(it.key, it.value) }

    fun chordSections(source: String): List<ContentSection<Chord>> = chords.filter { sourceOf(it) == source }
        .groupBy { if (source == BASIC_CONTENT) it.group else sectionTitle(it.group, source) }
        .map { ContentSection(it.key, it.value) }

    fun phraseText(phrase: Phrase): ContentCardText {
        if (phrase.assetKey?.packageId == MOZART_PACKAGE) {
            // Product display whitelist; the original K265-B title/data stay unchanged.
            val title = when (phrase.assetKey.practiceId) {
                "K265-A" -> "1 主题"
                "K265-B" -> "2 变奏"
                "K265-C" -> "3 再现与结尾"
                else -> stripPrefix(phrase.title, "莫扎特 K265")
            }
            return ContentCardText(title, "莫扎特 K265")
        }
        builtinText[phrase.title]?.let { return it }
        val block = Regex("七个Block · (\\d+) (.+)").matchEntire(phrase.title)
        if (block != null) return ContentCardText(block.groupValues[2], "Block " + block.groupValues[1].padStart(2, '0'))
        val source = sourceOf(phrase)
        if (source != BASIC_CONTENT) return ContentCardText(stripPrefix(phrase.title, packageNames[source]))
        // Recognized technique labels carry information even though they are not section names.
        val technique = listOf("三连音", "P.M.").firstOrNull { phrase.title.startsWith("$it · ") }
        return if (technique != null) ContentCardText(stripPrefix(phrase.title, technique), technique)
            else ContentCardText(phrase.title)
    }

    fun chordText(chord: Chord): ContentCardText {
        val title = stripPrefix(chord.name, packageNames[sourceOf(chord)])
        val split = title.lastIndexOf(" · ")
        return if (split > 0 && title.substring(split + 3).contains("品"))
            ContentCardText(title.substring(0, split), title.substring(split + 3)) else ContentCardText(title)
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

private val builtinText = mapOf(
    "单音短句 · C 大调小句" to ContentCardText("C 大调小句", "入门短句"),
    "莫扎特 K265 · 1 主题" to ContentCardText("1 主题", "莫扎特 K265"),
    "莫扎特 K265 · 2 变奏" to ContentCardText("2 变奏", "莫扎特 K265"),
    "莫扎特 K265 · 3 再现与结尾" to ContentCardText("3 再现与结尾", "莫扎特 K265"),
    "滑音 · 5/7 与 7\\5" to ContentCardText("5/7 与 7\\5", "滑音"),
    "击弦 · 5h7" to ContentCardText("5h7", "击弦"),
    "勾弦 · 7p5" to ContentCardText("7p5", "勾弦"),
    "节奏句 · 基础版" to ContentCardText("基础版", "简单和弦节奏"),
    "节奏句 · 完整版" to ContentCardText("完整版", "综合片段"),
)
