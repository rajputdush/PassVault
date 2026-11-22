package com.ritvik.passvault.search

/**
 * Forgiving search for a list of a few hundred logins: finds "amazon" from "amzaon" or "amzn", and "gmail" from "gml".
 * Every word you type has to match something; better matches (whole word, start of a word) rank higher.
 */
object FuzzySearch {
    private const val MAX_FIELD_LENGTH = 200
    private const val MAX_TOKENS = 6

    /** 0 means "does not match"; otherwise higher is a better match. A blank query matches everything with score 1. */
    fun score(query: String, fields: List<String>): Int {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return 1
        val prepared = fields.filter { it.isNotBlank() }.map { Prepared(it.take(MAX_FIELD_LENGTH).lowercase()) }
        if (prepared.isEmpty()) return 0
        var total = 0
        for (token in tokens) {
            var best = 0
            for (field in prepared) {
                best = maxOf(best, tokenScore(token, field))
                if (best == EXACT_WORD) break
            }
            if (best == 0) return 0
            total += best
        }
        return total
    }

    fun matches(query: String, fields: List<String>): Boolean = score(query, fields) > 0

    private const val EXACT_WORD = 100
    private const val WORD_PREFIX = 80
    private const val SUBSTRING = 60
    private const val TYPO = 40
    private const val SUBSEQUENCE = 25

    private class Prepared(val text: String) {
        val words: List<String> = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    }

    private fun tokens(query: String): List<String> =
        query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(MAX_TOKENS)

    private fun tokenScore(token: String, field: Prepared): Int {
        if (token in field.words) return EXACT_WORD
        if (field.words.any { it.startsWith(token) }) return WORD_PREFIX
        if (field.text.contains(token)) return SUBSTRING
        if (token.length < 3) return 0

        val allowed = if (token.length >= 7) 2 else if (token.length >= 4) 1 else 0
        if (allowed > 0) {
            for (word in field.words) {
                // Compare against the whole word and against a same-length start of it ("amazn" vs "amazon").
                if (distance(token, word, allowed) <= allowed) return TYPO
                if (word.length > token.length && distance(token, word.substring(0, token.length), allowed) <= allowed) return TYPO
            }
        }
        // "gml" -> "gmail": letters in order, starting at the beginning of a word.
        for (word in field.words) {
            if (word[0] == token[0] && isSubsequence(token, word)) return SUBSEQUENCE
        }
        return 0
    }

    private fun isSubsequence(token: String, word: String): Boolean {
        var i = 0
        for (c in word) {
            if (i < token.length && c == token[i]) i++
        }
        return i == token.length
    }

    /** Edit distance counting a swap of two neighbouring letters as one edit. Stops early once it exceeds [limit]. */
    internal fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        val prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        var older = prev2
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, older[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val tmp = older
            older = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
