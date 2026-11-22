package com.ritvik.passvault

import com.ritvik.passvault.crypto.EncryptedBlob
import com.ritvik.passvault.crypto.KdfParams
import com.ritvik.passvault.crypto.VaultFormatException
import com.ritvik.passvault.crypto.VaultHeader
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

/** Reads and writes the vault header as JSON. Binary fields are Base64. Needs API 26+ for java.util.Base64. */
object VaultHeaderJson {
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun encode(header: VaultHeader): String = JSONObject().apply {
        put("version", header.version)
        put(
            "kdf",
            JSONObject().apply {
                put("algorithm", header.kdf.algorithm)
                put("salt", encoder.encodeToString(header.kdf.salt))
                put("iterations", header.kdf.iterations)
                put("memoryKiB", header.kdf.memoryKiB)
                put("parallelism", header.kdf.parallelism)
            },
        )
        put("passwordWrappedKey", blobToJson(header.passwordWrappedKey))
        put("recoveryWrappedKey", blobToJson(header.recoveryWrappedKey))
    }.toString(2)

    fun decode(text: String): VaultHeader {
        try {
            val root = JSONObject(text)
            return VaultHeader(
                version = root.getInt("version"),
                kdf = decodeKdf(root.getJSONObject("kdf")),
                passwordWrappedKey = blobFromJson(root.getJSONObject("passwordWrappedKey")),
                recoveryWrappedKey = blobFromJson(root.getJSONObject("recoveryWrappedKey")),
            )
        } catch (e: JSONException) {
            throw VaultFormatException("Vault header is corrupt", e)
        } catch (e: IllegalArgumentException) {
            throw VaultFormatException("Vault header is corrupt", e)
        }
    }

    fun decodeKdf(kdf: JSONObject): KdfParams = try {
        KdfParams(
            algorithm = kdf.getString("algorithm"),
            salt = decoder.decode(kdf.getString("salt")),
            iterations = kdf.getInt("iterations"),
            memoryKiB = kdf.getInt("memoryKiB"),
            parallelism = kdf.getInt("parallelism"),
        )
    } catch (e: JSONException) {
        throw VaultFormatException("Key settings are corrupt", e)
    } catch (e: IllegalArgumentException) {
        throw VaultFormatException("Key settings are corrupt", e)
    }

    private fun blobToJson(blob: EncryptedBlob) = JSONObject().apply {
        put("nonce", encoder.encodeToString(blob.nonce))
        put("ciphertext", encoder.encodeToString(blob.ciphertext))
    }

    private fun blobFromJson(json: JSONObject) = EncryptedBlob(
        nonce = decoder.decode(json.getString("nonce")),
        ciphertext = decoder.decode(json.getString("ciphertext")),
    )
}
