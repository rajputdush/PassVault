package com.ritvik.passvault.activity

/**
 * What the activity log needs to know about an item to describe an edit. Secrets (password, card number, codes, PIN,
 * 2FA key, notes) are only compared to see whether they changed. They are never copied into the description.
 */
data class ItemSnapshot(
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val totp: String = "",
    val appId: String = "",
    val folder: String = "",
    val favorite: Boolean = false,
    val cardholder: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val cardPin: String = "",
    /** One entry per custom field, label and value and whether it is hidden joined together, so any change shows. */
    val customFields: List<String> = emptyList(),
)

/**
 * Turns "what changed in this item" into short, safe sentences for the activity log, one line per change, for
 * example "Username changed from old@x.com to new@x.com".
 */
object ChangeSummary {
    const val MAX_DETAIL = 300

    /** The changes between [before] and [after], in a fixed order. Empty when nothing changed. */
    fun describe(before: ItemSnapshot, after: ItemSnapshot): List<String> {
        val out = ArrayList<String>()
        fun text(noun: String, old: String, new: String) {
            val a = old.trim()
            val b = new.trim()
            if (a == b) return
            out += when {
                a.isEmpty() -> "$noun set to $b"
                b.isEmpty() -> "$noun removed (was $a)"
                else -> "$noun changed from $a to $b"
            }
        }
        fun secret(noun: String, old: String, new: String) {
            if (old == new) return
            out += when {
                old.isEmpty() -> "$noun added"
                new.isEmpty() -> "$noun removed"
                else -> "$noun changed"
            }
        }

        text("Name", before.title, after.title)
        text("Username", before.username, after.username)
        secret("Password", before.password, after.password)
        text("Website", before.url, after.url)
        text("Linked app", before.appId, after.appId)
        secret("2FA key", before.totp, after.totp)
        if (before.notes != after.notes) out += "Notes edited"
        if (before.cardholder.trim() != after.cardholder.trim()) out += "Cardholder name changed"
        secret("Card number", before.cardNumber, after.cardNumber)
        if (before.cardExpiry.trim() != after.cardExpiry.trim()) out += "Expiry date changed"
        secret("Security code", before.cardCvv, after.cardCvv)
        secret("PIN", before.cardPin, after.cardPin)
        if (before.customFields != after.customFields) out += "Custom fields changed"
        if (before.folder.trim() != after.folder.trim()) {
            out += if (after.folder.isBlank()) "Removed from folder ${before.folder.trim()}" else "Moved to folder ${after.folder.trim()}"
        }
        if (before.favorite != after.favorite) out += if (after.favorite) "Added to favourites" else "Removed from favourites"
        return out
    }

    /** Joins changes into lines that fit the log, cutting at a whole change and saying how many were left out. */
    fun join(changes: List<String>, max: Int = MAX_DETAIL): String {
        val full = changes.joinToString("\n")
        if (full.length <= max) return full
        val kept = ArrayList<String>()
        var length = 0
        for ((index, change) in changes.withIndex()) {
            val more = "\n+${changes.size - index} more"
            val add = (if (kept.isEmpty()) 0 else 1) + change.length
            if (length + add + more.length > max) break
            kept += change
            length += add
        }
        return if (kept.isEmpty()) full.take(max - 1) + "…" else kept.joinToString("\n") + "\n+${changes.size - kept.size} more"
    }

    /**
     * For a login that is being saved as new: whether it is the first account on that site or another one.
     * @param othersOnSameSite how many other logins already belong to the same site or app
     * @param site the site's domain or the app's package, or null when the login has neither
     */
    fun accountNote(othersOnSameSite: Int, site: String?): String = when {
        site.isNullOrBlank() -> ""
        othersOnSameSite <= 0 -> "First account on $site"
        else -> "Another account on $site (#${othersOnSameSite + 1})"
    }

    /** "Amazon · work@x.com": the item's name plus its username, so several accounts on one site can be told apart. */
    fun nameWithUser(name: String, username: String): String {
        val user = username.trim()
        return if (user.isEmpty() || user.equals(name.trim(), ignoreCase = true)) name else "$name · $user"
    }
}
