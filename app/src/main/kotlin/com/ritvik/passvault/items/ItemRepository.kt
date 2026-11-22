package com.ritvik.passvault.items

import com.ritvik.passvault.crypto.EncryptedBlob
import com.ritvik.passvault.crypto.VaultCrypto
import com.ritvik.passvault.crypto.wipe
import com.ritvik.passvault.writeAtomically
import org.json.JSONObject
import java.io.File
import java.util.Base64

/** An item exactly as stored on disk: still encrypted. Used for backup and restore. */
class RawItem(val id: String, val nonce: ByteArray, val ciphertext: ByteArray)

/**
 * Stores each item as its own encrypted file (<uuid>.item) in [dir]. The item id is bound into the
 * encryption as associated data, so a file renamed to another id will not decrypt.
 * A single corrupt file never takes the rest of the vault down with it.
 */
class ItemRepository(
    private val dir: File,
    private val crypto: VaultCrypto,
    /** Held while an item is written, so two writers never interleave. */
    val lock: Any = Any(),
) {
    private val idPattern = Regex("^[0-9a-fA-F-]{36}$")
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    init {
        dir.mkdirs()
    }

    /** Items that could not be read (corrupt or tampered) are skipped. */
    fun list(vaultKey: ByteArray): List<VaultItem> {
        val files = itemFiles() ?: return emptyList()
        return files.mapNotNull { file ->
            val id = file.name.removeSuffix(EXTENSION)
            if (!idPattern.matches(id)) return@mapNotNull null
            try {
                read(vaultKey, id, file)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.title.lowercase() }
    }

    fun save(vaultKey: ByteArray, item: VaultItem) {
        val plain = item.toJson().toByteArray(Charsets.UTF_8)
        try {
            val blob = crypto.encryptItem(vaultKey, item.id, plain)
            synchronized(lock) {
                writeRaw(RawItem(item.id, blob.nonce, blob.ciphertext))
            }
        } finally {
            plain.wipe()
        }
    }

    fun delete(id: String) {
        if (!idPattern.matches(id)) return
        synchronized(lock) {
            File(dir, id + EXTENSION).delete()
        }
    }

    /** Decrypts one item, or returns null if it is missing, damaged or tampered with. */
    fun get(vaultKey: ByteArray, id: String): VaultItem? {
        if (!idPattern.matches(id)) return null
        val file = File(dir, id + EXTENSION)
        if (!file.exists()) return null
        return try {
            read(vaultKey, id, file)
        } catch (e: Exception) {
            null
        }
    }

    /** One stored item without decrypting it, or null if missing or unreadable. */
    fun readRawOne(id: String): RawItem? {
        if (!idPattern.matches(id)) return null
        val file = File(dir, id + EXTENSION)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            RawItem(id, decoder.decode(json.getString("nonce")), decoder.decode(json.getString("ciphertext")))
        } catch (e: Exception) {
            null
        }
    }

    /** Ids of all stored item files (no key needed). */
    fun listIds(): List<String> =
        itemFiles()?.map { it.name.removeSuffix(EXTENSION) }?.filter { idPattern.matches(it) } ?: emptyList()

    /** Every stored item without decrypting it (needs no key). Unreadable files are skipped. */
    fun listRaw(): List<RawItem> {
        val files = itemFiles() ?: return emptyList()
        return files.mapNotNull { file ->
            val id = file.name.removeSuffix(EXTENSION)
            if (!idPattern.matches(id)) return@mapNotNull null
            try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                RawItem(id, decoder.decode(json.getString("nonce")), decoder.decode(json.getString("ciphertext")))
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Writes already-encrypted items, used when restoring a backup. */
    fun writeAll(items: List<RawItem>) {
        items.forEach { writeRaw(it) }
    }

    private fun writeRaw(item: RawItem) {
        require(idPattern.matches(item.id)) { "Invalid item id" }
        val json = JSONObject().apply {
            put("nonce", encoder.encodeToString(item.nonce))
            put("ciphertext", encoder.encodeToString(item.ciphertext))
        }
        writeAtomically(File(dir, item.id + EXTENSION), json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun itemFiles(): Array<File>? = dir.listFiles { f -> f.name.endsWith(EXTENSION) }

    private fun read(vaultKey: ByteArray, id: String, file: File): VaultItem {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val blob = EncryptedBlob(
            nonce = decoder.decode(json.getString("nonce")),
            ciphertext = decoder.decode(json.getString("ciphertext")),
        )
        val plain = crypto.decryptItem(vaultKey, id, blob)
        try {
            return VaultItem.fromJson(String(plain, Charsets.UTF_8)).copy(id = id)
        } finally {
            plain.wipe()
        }
    }

    private companion object {
        const val EXTENSION = ".item"
    }
}
