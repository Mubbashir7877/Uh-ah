package com.example.uh_ah.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Wraps the small bit of [DevicePolicyManager] API surface Uh-Ah needs for basic Device Admin status. */
object DeviceAdminHelper {

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, UhAhDeviceAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(context))
    }

    /** Intent that opens the system "Activate device admin app?" screen for Uh-Ah. */
    fun requestActivationIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Uh-Ah uses Device Admin to detect and remove newly installed apps on your delete list."
            )
        }
}
