package com.ritvik.passvault.backup

import com.ritvik.passvault.VaultHeaderJson
import com.ritvik.passvault.crypto.VaultFormatException
import com.ritvik.passvault.crypto.VaultHeader
import com.ritvik.passvault.items.RawItem
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

class Backup(val header: VaultHeader, val items: List<RawItem>)

/**
 * A backup is the vault header plus every item file, exactly as they are stored on the phone.
 * Nothing in it is readable without the master password (as it was at backup time) or the recovery
 * key, so the file is safe to keep in cloud storage or email to yourself.
 */
object BackupCodec {
    private const val FORMAT = "passvault-backup"
    private const val VERSION = 1
    private val idPattern = Regex("^[0-9a-fA-F-]{36}$")
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun encode(header: VaultHeader, items: List<RawItem>): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", System.currentTimeMillis())
        put("header", JSONObject(VaultHeaderJson.encode(header)))
        put(
            "items",
            JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("id", item.id)
                            put("nonce", encoder.encodeToString(item.nonce))
                            put("ciphertext", encoder.encodeToString(item.ciphertext))
                        },
                    )
                }
            },
        )
    }.toString()

    fun decode(text: String): Backup {
        try {
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT) {
                throw BackupFormatException("This file is not a PassVault backup.")
            }
            if (root.optInt("version") != VERSION) {
                throw BackupFormatException("This backup was made by a different version of the app.")
            }
            val header = VaultHeaderJson.decode(root.getJSONObject("header").toString())
            val array = root.getJSONArray("items")
            val items = (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                val id = o.getString("id")
                if (!idPattern.matches(id)) throw BackupFormatException("The backup contains an invalid item.")
                RawItem(id, decoder.decode(o.getString("nonce")), decoder.decode(o.getString("ciphertext")))
            }
            return Backup(header, items)
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: VaultFormatException) {
            throw BackupFormatException("The backup file is damaged.", e)
        } catch (e: JSONException) {
            throw BackupFormatException("The backup file is damaged or not a PassVault backup.", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("The backup file is damaged.", e)
        }
    }
}
