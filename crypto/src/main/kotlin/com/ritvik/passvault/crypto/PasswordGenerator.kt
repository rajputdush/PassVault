package com.ritvik.passvault.crypto

import java.security.SecureRandom

/**
 * Generates random passwords with a cryptographically secure random source.
 * Every enabled character class appears at least once, and picks are unbiased
 * (SecureRandom.nextInt(bound) rejects values that would cause modulo bias).
 */
object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?"
    const val MAX_LENGTH = 128

    fun generate(
        length: Int = 20,
        lower: Boolean = true,
        upper: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
        random: SecureRandom = SecureRandom(),
    ): String {
        val pools = buildList {
            if (lower) add(LOWER)
            if (upper) add(UPPER)
            if (digits) add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        require(pools.isNotEmpty()) { "Select at least one character type" }
        require(length in pools.size..MAX_LENGTH) { "Length must be between ${pools.size} and $MAX_LENGTH" }

        val all = pools.joinToString("")
        val chars = CharArray(length)
        for (i in pools.indices) chars[i] = pools[i][random.nextInt(pools[i].length)]
        for (i in pools.size until length) chars[i] = all[random.nextInt(all.length)]

        // Fisher-Yates shuffle so the guaranteed characters are not always at the start.
        for (i in length - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    const val MIN_WORDS = 3
    const val MAX_WORDS = 12

    /**
     * A passphrase of random words from the 7,776-word EFF list, for example "Tumble-Rocket-Dimple-Oxygen-Salad".
     * Each word adds about 12.9 bits, so 5 words is about 65 bits and 6 words about 78 bits.
     */
    fun passphrase(
        words: Int = 5,
        separator: String = "-",
        capitalize: Boolean = true,
        addNumber: Boolean = false,
        random: SecureRandom = SecureRandom(),
    ): String {
        require(words in MIN_WORDS..MAX_WORDS) { "Use between $MIN_WORDS and $MAX_WORDS words" }
        val list = EffWordList.words
        val picked = MutableList(words) {
            val w = list[random.nextInt(list.size)]
            if (capitalize) w.replaceFirstChar { c -> c.uppercaseChar() } else w
        }
        if (addNumber) {
            val at = random.nextInt(words)
            picked[at] = picked[at] + random.nextInt(10)
        }
        return picked.joinToString(separator)
    }

    /** Entropy of a passphrase made with [words] words, in bits (the list size is public, the choice is random). */
    fun passphraseBits(words: Int): Double = words * (Math.log(EffWordList.words.size.toDouble()) / Math.log(2.0))
}
