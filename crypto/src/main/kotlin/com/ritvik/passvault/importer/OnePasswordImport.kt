package com.ritvik.passvault.importer

import com.ritvik.passvault.autofill.DomainMatcher
import com.ritvik.passvault.totp.Totp
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads a 1Password export (.1pux). It is a zip file holding `export.data`, a JSON file with accounts, vaults and
 * items. Logins, passwords, cards and secure notes come across with their notes, 2FA secrets and extra fields.
 * Items in 1Password's trash and other kinds (identities, bank accounts, licences, ...) are skipped.
 */
internal object OnePasswordImport {
    private const val MAX_UNZIPPED = 60L * 1024 * 1024
    private const val MAX_FIELDS = 20

    fun looksLikeZip(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun parse(bytes: ByteArray): ImportPreview {
        val json = readExportData(bytes)
        val root = MiniJson.parse(json) as? Map<*, *> ?: throw notOnePux()
        val accounts = root["accounts"] as? List<*> ?: throw notOnePux()
        val out = ArrayList<ImportedLogin>()
        var skipped = 0
        for (account in accounts) {
            for (vault in ((account as? Map<*, *>)?.get("vaults") as? List<*>).orEmpty()) {
                val vaultMap = vault as? Map<*, *> ?: continue
                val folder = ((vaultMap["attrs"] as? Map<*, *>)?.get("name") as? String).orEmpty().trim()
                for (raw in (vaultMap["items"] as? List<*>).orEmpty()) {
                    if (out.size + skipped > PasswordImport.MAX_ROWS) {
                        throw ImportFormatException("The file has more than ${PasswordImport.MAX_ROWS} items. Split it and import in parts.")
                    }
                    val item = raw as? Map<*, *>
                    val converted = item?.let { convert(it, folder) }
                    if (converted == null) skipped++ else out += converted
                }
            }
        }
        return ImportPreview(out, skipped, "1Password")
    }

    private fun notOnePux() = ImportFormatException("This file is not a 1Password export (.1pux).")

    private fun readExportData(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "export.data") {
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > MAX_UNZIPPED) throw ImportFormatException("The file is too large to be a password export.")
                        buffer.write(chunk, 0, n)
                    }
                    return buffer.toString(Charsets.UTF_8.name())
                }
            }
        }
        throw notOnePux()
    }

    private fun convert(item: Map<*, *>, folder: String): ImportedLogin? {
        if (item["state"] == "archived" || item["trashed"] == true) return null
        val category = item["categoryUuid"] as? String
        val overview = item["overview"] as? Map<*, *> ?: return null
        val details = item["details"] as? Map<*, *> ?: return null
        val title = (overview["title"] as? String).orEmpty().trim()
        val favorite = ((item["favIndex"] as? Double) ?: 0.0) > 0.0
        val notes = (details["notesPlain"] as? String).orEmpty()

        // Every field in every section, flattened: (section title, field title, kind, value).
        val sectionFields = ArrayList<SectionField>()
        for (section in (details["sections"] as? List<*>).orEmpty()) {
            val s = section as? Map<*, *> ?: continue
            for (f in (s["fields"] as? List<*>).orEmpty()) {
                val field = f as? Map<*, *> ?: continue
                val value = field["value"] as? Map<*, *> ?: continue
                val (kind, text) = value.entries.firstOrNull { it.value != null && it.key is String }
                    ?.let { (it.key as String) to valueText(it.value) } ?: continue
                if (text.isEmpty()) continue
                sectionFields += SectionField((field["title"] as? String).orEmpty().trim(), kind, text)
            }
        }

        return when (category) {
            "001", "005" -> {
                val loginFields = (details["loginFields"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
                fun designated(name: String) = loginFields.firstOrNull { it["designation"] == name }?.get("value") as? String
                val username = designated("username").orEmpty().trim()
                val password = designated("password") ?: (details["password"] as? String).orEmpty()
                val urls = ((overview["urls"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("url") as? String } +
                    listOfNotNull(overview["url"] as? String)).map { it.trim() }.filter { it.isNotEmpty() }
                val url = urls.firstOrNull().orEmpty()
                if (title.isEmpty() && url.isEmpty() && username.isEmpty() && password.isEmpty()) return null

                val extras = ArrayList<ImportedField>()
                var totp = ""
                for (f in sectionFields) {
                    if (f.kind == "totp") {
                        if (totp.isEmpty() && Totp.isValid(f.text)) totp = f.text
                        else extras += ImportedField("2FA key (not usable here)", f.text, hidden = true)
                    } else {
                        extras += ImportedField(f.title.ifEmpty { "Field" }, f.text, hidden = f.kind == "concealed")
                    }
                }
                ImportedLogin(
                    title = title.ifEmpty { DomainMatcher.hostOf(url) ?: username.ifEmpty { "Imported login" } },
                    url = url, username = username, password = password, notes = notes,
                    kind = ImportedKind.LOGIN, totp = totp, folder = folder, favorite = favorite,
                    customFields = extras.take(MAX_FIELDS),
                )
            }
            "002" -> {
                var number = ""
                var holder = ""
                var expiry = ""
                var cvv = ""
                var pin = ""
                val extras = ArrayList<ImportedField>()
                for (f in sectionFields) {
                    val name = f.title.lowercase()
                    when {
                        f.kind == "creditCardNumber" || name == "number" || name == "card number" -> number = f.text.filter { it.isDigit() }
                        name.contains("cardholder") || name == "name on card" -> holder = f.text.trim()
                        f.kind == "monthYear" || name.startsWith("expiry") -> expiry = monthYear(f.text)
                        name.contains("verification") || name == "cvv" || name == "cvc" -> cvv = f.text.trim()
                        name == "pin" -> pin = f.text.trim()
                        f.kind == "creditCardType" -> Unit
                        else -> extras += ImportedField(f.title.ifEmpty { "Field" }, f.text, hidden = f.kind == "concealed")
                    }
                }
                if (number.isEmpty() && holder.isEmpty() && title.isEmpty()) return null
                if (pin.isNotEmpty()) extras.add(0, ImportedField("PIN", pin, hidden = true))
                ImportedLogin(
                    title = title, url = "", username = "", password = "", notes = notes, kind = ImportedKind.CARD,
                    folder = folder, favorite = favorite, customFields = extras.take(MAX_FIELDS),
                    cardholder = holder, cardNumber = number, cardExpiry = expiry, cardCvv = cvv,
                )
            }
            "003" -> {
                if (title.isEmpty() && notes.isEmpty()) return null
                ImportedLogin(
                    title = title.ifEmpty { "Imported note" }, url = "", username = "", password = "", notes = notes,
                    kind = ImportedKind.NOTE, folder = folder, favorite = favorite,
                    customFields = sectionFields.map { ImportedField(it.title.ifEmpty { "Field" }, it.text, it.kind == "concealed") }.take(MAX_FIELDS),
                )
            }
            else -> null
        }
    }

    private class SectionField(val title: String, val kind: String, val text: String)

    private fun valueText(v: Any?): String = when (v) {
        is String -> v
        is Double -> if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(", ") { "${it.key}: ${valueText(it.value)}" }
        else -> ""
    }

    /** 1Password stores an expiry as a number like 202812 (year 2028, month 12). Becomes "12/28". */
    private fun monthYear(text: String): String {
        val digits = text.filter { it.isDigit() }
        if (digits.length != 6) return ""
        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4).toInt()
        if (month !in 1..12) return ""
        return "%02d/%02d".format(month, year % 100)
    }
}
