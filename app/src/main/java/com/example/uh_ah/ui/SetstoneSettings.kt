package com.example.uh_ah.ui

import com.example.uh_ah.data.SetstoneControl
import com.example.uh_ah.data.SetstoneSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class SetstoneSettings(private val store: SetstoneSettingsStore) {
    private val _enabled = MutableStateFlow(SetstoneControl.entries.associateWith(store::isEnabled))
    val enabled = _enabled.asStateFlow()

    private val _pendingDisable = MutableStateFlow<SetstoneControl?>(null)
    val pendingDisable = _pendingDisable.asStateFlow()

    fun requestChange(control: SetstoneControl, enabled: Boolean) {
        if (_pendingDisable.value != null || _enabled.value.getValue(control) == enabled) return
        if (enabled) {
            setEnabled(control, true)
        } else {
            _pendingDisable.value = control
        }
    }

    fun confirmDisable(password: String): Boolean {
        val control = _pendingDisable.value ?: return false
        if (!SetstonePassword.isValid(password)) return false
        setEnabled(control, false)
        _pendingDisable.value = null
        return true
    }

    fun cancelDisable() {
        _pendingDisable.value = null
    }

    private fun setEnabled(control: SetstoneControl, enabled: Boolean) {
        store.setEnabled(control, enabled)
        _enabled.value = _enabled.value + (control to enabled)
    }
}

private object SetstonePassword {
    // Development-only shared password.
    fun isValid(password: String): Boolean = password == "12345"
}
