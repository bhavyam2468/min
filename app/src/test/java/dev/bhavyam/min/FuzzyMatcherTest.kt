package dev.bhavyam.min

import dev.bhavyam.min.search.FuzzyMatcher
import dev.bhavyam.min.search.SearchKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    private fun score(query: String, name: String) =
        FuzzyMatcher.score(FuzzyMatcher.normalize(query), SearchKey(name))

    private fun matches(query: String, name: String) = score(query, name) != FuzzyMatcher.NO_MATCH

    @Test
    fun oneTypoIsAllowed() {
        assertTrue(matches("chastgpt", "ChatGPT")) // extra letter
        assertTrue(matches("chtgpt", "ChatGPT")) // missing letter
        assertTrue(matches("chatgpy", "ChatGPT")) // wrong letter
        assertTrue(matches("chst", "ChatGPT")) // wrong letter while still typing
    }

    @Test
    fun twoTyposNeverMatch() {
        assertTrue(!matches("cshtgpt", "ChatGPT"))
        assertTrue(!matches("ctgpt", "ChatGPT"))
    }

    @Test
    fun noSubsequenceMatching() {
        assertTrue(!matches("cgt", "ChatGPT"))
        assertTrue(!matches("gmal", "Google Maps") && matches("maps", "Google Maps"))
    }

    @Test
    fun caseInsensitive() = assertTrue(matches("CHAT", "chatgpt") && matches("chat", "CHATGPT"))

    @Test
    fun tiersRankInOrder() {
        val prefix = score("ma", "Maps")
        val wordStart = score("ma", "Google Maps")
        val substring = score("ap", "Maps")
        val typo = score("mapz", "Maps")
        assertTrue(prefix > wordStart && wordStart > substring && substring > typo)
        assertTrue(score("mpas", "Maps") == FuzzyMatcher.NO_MATCH || score("mpas", "Maps") < substring)
    }

    @Test
    fun shorterNameWinsTies() = assertTrue(score("cam", "Camera") > score("cam", "Camera Pro"))

    @Test
    fun typoMatchesWordStartsAndPrefixes() {
        assertTrue(matches("mpas", "Google Maps").not())
        assertTrue(matches("msps", "Google Maps")) // wrong letter at a later word
        assertTrue(matches("goggle", "Google Maps"))
    }

    @Test
    fun shortQueriesNeverTypoMatch() {
        assertTrue(!matches("xh", "ChatGPT"))
        assertEquals(FuzzyMatcher.NO_MATCH, score("zq", "Maps"))
    }

    /** As a query grows, a name may drop out of the results but never come back. */
    @Test
    fun resultsNeverFlicker() {
        val names = listOf("ChatGPT", "Chrome", "Calculator", "Google Maps", "Messages", "Camera", "Clock")
        val typed = "chastgptx"
        for (name in names) {
            var droppedOut = false
            for (end in FuzzyMatcher.MIN_TYPO_QUERY..typed.length) {
                val hit = matches(typed.substring(0, end), name)
                assertTrue("$name reappeared at ${typed.substring(0, end)}", !(droppedOut && hit))
                if (!hit) droppedOut = true
            }
        }
    }
}
