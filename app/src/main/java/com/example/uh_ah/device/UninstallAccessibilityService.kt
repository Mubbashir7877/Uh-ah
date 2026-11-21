package com.example.uh_ah.device

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uh_ah.data.InstallProtectionRepository
import com.example.uh_ah.data.PreferencesManager
import com.example.uh_ah.data.SetstoneControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val preferences by lazy { PreferencesManager(this) }
    private var vpnCheckInProgress = false
    private var browserIdentityCheckInProgress = false
    private val browserGuard = SamsungBrowserGuard()
    private var connected = false
    private val uberGuard = UberEatsGuard()
    private val vpnGuard = VpnDisconnectGuard {
        vpnSecurityEnabled() && AccessibilityHelper.isServiceEnabled(this)
    }

    private fun vpnSecurityEnabled() =
        connected && preferences.isEnabled(SetstoneControl.VPN_SECURITY)

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        serviceConnected.value = true
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
        val packageName = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (packageName in SamsungBrowserRules.searchable) handleSamsungBrowser(event, packageName)
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            if (packageName == UBER_RIDER_PACKAGE) handleUberNavigation(event, packageName)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        handleSamsungBrowser(event, packageName)
        handleUberNavigation(event, packageName)
        if (packageName == SafeLookVpnDialog.SETTINGS_PACKAGE && vpnSecurityEnabled()) {
            checkVpnDialog(event.windowId)
        }
        if (UninstallEngine.pendingAutoConfirm.isEmpty() && protectedApps.isEmpty()) return

        val root = rootInActiveWindow ?: return
        try {
            // Stock Android uses one of the known installer packages. OEM builds may
            // host the dialog elsewhere, but an app-info page also has an Uninstall
            // action. For unknown hosts require both Uninstall and Cancel controls,
            // which identifies the confirmation dialog without closing app details.
            val knownInstaller = packageName in PACKAGE_INSTALLER_PACKAGES
            if (!knownInstaller && (findNodeByText(root, POSITIVE_BUTTON_LABELS) == null ||
                    findNodeByText(root, NEGATIVE_BUTTON_LABELS) == null)) return

            val protectedTarget = findProtectedTarget(root)
            if (protectedTarget != null) {
                dismissProtectedUninstall(root, protectedTarget)
                return
            }

            val button = (if (knownInstaller) findConfirmButton(root)
                else findNodeByText(root, POSITIVE_BUTTON_LABELS)) ?: return
            val target = UninstallEngine.pendingAutoConfirm.keys.firstOrNull { UninstallEngine.hasPendingAutoConfirm(it) }
            if (target == null) return
            val clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "Auto-confirm tap on uninstall dialog for $target: $clicked")
            if (clicked) UninstallEngine.clearPendingAutoConfirm(target)
        } finally {
            root.recycle()
        }
    }

    private fun browserEnabled() =
        connected && preferences.isEnabled(SetstoneControl.BROWSER_CONTROL)

    private fun handleSamsungBrowser(event: AccessibilityEvent, packageName: String) {
        if (packageName !in SamsungBrowserRules.monitored &&
            (!browserGuard.hasAttempted || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        ) return
        if (!browserEnabled()) {
            browserGuard.reset()
            return
        }

        val windowId = event.windowId
        val active = browserRoot(packageName, windowId) ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            browserGuard.foregroundChanged(packageName)
        }
        if (packageName !in SamsungBrowserRules.monitored || browserGuard.hasAttempted(packageName)) return
        if (packageName in SamsungBrowserRules.browsers) {
            blockBrowserScreen(packageName, windowId)
            return
        }
        if (packageName == SamsungBrowserRules.SETTINGS && browserIdentityCheckInProgress &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val source = if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) event.source else null
        val screen = SamsungBrowserAccessibility.read(active, packageName, source)
        val title = screen.settingsTargetTitle
        if (title == null || screen.settingsTargetId) {
            blockBrowserScreen(packageName, windowId, screen)
        } else if (!browserIdentityCheckInProgress) {
            browserIdentityCheckInProgress = true
            serviceScope.launch {
                try {
                    val confirmed = withContext(Dispatchers.IO) {
                        try {
                            SamsungBrowserPackages.uniquelyMatchesLabel(this@UninstallAccessibilityService, title)
                        } catch (_: SecurityException) {
                            false
                        }
                    }
                    if (!confirmed || !browserEnabled()) return@launch
                    val current = browserRoot(packageName, windowId) ?: return@launch
                    val verified = SamsungBrowserAccessibility.read(current, packageName, null)
                    if (verified.settingsTargetTitle == title) {
                        blockBrowserScreen(packageName, windowId, verified, settingsIdentityConfirmed = true)
                    }
                } finally {
                    browserIdentityCheckInProgress = false
                }
            }
        }
    }

    private fun browserRoot(packageName: String, windowId: Int) =
        rootInActiveWindow?.takeIf { it.windowId == windowId && it.packageName?.toString() == packageName }

    private fun blockBrowserScreen(
        packageName: String,
        windowId: Int,
        screen: BrowserScreen = BrowserScreen(),
        settingsIdentityConfirmed: Boolean = false
    ) {
        browserGuard.handle(
            packageName = packageName,
            enabled = browserEnabled(),
            authorized = {
                AccessibilityHelper.isServiceEnabled(this) && browserRoot(packageName, windowId) != null
            },
            screen = screen,
            settingsIdentityConfirmed = settingsIdentityConfirmed,
            goHome = { performGlobalAction(GLOBAL_ACTION_HOME) }
        )
    }

    private fun handleUberNavigation(event: AccessibilityEvent, packageName: String) {
        val enabled = connected && preferences.isEnabled(SetstoneControl.UBER_CONTROL)
        if (!enabled) {
            uberGuard.leaveUber()
            return
        }

        if (packageName != UBER_RIDER_PACKAGE) {
            if (uberGuard.homeAttempted && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val active = rootInActiveWindow
                if (active?.windowId == event.windowId && active.packageName?.toString() == packageName) {
                    uberGuard.leaveUber()
                }
            }
            return
        }
        if (uberGuard.homeAttempted) return

        val active = rootInActiveWindow ?: return
        val foreground = active.packageName?.toString() ?: return
        if (active.windowId != event.windowId || foreground != UBER_RIDER_PACKAGE) return
        val groups = UberAccessibilityNavigation.read(active)
        if (groups.isEmpty()) return

        uberGuard.handle(
            enabled = preferences.isEnabled(SetstoneControl.UBER_CONTROL),
            authorized = AccessibilityHelper.isServiceEnabled(this),
            foregroundPackage = foreground,
            groups = groups,
            goHome = { performGlobalAction(GLOBAL_ACTION_HOME) }
        )
    }

    private fun checkVpnDialog(windowId: Int) {
        if (vpnCheckInProgress || vpnGuard.hasAttempted(windowId)) return
        val initial = rootInActiveWindow?.let { SafeLookVpnDialog.read(it, windowId) } ?: return
        if (initial.cancel == null) return

        vpnCheckInProgress = true
        serviceScope.launch {
            try {
                val expected = withContext(Dispatchers.IO) {
                    SafeLookVpnDialog.expected(this@UninstallAccessibilityService, initial.dialog.title)
                } ?: return@launch
                if (!vpnSecurityEnabled()) return@launch
                val current = rootInActiveWindow?.let { SafeLookVpnDialog.read(it, windowId) }
                vpnGuard.handle(
                    windowId = windowId,
                    expected = expected,
                    visible = current?.dialog,
                    clickCancel = current?.cancel?.let { button ->
                        { button.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                    },
                    goHome = { performGlobalAction(GLOBAL_ACTION_HOME) }
                )
            } finally {
                vpnCheckInProgress = false
            }
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

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        serviceConnected.value = false
        browserGuard.reset()
        uberGuard.leaveUber()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        serviceConnected.value = false
        browserGuard.reset()
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class ProtectedTarget(val packageName: String, val appName: String)

    companion object {
        private val serviceConnected = MutableStateFlow(false)
        internal val isConnected = serviceConnected.asStateFlow()


        private const val TAG = "UninstallA11yService"
        private val PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )
        private val POSITIVE_BUTTON_LABELS = setOf("ok", "uninstall", "deactivate")
        private val NEGATIVE_BUTTON_LABELS = setOf("cancel", "no", "keep app")
    }
}
