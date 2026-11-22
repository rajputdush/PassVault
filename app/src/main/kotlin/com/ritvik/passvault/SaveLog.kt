package com.ritvik.passvault

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remembers what happened to the last few "save to PassVault" prompts, so a save that seems to do nothing can be
 * explained. It only records the kind (login or card) and the outcome. Never a number, password, name or website.
 */
object SaveLog {
    private const val MAX_LINES = 8
    private fun file(context: Context) = File(context.filesDir, "save_log.txt")

    @Synchronized
    fun record(context: Context, kind: String, outcome: String) {
        try {
            val stamp = SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).format(Date())
            val lines = (read(context)?.lines().orEmpty() + "$stamp  $kind: $outcome").takeLast(MAX_LINES)
            file(context).writeText(lines.joinToString("\n"))
        } catch (_: Throwable) {
            // A logging problem must never get in the way of saving.
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        file(context).delete()
    }
}
