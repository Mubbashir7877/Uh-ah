package com.example.uh_ah.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uh_ah.data.SetstoneControl
import com.example.uh_ah.device.SamsungBrowserPackages
import com.example.uh_ah.device.UninstallAccessibilityService
import com.example.uh_ah.ui.SetstonePassword
import com.example.uh_ah.ui.SetstoneSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetstoneEngineScreen(
    settings: SetstoneSettings,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onTabSelected: (EngineTab) -> Unit
) {
    val enabled by settings.enabled.collectAsStateWithLifecycle()
    val pendingDisable by settings.pendingDisable.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Setstone Engine", Modifier.semantics { heading() }) })
        },
        bottomBar = { EngineNavigationBar(EngineTab.SET_STONE, onTabSelected) }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())) {
            SetstoneControl.entries.forEach { control ->
                ListItem(
                    headlineContent = { Text(control.label) },
                    supportingContent = {
                        when (control) {
                            SetstoneControl.BROWSER_CONTROL -> BrowserControlStatus(
                                enabled.getValue(control), accessibilityEnabled, onEnableAccessibility
                            )
                            else -> EngineAccessibilityStatus(
                                control, enabled.getValue(control), accessibilityEnabled, onEnableAccessibility
                            )
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = enabled.getValue(control),
                            onCheckedChange = { settings.requestChange(control, it) },
                            modifier = Modifier.semantics { contentDescription = control.label }
                        )
                    }
                )
            }
            IconButton(onClick = {}, enabled = false, modifier = Modifier.padding(start = 12.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add control")
            }
        }
    }

    pendingDisable?.let { control ->
        key(control) {
            SetstonePasswordDialog(control, settings::confirmDisable, settings::cancelDisable)
        }
    }
}

@Composable
private fun EngineAccessibilityStatus(
    control: SetstoneControl,
    enabled: Boolean,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit
) {
    val description = when (control) {
        SetstoneControl.VPN_SECURITY ->
            "Uses Accessibility to cancel recognized SafeLook disconnect dialogs and return to Home."
        SetstoneControl.UBER_CONTROL ->
            "Uses Accessibility to return to Home when Uber’s Eats tab is recognized as selected."
        SetstoneControl.BROWSER_CONTROL -> return
    }
    Column {
        Text(description)
        if (enabled && !accessibilityEnabled) {
            TextButton(onClick = onEnableAccessibility) { Text("Open Accessibility settings") }
        }
    }
}

@Composable
private fun BrowserControlStatus(
    enabled: Boolean,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit
) {
    val connected by UninstallAccessibilityService.isConnected.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val packageSummary by produceState<String?>(null, context, lifecycle) {
        lifecycle.currentStateFlow.collectLatest { state ->
            if (state == Lifecycle.State.RESUMED) {
                value = withContext(Dispatchers.IO) { SamsungBrowserPackages.summary(context) }
            }
        }
    }

    Column {
        Text(when {
            !enabled -> "Off. Browser monitoring is paused."
            !accessibilityEnabled -> "Accessibility permission needed to monitor Samsung Browser."
            !connected -> "Waiting for Android to connect the accessibility service."
            else -> "Monitoring Samsung Browser and recognized store and Settings screens."
        })
        if (enabled && !accessibilityEnabled) {
            TextButton(onClick = onEnableAccessibility) { Text("Open Accessibility settings") }
        }
        packageSummary?.let { Text(it) }
        Text("Unrecognized screens are skipped. Uh-Ah does not manage Android package restrictions.")
    }
}

private val SetstoneControl.label: String
    get() = when (this) {
        SetstoneControl.VPN_SECURITY -> "VPN Security"
        SetstoneControl.BROWSER_CONTROL -> "Browser Control"
        SetstoneControl.UBER_CONTROL -> "Uber Control"
    }

@Composable
private fun SetstonePasswordDialog(
    control: SetstoneControl,
    onConfirm: (String) -> Boolean,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Turn off ${control.label}?") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    invalid = false
                },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (SetstonePassword.isNumeric) KeyboardType.NumberPassword else KeyboardType.Password
                ),
                isError = invalid,
                supportingText = { if (invalid) Text("Incorrect password") }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                invalid = !onConfirm(password)
                password = ""
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
