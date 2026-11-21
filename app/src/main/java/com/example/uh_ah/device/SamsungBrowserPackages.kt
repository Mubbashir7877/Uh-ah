package com.example.uh_ah.device

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserManager

internal object SamsungBrowserPackages {
    fun uniquelyMatchesLabel(context: Context, title: String): Boolean {
        if (context.getSystemService(UserManager::class.java)?.userProfiles?.size != 1) return false
        val pm = context.packageManager
        val expected = SamsungBrowserRules.normalize(title)
        val apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        val match = apps.filter { SamsungBrowserRules.normalize(it.loadLabel(pm).toString()) == expected }
            .singleOrNull()
        return match?.packageName in SamsungBrowserRules.browsers
    }

    fun summary(context: Context): String {
        val pm = context.packageManager
        return SamsungBrowserRules.browsers.joinToString(" · ") { packageName ->
            val name = if (packageName == SamsungBrowserRules.STABLE) "Samsung Browser" else "Beta"
            val state = try {
                val app = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                when {
                    !app.enabled -> "disabled in Android"
                    app.flags and ApplicationInfo.FLAG_SUSPENDED != 0 -> "suspended by Android"
                    else -> "enabled in Android"
                }
            } catch (_: PackageManager.NameNotFoundException) {
                "unavailable to Uh-Ah"
            } catch (_: SecurityException) {
                "status unavailable"
            }
            "$name: $state"
        }
    }
}
