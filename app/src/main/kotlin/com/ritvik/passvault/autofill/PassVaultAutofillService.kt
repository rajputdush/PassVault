package com.ritvik.passvault.autofill

import com.ritvik.passvault.ui.Strings
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.widget.Toast
import com.ritvik.passvault.ActivityLog
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.SaveLog
import com.ritvik.passvault.PassVaultApp
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.items.logins

/**
 * Fills logins into other apps and websites.
 *
 *  - Vault locked: one entry, "Unlock PassVault", opens the unlock screen (fingerprint or master password).
 *  - Vault unlocked and the website matches a saved login: those logins are offered directly.
 *  - Anything else (apps, or a site with no saved login): "Choose from PassVault" opens a picker. A login is
 *    never filled into an app or site it was not saved for unless the user picks it there themselves.
 *  - Payment forms: always "Choose a card from PassVault", which opens a picker of saved cards. Cards are never
 *    offered in the drop-down and never filled without the user tapping the card. Cards are not saved from forms.
 */
class PassVaultAutofillService : AutofillService() {
    private val container: AppContainer get() = (application as PassVaultApp).container

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            val form = structure?.let { FormParser.parse(it) }
            // Never fill PassVault's own screens, and ignore pages with nothing to fill.
            if (form == null || form.ids.isEmpty() || form.packageName == packageName) {
                callback.onSuccess(null)
                return
            }

            val response = FillResponse.Builder()
            val unlocked = container.session.isUnlocked

            // A payment form. Cards are never put in the drop-down or filled directly, even when the vault is
            // unlocked: the user always opens PassVault and taps the card they want to use.
            if (form.card != null) {
                val label = if (unlocked) "Choose a card from PassVault" else "Unlock PassVault to use a card"
                response.addDataset(AutofillDatasets.needsScreen(this, form, label))
                callback.onSuccess(response.build())
                return
            }

            if (unlocked) {
                val logins = loadItems().logins()
                // Websites match by registrable domain. Apps match only logins linked to exactly that package
                // (browsers are never matched by package: the website decides there).
                val matches = if (form.webDomain != null) {
                    logins.filter { DomainMatcher.matches(it.url, form.webDomain) }
                } else {
                    SaveMatcher.forApp(logins, form.packageName, { it.appId }, { it.url })
                }
                val offered = matches.sortedBy { it.displayTitle.lowercase() }.take(MAX_DIRECT)
                offered.forEach { response.addDataset(AutofillDatasets.fillNow(this, form, it)) }
                // Android does not say which entry the user taps, so the log records that these were offered.
                offered.forEach {
                    container.activityLog.record(ActivityLog.KIND_OFFER, it.displayTitle + " -> " + (form.webDomain ?: form.packageName))
                }
            }
            val label = if (unlocked) "Choose from PassVault" else "Unlock PassVault"
            response.addDataset(AutofillDatasets.needsScreen(this, form, label))

            // Offer to save after the user signs in, when the form has a password field.
            // On sign-up and "change password" forms the new password is what gets saved or updated.
            val newPasswordId = form.newPasswordId ?: form.passwordId
            if (newPasswordId != null) {
                val optional = form.saveIds.filter { it != newPasswordId }
                val save = SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_PASSWORD, arrayOf(newPasswordId))
                if (optional.isNotEmpty()) save.setOptionalIds(optional.toTypedArray())
                // Single-screen apps and pages hide the form after sign-in instead of navigating away.
                save.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                response.setSaveInfo(save.build())
            }
            callback.onSuccess(response.build())
        } catch (e: Exception) {
            callback.onSuccess(null) // an autofill failure must never break the other app
        }
    }

    /**
     * The form to save. Android can hand over several screen snapshots; the newest one that actually holds what the
     * user typed wins, so a snapshot taken before typing (or after the boxes were cleared) is not mistaken for "nothing".
     */
    private fun formForSave(request: SaveRequest): ParsedForm? {
        var fallback: ParsedForm? = null
        for (context in request.fillContexts.asReversed()) {
            val form = FormParser.parse(context.structure) ?: continue
            if (form.card != null) continue // payment forms are filled from PassVault, never saved
            if (!form.passwordValue.isNullOrEmpty()) return form
            if (fallback == null) fallback = form
        }
        return fallback
    }

    /**
     * PassVault is locked, so the new item cannot be encrypted yet. Android is handed a screen to open instead: the
     * user unlocks, and the login that was just typed is saved. Before Android 9 that is not possible, so
     * the user is told to unlock PassVault first.
     */
    private fun askToUnlockAndSave(pending: PendingSave, whatWasLocked: String, callback: SaveCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            container.pendingSave = pending
            val unlockScreen = PendingIntent.getActivity(
                this, 0, Intent(this, AutofillSaveActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            )
            callback.onSuccess(unlockScreen.intentSender)
        } else {
            Toast.makeText(this, Strings.tr("PassVault was locked, so this $whatWasLocked was not saved. Unlock PassVault first, then try again."), Toast.LENGTH_LONG).show()
            callback.onSuccess()
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        try {
            val form = formForSave(request)
            if (form == null) SaveLog.record(this, "unknown", "no form found in the saved screen")
            if (form?.card != null) {
                callback.onSuccess()
                return
            }
            val password = form?.passwordValue
            if (form == null || password.isNullOrEmpty()) {
                callback.onSuccess()
                return
            }
            if (!container.session.isUnlocked) {
                // The vault key is needed to encrypt the new login, and it is only in memory while unlocked.
                askToUnlockAndSave(PendingSave(login = form), "login", callback)
                return
            }
            val message = when (AutofillSaver.save(container, this, form)) {
                AutofillSaver.Result.SAVED -> "Saved to PassVault"
                AutofillSaver.Result.UPDATED -> "Password updated in PassVault"
                AutofillSaver.Result.UNCHANGED -> "Already saved in PassVault"
                AutofillSaver.Result.UNKNOWN_SITE -> "PassVault couldn't tell which website this is, so nothing was saved."
                else -> "Could not save to PassVault"
            }
            val text = Strings.tr(message)
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
            // Some phones hide toasts from background services, so a small screen shows the result as well (Android 9+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val screen = PendingIntent.getActivity(
                    this, 1, Intent(this, AutofillSaveActivity::class.java).putExtra(AutofillSaveActivity.EXTRA_MESSAGE, text),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
                )
                callback.onSuccess(screen.intentSender)
            } else {
                callback.onSuccess()
            }
        } catch (e: Exception) {
            SaveLog.record(this, "unknown", "error: ${e.javaClass.simpleName}")
            callback.onFailure("Could not save")
        }
    }

    private fun loadItems(): List<VaultItem> =
        container.session.withKey { key -> container.itemRepository.list(key).filter { !it.inTrash } } ?: emptyList()

    private companion object {
        const val MAX_DIRECT = 5
    }
}
