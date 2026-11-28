package com.keepsakeai.app.explore

/**
 * Pilot answers saved onto a card. They live at the end of the card's text, each under a marker line
 * "[pilot · 28 Nov 2026] the question", so search, backups and Pilot's own context see them with no schema change.
 * Pure and unit-tested.
 */
object Explorations {
    data class Item(val date: String, val question: String, val answer: String)

    private val marker = Regex("\n\n\\[pilot · ([^\\]\n]{1,30})] ([^\n]*)\n")

    /** The card's own text, and the explorations saved under it. */
    fun split(raw: String): Pair<String, List<Item>> {
        val ms = marker.findAll(raw).toList()
        if (ms.isEmpty()) return raw to emptyList()
        val items = ms.mapIndexed { i, m ->
            val end = ms.getOrNull(i + 1)?.range?.first ?: raw.length
            Item(m.groupValues[1], m.groupValues[2], raw.substring(m.range.last + 1, end).trim())
        }
        return raw.substring(0, ms.first().range.first) to items
    }

    fun join(base: String, items: List<Item>): String =
        base.trimEnd() + items.joinToString("") { "\n\n[pilot · ${it.date}] ${oneLine(it.question)}\n${it.answer.trim()}" }

    fun add(raw: String, item: Item): String = split(raw).let { (base, items) -> join(base, items + item) }

    fun remove(raw: String, index: Int): String = split(raw).let { (base, items) -> join(base, items.filterIndexed { i, _ -> i != index }) }

    /** Tidies an answer for keeping: drops [n] citations (they only make sense in the chat) and map markers. */
    fun clean(answer: String): String =
        answer.replace(Regex("\\s?\\[\\d{1,2}]"), "").replace(Regex("\n{3,}"), "\n\n").trim()

    /** Text to embed for search: the card, then what was explored (questions first, they say what it's about). */
    fun embedText(title: String, summary: String, raw: String, tags: String): String {
        val (base, items) = split(raw)
        return "$title $summary ${base.take(400)} ${items.joinToString(" ") { it.question }} ${items.joinToString(" ") { it.answer.take(200) }} $tags"
    }

    private fun oneLine(s: String) = s.replace(Regex("\\s+"), " ").trim().take(200).ifBlank { "question" }
}
