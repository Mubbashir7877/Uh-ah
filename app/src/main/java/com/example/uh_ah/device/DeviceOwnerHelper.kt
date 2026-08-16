package com.example.uh_ah.device

import android.app.admin.DevicePolicyManager
import android.content.Context

/**
 * Device Owner is what actually makes uninstall silent (no system confirmation
 * dialog): the OS treats device/profile owner apps as trusted for package
 * management, so `PackageInstaller.uninstall()` completes without prompting.
 *
 * There's no in-app way to become Device Owner — it can only be granted via
 * `adb shell dpm set-device-owner ...` on a device/profile with no other
 * accounts or admin apps already configured (typically a freshly factory-reset
 * phone or a fresh emulator). This is an optional, advanced setup path; without
 * it, Uh-Ah still works via the regular Device Admin + system uninstall dialog.
 */
object DeviceOwnerHelper {

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /** The exact command the user needs to run from a computer with adb + USB debugging enabled. */
    fun adbSetDeviceOwnerCommand(context: Context): String {
        val admin = DeviceAdminHelper.adminComponent(context)
        return "adb shell dpm set-device-owner ${admin.flattenToShortString()}"
    }
}
