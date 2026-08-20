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
import com.example.uh_ah.data.ProtectedAppEntity
import com.example.uh_ah.ui.components.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallProtectionScreen(
    apps: List<ProtectedAppEntity>,
    masterEnabled: Boolean,
    isAccessibilityServiceActive: Boolean,
    snackbarHostState: SnackbarHostState,
    onMasterToggle: (Boolean) -> Unit,
    onAppToggle: (ProtectedAppEntity, Boolean) -> Unit,
    onDeleteApp: (ProtectedAppEntity) -> Unit,
    onAddClick: () -> Unit,
    onEnableAccessibility: () -> Unit,
    selectedTab: EngineTab,
    onTabSelected: (EngineTab) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { EngineNavigationBar(selectedTab, onTabSelected) },
        topBar = {
            TopAppBar(
                title = { Text("Uh-Ah 🔒") },
                actions = { IconButton(onClick = onAddClick) { Icon(Icons.Default.Add, "Add app") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Install engine", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (masterEnabled) "Preventing protected apps from being uninstalled"
                        else "Paused — list is kept, uninstalls are not blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(masterEnabled, onCheckedChange = onMasterToggle)
            }

            if (!isAccessibilityServiceActive) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Column(Modifier.weight(1f)) {
                            Text("Uninstall protection isn't enabled", style = MaterialTheme.typography.titleSmall)
                            Text("Required to automatically cancel system uninstall dialogs.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onEnableAccessibility) { Text("Enable") }
                    }
                }
            }

            if (apps.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text("🔒", style = MaterialTheme.typography.displayMedium)
                    Text("Your keep-installed list is empty", style = MaterialTheme.typography.titleMedium)
                    Text("Tap + to choose an installed app you want to protect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppCard(app, { onAppToggle(app, it) }, { onDeleteApp(app) })
                    }
                }
            }
        }
    }
}
