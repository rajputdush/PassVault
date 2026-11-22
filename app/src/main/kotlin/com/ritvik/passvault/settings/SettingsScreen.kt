package com.ritvik.passvault.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import com.ritvik.passvault.ui.ScreenTitle
import com.ritvik.passvault.ui.Eyebrow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Create
import com.ritvik.passvault.ui.IconBadge
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.BuildConfig
import com.ritvik.passvault.CrashLog
import com.ritvik.passvault.SaveLog
import com.ritvik.passvault.canUseBiometrics
import com.ritvik.passvault.confirmItsYou
import com.ritvik.passvault.onboarding.TutorialScreen
import com.ritvik.passvault.ui.AppTextField
import com.ritvik.passvault.ui.AppLanguage
import com.ritvik.passvault.ui.ThemeMode
import com.ritvik.passvault.ui.ErrorText
import com.ritvik.passvault.ui.PrimaryButton
import com.ritvik.passvault.ui.ScreenColumn
import com.ritvik.passvault.ui.SecondaryButton
import com.ritvik.passvault.ui.SectionCard
import java.io.IOException
import com.ritvik.passvault.ActivityLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** Writes a settings change to the activity log. Nothing secret is ever passed here, only the name and the new value. */
private fun AppContainer.logSetting(text: String) = activityLog.record(ActivityLog.KIND_SETTING, text)

private fun clipboardText(seconds: Int) = if (seconds > 0) "Clear clipboard after $seconds seconds" else "Clear clipboard turned off"

private fun screenshotText(allowed: Boolean) = if (allowed) "Screenshots allowed" else "Screenshots blocked"

private val AUTO_LOCK_OPTIONS = listOf("Immediately" to 0, "30 seconds" to 30, "1 minute" to 60, "5 minutes" to 300)
private val CLIPBOARD_OPTIONS = listOf("15 seconds" to 15, "30 seconds" to 30, "1 minute" to 60, "Never" to 0)

private enum class Page(val title: String) {
    HOME("Settings"),
    SEC_LOCK("Lock & privacy"),
    SEC_PASSWORD("Master password"),
    SEC_RECOVERY("Recovery key"),
    SEC_ACTIVITY("Activity log"),
    BACKUP("Backup & import"),
    AUTOFILL("Autofill"),
    APPEARANCE("Appearance"),
    ABOUT("About"),
}

