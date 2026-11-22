package com.ritvik.passvault.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzySearchTest {
    private val amazon = listOf("Amazon", "me@mail.com", "amazon.com")
    private val gmail = listOf("Gmail", "ritvik@gmail.com", "google.com")

    @Test fun plainSubstringsAndPrefixesMatch() {
        assertTrue(FuzzySearch.matches("ama", amazon))
        assertTrue(FuzzySearch.matches("MAIL", amazon))
        assertTrue(FuzzySearch.matches("ritvik", gmail))
    }

    @Test fun toleratesTyposAndSwaps() {
        assertTrue(FuzzySearch.matches("amazn", amazon))
        assertTrue(FuzzySearch.matches("amzaon", amazon))
        assertTrue(FuzzySearch.matches("amaazon", amazon))
        assertTrue(FuzzySearch.matches("gogle", gmail))
    }

    @Test fun lettersInOrderMatchFromTheStartOfAWord() {
        assertTrue(FuzzySearch.matches("gml", gmail))
        assertTrue(FuzzySearch.matches("amzn", amazon))
        assertFalse(FuzzySearch.matches("mzn", amazon)) // must start where a word starts
    }

    @Test fun unrelatedTextDoesNotMatch() {
        assertFalse(FuzzySearch.matches("netflix", amazon))
        assertFalse(FuzzySearch.matches("xyz", gmail))
        assertFalse(FuzzySearch.matches("ab", amazon.drop(1).take(1))) // too short for fuzzy matching
    }

    @Test fun everyWordMustMatch() {
        assertTrue(FuzzySearch.matches("amazon me", amazon))
        assertFalse(FuzzySearch.matches("amazon netflix", amazon))
    }

    @Test fun betterMatchesRankHigher() {
        val whole = FuzzySearch.score("amazon", amazon)
        val prefix = FuzzySearch.score("amaz", amazon)
        val typo = FuzzySearch.score("amazn", listOf("amazon"))
        val loose = FuzzySearch.score("amzn", amazon)
        assertTrue(whole > prefix)
        assertTrue(prefix > typo)
        assertTrue(typo > loose)
    }

    @Test fun blankQueryMatchesEverything() {
        assertEquals(1, FuzzySearch.score("   ", amazon))
        assertEquals(1, FuzzySearch.score("", emptyList()))
        assertEquals(0, FuzzySearch.score("a", emptyList()))
    }

    @Test fun distanceCountsSwapAsOneEdit() {
        assertEquals(1, FuzzySearch.distance("ab", "ba", 2))
        assertEquals(1, FuzzySearch.distance("amazon", "amazn", 2))
        assertEquals(3, FuzzySearch.distance("abc", "xyz", 2))
    }
}
