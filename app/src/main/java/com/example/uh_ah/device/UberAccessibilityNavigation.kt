package com.example.uh_ah.device

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal object UberAccessibilityNavigation {
    private const val MAX_NODES = 180
    private const val MAX_DEPTH = 12

    fun read(root: AccessibilityNodeInfo, activatedNode: AccessibilityNodeInfo? = null): UberNavigationState {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        val rows = mutableListOf<UberTabRow>()
        val rootBounds = root.bounds()
        val activated = activatedNode?.takeIf { it.belongsTo(root) }
        var scheduled = 1
        var hasEatsSearch = false

        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            if (depth == MAX_DEPTH || !node.belongsTo(root)) continue
            if (isEatsSearchField(node)) hasEatsSearch = true

            val children = mutableListOf<AccessibilityNodeInfo>()
            val count = minOf(node.childCount, MAX_NODES - scheduled)
            for (index in 0 until count) {
                val child = node.getChild(index)?.takeIf { it.belongsTo(root) } ?: continue
                queue.addLast(child to depth + 1)
                scheduled++
                children.add(child)
            }
            if (children.size !in 2..5 || count != node.childCount) continue

            val tabs = children.mapNotNull { child -> readTab(child, root, activated) }
            if (tabs.size == children.size) {
                val top = isHorizontalTopRow(children, rootBounds)
                rows.add(UberTabRow(tabs.map { tab -> tab.copy(activated = tab.activated && top) }, top))
            }
        }
        return UberNavigationState(rows, hasEatsSearch)
    }

    private fun readTab(
        node: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo,
        activated: AccessibilityNodeInfo?,
        depth: Int = 2
    ): UberTab? {
        if (!node.isEnabled) return null
        val actionable = node.isClickable ||
            AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK in node.actionList ||
            node.collectionItemInfo != null || node.isCheckable
        if (!actionable) {
            val child = if (depth > 0 && node.childCount == 1) node.getChild(0) else null
            return child?.takeIf { it.belongsTo(root) }?.let { readTab(it, root, activated, depth - 1) }
        }

        val values = listOfNotNull(node.contentDescription, node.text)
        val labels = values.mapNotNull { UberNavigation.tabLabel(it.toString()) }.distinct()
        val label = when {
            labels.size == 1 -> labels.single()
            labels.isEmpty() -> readChildLabel(node, root)
            else -> null
        } ?: return null
        val selected = node.isSelected || node.collectionItemInfo?.isSelected == true ||
            (node.isCheckable && node.isChecked) || node.stateDescription?.toString()?.equals("selected", true) == true ||
            values.any { it.toString().endsWith(", selected", ignoreCase = true) }
        val tapped = activated != null &&
            (node == activated || node.bounds().contains(activated.bounds()) &&
                listOfNotNull(activated.text, activated.contentDescription)
                    .any { UberNavigation.tabLabel(it.toString()) == label })
        return UberTab(label, selected, tapped)
    }

    private fun readChildLabel(node: AccessibilityNodeInfo, root: AccessibilityNodeInfo): String? =
        (0 until minOf(node.childCount, 4)).mapNotNull { index ->
            node.getChild(index)?.takeIf { it.belongsTo(root) && !it.isClickable }
        }.flatMap { listOfNotNull(it.text, it.contentDescription) }
            .mapNotNull { UberNavigation.tabLabel(it.toString()) }.distinct().singleOrNull()

    private fun isEatsSearchField(node: AccessibilityNodeInfo): Boolean {
        val searchNode = node.isEditable || node.className?.toString() == "android.widget.SearchView" ||
            node.isClickable && node.hintText != null
        return searchNode && listOfNotNull(node.hintText, node.text, node.contentDescription)
            .any { UberNavigation.isEatsSearch(it.toString()) }
    }

    private fun isHorizontalTopRow(children: List<AccessibilityNodeInfo>, root: Rect): Boolean {
        if (root.isEmpty) return false
        val bounds = children.map { it.bounds() }
        val first = bounds.first()
        val sharedTop = bounds.maxOf { it.top }
        val sharedBottom = bounds.minOf { it.bottom }
        return bounds.none(Rect::isEmpty) &&
            first.centerY() in root.top..(root.top + root.height() / 3) &&
            sharedTop < sharedBottom &&
            bounds.zipWithNext().all { (left, right) -> left.centerX() < right.centerX() }
    }

    private fun AccessibilityNodeInfo.bounds() = Rect().also(::getBoundsInScreen)

    private fun AccessibilityNodeInfo.belongsTo(root: AccessibilityNodeInfo) =
        isVisibleToUser && windowId == root.windowId && packageName?.toString() == UBER_RIDER_PACKAGE
}
