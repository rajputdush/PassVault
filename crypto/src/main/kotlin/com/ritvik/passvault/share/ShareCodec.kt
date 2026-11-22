package com.ritvik.passvault.share

import com.ritvik.passvault.crypto.AesGcm
import com.ritvik.passvault.crypto.DecryptionException
import com.ritvik.passvault.crypto.EncryptedBlob
import com.ritvik.passvault.crypto.Hkdf
import com.ritvik.passvault.crypto.Kdf
import com.ritvik.passvault.crypto.KdfMinimums
import com.ritvik.passvault.crypto.KdfParams
import com.ritvik.passvault.crypto.KdfSettings
import com.ritvik.passvault.crypto.KeyDeriver
import com.ritvik.passvault.crypto.PasswordGenerator
import com.ritvik.passvault.crypto.secureRandomBytes
import com.ritvik.passvault.crypto.wipe
import com.ritvik.passvault.importer.ImportFormatException
import com.ritvik.passvault.importer.MiniJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** One extra labelled value on a shared item. */
data class SharedField(val label: String, val value: String, val hidden: Boolean)

/**
 * The part of an item that is handed to someone else. It is deliberately smaller than a vault item: no id, no
 * favourite flag, no folder and no history, because those belong to the sender's own vault.
 * [type] is the item type key ("login", "card" or "note").
 */
data class SharedItem(
    val type: String,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val totp: String = "",
    val appId: String = "",
    val cardholder: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val cardPin: String = "",
    val customFields: List<SharedField> = emptyList(),
)

class ShareException(message: String, val reason: Reason) : Exception(message) {
    enum class Reason { NOT_A_SHARE, UNSUPPORTED, WRONG_PASSPHRASE, EXPIRED, WEAK_SETTINGS, TOO_BIG }
}

/**
 * Packs one item into a text code that only the right passphrase can open: it works as plain text, as a
 * `passvault://share/...` link or inside a QR code, and needs no server.
 *
 *   code     = "PVS1." + base64url( header | nonce | ciphertext )
 *   header   = version(1) | kdf(1) | memoryKiB(4) | iterations(4) | parallelism(1) | salt(16)
 *   key      = HKDF( KDF(passphrase, salt, parameters) )          (a label separate from every vault key)
 *   payload  = gzip( JSON { created, expires, item } ), encrypted with AES-256-GCM
 *
 * The whole header is bound into the encryption as associated data, so changing the key-derivation settings in
 * a code (to make it cheaper to guess) makes it fail to open. The passphrase is never part of the code.
 *
 * Limits worth knowing: anyone who holds both the code and the passphrase can read the item, and a code cannot
 * be taken back once sent. The expiry is checked by PassVault when the code is opened, so it protects against
 * old messages being used by mistake, not against someone who tampers with their own copy of the app.
 */
object ShareCodec {
    const val PREFIX = "PVS1."
    const val LINK_PREFIX = "passvault://share/"
    const val MIN_PASSPHRASE_LENGTH = 10
    /** A QR code with more characters than this is too dense to scan reliably from a phone screen. */
    const val QR_MAX_CHARS = 1000

    private const val VERSION: Byte = 1
    private const val KDF_ARGON2ID: Byte = 1
    private const val KDF_PBKDF2: Byte = 2
    private const val SALT_SIZE = 16
    private const val HEADER_SIZE = 1 + 1 + 4 + 4 + 1 + SALT_SIZE
    private const val MAX_PLAINTEXT = 200_000
    private const val MAX_CODE_BYTES = 120_000
    private const val MAX_ARGON2_MEMORY_KIB = 256 * 1024
    private const val MAX_ARGON2_ITERATIONS = 16
    private const val MAX_PBKDF2_ITERATIONS = 2_000_000
    private val AAD_LABEL = "passvault/v1/share".toByteArray(Charsets.UTF_8)
    private val HKDF_LABEL = "passvault/v1/hkdf/share".toByteArray(Charsets.UTF_8)
    private val CODE_PATTERN = Regex("PVS1\\.[A-Za-z0-9_-]{20,}")

    /** A fresh five-word passphrase (about 65 bits), lower case so it is easy to read out and type. */
    fun generatePassphrase(): String = PasswordGenerator.passphrase(words = 5, separator = "-", capitalize = false)

    /**
     * The link to send. With a [webBase] (for example "https://example.com/passvault/share/") it is a normal web link
     * with the code after a "#", which chat apps make tappable. The part after "#" is never sent to the web server.
     * Without one it is a passvault:// link.
     */
    fun toLink(code: String, webBase: String = ""): String =
        if (webBase.isBlank()) LINK_PREFIX + code else webBase.trimEnd('/') + "/#" + code

    fun fitsInQr(text: String): Boolean = text.length <= QR_MAX_CHARS

