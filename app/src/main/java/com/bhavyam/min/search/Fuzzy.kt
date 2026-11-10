package com.bhavyam.min.search

import java.util.Arrays

/**
 * A name prepared for matching once, when it is indexed: lowercased char by char (so indices
 * line up with the label) plus the positions where words start — after a separator, or at a
 * lower→upper case change, so "ChatGPT" has words at "Chat" and "GPT".
 */
class FuzzyKey(label: String) {
    val text: String
    val starts: IntArray

    init {
        val chars = CharArray(label.length)
        val found = IntArray(label.length)
        var n = 0
        for (i in label.indices) {
            val c = label[i]
            chars[i] = c.lowercaseChar()
            val prev = if (i == 0) ' ' else label[i - 1]
            val atWord = c.isLetterOrDigit() &&
                (i == 0 || !prev.isLetterOrDigit() || (prev.isLowerCase() && c.isUpperCase()))
            if (atWord) found[n++] = i
        }
        text = String(chars)
        starts = found.copyOf(n)
    }

    fun isWordStart(i: Int): Boolean = starts.binarySearch(i) >= 0
}

/**
 * The matching rules, strict on purpose:
 *  1. Case-insensitive.
 *  2. An exact substring always wins. Whole name > start of name > start of a word > anywhere,
 *     and a shorter name beats a longer one inside the same tier.
 *  3. Otherwise exactly one typo is forgiven — one wrong, missing or extra letter — against the
 *     start of the name or the start of any word, including a prefix of it so a half-typed word
 *     still matches. Never two. Typo matches always rank below every substring match.
 *  4. No loose subsequence matching. A typo needs context, so it only applies from
 *     [MIN_TYPO_QUERY] letters: with fewer, every name is one edit away from a match.
 *
 * Every rule depends only on the query and the name, so results are stable while typing.
 */
object Fuzzy {
    const val NONE = -1
    const val MIN_TYPO_QUERY = 3

    private const val EXACT = 6
    private const val PREFIX = 5
    private const val WORD = 4
    private const val SUBSTRING = 3
    private const val TYPO_PREFIX = 2
    private const val TYPO_WORD = 1
    private const val TIER = 1000
    private const val MAX_LENGTH = TIER - 1

    /** Score for an already-lowercased [query]; higher is better, [NONE] means no match. */
    fun score(query: String, key: FuzzyKey): Int {
        val t = key.text
        if (query.isEmpty()) return 0
        val shortness = MAX_LENGTH - t.length.coerceAtMost(MAX_LENGTH)
        var at = t.indexOf(query)
        if (at >= 0) {
            if (query.length == t.length) return EXACT * TIER + shortness
            if (at == 0) return PREFIX * TIER + shortness
            while (at >= 0) {
                if (key.isWordStart(at)) return WORD * TIER + shortness
                at = t.indexOf(query, at + 1)
            }
            return SUBSTRING * TIER + shortness
        }
        if (query.length < MIN_TYPO_QUERY) return NONE
        if (oneEditFromPrefix(query, t, 0)) return TYPO_PREFIX * TIER + shortness
        for (s in key.starts) {
            if (s > 0 && oneEditFromPrefix(query, t, s)) return TYPO_WORD * TIER + shortness
        }
        return NONE
    }

    /** Is [q] within one edit of some prefix of `t[from..]`? Only prefixes of |q|±1 can be. */
    private fun oneEditFromPrefix(q: String, t: String, from: Int): Boolean {
        val available = t.length - from
        for (len in q.length - 1..q.length + 1) {
            if (len in 1..available && withinOneEdit(q, t, from, len)) return true
        }
        return false
    }

    /** Levenshtein distance ≤ 1 between [a] and `b[from, from + len)`, in O(n), no allocation. */
    private fun withinOneEdit(a: String, b: String, from: Int, len: Int): Boolean {
        val la = a.length
        if (la == len) {
            var diff = 0
            for (i in 0 until la) if (a[i] != b[from + i] && ++diff > 1) return false
            return true
        }
        // lengths differ by one: walk both, letting the longer side skip a single char
        val aIsLonger = la > len
        val shortLen = if (aIsLonger) len else la
        var i = 0 // index in the shorter
        var j = 0 // index in the longer
        var skipped = false
        while (i < shortLen) {
            val short = if (aIsLonger) b[from + i] else a[i]
            val long = if (aIsLonger) a[j] else b[from + j]
            if (short == long) {
                i++
                j++
            } else {
                if (skipped) return false
                skipped = true
                j++
            }
        }
        return true // at most one unmatched char is left at the end of the longer side
    }
}

/**
 * Ranks items by [Fuzzy.score] into a caller-owned list, reusing one packed buffer so a
 * keystroke allocates nothing here. Ties keep the input order, so the source decides the
 * tie-break (apps: most recently used first; contacts: alphabetical).
 */
class Ranker {
    @PublishedApi internal var packed = LongArray(256)

    inline fun <T> rank(query: String, items: List<T>, key: (T) -> FuzzyKey, out: MutableList<in T>) {
        val n = items.size
        if (packed.size < n) packed = LongArray(n * 2)
        var m = 0
        for (i in 0 until n) {
            val s = Fuzzy.score(query, key(items[i]))
            // score in the high bits, inverted index in the low bits: one ascending sort orders
            // by score and keeps the original order among equal scores
            if (s != Fuzzy.NONE) packed[m++] = (s.toLong() shl 32) or (Int.MAX_VALUE - i).toLong()
        }
        Arrays.sort(packed, 0, m)
        for (k in m - 1 downTo 0) out.add(items[Int.MAX_VALUE - (packed[k] and 0xFFFFFFFFL).toInt()])
    }
}
