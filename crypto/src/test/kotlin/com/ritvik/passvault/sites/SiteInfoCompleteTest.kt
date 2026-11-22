package com.ritvik.passvault.sites

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteInfoCompleteTest {
    @Test
    fun finishedAddressesCount() {
        assertTrue(SiteInfo.looksComplete("amazon.com"))
        assertTrue(SiteInfo.looksComplete("https://www.amazon.in/signin"))
        assertTrue(SiteInfo.looksComplete("login.my-bank.co.uk"))
        assertTrue(SiteInfo.looksComplete("192.168.1.25"))
    }

    @Test
    fun halfTypedAddressesDoNot() {
        assertFalse(SiteInfo.looksComplete(""))
        assertFalse(SiteInfo.looksComplete("a"))
        assertFalse(SiteInfo.looksComplete("amaz"))
        assertFalse(SiteInfo.looksComplete("amazon"))
        assertFalse(SiteInfo.looksComplete("amazon."))
        assertFalse(SiteInfo.looksComplete("amazon.c"))
        assertFalse(SiteInfo.looksComplete("192.168"))
        assertFalse(SiteInfo.looksComplete("amazon.c0m"))
    }
}
