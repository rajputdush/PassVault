package com.ritvik.passvault.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFieldsTest {
    private fun kind(hints: List<String> = emptyList(), vararg names: String) = CardFieldClassifier.classify(hints, names.toList())

    @Test
    fun androidHintsWin() {
        assertEquals(CardFieldKind.NUMBER, kind(listOf("creditCardNumber")))
        assertEquals(CardFieldKind.CVV, kind(listOf("creditCardSecurityCode")))
        assertEquals(CardFieldKind.EXPIRY, kind(listOf("creditCardExpirationDate")))
        assertEquals(CardFieldKind.EXP_MONTH, kind(listOf("creditCardExpirationMonth")))
        assertEquals(CardFieldKind.EXP_YEAR, kind(listOf("creditCardExpirationYear")))
        assertNull(kind(listOf("creditCardExpirationDay")))
    }

    @Test
    fun htmlAutocompleteTokensWork() {
        assertEquals(CardFieldKind.NUMBER, kind(listOf("cc-number")))
        assertEquals(CardFieldKind.CVV, kind(listOf("cc-csc")))
        assertEquals(CardFieldKind.EXPIRY, kind(listOf("cc-exp")))
        assertEquals(CardFieldKind.EXP_MONTH, kind(listOf("cc-exp-month")))
        assertEquals(CardFieldKind.EXP_YEAR, kind(listOf("cc-exp-year")))
        assertEquals(CardFieldKind.HOLDER, kind(listOf("cc-name")))
        // "section-billing cc-number" style values carry several tokens.
        assertEquals(CardFieldKind.NUMBER, kind(listOf("section-billing cc-number")))
    }

    @Test
    fun hintsBeatNames() {
        assertEquals(CardFieldKind.CVV, kind(listOf("cc-csc"), "card_number"))
    }

    @Test
    fun namesAreTheFallback() {
        assertEquals(CardFieldKind.NUMBER, kind(names = arrayOf("card_number")))
        assertEquals(CardFieldKind.NUMBER, kind(names = arrayOf("edit_credit_card")))
        assertEquals(CardFieldKind.CVV, kind(names = arrayOf("card-cvv")))
        assertEquals(CardFieldKind.CVV, kind(names = arrayOf("cvc")))
        assertEquals(CardFieldKind.EXPIRY, kind(names = arrayOf("expiry_date")))
        assertEquals(CardFieldKind.EXP_MONTH, kind(names = arrayOf("exp_month")))
        assertEquals(CardFieldKind.EXP_MONTH, kind(names = arrayOf("expiryMM")))
        assertEquals(CardFieldKind.EXP_YEAR, kind(names = arrayOf("expiration-year")))
        assertEquals(CardFieldKind.EXP_YEAR, kind(names = arrayOf("expYY")))
        assertEquals(CardFieldKind.HOLDER, kind(names = arrayOf("cardholder_name")))
        assertEquals(CardFieldKind.HOLDER, kind(names = arrayOf("Name on card")))
    }

    @Test
    fun ordinaryFieldsAreNotCardFields() {
        assertNull(kind(names = arrayOf("username")))
        assertNull(kind(names = arrayOf("email")))
        assertNull(kind(names = arrayOf("full_name")))
        assertNull(kind(names = arrayOf("search")))
        assertNull(kind())
        // One-time-code boxes are called "security code" on many sign-in pages.
        assertNull(kind(names = arrayOf("security_code")))
        assertNull(kind(names = arrayOf("verification code")))
    }

    @Test
    fun cardFormNeedsNumberOrCvvWithExpiry() {
        assertTrue(CardFieldClassifier.isCardForm(listOf(CardFieldKind.NUMBER)))
        assertTrue(CardFieldClassifier.isCardForm(listOf(CardFieldKind.CVV, CardFieldKind.EXPIRY)))
        assertTrue(CardFieldClassifier.isCardForm(listOf(CardFieldKind.CVV, CardFieldKind.EXP_MONTH, CardFieldKind.EXP_YEAR)))
        assertFalse(CardFieldClassifier.isCardForm(listOf(CardFieldKind.CVV)))
        assertFalse(CardFieldClassifier.isCardForm(listOf(CardFieldKind.HOLDER)))
        assertFalse(CardFieldClassifier.isCardForm(listOf(CardFieldKind.EXPIRY, CardFieldKind.HOLDER)))
        assertFalse(CardFieldClassifier.isCardForm(emptyList()))
    }

    @Test
    fun monthAndYearParts() {
        assertEquals("08", CardFill.month("8/28"))
        assertEquals("08", CardFill.month("08/2028"))
        assertEquals("12", CardFill.month("1229"))
        assertEquals("28", CardFill.year("08/28", 2))
        assertEquals("2028", CardFill.year("08/28", 4))
        assertEquals("2031", CardFill.year("01-2031"))
        assertNull(CardFill.month("soon"))
        assertNull(CardFill.year(""))
    }

    @Test
    fun combinedFollowsBoxLength() {
        assertEquals("08/28", CardFill.combined("08/28"))
        assertEquals("08/28", CardFill.combined("08/2028", 5))
        assertEquals("0828", CardFill.combined("08/28", 4))
        assertEquals("082028", CardFill.combined("08/28", 6))
        assertEquals("08/2028", CardFill.combined("08/28", 7))
        assertEquals("08/2028", CardFill.combined("08/28", 9))
        assertNull(CardFill.combined("nope"))
        assertNull(CardFill.combined("08/28", 3))
    }

    @Test
    fun monthDropDowns() {
        val numeric = listOf("Month", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12")
        assertEquals(8, CardFill.pickOption(numeric, CardFieldKind.EXP_MONTH, "08/28"))
        val unpadded = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
        assertEquals(7, CardFill.pickOption(unpadded, CardFieldKind.EXP_MONTH, "08/28"))
        val named = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        assertEquals(7, CardFill.pickOption(named, CardFieldKind.EXP_MONTH, "08/28"))
        val short = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        assertEquals(11, CardFill.pickOption(short, CardFieldKind.EXP_MONTH, "12/28"))
        val both = listOf("01 - January", "02 - February", "03 - March", "04 - April", "05 - May", "06 - June", "07 - July", "08 - August")
        assertEquals(7, CardFill.pickOption(both, CardFieldKind.EXP_MONTH, "08/28"))
    }

    @Test
    fun yearDropDowns() {
        val full = listOf("Year", "2026", "2027", "2028", "2029")
        assertEquals(3, CardFill.pickOption(full, CardFieldKind.EXP_YEAR, "08/28"))
        val short = listOf("26", "27", "28", "29")
        assertEquals(2, CardFill.pickOption(short, CardFieldKind.EXP_YEAR, "08/2028"))
        assertNull(CardFill.pickOption(full, CardFieldKind.EXP_YEAR, "08/35"))
        assertNull(CardFill.pickOption(full, CardFieldKind.NUMBER, "08/28"))
        assertNull(CardFill.pickOption(full, CardFieldKind.EXP_YEAR, "garbage"))
    }
}
