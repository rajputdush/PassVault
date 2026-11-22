package com.ritvik.passvault.cards

/** Helpers for payment cards stored in the vault. Everything here works offline on the phone. */
object CardInfo {
    fun digitsOnly(number: String): String = number.filter { it.isDigit() }

    /** Luhn checksum, the check digit scheme every major card network uses. Catches most typing mistakes. */
    fun luhnValid(number: String): Boolean {
        val digits = digitsOnly(number)
        if (digits.length !in 12..19) return false
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    /** The card network, guessed from the first digits, or "Card" if unknown. */
    fun brand(number: String): String {
        val d = digitsOnly(number)
        fun starts(vararg prefixes: String) = prefixes.any { d.startsWith(it) }
        fun inRange(len: Int, range: IntRange): Boolean = d.length >= len && d.substring(0, len).toIntOrNull() in range
        return when {
            d.isEmpty() -> "Card"
            starts("34", "37") -> "American Express"
            starts("4") -> "Visa"
            inRange(2, 51..55) || inRange(4, 2221..2720) -> "Mastercard"
            starts("6521", "6522") -> "RuPay"
            starts("6011", "65") || inRange(3, 644..649) -> "Discover"
            starts("60", "81", "82", "508") -> "RuPay"
            starts("62") -> "UnionPay"
            starts("36", "38") || inRange(3, 300..305) -> "Diners Club"
            inRange(4, 3528..3589) -> "JCB"
            else -> "Card"
        }
    }

    /** Groups digits the way they are printed on the card: 4-6-5 for American Express, otherwise groups of 4. */
    fun format(number: String): String {
        val d = digitsOnly(number)
        return if (brand(d) == "American Express" && d.length == 15) {
            d.substring(0, 4) + " " + d.substring(4, 10) + " " + d.substring(10)
        } else {
            d.chunked(4).joinToString(" ")
        }
    }

    /** "•••• 1234": enough to recognise a card without showing it. */
    fun masked(number: String): String {
        val d = digitsOnly(number)
        return if (d.length < 4) "••••" else "•••• " + d.takeLast(4)
    }

    /**
     * Whether [expiry] ("MM/YY", "MM/YYYY", "MMYY" or "MM-YY") is a real month that has not passed yet.
     * A card is valid through the END of its expiry month.
     */
    fun expiryValid(expiry: String, nowYear: Int, nowMonth: Int): Boolean {
        val parsed = parseExpiry(expiry) ?: return false
        val (month, year) = parsed
        return year > nowYear || (year == nowYear && month >= nowMonth)
    }

    /** Month 1..12 and full year, or null if [expiry] is not a recognisable date. */
    fun parseExpiry(expiry: String): Pair<Int, Int>? {
        val t = expiry.trim()
        val parts = if (t.any { it == '/' || it == '-' }) {
            t.split('/', '-').map { it.trim() }
        } else if (t.length == 4 && t.all { it.isDigit() }) {
            listOf(t.substring(0, 2), t.substring(2))
        } else {
            return null
        }
        if (parts.size != 2) return null
        val month = parts[0].toIntOrNull() ?: return null
        var year = parts[1].toIntOrNull() ?: return null
        if (month !in 1..12) return null
        if (parts[1].length == 2) year += 2000 else if (parts[1].length != 4) return null
        return month to year
    }
}
