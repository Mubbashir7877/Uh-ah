package com.example.uh_ah.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class DuplicateProtectedAppException(val appName: String) : Exception("$appName is already protected")

/** Persistent source of truth for the keep-installed engine and its list. */
class InstallProtectionRepository private constructor(
    private val dao: ProtectedAppDao,
    private val prefs: PreferencesManager
) {
    val apps: Flow<List<ProtectedAppEntity>> = dao.observeAll()
    val masterEnabled: StateFlow<Boolean> = prefs.installProtectionEnabled

    fun setMasterEnabled(enabled: Boolean) {
        prefs.installProtectionEnabledValue = enabled
    }

    suspend fun addApp(packageName: String, appName: String) {
        if (dao.exists(packageName)) throw DuplicateProtectedAppException(appName)
        dao.insert(ProtectedAppEntity(packageName, appName, isEnabled = true))
    }

    suspend fun deleteApp(app: ProtectedAppEntity) = dao.delete(app)

    suspend fun setAppEnabled(packageName: String, enabled: Boolean) =
        dao.setEnabled(packageName, enabled)

    companion object {
        @Volatile private var instance: InstallProtectionRepository? = null

        fun getInstance(context: Context): InstallProtectionRepository =
            instance ?: synchronized(this) {
                instance ?: InstallProtectionRepository(
                    AppDatabase.getInstance(context).protectedAppDao(),
                    PreferencesManager(context)
                ).also { instance = it }
            }
    }
}
