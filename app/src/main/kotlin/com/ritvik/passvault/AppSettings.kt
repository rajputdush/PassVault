package com.ritvik.passvault

import android.content.Context
import com.ritvik.passvault.crypto.AttemptStore
import com.ritvik.passvault.ui.AppLanguage
import com.ritvik.passvault.ui.Strings
import com.ritvik.passvault.ui.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small user preferences. None of these are secrets. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** How long the app may stay in the background before locking. 0 = lock as soon as you leave. */
    var autoLockSeconds: Int
        get() = prefs.getInt(KEY_AUTO_LOCK, 30)
        set(value) {
            prefs.edit().putInt(KEY_AUTO_LOCK, value).apply()
        }

    /** How long copied passwords stay on the clipboard. 0 = never clear. */
    var clipboardClearSeconds: Int
        get() = prefs.getInt(KEY_CLIPBOARD, 30)
        set(value) {
            prefs.edit().putInt(KEY_CLIPBOARD, value).apply()
        }

    /** Ask for the fingerprint before opening a login or card or copying its password, even while unlocked. */
    var confirmBeforeReveal: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_REVEAL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CONFIRM_REVEAL, value).apply()
        }

    /** Show real website icons (needs the internet and tells an icon service which domains you use). Off by default. */
    var showSiteIcons: Boolean
        get() = prefs.getBoolean(KEY_SITE_ICONS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SITE_ICONS, value).apply()
        }

    private val _themeMode = MutableStateFlow(ThemeMode.fromKey(prefs.getString(KEY_THEME, null)))

    /** Light, dark, or follow the phone. A flow so every screen changes the moment the user picks one. */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.key).apply()
        _themeMode.value = mode
    }

    private val _language = MutableStateFlow(AppLanguage.fromKey(prefs.getString(KEY_LANGUAGE, null)))

    /** The app's language: English, Hindi, or follow the phone. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    init {
        Strings.applyLanguage(_language.value)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.key).apply()
        _language.value = language
        Strings.applyLanguage(language)
    }

    /** True once the user answered the "show real logos?" question in the editor, so it is not asked again. */
    var iconPromptDismissed: Boolean
        get() = prefs.getBoolean(KEY_ICON_PROMPT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ICON_PROMPT, value).apply()
        }

    private val _groupSites = MutableStateFlow(prefs.getBoolean(KEY_GROUP_SITES, true))

    /** Whether logins from the same site or app are shown together as one entry. A flow so the list updates at once. */
    val groupSites: StateFlow<Boolean> = _groupSites.asStateFlow()

    fun setGroupSites(on: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP_SITES, on).apply()
        _groupSites.value = on
    }

    /** Whether the first-run tour has been shown (or skipped). */
    var tourSeen: Boolean
        get() = prefs.getBoolean(KEY_TOUR_SEEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TOUR_SEEN, value).apply()
        }

    private val _allowScreenshots = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_SCREENSHOTS, false))

    /** Whether screenshots and screen recording are allowed inside PassVault. Off by default (screens are protected); the user can switch it on. */
    val allowScreenshots: StateFlow<Boolean> = _allowScreenshots.asStateFlow()

    fun setAllowScreenshots(on: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_SCREENSHOTS, on).apply()
        _allowScreenshots.value = on
    }

    /** Whether the encrypted activity log records events. On by default. */
    var activityLogEnabled: Boolean
        get() = prefs.getBoolean(KEY_ACTIVITY_LOG, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ACTIVITY_LOG, value).apply()
        }

    private companion object {
        const val KEY_ALLOW_SCREENSHOTS = "allow_screenshots"
        const val KEY_ACTIVITY_LOG = "activity_log_enabled"
        const val KEY_THEME = "theme_mode"
        const val KEY_LANGUAGE = "language"
        const val KEY_CONFIRM_REVEAL = "confirm_before_reveal"
        const val KEY_TOUR_SEEN = "tour_seen"
        const val KEY_GROUP_SITES = "group_sites"
        const val KEY_ICON_PROMPT = "icon_prompt_dismissed"
        const val KEY_SITE_ICONS = "show_site_icons"
        const val KEY_AUTO_LOCK = "auto_lock_seconds"
        const val KEY_CLIPBOARD = "clipboard_clear_seconds"
    }
}

/** Keeps the wrong-password counter across app restarts. Written with commit() so a crash cannot lose a failure. */
class PrefsAttemptStore(context: Context) : AttemptStore {
    private val prefs = context.getSharedPreferences("attempts", Context.MODE_PRIVATE)

    override var failures: Int
        get() = prefs.getInt("failures", 0)
        set(value) {
            prefs.edit().putInt("failures", value).commit()
        }

    override var lockedUntilMs: Long
        get() = prefs.getLong("locked_until_ms", 0L)
        set(value) {
            prefs.edit().putLong("locked_until_ms", value).commit()
        }

    override var lockDelayMs: Long
        get() = prefs.getLong("lock_delay_ms", 0L)
        set(value) {
            prefs.edit().putLong("lock_delay_ms", value).commit()
        }

    override var lockStartElapsedMs: Long
        get() = prefs.getLong("lock_start_elapsed_ms", 0L)
        set(value) {
            prefs.edit().putLong("lock_start_elapsed_ms", value).commit()
        }

    override var lockBootId: Int
        get() = prefs.getInt("lock_boot_id", 0)
        set(value) {
            prefs.edit().putInt("lock_boot_id", value).commit()
        }
}
