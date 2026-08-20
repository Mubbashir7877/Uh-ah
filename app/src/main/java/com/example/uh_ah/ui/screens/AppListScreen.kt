package com.example.uh_ah.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.uh_ah.data.AppEntity
import com.example.uh_ah.ui.components.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    apps: List<AppEntity>,
    masterEnabled: Boolean,
    isDeviceAdminActive: Boolean,
    isAccessibilityServiceActive: Boolean,
    snackbarHostState: SnackbarHostState,
    onMasterToggle: (Boolean) -> Unit,
    onAppToggle: (AppEntity, Boolean) -> Unit,
    onDeleteApp: (AppEntity) -> Unit,
    onAddClick: () -> Unit,
    onEnableDeviceAdmin: () -> Unit,
    onEnableAccessibility: () -> Unit,
    selectedTab: EngineTab,
    onTabSelected: (EngineTab) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { EngineNavigationBar(selectedTab, onTabSelected) },
        topBar = {
            TopAppBar(
                title = { Text("Uh-Ah 🚫") },
                actions = {
                    IconButton(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add app")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            MasterToggleBar(masterEnabled = masterEnabled, onToggle = onMasterToggle)

            if (!isDeviceAdminActive) {
                DeviceAdminBanner(onEnableDeviceAdmin)
            }

            if (!isAccessibilityServiceActive) {
                AccessibilityBanner(onEnableAccessibility)
            }

            if (apps.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppCard(
                            app = app,
                            onToggle = { enabled -> onAppToggle(app, enabled) },
                            onDelete = { onDeleteApp(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterToggleBar(masterEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "Uninstaller engine", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (masterEnabled) "Monitoring installs and enforcing your list" else "Paused — list is kept, nothing is removed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = masterEnabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun DeviceAdminBanner(onEnableDeviceAdmin: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Device Admin isn't active",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Required to detect and remove newly installed apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onEnableDeviceAdmin) { Text("Enable") }
        }
    }
}

@Composable
private fun AccessibilityBanner(onEnableAccessibility: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Auto-confirm isn't enabled",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Without it, removing an app needs one manual tap on the system dialog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onEnableAccessibility) { Text("Enable") }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🚫", style = MaterialTheme.typography.displayMedium)
        Text(
            text = "Your delete list is empty",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Tap + to add an app you never want installed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
