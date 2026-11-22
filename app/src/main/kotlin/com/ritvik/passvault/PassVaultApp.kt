package com.ritvik.passvault

import android.app.Application
import android.content.Context
import com.ritvik.passvault.crypto.AttemptLimiter
import com.ritvik.passvault.crypto.KdfSettings
import com.ritvik.passvault.crypto.Pbkdf2KeyDeriver
import com.ritvik.passvault.crypto.VaultCrypto
import com.ritvik.passvault.items.ItemRepository
import kotlinx.coroutines.launch
import java.io.File

class PassVaultApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
    }
}

/** Simple manual dependency container. Swap for Hilt later if the app grows. */
class AppContainer(context: Context) {
    val vaultCrypto = VaultCrypto(
        derivers = listOf(Argon2KeyDeriver(), Pbkdf2KeyDeriver()),
        newVaultKdf = KdfSettings.argon2id(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1),
    )
    val headerStore = VaultHeaderStore(File(context.filesDir, "vault_header.json"))
    val itemRepository = ItemRepository(File(context.filesDir, "items"), vaultCrypto)
    val biometricVault = BiometricVault(context.applicationContext)
    val settings = AppSettings(context.applicationContext)
    val attemptLimiter = AttemptLimiter(PrefsAttemptStore(context.applicationContext))
    val session = VaultSession()

    val icons = com.ritvik.passvault.sites.IconLoader(context.applicationContext, settings)

    /** A short message shown at the bottom of the vault screen (for example "Saved"). In memory only. */
    val notice = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Encrypted diary of unlocks, copies, autofills, deletes and restores. */
    val activityLog = ActivityLog(File(context.filesDir, "activity_log.bin"), session, settings)

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            session.unlocked.collect { if (it) activityLog.record(ActivityLog.KIND_UNLOCK) }
        }
        // PassVault is offline-only. Remove the leftover state of the old optional sync, if this phone had it on.
        try { File(context.filesDir, "sync_state.json").delete() } catch (_: Exception) {}
    }

    /**
     * Set to true while a system file picker is open. Opening it moves the app to the background,
     * and without this the vault would auto-lock and the picker's result would be lost.
     */
    @Volatile
    var autoLockSuspended = false

    /** Increases each time the app comes to the front, so screens can re-check things the user changed in Android's settings. */
    val appForeground = kotlinx.coroutines.flow.MutableStateFlow(0)

    /**
     * An encrypted share code that arrived through a passvault://share link. It waits here (in memory only) until the
     * vault is unlocked, then the vault screen offers to open it. It is encrypted, so holding it is harmless.
     */
    val pendingShare = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /**
     * A card or login waiting for the user to unlock PassVault so it can be saved. In memory only, and it expires
     * after a few minutes (see [com.ritvik.passvault.autofill.PendingSave]).
     */
    @Volatile
    var pendingSave: com.ritvik.passvault.autofill.PendingSave? = null
}
