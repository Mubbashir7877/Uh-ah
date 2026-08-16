package com.example.uh_ah.device

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.uh_ah.data.AppRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides whether a just-installed package should be removed, and how.
 *
 *  - Device Owner (see [DeviceOwnerHelper]): silent uninstall via
 *    [android.content.pm.PackageInstaller], no user interaction.
 *  - Otherwise: launch the system's uninstall confirmation screen and mark the
 *    package as [pendingAutoConfirm] so [UninstallAccessibilityService] can tap
 *    through it the instant the dialog appears — including while Uh-Ah itself
 *    is in the background, since the accessibility service is bound by the OS
 *    independently of our process/activity lifecycle.
 *
 * Two entry points feed the same [uninstall]: [onPackageInstalled] (the
 * PACKAGE_ADDED broadcast, fast but occasionally missed — see the
 * package-visibility note on the QUERY_ALL_PACKAGES permission) and
 * [scanInstalledApps] ([UninstallWatcherService]'s periodic sweep, a backstop
 * that doesn't depend on the broadcast arriving at all).
 */
object UninstallEngine {
    private const val TAG = "UninstallEngine"
    internal const val ACTION_UNINSTALL_RESULT = "com.example.uh_ah.ACTION_UNINSTALL_RESULT"
    internal const val EXTRA_PACKAGE_NAME = "extra_package_name"

    /**
     * Packages we've just launched the system uninstall confirmation dialog for,
     * keyed to the time we launched it. [UninstallAccessibilityService] consults
     * this to decide whether an uninstall-confirmation window it's looking at is
     * one of ours to auto-tap, rather than blindly confirming every uninstall
     * dialog on the device (including ones the user triggered manually
     * themselves). Entries older than [PENDING_CONFIRM_TTL_MS] are ignored —
     * covers the case where the dialog never actually appeared (e.g. the
     * accessibility service was off, or the activity start was blocked).
     */
    internal val pendingAutoConfirm = ConcurrentHashMap<String, Long>()
    private const val PENDING_CONFIRM_TTL_MS = 30_000L

    /** True if [packageName] currently has a fresh, unexpired pending confirmation. */
    internal fun hasPendingAutoConfirm(packageName: String): Boolean {
        val markedAt = pendingAutoConfirm[packageName] ?: return false
        return SystemClock.elapsedRealtime() - markedAt < PENDING_CONFIRM_TTL_MS
    }

    internal fun clearPendingAutoConfirm(packageName: String) {
        pendingAutoConfirm.remove(packageName)
    }

    /**
     * Both entry points can land on the same package within one poll interval
     * (broadcast fires, then the sweep runs before the uninstall completes).
     * Without this, the dialog path in particular would relaunch the system
     * confirmation screen on top of itself every 2.5s until the user answers.
     */
    private val recentAttempts = ConcurrentHashMap<String, Long>()
    private const val ATTEMPT_COOLDOWN_MS = 10_000L

    suspend fun onPackageInstalled(context: Context, packageName: String) {
        val repo = AppRepository.getInstance(context)
        if (!repo.masterEnabled.value) {
            Log.d(TAG, "Master toggle is off, ignoring install of $packageName")
            return
        }
        val entry = repo.findByPackageName(packageName)
        if (entry == null) {
            Log.d(TAG, "$packageName is not on the delete list")
            return
        }
        if (!entry.isEnabled) {
            Log.d(TAG, "${entry.appName} is on the list but individually toggled off")
            return
        }
        uninstall(context, packageName)
    }

    /**
     * Periodic backstop: cross-references every currently-installed package
     * against the enabled delete list, rather than waiting on a broadcast.
     * Catches installs the PACKAGE_ADDED path missed for any reason, and
     * self-heals if the list changes while an app is already present.
     */
    suspend fun scanInstalledApps(context: Context) {
        val repo = AppRepository.getInstance(context)
        if (!repo.masterEnabled.value) return
        val targets = repo.getEnabledPackageNames()
        if (targets.isEmpty()) return

        val installed = try {
            context.packageManager.getInstalledPackages(0).mapTo(HashSet()) { it.packageName }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't list installed packages", e)
            return
        }

        for (packageName in targets) {
            if (packageName in installed) {
                uninstall(context, packageName)
            }
        }
    }

    private fun uninstall(context: Context, packageName: String) {
        val now = SystemClock.elapsedRealtime()
        val lastAttempt = recentAttempts[packageName]
        if (lastAttempt != null && now - lastAttempt < ATTEMPT_COOLDOWN_MS) return
        recentAttempts[packageName] = now

        if (DeviceOwnerHelper.isDeviceOwner(context)) {
            Log.i(TAG, "Uninstalling $packageName silently (device owner)")
            uninstallSilently(context, packageName)
        } else {
            Log.i(TAG, "Not device owner; launching uninstall confirmation dialog for $packageName")
            triggerUninstallDialog(context, packageName)
        }
    }

    private fun uninstallSilently(context: Context, packageName: String) {
        try {
            val installer = context.packageManager.packageInstaller
            val resultIntent = Intent(context, UninstallResultReceiver::class.java).apply {
                action = ACTION_UNINSTALL_RESULT
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                packageName.hashCode(),
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            installer.uninstall(packageName, pendingIntent.intentSender)
        } catch (e: Exception) {
            Log.w(TAG, "Silent uninstall of $packageName failed, falling back to dialog", e)
            triggerUninstallDialog(context, packageName)
        }
    }

    private fun triggerUninstallDialog(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            pendingAutoConfirm[packageName] = SystemClock.elapsedRealtime()
            context.startActivity(intent)
        } catch (e: Exception) {
            pendingAutoConfirm.remove(packageName)
            Log.e(TAG, "Couldn't launch uninstall dialog for $packageName", e)
        }
    }
}
