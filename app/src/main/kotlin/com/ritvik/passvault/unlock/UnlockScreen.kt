package com.ritvik.passvault.unlock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.canUseBiometrics
import com.ritvik.passvault.startBiometricUnlock
import com.ritvik.passvault.ui.AppTextField
import com.ritvik.passvault.ui.ErrorText
import com.ritvik.passvault.ui.PrimaryButton
import com.ritvik.passvault.ui.ScreenColumn
import com.ritvik.passvault.ui.ScreenHeader
import com.ritvik.passvault.ui.SecondaryButton

@Composable
fun UnlockHost(container: AppContainer, activity: FragmentActivity) {
    val viewModel: UnlockViewModel = viewModel(
        factory = viewModelFactory { initializer { UnlockViewModel(container) } },
    )
    val state by viewModel.state.collectAsState()
    val biometricReady = container.biometricVault.isEnabled() && canUseBiometrics(activity)

    // Offer the fingerprint prompt straight away when the lock screen appears.
    LaunchedEffect(Unit) {
        if (biometricReady) startBiometricUnlock(activity, container, viewModel)
    }

    UnlockScreen(
        state = state,
        biometricReady = biometricReady,
        onUnlock = viewModel::unlock,
        onBiometric = { startBiometricUnlock(activity, container, viewModel) },
        onShowRecovery = viewModel::showRecovery,
        onRecoveryReset = viewModel::resetWithRecoveryKey,
    )
}

@Composable
fun UnlockScreen(
    state: UnlockUiState,
    biometricReady: Boolean,
    onUnlock: (String) -> Unit,
    onBiometric: () -> Unit,
    onShowRecovery: (Boolean) -> Unit,
    onRecoveryReset: (recoveryKey: String, newPassword: String, confirmation: String) -> Unit,
) {
    ScreenColumn(centered = true) {
        when (state.mode) {
            UnlockMode.PASSWORD -> PasswordUnlock(state, biometricReady, onUnlock, onBiometric, onShowRecovery)
            UnlockMode.RECOVERY -> RecoveryReset(state, onShowRecovery, onRecoveryReset)
        }
    }
}

@Composable
private fun PasswordUnlock(
    state: UnlockUiState,
    biometricReady: Boolean,
    onUnlock: (String) -> Unit,
    onBiometric: () -> Unit,
    onShowRecovery: (Boolean) -> Unit,
) {
    // remember {} on purpose: rememberSaveable would write the password into the saved-state Bundle.
    var password by remember { mutableStateOf("") }

    ScreenHeader(
        icon = Icons.Filled.Lock,
        title = "PassVault is locked",
        subtitle = "Enter your master password to continue.",
    )

    AppTextField(
        value = password,
        onValueChange = { password = it },
        label = "Master password",
        secret = true,
        enabled = !state.working,
        imeAction = ImeAction.Done,
        onImeAction = { onUnlock(password) },
    )

    ErrorText(state.error)

    PrimaryButton(
        text = "Unlock",
        onClick = { onUnlock(password) },
        enabled = password.isNotEmpty(),
        loading = state.working,
    )

    if (biometricReady) {
        SecondaryButton(
            text = "Use fingerprint",
            onClick = onBiometric,
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    TextButton(
        onClick = { onShowRecovery(true) },
        enabled = !state.working,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Forgot password? Use recovery key")
    }
}

@Composable
private fun RecoveryReset(
    state: UnlockUiState,
    onShowRecovery: (Boolean) -> Unit,
    onReset: (String, String, String) -> Unit,
) {
    var recoveryKey by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }

    ScreenHeader(
        icon = Icons.Filled.Lock,
        title = "Reset with recovery key",
        subtitle = "Enter the recovery key you saved when you created the vault, then choose a new master password. Your saved items stay as they are.",
    )

    AppTextField(
        value = recoveryKey,
        onValueChange = { recoveryKey = it },
        label = "Recovery key",
        enabled = !state.working,
        singleLine = false,
        minLines = 2,
    )
    AppTextField(
        value = newPassword,
        onValueChange = { newPassword = it },
        label = "New master password",
        secret = true,
        revealed = showPasswords,
        enabled = !state.working,
        trailing = @Composable {
            TextButton(onClick = { showPasswords = !showPasswords }) { Text(if (showPasswords) "Hide" else "Show") }
        },
    )
    com.ritvik.passvault.ui.PasswordStrengthBar(newPassword)
    AppTextField(
        value = confirmation,
        onValueChange = { confirmation = it },
        label = "Confirm new password",
        secret = true,
        revealed = showPasswords,
        enabled = !state.working,
        imeAction = ImeAction.Done,
        onImeAction = { onReset(recoveryKey, newPassword, confirmation) },
    )

    ErrorText(state.error)

    PrimaryButton(
        text = "Reset password and unlock",
        onClick = { onReset(recoveryKey, newPassword, confirmation) },
        enabled = recoveryKey.isNotBlank(),
        loading = state.working,
    )
    TextButton(
        onClick = { onShowRecovery(false) },
        enabled = !state.working,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Back")
    }
}
