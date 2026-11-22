package com.ritvik.passvault.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeSummaryTest {
    private val base = ItemSnapshot(title = "Amazon", username = "old@x.com", password = "p1", url = "amazon.com")

    private fun changes(after: ItemSnapshot, before: ItemSnapshot = base) = ChangeSummary.describe(before, after)

    @Test
    fun nothingChangedGivesNothing() {
        assertTrue(changes(base.copy()).isEmpty())
        assertTrue(changes(base.copy(username = "  old@x.com  ")).isEmpty())
    }

    @Test
    fun usernameChangeShowsOldAndNew() {
        assertEquals(listOf("Username changed from old@x.com to new@x.com"), changes(base.copy(username = "new@x.com")))
        assertEquals(listOf("Username set to a@b.c"), changes(base.copy(username = "a@b.c"), base.copy(username = "")))
        assertEquals(listOf("Username removed (was old@x.com)"), changes(base.copy(username = "")))
    }

    @Test
    fun passwordChangeNeverShowsThePassword() {
        val result = changes(base.copy(password = "SuperSecret99!"))
        assertEquals(listOf("Password changed"), result)
        assertFalse(result.joinToString().contains("SuperSecret"))
        assertFalse(result.joinToString().contains("p1"))
    }

    @Test
    fun cardSecretsAreOnlyMarkedChanged() {
        val card = ItemSnapshot(title = "Visa", cardNumber = "4111111111111111", cardCvv = "123", cardPin = "9999", cardExpiry = "08/28", cardholder = "R S")
        val after = card.copy(cardNumber = "5555555555554444", cardCvv = "456", cardPin = "", cardExpiry = "09/29")
        val result = ChangeSummary.describe(card, after)
        assertEquals(listOf("Card number changed", "Expiry date changed", "Security code changed", "PIN removed"), result)
        val text = result.joinToString()
        assertFalse(text.contains("4111") || text.contains("5555") || text.contains("123") || text.contains("456") || text.contains("9999"))
    }

    @Test
    fun notesAndTotpAreNeverCopied() {
        val result = changes(base.copy(notes = "secret recovery codes 1234", totp = "JBSWY3DPEHPK3PXP"))
        assertEquals(listOf("2FA key added", "Notes edited"), result)
        assertFalse(result.joinToString().contains("JBSWY"))
        assertFalse(result.joinToString().contains("1234"))
    }

    @Test
    fun several_changes_keepAFixedOrder() {
        val after = base.copy(title = "Amazon Work", username = "w@x.com", password = "p2", url = "amazon.in", folder = "Work", favorite = true)
        assertEquals(
            listOf("Name changed from Amazon to Amazon Work", "Username changed from old@x.com to w@x.com", "Password changed", "Website changed from amazon.com to amazon.in", "Moved to folder Work", "Added to favourites"),
            changes(after),
        )
    }

    @Test
    fun foldersAndFavourites() {
        val inFolder = base.copy(folder = "Work", favorite = true)
        assertEquals(listOf("Removed from folder Work", "Removed from favourites"), changes(base, inFolder))
    }

    @Test
    fun customFieldsAndLinkedApp() {
        val a = base.copy(customFields = listOf("Plan\u0000Gold\u0000false"))
        assertEquals(listOf("Custom fields changed"), changes(a.copy(customFields = listOf("Plan\u0000Silver\u0000false")), a))
        assertEquals(listOf("Linked app set to com.amazon.mShop.android.shopping"), changes(base.copy(appId = "com.amazon.mShop.android.shopping")))
    }

    @Test
    fun joinFitsTheLogLine() {
        assertEquals("a\nb", ChangeSummary.join(listOf("a", "b")))
        val many = (1..30).map { "change number $it" }
        val joined = ChangeSummary.join(many, 80)
        assertTrue(joined.length <= 80)
        assertTrue(joined.endsWith(" more"))
        assertTrue(joined.startsWith("change number 1\n"))
        assertEquals("", ChangeSummary.join(emptyList()))
    }

    @Test
    fun accountNotes() {
        assertEquals("First account on amazon.com", ChangeSummary.accountNote(0, "amazon.com"))
        assertEquals("Another account on amazon.com (#2)", ChangeSummary.accountNote(1, "amazon.com"))
        assertEquals("Another account on amazon.com (#4)", ChangeSummary.accountNote(3, "amazon.com"))
        assertEquals("", ChangeSummary.accountNote(2, null))
        assertEquals("", ChangeSummary.accountNote(2, " "))
    }

    @Test
    fun nameWithUserTellsAccountsApart() {
        assertEquals("Amazon · work@x.com", ChangeSummary.nameWithUser("Amazon", "work@x.com"))
        assertEquals("Amazon", ChangeSummary.nameWithUser("Amazon", ""))
        assertEquals("work@x.com", ChangeSummary.nameWithUser("work@x.com", "work@x.com"))
    }
}
