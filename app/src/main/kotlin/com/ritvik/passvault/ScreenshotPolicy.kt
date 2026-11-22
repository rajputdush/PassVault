package com.ritvik.passvault

import android.view.Window
import android.view.WindowManager

/** Allowed: screenshots, screen recording and the recents preview work. Not allowed: they are blocked (FLAG_SECURE). */
fun Window.applyScreenshotPolicy(allowed: Boolean) {
    if (allowed) {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }
}
