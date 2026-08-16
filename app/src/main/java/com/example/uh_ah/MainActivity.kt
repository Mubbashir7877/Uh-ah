package com.example.uh_ah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uh_ah.device.AccessibilityHelper
import com.example.uh_ah.device.DeviceAdminHelper
import com.example.uh_ah.ui.MainViewModel
import com.example.uh_ah.ui.screens.AddAppSheet
import com.example.uh_ah.ui.screens.AppListScreen
import com.example.uh_ah.ui.screens.SurveyScreen
import com.example.uh_ah.ui.theme.UhAhTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestDeviceAdmin = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDeviceAdminStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Device admin/owner status can change from Settings while the app isn't in
        // front; refresh every time we come back to RESUMED, not just in onCreate.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDeviceAdminStatus()
        })

        setContent {
            UhAhTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UhAhApp(
                        viewModel = viewModel,
                        onEnableDeviceAdmin = { requestDeviceAdmin.launch(DeviceAdminHelper.requestActivationIntent(this)) },
                        onEnableAccessibility = { startActivity(AccessibilityHelper.openAccessibilitySettingsIntent()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UhAhApp(
    viewModel: MainViewModel,
    onEnableDeviceAdmin: () -> Unit,
    onEnableAccessibility: () -> Unit
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val masterEnabled by viewModel.masterEnabled.collectAsStateWithLifecycle()
    val isDeviceAdminActive by viewModel.isDeviceAdminActive.collectAsStateWithLifecycle()
    val isAccessibilityServiceActive by viewModel.isAccessibilityServiceActive.collectAsStateWithLifecycle()
    val showAddAppSheet by viewModel.showAddAppSheet.collectAsStateWithLifecycle()
    val addAppQuery by viewModel.addAppQuery.collectAsStateWithLifecycle()
    val addAppState by viewModel.addAppState.collectAsStateWithLifecycle()
    val surveyState by viewModel.surveyState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    var showFirstLaunchPrompt by remember {
        mutableStateOf(!viewModel.hasSeenDeviceAdminPrompt() && !isDeviceAdminActive)
    }

    val currentSurvey = surveyState
    if (currentSurvey != null) {
        SurveyScreen(
            state = currentSurvey,
            onAnswer = viewModel::answerSurveyQuestion,
            onConfirmTextChange = viewModel::updateConfirmText,
            onSubmitConfirm = viewModel::submitConfirmText,
            onCancel = viewModel::cancelSurvey
        )
    } else {
        AppListScreen(
            apps = apps,
            masterEnabled = masterEnabled,
            isDeviceAdminActive = isDeviceAdminActive,
            isAccessibilityServiceActive = isAccessibilityServiceActive,
            snackbarHostState = snackbarHostState,
            onMasterToggle = viewModel::setMasterEnabled,
            onAppToggle = viewModel::onAppToggleRequested,
            onDeleteApp = viewModel::onDeleteRequested,
            onAddClick = viewModel::openAddAppSheet,
            onEnableDeviceAdmin = onEnableDeviceAdmin,
            onEnableAccessibility = onEnableAccessibility
        )
    }

    if (showAddAppSheet) {
        AddAppSheet(
            query = addAppQuery,
            state = addAppState,
            onQueryChange = viewModel::updateAddAppQuery,
            onSearch = viewModel::searchPlayStore,
            onConfirmAdd = viewModel::confirmAddApp,
            onDismiss = viewModel::dismissAddAppSheet
        )
    }

    if (showFirstLaunchPrompt) {
        AlertDialog(
            onDismissRequest = {
                showFirstLaunchPrompt = false
                viewModel.markDeviceAdminPromptSeen()
            },
            title = { Text("Enable Device Admin") },
            text = {
                Text(
                    "Uh-Ah needs Device Admin to detect newly installed apps and remove " +
                        "the ones on your list. You can enable this later from the banner at the top."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFirstLaunchPrompt = false
                    viewModel.markDeviceAdminPromptSeen()
                    onEnableDeviceAdmin()
                }) { Text("Enable now") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFirstLaunchPrompt = false
                    viewModel.markDeviceAdminPromptSeen()
                }) { Text("Not now") }
            }
        )
    }
}
