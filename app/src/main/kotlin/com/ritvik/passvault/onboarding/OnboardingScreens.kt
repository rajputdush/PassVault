package com.ritvik.passvault.onboarding

import com.ritvik.passvault.ui.Strings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ritvik.passvault.crypto.PasswordPolicy.Strength
import com.ritvik.passvault.ui.success
import com.ritvik.passvault.ui.warning
import com.ritvik.passvault.ui.AppTextField
import com.ritvik.passvault.ui.ErrorText
import com.ritvik.passvault.ui.PrimaryButton
import com.ritvik.passvault.ui.ScreenColumn
import com.ritvik.passvault.ui.ScreenHeader
import com.ritvik.passvault.ui.SecondaryButton
import com.ritvik.passvault.security.EmergencyKit
import android.widget.Toast
import java.io.IOException
import java.time.LocalDate

@Composable
fun CreatePasswordScreen(
    state: OnboardingUiState,
    onPasswordChanged: (String) -> Unit,
    onCreate: (password: String, confirmation: String) -> Unit,
    onRestore: (Uri) -> Unit,
) {
    // Deliberately remember {} and not rememberSaveable {}: a saveable value would be written
    // to the saved-instance-state Bundle, which we do not want for a master password.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestore(uri)
    }

    ScreenColumn {
        ScreenHeader(
            icon = Icons.Filled.Lock,
            title = "Create your master password",
            subtitle = "One password unlocks everything. It is never stored or sent anywhere, so it cannot be reset for you. Choose something long that you will remember.",
        )

        AppTextField(
            value = password,
            onValueChange = {
                password = it
                onPasswordChanged(it)
            },
            label = "Master password",
            secret = true,
            revealed = visible,
            enabled = !state.working,
        )
        StrengthMeter(password.isNotEmpty(), state.strength)

        AppTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = "Confirm master password",
            secret = true,
            revealed = visible,
            enabled = !state.working,
            imeAction = ImeAction.Done,
            onImeAction = { onCreate(password, confirmation) },
        )

        TextButton(onClick = { visible = !visible }) {
            Text(if (visible) "Hide passwords" else "Show passwords")
        }

        ErrorText(state.error)

        PrimaryButton(
            text = "Create vault",
            onClick = { onCreate(password, confirmation) },
            loading = state.working,
        )

        TextButton(
            onClick = { restorePicker.launch(arrayOf("*/*")) },
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore from backup")
        }
    }
}

@Composable
private fun StrengthMeter(show: Boolean, strength: Strength) {
    if (!show) return
    val (fraction, label, color) = when (strength) {
        Strength.TOO_SHORT -> Triple(0.1f, "Too short (12+ characters)", MaterialTheme.colorScheme.error)
        Strength.WEAK -> Triple(0.35f, "Weak", MaterialTheme.colorScheme.error)
        Strength.FAIR -> Triple(0.65f, "Fair", MaterialTheme.colorScheme.warning)
        Strength.STRONG -> Triple(1f, "Strong", MaterialTheme.colorScheme.success)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecoveryKeyScreen(
    state: OnboardingUiState,
    onSaved: () -> Unit,
) {
    var confirmed by remember { mutableStateOf(false) }
    var kitWarning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val kitLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val saved = try {
                val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open the file")
                out.use { it.write(EmergencyKit.create(state.recoveryKey, LocalDate.now().toString())) }
                true
            } catch (e: Exception) {
                false
            }
            Toast.makeText(
                context,
                Strings.tr(if (saved) "Saved. Print it, then delete the file." else "Could not save the file."),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    if (kitWarning) {
        com.ritvik.passvault.ui.EmergencyKitWarningDialog(
            onContinue = { kitWarning = false; kitLauncher.launch("passvault-emergency-kit.pdf") },
            onDismiss = { kitWarning = false },
        )
    }

    ScreenColumn {
        ScreenHeader(
            icon = Icons.Filled.Warning,
            title = "Save your recovery key",
            subtitle = "If you forget your master password, this key is the only way back into your vault. Write it down or print it and keep it somewhere safe, away from this phone. It is shown only once.",
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.recoveryKey.split("-").chunked(4).joinToString("\n") { it.joinToString("-") },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(20.dp),
            )
        }

        SecondaryButton(
            text = "Save printable Emergency Kit (PDF)",
            onClick = { kitWarning = true },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }, enabled = !state.working)
            Text("I have saved my recovery key somewhere safe", style = MaterialTheme.typography.bodyMedium)
        }

        ErrorText(state.error)

        PrimaryButton(
            text = "Continue",
            onClick = onSaved,
            enabled = confirmed,
            loading = state.working,
        )
    }
}
