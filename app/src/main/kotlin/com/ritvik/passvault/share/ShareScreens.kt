package com.ritvik.passvault.share

import com.ritvik.passvault.ui.Strings
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ritvik.passvault.ActivityLog
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.BuildConfig
import com.ritvik.passvault.ClipboardHelper
import com.ritvik.passvault.cards.CardInfo
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.ui.AppTextField
import com.ritvik.passvault.ui.ErrorText
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Dialogs show secrets, and a dialog is its own window, so it needs its own "no screenshots" flag. */
private val SECURE_DIALOG = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)

private class Validity(val label: String, val millis: Long)

private const val HOUR = 60L * 60 * 1000
private val VALIDITIES = listOf(Validity("1 hour", HOUR), Validity("1 day", 24 * HOUR), Validity("7 days", 7 * 24 * HOUR))

private class ShareResult(val link: String, val passphrase: String, val expiresAt: Long)

// ---------------------------------------------------------------- sending

/**
 * Makes an encrypted link (and QR code) for one item. The passphrase is shown here and is not part of the link:
 * the sender passes it on by a different route.
 */
@Composable
fun ShareItemDialog(container: AppContainer, item: VaultItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf(ShareCodec.generatePassphrase()) }
    var validity by remember { mutableIntStateOf(1) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ShareResult?>(null) }

    // The system share sheet moves PassVault to the background. Don't auto-lock (and lose this screen) while it is open.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) container.autoLockSuspended = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            container.autoLockSuspended = false
        }
    }

    val done = result
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        properties = SECURE_DIALOG,
        title = { Text(if (done == null) "Share securely" else "Ready to send") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (done == null) {
                    Text(
                        "This makes an encrypted link for \"${item.displayTitle}\". The person you send it to needs PassVault and the passphrase below. Nothing is uploaded: the link itself holds the encrypted item.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = "Passphrase",
                        enabled = !working,
                    )
                    TextButton(onClick = { passphrase = ShareCodec.generatePassphrase() }, enabled = !working) {
                        Text("Make a new passphrase")
                    }
                    Text("Link works for", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VALIDITIES.forEachIndexed { i, v ->
                            FilterChip(selected = validity == i, onClick = { validity = i }, label = { Text(v.label) }, enabled = !working)
                        }
                    }
                    Text(
                        "Whoever has both the link and the passphrase can read everything in this item, including its password, 2FA key or card details. A link can't be taken back once it is sent, so share it only with people you trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ErrorText(error)
                } else {
                    if (ShareCodec.fitsInQr(done.link)) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCode(done.link) }
                        Text(
                            "On the other phone: open PassVault, tap + then \"Receive a shared item\" and tap Scan QR.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "This item is too big for a QR code, so send the link instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Passphrase", style = MaterialTheme.typography.labelLarge)
                    Text(done.passphrase, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                    OutlinedButton(
                        onClick = {
                            ClipboardHelper.copy(context, "Passphrase", done.passphrase, container.settings.clipboardClearSeconds)
                            container.activityLog.record(ActivityLog.KIND_SHARE, ActivityLog.nameOf(item) + "\nShare passphrase copied")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copy passphrase") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                container.autoLockSuspended = true
                                val message = "A PassVault item, encrypted for you.\n\nOpen it in PassVault:\n${done.link}\n\nIf tapping does nothing: copy this whole message, open PassVault, tap + then \"Receive a shared item\" and tap Paste.\n\nThe passphrase comes separately. The link stops working on " +
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(done.expiresAt)) + "."
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "PassVault shared item")
                                    putExtra(Intent.EXTRA_TEXT, message)
                                }
                                context.startActivity(Intent.createChooser(send, "Send the link with"))
                                container.activityLog.record(ActivityLog.KIND_SHARE, ActivityLog.nameOf(item) + "\nLink sent through the share sheet")
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Send link") }
                        OutlinedButton(
                            onClick = {
                                ClipboardHelper.copy(context, "PassVault link", done.link, container.settings.clipboardClearSeconds)
                                container.activityLog.record(ActivityLog.KIND_SHARE, ActivityLog.nameOf(item) + "\nShare link copied")
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Copy link") }
                    }
                    Text(
                        "Send the passphrase by a different route than the link, for example the link in a message and the passphrase in person or by phone. The link stops working on " +
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(done.expiresAt)) + ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (done == null) {
                TextButton(
                    enabled = !working && passphrase.trim().length >= ShareCodec.MIN_PASSPHRASE_LENGTH,
                    onClick = {
                        scope.launch {
                            working = true
                            error = null
                            try {
                                val ttl = VALIDITIES[validity].millis
                                val code = ShareService.create(item, passphrase.trim(), ttl)
                                result = ShareResult(ShareCodec.toLink(code, BuildConfig.SHARE_WEB_URL), passphrase.trim(), System.currentTimeMillis() + ttl)
                                // The log says what was shared and for how long. The link and passphrase are never written to it.
                                container.activityLog.record(
                                    ActivityLog.KIND_SHARE,
                                    ActivityLog.nameOf(item) + "\nEncrypted link made, valid " + VALIDITIES[validity].label,
                                )
                            } catch (e: ShareException) {
                                error = e.message
                            } catch (e: IllegalArgumentException) {
                                error = e.message
                            }
                            working = false
                        }
                    },
                ) { Text(if (working) "Working…" else "Create link") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (done == null) TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------- receiving

/**
 * Opens a shared item: paste or scan the link, type the passphrase, look at what is inside, then save it to the vault.
 * Nothing is saved until the user taps Save.
 *
 * @param initialCode a code that arrived through a link the user tapped; the code box is hidden then
 */
@Composable
fun ReceiveShareDialog(
    initialCode: String?,
    onScanQr: ((String) -> Unit) -> Unit,
    onSave: (VaultItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf(initialCode.orEmpty()) }
    var passphrase by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<VaultItem?>(null) }
    var reveal by remember { mutableStateOf(false) }

    // If a share link was just copied (for example from WhatsApp), pick it up so the person only has to type the passphrase.
    LaunchedEffect(Unit) {
        if (initialCode == null && code.isBlank()) {
            clipboard.getText()?.text?.let { text -> ShareCodec.extractCode(text)?.let { code = it } }
        }
    }

    val item = opened
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        properties = SECURE_DIALOG,
        title = { Text(if (item == null) "Receive a shared item" else "Save this item?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (item == null) {
                    if (initialCode == null) {
                        Text(
                            "Paste the link or message you were sent, or scan the QR code on the sender's screen.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        AppTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = "Link",
                            singleLine = false,
                            minLines = 2,
                            enabled = !working,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { clipboard.getText()?.text?.let { code = it } },
                                enabled = !working,
                                modifier = Modifier.weight(1f),
                            ) { Text("Paste") }
                            OutlinedButton(
                                onClick = { onScanQr { scanned -> code = scanned } },
                                enabled = !working,
                                modifier = Modifier.weight(1f),
                            ) { Text("Scan QR") }
                        }
                    } else {
                        Text("Someone sent you an encrypted item. Enter the passphrase they gave you to open it.", style = MaterialTheme.typography.bodyMedium)
                    }
                    AppTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = "Passphrase from the sender",
                        secret = true,
                        revealed = reveal,
                        enabled = !working,
                        trailing = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") } },
                    )
                    ErrorText(error)
                } else {
                    Text(item.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val line = listOf(item.type.label, item.subtitle).filter { it.isNotBlank() }.joinToString("  ·  ")
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val includes = includes(item)
                    if (includes.isNotEmpty()) {
                        Text("Includes: $includes", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "It is added to your vault as a new item. Check that you trust the sender before you use what's inside.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (item == null) {
                TextButton(
                    enabled = !working && code.isNotBlank() && passphrase.isNotBlank(),
                    onClick = {
                        scope.launch {
                            working = true
                            error = null
                            try {
                                val shared = ShareService.open(code, passphrase)
                                opened = ShareService.toVaultItem(shared, System.currentTimeMillis())
                            } catch (e: ShareException) {
                                error = e.message
                            }
                            working = false
                        }
                    },
                ) { Text(if (working) "Opening…" else "Open") }
            } else {
                TextButton(onClick = { onSave(item) }) { Text("Save to my vault") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
}

/** A plain-words list of what an item holds, without showing any of it. */
private fun includes(item: VaultItem): String = listOfNotNull(
    "a password".takeIf { item.password.isNotEmpty() },
    "a 2FA key".takeIf { item.totp.isNotEmpty() },
    "a card number (${CardInfo.masked(item.cardNumber)})".takeIf { item.type == ItemType.CARD && item.cardNumber.isNotEmpty() },
    "a security code or PIN".takeIf { item.cardCvv.isNotEmpty() || item.cardPin.isNotEmpty() },
    "notes".takeIf { item.notes.isNotEmpty() },
    "${item.customFields.size} extra field${if (item.customFields.size == 1) "" else "s"}".takeIf { item.customFields.isNotEmpty() },
).joinToString(", ")

/** Shown briefly after a received item has been saved. */
fun toastSaved(context: android.content.Context) {
    Toast.makeText(context, Strings.tr("Saved to your vault"), Toast.LENGTH_SHORT).show()
}
