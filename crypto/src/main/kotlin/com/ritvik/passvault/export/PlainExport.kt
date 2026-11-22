package com.ritvik.passvault.export

import com.ritvik.passvault.importer.ImportedKind
import com.ritvik.passvault.importer.ImportedLogin

/**
 * Plain-text exports so people can take their data to another password manager. NOT encrypted: whoever gets the
 * file can read every password, so the app asks for the master password first and tells the user to delete it.
 *
 * The JSON follows Bitwarden's export format, which most managers (and PassVault itself) can import, and keeps
 * cards, notes, folders, favourites, 2FA keys and extra fields. The CSV only holds logins.
 */
object PlainExport {
    fun toBitwardenJson(items: List<ImportedLogin>): String {
        val folderIds = LinkedHashMap<String, String>()
        items.map { it.folder.trim() }.filter { it.isNotEmpty() }.distinct().forEachIndexed { i, name ->
            folderIds[name] = "folder-${i + 1}"
        }
        val out = StringBuilder()
        out.append("{\n  \"encrypted\": false,\n  \"folders\": [")
        out.append(folderIds.entries.joinToString(",") { "\n    {\"id\": ${q(it.value)}, \"name\": ${q(it.key)}}" })
        out.append(if (folderIds.isEmpty()) "],\n" else "\n  ],\n")
        out.append("  \"items\": [")
        items.forEachIndexed { index, item ->
            if (index > 0) out.append(',')
            out.append("\n    ").append(itemJson(item, index, folderIds[item.folder.trim()]))
        }
        out.append(if (items.isEmpty()) "]\n}\n" else "\n  ]\n}\n")
        return out.toString()
    }

    private fun itemJson(item: ImportedLogin, index: Int, folderId: String?): String {
        val type = when (item.kind) { ImportedKind.LOGIN -> 1; ImportedKind.NOTE -> 2; ImportedKind.CARD -> 3 }
        val sb = StringBuilder("{")
        sb.append("\"id\": ${q("item-${index + 1}")}, ")
        sb.append("\"folderId\": ${folderId?.let { q(it) } ?: "null"}, ")
        sb.append("\"type\": $type, ")
        sb.append("\"name\": ${q(item.title)}, ")
        sb.append("\"notes\": ${if (item.notes.isEmpty()) "null" else q(item.notes)}, ")
        sb.append("\"favorite\": ${item.favorite}, ")
        sb.append("\"fields\": [")
        sb.append(item.customFields.joinToString(", ") {
            "{\"name\": ${q(it.label)}, \"value\": ${q(it.value)}, \"type\": ${if (it.hidden) 1 else 0}}"
        })
        sb.append("]")
        when (item.kind) {
            ImportedKind.LOGIN -> {
                val uris = ArrayList<String>()
                if (item.url.isNotBlank()) uris += "{\"match\": null, \"uri\": ${q(item.url)}}"
                if (item.appId.isNotBlank()) uris += "{\"match\": null, \"uri\": ${q("androidapp://" + item.appId)}}"
                sb.append(", \"login\": {\"username\": ${q(item.username)}, \"password\": ${q(item.password)}, ")
                sb.append("\"totp\": ${if (item.totp.isBlank()) "null" else q(item.totp)}, \"uris\": [${uris.joinToString(", ")}]}")
            }
            ImportedKind.CARD -> {
                val (month, year) = splitExpiry(item.cardExpiry)
                sb.append(", \"card\": {\"cardholderName\": ${q(item.cardholder)}, \"brand\": null, \"number\": ${q(item.cardNumber)}, ")
                sb.append("\"expMonth\": ${q(month)}, \"expYear\": ${q(year)}, \"code\": ${q(item.cardCvv)}}")
            }
            ImportedKind.NOTE -> sb.append(", \"secureNote\": {\"type\": 0}")
        }
        sb.append("}")
        return sb.toString()
    }

    /** "08/28" becomes ("8", "2028"); anything else becomes empty strings. */
    private fun splitExpiry(expiry: String): Pair<String, String> {
        val parts = expiry.split('/', '-').map { it.trim() }
        val month = parts.getOrNull(0)?.toIntOrNull()
        val yearRaw = parts.getOrNull(1)?.toIntOrNull()
        if (month == null || yearRaw == null || month !in 1..12) return "" to ""
        val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
        return month.toString() to year.toString()
    }

    /** Logins only, with the column names Chrome, Bitwarden and most importers understand. */
    fun toCsv(items: List<ImportedLogin>): String {
        val out = StringBuilder("name,url,username,password,note\n")
        for (item in items) {
            if (item.kind != ImportedKind.LOGIN) continue
            out.append(listOf(item.title, item.url, item.username, item.password, item.notes).joinToString(",") { csv(it) }).append('\n')
        }
        return out.toString()
    }

    /** Quotes a CSV cell, and defuses cells that a spreadsheet would run as a formula (=, +, -, @). */
    private fun csv(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun q(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append("\\u%04x".format(c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }
}
