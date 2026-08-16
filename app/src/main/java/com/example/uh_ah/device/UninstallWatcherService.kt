package com.example.uh_ah.device

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.uh_ah.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that re-checks the installed-app list every
 * [POLL_INTERVAL_MS] and uninstalls anything matching the enabled delete list
 * — see [UninstallEngine.scanInstalledApps]. This is a backstop, not the
 * primary path: [PackageInstallReceiver] reacts to the install broadcast and
 * is usually faster. Started/stopped by [com.example.uh_ah.UhAhApplication]
 * whenever the master toggle flips, and by [BootReceiver] after a reboot.
 *
 * Runs as a foreground service (with a minimal-priority, silent notification)
 * because a plain background coroutine would get killed by the OS well before
 * a useful polling cadence like 2.5s could do anything.
 */
class UninstallWatcherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        scope.launch {
            while (isActive) {
                // One bad scan (e.g. a transient PackageManager hiccup) must not kill the loop —
                // without this, an uncaught exception here ends polling for the rest of the process's
                // life with no visible sign anything stopped.
                try {
                    UninstallEngine.scanInstalledApps(applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Scan pass failed", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Protection active",
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Watching for apps on your delete list")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "UninstallWatcher"
        private const val CHANNEL_ID = "uninstall_watcher"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 2_500L
    }
}