    /** Finds a share code in anything the user pasted: the bare code, the link, or a whole message containing either. */
    fun extractCode(text: String): String? = CODE_PATTERN.find(text)?.value

    fun seal(
        item: SharedItem,
        passphrase: CharArray,
        deriver: KeyDeriver,
        settings: KdfSettings,
        now: Long,
        validForMillis: Long,
    ): String {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "The passphrase must be at least $MIN_PASSPHRASE_LENGTH characters" }
        require(deriver.algorithm == settings.algorithm) { "The key deriver does not match the settings" }
        val params = settings.toParams(secureRandomBytes(SALT_SIZE))
        val plaintext = gzip(payloadJson(item, now, now + validForMillis).toByteArray(Charsets.UTF_8))
        if (plaintext.size > MAX_PLAINTEXT) throw ShareException("This item is too big to share.", ShareException.Reason.TOO_BIG)

        val header = encodeHeader(params)
        val key = deriveKey(passphrase, params, deriver)
        try {
            val blob = AesGcm.encrypt(key, plaintext, aad(header))
            val raw = ByteBuffer.allocate(header.size + blob.nonce.size + blob.ciphertext.size)
                .put(header).put(blob.nonce).put(blob.ciphertext).array()
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        } finally {
            key.wipe()
        }
    }

    fun open(
        text: String,
        passphrase: CharArray,
        derivers: List<KeyDeriver>,
        now: Long,
        minimums: KdfMinimums = KdfMinimums.STRICT,
    ): SharedItem {
        val code = extractCode(text) ?: throw ShareException("That doesn't look like a PassVault share.", ShareException.Reason.NOT_A_SHARE)
        val raw = try {
            Base64.getUrlDecoder().decode(code.removePrefix(PREFIX))
        } catch (e: IllegalArgumentException) {
            throw notAShare()
        }
        if (raw.size < HEADER_SIZE + AesGcm.NONCE_SIZE_BYTES + 16 || raw.size > MAX_CODE_BYTES) throw notAShare()

        val buf = ByteBuffer.wrap(raw)
        val version = buf.get()
        if (version != VERSION) {
            throw ShareException("This share was made by a newer version of PassVault. Update the app and try again.", ShareException.Reason.UNSUPPORTED)
        }
        val kdfId = buf.get()
        val memory = buf.getInt()
        val iterations = buf.getInt()
        val parallelism = buf.get().toInt()
        val salt = ByteArray(SALT_SIZE).also { buf.get(it) }
        val params = checkedParams(kdfId, memory, iterations, parallelism, salt, minimums)
        val deriver = derivers.firstOrNull { it.algorithm == params.algorithm } ?: throw notAShare()

        val nonce = ByteArray(AesGcm.NONCE_SIZE_BYTES).also { buf.get(it) }
        val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }
        val key = deriveKey(passphrase, params, deriver)
        val compressed = try {
            AesGcm.decrypt(key, EncryptedBlob(nonce, ciphertext), aad(raw.copyOfRange(0, HEADER_SIZE)))
        } catch (e: DecryptionException) {
            throw ShareException("Wrong passphrase, or the code was changed or cut short.", ShareException.Reason.WRONG_PASSPHRASE)
        } finally {
            key.wipe()
        }

        val json = String(gunzip(compressed, MAX_PLAINTEXT), Charsets.UTF_8)
        return parsePayload(json, now)
    }

    // ---------------------------------------------------------------- key and header

    private fun normalize(passphrase: CharArray): CharArray {
        val text = Normalizer.normalize(CharBuffer.wrap(passphrase), Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)
        return text.toCharArray()
    }

    private fun deriveKey(passphrase: CharArray, params: KdfParams, deriver: KeyDeriver): ByteArray {
        val normalized = normalize(passphrase)
        val master = try {
            deriver.derive(normalized, params)
        } finally {
            normalized.wipe()
        }
        try {
            return Hkdf.derive(master, null, HKDF_LABEL, AesGcm.KEY_SIZE_BYTES)
        } finally {
            master.wipe()
        }
    }

    private fun encodeHeader(p: KdfParams): ByteArray {
        val id = when (p.algorithm) {
            Kdf.ARGON2ID -> KDF_ARGON2ID
            Kdf.PBKDF2_SHA256 -> KDF_PBKDF2
            else -> throw IllegalArgumentException("Unsupported key derivation ${p.algorithm}")
        }
        return ByteBuffer.allocate(HEADER_SIZE)
            .put(VERSION).put(id).putInt(p.memoryKiB).putInt(p.iterations).put(p.parallelism.toByte()).put(p.salt)
            .array()
    }

    private fun aad(header: ByteArray): ByteArray = AAD_LABEL + header

    private fun checkedParams(kdfId: Byte, memory: Int, iterations: Int, parallelism: Int, salt: ByteArray, min: KdfMinimums): KdfParams {
        val weak = { ShareException("This share uses weaker protection than PassVault allows, so it was not opened.", ShareException.Reason.WEAK_SETTINGS) }
        if (parallelism !in 1..8) throw notAShare()
        return when (kdfId) {
            KDF_ARGON2ID -> {
                if (iterations !in 1..MAX_ARGON2_ITERATIONS || memory !in 8..MAX_ARGON2_MEMORY_KIB) throw notAShare()
                if (iterations < min.argon2Iterations || memory < min.argon2MemoryKiB) throw weak()
                KdfParams(Kdf.ARGON2ID, salt, iterations, memory, parallelism)
            }
            KDF_PBKDF2 -> {
                if (iterations !in 1..MAX_PBKDF2_ITERATIONS) throw notAShare()
                if (iterations < min.pbkdf2Iterations) throw weak()
                KdfParams(Kdf.PBKDF2_SHA256, salt, iterations, 0, parallelism)
            }
            else -> throw notAShare()
        }
    }

    private fun notAShare() = ShareException("That doesn't look like a valid PassVault share. It may be cut short or damaged.", ShareException.Reason.NOT_A_SHARE)

    // ---------------------------------------------------------------- payload

    private fun payloadJson(item: SharedItem, created: Long, expires: Long): String {
        val sb = StringBuilder(512)
        sb.append("{\"v\":1,\"created\":").append(created).append(",\"expires\":").append(expires).append(",\"item\":{")
        var first = true
        fun field(name: String, value: String) {
            if (value.isEmpty()) return
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(name).append("\":")
            quote(sb, value)
        }
        field("type", item.type)
        field("title", item.title)
        field("username", item.username)
        field("password", item.password)
        field("url", item.url)
        field("notes", item.notes)
        field("totp", item.totp)
        field("appId", item.appId)
        field("cardholder", item.cardholder)
        field("cardNumber", item.cardNumber)
        field("cardExpiry", item.cardExpiry)
        field("cardCvv", item.cardCvv)
        field("cardPin", item.cardPin)
        if (item.customFields.isNotEmpty()) {
            if (!first) sb.append(',')
            sb.append("\"fields\":[")
            item.customFields.forEachIndexed { i, f ->
                if (i > 0) sb.append(',')
                sb.append("{\"l\":")
                quote(sb, f.label)
                sb.append(",\"v\":")
                quote(sb, f.value)
                sb.append(",\"h\":").append(f.hidden).append('}')
            }
            sb.append(']')
        }
        sb.append("}}")
        return sb.toString()
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private fun parsePayload(json: String, now: Long): SharedItem {
        val root = try {
            MiniJson.parse(json) as? Map<*, *>
        } catch (e: ImportFormatException) {
            null
        } ?: throw notAShare()
        if ((root["v"] as? Double)?.toInt() != 1) throw notAShare()
        val expires = (root["expires"] as? Double)?.toLong() ?: throw notAShare()
        if (now > expires) {
            throw ShareException("This share has expired. Ask the sender to make a new one.", ShareException.Reason.EXPIRED)
        }
        val item = root["item"] as? Map<*, *> ?: throw notAShare()
        fun s(key: String) = (item[key] as? String).orEmpty()
        val fields = (item["fields"] as? List<*>).orEmpty().take(MAX_FIELDS).mapNotNull { f ->
            val m = f as? Map<*, *> ?: return@mapNotNull null
            SharedField(
                label = (m["l"] as? String).orEmpty().take(MAX_LABEL),
                value = (m["v"] as? String).orEmpty().take(MAX_VALUE),
                hidden = m["h"] as? Boolean ?: false,
            )
        }
        val type = s("type").takeIf { it in TYPES } ?: "login"
        return SharedItem(
            type = type,
            title = s("title"), username = s("username"), password = s("password"), url = s("url"), notes = s("notes"),
            totp = s("totp"), appId = s("appId"), cardholder = s("cardholder"), cardNumber = s("cardNumber"),
            cardExpiry = s("cardExpiry"), cardCvv = s("cardCvv"), cardPin = s("cardPin"), customFields = fields,
        )
    }

    private val TYPES = setOf("login", "card", "note")
    private const val MAX_FIELDS = 20
    private const val MAX_LABEL = 60
    private const val MAX_VALUE = 2000

    // ---------------------------------------------------------------- compression

    internal fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    /** Decompresses, but refuses to expand beyond [limit] bytes, so a small crafted code cannot exhaust memory. */
    internal fun gunzip(data: ByteArray, limit: Int): ByteArray {
        try {
            GZIPInputStream(ByteArrayInputStream(data)).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > limit) throw notAShare()
                    out.write(buffer, 0, n)
                }
                return out.toByteArray()
            }
        } catch (e: java.io.IOException) {
            throw notAShare()
        }
    }
}
