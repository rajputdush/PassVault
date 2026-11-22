package com.ritvik.passvault.autofill

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.ritvik.passvault.AppContainer
import com.ritvik.passvault.applyScreenshotPolicy
import com.ritvik.passvault.SaveLog
import com.ritvik.passvault.PassVaultApp
import com.ritvik.passvault.ui.LocalIconLoader
import com.ritvik.passvault.ui.PassVaultTheme
import com.ritvik.passvault.ui.Strings
import com.ritvik.passvault.ui.Text
import com.ritvik.passvault.unlock.UnlockHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android shows its "Save to PassVault?" question, and the user says yes while PassVault is locked. This screen then
 * opens on top of the other app: unlock, and the card or login that was just typed is saved. The vault is locked
 * again afterwards if this screen is what unlocked it.
 */
class AutofillSaveActivity : FragmentActivity() {
    private lateinit var container: AppContainer
    private var wasLocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as PassVaultApp).container
        window.applyScreenshotPolicy(container.settings.allowScreenshots.value)
        enableEdgeToEdge()
        wasLocked = !container.session.isUnlocked

        intent.getStringExtra(EXTRA_MESSAGE)?.let { showResult(it); return }

        val pending = container.pendingSave
        if (pending == null || pending.expired) {
            container.pendingSave = null
            SaveLog.record(applicationContext, "save screen", if (pending == null) "opened but nothing was waiting" else "opened but the waiting save had expired")
            showResult("There is nothing waiting to be saved. Please try the save again.")
            return
        }
        SaveLog.record(applicationContext, "save screen", if (wasLocked) "opened, waiting for unlock" else "opened, vault already unlocked")
        val what = "login"

        setContent {
            val themeMode by container.settings.themeMode.collectAsState()
            PassVaultTheme(themeMode) {
                CompositionLocalProvider(LocalIconLoader provides container.icons) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(modifier = Modifier.safeDrawingPadding()) {
                            val unlocked by container.session.unlocked.collectAsState()
                            var finished by remember { mutableStateOf<String?>(null) }
                            if (finished != null) {
                                ResultBody(finished!!) { finish() }
                            } else if (!unlocked) {
                                Column {
                                    Text(
                                        Strings.tr("Unlock PassVault to save this $what."),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                                    )
                                    UnlockHost(container, this@AutofillSaveActivity)
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(Strings.tr("Saving…"), style = MaterialTheme.typography.bodyLarge)
                                }
                                LaunchedEffect(Unit) {
                                    val message = withContext(Dispatchers.IO) { save(pending) }
                                    SaveLog.record(applicationContext, "login", "after unlock: $message")
                                    container.pendingSave = null
                                    finished = Strings.tr(message)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Just shows the outcome of a save that already happened (or was refused), on top of the other app. */
    private fun showResult(message: String) {
        setContent {
            val themeMode by container.settings.themeMode.collectAsState()
            PassVaultTheme(themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ResultBody(message) { finish() }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ResultBody(message: String, onOk: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Button(onClick = onOk, modifier = Modifier.padding(top = 24.dp)) { Text("OK") }
        }
    }

    private fun save(pending: PendingSave): String {
        pending.login?.let { form ->
            return when (AutofillSaver.save(container, applicationContext, form)) {
                AutofillSaver.Result.SAVED -> "Saved to PassVault"
                AutofillSaver.Result.UPDATED -> "Password updated in PassVault"
                AutofillSaver.Result.UNCHANGED -> "Already saved in PassVault"
                AutofillSaver.Result.UNKNOWN_SITE -> "PassVault couldn't tell which website this is, so nothing was saved."
                else -> "Could not save to PassVault"
            }
        }
        return "Could not save to PassVault"
    }

    companion object {
        const val EXTRA_MESSAGE = "passvault.save_result_message"
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing && container.pendingSave != null) {
            SaveLog.record(applicationContext, "save screen", "went to the background before saving")
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) container.pendingSave = null
        // If this screen is what unlocked the vault, do not leave it unlocked behind the user's back.
        if (!isChangingConfigurations && wasLocked) container.session.lock()
        super.onDestroy()
    }
}
