package com.example.uh_ah.device

internal const val UBER_RIDER_PACKAGE = "com.ubercab"

internal data class UberTab(val label: String, val selected: Boolean)

internal object UberNavigation {
    private val serviceTabs = mapOf(
        listOf("Uber", "Eats", "Shops", "Courier") to 1,
        listOf("Rides", "Delivery") to 1
    )
    private val mainNavigation = listOf("Home", "Services", "Activity", "Account")

    fun isEatsSelected(groups: List<List<UberTab>>): Boolean {
        if (groups.none { tabs -> tabs.map(UberTab::label) == mainNavigation }) return false

        val services = groups.mapNotNull { tabs ->
            val eats = serviceTabs[tabs.map(UberTab::label)] ?: return@mapNotNull null
            tabs.count(UberTab::selected) == 1 && tabs[eats].selected
        }
        return services.singleOrNull() == true
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
        groups: List<List<UberTab>>,
        goHome: () -> Unit
    ) {
        if (!enabled || !authorized || foregroundPackage != UBER_RIDER_PACKAGE ||
            homeAttempted || !UberNavigation.isEatsSelected(groups)
        ) return

        homeAttempted = true
        goHome()
    }
}
