package com.forgeprom.studio.domain

import java.util.Locale

/** Local lexical retrieval over the currently visible story notes. */
object StoryContext {
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val marks = Regex("[\\p{M}ـ]")
    private fun tokens(text: String): Set<String> = words.findAll(
        marks.replace(text, "").lowercase(Locale.ROOT).replace(Regex("[أإآ]"), "ا")
    ).map { it.value }.filter { it.length >= 3 }.toSet()

    fun retrieve(brief: String, notes: String): String {
        val query = tokens(brief)
        if (query.isEmpty()) return ""
        return notes.split(Regex("[\\n.!؟]+"))
            .map(String::trim).filter(String::isNotEmpty).distinct()
            .mapIndexed { index, passage -> Triple(index, passage, tokens(passage).intersect(query).size) }
            .filter { it.third > 0 }
            .sortedWith(compareByDescending<Triple<Int, String, Int>> { it.third }.thenBy { it.first })
            .take(3).sortedBy { it.first }
            .joinToString("\n") { it.second }.take(900)
    }
}
