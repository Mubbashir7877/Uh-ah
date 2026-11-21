package com.example.uh_ah.device

import java.util.Locale

internal const val UBER_RIDER_PACKAGE = "com.ubercab"

internal data class UberTab(val label: String, val selected: Boolean, val activated: Boolean = false)

internal data class UberTabRow(val tabs: List<UberTab>, val isTopServiceRow: Boolean = false)

internal data class UberNavigationState(
    val rows: List<UberTabRow>,
    val hasEatsSearch: Boolean = false
)

internal object UberNavigation {
    private val ridesLabels = setOf("uber", "ride", "rides")
    private val eatsLabels = setOf("eats", "uber eats", "delivery")
    private val otherServiceLabels = setOf("shops", "courier", "travel", "hotels", "shop")
    private val mainNavigation = listOf("home", "services", "activity", "account")

    fun tabLabel(value: String): String? {
        val label = value.trim().lowercase(Locale.ROOT).removeSuffix(", selected").removeSuffix(", tab")
        return label.takeIf {
            it in ridesLabels || it in eatsLabels || it in otherServiceLabels || it in mainNavigation
        }
    }

    fun isEatsSearch(value: String): Boolean =
        value.trim().lowercase(Locale.ROOT).trimEnd('.', '?', '…') in
            setOf("search uber eats", "food, groceries, drinks, etc")

    fun isEatsSelected(state: UberNavigationState): Boolean {
        val hasMainNavigation = state.rows.any { row -> row.tabs.map(UberTab::label) == mainNavigation }
        val serviceRows = state.rows.filter { row ->
            val labels = row.tabs.map(UberTab::label)
            val documented = labels == listOf("uber", "eats", "shops", "courier") ||
                labels == listOf("rides", "delivery")
            (row.isTopServiceRow || documented && hasMainNavigation) &&
                labels.size in 2..5 && labels.distinct().size == labels.size &&
                labels.first() in ridesLabels && labels[1] in eatsLabels &&
                labels.drop(2).all(otherServiceLabels::contains)
        }
        if (serviceRows.size > 1) return false

        val row = serviceRows.singleOrNull() ?: return state.hasEatsSearch
        val eats = row.tabs[1]
        if (eats.activated) return true
        val selected = row.tabs.filter(UberTab::selected)
        return if (selected.isEmpty()) state.hasEatsSearch else selected.singleOrNull() == eats
    }
}

internal class UberEatsGuard {
    var homeAttempted: Boolean = false
        private set

    fun leaveUber() {
        homeAttempted = false
    }

    fun handle(
        enabled: Boolean,
        authorized: Boolean,
        foregroundPackage: String,
        navigation: UberNavigationState,
        goHome: () -> Unit
    ) {
        if (!enabled || !authorized || foregroundPackage != UBER_RIDER_PACKAGE ||
            homeAttempted || !UberNavigation.isEatsSelected(navigation)
        ) return

        homeAttempted = true
        goHome()
    }
}
