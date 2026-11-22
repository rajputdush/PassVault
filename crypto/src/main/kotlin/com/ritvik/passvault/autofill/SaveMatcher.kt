package com.ritvik.passvault.autofill

/**
 * Decides whether a login the user just submitted in another app or website is an update to a saved login
 * (same site or app, same username) or a brand-new one.
 */
object SaveMatcher {
    /**
     * @param webDomain the site reported by the browser, or null for a native app
     * @param appId the app's package name (used only when [webDomain] is null)
     * @param appLabel the app's name; logins saved before app ids existed are matched by it
     * @param username what was typed into the username box (blank if the form had none, e.g. a "change password" page)
     * @return the saved login to update, or null if a new one should be created
     */
    fun <T> findExisting(
        items: List<T>,
        webDomain: String?,
        appId: String?,
        appLabel: String?,
        username: String,
        urlOf: (T) -> String,
        titleOf: (T) -> String,
        appIdOf: (T) -> String,
        usernameOf: (T) -> String,
    ): T? {
        val samePlace = if (webDomain != null) {
            items.filter { DomainMatcher.matches(urlOf(it), webDomain) }
        } else {
            items.filter { item ->
                if (urlOf(item).isNotBlank()) return@filter false
                val linked = appIdOf(item)
                when {
                    linked.isNotBlank() -> !appId.isNullOrBlank() && linked.equals(appId, ignoreCase = true)
                    // Older app logins have no package: fall back to the app's name.
                    else -> !appLabel.isNullOrBlank() && titleOf(item).trim().equals(appLabel.trim(), ignoreCase = true)
                }
            }
        }
        val name = username.trim()
        return if (name.isNotEmpty()) {
            samePlace.firstOrNull { usernameOf(it).trim().equals(name, ignoreCase = true) }
        } else {
            // No username on the form: only safe to update if the site has exactly one saved login.
            samePlace.singleOrNull()
        }
    }

    /** Logins to offer directly inside an app: only those linked to exactly this package. */
    fun <T> forApp(items: List<T>, appId: String?, appIdOf: (T) -> String, urlOf: (T) -> String): List<T> {
        val id = AppIds.linkable(appId) ?: return emptyList()
        return items.filter { appIdOf(it).equals(id, ignoreCase = true) && urlOf(it).isBlank() }
    }
}
