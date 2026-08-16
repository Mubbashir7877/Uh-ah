package com.example.uh_ah.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.uh_ah.data.AppRepository

/**
 * Restarts [UninstallWatcherService] after a reboot, if protection was on
 * when the device went down. Without this the watcher only comes back once
 * the user manually opens the app — [UhAhApplication] starts/stops it
 * reactively while the process is alive, but a fresh boot doesn't create
 * that process on its own.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        if (AppRepository.getInstance(appContext).masterEnabled.value) {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, UninstallWatcherService::class.java)
            )
        }
    }
}
