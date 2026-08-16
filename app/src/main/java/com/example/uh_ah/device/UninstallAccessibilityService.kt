package com.example.uh_ah.device

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Auto-confirms the system uninstall dialog [UninstallEngine.triggerUninstallDialog]
 * launches, so a non-Device-Owner install of Uh-Ah still gets a hands-off removal
 * instead of leaving a tap for the user. Bound and driven entirely by the OS
 * accessibility framework once the user enables it in Settings — that's what
 * makes this work even while Uh-Ah's own process/activity is in the background
 * or not running at all, unlike anything wired through an Activity/Service we own.
 *
 * Only taps through dialogs for packages [UninstallEngine] actually asked to be
 * removed (see [UninstallEngine.hasPendingAutoConfirm]) — never blindly confirms
 * every uninstall dialog on the device, so a manual uninstall the user starts
 * themselves for an unrelated app is left alone.
 */
class UninstallAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in PACKAGE_INSTALLER_PACKAGES) return
        if (UninstallEngine.pendingAutoConfirm.isEmpty()) return

        val root = rootInActiveWindow ?: return
        try {
            val button = findConfirmButton(root) ?: return
            val target = UninstallEngine.pendingAutoConfirm.keys.firstOrNull { UninstallEngine.hasPendingAutoConfirm(it) }
            if (target == null) return
            val clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "Auto-confirm tap on uninstall dialog for $target: $clicked")
            if (clicked) UninstallEngine.clearPendingAutoConfirm(target)
        } finally {
            root.recycle()
        }
    }

    /**
     * The confirmation dialog's positive button is `android:id/button1` on every
     * stock/AOSP-derived build we've seen; text/content-description is the
     * fallback for OEM skins that restyle the dialog without keeping that id.
     */
    private fun findConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()?.let { return it }
        return findNodeByText(root, POSITIVE_BUTTON_LABELS)
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, labels: Set<String>): AccessibilityNodeInfo? {
        val text = (node.text ?: node.contentDescription)?.toString()?.trim()?.lowercase()
        if (node.isClickable && text != null && text in labels) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findNodeByText(child, labels)
            if (match != null) return match
        }
        return null
    }

    override fun onInterrupt() {}

    private companion object {
        const val TAG = "UninstallA11yService"
        val PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )
        val POSITIVE_BUTTON_LABELS = setOf("ok", "uninstall")
    }
}
