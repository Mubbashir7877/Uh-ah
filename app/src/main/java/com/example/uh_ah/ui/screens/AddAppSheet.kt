package com.example.uh_ah.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.uh_ah.ui.AddAppState
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.uh_ah.ui.components.AppIcon

/**
 * Add-app dialog: name input -> Play Store search (with progress indicator) ->
 * preview card with icon/name -> Add. Duplicate/not-found/network errors are
 * shown inline rather than crashing or silently failing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppSheet(
    query: String,
    state: AddAppState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onConfirmAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(text = "Add app to delete list", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("App name") },
                placeholder = { Text("e.g. DoorDash, TikTok") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("addAppQueryField")
            )
            Spacer(Modifier.height(12.dp))

            when (state) {
                AddAppState.Idle -> {
                    Button(
                        onClick = onSearch,
                        enabled = query.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Search Play Store") }
                }

                AddAppState.Searching -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }

                is AddAppState.Found -> {
                    if (state.wasCorrected) {
                        Text(
                            text = "Did you mean this?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppIcon(iconUrl = state.result.iconUrl, size = 48.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.result.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.result.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onSearch, modifier = Modifier.weight(1f)) {
                            Text("Search again")
                        }
                        Button(onClick = onConfirmAdd, modifier = Modifier.weight(1f)) {
                            Text("Add")
                        }
                    }
                }

                AddAppState.NotFound -> {
                    ErrorMessage("No Play Store results for \"$query\". Check the spelling and try again.")
                }

                is AddAppState.Duplicate -> {
                    ErrorMessage("${state.appName} is already on your list.")
                }

                is AddAppState.Error -> {
                    ErrorMessage("Couldn't reach the Play Store: ${state.message}")
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onSearch) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun ErrorMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}
