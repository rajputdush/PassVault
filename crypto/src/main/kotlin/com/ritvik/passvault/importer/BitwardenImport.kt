package com.ritvik.passvault.importer

import com.ritvik.passvault.autofill.AppIds
import com.ritvik.passvault.autofill.DomainMatcher
import com.ritvik.passvault.totp.Totp

/**
 * Reads Bitwarden's unencrypted JSON export (Tools > Export vault > .json). Unlike CSV it carries cards, secure notes,
 * folders, favourites, 2FA secrets and custom fields, so those come across too. Identities are skipped.
 */
internal object BitwardenImport {
    private const val MAX_FIELDS = 20

    fun looksLikeJson(text: String): Boolean = text.trimStart('﻿', ' ', '\n', '\r', '\t').startsWith("{")

    fun parse(text: String): ImportPreview {
        val root = MiniJson.parse(text.trimStart('﻿')) as? Map<*, *>
            ?: throw ImportFormatException("This JSON file is not a Bitwarden export.")
        if (root["encrypted"] == true) {
            throw ImportFormatException(
                "This export is encrypted. In Bitwarden choose Export vault with the file format \".json\" (not \"encrypted\") and try again.",
            )
        }
        val items = root["items"] as? List<*> ?: throw ImportFormatException("This JSON file is not a Bitwarden export.")
        if (items.size > PasswordImport.MAX_ROWS) {
            throw ImportFormatException("The file has more than ${PasswordImport.MAX_ROWS} items. Split it and import in parts.")
        }
        val folders = (root["folders"] as? List<*>).orEmpty()
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull { f -> (f["id"] as? String)?.let { id -> id to (f["name"] as? String).orEmpty().trim() } }
            .toMap()

        val out = ArrayList<ImportedLogin>()
        var skipped = 0
        for (raw in items) {
            val item = raw as? Map<*, *>
            val converted = item?.let { convert(it, folders) }
            if (converted == null) skipped++ else out += converted
        }
        return ImportPreview(out, skipped, "Bitwarden")
    }

    private fun convert(item: Map<*, *>, folders: Map<String, String>): ImportedLogin? {
        val type = (item["type"] as? Double)?.toInt()
        val name = (item["name"] as? String).orEmpty().trim()
        val notes = (item["notes"] as? String).orEmpty()
        val folder = (item["folderId"] as? String)?.let { folders[it] }.orEmpty()
        val favorite = item["favorite"] == true
        val fields = ArrayList<ImportedField>()
        (item["fields"] as? List<*>).orEmpty().forEach { f ->
            val field = f as? Map<*, *> ?: return@forEach
            val label = (field["name"] as? String).orEmpty().trim()
            val value = (field["value"] as? String).orEmpty()
            val fieldType = (field["type"] as? Double)?.toInt() ?: 0
            // 3 = linked to another field, which has no value of its own.
            if (fieldType != 3 && (label.isNotEmpty() || value.isNotEmpty())) fields += ImportedField(label, value, hidden = fieldType == 1)
        }

        return when (type) {
            1 -> {
                val login = item["login"] as? Map<*, *> ?: return null
                val username = (login["username"] as? String).orEmpty().trim()
                val password = (login["password"] as? String).orEmpty()
                val uris = (login["uris"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("uri") as? String }.map { it.trim() }
                    .filter { it.isNotEmpty() }
                var url = ""
                var appId = ""
                uris.firstOrNull { it.startsWith("androidapp://", ignoreCase = true) }?.let {
                    appId = AppIds.linkable(it.substring("androidapp://".length)).orEmpty()
                }
                uris.firstOrNull { !it.startsWith("androidapp://", ignoreCase = true) && !it.startsWith("iosapp://", ignoreCase = true) }
                    ?.let { url = it }
                // An item with nothing to find it by or fill in is not worth importing.
                if (name.isEmpty() && url.isEmpty() && username.isEmpty() && password.isEmpty()) return null

                var totp = (login["totp"] as? String).orEmpty().trim()
                if (totp.isNotEmpty() && !Totp.isValid(totp)) {
                    // Not a standard authenticator secret (for example Steam): keep it rather than lose it.
                    fields.add(0, ImportedField("2FA key (not usable here)", totp, hidden = true))
                    totp = ""
                }
                ImportedLogin(
                    title = name.ifEmpty { DomainMatcher.hostOf(url) ?: username.ifEmpty { "Imported login" } },
                    url = url,
                    username = username,
                    password = password,
                    notes = notes,
                    kind = ImportedKind.LOGIN,
                    totp = totp,
                    appId = appId,
                    folder = folder,
                    favorite = favorite,
                    customFields = fields.take(MAX_FIELDS),
                )
            }
            2 -> {
                if (name.isEmpty() && notes.isEmpty()) return null
                ImportedLogin(
                    title = name.ifEmpty { "Imported note" }, url = "", username = "", password = "", notes = notes,
                    kind = ImportedKind.NOTE, folder = folder, favorite = favorite, customFields = fields.take(MAX_FIELDS),
                )
            }
            3 -> {
                val card = item["card"] as? Map<*, *> ?: return null
                val number = (card["number"] as? String).orEmpty().filter { it.isDigit() }
                val holder = (card["cardholderName"] as? String).orEmpty().trim()
                if (number.isEmpty() && holder.isEmpty() && name.isEmpty()) return null
                val brand = (card["brand"] as? String).orEmpty().trim()
                // Keep the brand as a field: it is shown automatically from the number, but old cards may differ.
                if (brand.isNotEmpty()) fields.add(0, ImportedField("Brand", brand, hidden = false))
                ImportedLogin(
                    title = name, url = "", username = "", password = "", notes = notes,
                    kind = ImportedKind.CARD, folder = folder, favorite = favorite, customFields = fields.take(MAX_FIELDS),
                    cardholder = holder,
                    cardNumber = number,
                    cardExpiry = expiry(card["expMonth"] as? String, card["expYear"] as? String),
                    cardCvv = (card["code"] as? String).orEmpty().trim(),
                )
            }
            else -> null // identities and anything unknown
        }
    }

    /** "12" and "2030" become "12/30". Anything unreadable becomes empty. */
    private fun expiry(month: String?, year: String?): String {
        val m = month?.trim()?.toIntOrNull() ?: return ""
        val y = year?.trim()?.toIntOrNull() ?: return ""
        if (m !in 1..12) return ""
        val yy = if (y >= 100) y % 100 else y
        return "%02d/%02d".format(m, yy)
    }
}
