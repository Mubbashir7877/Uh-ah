package com.example.uh_ah.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for newly installed packages system-wide. Registered in the manifest
 * (not dynamically) so it fires even while the app's UI isn't running.
 *
 * The actual decision logic (does this match the delete list? are the toggles
 * on?) lives in [UninstallEngine] — this class is just the OS entry point,
 * kept thin and using `goAsync()` since matching against Room is a suspend call.
 */
class PackageInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        // A version update of an already-installed app is "replacing", not a
        // fresh install — the user didn't just re-add something off the list.
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val packageName = intent.data?.schemeSpecificPart ?: return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                UninstallEngine.onPackageInstalled(appContext, packageName)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
