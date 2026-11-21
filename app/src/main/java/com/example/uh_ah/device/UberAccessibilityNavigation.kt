package com.example.uh_ah.device

import android.view.accessibility.AccessibilityNodeInfo

internal object UberAccessibilityNavigation {
    private const val MAX_NODES = 120
    private const val MAX_DEPTH = 8
    private val tabLabels = setOf(
        "Uber", "Eats", "Shops", "Courier", "Rides", "Delivery",
        "Home", "Services", "Activity", "Account"
    )

    fun read(root: AccessibilityNodeInfo): List<List<UberTab>> {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        val groups = mutableListOf<List<UberTab>>()
        var scheduled = 1

        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            if (depth == MAX_DEPTH || !node.isVisibleToUser || node.packageName?.toString() != UBER_RIDER_PACKAGE) continue

            val count = minOf(node.childCount, MAX_NODES - scheduled)
            val tabs = mutableListOf<UberTab>()
            for (index in 0 until count) {
                val child = node.getChild(index) ?: continue
                if (child.windowId != root.windowId || !child.isVisibleToUser ||
                    child.packageName?.toString() != UBER_RIDER_PACKAGE
                ) continue
                queue.addLast(child to depth + 1)
                scheduled++
                if (node.childCount == 2 || node.childCount == 4) readTab(child)?.let(tabs::add)
            }
            if (tabs.isNotEmpty() && tabs.size == node.childCount) groups.add(tabs)
        }
        return groups
    }

    private fun readTab(node: AccessibilityNodeInfo): UberTab? {
        if (!node.isEnabled || !node.isClickable ||
            AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK !in node.actionList
        ) return null

        val label = sequenceOf(node.contentDescription, node.text)
            .mapNotNull { it?.toString()?.trim()?.takeIf(tabLabels::contains) }
            .distinct()
            .singleOrNull() ?: return null
        val selected = node.isSelected || node.collectionItemInfo?.isSelected == true
        return UberTab(label, selected)
    }
}
