package com.ritvik.passvault.autofill

/** Android application ids (package names) such as com.netflix.mediaclient. */
object AppIds {
    private val PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    /**
     * Browsers are never matched by package: one browser shows every website, so a login "for Chrome" would be
     * offered on every page. In a browser the website address decides instead.
     */
    private val BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "com.google.android.apps.chrome",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus", "org.mozilla.klar",
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
        "com.sec.android.app.sbrowser", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
        "com.kiwibrowser.browser", "com.UCMobile.intl", "com.uc.browser.en", "com.yandex.browser",
        "org.chromium.chrome", "org.torproject.torbrowser", "com.mi.globalbrowser", "com.android.browser",
        "com.google.android.googlequicksearchbox", "com.ecosia.android", "com.cloudmosa.puffinFree",
        "org.bromite.bromite", "org.cromite.cromite", "com.stoutner.privacybrowser.standard",
    )

    fun isValid(packageName: String?): Boolean = packageName != null && packageName.length <= 200 && PATTERN.matches(packageName)

    fun isBrowser(packageName: String?): Boolean =
        packageName != null && packageName.trim().lowercase() in BROWSERS.map { it.lowercase() }

    /** The package to remember for a login saved from an app, or null (a browser, or something that is not a package name). */
    fun linkable(packageName: String?): String? = packageName?.trim()?.takeIf { isValid(it) && !isBrowser(it) }

    /** "com.netflix.mediaclient" becomes "Mediaclient": a readable name when the app's real label is not available. */
    fun readableName(packageName: String): String {
        val skip = setOf("com", "org", "net", "io", "android", "app", "mobile", "www", "me", "co")
        val parts = packageName.split('.').filter { it.isNotEmpty() }
        val best = parts.lastOrNull { it.lowercase() !in skip } ?: parts.lastOrNull() ?: packageName
        return best.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
    }
}
