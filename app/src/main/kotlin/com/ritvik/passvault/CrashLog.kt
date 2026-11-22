package com.ritvik.passvault

import android.content.Context
import android.content.Intent
import android.os.Build
import com.ritvik.passvault.diagnostics.CrashReport
import java.io.File

/**
 * Remembers the last crash on the phone, in a small text file. Nothing is sent anywhere automatically: the user can
 * look at it and choose to share it from Settings → About. The report has no exception messages (see [CrashReport]).
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file(app).writeText(
                    CrashReport.format(
                        error,
                        appVersion = BuildConfig.VERSION_NAME,
                        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        device = "${Build.MANUFACTURER} ${Build.MODEL}",
                        timeMs = System.currentTimeMillis(),
                    ),
                )
            } catch (_: Throwable) {
                // Never let the logger itself hide the real crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Opens the Android share sheet so the user picks where (if anywhere) the report goes. */
    fun share(context: Context, report: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PassVault crash report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(send, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
