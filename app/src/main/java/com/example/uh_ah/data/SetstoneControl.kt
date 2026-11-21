package com.example.uh_ah.data

/** Stable identities for stored settings and future runtime handlers. */
enum class SetstoneControl(internal val preferenceKey: String) {
    VPN_SECURITY("setstone_vpn_security"),
    BROWSER_CONTROL("setstone_browser_control"),
    UBER_CONTROL("setstone_uber_control")
}

interface SetstoneSettingsStore {
    fun isEnabled(control: SetstoneControl): Boolean
    fun setEnabled(control: SetstoneControl, enabled: Boolean)
}
