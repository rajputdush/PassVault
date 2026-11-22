package com.ritvik.passvault.sites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteGroupsTest {
    private data class L(val id: String, val url: String = "", val app: String = "")

    private fun group(vararg items: L) = SiteGroups.group(items.toList()) { SiteGroups.keyOf(it.url, it.app) }

    @Test
    fun accountsOnOneSiteBecomeOneGroup() {
        val result = group(L("a", "amazon.com"), L("b", "https://www.amazon.com/ap/signin"), L("c", "smile.amazon.com"))
        assertEquals(1, result.size)
        val g = result[0] as ListEntry.Group
        assertEquals(listOf("a", "b", "c"), g.items.map { it.id })
    }

    @Test
    fun aSingleLoginStaysSingle() {
        val result = group(L("a", "amazon.com"), L("b", "github.com"))
        assertEquals(listOf(ListEntry.Single(L("a", "amazon.com")), ListEntry.Single(L("b", "github.com"))), result)
    }

    @Test
    fun differentSitesAreNotMixed() {
        val result = group(L("a", "amazon.com"), L("b", "amazon.in"), L("c", "amazon.com"))
        assertEquals(2, result.size)
        assertTrue(result[0] is ListEntry.Group)
        assertEquals(listOf("a", "c"), (result[0] as ListEntry.Group).items.map { it.id })
        assertEquals(ListEntry.Single(L("b", "amazon.in")), result[1])
    }

    @Test
    fun groupSitsWhereItsFirstItemWas() {
        val result = group(L("fav", "amazon.com"), L("x", "github.com"), L("other", "amazon.com"), L("y", "gitlab.com"))
        assertEquals(3, result.size)
        assertEquals("site:amazon.com", (result[0] as ListEntry.Group).key)
        assertEquals(ListEntry.Single(L("x", "github.com")), result[1])
        assertEquals(ListEntry.Single(L("y", "gitlab.com")), result[2])
    }

    @Test
    fun appLoginsGroupByPackage() {
        val result = group(L("a", app = "com.netflix.mediaclient"), L("b", app = "com.netflix.mediaclient"))
        assertEquals("app:com.netflix.mediaclient", (result.single() as ListEntry.Group).key)
    }

    @Test
    fun loginsWithNoSiteOrAppNeverGroup() {
        val result = group(L("a"), L("b"), L("c", "not a site"))
        assertEquals(3, result.size)
        assertTrue(result.all { it is ListEntry.Single })
        assertNull(SiteGroups.keyOf("", ""))
    }

    @Test
    fun websiteBeatsApp() {
        assertEquals("site:amazon.com", SiteGroups.keyOf("amazon.com", "com.amazon.mShop.android.shopping"))
    }

    @Test
    fun duplicatesIgnoreCaseAndSpaces() {
        assertEquals(setOf(0, 2), SiteGroups.duplicatePositions(listOf("Ritvik@Mail.com", "work@x.com", " ritvik@mail.com ")))
        assertEquals(emptySet<Int>(), SiteGroups.duplicatePositions(listOf("a", "b", "")))
        assertEquals(emptySet<Int>(), SiteGroups.duplicatePositions(listOf("", "")))
    }

    @Test
    fun summaryShowsAFewAccounts() {
        assertEquals("a@x.com, b@x.com", SiteGroups.accountSummary(listOf("a@x.com", "b@x.com")))
        assertEquals("a@x.com, b@x.com +2", SiteGroups.accountSummary(listOf("a@x.com", "b@x.com", "c@x.com", "d@x.com")))
        assertEquals("a@x.com", SiteGroups.accountSummary(listOf("a@x.com", " a@x.com ", "")))
        assertEquals("", SiteGroups.accountSummary(listOf("", " ")))
    }
}
