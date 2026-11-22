package com.ritvik.passvault.vault

import com.ritvik.passvault.ui.Strings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import com.ritvik.passvault.ui.Eyebrow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import com.ritvik.passvault.share.toastSaved
import com.ritvik.passvault.share.ShareItemDialog
import com.ritvik.passvault.share.ReceiveShareDialog
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ritvik.passvault.ui.warning
import com.ritvik.passvault.sites.SiteInfo
import com.ritvik.passvault.sites.SiteGroups
import com.ritvik.passvault.sites.ListEntry
import com.ritvik.passvault.autofill.AppIds
import com.ritvik.passvault.apps.InstalledApps
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.ActivityLog
import com.ritvik.passvault.ClipboardHelper
import com.ritvik.passvault.canUseBiometrics
import com.ritvik.passvault.confirmItsYou
import com.ritvik.passvault.crypto.AuditEntry
import com.ritvik.passvault.crypto.PasswordAudit
import com.ritvik.passvault.enableBiometricUnlock
import com.ritvik.passvault.cards.CardInfo
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.items.logins
import com.ritvik.passvault.search.FuzzySearch
import com.ritvik.passvault.security.SecurityPill
import com.ritvik.passvault.security.SecurityHost
import com.ritvik.passvault.settings.SettingsHost
import com.ritvik.passvault.totp.Totp
import com.ritvik.passvault.ui.AppTextField

/** A new, empty login for the same website or app as [existing]. */
private fun sameSiteLogin(existing: VaultItem): VaultItem =
    VaultItem.blank(ItemType.LOGIN).copy(url = existing.url, appId = existing.appId, folder = existing.folder)

private class EditTarget(val item: VaultItem, val isNew: Boolean)

/** Screens at least this wide (tablets, unfolded foldables) show the list and the open item side by side. */
private const val WIDE_LAYOUT_DP = 840
private val WIDE_LIST_WIDTH = 400.dp

