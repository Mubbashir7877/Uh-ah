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
        fixture.event(listOf(navigation, eats.last() + UberTab("other", true)))
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

    @Test
    fun compactTopServiceNavigationCanSelectEatsWithoutTheBottomBar() {
        val fixture = Fixture()
        fixture.event(listOf(tabs("Ride", "Uber Eats", selected = 0)), topServiceRow = true)
        assertEquals(0, fixture.homeActions)

        fixture.event(listOf(tabs("Ride", "Uber Eats, Tab, Selected", "Travel", selected = 1)), topServiceRow = true)
        assertEquals(1, fixture.homeActions)
    }

    @Test
    fun confirmedEatsTabActivationDoesNotRequireSelectedMetadata() {
        val fixture = Fixture()
        val tapped = listOf(UberTab("rides", true), UberTab("eats", false, activated = true))
        fixture.event(listOf(tapped))
        assertEquals(0, fixture.homeActions)

        fixture.event(listOf(tapped), topServiceRow = true)
        assertEquals(1, fixture.homeActions)
    }

    @Test
    fun dedicatedEatsSearchDoesNotOverrideExplicitRidesSelection() {
        val fixture = Fixture()
        fixture.event(rides, hasEatsSearch = true)
        assertEquals(0, fixture.homeActions)

        fixture.event(emptyList(), hasEatsSearch = true)
        assertEquals(1, fixture.homeActions)
    }

    private fun tabs(vararg labels: String, selected: Int) =
        labels.mapIndexed { index, label -> UberTab(UberNavigation.tabLabel(label) ?: error(label), index == selected) }

    private class Fixture {
        val guard = UberEatsGuard()
        var homeActions = 0

        fun event(
            groups: List<List<UberTab>>,
            enabled: Boolean = true,
            authorized: Boolean = true,
            topServiceRow: Boolean = false,
            hasEatsSearch: Boolean = false,
            packageName: String = UBER_RIDER_PACKAGE
        ) {
            val rows = groups.map { UberTabRow(it, isTopServiceRow = topServiceRow) }
            guard.handle(enabled, authorized, packageName, UberNavigationState(rows, hasEatsSearch)) {
                homeActions++
            }
        }
    }
}
