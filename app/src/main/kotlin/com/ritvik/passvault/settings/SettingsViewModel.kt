package com.ritvik.passvault.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritvik.passvault.ActivityLog
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.backup.BackupCodec
import com.ritvik.passvault.crypto.AttemptLimiter
import com.ritvik.passvault.crypto.InvalidRecoveryKeyException
import com.ritvik.passvault.crypto.PasswordPolicy
import com.ritvik.passvault.crypto.VaultFormatException
import com.ritvik.passvault.crypto.WrongCredentialsException
import com.ritvik.passvault.crypto.wipe
import com.ritvik.passvault.cards.CardInfo
import com.ritvik.passvault.importer.ImportFormatException
import com.ritvik.passvault.export.PlainExport
import com.ritvik.passvault.importer.ImportedField
import com.ritvik.passvault.importer.ImportedKind
import com.ritvik.passvault.importer.ImportedLogin
import com.ritvik.passvault.importer.PasswordImport
import com.ritvik.passvault.items.CustomField
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.security.EmergencyKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val working: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    /** Increases after each successful password change so the screen can clear its text fields. */
    val passwordChangedCount: Int = 0,
    /** A freshly created recovery key, shown once. Cleared as soon as the user leaves the screen or taps Done. */
    val newRecoveryKey: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val limiter get() = container.attemptLimiter

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun changePassword(current: String, newPassword: String, confirmation: String) {
        if (_state.value.working) return
        if (current.isEmpty()) {
            fail("Enter your current master password.")
            return
        }
        if (newPassword.length < PasswordPolicy.MIN_LENGTH) {
            fail("Use at least ${PasswordPolicy.MIN_LENGTH} characters for the new password.")
            return
        }
        if (newPassword != confirmation) {
            fail("The two new passwords don't match.")
            return
        }
        val wait = limiter.remainingLockMs()
        if (wait > 0) {
            fail("Too many wrong attempts. Try again in ${AttemptLimiter.describe(wait)}.")
            return
        }

        _state.update { it.copy(working = true, message = null) }
        val currentChars = current.toCharArray()
        val newChars = newPassword.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val header = container.headerStore.load() ?: throw VaultFormatException("Vault header is missing")
                // Proves the user knows the current password. Items are untouched: only the key wrapper changes.
                val vaultKey = container.vaultCrypto.unlock(header, currentChars)
                try {
                    val newHeader = container.vaultCrypto.changeMasterPassword(header, vaultKey, newChars)
                    container.headerStore.save(newHeader)
                } finally {
                    vaultKey.wipe()
                }
                limiter.recordSuccess()
                container.activityLog.record(ActivityLog.KIND_SECURITY, "Master password changed")
                _state.update {
                    it.copy(
                        working = false,
                        message = "Master password changed.",
                        isError = false,
                        passwordChangedCount = it.passwordChangedCount + 1,
                    )
                }
            } catch (e: WrongCredentialsException) {
                val delay = limiter.recordFailure()
                fail(
                    if (delay > 0) {
                        "Wrong current password. Try again in ${AttemptLimiter.describe(delay)}."
                    } else {
                        "Wrong current password."
                    },
                )
            } catch (e: Exception) {
                fail("Could not change the password. Please try again.")
            } finally {
                currentChars.wipe()
                newChars.wipe()
            }
        }
    }

    /** Lets the user check that the recovery key they wrote down is correct. Nothing is changed. */
    fun verifyRecoveryKey(recoveryKey: String) {
        if (_state.value.working || recoveryKey.isBlank()) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val header = container.headerStore.load() ?: throw VaultFormatException("Vault header is missing")
                container.vaultCrypto.unlockWithRecoveryKey(header, recoveryKey).wipe()
                _state.update { it.copy(working = false, message = "Your recovery key is correct.", isError = false) }
            } catch (e: InvalidRecoveryKeyException) {
                fail("That doesn't look like a valid recovery key.")
            } catch (e: WrongCredentialsException) {
                fail("This recovery key does not match your vault.")
            } catch (e: Exception) {
                fail("Could not check the recovery key.")
            }
        }
    }

    /**
     * Creates a new recovery key (the old one stops working). Needs the master password, which also proves that the
     * person holding the phone is the owner.
     */
    fun regenerateRecoveryKey(password: String) {
        if (_state.value.working || password.isEmpty()) return
        val wait = limiter.remainingLockMs()
        if (wait > 0) {
            fail("Too many wrong attempts. Try again in ${AttemptLimiter.describe(wait)}.")
            return
        }
        _state.update { it.copy(working = true, message = null, newRecoveryKey = null) }
        val chars = password.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val header = container.headerStore.load() ?: throw VaultFormatException("Vault header is missing")
                val vaultKey = container.vaultCrypto.unlock(header, chars)
                try {
                    val fresh = container.vaultCrypto.regenerateRecoveryKey(header, vaultKey)
                    container.headerStore.save(fresh.header)
                    limiter.recordSuccess()
                    container.activityLog.record(ActivityLog.KIND_SECURITY, "New recovery key created (the old one no longer works)")
                    _state.update {
                        it.copy(working = false, message = null, isError = false, newRecoveryKey = fresh.recoveryKey)
                    }
                } finally {
                    vaultKey.wipe()
                }
            } catch (e: WrongCredentialsException) {
                val delay = limiter.recordFailure()
                fail(if (delay > 0) "Wrong password. Try again in ${AttemptLimiter.describe(delay)}." else "Wrong password.")
            } catch (e: Exception) {
                fail("Could not create a new recovery key. Please try again.")
            } finally {
                chars.wipe()
            }
        }
    }

    /** Checks the master password (with the same wrong-attempt limits as unlocking) and then runs [onOk] on the main thread. */
    fun verifyPassword(password: String, onOk: () -> Unit) {
        if (_state.value.working || password.isEmpty()) return
        val wait = limiter.remainingLockMs()
        if (wait > 0) {
            fail("Too many wrong attempts. Try again in ${AttemptLimiter.describe(wait)}.")
            return
        }
        _state.update { it.copy(working = true, message = null) }
        val chars = password.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val header = container.headerStore.load() ?: throw VaultFormatException("Vault header is missing")
                container.vaultCrypto.unlock(header, chars).wipe()
                limiter.recordSuccess()
                _state.update { it.copy(working = false) }
                withContext(Dispatchers.Main) { onOk() }
            } catch (e: WrongCredentialsException) {
                val delay = limiter.recordFailure()
                fail(if (delay > 0) "Wrong password. Try again in ${AttemptLimiter.describe(delay)}." else "Wrong password.")
            } catch (e: Exception) {
                fail("Could not check the password. Please try again.")
            } finally {
                chars.wipe()
            }
        }
    }

    /** Writes every item (not those in the Trash) as an UNENCRYPTED Bitwarden-style JSON or a CSV of the logins. */
    fun exportPlain(json: Boolean, write: (ByteArray) -> Unit) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = synchronized(container.itemRepository.lock) {
                    container.session.withKey { key -> container.itemRepository.list(key) }
                }?.filter { !it.inTrash } ?: throw IllegalStateException("locked")
                val rows = items.map { it.toImported() }
                val text = if (json) PlainExport.toBitwardenJson(rows) else PlainExport.toCsv(rows)
                write(text.toByteArray(Charsets.UTF_8))
                val count = if (json) rows.size else rows.count { it.kind == ImportedKind.LOGIN }
                container.activityLog.record(
                    ActivityLog.KIND_SECURITY,
                    "Unprotected " + (if (json) "JSON" else "CSV") + " export saved ($count items)",
                )
                _state.update {
                    it.copy(
                        working = false,
                        isError = false,
                        message = "Saved $count item(s) as a plain-text file. It holds your passwords unprotected: import it where you need it, then delete it from your phone and from anywhere it was copied to.",
                    )
                }
            } catch (e: Exception) {
                fail("Could not save the file.")
            }
        }
    }

    private fun VaultItem.toImported() = ImportedLogin(
        title = displayTitle, url = url, username = username, password = password, notes = notes,
        kind = when (type) {
            ItemType.LOGIN -> ImportedKind.LOGIN
            ItemType.CARD -> ImportedKind.CARD
            ItemType.NOTE -> ImportedKind.NOTE
        },
        totp = totp, appId = appId, folder = folder, favorite = favorite,
        customFields = (if (cardPin.isNotBlank()) listOf(ImportedField("PIN", cardPin, true)) else emptyList()) +
            customFields.map { ImportedField(it.label, it.value, it.hidden) },
        cardholder = cardholder, cardNumber = cardNumber, cardExpiry = cardExpiry, cardCvv = cardCvv,
    )

    /** Forgets the new recovery key held for display. */
    fun dismissRecoveryKey() {
        _state.update { it.copy(newRecoveryKey = null) }
    }

    /** [write] receives the Emergency Kit PDF and must save it where the user chose. */
    fun saveEmergencyKit(write: (ByteArray) -> Unit) {
        val key = _state.value.newRecoveryKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                write(EmergencyKit.create(key, java.time.LocalDate.now().toString()))
                _state.update {
                    it.copy(
                        message = "Emergency Kit saved. Print it, keep the paper safe, then delete the file.",
                        isError = false,
                    )
                }
            } catch (e: Exception) {
                fail("Could not save the Emergency Kit.")
            }
        }
    }

    /** [write] receives the backup file contents and must save them where the user chose. */
    fun exportBackup(write: (ByteArray) -> Unit) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val header = container.headerStore.load() ?: throw VaultFormatException("Vault header is missing")
                val items = container.itemRepository.listRaw()
                write(BackupCodec.encode(header, items).toByteArray(Charsets.UTF_8))
                container.activityLog.record(ActivityLog.KIND_SECURITY, "Encrypted backup saved (${items.size} items)")
                _state.update {
                    it.copy(
                        working = false,
                        isError = false,
                        message = "Backup saved with ${items.size} item(s). To open it you need the master password you have now, or your recovery key.",
                    )
                }
            } catch (e: Exception) {
                fail("Could not save the backup.")
            }
        }
    }

    /** [read] returns the contents of the CSV file the user picked. */
    fun importPasswords(read: () -> ByteArray) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            var bytes: ByteArray? = null
            try {
                bytes = read()
                if (bytes.size > MAX_IMPORT_BYTES) throw ImportFormatException("The file is too large to be a password export.")
                val preview = PasswordImport.parseFile(bytes)
                val repo = container.itemRepository
                var logins = 0
                var cards = 0
                var notes = 0
                var duplicates = 0
                // Same lock order as everywhere else: items first, then the session key.
                val done = synchronized(repo.lock) {
                    container.session.withKey { key ->
                        val current = repo.list(key)
                        val fresh = PasswordImport.withoutDuplicates(
                            preview.logins,
                            existing = current.filter { it.type == ItemType.LOGIN }.map { Triple(it.url, it.username, it.password) },
                            existingCards = current.filter { it.type == ItemType.CARD }.map { it.cardNumber },
                            existingNotes = current.filter { it.type == ItemType.NOTE }.map { PasswordImport.noteKey(it.title, it.notes) },
                        )
                        duplicates = preview.logins.size - fresh.size
                        val now = System.currentTimeMillis()
                        fresh.forEach {
                            repo.save(key, toVaultItem(it, now))
                            when (it.kind) {
                                ImportedKind.LOGIN -> logins++
                                ImportedKind.CARD -> cards++
                                ImportedKind.NOTE -> notes++
                            }
                        }
                        true
                    }
                }
                if (done != true) {
                    fail("The vault is locked. Unlock it and try again.")
                    return@launch
                }
                container.activityLog.record(ActivityLog.KIND_ADDED, "Imported " + describeCounts(logins, cards, notes) + " from ${preview.source}")
                _state.update {
                    it.copy(
                        working = false,
                        isError = false,
                        message = "Imported " + describeCounts(logins, cards, notes) + " from ${preview.source}" +
                            (if (duplicates > 0) ", $duplicates already in your vault" else "") +
                            (if (preview.skipped > 0) ", ${preview.skipped} skipped (empty, no password or not supported)" else "") +
                            ". Now delete the file you imported: it holds your passwords in plain text.",
                    )
                }
            } catch (e: ImportFormatException) {
                fail(e.message ?: "This file could not be imported.")
            } catch (e: Exception) {
                fail("Could not read the file.")
            } finally {
                bytes?.fill(0)
            }
        }
    }

    private fun describeCounts(logins: Int, cards: Int, notes: Int): String {
        val parts = buildList {
            if (logins > 0 || (cards == 0 && notes == 0)) add("$logins login(s)")
            if (cards > 0) add("$cards card(s)")
            if (notes > 0) add("$notes note(s)")
        }
        return parts.joinToString(", ")
    }

    private fun toVaultItem(i: ImportedLogin, now: Long): VaultItem = VaultItem(
        id = VaultItem.newId(),
        title = i.title,
        username = i.username,
        password = i.password,
        url = i.url,
        notes = i.notes,
        updatedAt = now,
        type = when (i.kind) {
            ImportedKind.LOGIN -> ItemType.LOGIN
            ImportedKind.CARD -> ItemType.CARD
            ImportedKind.NOTE -> ItemType.NOTE
        },
        favorite = i.favorite,
        folder = i.folder.take(40),
        totp = i.totp,
        appId = i.appId,
        passwordChangedAt = now,
        createdAt = now,
        source = "import",
        cardholder = i.cardholder,
        cardNumber = CardInfo.digitsOnly(i.cardNumber),
        cardExpiry = i.cardExpiry,
        cardCvv = i.cardCvv,
        customFields = i.customFields.take(CustomField.MAX_FIELDS)
            .map { CustomField(it.label.take(CustomField.MAX_LABEL), it.value.take(CustomField.MAX_VALUE), it.hidden) },
    )

    private fun fail(message: String) {
        _state.update { it.copy(working = false, message = message, isError = true) }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 8 * 1024 * 1024
    }
}
