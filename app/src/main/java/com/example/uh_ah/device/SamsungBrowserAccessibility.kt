package com.example.uh_ah.device

import android.view.accessibility.AccessibilityNodeInfo

internal object SamsungBrowserAccessibility {
    private const val MAX_NODES = 160
    private const val MAX_DEPTH = 10
    private val searchHints = setOf("search apps & games", "search apps and games", "search galaxy store", "search settings")
    private val appInfoTitles = setOf("app info", "application info")
    private val browserPickerTitles = setOf("default browser app", "browser app", "default browser")
    private val appDetails = setOf("notifications", "permissions", "storage & cache")
    private val searchIdPattern = Regex("search(?:_src_text|_box(?:_text_input)?|_edit_text|_text_input|_query|_field)?")

    fun read(root: AccessibilityNodeInfo, host: String, eventSource: AccessibilityNodeInfo?): BrowserScreen {
        if (eventSource != null && ownedBy(eventSource, root, host) && isTargetSearch(eventSource, host)) {
            return BrowserScreen(targetedSearch = true)
        }
        val budget = NodeBudget()
        val signals = scan(root, root, host, budget, MAX_DEPTH)
        if (signals.search) return BrowserScreen(targetedSearch = true)
        if (host in SamsungBrowserRules.stores) return BrowserScreen(storeCard = signals.storeCard)
        if (host != SamsungBrowserRules.SETTINGS) return BrowserScreen()

        if (signals.browserPicker && signals.selectedBrowser != null) {
            return BrowserScreen(settingsTargetTitle = signals.selectedBrowser)
        }
        if (signals.appInfo && signals.appHeaderPresent) {
            return BrowserScreen(settingsTargetId = signals.appHeaderId, settingsTargetTitle = signals.appHeaderTitle)
        }
        if (signals.composeAppInfoTitle && signals.composeAppHeader != null) {
            return BrowserScreen(settingsTargetTitle = signals.composeAppHeader)
        }
        return BrowserScreen()
    }

    private fun scan(
        node: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo,
        host: String,
        budget: NodeBudget,
        depth: Int
    ): Signals {
        if (budget.remaining == 0 || !ownedBy(node, root, host)) return Signals()
        budget.remaining--
        val id = node.viewIdResourceName.orEmpty()
        val labels = listOfNotNull(node.text, node.contentDescription).flatMap { it.lineSequence().toList() }
        val normalizedLabels = labels.map(SamsungBrowserRules::normalize)
        val targetTitle = normalizedLabels.filter(SamsungBrowserRules::isTitle).distinct().singleOrNull()
        val exactId = labels.any { SamsungBrowserRules.targetId(it) != null }
        val isAppHeader = host == SamsungBrowserRules.SETTINGS &&
            id in setOf("$host:id/entity_header_title", "$host:id/app_name", "$host:id/package_name")
        val isPageTitle = host == SamsungBrowserRules.SETTINGS &&
            id in setOf("$host:id/collapsing_toolbar", "$host:id/action_bar_title", "android:id/action_bar_title")
        val clickable = node.isEnabled && node.isClickable
        val headerTitle = targetTitle ?: normalizedLabels.firstOrNull { it == "internet" }
        val result = Signals(
            count = 1,
            search = isTargetSearch(node, host),
            targetTitles = if (targetTitle == null) 0 else 1,
            targetId = exactId,
            publisher = labels.any(SamsungBrowserRules::isPublisher),
            actionLabel = labels.any(SamsungBrowserRules::isAction),
            appAction = clickable && labels.any(SamsungBrowserRules::isAction),
            appInfo = isPageTitle && normalizedLabels.any(appInfoTitles::contains),
            browserPicker = isPageTitle && normalizedLabels.any(browserPickerTitles::contains),
            appHeaderPresent = isAppHeader,
            appHeaderTitle = headerTitle.takeIf { isAppHeader },
            appHeaderId = isAppHeader && exactId,
            composeAppInfoTitle = host == SamsungBrowserRules.SETTINGS && normalizedLabels.any(appInfoTitles::contains),
            appDetails = normalizedLabels.filter(appDetails::contains).toSet(),
            checked = node.isCheckable && node.isChecked,
            browserRowTitle = headerTitle,
            selectedBrowser = headerTitle.takeIf { node.isCheckable && node.isChecked }
        )
        if (result.search) return result
        val settingsScroll = host == SamsungBrowserRules.SETTINGS &&
            node.className?.toString() == "android.widget.ScrollView"
        var earlyHeader: String? = null
        if (depth > 0) {
            for (index in 0 until minOf(node.childCount, budget.remaining)) {
                if (budget.remaining == 0) break
                val child = node.getChild(index) ?: continue
                val childSignals = scan(child, root, host, budget, depth - 1)
                if (settingsScroll && index < 3 && child.className?.toString() == "android.widget.TextView") {
                    earlyHeader = earlyHeader ?: childSignals.browserRowTitle
                }
                result.merge(childSignals)
                if (result.search || result.storeCard) return result
            }
        }
        if (clickable && result.count <= 5 && result.actionLabel) result.appAction = true
        if (settingsScroll && result.appAction && result.appDetails.containsAll(appDetails)) {
            result.composeAppHeader = earlyHeader
        }
        // Keep identity evidence together in a compact card or product header.
        // Never join unrelated publisher/action text across an entire store page.
        if (host in SamsungBrowserRules.stores && node !== root && result.count <= 20 && result.targetTitles <= 1) {
            result.storeCard = BrowserStoreCard(
                targetTitle = result.targetTitles == 1,
                targetId = result.targetId,
                publisher = result.publisher,
                appAction = result.appAction,
                clickable = clickable
            ).matches()
        }
        if (host == SamsungBrowserRules.SETTINGS && clickable && result.count <= 6 && result.checked) {
            result.selectedBrowser = result.browserRowTitle
        }
        return result
    }