/** Settings as a menu: the first page lists topics, each topic opens its own page. */
@Composable
fun SettingsHost(
    container: AppContainer,
    onBack: () -> Unit,
    /** Fingerprint unlock is switched here; the vault screen owns the actual set-up, so it is passed in. */
    biometricAvailable: Boolean = false,
    biometricOn: Boolean = false,
    onToggleBiometric: () -> Unit = {},
    onOpenGenerator: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container) } },
    )
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var tour by rememberSaveable { mutableStateOf(false) }

    val foregroundTick by container.appForeground.collectAsState()
    val autofillEnabled = remember(foregroundTick) {
        context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
    }

    // A message belongs to the page where the action happened.
    LaunchedEffect(page) {
        viewModel.clearMessage()
        if (page != Page.SEC_RECOVERY) viewModel.dismissRecoveryKey()
    }
    DisposableEffect(Unit) { onDispose { viewModel.dismissRecoveryKey() } }
    val goBack: () -> Unit = {
        when (page) {
            Page.HOME -> onBack()
            else -> page = Page.HOME
        }
    }
    BackHandler(onBack = goBack)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        container.autoLockSuspended = false
        if (uri != null) {
            viewModel.exportBackup { bytes ->
                val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open the file")
                out.use { it.write(bytes) }
            }
        }
    }
    val kitLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        container.autoLockSuspended = false
        if (uri != null) {
            viewModel.saveEmergencyKit { bytes ->
                val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open the file")
                out.use { it.write(bytes) }
            }
        }
    }
    // The two plain-text exports (for moving to another password manager).
    val plainJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        container.autoLockSuspended = false
        if (uri != null) {
            viewModel.exportPlain(json = true) { bytes ->
                val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open the file")
                out.use { it.write(bytes) }
            }
        }
    }
    val plainCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        container.autoLockSuspended = false
        if (uri != null) {
            viewModel.exportPlain(json = false) { bytes ->
                val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open the file")
                out.use { it.write(bytes) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        container.autoLockSuspended = false
        if (uri != null) {
            viewModel.importPasswords {
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
                input.use { it.readBytes() }
            }
        }
    }

    if (tour) {
        BackHandler { tour = false }
        TutorialScreen(onFinish = { tour = false })
        return
    }

    ScreenColumn {
        if (page == Page.HOME) {
            ScreenTitle("Preferences", "Settings")
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = goBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }

        // Results appear as a popup, so they are seen wherever the person has scrolled to, not hidden at the top.
        state.message?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::clearMessage,
                title = { Text(if (state.isError) "That didn't work" else "Done") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("OK") } },
            )
        }

        when (page) {
            Page.HOME -> {
                var clipboard by remember { mutableStateOf(container.settings.clipboardClearSeconds) }
                // Switching "Clear clipboard" back on restores the last time the person chose, or 30 seconds.
                var lastClipboard by remember { mutableStateOf(if (clipboard > 0) clipboard else 30) }
                val screenshots by container.settings.allowScreenshots.collectAsState()
                val themeMode by container.settings.themeMode.collectAsState()
                val autoLock = container.settings.autoLockSeconds
                HomePage(
                    biometricAvailable = biometricAvailable,
                    biometricOn = biometricOn,
                    onToggleBiometric = onToggleBiometric,
                    autoLockLabel = AUTO_LOCK_OPTIONS.firstOrNull { it.second == autoLock }?.first ?: "$autoLock s",
                    clipboardSeconds = clipboard,
                    onClipboard = { on ->
                        val seconds = if (on) lastClipboard else 0
                        if (seconds > 0) lastClipboard = seconds
                        clipboard = seconds
                        container.settings.clipboardClearSeconds = seconds
                        container.logSetting(clipboardText(seconds))
                    },
                    allowScreenshots = screenshots,
                    onAllowScreenshots = {
                        container.settings.setAllowScreenshots(it)
                        container.logSetting(screenshotText(it))
                    },
                    autofillEnabled = autofillEnabled,
                    themeLabel = themeMode.label,
                    onOpen = { page = it },
                    onLockNow = { container.session.lock() },
                    onOpenGenerator = onOpenGenerator,
                )
            }

            Page.SEC_LOCK -> {
                var autoLock by remember { mutableStateOf(container.settings.autoLockSeconds) }
                var clipboard by remember { mutableStateOf(container.settings.clipboardClearSeconds) }
                var confirmReveal by remember { mutableStateOf(container.settings.confirmBeforeReveal) }
                val screenshots by container.settings.allowScreenshots.collectAsState()
                LockPrivacyPage(
                    allowScreenshots = screenshots,
                    onAllowScreenshots = {
                        container.settings.setAllowScreenshots(it)
                        container.logSetting(screenshotText(it))
                    },
                    autoLockSeconds = autoLock,
                    onAutoLock = {
                        autoLock = it
                        container.settings.autoLockSeconds = it
                        container.logSetting("Auto-lock: " + (AUTO_LOCK_OPTIONS.firstOrNull { o -> o.second == it }?.first ?: "$it seconds"))
                    },
                    clipboardSeconds = clipboard,
                    onClipboard = {
                        clipboard = it
                        container.settings.clipboardClearSeconds = it
                        container.logSetting(clipboardText(it))
                    },
                    confirmAvailable = canUseBiometrics(context),
                    confirmReveal = confirmReveal,
                    onConfirmReveal = {
                        confirmReveal = it
                        container.settings.confirmBeforeReveal = it
                        container.logSetting(if (it) "Fingerprint check before showing passwords turned on" else "Fingerprint check before showing passwords turned off")
                    },
                )
            }

            Page.SEC_ACTIVITY -> ActivityLogPage(container)

            Page.SEC_PASSWORD -> MasterPasswordPage(state = state, onChangePassword = viewModel::changePassword)

            Page.SEC_RECOVERY -> RecoveryKeyPage(
                state = state,
                onVerifyRecovery = viewModel::verifyRecoveryKey,
                onRegenerateRecovery = viewModel::regenerateRecoveryKey,
                onSaveKit = {
                    // The file picker moves this app to the background; don't auto-lock while it is open.
                    container.autoLockSuspended = true
                    kitLauncher.launch("passvault-emergency-kit-${LocalDate.now()}.pdf")
                },
                onDismissKey = viewModel::dismissRecoveryKey,
            )

            Page.BACKUP -> BackupPage(
                working = state.working,
                onExport = {
                    // The file picker moves this app to the background; don't auto-lock while it is open.
                    container.autoLockSuspended = true
                    exportLauncher.launch("passvault-backup-${LocalDate.now()}.json")
                },
                onImport = {
                    container.autoLockSuspended = true
                    importLauncher.launch(arrayOf("*/*"))
                },
                onExportPlain = { password, json ->
                    viewModel.verifyPassword(password) {
                        // The file picker moves this app to the background; don't auto-lock while it is open.
                        container.autoLockSuspended = true
                        if (json) plainJsonLauncher.launch("passvault-export-${LocalDate.now()}.json")
                        else plainCsvLauncher.launch("passvault-logins-${LocalDate.now()}.csv")
                    }
                },
            )

            Page.AUTOFILL -> AutofillPage(
                enabled = autofillEnabled,
                onTurnOn = {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${context.packageName}"))
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },
            )

            Page.APPEARANCE -> {
                val iconsOn by container.icons.enabled.collectAsState()
                val themeMode by container.settings.themeMode.collectAsState()
                val language by container.settings.language.collectAsState()
                val groupSites by container.settings.groupSites.collectAsState()
                AppearancePage(
                    language = language,
                    onLanguage = {
                        container.settings.setLanguage(it)
                        container.logSetting("Language: ${it.label}")
                    },
                    themeMode = themeMode,
                    onTheme = {
                        container.settings.setThemeMode(it)
                        container.logSetting("Theme: ${it.label}")
                    },
                    iconsOn = iconsOn,
                    onChange = {
                        container.icons.setEnabled(it)
                        container.logSetting(if (it) "Real website logos turned on" else "Real website logos turned off")
                    },
                    groupSites = groupSites,
                    onGroupSites = {
                        container.settings.setGroupSites(it)
                        container.logSetting(if (it) "Grouping accounts from the same site turned on" else "Grouping accounts from the same site turned off")
                    },
                )
            }

            Page.ABOUT -> AboutPage(onTour = { tour = true })
        }
    }
}

