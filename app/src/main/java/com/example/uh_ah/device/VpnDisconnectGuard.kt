package com.example.uh_ah.device

internal data class VpnDisconnectDialog(
    val title: String,
    val message: String,
    val disconnect: String,
    val cancel: String
)

internal class VpnDisconnectGuard(private val canAct: () -> Boolean) {
    private var attemptedWindowId: Int? = null

    fun hasAttempted(windowId: Int) = attemptedWindowId == windowId

    fun handle(
        windowId: Int,
        expected: VpnDisconnectDialog?,
        visible: VpnDisconnectDialog?,
        clickCancel: (() -> Boolean)?,
        goHome: () -> Unit
    ) {
        if (!canAct() || hasAttempted(windowId) || expected == null ||
            visible != expected || clickCancel == null
        ) return

        attemptedWindowId = windowId
        if (clickCancel() && canAct()) goHome()
    }
}
