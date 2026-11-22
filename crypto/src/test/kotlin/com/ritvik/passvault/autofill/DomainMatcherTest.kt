package com.ritvik.passvault.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {
    @Test
    fun sameSiteMatchesWhateverWayItWasTyped() {
        assertTrue(DomainMatcher.matches("github.com", "github.com"))
        assertTrue(DomainMatcher.matches("https://github.com/login", "github.com"))
        assertTrue(DomainMatcher.matches("HTTPS://www.GitHub.com", "github.com"))
        assertTrue(DomainMatcher.matches("github.com:443/x?y=1#z", "www.github.com"))
    }

    @Test
    fun subdomainsOfTheSameSiteMatchBothWays() {
        assertTrue(DomainMatcher.matches("example.com", "login.example.com"))
        assertTrue(DomainMatcher.matches("accounts.example.com", "example.com"))
        assertTrue(DomainMatcher.matches("a.example.com", "b.example.com"))
    }

    @Test
    fun lookAlikeSitesDoNotMatch() {
        assertFalse(DomainMatcher.matches("github.com", "github.com.evil.com"))
        assertFalse(DomainMatcher.matches("github.com", "notgithub.com"))
        assertFalse(DomainMatcher.matches("github.com", "githhub.com"))
        assertFalse(DomainMatcher.matches("https://evil.com@github.com", "evil.com"))
        assertFalse(DomainMatcher.matches("github.com", "github.co"))
    }

    @Test
    fun twoPartSuffixesAreRespected() {
        assertTrue(DomainMatcher.matches("bbc.co.uk", "www.bbc.co.uk"))
        assertFalse(DomainMatcher.matches("bbc.co.uk", "evil.co.uk"))
        assertEquals("bbc.co.uk", DomainMatcher.registrable("news.bbc.co.uk"))
    }

    @Test
    fun sharedHostingCustomersAreDifferentOwners() {
        assertFalse(DomainMatcher.matches("alice.github.io", "mallory.github.io"))
        assertTrue(DomainMatcher.matches("alice.github.io", "alice.github.io"))
        assertFalse(DomainMatcher.matches("myapp.vercel.app", "evil.vercel.app"))
    }

    @Test
    fun emptyOrMissingNeverMatches() {
        assertFalse(DomainMatcher.matches("", "example.com"))
        assertFalse(DomainMatcher.matches("   ", "example.com"))
        assertFalse(DomainMatcher.matches("example.com", null))
        assertFalse(DomainMatcher.matches("example.com", ""))
        assertNull(DomainMatcher.hostOf("https://"))
    }

    @Test
    fun ipAddressesMatchOnlyExactly() {
        assertTrue(DomainMatcher.matches("192.168.1.5:3000", "192.168.1.5"))
        assertFalse(DomainMatcher.matches("192.168.1.5", "10.168.1.5"))
        assertFalse(DomainMatcher.matches("1.5", "9.1.5"))
    }
}
