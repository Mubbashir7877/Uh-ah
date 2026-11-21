package com.example.uh_ah.device

import org.junit.Assert.assertEquals
import org.junit.Test

class UberEatsGuardTest {
    private val navigation = tabs("Home", "Services", "Activity", "Account", selected = 0)
    private val rides = listOf(navigation, tabs("Uber", "Eats", "Shops", "Courier", selected = 0))
    private val eats = listOf(navigation, tabs("Uber", "Eats", "Shops", "Courier", selected = 1))

    @Test
    fun ridesAndLaunchRemainAvailableUntilEatsIsSelected() {
        val fixture = Fixture()
        fixture.event(emptyList())
        fixture.event(rides)
        assertEquals(0, fixture.homeActions)

        fixture.event(eats)
        assertEquals(1, fixture.homeActions)
    }

    @Test
    fun documentedDeliveryTabAlsoRequiresMainNavigation() {
        val fixture = Fixture()
        val delivery = tabs("Rides", "Delivery", selected = 1)
        fixture.event(listOf(delivery))
        assertEquals(0, fixture.homeActions)

        fixture.event(listOf(navigation, delivery))
        assertEquals(1, fixture.homeActions)
    }

    @Test
    fun offAndMissingPermissionLeaveEatsAvailable() {
        val fixture = Fixture()
        fixture.event(eats, enabled = false)
        fixture.event(eats, authorized = false)
        assertEquals(0, fixture.homeActions)
    }

    @Test
    fun otherAppsIncludingStandaloneEatsAreIgnored() {
        val fixture = Fixture()
        fixture.event(eats, packageName = "com.ubercab.eats")
        fixture.event(eats, packageName = "com.example.other")
        assertEquals(0, fixture.homeActions)
    }

    @Test
    fun mentionsChangedLayoutsAndAmbiguousSelectionAreIgnored() {
        val fixture = Fixture()
        fixture.event(listOf(navigation, tabs("Eats", selected = 0)))
        fixture.event(listOf(navigation, tabs("Rides", "Eats", selected = 1)))
        fixture.event(listOf(navigation, tabs("Uber", "Eats", "Shops", "Courier", selected = -1)))
        fixture.event(listOf(navigation, eats.last() + UberTab("Other", true)))
        fixture.event(eats + listOf(rides.last()))
        fixture.event(listOf(navigation, eats.last().map { it.copy(selected = true) }))
        assertEquals(0, fixture.homeActions)
    }

    @Test
    fun rapidEventsTriggerOneActionUntilUberLeavesTheForeground() {
        val fixture = Fixture()
        repeat(10) { fixture.event(eats) }
        assertEquals(1, fixture.homeActions)

        fixture.guard.leaveUber()
        fixture.event(eats)
        assertEquals(2, fixture.homeActions)
    }

    private fun tabs(vararg labels: String, selected: Int) =
        labels.mapIndexed { index, label -> UberTab(label, index == selected) }

    private class Fixture {
        val guard = UberEatsGuard()
        var homeActions = 0

        fun event(
            groups: List<List<UberTab>>,
            enabled: Boolean = true,
            authorized: Boolean = true,
            packageName: String = UBER_RIDER_PACKAGE
        ) {
            guard.handle(enabled, authorized, packageName, groups) { homeActions++ }
        }
    }
}
