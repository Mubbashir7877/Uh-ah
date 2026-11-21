package com.example.uh_ah.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class EngineTab { REMOVE_INSTALLED, KEEP_INSTALLED, SET_STONE }

@Composable
fun EngineNavigationBar(selected: EngineTab, onSelected: (EngineTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == EngineTab.REMOVE_INSTALLED,
            onClick = { onSelected(EngineTab.REMOVE_INSTALLED) },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            label = { Text("Remove installs") }
        )
        NavigationBarItem(
            selected = selected == EngineTab.KEEP_INSTALLED,
            onClick = { onSelected(EngineTab.KEEP_INSTALLED) },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            label = { Text("Keep installed") }
        )
        NavigationBarItem(
            selected = selected == EngineTab.SET_STONE,
            onClick = { onSelected(EngineTab.SET_STONE) },
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            label = { Text("SetStone") }
        )
    }
}
