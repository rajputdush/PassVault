package com.ritvik.passvault

import com.ritvik.passvault.activity.ChangeSummary
import com.ritvik.passvault.activity.ItemSnapshot
import com.ritvik.passvault.crypto.AesGcm
import com.ritvik.passvault.crypto.EncryptedBlob
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.VaultItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A private diary of what happened in the vault: unlocks, new and changed items (including which username or
 * setting changed), copies, shares, autofills, deletes, restores, and changes to theme, language and security settings.
 *
 * It never holds a password, code or note, only the item's title and what was done. The whole file is encrypted
 * with the vault key (AES-256-GCM), so it is unreadable while the vault is locked, and it keeps the latest
 * [MAX_ENTRIES] events. Events that happen while the vault is locked cannot be written and are skipped.
 *
 * Lock order: this log's lock, then the session lock. Never call [record] from inside `session.withKey`.
 */
class ActivityLog(
    private val file: File,
    private val session: VaultSession,
    private val settings: AppSettings,
) {
    class Entry(val time: Long, val kind: String, val detail: String)

    private val lock = Any()
    private var lastOffer = ""
    private var lastOfferAt = 0L

    fun record(kind: String, detail: String = "") {
        if (!settings.activityLogEnabled) return
        try {
            synchronized(lock) {
                val now = System.currentTimeMillis()
                // The autofill drop-down can be asked for many times in a row on one screen: log it once a minute.
                if (kind == KIND_OFFER) {
                    if (detail == lastOffer && now - lastOfferAt < 60_000) return
                    lastOffer = detail
                    lastOfferAt = now
                }
                session.withKey { key ->
                    val list = readWith(key).toMutableList()
                    list.add(Entry(now, kind, detail.take(ChangeSummary.MAX_DETAIL)))
                    while (list.size > MAX_ENTRIES) list.removeAt(0)
                    writeWith(key, list)
                }
            }
        } catch (_: Exception) {
            // A diary must never break the action it describes.
        }
    }

    /** Oldest first. Empty while the vault is locked. */
    fun read(): List<Entry> = try {
        synchronized(lock) { session.withKey { readWith(it) } } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun clear() {
        synchronized(lock) {
            try { file.delete() } catch (_: Exception) {}
        }
    }

    private fun readWith(key: ByteArray): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            val bytes = file.readBytes()
            if (bytes.size <= AesGcm.NONCE_SIZE_BYTES) return emptyList()
            val blob = EncryptedBlob(bytes.copyOfRange(0, AesGcm.NONCE_SIZE_BYTES), bytes.copyOfRange(AesGcm.NONCE_SIZE_BYTES, bytes.size))
            val plain = AesGcm.decrypt(key, blob, AAD)
            val array = JSONArray(String(plain, Charsets.UTF_8))
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                Entry(o.getLong("t"), o.getString("k"), o.optString("d"))
            }
        } catch (_: Exception) {
            emptyList() // unreadable or tampered: start a fresh log
        }
    }

    private fun writeWith(key: ByteArray, list: List<Entry>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("t", it.time).put("k", it.kind).put("d", it.detail)) }
        val blob = AesGcm.encrypt(key, array.toString().toByteArray(Charsets.UTF_8), AAD)
        writeAtomically(file, blob.nonce + blob.ciphertext)
    }

    companion object {
        const val MAX_ENTRIES = 500
        const val KIND_UNLOCK = "unlock"
        const val KIND_COPY = "copy"
        const val KIND_AUTOFILL = "autofill"
        const val KIND_OFFER = "offer"
        const val KIND_ADDED = "added"
        const val KIND_UPDATED = "updated"
        const val KIND_DELETE = "delete"
        const val KIND_RESTORE = "restore"
        const val KIND_PURGE = "purge"
        const val KIND_SHARE = "share"
        const val KIND_SETTING = "setting"
        const val KIND_SECURITY = "security"
        private val AAD = "passvault/v1/activity-log".toByteArray()

        /** The item's name plus, for a login, its username, so several accounts on one site can be told apart in the log. */
        fun nameOf(item: VaultItem): String =
            ChangeSummary.nameWithUser(item.displayTitle, if (item.type == ItemType.LOGIN) item.username else "")

        /** What the log compares to describe an edit. Secrets are only compared, never written. */
        fun snapshotOf(item: VaultItem) = ItemSnapshot(
            title = item.title, username = item.username, password = item.password, url = item.url, notes = item.notes,
            totp = item.totp, appId = item.appId, folder = item.folder, favorite = item.favorite,
            cardholder = item.cardholder, cardNumber = item.cardNumber, cardExpiry = item.cardExpiry,
            cardCvv = item.cardCvv, cardPin = item.cardPin,
            customFields = item.customFields.map { it.label + "\u0000" + it.value + "\u0000" + it.hidden },
        )

        fun label(kind: String): String = when (kind) {
            KIND_UNLOCK -> "Unlocked"
            KIND_COPY -> "Copied"
            KIND_AUTOFILL -> "Autofilled"
            KIND_OFFER -> "Autofill offered"
            KIND_ADDED -> "Saved new"
            KIND_UPDATED -> "Updated"
            KIND_DELETE -> "Moved to Trash"
            KIND_RESTORE -> "Restored from Trash"
            KIND_PURGE -> "Deleted forever"
            KIND_SHARE -> "Shared"
            KIND_SETTING -> "Changed setting"
            KIND_SECURITY -> "Security"
            else -> kind
        }
    }
}
