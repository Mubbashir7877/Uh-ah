package com.example.uh_ah.device

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserManager
import android.view.accessibility.AccessibilityNodeInfo

internal object SafeLookVpnDialog {
    const val SETTINGS_PACKAGE = "com.android.settings"
    private const val SAFELOOK_PACKAGE = "com.pro.SafeLook"
    private const val SAFELOOK_SERVICE = "$SAFELOOK_PACKAGE.BlockVpnService"
    private const val SAFELOOK_LABEL = "SafeLook"

    fun expected(context: Context, title: String): VpnDisconnectDialog? = try {
        resolve(context, title)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun resolve(context: Context, title: String): VpnDisconnectDialog? {
        // Settings can show work-profile VPNs whose package metadata we cannot inspect.
        if (context.getSystemService(UserManager::class.java)?.userProfiles?.size != 1) return null

        val pm = context.packageManager
        val settings = pm.getApplicationInfo(SETTINGS_PACKAGE, PackageManager.ApplicationInfoFlags.of(0))
        if (settings.flags and ApplicationInfo.FLAG_SYSTEM == 0) return null
        val packages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(
            PackageManager.GET_SERVICES.toLong()
        ))
        val safeLook = packages.singleOrNull { it.packageName == SAFELOOK_PACKAGE } ?: return null
        val service = safeLook.services?.singleOrNull {
            it.name == SAFELOOK_SERVICE && it.enabled &&
                it.permission == Manifest.permission.BIND_VPN_SERVICE
        } ?: return null
        if (safeLook.applicationInfo?.enabled != true || service.loadLabel(pm).toString() != title) return null

        // AOSP displays a label, not the VPN package ID. Reject any competing VPN
        // app or service that could supply the same label.
        val ambiguous = packages.any { pkg ->
            pkg.packageName != SAFELOOK_PACKAGE &&
                pkg.services.orEmpty().any {
                    it.permission == Manifest.permission.BIND_VPN_SERVICE &&
                        (it.loadLabel(pm).toString() == title ||
                            pkg.applicationInfo?.loadLabel(pm)?.toString() == title)
                }
        }
        if (ambiguous) return null

        val resources = pm.getResourcesForApplication(settings)
        fun label(name: String): String? {
            val id = resources.getIdentifier(name, "string", SETTINGS_PACKAGE)
            return if (id == 0) null else resources.getString(id)
        }
        return VpnDisconnectDialog(
            title = title,
            message = label("vpn_disconnect_confirm") ?: return null,
            disconnect = label("vpn_disconnect") ?: return null,
            cancel = label("vpn_cancel") ?: return null
        )
    }

    fun read(root: AccessibilityNodeInfo, windowId: Int): Match? {
        if (root.packageName?.toString() != SETTINGS_PACKAGE || root.windowId != windowId) return null

        fun node(id: String) = root.findAccessibilityNodeInfosByViewId(id)
            .singleOrNull()?.takeIf {
                it.isVisibleToUser && it.windowId == windowId && it.packageName?.toString() == SETTINGS_PACKAGE
            }

        val title = node("$SETTINGS_PACKAGE:id/alertTitle")?.text?.toString() ?: return null
        if (title != SAFELOOK_LABEL) return null
        val message = node("android:id/message")?.text?.toString() ?: return null
        val disconnect = node("android:id/button1")?.text?.toString() ?: return null
        val cancel = node("android:id/button2") ?: return null
        val cancelLabel = cancel.text?.toString() ?: return null
        return Match(
            VpnDisconnectDialog(title, message, disconnect, cancelLabel),
            cancel.takeIf {
                it.isEnabled && it.isClickable &&
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK in it.actionList
            }
        )
    }

    data class Match(val dialog: VpnDisconnectDialog, val cancel: AccessibilityNodeInfo?)
}
