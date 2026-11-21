package com.example.uh_ah.ui

internal object SetstonePassword {
    // Edit this value to change the development password for all Setstone controls.
    const val VALUE = "ArgKryNeoXen@781123>?<99"

    val isNumeric: Boolean
        get() = VALUE.all { it in '0'..'9' }

    fun isValid(password: String): Boolean = password == VALUE
}
