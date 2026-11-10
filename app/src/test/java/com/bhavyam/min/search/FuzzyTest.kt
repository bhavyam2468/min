package com.bhavyam.min.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The matching rules from the spec, one assertion each. */
class FuzzyTest {

    private fun matches(query: String, name: String) = Fuzzy.score(query.lowercase(), FuzzyKey(name)) != Fuzzy.NONE

    private fun rank(query: String, names: List<String>): List<String> {
        val keys = names.associateWith(::FuzzyKey)
        val out = ArrayList<String>()
        Ranker().rank(query, names, { keys.getValue(it) }, out)
        return out
    }

    @Test fun substringMatchesIgnoringCase() {
        assertTrue(matches("chatgpt", "ChatGPT"))
        assertTrue(matches("CHAT", "ChatGPT"))
        assertTrue(matches("gpt", "ChatGPT"))
        assertTrue(matches("tube", "YouTube"))
        assertTrue(matches("oogle", "Google Maps"))
    }

    @Test fun oneTypoIsForgiven() {
        assertTrue(matches("chastgpt", "ChatGPT")) // extra letter
        assertTrue(matches("chtgpt", "ChatGPT"))   // missing letter
        assertTrue(matches("xhatgpt", "ChatGPT"))  // wrong letter
        assertTrue(matches("chatgptx", "ChatGPT"))
        assertTrue(matches("yoi", "YouTube"))      // half-typed word with a typo
        assertTrue(matches("mapz", "Google Maps")) // typo at the start of a later word
    }

    @Test fun twoTyposNeverMatch() {
        assertFalse(matches("cshtgpt", "ChatGPT"))
        assertFalse(matches("ctgpt", "ChatGPT"))
        assertFalse(matches("chatgpxx", "ChatGPT"))
        assertFalse(matches("mpas", "Maps"))       // a swap is two edits
    }

    @Test fun noLooseSubsequences() {
        assertFalse(matches("yt", "YouTube"))
        assertFalse(matches("cl", "Calculator"))
        assertFalse(matches("ab", "Calculator"))
    }

    /** One extra letter in front of a word is one insertion: "g" + "maps". */
    @Test fun extraLetterBeforeAWordIsOneTypo() = assertTrue(matches("gmaps", "Google Maps"))

    @Test fun exactBeatsPrefixBeatsWordShorterFirst() {
        val names = listOf("Google Chat", "ChatGPT", "Chat", "Chrome", "WhatsApp", "Hangouts Chat Beta")
        assertEquals(listOf("Chat", "ChatGPT", "Google Chat", "Hangouts Chat Beta", "WhatsApp"), rank("chat", names))
    }

    @Test fun typoMatchesRankBelowSubstrings() {
        // "Crome Tools" contains the query; the Chromes are one letter off
        assertEquals(listOf("Crome Tools", "Chrome", "Chromecast"), rank("crome", listOf("Chrome", "Chromecast", "Crome Tools")))
    }

    @Test fun typingNeverFlickersToNoMatch() {
        for (name in listOf("ChatGPT", "Google Maps", "Spotify")) {
            val typed = StringBuilder()
            for (c in name.lowercase()) {
                typed.append(c)
                assertTrue("'$typed' should still match $name", matches(typed.toString(), name))
            }
        }
    }

    @Test fun tiesKeepSourceOrder() {
        // apps arrive most-recent-first; equal scores must not reshuffle them
        assertEquals(listOf("Maps B", "Maps A"), rank("maps", listOf("Maps B", "Maps A")))
    }
}