@Composable
fun VaultHost(container: AppContainer, activity: FragmentActivity) {
    val viewModel: VaultViewModel = viewModel(
        factory = viewModelFactory { initializer { VaultViewModel(container) } },
    )
    val items by viewModel.items.collectAsState()
    val trash by viewModel.trash.collectAsState()
    var showTrash by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // When the fingerprint was last confirmed. Lives in composition memory, so locking the vault forgets it.
    var verifiedUntil by remember { mutableStateOf(0L) }
    // Runs [action] now, or after a fingerprint check if the user turned that on in Settings (valid for 30 seconds).
    val guarded: (String, () -> Unit) -> Unit = { title, action ->
        if (!container.settings.confirmBeforeReveal || !canUseBiometrics(activity) || System.currentTimeMillis() < verifiedUntil) {
            action()
        } else {
            activity.confirmItsYou(
                title = title,
                onSuccess = {
                    verifiedUntil = System.currentTimeMillis() + 30_000L
                    action()
                },
                onFailure = { message -> if (message != null) Toast.makeText(context, Strings.tr(message), Toast.LENGTH_SHORT).show() },
            )
        }
    }

    // These live in composition memory only, so they disappear the moment the vault locks.
    var target by remember { mutableStateOf<EditTarget?>(null) }
    var query by remember { mutableStateOf("") }
    var filterKey by remember { mutableStateOf(FILTER_ALL) }
    var showTypePicker by remember { mutableStateOf(false) }
    var biometricOn by remember { mutableStateOf(container.biometricVault.isEnabled()) }
    var showSettings by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }

    val attentionCount = remember(items) {
        val entries = items.logins().map { AuditEntry(it.id, it.displayTitle, it.password, it.passwordAge) }
        PasswordAudit.analyze(entries, System.currentTimeMillis()).attentionIds.size
    }
    val folders = remember(items) { folderNames(items) }

    // Google's code scanner runs in Play services, so PassVault itself never needs the camera permission.
    val scanQr: ((String) -> Unit) -> Unit = { onResult ->
        // The scanner moves this app to the background; don't auto-lock (and lose the form) while it is open.
        container.autoLockSuspended = true
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(activity, options).startScan()
            .addOnSuccessListener { barcode ->
                container.autoLockSuspended = false
                barcode.rawValue?.let(onResult)
            }
            .addOnCanceledListener { container.autoLockSuspended = false }
            .addOnFailureListener {
                container.autoLockSuspended = false
                Toast.makeText(context, Strings.tr("Couldn't scan. Paste the setup key instead."), Toast.LENGTH_LONG).show()
            }
    }

    var sharing by remember { mutableStateOf<VaultItem?>(null) }
    var showReceive by remember { mutableStateOf(false) }
    val pendingShare by container.pendingShare.collectAsState()
    val groupSites by container.settings.groupSites.collectAsState()

    // Tablets and landscape screens get a list on the left and the open item on the right.
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_DP

    val copyMain: (VaultItem) -> Unit = {
        when (it.type) {
            ItemType.LOGIN -> {
                ClipboardHelper.copy(context, "Password", it.password, container.settings.clipboardClearSeconds)
                container.activityLog.record(ActivityLog.KIND_COPY, "Password of " + ActivityLog.nameOf(it))
            }
            ItemType.CARD -> {
                ClipboardHelper.copy(
                    context, "Card number", CardInfo.digitsOnly(it.cardNumber), container.settings.clipboardClearSeconds,
                )
                container.activityLog.record(ActivityLog.KIND_COPY, "Card number of " + ActivityLog.nameOf(it))
            }
            ItemType.NOTE -> Unit
        }
    }
    val copyCode: (VaultItem) -> Unit = {
        Totp.parse(it.totp)?.let { config ->
            ClipboardHelper.copy(
                context, "2FA code", Totp.code(config, System.currentTimeMillis()), container.settings.clipboardClearSeconds,
            )
            container.activityLog.record(ActivityLog.KIND_COPY, "2FA code of " + ActivityLog.nameOf(it))
        }
    }

    val editing = target
    val toggleBiometric: () -> Unit = {
        if (biometricOn) {
            container.biometricVault.disable()
            biometricOn = false
            container.activityLog.record(ActivityLog.KIND_SETTING, "Fingerprint unlock turned off")
        } else {
            enableBiometricUnlock(activity, container) { ok, message ->
                biometricOn = ok
                if (ok) container.activityLog.record(ActivityLog.KIND_SETTING, "Fingerprint unlock turned on")
                val text = if (ok) "Fingerprint unlock is on." else message
                if (text != null) Toast.makeText(context, Strings.tr(text), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val listPane: @Composable (String?) -> Unit = { selectedId ->
        VaultListScreen(
            items = items,
            query = query,
            onQueryChange = { query = it },
            filterKey = filterKey,
            onFilterChange = { filterKey = it },
            onAdd = { showTypePicker = true },
            onOpen = { item -> guarded("Open this item") { target = EditTarget(item, isNew = false) } },
            onCopyMain = { item -> guarded("Copy to clipboard") { copyMain(item) } },
            onCopyCode = { item -> guarded("Copy 2FA code") { copyCode(item) } },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onLock = { container.session.lock() },
            onSettings = { showSettings = true },
            attentionCount = attentionCount,
            onSecurity = { showSecurity = true },
            trashCount = trash.size,
            onTrash = { showTrash = true },
            biometricAvailable = canUseBiometrics(activity),
            biometricOn = biometricOn,
            onToggleBiometric = toggleBiometric,
            selectedId = selectedId,
            groupSites = groupSites,
            onAddAccount = { existing -> guarded("Add an account") { target = EditTarget(sameSiteLogin(existing), isNew = true) } },
        )
        if (showTypePicker) {
            TypePickerDialog(
                onPick = {
                    showTypePicker = false
                    target = EditTarget(VaultItem.blank(it), isNew = true)
                },
                onReceive = {
                    showTypePicker = false
                    showReceive = true
                },
                onDismiss = { showTypePicker = false },
            )
        }
    }

    val editorPane: @Composable (EditTarget) -> Unit = { e ->
        // Keyed by item so that picking another item in the tablet list starts a fresh form instead of reusing the old one.
        key(e.item.id) {
            ItemEditScreen(
                item = e.item,
                isNew = e.isNew,
                folders = folders,
                onSave = {
                    viewModel.saveEdited(it, e.isNew)
                    target = null
                },
                onDelete = {
                    viewModel.delete(e.item.id)
                    target = null
                },
                onBack = { target = null },
                onAddAnother = if (!e.isNew && e.item.type == ItemType.LOGIN && (e.item.url.isNotBlank() || e.item.appId.isNotBlank())) {
                    { target = EditTarget(sameSiteLogin(e.item), isNew = true) }
                } else null,
                onCopy = { label, text ->
                    ClipboardHelper.copy(context, label, text, container.settings.clipboardClearSeconds)
                    container.activityLog.record(ActivityLog.KIND_COPY, label + " of " + ActivityLog.nameOf(e.item))
                },
                onScanQr = scanQr,
                onShare = { sharing = e.item },
            )
        }
    }

    // The four main screens share a bottom bar. It hides while an item or the Trash is open.
    val onMainScreen = !showTrash && (wide || editing == null)
    Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
    if (showTrash) {
        BackHandler { showTrash = false }
        TrashScreen(
            items = trash,
            onRestore = viewModel::restore,
            onDeleteForever = viewModel::deleteForever,
            onEmpty = viewModel::emptyTrash,
            onBack = { showTrash = false },
        )
    } else if (showGenerator) {
        BackHandler { showGenerator = false; showSettings = true }
        GeneratorScreen(container, onBack = { showGenerator = false; showSettings = true })
    } else if (showSettings) {
        SettingsHost(
            container = container,
            onBack = { showSettings = false },
            biometricAvailable = canUseBiometrics(activity),
            biometricOn = biometricOn,
            onToggleBiometric = toggleBiometric,
            onOpenGenerator = { showSettings = false; showGenerator = true },
        )
    } else if (showSecurity) {
        BackHandler { showSecurity = false }
        SecurityHost(
            items = items,
            onOpenItem = { item ->
                guarded("Open this item") {
                    showSecurity = false
                    target = EditTarget(item, isNew = false)
                }
            },
            onTrashItems = { ids -> ids.forEach(viewModel::delete) },
            onBack = { showSecurity = false },
        )
    } else if (wide) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(WIDE_LIST_WIDTH).fillMaxHeight()) { listPane(editing?.item?.id) }
            VerticalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (editing != null) {
                    BackHandler { target = null }
                    editorPane(editing)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Select an item, or tap + to add one",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    } else if (editing != null) {
        BackHandler { target = null }
        editorPane(editing)
    } else {
        listPane(null)
    }
    }
    }

    sharing?.let { item -> ShareItemDialog(container, item, onDismiss = { sharing = null }) }
    NoticeBanner(container)

    // A link someone tapped (pendingShare) or "Receive a shared item" from the + menu.
    if (showReceive || pendingShare != null) {
        ReceiveShareDialog(
            initialCode = pendingShare,
            onScanQr = scanQr,
            onSave = {
                viewModel.save(it)
                container.activityLog.record(ActivityLog.KIND_ADDED, it.displayTitle + " (received as a shared item)")
                container.notice.value = "Saved \"" + it.displayTitle + "\" to PassVault"
                container.pendingShare.value = null
                showReceive = false
                toastSaved(context)
            },
            onDismiss = {
                container.pendingShare.value = null
                showReceive = false
            },
        )
    }
}

