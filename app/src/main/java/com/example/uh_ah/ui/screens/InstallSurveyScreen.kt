package com.example.uh_ah.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uh_ah.ui.InstallPendingAction
import com.example.uh_ah.ui.InstallSurveyState
import com.example.uh_ah.ui.SURVEY_QUESTIONS
import com.example.uh_ah.ui.components.rememberQuestionNarrationComplete

/** Same deliberate confirmation flow used by the remove-installs engine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallSurveyScreen(
    state: InstallSurveyState,
    onAnswer: (Boolean) -> Unit,
    onConfirmTextChange: (String) -> Unit,
    onSubmitConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val totalPages = SURVEY_QUESTIONS.size + 1
    val currentPage = if (state.awaitingConfirmText) SURVEY_QUESTIONS.size else state.questionIndex
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(actionLabel(state.action)) },
            navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("Step ${currentPage + 1} of $totalPages", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (currentPage + 1).toFloat() / totalPages },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Spacer(Modifier.height(32.dp))
            if (state.awaitingConfirmText) {
                Text("Type \"confirm\" to proceed", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("This will apply to ${targetLabel(state.action)}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(state.confirmText, onConfirmTextChange, label = { Text("Confirmation") },
                    placeholder = { Text("confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
                Button(onClick = onSubmitConfirm, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Submit")
                }
            } else {
                val narrationComplete = rememberQuestionNarrationComplete(
                    SURVEY_QUESTIONS[state.questionIndex]
                )
                Text(SURVEY_QUESTIONS[state.questionIndex], style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(40.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f)) { Text("No") }
                    Button(
                        onClick = { onAnswer(true) },
                        enabled = narrationComplete,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (narrationComplete) "Yes" else "Listening…") }
                }
            }
        }
    }
}

private fun actionLabel(action: InstallPendingAction) = when (action) {
    is InstallPendingAction.ToggleOff -> "Turn off ${action.app.appName}"
    is InstallPendingAction.Delete -> "Delete ${action.app.appName}"
    InstallPendingAction.MasterToggleOff -> "Turn off install protection"
}

private fun targetLabel(action: InstallPendingAction) = when (action) {
    is InstallPendingAction.ToggleOff -> action.app.appName
    is InstallPendingAction.Delete -> action.app.appName
    InstallPendingAction.MasterToggleOff -> "all apps on your keep-installed list"
}
