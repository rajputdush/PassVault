package com.ritvik.passvault.sites

import com.ritvik.passvault.autofill.AppIds

/** One row of the vault list: a single item, or several accounts that belong to the same site or app. */
sealed interface ListEntry<out T> {
    data class Single<T>(val item: T) : ListEntry<T>
    data class Group<T>(val key: String, val items: List<T>) : ListEntry<T>
}

/**
 * Puts logins for the same website (or the same app) together, so several accounts on one site show up as one tidy
 * entry instead of near-identical rows.
 */
object SiteGroups {
    /**
     * What a login belongs to: its site's registrable domain (so mail.google.com and accounts.google.com are both
     * "google.com") or, with no website, the app it is linked to. Null when it has neither, which means "never group".
     */
    fun keyOf(url: String, appId: String): String? =
        SiteInfo.domainOf(url)?.let { "site:$it" } ?: AppIds.linkable(appId)?.let { "app:$it" }

    /**
     * Groups [items] by [keyOf], keeping the order they came in: a group sits where its first item was, so a
     * favourite account pulls its whole group up. A site with one login stays a plain single entry.
     */
    fun <T> group(items: List<T>, keyOf: (T) -> String?): List<ListEntry<T>> {
        val byKey = LinkedHashMap<String, MutableList<T>>()
        for (item in items) {
            val key = keyOf(item) ?: continue
            byKey.getOrPut(key) { mutableListOf() }.add(item)
        }
        val emitted = HashSet<String>()
        val out = ArrayList<ListEntry<T>>(items.size)
        for (item in items) {
            val key = keyOf(item)
            val members = key?.let { byKey[it] }
            when {
                key == null || members == null || members.size < 2 -> out += ListEntry.Single(item)
                emitted.add(key) -> out += ListEntry.Group(key, members.toList())
            }
        }
        return out
    }

    /**
     * Positions (in [usernames]) of accounts that look like the same one saved twice: the same non-empty username,
     * ignoring case and outer spaces.
     */
    fun duplicatePositions(usernames: List<String>): Set<Int> {
        val seen = HashMap<String, MutableList<Int>>()
        usernames.forEachIndexed { i, name ->
            val key = name.trim().lowercase()
            if (key.isNotEmpty()) seen.getOrPut(key) { mutableListOf() }.add(i)
        }
        return seen.values.filter { it.size > 1 }.flatten().toSet()
    }

    /** A short line for a collapsed group: "alice@x.com, bob@y.com +1". */
    fun accountSummary(usernames: List<String>, show: Int = 2): String {
        val names = usernames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return ""
        val head = names.take(show).joinToString(", ")
        return if (names.size > show) "$head +${names.size - show}" else head
    }
}
