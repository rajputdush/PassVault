package com.ritvik.passvault

import com.ritvik.passvault.crypto.wipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the unlocked vault key in memory, and only while the vault is unlocked.
 * [lock] overwrites the key. The UI watches [unlocked] and switches to the unlock screen.
 */
class VaultSession {
    private var vaultKey: ByteArray? = null
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    val isUnlocked: Boolean get() = _unlocked.value

    /** Takes ownership of [key]; it is wiped on [lock]. */
    @Synchronized
    fun unlock(key: ByteArray) {
        vaultKey?.wipe()
        vaultKey = key
        _unlocked.value = true
    }

    @Synchronized
    fun lock() {
        vaultKey?.wipe()
        vaultKey = null
        _unlocked.value = false
    }

    /** Runs [block] with the key, or returns null if locked. Do not keep a reference to the array. */
    @Synchronized
    fun <T> withKey(block: (ByteArray) -> T): T? = vaultKey?.let(block)
}