// ---------------------------------------------------------------- menu

@Composable
private fun HomePage(
    biometricAvailable: Boolean,
    biometricOn: Boolean,
    onToggleBiometric: () -> Unit,
    autoLockLabel: String,
    clipboardSeconds: Int,
    onClipboard: (Boolean) -> Unit,
    allowScreenshots: Boolean,
    onAllowScreenshots: (Boolean) -> Unit,
    autofillEnabled: Boolean,
    themeLabel: String,
    onOpen: (Page) -> Unit,
    onLockNow: () -> Unit,
    onOpenGenerator: () -> Unit,
) {
    SectionLabel("Unlock")
    MenuGroup {
        if (biometricAvailable) {
            SwitchRow(Icons.Filled.Fingerprint, "Fingerprint unlock", biometricOn) { onToggleBiometric() }
            RowDivider()
        }
        NavRow(Icons.Filled.Lock, "Lock & privacy", value = autoLockLabel) { onOpen(Page.SEC_LOCK) }
    }

    SectionLabel("Security")
    MenuGroup {
        NavRow(Icons.Filled.Create, "Master password") { onOpen(Page.SEC_PASSWORD) }
        RowDivider()
        NavRow(Icons.Filled.Refresh, "Recovery key") { onOpen(Page.SEC_RECOVERY) }
        RowDivider()
        NavRow(Icons.Filled.History, "Activity log") { onOpen(Page.SEC_ACTIVITY) }
    }

    SectionLabel("Customize")
    MenuGroup {
        NavRow(Icons.Filled.AutoFixHigh, "Password generator") { onOpenGenerator() }
        RowDivider()
        NavRow(Icons.Filled.Create, "Autofill", value = if (autofillEnabled) "On" else "Off") { onOpen(Page.AUTOFILL) }
        RowDivider()
        NavRow(Icons.Filled.Palette, "Appearance", value = themeLabel) { onOpen(Page.APPEARANCE) }
    }

    SectionLabel("Backup")
    MenuGroup {
        NavRow(Icons.Filled.Download, "Export, restore & import") { onOpen(Page.BACKUP) }
    }

    SectionLabel("About")
    MenuGroup {
        NavRow(Icons.Filled.Info, "Version", value = BuildConfig.VERSION_NAME) { onOpen(Page.ABOUT) }
        RowDivider()
        NavRow(Icons.AutoMirrored.Filled.ExitToApp, "Lock vault now") { onLockNow() }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Eyebrow(text, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 58.dp))
}

