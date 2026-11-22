package com.ritvik.passvault.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.ritvik.passvault.autofill.AppIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One app on this phone that can be opened from the launcher. */
data class AppEntry(val label: String, val packageName: String)

/**
 * The apps installed on this phone, for linking a login to an app. It only asks Android for apps that appear in the
 * launcher (declared with <queries> in the manifest), so PassVault does not need the broad "see all apps"
 * permission. Names and icons come from Android and never leave the phone.
 */
object InstalledApps {
    private val icons = LruCache<String, ImageBitmap>(150)

    /** Launcher apps, A to Z. PassVault itself and web browsers are left out: a browser shows every website. */
    suspend fun list(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName || AppIds.isBrowser(pkg)) return@mapNotNull null
                AppEntry(info.loadLabel(pm).toString().trim().ifEmpty { AppIds.readableName(pkg) }, pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** The app's name, or null if it is not installed on this phone (a login synced from another device, say). */
    fun label(context: Context, packageName: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** An icon that is already loaded, or null. Safe to call while drawing. */
    fun peekIcon(packageName: String): ImageBitmap? = icons.get(packageName)

    suspend fun icon(context: Context, packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        icons.get(packageName)?.let { return@withContext it }
        try {
            val bitmap = context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            icons.put(packageName, bitmap)
            bitmap
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }
}