    private fun isTargetSearch(node: AccessibilityNodeInfo, host: String): Boolean {
        if (host !in SamsungBrowserRules.searchable || !node.isEditable || !node.isFocused || node.isPassword) return false
        val id = node.viewIdResourceName.orEmpty()
        val searchId = id.startsWith("$host:id/") && searchIdPattern.matches(id.substringAfter("/"))
        val searchHint = listOfNotNull(node.hintText, node.contentDescription).any {
            SamsungBrowserRules.normalize(it.toString()) in searchHints
        }
        return (searchId || searchHint) && node.text?.toString()?.let(SamsungBrowserRules::isSearch) == true
    }

    private fun ownedBy(node: AccessibilityNodeInfo, root: AccessibilityNodeInfo, host: String) =
        node.isVisibleToUser && node.windowId == root.windowId && node.packageName?.toString() == host

    private class NodeBudget(var remaining: Int = MAX_NODES)

    private data class Signals(
        var count: Int = 0,
        var search: Boolean = false,
        var targetTitles: Int = 0,
        var targetId: Boolean = false,
        var publisher: Boolean = false,
        var actionLabel: Boolean = false,
        var appAction: Boolean = false,
        var storeCard: Boolean = false,
        var appInfo: Boolean = false,
        var browserPicker: Boolean = false,
        var appHeaderPresent: Boolean = false,
        var appHeaderTitle: String? = null,
        var appHeaderId: Boolean = false,
        var composeAppInfoTitle: Boolean = false,
        var composeAppHeader: String? = null,
        var appDetails: Set<String> = emptySet(),
        var checked: Boolean = false,
        var browserRowTitle: String? = null,
        var selectedBrowser: String? = null
    ) {
        fun merge(other: Signals) {
            count += other.count
            search = search || other.search
            targetTitles += other.targetTitles
            targetId = targetId || other.targetId
            publisher = publisher || other.publisher
            actionLabel = actionLabel || other.actionLabel
            appAction = appAction || other.appAction
            storeCard = storeCard || other.storeCard
            appInfo = appInfo || other.appInfo
            browserPicker = browserPicker || other.browserPicker
            appHeaderPresent = appHeaderPresent || other.appHeaderPresent
            appHeaderTitle = appHeaderTitle ?: other.appHeaderTitle
            appHeaderId = appHeaderId || other.appHeaderId
            composeAppInfoTitle = composeAppInfoTitle || other.composeAppInfoTitle
            composeAppHeader = composeAppHeader ?: other.composeAppHeader
            if (other.appDetails.isNotEmpty()) appDetails += other.appDetails
            checked = checked || other.checked
            browserRowTitle = browserRowTitle ?: other.browserRowTitle
            selectedBrowser = selectedBrowser ?: other.selectedBrowser
        }
    }
}
