package com.ritvik.passvault.share

import com.ritvik.passvault.Argon2KeyDeriver
import com.ritvik.passvault.crypto.KdfSettings
import com.ritvik.passvault.crypto.Pbkdf2KeyDeriver
import com.ritvik.passvault.crypto.wipe
import com.ritvik.passvault.items.CustomField
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Connects the vault's items to the share format. The heavy key derivation runs off the main thread. */
object ShareService {
    /** Same strength as the master password gets: 64 MiB, 3 passes. The settings travel inside the code. */
    private val SETTINGS = KdfSettings.argon2id(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1)

    fun toShared(item: VaultItem) = SharedItem(
        type = item.type.key,
        title = item.title,
        username = item.username,
        password = item.password,
        url = item.url,
        notes = item.notes,
        totp = item.totp,
        appId = item.appId,
        cardholder = item.cardholder,
        cardNumber = item.cardNumber,
        cardExpiry = item.cardExpiry,
        cardCvv = item.cardCvv,
        cardPin = item.cardPin,
        customFields = item.customFields.map { SharedField(it.label, it.value, it.hidden) },
    )

    /** A brand-new item for the receiver's own vault: new id, today's date, no folder, not a favourite. */
    fun toVaultItem(shared: SharedItem, now: Long) = VaultItem(
        id = VaultItem.newId(),
        title = shared.title,
        username = shared.username,
        password = shared.password,
        url = shared.url,
        notes = shared.notes,
        updatedAt = now,
        type = ItemType.fromKey(shared.type),
        totp = shared.totp,
        appId = shared.appId,
        passwordChangedAt = now,
        createdAt = now,
        source = "shared",
        cardholder = shared.cardholder,
        cardNumber = shared.cardNumber,
        cardExpiry = shared.cardExpiry,
        cardCvv = shared.cardCvv,
        cardPin = shared.cardPin,
        customFields = shared.customFields.map { CustomField(it.label, it.value, it.hidden) },
    )

    /** @return the share code (not the link). @throws ShareException if the item is too big. */
    suspend fun create(item: VaultItem, passphrase: String, validForMillis: Long): String = withContext(Dispatchers.Default) {
        val chars = passphrase.toCharArray()
        try {
            ShareCodec.seal(toShared(item), chars, Argon2KeyDeriver(), SETTINGS, System.currentTimeMillis(), validForMillis)
        } finally {
            chars.wipe()
        }
    }

    /** @param text a code, a link, or a whole message containing one. @throws ShareException with a user-readable message. */
    suspend fun open(text: String, passphrase: String): SharedItem = withContext(Dispatchers.Default) {
        val chars = passphrase.toCharArray()
        try {
            ShareCodec.open(text, chars, listOf(Argon2KeyDeriver(), Pbkdf2KeyDeriver()), System.currentTimeMillis())
        } finally {
            chars.wipe()
        }
    }
}
