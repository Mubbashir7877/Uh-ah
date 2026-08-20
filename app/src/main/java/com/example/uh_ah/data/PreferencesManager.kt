package com.example.uh_ah.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Small SharedPreferences wrapper for the handful of app-wide flags that don't
 * belong in a Room table (the master toggle, and whether we've already shown
 * the one-time Device Admin setup prompt). Backed by a StateFlow so the UI can
 * observe changes the same way it observes the Room-backed app list.
 */
class PreferencesManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _masterEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MASTER_ENABLED, DEFAULT_MASTER_ENABLED)
    )
    val masterEnabled: StateFlow<Boolean> = _masterEnabled

    private val _installProtectionEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_INSTALL_PROTECTION_ENABLED, DEFAULT_MASTER_ENABLED)
    )
    val installProtectionEnabled: StateFlow<Boolean> = _installProtectionEnabled

    var masterEnabledValue: Boolean
        get() = _masterEnabled.value
        set(value) {
            prefs.edit().putBoolean(KEY_MASTER_ENABLED, value).apply()
            _masterEnabled.value = value
        }

    var installProtectionEnabledValue: Boolean
        get() = _installProtectionEnabled.value
        set(value) {
            prefs.edit().putBoolean(KEY_INSTALL_PROTECTION_ENABLED, value).apply()
            _installProtectionEnabled.value = value
        }

    /** Whether the first-launch Device Admin explanation has already been shown/dismissed. */
    var hasSeenDeviceAdminPrompt: Boolean
        get() = prefs.getBoolean(KEY_SEEN_DEVICE_ADMIN_PROMPT, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN_DEVICE_ADMIN_PROMPT, value).apply()

    companion object {
        private const val PREFS_NAME = "uh_ah_prefs"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_INSTALL_PROTECTION_ENABLED = "install_protection_enabled"
        private const val KEY_SEEN_DEVICE_ADMIN_PROMPT = "seen_device_admin_prompt"
        private const val DEFAULT_MASTER_ENABLED = true
    }
}
