package com.example.uh_ah.device

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * Accessibility services can't be enabled by the app itself (no equivalent of
 * Device Admin's ACTION_ADD_DEVICE_ADMIN) — the user has to flip it on from
 * system Settings. This just detects whether they have, and opens that screen.
 */
object AccessibilityHelper {

    fun isServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${UninstallAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
            .any { it.equals(expected, ignoreCase = true) }
    }

    /** Opens system Accessibility settings; Uh-Ah's own entry still needs a manual tap to enable — see class doc. */
    fun openAccessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
