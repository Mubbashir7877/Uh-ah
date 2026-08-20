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
import com.example.uh_ah.ui.InstallProtectionViewModel
import com.example.uh_ah.ui.screens.AddAppSheet
import com.example.uh_ah.ui.screens.AddInstalledAppSheet
import com.example.uh_ah.ui.screens.AppListScreen
import com.example.uh_ah.ui.screens.EngineTab
import com.example.uh_ah.ui.screens.InstallProtectionScreen
import com.example.uh_ah.ui.screens.InstallSurveyScreen
import com.example.uh_ah.ui.screens.SurveyScreen
import com.example.uh_ah.ui.theme.UhAhTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val installProtectionViewModel: InstallProtectionViewModel by viewModels()

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
                        installViewModel = installProtectionViewModel,
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
    installViewModel: InstallProtectionViewModel,
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
    val protectedApps by installViewModel.apps.collectAsStateWithLifecycle()
    val installMasterEnabled by installViewModel.masterEnabled.collectAsStateWithLifecycle()
    val showInstalledAppSheet by installViewModel.showAddSheet.collectAsStateWithLifecycle()
    val installedAppQuery by installViewModel.query.collectAsStateWithLifecycle()
    val installedApps by installViewModel.filteredInstalledApps.collectAsStateWithLifecycle()
    val isScanningInstalledApps by installViewModel.isScanning.collectAsStateWithLifecycle()
    val installSurveyState by installViewModel.surveyState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    LaunchedEffect(Unit) {
        installViewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    var selectedTab by remember { mutableStateOf(EngineTab.REMOVE_INSTALLED) }

    var showFirstLaunchPrompt by remember {
        mutableStateOf(!viewModel.hasSeenDeviceAdminPrompt() && !isDeviceAdminActive)
    }

    val currentSurvey = surveyState
    val currentInstallSurvey = installSurveyState
    if (selectedTab == EngineTab.REMOVE_INSTALLED && currentSurvey != null) {
        SurveyScreen(
            state = currentSurvey,
            onAnswer = viewModel::answerSurveyQuestion,
            onConfirmTextChange = viewModel::updateConfirmText,
            onSubmitConfirm = viewModel::submitConfirmText,
            onCancel = viewModel::cancelSurvey
        )
    } else if (selectedTab == EngineTab.KEEP_INSTALLED && currentInstallSurvey != null) {
        InstallSurveyScreen(
            state = currentInstallSurvey,
            onAnswer = installViewModel::answerSurveyQuestion,
            onConfirmTextChange = installViewModel::updateConfirmText,
            onSubmitConfirm = installViewModel::submitConfirmText,
            onCancel = installViewModel::cancelSurvey
        )
    } else if (selectedTab == EngineTab.REMOVE_INSTALLED) {
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
            onEnableAccessibility = onEnableAccessibility,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    } else {
        InstallProtectionScreen(
            apps = protectedApps,
            masterEnabled = installMasterEnabled,
            isAccessibilityServiceActive = isAccessibilityServiceActive,
            snackbarHostState = snackbarHostState,
            onMasterToggle = installViewModel::setMasterEnabled,
            onAppToggle = installViewModel::onAppToggleRequested,
            onDeleteApp = installViewModel::onDeleteRequested,
            onAddClick = installViewModel::openAddSheet,
            onEnableAccessibility = onEnableAccessibility,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
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

    if (showInstalledAppSheet) {
        AddInstalledAppSheet(
            query = installedAppQuery,
            apps = installedApps,
            isScanning = isScanningInstalledApps,
            onQueryChange = installViewModel::updateQuery,
            onAdd = installViewModel::addApp,
            onDismiss = installViewModel::dismissAddSheet
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
