package com.ritvik.passvault.autofill

/**
 * Decides whether a saved login's web address belongs to the site a browser reports, so passwords are only
 * offered to the real site and never to a look-alike ("github.com.evil.com" or "notgithub.com").
 *
 * Two addresses match when they share the same registrable domain (example.com matches login.example.com).
 * This uses a short built-in list of two-part public suffixes (co.uk, com.au ...) and shared-hosting domains
 * (github.io, vercel.app ...) instead of the full Public Suffix List; unknown ones fall back to "last two labels".
 */
object DomainMatcher {
    private val TWO_PART_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.in", "net.in", "org.in", "ac.in", "gov.in", "edu.in", "co.jp", "ne.jp", "or.jp", "ac.jp",
        "com.br", "net.br", "org.br", "com.cn", "net.cn", "org.cn", "com.mx", "co.nz", "co.za", "com.sg",
        "com.tr", "com.ar", "co.kr", "com.hk", "com.tw", "co.id", "com.my", "com.ph", "com.pk", "com.bd",
        // Shared hosting: every customer is a different owner, so these behave like public suffixes.
        "github.io", "gitlab.io", "blogspot.com", "herokuapp.com", "vercel.app", "netlify.app", "pages.dev",
        "web.app", "firebaseapp.com", "azurewebsites.net", "appspot.com", "workers.dev", "onrender.com",
        "wordpress.com", "myshopify.com", "s3.amazonaws.com", "cloudfront.net",
    )

    /** [savedUrl] is what the user typed for the login (may lack a scheme); [webDomain] is what the browser reports. */
    fun matches(savedUrl: String, webDomain: String?): Boolean {
        val saved = hostOf(savedUrl) ?: return false
        val site = hostOf(webDomain ?: return false) ?: return false
        if (saved == site) return true
        if (isIp(saved) || isIp(site)) return false
        return registrable(saved) == registrable(site)
    }

    /** Lower-case host without scheme, credentials, port, path or a leading "www.", or null if there is none. */
    fun hostOf(input: String): String? {
        var s = input.trim().lowercase()
        if (s.isEmpty()) return null
        s = s.substringAfter("://", s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@')
        s = s.substringBefore(':')
        s = s.trim('.')
        if (s.startsWith("www.")) s = s.removePrefix("www.")
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        return s
    }

    fun registrable(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        val take = if (lastTwo in TWO_PART_SUFFIXES) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    private fun isIp(host: String) = host.all { it.isDigit() || it == '.' } && host.contains('.')
}
