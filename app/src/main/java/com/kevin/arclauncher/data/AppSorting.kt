package com.kevin.arclauncher.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Bucket for labels that don't start with a Latin letter (digits, symbols, other scripts). */
const val NON_LETTER_SECTION = "#"

/** The fast-scroll index, top to bottom. */
val SECTION_INDEX: List<String> = listOf(NON_LETTER_SECTION) + ('A'..'Z').map { it.toString() }

data class Section<T>(val key: String, val items: List<T>)

/**
 * "Évernote" -> "E", "7-Zip" -> "#", "  maps" -> "M".
 * Accents are stripped with Unicode NFD so they file under their base letter.
 */
fun sectionKeyFor(label: String): String {
    val first = label.trimStart().firstOrNull() ?: return NON_LETTER_SECTION
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercaseChar()
    return if (base in 'A'..'Z') base.toString() else NON_LETTER_SECTION
}

fun sectionOrder(key: String): Int = SECTION_INDEX.indexOf(key)

/** Groups items into "#", "A".."Z" sections, each sorted with a locale-aware, case-insensitive Collator. */
fun <T> buildSections(
    items: List<T>,
    label: (T) -> String,
    locale: Locale = Locale.getDefault(),
): List<Section<T>> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return items.groupBy { sectionKeyFor(label(it)) }
        .map { (key, group) -> Section(key, group.sortedWith { a, b -> collator.compare(label(a), label(b)) }) }
        .sortedBy { sectionOrder(it.key) }
}

/** Case-insensitive substring match; also matches word initials, e.g. "gm" finds "Google Maps". */
fun matchesQuery(label: String, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (label.contains(q, ignoreCase = true)) return true
    val initials = label.split(' ', '-', '_', '.').filter { it.isNotEmpty() }.joinToString("") { it.first().toString() }
    return initials.startsWith(q, ignoreCase = true)
}
