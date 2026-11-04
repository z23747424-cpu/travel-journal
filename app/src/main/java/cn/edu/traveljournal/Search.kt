package cn.edu.traveljournal

import java.text.Normalizer
import java.util.Locale

/** Deterministic keyword/bigram baseline; this does not call a model or expand synonyms. */
fun localSearch(query: String, journals: List<Journal>): List<SearchHit> {
    val normalized = searchText(query.trim())
    if (normalized.isBlank()) return emptyList()
    val terms = Regex("[\\p{IsHan}]+|[a-z0-9]+").findAll(normalized).flatMap { match ->
        val value = match.value
        if (value.first().code > 127 && value.length > 1) value.windowed(2).asSequence()
        else sequenceOf(value)
    }.toList().distinct()
    if (terms.isEmpty()) return emptyList()
    return journals.mapNotNull { journal ->
        val title = searchText(journal.title)
        val place = searchText(journal.place)
        val tags = searchText(journal.tags.joinToString(" "))
        val content = searchText("${journal.body} ${journal.photoDescription} ${journal.date}")
        val matched = terms.filter { it in title || it in place || it in tags || it in content }
        if (matched.isEmpty()) null else {
            val score = matched.sumOf { term ->
                (if (term in title) 5 else 0) + (if (term in place) 4 else 0) +
                    (if (term in tags) 4 else 0) + (if (term in content) 1 else 0)
            } + if (normalized in "$title $place $tags $content") 8 else 0
            Triple(journal, score, matched)
        }
    }.sortedWith(compareByDescending<Triple<Journal, Int, List<String>>> { it.second }
        .thenByDescending { it.first.date }.thenBy { it.first.id })
        .map { SearchHit(it.first.id, "本地关键词匹配：${it.third.take(5).joinToString("、")}") }
}

private fun searchText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
