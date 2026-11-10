package dev.bhavyam.min.search

/** What the user typed: trimmed as typed (for display) and [FuzzyMatcher.normalize]d (for matching). */
class Query(raw: CharSequence) {
    val text: String = raw.trim().toString()
    val normalized: String = FuzzyMatcher.normalize(text)
    val isEmpty get() = normalized.isEmpty()
}

/**
 * One result category (a column you swipe to). Adding a category = one new implementation of this,
 * registered in [SearchEngine]'s provider list.
 */
interface SearchProvider {
    /** The category name shown right above the input. */
    val label: String

    /** Whether the column exists right now (contacts need their permission). */
    val isAvailable: Boolean

    /** Append results to [out], best match first. An empty query means "browse". */
    fun query(query: Query, out: MutableList<SearchResult>)
}
