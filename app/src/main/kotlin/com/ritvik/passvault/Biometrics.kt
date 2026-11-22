package com.ritvik.passvault

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ritvik.passvault.unlock.UnlockViewModel
import javax.crypto.Cipher

/** True if the device has strong biometrics enrolled and ready. */
fun canUseBiometrics(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

fun Context.findFragmentActivity(): FragmentActivity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    error("No FragmentActivity found")
}

/**
 * Shows the system biometric prompt tied to [cipher]. [onFailure] gets null when the user simply
 * cancelled (nothing to show), or a message for real errors.
 */
fun FragmentActivity.authenticateWithBiometrics(
    cipher: Cipher,
    title: String,
    onSuccess: (Cipher) -> Unit,
    onFailure: (String?) -> Unit,
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val authenticated = result.cryptoObject?.cipher
            if (authenticated != null) onSuccess(authenticated) else onFailure("Fingerprint check failed.")
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            onFailure(if (cancelled) null else errString.toString())
        }
    }
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText("Use password")
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}

fun startBiometricUnlock(activity: FragmentActivity, container: AppContainer, viewModel: UnlockViewModel) {
    val cipher = try {
        container.biometricVault.newDecryptCipher()
    } catch (e: Exception) {
        // Most likely the key was invalidated because a fingerprint was added or removed.
        container.biometricVault.disable()
        viewModel.onBiometricError("Fingerprint unlock was turned off because your fingerprints changed. Use your password.")
        return
    }
    activity.authenticateWithBiometrics(
        cipher = cipher,
        title = "Unlock PassVault",
        onSuccess = viewModel::onBiometricSuccess,
        onFailure = viewModel::onBiometricError,
    )
}

/**
 * "Is it really you?" check while the vault is already unlocked. No key is involved, it only gates the screen.
 * [onFailure] gets null when the user cancelled.
 */
fun FragmentActivity.confirmItsYou(title: String, onSuccess: () -> Unit, onFailure: (String?) -> Unit) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            onFailure(if (cancelled) null else errString.toString())
        }
    }
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText("Cancel")
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(info)
}

/** Turns on fingerprint unlock. Must be called while the vault is unlocked. */
fun enableBiometricUnlock(activity: FragmentActivity, container: AppContainer, onResult: (Boolean, String?) -> Unit) {
    val cipher = try {
        container.biometricVault.newEncryptCipher()
    } catch (e: Exception) {
        onResult(false, "Could not set up fingerprint unlock on this device.")
        return
    }
    activity.authenticateWithBiometrics(
        cipher = cipher,
        title = "Enable fingerprint unlock",
        onSuccess = { authenticated ->
            try {
                container.session.withKey { container.biometricVault.store(authenticated, it) }
                onResult(true, null)
            } catch (e: Exception) {
                container.biometricVault.disable()
                onResult(false, "Could not save fingerprint unlock.")
            }
        },
        onFailure = { message ->
            container.biometricVault.disable()
            onResult(false, message)
        },
    )
}
