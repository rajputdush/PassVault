package com.ritvik.passvault

import com.ritvik.passvault.ui.Strings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast

/** Copies secrets to the clipboard, marks them sensitive (hides the preview on Android 13+), and clears them later. */
object ClipboardHelper {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    /** [clearAfterSeconds] of 0 means the clipboard is never cleared automatically. */
    fun copy(context: Context, label: String, text: String, clearAfterSeconds: Int) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        manager.setPrimaryClip(clip)

        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null
        val message = if (clearAfterSeconds > 0) {
            val task = Runnable { clear(manager) }
            pendingClear = task
            handler.postDelayed(task, clearAfterSeconds * 1000L)
            "$label copied. Clipboard clears in $clearAfterSeconds seconds."
        } else {
            "$label copied."
        }
        Toast.makeText(appContext, Strings.tr(message), Toast.LENGTH_SHORT).show()
    }

    private fun clear(manager: ClipboardManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
