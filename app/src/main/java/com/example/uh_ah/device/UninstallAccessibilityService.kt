package com.example.uh_ah.device

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uh_ah.data.InstallProtectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var protectedApps: List<ProtectedTarget> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val repo = InstallProtectionRepository.getInstance(this)
        serviceScope.launch {
            combine(repo.apps, repo.masterEnabled) { apps, masterEnabled ->
                if (!masterEnabled) emptyList() else apps
                    .filter { it.isEnabled }
                    .map { ProtectedTarget(it.packageName, it.appName) }
            }.collect { protectedApps = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (UninstallEngine.pendingAutoConfirm.isEmpty() && protectedApps.isEmpty()) return

        val root = rootInActiveWindow ?: return
        try {
            // Stock Android uses one of the known installer packages. OEM builds may
            // host the dialog elsewhere, but an app-info page also has an Uninstall
            // action. For unknown hosts require both Uninstall and Cancel controls,
            // which identifies the confirmation dialog without closing app details.
            if (packageName !in PACKAGE_INSTALLER_PACKAGES &&
                (findConfirmButton(root) == null || findCancelButton(root) == null)) return

            val protectedTarget = findProtectedTarget(root)
            if (protectedTarget != null) {
                dismissProtectedUninstall(root, protectedTarget)
                return
            }

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

    /** Cancels an uninstall or device-admin deactivation for an enabled protected app. */
    private fun dismissProtectedUninstall(root: AccessibilityNodeInfo, target: ProtectedTarget) {
        val cancelButton = findCancelButton(root)
        val dismissed = cancelButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true ||
            performGlobalAction(GLOBAL_ACTION_BACK)
        Log.i(TAG, "Blocked uninstall of ${target.packageName}: $dismissed")
    }

    private fun findProtectedTarget(root: AccessibilityNodeInfo): ProtectedTarget? {
        val visibleStrings = mutableListOf<String>()
        collectVisibleStrings(root, visibleStrings)
        return protectedApps.firstOrNull { target ->
            UninstallDialogMatcher.matches(target.packageName, target.appName, visibleStrings)
        }
    }

    private fun collectVisibleStrings(node: AccessibilityNodeInfo, output: MutableList<String>) {
        node.text?.toString()?.let(output::add)
        node.contentDescription?.toString()?.let(output::add)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectVisibleStrings(it, output) }
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

    private fun findCancelButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("android:id/button2").firstOrNull()?.let { return it }
        return findNodeByText(root, NEGATIVE_BUTTON_LABELS)
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

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class ProtectedTarget(val packageName: String, val appName: String)

    private companion object {
        const val TAG = "UninstallA11yService"
        val PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )
        val POSITIVE_BUTTON_LABELS = setOf("ok", "uninstall", "deactivate")
        val NEGATIVE_BUTTON_LABELS = setOf("cancel", "no", "keep app")
    }
}
