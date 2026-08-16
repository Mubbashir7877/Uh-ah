package com.example.uh_ah

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.uh_ah.data.AppRepository
import com.example.uh_ah.device.UninstallWatcherService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wires up Coil 3's singleton [ImageLoader] with an OkHttp network fetcher so
 * [coil3.compose.AsyncImage]/[coil3.compose.SubcomposeAsyncImage] can load
 * Play Store icon URLs — Coil 3 splits networking into a separate artifact
 * and needs it registered explicitly, it isn't picked up automatically.
 *
 * Also owns [UninstallWatcherService]'s lifecycle: the service should run
 * exactly when the master toggle is on, for as long as the process is alive,
 * so it's driven off [AppRepository.masterEnabled] here rather than from the
 * UI layer (which only exists while an Activity is on screen).
 */
class UhAhApplication : Application(), SingletonImageLoader.Factory {

    // Without this, an uncaught exception here (e.g. a ForegroundServiceStartNotAllowedException
    // if the OS ever refuses the start) propagates to the thread's default handler and kills the
    // whole process — silently, with no crash UI, permanently ending the watcher until the app is
    // reopened. Log and keep the master-toggle collector alive instead.
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Uncaught exception in app-wide scope", e)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    override fun onCreate() {
        super.onCreate()
        val repo = AppRepository.getInstance(this)
        appScope.launch {
            repo.masterEnabled.collect { enabled ->
                val watcherIntent = Intent(this@UhAhApplication, UninstallWatcherService::class.java)
                try {
                    if (enabled) {
                        ContextCompat.startForegroundService(this@UhAhApplication, watcherIntent)
                    } else {
                        stopService(watcherIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't ${if (enabled) "start" else "stop"} UninstallWatcherService", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "UhAhApplication"
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
}
