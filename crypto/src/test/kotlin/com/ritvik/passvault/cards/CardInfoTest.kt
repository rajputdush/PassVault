package com.ritvik.passvault.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardInfoTest {
    @Test fun luhnAcceptsKnownTestNumbers() {
        assertTrue(CardInfo.luhnValid("4111 1111 1111 1111"))
        assertTrue(CardInfo.luhnValid("5555555555554444"))
        assertTrue(CardInfo.luhnValid("378282246310005"))
        assertTrue(CardInfo.luhnValid("6011111111111117"))
    }

    @Test fun luhnRejectsTypos() {
        assertFalse(CardInfo.luhnValid("4111 1111 1111 1112"))
        assertFalse(CardInfo.luhnValid("1234"))
        assertFalse(CardInfo.luhnValid(""))
        assertFalse(CardInfo.luhnValid("abcd"))
    }

    @Test fun detectsBrands() {
        assertEquals("Visa", CardInfo.brand("4111111111111111"))
        assertEquals("Mastercard", CardInfo.brand("5555555555554444"))
        assertEquals("Mastercard", CardInfo.brand("2223000048400011"))
        assertEquals("American Express", CardInfo.brand("378282246310005"))
        assertEquals("Discover", CardInfo.brand("6011111111111117"))
        assertEquals("RuPay", CardInfo.brand("6521123412341234"))
        assertEquals("RuPay", CardInfo.brand("8100123412341234"))
        assertEquals("Diners Club", CardInfo.brand("30569309025904"))
        assertEquals("JCB", CardInfo.brand("3530111333300000"))
        assertEquals("Card", CardInfo.brand(""))
        assertEquals("Card", CardInfo.brand("9999"))
    }

    @Test fun formatsAndMasks() {
        assertEquals("4111 1111 1111 1111", CardInfo.format("4111111111111111"))
        assertEquals("3782 822463 10005", CardInfo.format("378282246310005"))
        assertEquals("•••• 1111", CardInfo.masked("4111 1111 1111 1111"))
        assertEquals("••••", CardInfo.masked("12"))
    }

    @Test fun expiry() {
        assertEquals(12 to 2030, CardInfo.parseExpiry("12/30"))
        assertEquals(1 to 2031, CardInfo.parseExpiry("01/2031"))
        assertEquals(7 to 2027, CardInfo.parseExpiry("0727"))
        assertEquals(7 to 2027, CardInfo.parseExpiry("07-27"))
        assertNull(CardInfo.parseExpiry("13/30"))
        assertNull(CardInfo.parseExpiry("00/30"))
        assertNull(CardInfo.parseExpiry("1/5/30"))
        assertNull(CardInfo.parseExpiry("soon"))
        assertTrue(CardInfo.expiryValid("11/26", 2026, 11)) // valid through the end of November
        assertFalse(CardInfo.expiryValid("10/26", 2026, 11))
        assertTrue(CardInfo.expiryValid("01/27", 2026, 11))
        assertFalse(CardInfo.expiryValid("12/25", 2026, 11))
    }
}
