package dev.bhavyam.min.search

import java.util.Arrays

/**
 * Scores a list with [FuzzyMatcher] and appends the matches best-first, ties kept in list order.
 * Score and index are packed into one long so a primitive sort does the work: no allocation per
 * keystroke once the buffer has grown to the list size.
 */
class Ranker<T : SearchResult>(private val keyOf: (T) -> SearchKey) {

    private var packed = LongArray(INITIAL_CAPACITY)

    fun rank(query: String, items: List<T>, out: MutableList<SearchResult>) {
        if (packed.size < items.size) packed = LongArray(items.size)
        var n = 0
        for (i in items.indices) {
            val score = FuzzyMatcher.score(query, keyOf(items[i]))
            if (score != FuzzyMatcher.NO_MATCH) packed[n++] = (score.toLong() shl 32) or (Int.MAX_VALUE - i).toLong()
        }
        Arrays.sort(packed, 0, n)
        for (k in n - 1 downTo 0) out += items[Int.MAX_VALUE - (packed[k] and 0xFFFFFFFFL).toInt()]
    }

    private companion object {
        const val INITIAL_CAPACITY = 256
    }
}
