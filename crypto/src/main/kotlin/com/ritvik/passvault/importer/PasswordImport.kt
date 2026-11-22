package com.ritvik.passvault.importer

import com.ritvik.passvault.autofill.DomainMatcher

enum class ImportedKind { LOGIN, CARD, NOTE }

class ImportedField(val label: String, val value: String, val hidden: Boolean)

/**
 * One item read from an import file. CSV files only produce plain logins; richer formats (Bitwarden JSON) also fill in
 * the rest. The name is historical: it can hold a card or a note too, see [kind].
 */
class ImportedLogin(
    val title: String,
    val url: String,
    val username: String,
    val password: String,
    val notes: String,
    val kind: ImportedKind = ImportedKind.LOGIN,
    val totp: String = "",
    val appId: String = "",
    val folder: String = "",
    val favorite: Boolean = false,
    val customFields: List<ImportedField> = emptyList(),
    val cardholder: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
)

class ImportPreview(
    val logins: List<ImportedLogin>,
    /** Rows that were not logins or had no password. */
    val skipped: Int,
    /** Human-readable guess at the source, e.g. "Chrome / Edge", for the confirmation message. */
    val source: String,
)

class ImportFormatException(message: String) : Exception(message)

/**
 * Reads the CSV files exported by Chrome/Edge/Brave, Firefox, Bitwarden, 1Password, LastPass and similar tools.
 * Columns are recognised by their header names, so unknown tools with ordinary headers work too.
 */
object PasswordImport {
    const val MAX_ROWS = 5000

    private val TITLE = listOf("name", "title", "login_name", "account", "site name")
    private val URL = listOf("url", "login_uri", "website", "web site", "origin", "uri", "hostname")
    private val USERNAME = listOf("username", "login_username", "user name", "user", "login", "email", "login username")
    private val PASSWORD = listOf("password", "login_password", "pass", "login password")
    private val NOTES = listOf("note", "notes", "extra", "comments", "comment")

    /** Reads whatever file the user picked: a 1Password .1pux (a zip), a Bitwarden JSON file or a CSV file. */
    fun parseFile(bytes: ByteArray): ImportPreview =
        if (OnePasswordImport.looksLikeZip(bytes)) OnePasswordImport.parse(bytes) else parse(String(bytes, Charsets.UTF_8))

    fun parse(text: String): ImportPreview {
        if (BitwardenImport.looksLikeJson(text)) return BitwardenImport.parse(text)
        val rows = CsvParser.parse(text)
        if (rows.size < 2) throw ImportFormatException("The file has no rows to import.")
        if (rows.size - 1 > MAX_ROWS) throw ImportFormatException("The file has more than $MAX_ROWS rows. Split it and import in parts.")

        val header = rows[0].map { it.trim().lowercase() }
        fun col(names: List<String>): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1

        val iPassword = col(PASSWORD)
        if (iPassword < 0) throw ImportFormatException("This file has no \"password\" column, so it does not look like a password export.")
        val iUrl = col(URL)
        val iUser = col(USERNAME)
        val iTitle = col(TITLE)
        val iNotes = col(NOTES)
        val iType = header.indexOf("type") // Bitwarden: login / note / card / identity

        val logins = ArrayList<ImportedLogin>()
        var skipped = 0
        for (row in rows.drop(1)) {
            fun cell(i: Int) = if (i in row.indices) row[i].trim() else ""
            val type = cell(iType).lowercase()
            val password = if (iPassword in row.indices) row[iPassword] else ""
            if ((iType >= 0 && type.isNotEmpty() && type != "login") || password.isEmpty()) {
                skipped++
                continue
            }
            val url = cell(iUrl)
            val username = cell(iUser)
            val title = cell(iTitle).ifEmpty { DomainMatcher.hostOf(url) ?: username.ifEmpty { "Imported login" } }
            logins += ImportedLogin(title, url, username, password, cell(iNotes))
        }
        return ImportPreview(logins, skipped, guessSource(header))
    }

    private fun guessSource(header: List<String>): String = when {
        "login_uri" in header && "login_password" in header -> "Bitwarden"
        "httprealm" in header || "formactionorigin" in header -> "Firefox"
        "grouping" in header && "extra" in header -> "LastPass"
        "title" in header && "url" in header && "username" in header && "password" in header -> "1Password"
        header.take(4) == listOf("name", "url", "username", "password") -> "Chrome / Edge / Brave"
        else -> "CSV file"
    }

    /**
     * Drops items that are already in the vault, and repeats inside the file.
     * Logins match on site + username + password; [existing] are those triples for what the vault holds.
     * Cards match on their number ([existingCards]); notes on title + text ([existingNotes], see [noteKey]).
     */
    fun withoutDuplicates(
        logins: List<ImportedLogin>,
        existing: List<Triple<String, String, String>>,
        existingCards: Collection<String> = emptyList(),
        existingNotes: Collection<String> = emptyList(),
    ): List<ImportedLogin> {
        fun key(url: String, user: String, pass: String) = Triple(DomainMatcher.hostOf(url) ?: url.lowercase(), user.lowercase(), pass)
        val seen = existing.mapTo(HashSet()) { key(it.first, it.second, it.third) }
        val seenCards = existingCards.mapTo(HashSet()) { it.filter { c -> c.isDigit() } }
        val seenNotes = HashSet<String>(existingNotes)
        return logins.filter {
            when (it.kind) {
                ImportedKind.LOGIN -> seen.add(key(it.url, it.username, it.password))
                ImportedKind.CARD -> it.cardNumber.isEmpty() || seenCards.add(it.cardNumber.filter { c -> c.isDigit() })
                ImportedKind.NOTE -> seenNotes.add(noteKey(it.title, it.notes))
            }
        }
    }

    fun noteKey(title: String, notes: String): String = title.trim().lowercase() + "\u0000" + notes.trim()
}
