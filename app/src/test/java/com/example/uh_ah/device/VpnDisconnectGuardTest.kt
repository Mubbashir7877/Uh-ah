package com.example.uh_ah.device

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnDisconnectGuardTest {
    private val target = VpnDisconnectDialog("SafeLook", "Disconnect this VPN?", "Disconnect", "Cancel")

    @Test
    fun matchingDialogClicksCancelThenGoesHomeOncePerWindow() {
        val fixture = Fixture()
        fixture.dispatch()
        fixture.dispatch()
        fixture.dispatch(windowId = 2)

        assertEquals(listOf("Cancel", "Home", "Cancel", "Home"), fixture.actions)
    }

    @Test
    fun offOrMissingAuthorizationDoesNotAct() {
        val fixture = Fixture()
        fixture.enabled = false
        fixture.dispatch()
        fixture.enabled = true
        fixture.authorized = false
        fixture.dispatch()

        assertEquals(emptyList<String>(), fixture.actions)
    }

    @Test
    fun unconfirmedIdentityAndUnrelatedDialogsDoNotAct() {
        val fixture = Fixture()
        fixture.dispatch(expected = null)
        fixture.dispatch(visible = target.copy(title = "Other VPN"))
        fixture.dispatch(visible = target.copy(message = "Delete this profile?"))
        fixture.dispatch(visible = target.copy(cancel = "Disconnect"))
        fixture.dispatch(visible = null)

        assertEquals(emptyList<String>(), fixture.actions)
    }

    @Test
    fun inaccessibleCancelOrRejectedClickDoesNotNavigate() {
        val fixture = Fixture()
        fixture.dispatch(click = null)
        fixture.dispatch(click = {
            fixture.actions += "Rejected cancel"
            false
        })
        fixture.dispatch()

        assertEquals(listOf("Rejected cancel"), fixture.actions)
    }

    @Test
    fun losingAuthorizationDuringCancelDoesNotNavigate() {
        val fixture = Fixture()
        fixture.dispatch(click = {
            fixture.actions += "Cancel"
            fixture.authorized = false
            true
        })

        assertEquals(listOf("Cancel"), fixture.actions)
    }

    private inner class Fixture {
        var enabled = true
        var authorized = true
        val actions = mutableListOf<String>()
        private val guard = VpnDisconnectGuard { enabled && authorized }

        fun dispatch(
            windowId: Int = 1,
            expected: VpnDisconnectDialog? = target,
            visible: VpnDisconnectDialog? = target,
            click: (() -> Boolean)? = {
                actions += "Cancel"
                true
            }
        ) {
            guard.handle(windowId, expected, visible, click) { actions += "Home" }
        }
    }
}
