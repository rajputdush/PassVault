package com.ritvik.passvault.export

import com.ritvik.passvault.autofill.DomainMatcher

/** What the finder needs to know about one saved item. */
class DuplicateCandidate(
    val id: String,
    val isLogin: Boolean,
    val isCard: Boolean,
    val url: String,
    val username: String,
    val password: String,
    val cardNumber: String,
    val updatedAt: Long,
)

/** Items that are the same thing saved more than once. [keepId] is the most recently changed one. */
class DuplicateGroup(
    val ids: List<String>,
    val keepId: String,
    /** True when everything matches (same password too). False = same account but different passwords. */
    val identical: Boolean,
    val isCard: Boolean,
)

object DuplicateFinder {
    fun find(items: List<DuplicateCandidate>): List<DuplicateGroup> {
        val groups = ArrayList<DuplicateGroup>()

        // Logins: the same site and username.
        items.filter { it.isLogin && (it.username.isNotBlank() || it.url.isNotBlank()) }
            .groupBy { (DomainMatcher.hostOf(it.url) ?: it.url.trim().lowercase()) to it.username.trim().lowercase() }
            .values.filter { it.size > 1 }
            .forEach { same ->
                val newest = same.maxByOrNull { it.updatedAt }!!
                groups += DuplicateGroup(same.map { it.id }, newest.id, same.map { it.password }.distinct().size == 1, isCard = false)
            }

        // Cards: the same number.
        items.filter { it.isCard && it.cardNumber.any { c -> c.isDigit() } }
            .groupBy { it.cardNumber.filter { c -> c.isDigit() } }
            .values.filter { it.size > 1 }
            .forEach { same ->
                val newest = same.maxByOrNull { it.updatedAt }!!
                groups += DuplicateGroup(same.map { it.id }, newest.id, identical = true, isCard = true)
            }
        return groups
    }
}
