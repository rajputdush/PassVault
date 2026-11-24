package com.ritvik.passvault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.ritvik.passvault.ui.LocalIconLoader
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ritvik.passvault.onboarding.CreatePasswordScreen
import com.ritvik.passvault.onboarding.OnboardingStep
import com.ritvik.passvault.onboarding.OnboardingViewModel
import com.ritvik.passvault.onboarding.RecoveryKeyScreen
import com.ritvik.passvault.onboarding.TutorialScreen
import com.ritvik.passvault.share.ShareCodec
import com.ritvik.passvault.ui.PassVaultTheme
import com.ritvik.passvault.ui.isAppDark
import com.ritvik.passvault.unlock.UnlockHost
import com.ritvik.passvault.vault.VaultHost
import java.io.IOException

// FragmentActivity (a ComponentActivity subclass) is required by BiometricPrompt.
class MainActivity : FragmentActivity() {
    private lateinit var container: AppContainer
    private val handler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable { container.session.lock() }
    private var stoppedAt = 0L
    private var lockDelayMs = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            container.session.lock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as PassVaultApp).container

        // Screenshots are blocked by default. When the user switches them on in Settings, screenshots, screen
        // recording and the recents-screen thumbnail are blocked, and the change applies at once.
        window.applyScreenshotPolicy(container.settings.allowScreenshots.value)
        lifecycleScope.launch {
            container.settings.allowScreenshots.collect { window.applyScreenshotPolicy(it) }
        }
        enableEdgeToEdge()

        // Lock immediately when the screen turns off.
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        if (savedInstanceState == null) {
            handleLockAction(intent)
            handleShareLink(intent)
        }

        setContent {
            val themeMode by container.settings.themeMode.collectAsState()
            // The status and navigation bar icons must match the app's own light or dark look, which can differ from the phone's.
            val dark = isAppDark(themeMode)
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
                onDispose { }
            }
            PassVaultTheme(themeMode) {
                CompositionLocalProvider(LocalIconLoader provides container.icons) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(modifier = Modifier.safeDrawingPadding()) {
                            AppRoot(container, this@MainActivity)
                        }
                    }
                }
            }
        }
    }

    // singleTask: a tapped passvault://share link arrives here when the app is already open.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareLink(intent)
    }

    /** The launcher shortcut "Lock now" (long-press the app icon) locks the vault and shows the unlock screen. */
    private fun handleLockAction(intent: Intent?) {
        if (intent?.action == ACTION_LOCK_NOW) container.session.lock()
    }

    /**
     * Holds the code from a passvault://share/... link until the vault is unlocked. The link can't do anything by
     * itself: the vault screen asks for the passphrase and for confirmation before anything is saved.
     */
    private fun handleShareLink(intent: Intent?) {
        val data = intent?.data ?: return
        val isAppLink = data.scheme == "passvault" && data.host == "share"
        val isWebLink = data.scheme == "https" && BuildConfig.SHARE_HOST.isNotEmpty() && data.host == BuildConfig.SHARE_HOST
        if (intent.action != Intent.ACTION_VIEW || !(isAppLink || isWebLink)) return
        val text = data.toString()
        if (text.length > 130_000) return
        ShareCodec.extractCode(text)?.let { container.pendingShare.value = it }
    }

    override fun onStop() {
        super.onStop()
        // A file picker opened by the app itself (backup export) must not lock the vault.
        if (isChangingConfigurations || container.autoLockSuspended) return

        val delayMs = container.settings.autoLockSeconds * 1000L
        if (delayMs <= 0L) {
            container.session.lock()
            return
        }
        lockDelayMs = delayMs
        stoppedAt = SystemClock.elapsedRealtime()
        handler.postDelayed(lockRunnable, delayMs)
    }

    override fun onStart() {
        super.onStart()
        container.appForeground.value += 1
        handler.removeCallbacks(lockRunnable)
        // Backup in case the timer could not fire while the process was frozen in the background.
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt >= lockDelayMs) {
            container.session.lock()
        }
        stoppedAt = 0L
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        handler.removeCallbacks(lockRunnable)
        super.onDestroy()
    }
}

@Composable
private fun AppRoot(container: AppContainer, activity: FragmentActivity) {
    var vaultExists by remember { mutableStateOf(container.headerStore.exists()) }
    // True only right after this session created or restored a vault, so people who already use PassVault never see it.
    var showTour by rememberSaveable { mutableStateOf(false) }
    val unlocked by container.session.unlocked.collectAsState()

    when {
        !vaultExists -> OnboardingFlow(container, onFinished = {
            vaultExists = true
            showTour = !container.settings.tourSeen
        })
        unlocked && showTour -> TutorialScreen(
            onFinish = {
                container.settings.tourSeen = true
                showTour = false
            },
        )
        unlocked -> VaultHost(container, activity)
        else -> UnlockHost(container, activity)
    }
}

@Composable
private fun OnboardingFlow(container: AppContainer, onFinished: () -> Unit) {
    val viewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory { initializer { OnboardingViewModel(container) } },
    )
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    when (state.step) {
        OnboardingStep.CREATE_PASSWORD -> CreatePasswordScreen(
            state = state,
            onPasswordChanged = viewModel::onPasswordChanged,
            onCreate = viewModel::createVault,
            onRestore = { uri ->
                viewModel.restoreBackup {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
                    input.use { it.readBytes() }
                }
            },
        )
        OnboardingStep.SHOW_RECOVERY_KEY -> RecoveryKeyScreen(
            state = state,
            onSaved = viewModel::onRecoveryKeySaved,
        )
        OnboardingStep.DONE -> LaunchedEffect(Unit) { onFinished() }
    }
}

/** Sent by the "Lock now" launcher shortcut. */
const val ACTION_LOCK_NOW = "com.ritvik.passvault.action.LOCK_NOW"
