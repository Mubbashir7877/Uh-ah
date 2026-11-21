package com.example.uh_ah

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uh_ah.data.PreferencesManager
import com.example.uh_ah.data.SetstoneControl
import com.example.uh_ah.data.SetstoneSettingsStore
import com.example.uh_ah.ui.SetstoneSettings
import com.example.uh_ah.ui.screens.SetstoneEngineScreen
import com.example.uh_ah.ui.theme.UhAhTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SetstoneEngineScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun passwordFlowAndPreferencesSurviveRecreation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val prefsName = "setstone-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences(prefsName, mode)
        }
        try {
            val settings = SetstoneSettings(PreferencesManager(context))
            compose.setContent {
                UhAhTheme { SetstoneEngineScreen(settings, accessibilityEnabled = true, onEnableAccessibility = {}) {} }
            }
            val browser = compose.onNodeWithContentDescription("Browser Control")
            browser.assertIsOn()
            browser.performClick()
            compose.onNodeWithText("Password").performTextInput("00000")
            compose.onNodeWithText("Confirm").performClick()
            compose.onNodeWithText("Incorrect password").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            browser.assertIsOn()

            browser.performClick()
            compose.onNodeWithText("Password").performTextInput("12345")
            compose.onNodeWithText("Confirm").performClick()
            browser.assertIsOff()
            val reopened = SetstoneSettings(PreferencesManager(context))
            assertEquals(false, reopened.enabled.value.getValue(SetstoneControl.BROWSER_CONTROL))

            browser.performClick()
            browser.assertIsOn()
            compose.onNodeWithText("Password").assertDoesNotExist()
            assertEquals(true, PreferencesManager(context).isEnabled(SetstoneControl.BROWSER_CONTROL))
        } finally {
            app.deleteSharedPreferences(prefsName)
        }
    }

    @Test
    fun enabledBrowserControlShowsWhenAccessibilityPermissionIsMissing() {
        val settings = SetstoneSettings(object : SetstoneSettingsStore {
            override fun isEnabled(control: SetstoneControl) = true
            override fun setEnabled(control: SetstoneControl, enabled: Boolean) = Unit
        })
        compose.setContent {
            UhAhTheme {
                SetstoneEngineScreen(settings, accessibilityEnabled = false, onEnableAccessibility = {}) {}
            }
        }
        compose.onNodeWithText("Accessibility permission needed to monitor Samsung Browser.")
            .performScrollTo()
            .assertExists()
        compose.onNodeWithContentDescription("Browser Control").assertIsOn()
    }

    @Test
    fun plusPlaceholderIsDisabledAndDoesNotChangeThePageOrSettings() {
        val settings = SetstoneSettings(object : SetstoneSettingsStore {
            override fun isEnabled(control: SetstoneControl) = true

            override fun setEnabled(control: SetstoneControl, enabled: Boolean) {
                error("The placeholder must not write settings")
            }
        })
        compose.setContent {
            UhAhTheme {
                SetstoneEngineScreen(settings, accessibilityEnabled = true, onEnableAccessibility = {}) { error("The placeholder must not navigate") }
            }
        }

        val add = compose.onNodeWithContentDescription("Add control")
        add.performScrollTo().assertIsNotEnabled()
        // A physical tap also exercises the disabled button without a semantics click.
        add.performTouchInput { click() }

        compose.onNodeWithText("Setstone Engine").assertExists()
        for (label in listOf("VPN Security", "Browser Control", "Uber Control")) {
            compose.onNodeWithContentDescription(label).assertIsOn()
        }
        assertEquals(null, settings.pendingDisable.value)
    }
}
