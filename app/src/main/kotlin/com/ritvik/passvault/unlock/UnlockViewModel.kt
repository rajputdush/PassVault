package com.ritvik.passvault.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.crypto.AttemptLimiter
import com.ritvik.passvault.crypto.InvalidRecoveryKeyException
import com.ritvik.passvault.crypto.PasswordPolicy
import com.ritvik.passvault.crypto.VaultFormatException
import com.ritvik.passvault.crypto.WrongCredentialsException
import com.ritvik.passvault.crypto.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher

enum class UnlockMode { PASSWORD, RECOVERY }

data class UnlockUiState(
    val mode: UnlockMode = UnlockMode.PASSWORD,
    val working: Boolean = false,
    val error: String? = null,
)

class UnlockViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(UnlockUiState())
    val state: StateFlow<UnlockUiState> = _state.asStateFlow()

    private val limiter get() = container.attemptLimiter

    fun showRecovery(show: Boolean) {
        _state.update { it.copy(mode = if (show) UnlockMode.RECOVERY else UnlockMode.PASSWORD, error = null) }
    }

    fun unlock(password: String) {
        if (_state.value.working || password.isEmpty()) return
        val wait = limiter.remainingLockMs()
        if (wait > 0) {
            fail("Too many wrong attempts. Try again in ${AttemptLimiter.describe(wait)}.")
            return
        }
        _state.update { it.copy(working = true, error = null) }
        val chars = password.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val header = container.headerStore.load() ?: throw VaultFormatException("Vault header is missing")
                val key = container.vaultCrypto.unlock(header, chars) // Argon2id: about a second
                limiter.recordSuccess()
                container.session.unlock(key)
                _state.update { it.copy(working = false) }
            } catch (e: WrongCredentialsException) {
                val delay = limiter.recordFailure()
                fail(
                    if (delay > 0) {
                        "Wrong master password. Too many attempts: try again in ${AttemptLimiter.describe(delay)}."
                    } else {
                        "Wrong master password."
                    },
                )
            } catch (e: VaultFormatException) {
                fail("The vault file is damaged or from a newer version of the app.")
            } catch (e: Exception) {
                fail("Could not unlock the vault. Please try again.")
            } finally {
                chars.wipe()
            }
        }
    }

    /**
     * "Forgot password": unlock with the recovery key and immediately choose a new master password.
     * The recovery key is 256 random bits, so guessing it is hopeless and it needs no attempt limit.
     */
    fun resetWithRecoveryKey(recoveryKey: String, newPassword: String, confirmation: String) {
        if (_state.value.working) return
        if (newPassword.length < PasswordPolicy.MIN_LENGTH) {
            _state.update { it.copy(error = "Use at least ${PasswordPolicy.MIN_LENGTH} characters for the new password.") }
            return
        }
        if (newPassword != confirmation) {
            _state.update { it.copy(error = "The two passwords don't match.") }
            return
        }
        _state.update { it.copy(working = true, error = null) }
        val chars = newPassword.toCharArray()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val header = container.headerStore.load() ?: throw VaultFormatException("Vault header is missing")
                val result = container.vaultCrypto.resetMasterPasswordWithRecoveryKey(header, recoveryKey, chars)
                container.headerStore.save(result.header) // persist the new header before unlocking
                limiter.recordSuccess()
                container.session.unlock(result.vaultKey)
                _state.update { UnlockUiState() }
            } catch (e: InvalidRecoveryKeyException) {
                fail("That doesn't look like a valid recovery key. Check it and try again.")
            } catch (e: WrongCredentialsException) {
                fail("That recovery key does not belong to this vault.")
            } catch (e: VaultFormatException) {
                fail("The vault file is damaged or from a newer version of the app.")
            } catch (e: Exception) {
                fail("Could not reset the password. Please try again.")
            } finally {
                chars.wipe()
            }
        }
    }

    fun onBiometricSuccess(cipher: Cipher) {
        try {
            container.session.unlock(container.biometricVault.read(cipher))
            limiter.recordSuccess()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Fingerprint unlock failed. Use your password.") }
        }
    }

    fun onBiometricError(message: String?) {
        if (message != null) _state.update { it.copy(error = message) }
    }

    private fun fail(message: String) {
        _state.update { it.copy(working = false, error = message) }
    }
}
