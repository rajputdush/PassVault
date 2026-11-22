package com.ritvik.passvault.sites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SiteInfoTest {
    @Test
    fun typedTitleWins() {
        assertEquals("My shopping", SiteInfo.displayName("My shopping", "https://www.amazon.in/ap/signin", "me"))
    }

    @Test
    fun blankTitleUsesTheWebsite() {
        assertEquals("Amazon", SiteInfo.displayName("", "https://www.amazon.in/ap/signin", ""))
        assertEquals("Facebook", SiteInfo.displayName("   ", "facebook.com", "me@x.com"))
        assertEquals("Google", SiteInfo.displayName("", "mail.google.com", ""))
        assertEquals("My Bank", SiteInfo.displayName("", "https://my-bank.co.uk/login", ""))
    }

    @Test
    fun titleThatIsJustTheAddressIsPrettified() {
        assertEquals("Amazon", SiteInfo.displayName("amazon.com", "https://amazon.com", ""))
        assertEquals("Facebook", SiteInfo.displayName("www.facebook.com", "https://facebook.com/login", ""))
    }

    @Test
    fun fallsBackToTitleThenUsernameThenUntitled() {
        assertEquals("Work VPN", SiteInfo.displayName("Work VPN", "", "me"))
        assertEquals("me@x.com", SiteInfo.displayName("", "", " me@x.com "))
        assertEquals("Untitled", SiteInfo.displayName("", "", ""))
        assertEquals("Untitled", SiteInfo.displayName("  ", "not a url", ""))
    }

    @Test
    fun brandsAreFoundByRegistrableDomainOnly() {
        assertNotNull(SiteInfo.brandFor("https://www.amazon.in/x"))
        assertNotNull(SiteInfo.brandFor("login.facebook.com"))
        assertNull(SiteInfo.brandFor("facebook.com.evil.com"))
        assertNull(SiteInfo.brandFor("notfacebook.com"))
        assertNull(SiteInfo.brandFor(""))
    }

    @Test
    fun domainOfHandlesOddInput() {
        assertEquals("amazon.co.uk", SiteInfo.domainOf("https://smile.amazon.co.uk/"))
        assertEquals("192.168.1.5", SiteInfo.domainOf("http://192.168.1.5:3000"))
        assertNull(SiteInfo.domainOf("localhost"))
        assertNull(SiteInfo.domainOf(""))
    }

    @Test
    fun letterIsTheFirstCharacter() {
        assertEquals("A", SiteInfo.letter("amazon"))
        assertEquals("?", SiteInfo.letter(""))
    }
}
