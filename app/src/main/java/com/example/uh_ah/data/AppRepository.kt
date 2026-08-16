package com.example.uh_ah.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Thrown by [AppRepository.addApp] when the package is already on the delete list. */
class DuplicateAppException(val appName: String) : Exception("$appName is already on the list")

/**
 * Single access point for the delete list (Room) and the app-wide flags
 * (SharedPreferences, via [PreferencesManager]). Everything else in the app —
 * ViewModel, the package-installed receiver, the uninstall engine — goes
 * through this instead of touching the DAO or prefs directly.
 */
class AppRepository private constructor(
    private val dao: AppDao,
    private val prefs: PreferencesManager
) {
    val apps: Flow<List<AppEntity>> = dao.observeAll()
    val masterEnabled: StateFlow<Boolean> = prefs.masterEnabled

    fun setMasterEnabled(enabled: Boolean) {
        prefs.masterEnabledValue = enabled
    }

    var hasSeenDeviceAdminPrompt: Boolean
        get() = prefs.hasSeenDeviceAdminPrompt
        set(value) { prefs.hasSeenDeviceAdminPrompt = value }

    suspend fun findByPackageName(packageName: String): AppEntity? =
        dao.findByPackageName(packageName)

    /** Snapshot of package names currently on the list and individually toggled on. */
    suspend fun getEnabledPackageNames(): Set<String> = dao.getEnabledPackageNames().toSet()

    /** Adds [packageName] to the list, enabled by default. Fails if already present. */
    suspend fun addApp(packageName: String, appName: String, iconUrl: String?) {
        if (dao.exists(packageName)) throw DuplicateAppException(appName)
        dao.insert(
            AppEntity(
                packageName = packageName,
                appName = appName,
                iconUrl = iconUrl,
                isEnabled = true
            )
        )
    }

    suspend fun deleteApp(entity: AppEntity) = dao.delete(entity)

    suspend fun setAppEnabled(packageName: String, enabled: Boolean) =
        dao.setEnabled(packageName, enabled)

    companion object {
        @Volatile
        private var instance: AppRepository? = null

        fun getInstance(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(
                    AppDatabase.getInstance(context).appDao(),
                    PreferencesManager(context)
                ).also { instance = it }
            }
    }
}
