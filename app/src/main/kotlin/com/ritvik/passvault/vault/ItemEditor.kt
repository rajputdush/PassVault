package com.ritvik.passvault.vault

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.ritvik.passvault.ui.Strings
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.ritvik.passvault.autofill.AppIds
import com.ritvik.passvault.ui.InfoButton
import com.ritvik.passvault.ui.LocalIconLoader
import com.ritvik.passvault.apps.InstalledApps
import com.ritvik.passvault.apps.AppIcon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ritvik.passvault.cards.CardInfo
import com.ritvik.passvault.crypto.Rating
import com.ritvik.passvault.crypto.SitePasswordStrength
import com.ritvik.passvault.items.CustomField
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.PasswordEntry
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.sites.SiteInfo
import com.ritvik.passvault.totp.Totp
import com.ritvik.passvault.ui.success
import com.ritvik.passvault.ui.warning
import com.ritvik.passvault.ui.AppTextField
import com.ritvik.passvault.ui.ErrorText
import com.ritvik.passvault.ui.PrimaryButton
import com.ritvik.passvault.ui.SecondaryButton
import com.ritvik.passvault.ui.ScreenColumn
import com.ritvik.passvault.ui.SectionCard
import com.ritvik.passvault.ui.SiteIcon
import kotlinx.coroutines.delay
import java.util.Calendar

private val GOLD = Color(0xFFF5A623)

/** A custom field while it is being edited. The id keeps each row's own state (like "revealed") when others are removed. */
private class EditableField(val id: Int, label: String, value: String, hidden: Boolean) {
    var label by mutableStateOf(label)
    var value by mutableStateOf(value)
    var hidden by mutableStateOf(hidden)
}

