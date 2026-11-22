package com.ritvik.passvault.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritvik.passvault.ActivityLog
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.sites.SiteGroups
import com.ritvik.passvault.activity.ChangeSummary
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.items.TRASH_DAYS
import com.ritvik.passvault.items.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the decrypted item list in memory only while the vault is unlocked; it is cleared on lock. */
class VaultViewModel(private val container: AppContainer) : ViewModel() {
    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    /** Everything except items in the Trash. */
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _trash = MutableStateFlow<List<VaultItem>>(emptyList())
    /** Deleted items that can still be restored, most recently deleted first. */
    val trash: StateFlow<List<VaultItem>> = _trash.asStateFlow()

    init {
        viewModelScope.launch {
            container.session.unlocked.collect { unlocked ->
                if (unlocked) reload() else clear()
            }
        }
    }

    /** Saves from the edit screen and records whether it was a new item or a change. */
    fun saveEdited(item: VaultItem, isNew: Boolean) {
        val name = item.displayTitle
        if (isNew) {
            val note = siteNote(item)
            container.activityLog.record(
                ActivityLog.KIND_ADDED,
                ActivityLog.nameOf(item) + "\nAdded manually" + (if (note.isNotEmpty()) "\n$note" else ""),
            )
            container.notice.value = "Saved \"$name\" to PassVault"
        } else {
            // Say what changed (a new username, a new password, a new website ...) without writing any secret.
            val before = _items.value.firstOrNull { it.id == item.id }
            val changes = if (before != null) ChangeSummary.describe(ActivityLog.snapshotOf(before), ActivityLog.snapshotOf(item)) else emptyList()
            if (before == null || changes.isNotEmpty()) {
                // When the username itself changed, the first line names the item only, and the change line says
                // which username it was and which it is now.
                val usernameChanged = before != null && before.username.trim() != item.username.trim()
                val who = if (usernameChanged) before!!.displayTitle else ActivityLog.nameOf(before ?: item)
                container.activityLog.record(
                    ActivityLog.KIND_UPDATED,
                    if (changes.isEmpty()) who else who + "\n" + ChangeSummary.join(changes, (ChangeSummary.MAX_DETAIL - who.length - 1).coerceAtLeast(60)),
                )
            }
            val passwordChanged = before != null && before.password != item.password && before.password.isNotEmpty()
            container.notice.value = if (passwordChanged) "Password changed. The old one is kept in Password history." else "Changes to \"$name\" saved"
        }
        save(item)
    }

    /** Stars or un-stars an item from the list and records it. */
    fun toggleFavorite(item: VaultItem) {
        val now = !item.favorite
        container.activityLog.record(
            ActivityLog.KIND_UPDATED,
            ActivityLog.nameOf(item) + if (now) "\nAdded to favourites" else "\nRemoved from favourites",
        )
        save(item.copy(favorite = now))
    }

    /** "first account on amazon.com" or "another account on amazon.com, #2", for a login about to be saved. */
    private fun siteNote(item: VaultItem): String {
        if (item.type != ItemType.LOGIN) return ""
        val key = SiteGroups.keyOf(item.url, item.appId) ?: return ""
        val others = _items.value.count { it.id != item.id && it.type == ItemType.LOGIN && SiteGroups.keyOf(it.url, it.appId) == key }
        return ChangeSummary.accountNote(others, key.substringAfter(':'))
    }

    fun save(item: VaultItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Lock order is always item lock first, then session lock.
                synchronized(container.itemRepository.lock) {
                    container.session.withKey { key ->
                        // Older items never recorded when their password last changed, so it was guessed from the last
                        // edit, which made the date jump on every unrelated save. Pin it now: from here on it moves
                        // only when the password really changes.
                        val pinned = if (item.passwordChangedAt == 0L) item.copy(passwordChangedAt = item.passwordAge) else item
                        container.itemRepository.save(key, pinned.copy(updatedAt = System.currentTimeMillis()))
                    }
                }
            }
            reload()
        }
    }

    /** Moves an item to the Trash. It can be restored for [TRASH_DAYS] days. */
    fun delete(id: String) {
        log(ActivityLog.KIND_DELETE, id)
        update(id) { it.copy(deletedAt = System.currentTimeMillis()) }
    }

    fun restore(id: String) {
        log(ActivityLog.KIND_RESTORE, id)
        update(id) { it.copy(deletedAt = 0) }
    }

    private fun log(kind: String, id: String) {
        val title = (_items.value + _trash.value).firstOrNull { it.id == id }?.displayTitle ?: return
        container.activityLog.record(kind, title)
    }

    fun deleteForever(id: String) {
        log(ActivityLog.KIND_PURGE, id)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.itemRepository.delete(id) }
            reload()
        }
    }

    fun emptyTrash() {
        val ids = _trash.value.map { it.id }
        if (ids.isNotEmpty()) container.activityLog.record(ActivityLog.KIND_PURGE, "Emptied the Trash (${ids.size} items)")
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ids.forEach { container.itemRepository.delete(it) } }
            reload()
        }
    }

    private fun update(id: String, change: (VaultItem) -> VaultItem) {
        val current = (_items.value + _trash.value).firstOrNull { it.id == id } ?: return
        save(change(current))
    }

    private fun clear() {
        _items.value = emptyList()
        _trash.value = emptyList()
    }

    private suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) {
            container.session.withKey { key ->
                val all = container.itemRepository.list(key)
                // Items that have been in the Trash too long are removed for good.
                val cutoff = System.currentTimeMillis() - TRASH_DAYS * 24L * 60 * 60 * 1000
                val (expired, kept) = all.partition { it.inTrash && it.deletedAt < cutoff }
                expired.forEach { container.itemRepository.delete(it.id) }
                kept
            }
        }
        // If the vault locked while we were loading, withKey returns null and the lists stay empty.
        if (loaded == null) {
            clear()
            return
        }
        _items.value = loaded.filter { !it.inTrash }
        _trash.value = loaded.filter { it.inTrash }.sortedByDescending { it.deletedAt }
    }
}
