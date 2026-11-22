package com.ritvik.passvault.vault

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.ClipboardHelper
import com.ritvik.passvault.crypto.PasswordGenerator
import com.ritvik.passvault.crypto.PasswordPolicy
import com.ritvik.passvault.ui.ScreenColumn
import com.ritvik.passvault.ui.ScreenTitle
import com.ritvik.passvault.ui.Text
import com.ritvik.passvault.ui.success
import com.ritvik.passvault.ui.warning
import kotlin.math.roundToInt

/** The Generator tab: make a password or passphrase and copy it. Nothing is saved. */
@Composable
fun GeneratorScreen(container: AppContainer, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var passphraseMode by remember { mutableStateOf(false) }
    var length by remember { mutableStateOf(16) }
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var words by remember { mutableStateOf(5) }
    var seed by remember { mutableStateOf(0) }

    val result = remember(passphraseMode, length, upper, lower, digits, symbols, words, seed) {
        if (passphraseMode) {
            PasswordGenerator.passphrase(words = words, separator = "-", capitalize = true, addNumber = false)
        } else {
            PasswordGenerator.generate(length = length, lower = lower, upper = upper, digits = digits, symbols = symbols)
        }
    }
    val enabledTypes = listOf(upper, lower, digits, symbols).count { it }
    val strength = remember(result) { PasswordPolicy.evaluate(result.toCharArray()) }
    val (label, color) = when (strength) {
        PasswordPolicy.Strength.TOO_SHORT -> "Too short" to MaterialTheme.colorScheme.error
        PasswordPolicy.Strength.WEAK -> "Weak" to MaterialTheme.colorScheme.error
        PasswordPolicy.Strength.FAIR -> "Fair" to MaterialTheme.colorScheme.warning
        PasswordPolicy.Strength.STRONG -> "Very strong" to MaterialTheme.colorScheme.success
    }

    ScreenColumn {
        if (onBack != null) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        ScreenTitle("Tools", "Generator")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !passphraseMode, onClick = { passphraseMode = false }, label = { Text("Password") })
            FilterChip(selected = passphraseMode, onClick = { passphraseMode = true }, label = { Text("Passphrase") })
        }

        val cardShape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, cardShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                result,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    label,
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Box(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { seed++ }
                        .semantics { contentDescription = "Generate another" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(22.dp))
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(23.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            ClipboardHelper.copy(context, "Password", result, container.settings.clipboardClearSeconds)
                            container.activityLog.record(com.ritvik.passvault.ActivityLog.KIND_COPY, "Generated password (from the generator)")
                            container.notice.value = "Copied. It will be cleared from the clipboard soon."
                        }
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    Text("Copy", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        val panel = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, cardShape)

        if (passphraseMode) {
            Column(modifier = panel.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text("Words", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(words.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
            }
        } else {
            Column(modifier = panel.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text("Length", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(length.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(value = length.toFloat(), onValueChange = { length = it.roundToInt() }, valueRange = 8f..64f)
            }
            Column(modifier = panel) {
                ToggleLine("Uppercase (A-Z)", upper, enabled = !(upper && enabledTypes == 1)) { upper = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleLine("Lowercase (a-z)", lower, enabled = !(lower && enabledTypes == 1)) { lower = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleLine("Numbers (0-9)", digits, enabled = !(digits && enabledTypes == 1)) { digits = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleLine("Symbols (!@#)", symbols, enabled = !(symbols && enabledTypes == 1)) { symbols = it }
            }
        }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