@Composable
private fun MenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

/** One compact row: a round icon, the name, and whatever sits at the end (a value, an arrow or a switch). */
@Composable
private fun SettingsRowBase(icon: ImageVector, title: String, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, 32.dp)
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** A row that opens a page, optionally showing the current value. */
@Composable
private fun NavRow(icon: ImageVector, title: String, value: String? = null, onClick: () -> Unit) {
    SettingsRowBase(icon, title, onClick) {
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A row with an on/off switch. Tapping anywhere on the row flips it. */
@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    SettingsRowBase(icon, title, onClick = { onChecked(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.scale(0.85f))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- pages

@Composable
private fun LockPrivacyPage(
    allowScreenshots: Boolean,
    onAllowScreenshots: (Boolean) -> Unit,
    autoLockSeconds: Int,
    onAutoLock: (Int) -> Unit,
    clipboardSeconds: Int,
    onClipboard: (Int) -> Unit,
    confirmAvailable: Boolean,
    confirmReveal: Boolean,
    onConfirmReveal: (Boolean) -> Unit,
) {
    SectionCard("Auto-lock", info = "Lock the vault after you leave the app for the time you choose. \"Immediately\" locks as soon as you switch away.") {
        OptionRow(AUTO_LOCK_OPTIONS, autoLockSeconds, onAutoLock)
    }

    if (confirmAvailable) {
        SectionCard(
            "Fingerprint check",
            info = "Even while PassVault is unlocked, ask for your fingerprint before opening a login or card, or copying a password. After one check you are not asked again for 30 seconds. Autofill is not affected.",
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ask before showing or copying", modifier = Modifier.weight(1f))
                Switch(checked = confirmReveal, onCheckedChange = onConfirmReveal)
            }
        }
    }

    SectionCard(
        "Screenshots",
        info = "On: you can take screenshots and record the screen inside PassVault. Off: Android blocks screenshots and screen recording, and the app shows blank in the recent-apps list. Turn it off if you do not want passwords captured by accident.",
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Allow screenshots", modifier = Modifier.weight(1f))
            Switch(checked = allowScreenshots, onCheckedChange = onAllowScreenshots)
        }
    }

    SectionCard("Clear clipboard", info = "Passwords and codes you copy are removed from the clipboard after the time you choose.") {
        OptionRow(CLIPBOARD_OPTIONS, clipboardSeconds, onClipboard)
    }

}

@Composable
private fun ActivityLogPage(container: AppContainer) {
    var enabled by remember { mutableStateOf(container.settings.activityLogEnabled) }
    var entries by remember { mutableStateOf<List<ActivityLog.Entry>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val doClear = {
        container.activityLog.clear()
        refresh++
    }

    LaunchedEffect(refresh) {
        entries = withContext(Dispatchers.IO) { container.activityLog.read() }.asReversed()
    }

    SectionCard(
        "Activity log",
        info = "A private diary of what happened in your vault: when it was unlocked, which password or code was copied, what was autofilled, and what was moved to or restored from the Trash. It stores only item titles and what was done, never passwords or notes. It is encrypted with your vault key, so it can only be read while PassVault is unlocked. The latest 500 events are kept. Android does not tell apps which autofill entry you tap, so autofill shows as \"Autofill offered\" for the drop-down and \"Autofilled\" when you pick from PassVault's own screen.",
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Record activity", modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    container.settings.activityLogEnabled = it
                },
            )
        }
    }

    SectionCard("Recent activity") {
        if (entries.isEmpty()) {
            Hint("Nothing recorded yet.")
        } else {
            val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            // A tall box that scrolls on its own, so a long log does not make the whole page huge. It takes most of the
            // screen's height, which leaves room to read several entries at once.
            val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeight * 0.72f).dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val shown = entries.take(200)
                shown.forEachIndexed { index, e ->
                    // The first line says what the entry is about; every line after it is one change, as a bullet.
                    val lines = e.detail.split('\n').filter { it.isNotBlank() }
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            ActivityLog.label(e.kind),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (lines.isNotEmpty()) {
                            Text(lines.first(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        lines.drop(1).forEach { line ->
                            Text(
                                "\u2022 " + line,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        Text(
                            format.format(Date(e.time)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (entries.isNotEmpty()) {
        SecondaryButton(text = "Clear log", onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth())
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the activity log?") },
            text = { Text("All recorded activity is deleted. Your passwords are not affected. Your fingerprint is asked first.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    var c: android.content.Context = ctx
                    while (c is android.content.ContextWrapper && c !is androidx.fragment.app.FragmentActivity) c = c.baseContext
                    val act = c as? androidx.fragment.app.FragmentActivity
                    if (act != null && canUseBiometrics(act)) {
                        act.confirmItsYou(
                            title = "Clear activity log",
                            onSuccess = { doClear() },
                            onFailure = { message ->
                                if (message != null) android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
                            },
                        )
                    } else {
                        doClear()
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MasterPasswordPage(
    state: SettingsUiState,
    onChangePassword: (current: String, new: String, confirmation: String) -> Unit,
) {
    // remember {} on purpose, never rememberSaveable {}: secrets must not go into the saved-state Bundle.
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }

    LaunchedEffect(state.passwordChangedCount) {
        currentPassword = ""
        newPassword = ""
        confirmation = ""
    }

    SectionCard("Change master password") {
        AppTextField(
            value = currentPassword,
            onValueChange = { currentPassword = it },
            label = "Current password",
            secret = true,
            revealed = showPasswords,
            enabled = !state.working,
        )
        AppTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = "New password (12+ characters)",
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
            onImeAction = { onChangePassword(currentPassword, newPassword, confirmation) },
        )
        PrimaryButton(
            text = "Change password",
            onClick = { onChangePassword(currentPassword, newPassword, confirmation) },
            loading = state.working,
        )
    }

}

@Composable
private fun RecoveryKeyPage(
    state: SettingsUiState,
    onVerifyRecovery: (String) -> Unit,
    onRegenerateRecovery: (String) -> Unit,
    onSaveKit: () -> Unit,
    onDismissKey: () -> Unit,
) {
    // remember {} on purpose, never rememberSaveable {}: secrets must not go into the saved-state Bundle.
    var recoveryKey by remember { mutableStateOf("") }
    var regeneratePassword by remember { mutableStateOf("") }
    var keySaved by remember { mutableStateOf(false) }

    LaunchedEffect(state.newRecoveryKey) {
        regeneratePassword = ""
        keySaved = false
    }

    SectionCard("Check your recovery key", info = "Type the recovery key you saved to confirm that it is correct. Nothing is changed.") {
        AppTextField(
            value = recoveryKey,
            onValueChange = { recoveryKey = it },
            label = "Recovery key",
            enabled = !state.working,
            singleLine = false,
            minLines = 2,
        )
        SecondaryButton(
            text = "Check recovery key",
            onClick = { onVerifyRecovery(recoveryKey) },
            enabled = !state.working && recoveryKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val freshKey = state.newRecoveryKey
    if (freshKey != null) {
        SectionCard(
            "Your new recovery key",
            info = "Your old recovery key no longer works. Save this one now: it is shown only once. Print the Emergency Kit and keep the paper somewhere safe, away from this phone. After printing, delete the PDF from your phone and from anywhere it was sent, because it contains the key.",
        ) {
            Hint("Shown only once. Your old key no longer works.")
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = freshKey.split("-").chunked(4).joinToString("\n") { it.joinToString("-") },
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            SecondaryButton(text = "Save Emergency Kit (PDF)", onClick = onSaveKit, modifier = Modifier.fillMaxWidth())
            Hint("Print it, then delete the PDF.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = keySaved, onCheckedChange = { keySaved = it })
                Text("I have saved my new recovery key", style = MaterialTheme.typography.bodyMedium)
            }
            PrimaryButton(text = "Done", onClick = onDismissKey, enabled = keySaved)
        }
    } else {
        SectionCard(
            "Replace your recovery key",
            info = "Lost your recovery key, or think someone saw it? Create a new one. The old key stops working immediately.",
        ) {
            AppTextField(
                value = regeneratePassword,
                onValueChange = { regeneratePassword = it },
                label = "Master password",
                secret = true,
                enabled = !state.working,
                imeAction = ImeAction.Done,
                onImeAction = { onRegenerateRecovery(regeneratePassword) },
            )
            SecondaryButton(
                text = "Create new recovery key",
                onClick = { onRegenerateRecovery(regeneratePassword) },
                enabled = !state.working && regeneratePassword.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BackupPage(
    working: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onExportPlain: (password: String, json: Boolean) -> Unit,
) {
    var plainDialog by remember { mutableStateOf<Boolean?>(null) } // null = closed, true = JSON, false = CSV
    var plainPassword by remember { mutableStateOf("") }
    SectionCard(
        "Encrypted backup",
        info = "Save an encrypted copy of your vault. It is useless without your master password or recovery key, so you can keep it in cloud storage.",
    ) {
        Hint("Save an encrypted copy of your vault.")
        PrimaryButton(text = "Export encrypted backup", onClick = onExport, enabled = !working)
    }

    SectionCard(
        "Restore",
        info = "To restore a backup on a new phone, install PassVault and choose \"Restore from backup\" on the first screen. For safety, a restore never overwrites a vault that already exists on the phone.",
    ) {
        Hint("Done from the first screen on a new phone.")
    }

    SectionCard(
        "Export to another app",
        info = "Take your data to another password manager. The file is NOT encrypted: anyone who gets it can read every password, so PassVault asks for your master password first. Import it where you need it, then delete the file everywhere. JSON keeps cards, notes, folders, 2FA keys and extra fields (Bitwarden format). CSV holds logins only.",
    ) {
        Hint("Not encrypted. Delete the file after importing.")
        SecondaryButton("Export as JSON (everything)", onClick = { plainPassword = ""; plainDialog = true }, enabled = !working, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Export logins as CSV", onClick = { plainPassword = ""; plainDialog = false }, enabled = !working, modifier = Modifier.fillMaxWidth())
    }

    plainDialog?.let { json ->
        AlertDialog(
            onDismissRequest = { plainDialog = null; plainPassword = "" },
            title = { Text("Export without encryption?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "The file will contain your passwords in plain text. Only do this to move to another password manager, and delete the file afterwards.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppTextField(
                        value = plainPassword,
                        onValueChange = { plainPassword = it },
                        label = "Master password",
                        secret = true,
                        imeAction = ImeAction.Done,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = plainPassword.isNotEmpty(),
                    onClick = {
                        val password = plainPassword
                        plainDialog = null
                        plainPassword = ""
                        onExportPlain(password, json)
                    },
                ) { Text("Export", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { plainDialog = null; plainPassword = "" }) { Text("Cancel") } },
        )
    }

    SectionCard(
        "Import from another app",
        info = "Bring your data from Chrome, Edge, Firefox, Bitwarden, 1Password, LastPass or another manager. Export it there as a CSV file (logins only), from Bitwarden as an unencrypted .json file, or from 1Password as a .1pux file. JSON and .1pux also bring cards, secure notes, folders, favourites, 2FA codes and custom fields. Items already in your vault are skipped. The file holds your passwords in plain text, so delete it right after importing.",
    ) {
        Hint("Chrome, Bitwarden, 1Password and more. Delete the file afterwards.")
        PrimaryButton(text = "Choose file to import", onClick = onImport, enabled = !working)
    }
}

@Composable
private fun AutofillPage(enabled: Boolean, onTurnOn: () -> Unit) {
    val setup = "Let PassVault fill logins into other apps and websites. Android will ask you to choose PassVault as the autofill service. In Chrome you also need to switch on Settings -> Autofill services -> \"Autofill using another service\"."
    val usage = "When you tap a username or password box in another app or website, choose PassVault in the suggestion to fill a saved login."
    val tips = "Fill in the Website field of each login: PassVault only offers a login automatically on the website it was saved for. In apps, and on other sites, you pick the login yourself."
    SectionCard(
        if (enabled) "Autofill is on" else "Autofill is off",
        info = (if (enabled) usage else setup) + "\n\n" + tips,
    ) {
        Hint(if (enabled) "PassVault fills logins in other apps and websites." else "Turn it on to fill logins in other apps and websites.")
        if (!enabled) PrimaryButton(text = "Turn on autofill", onClick = onTurnOn)
    }
}

@Composable
private fun AppearancePage(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    themeMode: ThemeMode, onTheme: (ThemeMode) -> Unit,
    iconsOn: Boolean,
    onChange: (Boolean) -> Unit,
    groupSites: Boolean,
    onGroupSites: (Boolean) -> Unit,
) {
    SectionCard(
        "Language",
        info = "Choose the language of the app. \"System\" follows your phone's language. Messages that come from websites or from Android itself stay as they are.",
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { option ->
                FilterChip(
                    selected = language == option,
                    onClick = { onLanguage(option) },
                    label = {
                        Text(
                            option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    SectionCard(
        "Theme",
        info = "Dark mode is easier on the eyes at night and saves battery on OLED screens. \"System\" follows Android's own setting.",
    ) {
        // Equal-width buttons that always fit the screen: no horizontal overflow, whatever the font size.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onTheme(mode) },
                    label = {
                        Text(
                            mode.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    SectionCard(
        "Your list",
        info = "If you have several logins for one site, such as three Amazon accounts, they are shown as one entry that you tap to open. Each account is listed by its username or email, and you can give an account a name such as Work or Personal when you edit it. Turn this off to see every login on its own row. While you search, every match is always listed on its own.",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Group accounts from the same site", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(if (groupSites) "On" else "Off (every login on its own row)", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = groupSites, onCheckedChange = onGroupSites)
        }
    }
    SectionCard(
        "Website icons",
        info = "Logins always get a coloured badge with the site's initial, drawn on your phone, with well-known sites such as Amazon and Facebook in their brand colour. Nothing is sent anywhere for that.\n\nIf you turn this switch on, PassVault asks DuckDuckGo's icon service over HTTPS for each site's icon. Only the site's domain (for example amazon.com) is sent, never usernames, passwords or notes. Icons are cached on this phone, and turning the switch off deletes them.",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show real website icons", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (iconsOn) "On" else "Off (coloured letter badges are used)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = iconsOn, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun AboutPage(onTour: () -> Unit) {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    SectionCard("PassVault") {
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Hint("A password manager that keeps everything encrypted on your device.")
        SecondaryButton("Take the tour", onClick = onTour)
    }
    SectionCard(
        "How your data is protected",
        info = "Your vault is encrypted with AES-256. The key is protected by your master password (Argon2id) and your recovery key, and neither is ever stored or sent anywhere. PassVault has no server and works fully offline. There are no ads and no trackers.\n\nBecause nobody can reset your master password, keep your recovery key somewhere safe.",
    ) {
        Hint("Encrypted on your device. No ads, no trackers.")
    }
    var saveLog by remember { mutableStateOf(SaveLog.read(context)) }
    saveLog?.let { log ->
        SectionCard(
            "Last save attempts",
            info = "When Android asks \"Save to PassVault?\" and you tap Save, PassVault notes here what happened (for example, that no card number could be read). It never records numbers, passwords or websites.",
        ) {
            Text(log, style = MaterialTheme.typography.bodySmall)
            SecondaryButton("Clear", onClick = {
                SaveLog.clear(context)
                saveLog = null
            })
        }
    }
    crash?.let { report ->
        SectionCard(
            "App crashed last time",
            info = "PassVault saved a short report on this phone. It has no passwords or messages, only which part of the app failed. Nothing is sent unless you share it. Sending it to the developer helps fix the problem.",
        ) {
            Hint("A short report is ready to share.")
            SecondaryButton("Share report", onClick = { CrashLog.share(context, report) })
            SecondaryButton("Delete report", onClick = {
                CrashLog.clear(context)
                crash = null
            })
        }
    }
}

@Composable
private fun OptionRow(options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}
