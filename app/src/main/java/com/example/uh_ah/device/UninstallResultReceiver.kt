package com.example.uh_ah.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * Status callback for the silent (Device Owner) uninstall path in
 * [UninstallEngine]. Only ever invoked by our own PendingIntent, so it's
 * purely for logging — there's no UI waiting on this result.
 */
class UninstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UninstallEngine.ACTION_UNINSTALL_RESULT) return
        val packageName = intent.getStringExtra(UninstallEngine.EXTRA_PACKAGE_NAME) ?: "unknown"
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Log.i("UninstallResult", "Silently uninstalled $packageName")
        } else {
            Log.w("UninstallResult", "Silent uninstall of $packageName failed (status=$status): $message")
        }
    }
}
