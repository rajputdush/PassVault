package com.ritvik.passvault.autofill

import com.ritvik.passvault.cards.CardInfo

/** The parts of a payment-card form PassVault knows how to fill. */
enum class CardFieldKind { NUMBER, EXPIRY, EXP_MONTH, EXP_YEAR, CVV, HOLDER }

/**
 * Recognises payment-card boxes in a form. Android's own autofill hints are trusted first, then the HTML
 * `autocomplete` attribute, then the field's name or id as a last resort.
 */
object CardFieldClassifier {
    // The values of View.AUTOFILL_HINT_CREDIT_CARD_*, spelled out so this stays plain JVM code.
    private const val HINT_NUMBER = "creditcardnumber"
    private const val HINT_CVV = "creditcardsecuritycode"
    private const val HINT_EXPIRY = "creditcardexpirationdate"
    private const val HINT_MONTH = "creditcardexpirationmonth"
    private const val HINT_YEAR = "creditcardexpirationyear"
    private const val HINT_DAY = "creditcardexpirationday"

    /**
     * @param hints the view's autofill hints (Android hints or HTML autocomplete tokens)
     * @param names every name-like string on the field (view id, HTML name, HTML id, placeholder), in any order
     * @return what the field is, or null if it is not a card field (or is the expiry day, which PassVault never has)
     */
    fun classify(hints: List<String>, names: List<String>): CardFieldKind? {
        val tokens = hints.flatMap { it.lowercase().split(' ', '\t', ',') }.filter { it.isNotBlank() }
        for (t in tokens) {
            when (t.filter { it.isLetterOrDigit() }) {
                HINT_NUMBER, "ccnumber" -> return CardFieldKind.NUMBER
                HINT_CVV, "cccsc" -> return CardFieldKind.CVV
                HINT_EXPIRY, "ccexp" -> return CardFieldKind.EXPIRY
                HINT_MONTH, "ccexpmonth" -> return CardFieldKind.EXP_MONTH
                HINT_YEAR, "ccexpyear" -> return CardFieldKind.EXP_YEAR
                HINT_DAY -> return null
                "ccname", "ccgivenname", "ccfamilyname", "ccadditionalname" -> return CardFieldKind.HOLDER
            }
        }
        val joined = names.joinToString(" ") { it.lowercase().filter { c -> c.isLetterOrDigit() } }
        if (joined.isBlank()) return null
        return fromNames(joined)
    }

    private fun has(text: String, vararg parts: String) = parts.any { it in text }

    private fun fromNames(n: String): CardFieldKind? {
        // "security code" is deliberately not matched by name: sign-in pages use it for one-time codes.
        if (has(n, "cvv", "cvc", "csc", "cardcode", "cardverification")) return CardFieldKind.CVV
        if (has(n, "expir", "expdate", "expmonth", "expyear", "validthru", "validuntil", "validto", "ccexp", "cardexp", "expmm", "expyy")) {
            val month = has(n, "month") || n.endsWith("mm") || has(n, "expmm")
            val year = has(n, "year") || n.endsWith("yy") || n.endsWith("yyyy") || has(n, "expyy")
            return when {
                month && !year -> CardFieldKind.EXP_MONTH
                year && !month -> CardFieldKind.EXP_YEAR
                else -> CardFieldKind.EXPIRY
            }
        }
        if (has(n, "cardholder", "nameoncard", "cardname", "ccname", "holdername", "cardowner", "nameonthecard")) return CardFieldKind.HOLDER
        if (has(n, "cardnumber", "cardno", "cardnum", "ccnumber", "ccnum", "creditcard", "debitcard", "paymentcard")) return CardFieldKind.NUMBER
        return null
    }

    /**
     * A form is a card form when it asks for the card number, or for a security code together with an expiry.
     * A lone cardholder, expiry or security-code box is not enough, so ordinary sign-in, one-time-code and address
     * forms are never mistaken for one.
     */
    fun isCardForm(kinds: Collection<CardFieldKind>): Boolean {
        if (CardFieldKind.NUMBER in kinds) return true
        val hasExpiry = CardFieldKind.EXPIRY in kinds || CardFieldKind.EXP_MONTH in kinds || CardFieldKind.EXP_YEAR in kinds
        return CardFieldKind.CVV in kinds && hasExpiry
    }
}

/** Turns the expiry saved on a card into what each kind of box expects. */
object CardFill {
    private val MONTH_NAMES = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )

    /** "08" for August, or null if the saved expiry is not a real date. */
    fun month(expiry: String): String? =
        CardInfo.parseExpiry(expiry)?.let { (m, _) -> m.toString().padStart(2, '0') }

    /** The year with 2 or 4 digits ("28" or "2028"), or null if the saved expiry is not a real date. */
    fun year(expiry: String, digits: Int = 4): String? {
        val (_, y) = CardInfo.parseExpiry(expiry) ?: return null
        return if (digits == 2) (y % 100).toString().padStart(2, '0') else y.toString()
    }

    /**
     * One box for the whole expiry. [maxLength] is the box's length limit when it has one: 4 means "MMYY",
     * 6 means "MMYYYY", 5 and anything unknown mean "MM/YY", 7 or more mean "MM/YYYY".
     */
    fun combined(expiry: String, maxLength: Int? = null): String? {
        val m = month(expiry) ?: return null
        return when {
            maxLength == null || maxLength <= 0 -> "$m/${year(expiry, 2)}"
            maxLength == 4 -> m + year(expiry, 2)
            maxLength == 5 -> "$m/${year(expiry, 2)}"
            maxLength == 6 -> m + year(expiry, 4)
            maxLength >= 7 -> "$m/${year(expiry, 4)}"
            else -> null
        }
    }

    /**
     * For a drop-down of months or years, the position of the right entry, or null if none fits. Months can be
     * written "08", "8", "August", "Aug" or "08 - August"; years "2028" or "28".
     */
    fun pickOption(options: List<String>, kind: CardFieldKind, expiry: String): Int? {
        val (month, year) = CardInfo.parseExpiry(expiry) ?: return null
        return when (kind) {
            CardFieldKind.EXP_MONTH -> options.indexOfFirst { monthOf(it) == month }.takeIf { it >= 0 }
            CardFieldKind.EXP_YEAR -> options.indexOfFirst { yearOf(it) == year }.takeIf { it >= 0 }
            else -> null
        }
    }

    private fun monthOf(option: String): Int? {
        val t = option.trim().lowercase()
        val lead = t.takeWhile { it.isDigit() }
        if (lead.isNotEmpty() && lead.length <= 2) return lead.toIntOrNull()?.takeIf { it in 1..12 }
        val idx = MONTH_NAMES.indexOfFirst { t.startsWith(it) || t.startsWith(it.take(3)) }
        return if (idx >= 0) idx + 1 else null
    }

    private fun yearOf(option: String): Int? {
        val digits = Regex("\\d+").findAll(option).map { it.value }.toList()
        val four = digits.firstOrNull { it.length == 4 }?.toIntOrNull()
        if (four != null) return four
        val two = digits.firstOrNull { it.length == 2 }?.toIntOrNull()
        return two?.let { 2000 + it }
    }
}
