package com.ritvik.passvault.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.backup.BackupCodec
import com.ritvik.passvault.backup.BackupFormatException
import com.ritvik.passvault.crypto.PasswordPolicy
import com.ritvik.passvault.crypto.VaultCrypto
import com.ritvik.passvault.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { CREATE_PASSWORD, SHOW_RECOVERY_KEY, DONE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.CREATE_PASSWORD,
    val strength: PasswordPolicy.Strength = PasswordPolicy.Strength.TOO_SHORT,
    val error: String? = null,
    val working: Boolean = false,
    val recoveryKey: String = "",
)

/**
 * Flow: create master password -> show recovery key -> user confirms they saved it -> header is
 * written to disk and the vault is unlocked. Nothing is persisted until the user confirms, so
 * if the app is killed at the recovery screen they simply start over instead of ending up with a
 * vault whose recovery key they never saw.
 */
private const val MAX_BACKUP_BYTES = 20_000_000

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var pending: VaultCrypto.CreatedVault? = null

    fun onPasswordChanged(password: String) {
        val chars = password.toCharArray()
        val strength = PasswordPolicy.evaluate(chars)
        chars.wipe()
        _state.update { it.copy(strength = strength, error = null) }
    }

    fun createVault(password: String, confirmation: String) {
        if (_state.value.working) return
        if (password.length < PasswordPolicy.MIN_LENGTH) {
            _state.update { it.copy(error = "Use at least ${PasswordPolicy.MIN_LENGTH} characters.") }
            return
        }
        if (password != confirmation) {
            _state.update { it.copy(error = "The two passwords don't match.") }
            return
        }
        _state.update { it.copy(working = true, error = null) }

        val chars = password.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Argon2id uses 64 MiB and takes a moment, so it runs off the main thread.
                val created = container.vaultCrypto.createVault(chars)
                pending?.vaultKey?.wipe()
                pending = created
                _state.update {
                    it.copy(step = OnboardingStep.SHOW_RECOVERY_KEY, recoveryKey = created.recoveryKey, working = false)
                }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = "Could not create the vault. Please try again.") }
            } finally {
                chars.wipe()
            }
        }
    }

    /** The user confirmed they stored the recovery key: persist the header and unlock. */
    fun onRecoveryKeySaved() {
        val created = pending ?: return
        if (_state.value.working) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.headerStore.save(created.header)
                container.session.unlock(created.vaultKey) // the session now owns the key
                pending = null
                _state.update { it.copy(step = OnboardingStep.DONE, recoveryKey = "", working = false) }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = "Could not save the vault. Please try again.") }
            }
        }
    }

    /**
     * Restore on a new phone: reads a backup file, writes the encrypted items, then the header last
     * (the header's existence is what marks a vault as present, so a crash leaves a clean start).
     * The user then unlocks with the master password that was current when the backup was made.
     */
    fun restoreBackup(readBytes: () -> ByteArray) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = readBytes()
                if (bytes.size > MAX_BACKUP_BYTES) {
                    throw BackupFormatException("That file is too large to be a PassVault backup.")
                }
                val backup = BackupCodec.decode(String(bytes, Charsets.UTF_8))
                container.itemRepository.writeAll(backup.items)
                container.headerStore.save(backup.header)
                _state.update { it.copy(step = OnboardingStep.DONE, working = false) }
            } catch (e: BackupFormatException) {
                _state.update { it.copy(working = false, error = e.message ?: "Could not restore the backup.") }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = "Could not read that file.") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        pending?.vaultKey?.wipe()
        pending = null
    }
}
