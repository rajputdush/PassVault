package com.ritvik.passvault.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMatcherTest {
    private data class L(val title: String, val url: String, val user: String, val app: String = "")

    private fun find(items: List<L>, domain: String?, label: String?, user: String, appId: String? = null) =
        SaveMatcher.findExisting(items, domain, appId, label, user, { it.url }, { it.title }, { it.app }, { it.user })

    private val amazon = L("", "amazon.com", "Me@Mail.com")
    private val amazon2 = L("", "https://www.amazon.in", "other@mail.com")
    private val oldApp = L("Netflix", "", "me")
    private val linkedApp = L("Spotify", "", "me", app = "com.spotify.music")

    @Test fun sameSiteAndUsernameIgnoringCaseUpdates() {
        assertEquals(amazon, find(listOf(amazon, amazon2), "smile.amazon.com", null, "me@mail.com"))
    }

    @Test fun newUsernameOnKnownSiteCreatesNew() {
        assertNull(find(listOf(amazon), "amazon.com", null, "someone@else.com"))
    }

    @Test fun otherSiteNeverMatches() {
        assertNull(find(listOf(amazon), "facebook.com", null, "me@mail.com"))
    }

    @Test fun blankUsernameUpdatesOnlyWhenUnambiguous() {
        assertEquals(amazon, find(listOf(amazon, L("", "facebook.com", "x")), "amazon.com", null, ""))
        assertNull(find(listOf(amazon, L("", "www.amazon.com", "second@mail.com")), "amazon.com", null, ""))
    }

    @Test fun olderAppLoginsMatchByName() {
        assertEquals(oldApp, find(listOf(amazon, oldApp), null, "netflix", "ME"))
        assertNull(find(listOf(oldApp), null, "Spotify", "me"))
        assertNull(find(listOf(oldApp), null, null, "me"))
    }

    @Test fun linkedAppLoginsMatchByPackage() {
        assertEquals(linkedApp, find(listOf(oldApp, linkedApp), null, "Some other label", "me", appId = "COM.spotify.music"))
        assertNull(find(listOf(linkedApp), null, "Spotify", "me", appId = "com.other.app"))
        // A login linked to one app is not matched to another app just because the names agree.
        assertNull(find(listOf(linkedApp), null, "Spotify", "me", appId = null))
    }

    @Test fun websiteLoginNeverMatchesAnApp() {
        assertNull(find(listOf(L("Netflix", "netflix.com", "me")), null, "Netflix", "me"))
    }

    @Test fun forAppOffersOnlyLinkedLogins() {
        val items = listOf(amazon, oldApp, linkedApp, L("X", "x.com", "u", app = "com.spotify.music"))
        val offered = SaveMatcher.forApp(items, "com.spotify.music", { it.app }, { it.url })
        assertEquals(listOf(linkedApp), offered)
        assertTrue(SaveMatcher.forApp(items, "com.android.chrome", { it.app }, { it.url }).isEmpty())
        assertTrue(SaveMatcher.forApp(items, null, { it.app }, { it.url }).isEmpty())
    }

    @Test fun appIdHelpers() {
        assertTrue(AppIds.isValid("com.netflix.mediaclient"))
        assertFalse(AppIds.isValid("netflix"))
        assertFalse(AppIds.isValid("com..bad"))
        assertFalse(AppIds.isValid(null))
        assertTrue(AppIds.isBrowser("com.android.chrome"))
        assertNull(AppIds.linkable("com.android.chrome"))
        assertEquals("com.spotify.music", AppIds.linkable(" com.spotify.music "))
        assertEquals("Mediaclient", AppIds.readableName("com.netflix.mediaclient"))
        assertEquals("Music", AppIds.readableName("com.spotify.music"))
    }
}