@Composable
fun ItemEditScreen(
    item: VaultItem,
    isNew: Boolean,
    /** Folders already in use, offered as quick choices. */
    folders: List<String>,
    onSave: (VaultItem) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onCopy: (label: String, text: String) -> Unit,
    /** Opens the QR scanner and hands back the scanned text. */
    onScanQr: ((String) -> Unit) -> Unit,
    /** Opens the share dialog for the saved item. Null hides the button. */
    onShare: ((VaultItem) -> Unit)? = null,
    /** Starts another login for the same site. Null hides the button. */
    onAddAnother: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(item.title) }
    var username by remember { mutableStateOf(item.username) }
    var password by remember { mutableStateOf(item.password) }
    var url by remember { mutableStateOf(item.url) }
    var notes by remember { mutableStateOf(item.notes) }
    var favorite by remember { mutableStateOf(item.favorite) }
    var folder by remember { mutableStateOf(item.folder) }
    var totp by remember { mutableStateOf(item.totp) }
    var appId by remember { mutableStateOf(item.appId) }
    // The address whose logo may be downloaded: only after typing pauses on something that looks finished
    // ("amazon.com"), so half-typed names such as "amaz" are never sent to the icon service.
    var iconUrl by remember { mutableStateOf(if (SiteInfo.looksComplete(item.url)) item.url else "") }
    LaunchedEffect(url) {
        if (!SiteInfo.looksComplete(url)) {
            iconUrl = ""
            return@LaunchedEffect
        }
        if (url != iconUrl) delay(800)
        iconUrl = url
    }
    var cardholder by remember { mutableStateOf(item.cardholder) }
    var cardNumber by remember { mutableStateOf(CardInfo.digitsOnly(item.cardNumber)) }
    var cardExpiry by remember { mutableStateOf(item.cardExpiry) }
    var cardCvv by remember { mutableStateOf(item.cardCvv) }
    var cardPin by remember { mutableStateOf(item.cardPin) }

    var nextFieldId by remember { mutableStateOf(item.customFields.size) }
    val customFields = remember {
        mutableStateListOf<EditableField>().apply {
            item.customFields.forEachIndexed { i, f -> add(EditableField(i, f.label, f.value, f.hidden)) }
        }
    }

    var showPassword by remember { mutableStateOf(isNew) }
    var showCardSecrets by remember { mutableStateOf(isNew) }
    var showTotp by remember { mutableStateOf(isNew || item.totp.isBlank()) }
    var showGenerator by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val type = item.type

    val save: () -> Unit = {
        val problem = when (type) {
            ItemType.LOGIN -> when {
                title.isBlank() && url.isBlank() && username.isBlank() ->
                    "Enter a website or a username so you can find this login later."
                totp.isNotBlank() && !Totp.isValid(totp) ->
                    "That 2FA key doesn't look right. Paste the setup key or the otpauth:// link, or scan the QR code."
                else -> null
            }
            ItemType.CARD ->
                if (title.isBlank() && cardNumber.isBlank() && cardholder.isBlank()) "Enter a card number, a name or the cardholder." else null
            ItemType.NOTE ->
                if (title.isBlank() && notes.isBlank()) "Write something in the note, or give it a title." else null
        }
        if (problem != null) {
            error = problem
        } else {
            onSave(
                item.withPassword(password, System.currentTimeMillis()).copy(
                    title = title.trim(),
                    username = username.trim(),
                    url = url.trim(),
                    notes = notes,
                    favorite = favorite,
                    folder = folder.trim(),
                    totp = totp.trim(),
                    appId = appId.trim(),
                    cardholder = cardholder.trim(),
                    cardNumber = CardInfo.digitsOnly(cardNumber),
                    cardExpiry = cardExpiry.trim(),
                    cardCvv = cardCvv.trim(),
                    cardPin = cardPin.trim(),
                    customFields = customFields
                        .filter { it.label.isNotBlank() || it.value.isNotBlank() }
                        .map { CustomField(it.label.trim().take(CustomField.MAX_LABEL), it.value.take(CustomField.MAX_VALUE), it.hidden) },
                ),
            )
        }
    }

    ScreenColumn {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                (if (isNew) "New " else "Edit ") + type.label.lowercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { favorite = !favorite }) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (favorite) "Remove from favourites" else "Add to favourites",
                    tint = if (favorite) GOLD else MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onClick = save) { Text("Save") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val headline: String
            if (type == ItemType.LOGIN) {
                SiteIcon(title, url, username, size = 56.dp, appId = appId, fetchUrl = iconUrl)
                headline = SiteInfo.displayName(title, url, username)
            } else {
                TypeBadge(type, size = 56.dp)
                headline = item.copy(title = title, cardNumber = cardNumber, notes = notes).displayTitle
            }
            Text(
                headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (type) {
            ItemType.LOGIN -> {
                AppTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = "Website (for example amazon.com)",
                    keyboardType = KeyboardType.Uri,
                )
                LogoPrompt(url)
                AppTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username or email",
                    keyboardType = KeyboardType.Email,
                    trailing = if (username.isNotEmpty()) {
                        @Composable { TextButton(onClick = { onCopy("Username", username) }) { Text("Copy") } }
                    } else {
                        null
                    },
                )
                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    secret = true,
                    revealed = showPassword,
                    trailing = @Composable {
                        TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") }
                    },
                )
                StrengthLine(password)
                if (item.history.isNotEmpty()) {
                    PasswordHistoryCard(item.history, onCopy, onUse = { password = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showGenerator = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("Generate", maxLines = 1) }
                    OutlinedButton(
                        onClick = { onCopy("Password", password) },
                        enabled = password.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy", maxLines = 1) }
                }

                SectionCard(
                    "Two-factor code",
                    info = "Optional. Add the setup key from the website (or scan its QR code) and PassVault shows the 6-digit code here, so you don't need a separate authenticator app.",
                ) {
                    AppTextField(
                        value = totp,
                        onValueChange = {
                            totp = it
                            error = null
                        },
                        label = "Setup key or otpauth:// link",
                        secret = true,
                        revealed = showTotp,
                        trailing = @Composable {
                            TextButton(onClick = { showTotp = !showTotp }) { Text(if (showTotp) "Hide" else "Show") }
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            onScanQr { scanned ->
                                if (Totp.isValid(scanned)) {
                                    totp = scanned.trim()
                                    error = null
                                } else {
                                    error = "That QR code isn't a 2FA setup code."
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Scan QR code") }
                    TotpCode(totp, onCopy)
                }

                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = "Name or label (optional, for example Work)",
                )
            }

            ItemType.CARD -> {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = "Name (optional, for example HDFC credit card)",
                )
                AppTextField(
                    value = cardholder,
                    onValueChange = {
                        cardholder = it
                        error = null
                    },
                    label = "Cardholder name",
                )
                AppTextField(
                    value = cardNumber,
                    onValueChange = {
                        cardNumber = CardInfo.digitsOnly(it).take(19)
                        error = null
                    },
                    label = "Card number",
                    secret = true,
                    revealed = showCardSecrets,
                    displayTransformation = com.ritvik.passvault.ui.CardNumberTransformation,
                    keyboardType = KeyboardType.NumberPassword,
                    trailing = @Composable {
                        TextButton(onClick = { showCardSecrets = !showCardSecrets }) { Text(if (showCardSecrets) "Hide" else "Show") }
                    },
                )
                CardNumberHint(cardNumber)
                OutlinedButton(
                    onClick = { onCopy("Card number", CardInfo.digitsOnly(cardNumber)) },
                    enabled = cardNumber.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Copy card number") }
                AppTextField(
                    value = cardExpiry,
                    onValueChange = { cardExpiry = it.filter { c -> c.isDigit() || c == '/' }.take(7) },
                    label = "Expiry (MM/YY)",
                    keyboardType = KeyboardType.Number,
                )
                ExpiryHint(cardExpiry)
                AppTextField(
                    value = cardCvv,
                    onValueChange = { cardCvv = it.filter { c -> c.isDigit() }.take(4) },
                    label = "Security code (CVV)",
                    secret = true,
                    revealed = showCardSecrets,
                    keyboardType = KeyboardType.NumberPassword,
                    trailing = if (cardCvv.isNotEmpty()) {
                        @Composable { TextButton(onClick = { onCopy("CVV", cardCvv) }) { Text("Copy") } }
                    } else {
                        null
                    },
                )
                AppTextField(
                    value = cardPin,
                    onValueChange = { cardPin = it.filter { c -> c.isDigit() }.take(12) },
                    label = "PIN (optional)",
                    secret = true,
                    revealed = showCardSecrets,
                    keyboardType = KeyboardType.NumberPassword,
                )
            }

            ItemType.NOTE -> {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = "Title",
                )
            }
        }

        AppTextField(
            value = notes,
            onValueChange = {
                notes = it
                error = null
            },
            label = if (type == ItemType.NOTE) "Note" else "Notes",
            singleLine = false,
            minLines = if (type == ItemType.NOTE) 10 else 3,
        )
        if (type == ItemType.NOTE) {
            OutlinedButton(
                onClick = { onCopy("Note", notes) },
                enabled = notes.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy note") }
        }

        CustomFieldsSection(
            fields = customFields,
            onAdd = { customFields.add(EditableField(nextFieldId++, "", "", false)) },
            onRemove = { customFields.remove(it) },
            onCopy = onCopy,
        )

        FolderField(folder, { folder = it }, folders)

        ErrorText(error)

        PrimaryButton(text = "Save", onClick = save)

        if (!isNew && onShare != null) {
            SecondaryButton("Share securely", onClick = { onShare(item) }, modifier = Modifier.fillMaxWidth())
            Text(
                "Makes an encrypted link or QR code for the saved version of this ${type.label.lowercase()}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (!isNew && onAddAnother != null) {
            SecondaryButton("Add another account for this site", onClick = onAddAnother, modifier = Modifier.fillMaxWidth())
        }

        if (!isNew) {
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Move to Trash", color = MaterialTheme.colorScheme.error)
            }
            RecordCard(item)
        }
    }

    if (showGenerator) {
        GeneratorDialog(
            onUse = {
                password = it
                showPassword = true
                showGenerator = false
            },
            onDismiss = { showGenerator = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Move this ${type.label.lowercase()} to Trash?") },
            text = { Text("You can restore it from Trash within 30 days. After that it is removed for good.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CustomFieldsSection(
    fields: List<EditableField>,
    onAdd: () -> Unit,
    onRemove: (EditableField) -> Unit,
    onCopy: (label: String, text: String) -> Unit,
) {
    SectionCard("Custom fields") {
        if (fields.isEmpty()) {
            Text(
                "Optional. Add anything else worth keeping with this item, such as a security question, an account number or a PIN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        fields.forEach { field ->
            key(field.id) {
                var reveal by remember { mutableStateOf(!field.hidden) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppTextField(
                        value = field.label,
                        onValueChange = { field.label = it.take(CustomField.MAX_LABEL) },
                        label = "Label (for example Security question)",
                    )
                    AppTextField(
                        value = field.value,
                        onValueChange = { field.value = it.take(CustomField.MAX_VALUE) },
                        label = "Value",
                        secret = field.hidden,
                        revealed = reveal,
                        trailing = if (field.hidden) {
                            @Composable { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") } }
                        } else {
                            null
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = field.hidden,
                            onClick = {
                                field.hidden = !field.hidden
                                reveal = !field.hidden
                            },
                            label = { Text("Hide value") },
                        )
                        TextButton(
                            onClick = { onCopy(field.label.ifBlank { "Value" }, field.value) },
                            enabled = field.value.isNotEmpty(),
                        ) { Text("Copy") }
                        TextButton(onClick = { onRemove(field) }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        if (fields.size < CustomField.MAX_FIELDS) {
            OutlinedButton(
                onClick = onAdd,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add custom field") }
        }
    }
}

@Composable
private fun FolderField(folder: String, onChange: (String) -> Unit, suggestions: List<String>) {
    AppTextField(value = folder, onValueChange = onChange, label = "Folder (optional, for example Work)")
    if (suggestions.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { name ->
                FilterChip(
                    selected = folder.trim().equals(name, ignoreCase = true),
                    onClick = { onChange(if (folder.trim().equals(name, ignoreCase = true)) "" else name) },
                    label = { Text(name) },
                )
            }
        }
    }
}

@Composable
private fun StrengthLine(password: String) {
    if (password.isEmpty()) return
    val assessment = remember(password) { SitePasswordStrength.assess(password) }
    val (label, color) = when (assessment.rating) {
        Rating.VERY_WEAK -> "Very weak" to MaterialTheme.colorScheme.error
        Rating.WEAK -> "Weak" to MaterialTheme.colorScheme.error
        Rating.FAIR -> "Fair" to MaterialTheme.colorScheme.warning
        Rating.STRONG -> "Strong" to MaterialTheme.colorScheme.success
    }
    val detail = assessment.reasons.firstOrNull()?.let { " - $it" }.orEmpty()
    Text(
        "Strength: $label$detail",
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun CardNumberHint(number: String) {
    val digits = CardInfo.digitsOnly(number)
    if (digits.length < 4) return
    val brand = CardInfo.brand(digits)
    val looksWrong = digits.length >= 12 && !CardInfo.luhnValid(digits)
    Text(
        if (looksWrong) "$brand. This number doesn't pass the check, so there may be a typo." else brand,
        style = MaterialTheme.typography.bodySmall,
        color = if (looksWrong) MaterialTheme.colorScheme.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun ExpiryHint(expiry: String) {
    if (expiry.isBlank()) return
    val cal = Calendar.getInstance()
    val ok = CardInfo.expiryValid(expiry, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    if (!ok) {
        Text(
            if (CardInfo.parseExpiry(expiry) == null) "Use the format MM/YY." else "This card has expired.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.warning,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** The live 6-digit code for a saved 2FA secret, with a countdown. Shows nothing if the secret isn't valid. */
@Composable
private fun TotpCode(secret: String, onCopy: (label: String, text: String) -> Unit) {
    val config = remember(secret) { Totp.parse(secret) }
    if (config != null) {
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(config) {
            while (true) {
                now = System.currentTimeMillis()
                delay(250)
            }
        }
        val code = Totp.code(config, now)
        val left = Totp.secondsRemaining(config, now)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (config.issuer.isNotBlank() || config.account.isNotBlank()) {
                Text(
                    listOf(config.issuer, config.account).filter { it.isNotBlank() }.joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Totp.format(code),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${left}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (left <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onCopy("2FA code", code) }) { Text("Copy") }
            }
            LinearProgressIndicator(
                progress = { left / config.periodSeconds.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Earlier passwords of a login. "Use" puts one back in the password box; it is only saved when the login is saved. */
/** Where and when this item was saved, shown at the bottom of the edit screen. */
@Composable
private fun RecordCard(item: VaultItem) {
    val format = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Record", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val origin = item.sourceText
        if (origin.isNotEmpty()) {
            Text(origin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.createdAt > 0) {
            Text(
                "Added " + format.format(java.util.Date(item.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Last changed " + format.format(java.util.Date(item.updatedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Only a login that actually has a password has a "password changed" date. A login saved with just a
        // username has none, and "Password set" is shown until the password has really been replaced once.
        if (item.type == ItemType.LOGIN && item.password.isNotEmpty()) {
            Text(
                (if (item.history.isEmpty()) "Password set " else "Password changed ") + format.format(java.util.Date(item.passwordAge)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PasswordHistoryCard(history: List<PasswordEntry>, onCopy: (String, String) -> Unit, onUse: (String) -> Unit) {
    var shown by remember { mutableStateOf<Int?>(null) }
    SectionCard(
        "Password history",
        info = "The last ${PasswordEntry.MAX} passwords this login used, so you can recover one if a change goes wrong.",
    ) {
        val format = remember { java.text.DateFormat.getDateInstance() }
        history.forEachIndexed { index, entry ->
            Column {
                Text(
                    if (shown == index) entry.password else "\u2022".repeat(10),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    Strings.tr("Set") + " " + format.format(java.util.Date(entry.setAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { shown = if (shown == index) null else index }) { Text(if (shown == index) "Hide" else "Show") }
                    TextButton(onClick = { onCopy("Old password", entry.password) }) { Text("Copy") }
                    TextButton(onClick = { onUse(entry.password) }) { Text("Use") }
                }
            }
        }
    }
}

/**
 * Asked once, when a finished-looking website address has been typed and real logos are off: the logo of the site
 * would appear next to the login. Saying yes turns on the same switch as Settings -> Appearance.
 */
@Composable
private fun LogoPrompt(url: String) {
    val loader = LocalIconLoader.current ?: return
    val on by loader.enabled.collectAsState()
    var dismissed by remember { mutableStateOf(loader.promptDismissed) }
    if (on || dismissed || !SiteInfo.looksComplete(url)) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Show the real logo for this site?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            "PassVault asks DuckDuckGo's icon service for the site's icon and sends only the site's address (for example " +
                "amazon.com), never your username, password or notes. You can turn this off in Settings, Appearance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row {
            TextButton(onClick = {
                loader.setEnabled(true)
                loader.dismissPrompt()
                dismissed = true
            }) { Text("Show logos") }
            TextButton(onClick = {
                loader.dismissPrompt()
                dismissed = true
            }) { Text("No thanks") }
        }
    }
}
