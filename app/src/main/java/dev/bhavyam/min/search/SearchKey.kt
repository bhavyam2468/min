package dev.bhavyam.min.search

import java.util.Locale

/**
 * A name prepared for matching once, so per-keystroke matching never allocates:
 * the lowercased text plus the index where every word starts.
 */
class SearchKey(name: String) {
    val text: String = name.lowercase(Locale.ROOT)
    val wordStarts: IntArray = wordStartsOf(text)

    private companion object {
        fun wordStartsOf(s: String): IntArray {
            var count = 0
            for (i in s.indices) if (isWordStart(s, i)) count++
            val starts = IntArray(count)
            var n = 0
            for (i in s.indices) if (isWordStart(s, i)) starts[n++] = i
            return starts
        }

        fun isWordStart(s: String, i: Int) =
            s[i].isLetterOrDigit() && (i == 0 || !s[i - 1].isLetterOrDigit())
    }
}
