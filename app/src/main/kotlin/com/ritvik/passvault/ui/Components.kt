package com.ritvik.passvault.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Standard page: scrollable column, capped at 520 dp wide so it also looks right on tablets,
 * optionally centred vertically (used for the lock screen).
 */
@Composable
fun ScreenColumn(centered: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (centered) Alignment.Center else Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Round icon badge, a title and an optional explanation, all centred. */
@Composable
fun ScreenHeader(icon: ImageVector, title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().height(46.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(44.dp),
    ) {
        Text(text)
    }
}

/**
 * Text field with the app's look. [secret] fields are masked unless [revealed], and ask the keyboard
 * not to learn or suggest the text.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secret: Boolean = false,
    revealed: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Used while the text is visible (not hidden as dots). Lets a field show spaces without changing what is stored. */
    displayTransformation: VisualTransformation? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else displayTransformation ?: VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret && keyboardType == KeyboardType.Text) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }),
        leadingIcon = leadingIcon?.let { icon -> @Composable { Icon(icon, contentDescription = null) } },
        trailingIcon = trailing,
    )
}

/**
 * White rounded card with a thin outline and a heading, used to group related settings or fields.
 * Long explanations go in [info]: it shows as a small (i) button next to the heading that opens a popup,
 * so the screen itself stays short.
 */
@Composable
fun SectionCard(title: String, info: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (info != null) InfoButton(title, info)
            }
            content()
        }
    }
}

/** A small spaced-out label above a title or a group, for example "PREFERENCES". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    androidx.compose.material3.Text(
        Strings.tr(text).uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        letterSpacing = 2.2.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** The big heading of a main screen: a small label above, then the title. */
@Composable
fun ScreenTitle(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

/** A small (i) icon. Tapping it opens a popup with [text], for explanations that would otherwise clutter the screen. */
@Composable
fun InfoButton(title: String, text: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(32.dp)) {
        Icon(
            Icons.Filled.Info,
            contentDescription = "More information about $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

@Composable
fun ErrorText(text: String?) {
    if (text != null) {
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}


/**
 * Shows a card number as groups ("4111 1111 1111 1111") while the stored text stays plain digits. Because the text
 * itself never changes, the cursor stays where the user is typing instead of jumping back before the new digit.
 */
object CardNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = com.ritvik.passvault.cards.CardInfo.format(digits)
        // For each digit, where it sits in the grouped text; separators are the spaces in between.
        val positions = ArrayList<Int>(digits.length + 1)
        for (i in shown.indices) if (shown[i] != ' ') positions.add(i)
        val mapping = object : OffsetMapping {
            // The cursor goes right after the digit before it, never before a following space.
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, digits.length)
                return if (o == 0) 0 else positions[o - 1] + 1
            }
            override fun transformedToOriginal(offset: Int): Int =
                shown.take(offset.coerceIn(0, shown.length)).count { it != ' ' }
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/** A bar and a word (Too short / Weak / Fair / Strong) for a password being chosen. Shows nothing while empty. */
@Composable
fun PasswordStrengthBar(password: String) {
    if (password.isEmpty()) return
    val strength = com.ritvik.passvault.crypto.PasswordPolicy.evaluate(password.toCharArray())
    val (fraction, label, color) = when (strength) {
        com.ritvik.passvault.crypto.PasswordPolicy.Strength.TOO_SHORT ->
            Triple(0.1f, "Too short (12+ characters)", MaterialTheme.colorScheme.error)
        com.ritvik.passvault.crypto.PasswordPolicy.Strength.WEAK -> Triple(0.35f, "Weak", MaterialTheme.colorScheme.error)
        com.ritvik.passvault.crypto.PasswordPolicy.Strength.FAIR -> Triple(0.65f, "Fair", MaterialTheme.colorScheme.warning)
        com.ritvik.passvault.crypto.PasswordPolicy.Strength.STRONG -> Triple(1f, "Strong", MaterialTheme.colorScheme.success)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** An icon on a soft round background in [tint], used at the start of list rows and card headers. */
@Composable
fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp = 34.dp) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/**
 * Shown before the Emergency Kit PDF is saved. The PDF holds the recovery key in plain text, and apps with storage
 * access or cloud-sync tools (Drive, Dropbox, WhatsApp backup) could copy it from Downloads or Documents.
 */
@androidx.compose.runtime.Composable
fun EmergencyKitWarningDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This file holds your recovery key") },
        text = {
            Text(
                "Anyone who gets this PDF can open your vault. If you save it to Downloads or Documents, other apps or a " +
                    "cloud-sync app (Google Drive, Dropbox, WhatsApp backup) may copy it. Print it right away, then delete the " +
                    "file from your phone and from anywhere it was copied to.",
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onContinue) { Text("I understand, save it") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The eye button for a password box: tap to show the text, tap again to hide it. */
@androidx.compose.runtime.Composable
fun RevealToggle(revealed: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (revealed) "Hide" else "Show",
        )
    }
}
