package com.ritvik.passvault.items

import com.ritvik.passvault.cards.CardInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** What kind of thing an item is. The key is what is stored, so never rename one. */
enum class ItemType(val key: String, val label: String, val plural: String) {
    LOGIN("login", "Login", "Logins"),
    CARD("card", "Card", "Cards"),
    NOTE("note", "Secure note", "Notes");

    companion object {
        fun fromKey(key: String?): ItemType = entries.firstOrNull { it.key == key } ?: LOGIN
    }
}

/** A password a login used before. Kept so a change can be undone. */
data class PasswordEntry(val password: String, val setAt: Long) {
    companion object { const val MAX = 5 }
}

/** An extra labelled value on an item, for example "Security question" or "Account number". */
data class CustomField(val label: String, val value: String, val hidden: Boolean = false) {
    companion object {
        const val MAX_FIELDS = 20
        const val MAX_LABEL = 60
        const val MAX_VALUE = 2000
    }
}

/**
 * One saved entry: a login, a payment card or a secure note. Everything in here is encrypted before it touches the disk.
 * Fields a type does not use stay empty.
 */
data class VaultItem(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val updatedAt: Long,
    val type: ItemType = ItemType.LOGIN,
    val favorite: Boolean = false,
    /** A simple label such as "Work" or "Family". Empty = not in a folder. */
    val folder: String = "",
    /** The 2FA secret: an otpauth:// link or a Base32 key. Empty = none. */
    val totp: String = "",
    /** Package name of the Android app this login belongs to (for example com.netflix.mediaclient). */
    val appId: String = "",
    /** When the password last changed. 0 for items saved before this was tracked: [passwordAge] falls back to [updatedAt]. */
    val passwordChangedAt: Long = 0,
    val cardholder: String = "",
    val cardNumber: String = "",
    /** As typed, for example 08/28. */
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val cardPin: String = "",
    val customFields: List<CustomField> = emptyList(),
    /** When the item was moved to the Trash. 0 = not in the Trash. Trashed items are removed for good after 30 days. */
    val deletedAt: Long = 0,
    /** Earlier passwords of this login, newest first (at most [PasswordEntry.MAX]). */
    val history: List<PasswordEntry> = emptyList(),
    /** When the item was first saved. 0 for items saved before this was tracked. */
    val createdAt: Long = 0,
    /** Where it came from: "manual", "website:<domain>", "app:<name>", "import" or "shared". Empty = not recorded. */
    val source: String = "",
) {
    /** The origin in words, for the bottom of the edit screen. Empty if it was not recorded. */
    val sourceText: String
        get() = when {
            source == "manual" -> "Added manually"
            source.startsWith("website:") -> "Saved from the website " + source.removePrefix("website:")
            source.startsWith("app:") -> "Saved from the app " + source.removePrefix("app:")
            source == "import" -> "Imported from a file"
            source == "shared" -> "Received as a shared item"
            else -> ""
        }

    /** This item with [newPassword], remembering the old one in [history]. Same password = unchanged item. */
    fun withPassword(newPassword: String, now: Long): VaultItem {
        if (newPassword == password) return this
        val older = if (password.isBlank()) history else listOf(PasswordEntry(password, passwordAge)) + history
        return copy(
            password = newPassword,
            passwordChangedAt = now,
            history = older.filter { it.password != newPassword }.take(PasswordEntry.MAX),
        )
    }

    val inTrash: Boolean get() = deletedAt > 0

    /** What the list shows: the name if there is one, otherwise something sensible for the type. */
    val displayTitle: String
        get() = when (type) {
            ItemType.LOGIN -> com.ritvik.passvault.sites.SiteInfo.displayName(title, url, username)
            ItemType.CARD -> title.ifBlank {
                if (cardNumber.isBlank()) "Card" else CardInfo.brand(cardNumber) + " " + CardInfo.masked(cardNumber)
            }
            ItemType.NOTE -> title.ifBlank {
                notes.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(40) ?: "Untitled note"
            }
        }

    /** The small second line in the list. */
    val subtitle: String
        get() = when (type) {
            ItemType.LOGIN -> username.ifBlank { url }
            ItemType.CARD -> listOf(CardInfo.masked(cardNumber).takeIf { cardNumber.isNotBlank() }, cardholder.takeIf { it.isNotBlank() })
                .filterNotNull().joinToString("  ·  ")
            ItemType.NOTE -> ""
        }

    /** When the password was last changed, as far as we know. */
    val passwordAge: Long get() = if (passwordChangedAt > 0) passwordChangedAt else updatedAt

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("username", username)
        put("password", password)
        put("url", url)
        put("notes", notes)
        put("updatedAt", updatedAt)
        put("type", type.key)
        put("favorite", favorite)
        put("folder", folder)
        put("totp", totp)
        put("appId", appId)
        put("passwordChangedAt", passwordChangedAt)
        put("cardholder", cardholder)
        put("cardNumber", cardNumber)
        put("cardExpiry", cardExpiry)
        put("cardCvv", cardCvv)
        put("cardPin", cardPin)
        put("deletedAt", deletedAt)
        put("createdAt", createdAt)
        put("source", source)
        put("history", JSONArray().apply {
            history.forEach { put(JSONObject().apply { put("password", it.password); put("setAt", it.setAt) }) }
        })
        put("customFields", JSONArray().apply {
            customFields.forEach { f ->
                put(JSONObject().apply {
                    put("label", f.label)
                    put("value", f.value)
                    put("hidden", f.hidden)
                })
            }
        })
    }.toString()

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun blank(type: ItemType = ItemType.LOGIN): VaultItem {
            val now = System.currentTimeMillis()
            return VaultItem(newId(), "", "", "", "", "", now, type = type, passwordChangedAt = now, createdAt = now, source = "manual")
        }

        private fun readHistory(o: JSONObject): List<PasswordEntry> {
            val array = o.optJSONArray("history") ?: return emptyList()
            val out = ArrayList<PasswordEntry>(array.length())
            for (i in 0 until minOf(array.length(), PasswordEntry.MAX)) {
                val e = array.optJSONObject(i) ?: continue
                val pw = e.optString("password")
                if (pw.isNotEmpty()) out.add(PasswordEntry(pw, e.optLong("setAt")))
            }
            return out
        }

        private fun readCustomFields(o: JSONObject): List<CustomField> {
            val array = o.optJSONArray("customFields") ?: return emptyList()
            val out = ArrayList<CustomField>(array.length())
            for (i in 0 until minOf(array.length(), CustomField.MAX_FIELDS)) {
                val f = array.optJSONObject(i) ?: continue
                out.add(CustomField(f.optString("label"), f.optString("value"), f.optBoolean("hidden", false)))
            }
            return out
        }

        /** Older items have none of the newer fields; they read as plain logins. */
        fun fromJson(text: String): VaultItem {
            val o = JSONObject(text)
            return VaultItem(
                id = o.getString("id"),
                title = o.optString("title"),
                username = o.optString("username"),
                password = o.optString("password"),
                url = o.optString("url"),
                notes = o.optString("notes"),
                updatedAt = o.optLong("updatedAt"),
                type = ItemType.fromKey(o.optString("type", "login")),
                favorite = o.optBoolean("favorite", false),
                folder = o.optString("folder"),
                totp = o.optString("totp"),
                appId = o.optString("appId"),
                passwordChangedAt = o.optLong("passwordChangedAt", 0),
                cardholder = o.optString("cardholder"),
                cardNumber = o.optString("cardNumber"),
                cardExpiry = o.optString("cardExpiry"),
                cardCvv = o.optString("cardCvv"),
                cardPin = o.optString("cardPin"),
                customFields = readCustomFields(o),
                deletedAt = o.optLong("deletedAt", 0),
                history = readHistory(o),
                createdAt = o.optLong("createdAt", 0),
                source = o.optString("source"),
            )
        }
    }
}

/** Only logins have a password worth auditing; cards and notes are left out of the security check. */
fun List<VaultItem>.logins(): List<VaultItem> = filter { it.type == ItemType.LOGIN && !it.inTrash }

/** Days a deleted item stays in the Trash before it is removed for good. */
const val TRASH_DAYS = 30