/** A short message at the bottom of the screen that goes away by itself. */
@Composable
private fun NoticeBanner(container: AppContainer) {
    val notice by container.notice.collectAsState()
    val message = notice ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(3500)
        if (container.notice.value == message) container.notice.value = null
    }
    androidx.compose.ui.window.Popup(
        alignment = Alignment.BottomCenter,
        offset = androidx.compose.ui.unit.IntOffset(0, -260),
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

private const val FILTER_ALL = "all"
private const val FILTER_FAVORITES = "fav"
private const val FILTER_TYPE = "type:"
private const val FILTER_FOLDER = "folder:"

/** The distinct folder names in use, ignoring case, in alphabetical order. */
private fun folderNames(items: List<VaultItem>): List<String> =
    items.map { it.folder.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

private fun matchesFilter(item: VaultItem, key: String): Boolean = when {
    key == FILTER_ALL -> true
    key == FILTER_FAVORITES -> item.favorite
    key.startsWith(FILTER_TYPE) -> item.type.key == key.removePrefix(FILTER_TYPE)
    key.startsWith(FILTER_FOLDER) -> item.folder.trim().equals(key.removePrefix(FILTER_FOLDER), ignoreCase = true)
    else -> true
}

/** How well [item] matches what was typed (0 = not at all). Passwords and card numbers are never searched. */
private fun searchScore(item: VaultItem, query: String): Int {
    val fields = buildList {
        add(item.displayTitle)
        add(item.title)
        add(item.folder)
        item.customFields.forEach { add(it.label) }
        when (item.type) {
            ItemType.LOGIN -> {
                add(item.username)
                add(item.url)
                add(item.appId)
            }
            ItemType.CARD -> add(item.cardholder)
            ItemType.NOTE -> add(item.notes)
        }
    }
    return FuzzySearch.score(query, fields)
}

@Composable
private fun TypePickerDialog(onPick: (ItemType) -> Unit, onReceive: () -> Unit, onDismiss: () -> Unit) {
    val blurbs = mapOf(
        ItemType.LOGIN to "A website or app account with a password and optional 2FA code",
        ItemType.CARD to "A payment card: number, expiry, security code",
        ItemType.NOTE to "Private text: recovery codes, Wi-Fi passwords, anything",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What do you want to save?") },
        text = {
            Column {
                ItemType.entries.forEachIndexed { index, type ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(type) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TypeBadge(type, size = 40.dp)
                        Column {
                            Text(type.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                blurbs.getValue(type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onReceive() }.padding(vertical = 12.dp),
                ) {
                    Text("Receive a shared item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Open an encrypted link or QR code someone sent you",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun VaultListScreen(
    items: List<VaultItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    filterKey: String,
    onFilterChange: (String) -> Unit,
    onAdd: () -> Unit,
    onOpen: (VaultItem) -> Unit,
    onCopyMain: (VaultItem) -> Unit,
    onCopyCode: (VaultItem) -> Unit,
    onToggleFavorite: (VaultItem) -> Unit,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    attentionCount: Int,
    onSecurity: () -> Unit,
    trashCount: Int = 0,
    onTrash: () -> Unit = {},
    biometricAvailable: Boolean,
    biometricOn: Boolean,
    onToggleBiometric: () -> Unit,
    /** "Add another account" on a site that is already saved: opens a new login with the same site filled in. */
    onAddAccount: (VaultItem) -> Unit = {},
    /** On a tablet the open item stays highlighted in the list. */
    selectedId: String? = null,
    /** Put logins from the same site or app together in one expandable entry. */
    groupSites: Boolean = true,
) {
    val folders = remember(items) { folderNames(items) }
    val types = remember(items) { ItemType.entries.filter { t -> items.any { it.type == t } } }
    val hasFavorites = remember(items) { items.any { it.favorite } }

    // A filter whose items are all gone (last favourite removed, folder emptied) falls back to "All".
    val activeFilter = when {
        filterKey == FILTER_ALL -> FILTER_ALL
        filterKey == FILTER_FAVORITES && hasFavorites -> filterKey
        filterKey.startsWith(FILTER_TYPE) && types.any { FILTER_TYPE + it.key == filterKey } -> filterKey
        filterKey.startsWith(FILTER_FOLDER) && folders.any { FILTER_FOLDER + it.lowercase() == filterKey.lowercase() } -> filterKey
        else -> FILTER_ALL
    }
    val showChips = hasFavorites || types.size > 1 || folders.isNotEmpty() || trashCount > 0

    val shown = remember(items, query, activeFilter) {
        val q = query.trim()
        items.filter { matchesFilter(it, activeFilter) }
            .map { it to searchScore(it, q) }
            .filter { it.second > 0 }
            // Best match first while searching; favourites, then A-Z otherwise.
            .sortedWith(
                compareByDescending<Pair<VaultItem, Int>> { if (q.isEmpty()) 0 else it.second }
                    .thenByDescending { it.first.favorite }
                    .thenBy { it.first.displayTitle.lowercase() },
            )
            .map { it.first }
    }

    // Several accounts on one site are shown as one entry. While searching, every match is listed on its own so
    // nothing is hidden inside a collapsed group.
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }
    val entries = remember(shown, query, groupSites) {
        if (!groupSites || query.isNotBlank()) {
            shown.map { ListEntry.Single(it) }
        } else {
            SiteGroups.group(shown) { if (it.type == ItemType.LOGIN) SiteGroups.keyOf(it.url, it.appId) else null }
        }
    }

    val context = LocalContext.current
    val loginCount = remember(items) { items.logins().size }

    // Without a search, the list reads A to Z with a letter above each group. While searching, best match first.
    val ordered = remember(entries, query) {
        if (query.isBlank()) entries.sortedBy { entryTitle(it, context).lowercase() } else entries
    }
    val rows = remember(ordered, query) {
        val out = ArrayList<ListRow>(ordered.size + 8)
        var letter = ""
        for (entry in ordered) {
            if (query.isBlank()) {
                val first = entryTitle(entry, context).trim().firstOrNull()?.uppercaseChar()
                val l = if (first != null && first.isLetter()) first.toString() else "#"
                if (l != letter) {
                    letter = l
                    out.add(ListRow.Header(l))
                }
            }
            out.add(ListRow.Entry(entry))
        }
        out
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAdd,
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("New item", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("Pass")
                            withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Vault") }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (items.logins().isNotEmpty()) {
                    SecurityPill(attentionCount, onSecurity, modifier = Modifier.padding(end = 10.dp))
                }
                RoundIconButton(onClick = onSettings, description = "Settings") {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                RoundIconButton(
                    onClick = onLock,
                    description = "Lock now",
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(start = 10.dp),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            SearchPill(query, onQueryChange)

            if (showChips) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = activeFilter == FILTER_ALL, onClick = { onFilterChange(FILTER_ALL) }, label = { Text("All") })
                    if (hasFavorites) {
                        FilterChip(
                            selected = activeFilter == FILTER_FAVORITES,
                            onClick = { onFilterChange(FILTER_FAVORITES) },
                            label = { Text("Favourites") },
                        )
                    }
                    if (types.size > 1) {
                        types.forEach { type ->
                            FilterChip(
                                selected = activeFilter == FILTER_TYPE + type.key,
                                onClick = { onFilterChange(FILTER_TYPE + type.key) },
                                label = { Text(type.plural) },
                            )
                        }
                    }
                    folders.forEach { name ->
                        FilterChip(
                            selected = activeFilter.equals(FILTER_FOLDER + name, ignoreCase = true),
                            onClick = { onFilterChange(FILTER_FOLDER + name.lowercase()) },
                            label = { Text(name) },
                        )
                    }
                    if (trashCount > 0) {
                        FilterChip(selected = false, onClick = onTrash, label = { Text("Trash ($trashCount)") })
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (types.size > 1) "All items" else "All logins",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(shown.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                if (query.isBlank()) Eyebrow("A-Z")
            }

            if (shown.isEmpty()) {
                EmptyState(hasItems = items.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        rows,
                        key = { row ->
                            when (row) {
                                is ListRow.Header -> "letter:" + row.letter
                                is ListRow.Entry -> when (val entry = row.entry) {
                                    is ListEntry.Single -> entry.item.id
                                    is ListEntry.Group -> "group:" + entry.key
                                }
                            }
                        },
                    ) { row ->
                        when (row) {
                            is ListRow.Header -> Text(
                                row.letter,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                            )
                            is ListRow.Entry -> when (val entry = row.entry) {
                                is ListEntry.Single -> ItemCard(
                                    entry.item,
                                    onOpen = { onOpen(entry.item) },
                                    onCopy = { onCopyMain(entry.item) },
                                    onCopyCode = { onCopyCode(entry.item) },
                                    onToggleFavorite = { onToggleFavorite(entry.item) },
                                    selected = entry.item.id == selectedId,
                                )
                                is ListEntry.Group -> GroupCard(
                                    group = entry,
                                    expanded = entry.key in expandedGroups,
                                    onToggle = {
                                        expandedGroups = if (entry.key in expandedGroups) expandedGroups - entry.key else expandedGroups + entry.key
                                    },
                                    selectedId = selectedId,
                                    onOpen = onOpen,
                                    onCopyMain = onCopyMain,
                                    onCopyCode = onCopyCode,
                                    onToggleFavorite = onToggleFavorite,
                                    onAddAccount = { onAddAccount(entry.items.first()) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A row of the list: a letter heading or an entry. */
private sealed interface ListRow {
    class Header(val letter: String) : ListRow
    class Entry(val entry: ListEntry<VaultItem>) : ListRow
}

/** The name an entry is sorted under: the item's title, or the site or app name of a group. */
private fun entryTitle(entry: ListEntry<VaultItem>, context: android.content.Context): String = when (entry) {
    is ListEntry.Single -> entry.item.displayTitle
    is ListEntry.Group -> groupName(entry, context)
}

private fun groupName(group: ListEntry.Group<VaultItem>, context: android.content.Context): String =
    if (group.key.startsWith("app:")) {
        val pkg = group.key.removePrefix("app:")
        InstalledApps.label(context, pkg) ?: AppIds.readableName(pkg)
    } else {
        SiteInfo.displayName("", group.items.first().url, "")
    }

@Composable
private fun RoundIconButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) { icon() }
    }
}

@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search your vault", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** The small "Copy" button on a row. */
@Composable
private fun CopyPill(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(end = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(hasItems: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp),
        )
        Text(
            if (hasItems) "Nothing matches" else "Nothing saved yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (!hasItems) {
            Text(
                "Tap the + button to save a login, a card or a secure note.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemCard(
    item: VaultItem,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onCopyCode: () -> Unit,
    onToggleFavorite: () -> Unit,
    selected: Boolean = false,
) {
    val canCopy = when (item.type) {
        ItemType.LOGIN -> item.password.isNotEmpty()
        ItemType.CARD -> item.cardNumber.isNotEmpty()
        ItemType.NOTE -> false
    }
    val hasCode = item.type == ItemType.LOGIN && item.totp.isNotBlank() && Totp.isValid(item.totp)
    val compact = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ItemIcon(item)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOf(item.subtitle, item.folder.trim()).filter { it.isNotBlank() }.joinToString("  ·  ")
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (item.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (item.favorite) "Remove from favourites" else "Add to favourites",
                    tint = if (item.favorite) Color(0xFFF5A623) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (hasCode) CopyPill("2FA", onCopyCode)
            if (canCopy) CopyPill("Copy", onCopy)
        }
    }
}

/**
 * Several logins for the same site or app, as one entry. Closed, it says how many accounts there are and shows the
 * first few usernames; open, every account is a row with its own username, copy buttons and favourite star.
 */
@Composable
private fun GroupCard(
    group: ListEntry.Group<VaultItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    selectedId: String?,
    onOpen: (VaultItem) -> Unit,
    onCopyMain: (VaultItem) -> Unit,
    onCopyCode: (VaultItem) -> Unit,
    onToggleFavorite: (VaultItem) -> Unit,
    onAddAccount: () -> Unit,
) {
    val context = LocalContext.current
    val first = group.items.first()
    val siteName = remember(group.key) { groupName(group, context) }
    val duplicates = remember(group.items) { SiteGroups.duplicatePositions(group.items.map { it.username }) }
    val summary = remember(group.items) { SiteGroups.accountSummary(group.items.map { it.username }) }
    // The item open on a tablet must stay visible, so its group stays open.
    val open = expanded || group.items.any { it.id == selectedId }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ItemIcon(first)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        siteName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val line = if (!open && summary.isNotBlank()) "${group.items.size} accounts  ·  $summary" else "${group.items.size} accounts"
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (group.items.any { it.favorite }) {
                    Icon(Icons.Filled.Star, contentDescription = "Has a favourite", tint = Color(0xFFF5A623), modifier = Modifier.size(18.dp))
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = if (open) "Hide accounts" else "Show accounts",
                        modifier = Modifier.size(28.dp).rotate(if (open) 180f else 0f),
                    )
                }
            }
            if (open) {
                group.items.forEachIndexed { index, item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AccountRow(
                        item = item,
                        selected = item.id == selectedId,
                        duplicate = index in duplicates,
                        onOpen = { onOpen(item) },
                        onCopy = { onCopyMain(item) },
                        onCopyCode = { onCopyCode(item) },
                        onToggleFavorite = { onToggleFavorite(item) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddAccount)
                        .padding(start = 64.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Add another account", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** One account inside an open group: the username is the main line, since the site is already the heading. */
@Composable
private fun AccountRow(
    item: VaultItem,
    selected: Boolean,
    duplicate: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onCopyCode: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val canCopy = item.password.isNotEmpty()
    val hasCode = item.totp.isNotBlank() && Totp.isValid(item.totp)
    val compact = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    // A name the user gave this account ("Work", "Personal"), when it says more than the site name already does.
    val label = item.title.trim().takeIf { it.isNotEmpty() && !it.equals(SiteInfo.displayName("", item.url, ""), ignoreCase = true) }
    val details = listOfNotNull(label, item.folder.trim().takeIf { it.isNotEmpty() }).joinToString("  ·  ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(start = 64.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.username.ifBlank { "No username" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (duplicate) {
                Text("Possible duplicate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.warning)
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                if (item.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (item.favorite) "Remove from favourites" else "Add to favourites",
                tint = if (item.favorite) Color(0xFFF5A623) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        if (hasCode) CopyPill("2FA", onCopyCode)
        if (canCopy) CopyPill("Copy", onCopy)
    }
}
