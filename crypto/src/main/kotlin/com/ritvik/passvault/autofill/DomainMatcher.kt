package com.ritvik.passvault.autofill

/**
 * Decides whether a saved login's web address belongs to the site a browser reports, so passwords are only
 * offered to the real site and never to a look-alike ("github.com.evil.com" or "notgithub.com").
 *
 * Two addresses match when they share the same registrable domain (example.com matches login.example.com).
 * "Registrable domain" comes from the Mozilla Public Suffix List (compiled into the app, see [PublicSuffixData]), so
 * multi-part endings (co.uk, co.za, edu.ng) and shared hosting (github.io, cloudapp.net, s3.amazonaws.com ...) are handled:
 * two customers of the same hosting service never match each other.
 */
object DomainMatcher {
    private val plainRules = HashSet<String>()
    private val wildcardRules = HashSet<String>() // stored without the leading "*."
    private val exceptionRules = HashSet<String>() // stored without the leading "!"

    init {
        for (chunk in PublicSuffixData.CHUNKS) {
            for (rule in chunk.split(' ')) {
                when {
                    rule.isEmpty() -> Unit
                    rule.startsWith("!") -> exceptionRules.add(rule.substring(1))
                    rule.startsWith("*.") -> wildcardRules.add(rule.substring(2))
                    else -> plainRules.add(rule)
                }
            }
        }
    }

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

    /**
     * The part of [host] a single owner can register: the public suffix plus one more label
     * ("login.example.co.uk" gives "example.co.uk", "a.github.io" gives "a.github.io").
     */
    fun registrable(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 1) return host
        var suffixLabels = 1 // the default rule: every top-level domain is a public suffix
        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (candidate in exceptionRules) {
                suffixLabels = labels.size - i - 1
                break
            }
            if (candidate in plainRules) {
                suffixLabels = labels.size - i
                break
            }
            if (i + 1 < labels.size && labels.subList(i + 1, labels.size).joinToString(".") in wildcardRules) {
                suffixLabels = labels.size - i
                break
            }
        }
        val take = suffixLabels + 1
        return if (labels.size <= take) host else labels.takeLast(take).joinToString(".")
    }

    private fun isIp(host: String) = host.all { it.isDigit() || it == '.' } && host.contains('.')
}
