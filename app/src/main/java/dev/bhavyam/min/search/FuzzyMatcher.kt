package dev.bhavyam.min.search

import java.util.Locale

/**
 * The one matcher used by every list that can be searched (apps, contacts, settings).
 *
 * Rules, strongest first — every tier always outranks the next:
 *  1. the query is a prefix of the name
 *  2. the query starts at the beginning of a word inside the name
 *  3. the query appears anywhere in the name
 *  4. exactly one typo (a wrong, missing or extra letter) against a prefix of the name
 *  5. exactly one typo against a prefix of any later word
 * Inside a tier, shorter names win. No subsequence matching, ever.
 *
 * Every tier is prefix-closed: if "abcd" matches a name, "abc" does too. So as the query grows a
 * result can only drop out, never reappear — the list cannot flicker to empty and back.
 * Typos are only considered from [MIN_TYPO_QUERY] letters, where one edit is still meaningful.
 */
object FuzzyMatcher {

    const val NO_MATCH = 0
    const val MIN_TYPO_QUERY = 3

    private const val PREFIX = 5000
    private const val WORD_START = 4000
    private const val SUBSTRING = 3000
    private const val TYPO_PREFIX = 2000
    private const val TYPO_WORD = 1000
    private const val MAX_LENGTH_PENALTY = 999

    /** Turn raw input into the form [score] expects. Call once per keystroke, not per item. */
    fun normalize(query: CharSequence): String = query.trim().toString().lowercase(Locale.ROOT)

    /** Score of [key] for an already [normalize]d, non-empty [query]; [NO_MATCH] if it does not match. */
    fun score(query: String, key: SearchKey): Int {
        val s = key.text
        val lengthPenalty = minOf(s.length, MAX_LENGTH_PENALTY)
        if (s.startsWith(query)) return PREFIX - lengthPenalty
        for (w in key.wordStarts) {
            if (w > 0 && s.startsWith(query, w)) return WORD_START - lengthPenalty
        }
        if (s.contains(query)) return SUBSTRING - lengthPenalty
        if (query.length < MIN_TYPO_QUERY) return NO_MATCH
        for (w in key.wordStarts) {
            if (oneEditFromPrefix(query, s, w)) return (if (w == 0) TYPO_PREFIX else TYPO_WORD) - lengthPenalty
        }
        return NO_MATCH
    }

    /**
     * True if [q] is within one edit of some prefix of `s.substring(start)`. Only the first mismatch
     * needs checking: an edit anywhere earlier inside a run of equal letters is the same edit.
     */
    private fun oneEditFromPrefix(q: String, s: String, start: Int): Boolean {
        val n = q.length
        var i = 0
        while (i < n && start + i < s.length && q[i] == s[start + i]) i++
        if (i == n) return true
        val wrongLetter = start + n <= s.length && q.regionMatches(i + 1, s, start + i + 1, n - i - 1)
        val extraLetter = start + n - 1 <= s.length && q.regionMatches(i + 1, s, start + i, n - i - 1)
        val missingLetter = start + n + 1 <= s.length && q.regionMatches(i, s, start + i + 1, n - i)
        return wrongLetter || extraLetter || missingLetter
    }
}
