package com.ritvik.passvault.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.ritvik.passvault.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvik.passvault.apps.InstalledApps
import com.ritvik.passvault.autofill.AppIds
import com.ritvik.passvault.sites.IconLoader
import com.ritvik.passvault.sites.SiteInfo

/** Provided once at the top of each screen tree; null means "no real website icons" (previews, tests). */
val LocalIconLoader = staticCompositionLocalOf<IconLoader?> { null }

private val PALETTE = listOf(
    Color(0xFF3F6BFF),
    Color(0xFF8E4EC6),
    Color(0xFF12A594),
    Color(0xFFE5484D),
    Color(0xFFE08A00),
    Color(0xFF3E8E41),
    Color(0xFF0E7490),
    Color(0xFFC2410C),
)

/**
 * The little picture next to a login. If the login is linked to an app that is installed on this phone, the app's own
 * icon is shown (it comes from Android, nothing is downloaded). Otherwise known sites (Amazon, Facebook, Google ...)
 * get their brand colour with a letter, and others a stable colour from the site name. If the user turned on
 * "Website icons" in Settings, the real site icon replaces the badge once it has loaded.
 *
 * @param fetchUrl the address whose icon may be downloaded, when that should lag behind [url]. The editor passes the
 *   address only after typing has paused on something that looks finished, so half-typed names are never sent.
 */
@Composable
fun SiteIcon(
    title: String,
    url: String,
    username: String,
    size: Dp = 42.dp,
    appId: String = "",
    fetchUrl: String = url,
) {
    val context = LocalContext.current
    val name = remember(title, url, username) { SiteInfo.displayName(title, url, username) }
    val domain = remember(url) { SiteInfo.domainOf(url) }
    val fetchDomain = remember(fetchUrl) { SiteInfo.domainOf(fetchUrl) }
    val brand = remember(url) { SiteInfo.brandFor(url) }
    val loader = LocalIconLoader.current
    val enabled = loader?.enabled?.collectAsState()?.value ?: false

    var icon by remember(fetchDomain, enabled) {
        mutableStateOf(if (enabled && fetchDomain != null) loader?.peek(fetchDomain) else null)
    }
    LaunchedEffect(fetchDomain, enabled) {
        if (enabled && fetchDomain != null && loader != null && icon == null) icon = loader.load(fetchDomain)
    }

    val app = remember(appId) { AppIds.linkable(appId) }
    var appIcon by remember(app) { mutableStateOf(app?.let { InstalledApps.peekIcon(it) }) }
    LaunchedEffect(app) {
        if (app != null && appIcon == null) appIcon = InstalledApps.icon(context, app)
    }

    val shape = RoundedCornerShape(size * 0.28f)
    val badgeColor = brand?.let { Color(it.color) }
        ?: PALETTE[((domain ?: name).lowercase().hashCode() and Int.MAX_VALUE) % PALETTE.size]
    val image = appIcon ?: icon

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (image != null) Color.White else badgeColor)
            .then(if (image != null) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(size * 0.16f),
            )
        } else {
            Text(
                text = SiteInfo.letter(name),
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
