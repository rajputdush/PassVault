package com.ritvik.passvault.autofill

import android.content.Context
import android.content.pm.PackageManager
import com.ritvik.passvault.ActivityLog
import com.ritvik.passvault.sites.SiteGroups
import com.ritvik.passvault.activity.ChangeSummary
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.items.logins

/** Saves a login the user just typed into another app or website, or updates the saved one if the password changed. */
object AutofillSaver {
    enum class Result { SAVED, UPDATED, UNCHANGED, UNKNOWN_SITE, FAILED }

    fun save(container: AppContainer, context: Context, form: ParsedForm): Result {
        val password = form.passwordValue?.takeIf { it.isNotEmpty() } ?: return Result.FAILED
        val username = form.usernameValue.orEmpty().trim()
        val domain = form.webDomain
        val pkg = form.packageName
        // A browser that did not report the page's address: saving "Chrome" as the site would be wrong and unsafe.
        if (domain == null && (pkg == null || AppIds.isBrowser(pkg))) return Result.UNKNOWN_SITE

        val appId = if (domain == null) AppIds.linkable(pkg) else null
        val label = if (domain == null) appLabel(context, pkg) else null
        // For websites the name is left empty: it is derived from the address. Apps have no address, so use the app's name.
        val title = label.orEmpty()

        val repo = container.itemRepository
        // Filled in while saving, for the activity log: is this the first account on the site, or another one?
        var accountNote = ""
        // Same lock order as everywhere else: items first, then the session key.
        val result = synchronized(repo.lock) {
            container.session.withKey { key ->
                val all = repo.list(key).logins()
                val existing = SaveMatcher.findExisting(
                    items = all,
                    webDomain = domain,
                    appId = appId,
                    appLabel = label,
                    username = username,
                    urlOf = { it.url },
                    titleOf = { it.title },
                    appIdOf = { it.appId },
                    usernameOf = { it.username },
                )
                when {
                    existing == null -> {
                        val site = SiteGroups.keyOf(domain.orEmpty(), appId.orEmpty())
                        val others = if (site == null) 0 else all.count { SiteGroups.keyOf(it.url, it.appId) == site }
                        accountNote = ChangeSummary.accountNote(others, site?.substringAfter(':'))
                        repo.save(
                            key,
                            VaultItem.blank().copy(
                                title = title,
                                username = username,
                                password = password,
                                url = domain.orEmpty(),
                                appId = appId.orEmpty(),
                                source = if (domain != null) "website:$domain" else "app:$label",
                            ),
                        )
                        Result.SAVED
                    }
                    existing.password == password -> Result.UNCHANGED
                    else -> {
                        val now = System.currentTimeMillis()
                        repo.save(
                            key,
                            existing.withPassword(password, now).copy(
                                updatedAt = now,
                                // Older app logins had no package; now that we know it, link them.
                                appId = existing.appId.ifBlank { appId.orEmpty() },
                            ),
                        )
                        Result.UPDATED
                    }
                }
            }
        } ?: Result.FAILED
        val shown = VaultItem.blank().copy(title = title, username = username, url = domain.orEmpty()).displayTitle
        val who = ChangeSummary.nameWithUser(shown, username)
        val origin = if (domain != null) "from website $domain" else "from the app $label"
        when (result) {
            Result.SAVED -> container.activityLog.record(
                ActivityLog.KIND_ADDED,
                "$who\nSaved through autofill ($origin)" + (if (accountNote.isNotEmpty()) "\n$accountNote" else ""),
            )
            Result.UPDATED -> container.activityLog.record(ActivityLog.KIND_UPDATED, "$who\nPassword changed through autofill ($origin)")
            else -> Unit
        }
        return result
    }

    private fun appLabel(context: Context, packageName: String?): String {
        if (packageName == null) return "Saved login"
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            AppIds.readableName(packageName)
        }
    }
}
