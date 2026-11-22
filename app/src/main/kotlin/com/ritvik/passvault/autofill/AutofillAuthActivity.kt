package com.ritvik.passvault.autofill

import com.ritvik.passvault.ui.Strings
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ritvik.passvault.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import com.ritvik.passvault.ui.LocalIconLoader
import com.ritvik.passvault.ui.SiteIcon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.ritvik.passvault.ActivityLog
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.applyScreenshotPolicy
import com.ritvik.passvault.PassVaultApp
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.vault.TypeBadge
import com.ritvik.passvault.search.FuzzySearch
import com.ritvik.passvault.items.logins
import com.ritvik.passvault.ui.AppTextField
import com.ritvik.passvault.ui.PassVaultTheme
import com.ritvik.passvault.unlock.UnlockHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shown on top of another app when the user taps "Unlock PassVault" or "Choose from PassVault" in the
 * autofill drop-down: unlock if needed, then pick the login to fill. The chosen login goes back to the system
 * as the answer; nothing is filled until the user taps an entry.
 */
class AutofillAuthActivity : FragmentActivity() {
    private lateinit var container: AppContainer
    private var wasLocked = false
    private var answered = false
    private var fillTarget = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as PassVaultApp).container
        window.applyScreenshotPolicy(container.settings.allowScreenshots.value)
        enableEdgeToEdge()
        wasLocked = !container.session.isUnlocked

        val usernameId = IntentCompat.getParcelableExtra(intent, EXTRA_USERNAME_ID, AutofillId::class.java)
        val passwordId = IntentCompat.getParcelableExtra(intent, EXTRA_PASSWORD_ID, AutofillId::class.java)
        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val callerPackage = intent.getStringExtra(EXTRA_PACKAGE)
        // Set when the form on screen is a payment form: the picker then lists saved cards, not logins.
        val cardIds = CardFormIds.fromIntent(intent)
        fillTarget = webDomain ?: callerPackage ?: ""
        if (cardIds == null && usernameId == null && passwordId == null) {
            cancel()
            return
        }

        setContent {
            val themeMode by container.settings.themeMode.collectAsState()
            PassVaultTheme(themeMode) {
              CompositionLocalProvider(LocalIconLoader provides container.icons) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.safeDrawingPadding()) {
                        val unlocked by container.session.unlocked.collectAsState()
                        if (!unlocked) {
                            UnlockHost(container, this@AutofillAuthActivity)
                        } else {
                            Picker(
                                container = container,
                                webDomain = webDomain,
                                callerPackage = callerPackage,
                                cards = cardIds != null,
                                onPick = { item ->
                                    if (cardIds != null) answerCard(cardIds, item) else answer(usernameId, passwordId, item)
                                },
                            )
                        }
                    }
                }
              }
            }
        }
    }

    private fun answer(usernameId: AutofillId?, passwordId: AutofillId?, item: VaultItem) {
        if (answered) return
        answered = true
        container.activityLog.record(ActivityLog.KIND_AUTOFILL, ActivityLog.nameOf(item) + " -> " + fillTarget)
        val dataset = AutofillDatasets.valuesOnly(usernameId, passwordId, item)
        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        finish()
    }

    private fun answerCard(card: CardFormIds, item: VaultItem) {
        if (answered) return
        val dataset = AutofillDatasets.cardValuesOnly(card, item)
        if (dataset == null) {
            Toast.makeText(this, Strings.tr("This card has nothing PassVault can fill into this form."), Toast.LENGTH_LONG).show()
            return
        }
        answered = true
        container.activityLog.record(ActivityLog.KIND_AUTOFILL, item.displayTitle + " (card) -> " + fillTarget)
        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        finish()
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onStop() {
        super.onStop()
        // Leaving this screen (home button, switching apps) cancels the request.
        if (!isChangingConfigurations && !isFinishing) cancel()
    }

    override fun onDestroy() {
        // If this screen is what unlocked the vault, do not leave it unlocked behind the user's back.
        if (!isChangingConfigurations && wasLocked) container.session.lock()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_USERNAME_ID = "passvault.username_id"
        const val EXTRA_PASSWORD_ID = "passvault.password_id"
        const val EXTRA_WEB_DOMAIN = "passvault.web_domain"
        const val EXTRA_PACKAGE = "passvault.package"
    }
}

@Composable
private fun Picker(
    container: AppContainer,
    webDomain: String?,
    callerPackage: String?,
    cards: Boolean,
    onPick: (VaultItem) -> Unit,
) {
    var items by remember { mutableStateOf<List<VaultItem>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            container.session.withKey { key ->
                val all = container.itemRepository.list(key)
                if (cards) all.filter { it.type == ItemType.CARD && !it.inTrash } else all.logins()
            }
        } ?: emptyList()
    }

    val all = items
    val shown = remember(all, query, webDomain) {
        if (cards) {
            // Card numbers, codes and PINs are never searched; names and the cardholder are enough.
            return@remember all.orEmpty()
                .filter { FuzzySearch.matches(query, listOf(it.displayTitle, it.title, it.cardholder, it.folder)) }
                .sortedWith(compareByDescending<VaultItem> { it.favorite }.thenBy { it.displayTitle.lowercase() })
        }
        val filtered = all.orEmpty().filter {
            FuzzySearch.matches(query, listOf(it.displayTitle, it.title, it.username, it.url, it.appId))
        }
        // Logins for this site first.
        filtered.sortedWith(
            compareByDescending<VaultItem> {
                if (webDomain != null) {
                    DomainMatcher.matches(it.url, webDomain)
                } else {
                    it.appId.isNotBlank() && it.appId.equals(callerPackage, ignoreCase = true)
                }
            }
                .thenBy { it.displayTitle.lowercase() },
        )
    }

    // Capped width so the list stays readable on tablets and in landscape.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(if (cards) "Fill a card" else "Fill a login", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "For: ${webDomain ?: callerPackage ?: "this app"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            AppTextField(value = query, onValueChange = { query = it }, label = "Search")
            when {
                all == null -> Unit
                shown.isEmpty() -> Text(
                    when {
                        cards && all.isEmpty() -> "You haven't saved any cards yet."
                        cards -> "No matching cards."
                        all.isEmpty() -> "Your vault is empty."
                        else -> "No matching logins."
                    },
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(shown, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(item) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (cards) TypeBadge(item.type, size = 40.dp) else SiteIcon(item.title, item.url, item.username, size = 40.dp, appId = item.appId)
                            Column {
                                Text(item.displayTitle, fontWeight = FontWeight.Medium)
                                val second = if (cards) item.subtitle else item.username
                                if (second.isNotBlank()) {
                                    Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
