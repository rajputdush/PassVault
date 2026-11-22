package com.ritvik.passvault.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.ritvik.passvault.crypto.PasswordGenerator
import kotlin.math.roundToInt

/** Lets the user shape a generated password or passphrase, then use it. */
@Composable
fun GeneratorDialog(onUse: (String) -> Unit, onDismiss: () -> Unit) {
    var passphraseMode by remember { mutableStateOf(false) }
    var length by remember { mutableStateOf(20) }
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var words by remember { mutableStateOf(5) }
    var capitalize by remember { mutableStateOf(true) }
    var addNumber by remember { mutableStateOf(false) }
    var separator by remember { mutableStateOf("-") }
    var seed by remember { mutableStateOf(0) }

    val result = remember(passphraseMode, length, upper, lower, digits, symbols, words, capitalize, addNumber, separator, seed) {
        if (passphraseMode) {
            PasswordGenerator.passphrase(words = words, separator = separator, capitalize = capitalize, addNumber = addNumber)
        } else {
            PasswordGenerator.generate(length = length, lower = lower, upper = upper, digits = digits, symbols = symbols)
        }
    }

    // Never allow switching off the last character type: a password needs at least one.
    val enabledTypes = listOf(upper, lower, digits, symbols).count { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        // A dialog is its own window, so it needs its own "no screenshots" flag to protect the password shown in it.
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Generate") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !passphraseMode, onClick = { passphraseMode = false }, label = { Text("Password") })
                    FilterChip(selected = passphraseMode, onClick = { passphraseMode = true }, label = { Text("Passphrase") })
                }

                Text(
                    result,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )

                if (passphraseMode) {
                    Text("$words words", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = words.toFloat(),
                        onValueChange = { words = it.roundToInt() },
                        valueRange = PasswordGenerator.MIN_WORDS.toFloat()..10f,
                    )
                    Text(
                        "About ${PasswordGenerator.passphraseBits(words).roundToInt()} bits of strength. Easy to type on a phone and easy to remember.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToggleRow("Capitalize words", capitalize) { capitalize = it }
                    ToggleRow("Add a number", addNumber) { addNumber = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Separator", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 4.dp))
                        listOf("-" to "-", "." to ".", "_" to "_", " " to "space").forEach { (value, label) ->
                            FilterChip(selected = separator == value, onClick = { separator = value }, label = { Text(label) })
                        }
                    }
                } else {
                    Text("$length characters", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = length.toFloat(),
                        onValueChange = { length = it.roundToInt() },
                        valueRange = 8f..64f,
                    )
                    ToggleRow("Uppercase (A-Z)", upper, enabled = !(upper && enabledTypes == 1)) { upper = it }
                    ToggleRow("Lowercase (a-z)", lower, enabled = !(lower && enabledTypes == 1)) { lower = it }
                    ToggleRow("Numbers (0-9)", digits, enabled = !(digits && enabledTypes == 1)) { digits = it }
                    ToggleRow("Symbols (!@#...)", symbols, enabled = !(symbols && enabledTypes == 1)) { symbols = it }
                }

                OutlinedButton(
                    onClick = { seed++ },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Generate another") }
            }
        },
        confirmButton = { TextButton(onClick = { onUse(result) }) { Text("Use this") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
