package com.example.uh_ah.device

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Admin lifecycle hook. We don't enforce any policy through this
 * receiver directly (see device_admin.xml) — its purpose is to make Uh-Ah
 * eligible to be Device Admin / Device Owner at all, which is a prerequisite
 * for the silent-uninstall path in [com.example.uh_ah.device.UninstallEngine].
 */
class UhAhDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Uh-Ah device admin enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(
            context,
            "Uh-Ah device admin disabled — auto-uninstall needs it re-enabled",
            Toast.LENGTH_LONG
        ).show()
    }
}
