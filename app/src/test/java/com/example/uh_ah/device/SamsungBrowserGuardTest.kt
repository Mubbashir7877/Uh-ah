package com.example.uh_ah.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungBrowserGuardTest {
    private val rules = SamsungBrowserRules

    @Test
    fun stableAndBetaLaunchesGoHomeButSafeLookAndOtherBrowsersDoNot() {
        val fixture = Fixture()
        fixture.event("com.pro.SafeLook")
        fixture.event("com.android.chrome")
        assertEquals(0, fixture.actions)

        fixture.event(rules.STABLE)
        fixture.event(rules.BETA)
        assertEquals(2, fixture.actions)
    }

    @Test
    fun targetedSearchAliasesAndExactPackageIdsMatchWithoutGeneralTerms() {
        listOf(
            "Samsung Internet", "Samsung Browser", " SAMSUNG  Internet Browser ",
            "Samsung Internet Beta", "Samsung Browser Beta", rules.BETA
        ).forEach { assertTrue(rules.isSearch(it)) }

        listOf("internet", "browser", "Samsung phones", "Firefox", "news about Samsung Browser")
            .forEach { assertFalse(rules.isSearch(it)) }

        val fixture = Fixture()
        fixture.event(rules.PLAY, BrowserScreen(targetedSearch = rules.isSearch("internet")))
        assertEquals(0, fixture.actions)
        fixture.event(rules.PLAY, BrowserScreen(targetedSearch = rules.isSearch("Samsung Browser")))
        assertEquals(1, fixture.actions)
    }

    @Test
    fun storeCardsRequireIdentityAndAppropriateControls() {
        assertEquals(rules.STABLE, rules.targetId("market://details?id=${rules.STABLE}"))
        assertEquals(rules.BETA, rules.targetId("https://galaxystore.samsung.com/detail/${rules.BETA}"))
        assertEquals(null, rules.targetId("https://example.test/detail/${rules.BETA}"))

        assertFalse(BrowserStoreCard(targetTitle = true, clickable = true).matches())
        assertFalse(BrowserStoreCard(targetTitle = true, publisher = true).matches())

        val fixture = Fixture()
        fixture.event(rules.PLAY, BrowserScreen(storeCard = BrowserStoreCard(
            targetTitle = true, targetId = true, appAction = true
        ).matches()))
        fixture.event(rules.GALAXY, BrowserScreen(storeCard = BrowserStoreCard(
            targetTitle = true, publisher = true, clickable = true
        ).matches()))
        assertEquals(2, fixture.actions)
    }

    @Test
    fun settingsRequiresVerifiedAppInfoEnableIdentityOrTargetedSearch() {
        val fixture = Fixture()
        fixture.event(rules.SETTINGS)
        fixture.event(rules.SETTINGS, BrowserScreen(settingsTargetTitle = "Samsung Browser"))
        fixture.event(rules.SETTINGS, BrowserScreen(settingsTargetTitle = "Other Browser"))
        assertEquals(0, fixture.actions)

        fixture.event(rules.SETTINGS, BrowserScreen(settingsTargetTitle = "Samsung Browser"), confirmed = true)
        fixture.event("com.example.launcher")
        fixture.event(rules.SETTINGS, BrowserScreen(settingsTargetId = true))
        fixture.event("com.example.launcher")
        fixture.event("com.google.android.settings.intelligence", BrowserScreen(targetedSearch = true))
        assertEquals(3, fixture.actions)
    }

    @Test
    fun disabledAndUnauthorizedTransitionsAreIgnoredAndDuplicatesAreSuppressed() {
        val fixture = Fixture()
        fixture.event(rules.STABLE, enabled = false)
        fixture.event(rules.STABLE, authorized = false)
        assertEquals(0, fixture.actions)
        repeat(10) { fixture.event(rules.STABLE) }
        assertEquals(1, fixture.actions)

        fixture.event("com.example.launcher")
        fixture.event(rules.STABLE)
        assertEquals(2, fixture.actions)
    }

    private class Fixture {
        private val guard = SamsungBrowserGuard()
        var actions = 0

        fun event(
            packageName: String,
            screen: BrowserScreen = BrowserScreen(),
            enabled: Boolean = true,
            authorized: Boolean = true,
            confirmed: Boolean = false
        ) {
            guard.foregroundChanged(packageName)
            guard.handle(packageName, enabled, { authorized }, screen, confirmed) { actions++ }
        }
    }
}
