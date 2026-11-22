package com.ritvik.passvault.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ritvik.passvault.ui.PrimaryButton
import com.ritvik.passvault.ui.ScreenColumn
import com.ritvik.passvault.ui.SecondaryButton

private class TourPage(val title: String, val points: List<String>, val action: TourAction? = null)

private enum class TourAction { TURN_ON_AUTOFILL }

private val PAGES = listOf(
    TourPage(
        "Only you can open your vault",
        listOf(
            "Everything is encrypted on this phone with your master password. We never see it, and nobody can reset it for you.",
            "Your recovery key is the only way back in if you forget the password. Keep the Emergency Kit PDF somewhere safe, away from this phone.",
            "PassVault locks itself after a while and whenever the screen turns off. Unlock with your fingerprint or face if you've turned that on.",
        ),
    ),
    TourPage(
        "Save logins, cards and notes",
        listOf(
            "Tap + to add a login, a payment card or a secure note. Add a 2FA key to see the live six-digit code right in PassVault.",
            "The generator makes strong passwords or easy-to-type passphrases. Favourites and folders keep things tidy, and search forgives typos.",
            "Already use another manager? Settings -> Backup & import brings in a Chrome, Bitwarden, 1Password or LastPass export.",
        ),
    ),
    TourPage(
        "Let PassVault fill things in",
        listOf(
            "Turn on autofill and PassVault offers your logins in apps and websites. It only fills a site's login on that site.",
            "On payment forms it offers your saved cards. You always choose the card yourself, so nothing is filled without a tap.",
        ),
        TourAction.TURN_ON_AUTOFILL,
    ),
    TourPage(
        "Stay safe, share carefully",
        listOf(
            "The Security check finds weak, reused and old passwords, checks for data breaches without sending your passwords anywhere, and warns about expired cards.",
            "To give someone an item, open it and tap Share securely. It makes an encrypted link or QR code that only opens with a passphrase you send separately.",
            "Make an encrypted backup now and then (Settings -> Backup & import). Dark mode and other looks are under Settings -> Appearance.",
        ),
    ),
)

/**
 * A short, skippable tour shown once after a vault is created, and any time from Settings -> About.
 * Plain pages with Next and Back buttons, so there is nothing to swipe or discover.
 */
@Composable
fun TutorialScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = PAGES[index.coerceIn(0, PAGES.lastIndex)]
    val last = index >= PAGES.lastIndex

    ScreenColumn {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1} of ${PAGES.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!last) TextButton(onClick = onFinish) { Text("Skip") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            PAGES.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .size(width = if (i == index) 24.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            page.points.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }

        if (page.action == TourAction.TURN_ON_AUTOFILL) {
            SecondaryButton(
                "Turn on autofill",
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${context.packageName}"))
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },
            )
        }

        Box(modifier = Modifier.height(8.dp))
        PrimaryButton(if (last) "Start using PassVault" else "Next", onClick = { if (last) onFinish() else index++ })
        if (index > 0) SecondaryButton("Back", onClick = { index-- })
    }
}
