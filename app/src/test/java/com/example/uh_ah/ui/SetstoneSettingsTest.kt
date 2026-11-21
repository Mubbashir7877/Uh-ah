package com.example.uh_ah.ui

import com.example.uh_ah.data.SetstoneControl
import com.example.uh_ah.data.SetstoneSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetstoneSettingsTest {
    @Test
    fun missingSettingsDefaultToOn() {
        val store = MemoryStore()
        val settings = SetstoneSettings(store)

        assertEquals(SetstoneControl.entries.associateWith { true }, settings.enabled.value)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun correctPasswordDisablesOnlyTheSelectedControl() {
        for (control in SetstoneControl.entries) {
            val store = MemoryStore()
            val settings = SetstoneSettings(store)

            settings.requestChange(control, false)
            assertEquals(control, settings.pendingDisable.value)
            assertTrue(settings.enabled.value.getValue(control))
            assertTrue(settings.confirmDisable("12345"))

            assertEquals(mapOf(control to false), store.saved)
            assertEquals(SetstoneControl.entries.associateWith { it != control }, settings.enabled.value)
            assertNull(settings.pendingDisable.value)
        }
    }

    @Test
    fun incorrectPasswordLeavesControlOnAndAllowsRetry() {
        val store = MemoryStore()
        val settings = SetstoneSettings(store)
        val control = SetstoneControl.BROWSER_CONTROL

        settings.requestChange(control, false)
        assertFalse(settings.confirmDisable("wrong"))
        assertTrue(settings.enabled.value.getValue(control))
        assertEquals(control, settings.pendingDisable.value)
        assertTrue(store.saved.isEmpty())
        assertTrue(settings.confirmDisable("12345"))
    }

    @Test
    fun cancellationClearsPendingRequestWithoutSaving() {
        val store = MemoryStore()
        val settings = SetstoneSettings(store)
        val control = SetstoneControl.UBER_CONTROL

        settings.requestChange(control, false)
        settings.cancelDisable()

        assertNull(settings.pendingDisable.value)
        assertFalse(settings.confirmDisable("12345"))
        assertTrue(settings.enabled.value.getValue(control))
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun disabledStateSurvivesRecreationAndReenablesWithoutPassword() {
        val store = MemoryStore()
        val control = SetstoneControl.VPN_SECURITY
        val original = SetstoneSettings(store)
        original.requestChange(control, false)
        assertTrue(original.confirmDisable("12345"))

        val reopened = SetstoneSettings(store)
        assertFalse(reopened.enabled.value.getValue(control))
        reopened.requestChange(control, true)

        assertNull(reopened.pendingDisable.value)
        assertTrue(reopened.enabled.value.getValue(control))
        assertTrue(SetstoneSettings(store).enabled.value.getValue(control))
    }

    private class MemoryStore : SetstoneSettingsStore {
        val saved = mutableMapOf<SetstoneControl, Boolean>()

        override fun isEnabled(control: SetstoneControl): Boolean = saved[control] ?: true

        override fun setEnabled(control: SetstoneControl, enabled: Boolean) {
            saved[control] = enabled
        }
    }
}
