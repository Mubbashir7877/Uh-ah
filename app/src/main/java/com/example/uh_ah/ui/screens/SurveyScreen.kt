package com.example.uh_ah.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uh_ah.ui.PendingAction
import com.example.uh_ah.ui.SURVEY_QUESTIONS
import com.example.uh_ah.ui.SurveyState

/**
 * Full-screen, visually distinct from the main list on purpose: this is a
 * deliberate speed bump before a toggle-off or delete takes effect. One
 * hardcoded yes/no question per page, then a final "type confirm" page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyScreen(
    state: SurveyState,
    onAnswer: (Boolean) -> Unit,
    onConfirmTextChange: (String) -> Unit,
    onSubmitConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val totalPages = SURVEY_QUESTIONS.size + 1
    val currentPage = if (state.awaitingConfirmText) SURVEY_QUESTIONS.size else state.questionIndex

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(actionLabel(state.action)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                text = "Step ${currentPage + 1} of $totalPages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (currentPage + 1).toFloat() / totalPages },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
            Spacer(Modifier.height(32.dp))

            if (state.awaitingConfirmText) {
                ConfirmTextPage(
                    target = targetLabel(state.action),
                    text = state.confirmText,
                    onTextChange = onConfirmTextChange,
                    onSubmit = onSubmitConfirm
                )
            } else {
                QuestionPage(
                    question = SURVEY_QUESTIONS[state.questionIndex],
                    onAnswer = onAnswer
                )
            }
        }
    }
}

@Composable
private fun QuestionPage(question: String, onAnswer: (Boolean) -> Unit) {
    Text(
        text = question,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(40.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(
            onClick = { onAnswer(false) },
            modifier = Modifier.weight(1f)
        ) { Text("No") }
        Button(
            onClick = { onAnswer(true) },
            modifier = Modifier.weight(1f)
        ) { Text("Yes") }
    }
}

@Composable
private fun ConfirmTextPage(
    target: String,
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Text(
        text = "Type \"confirm\" to proceed",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "This will apply to $target.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text("Confirmation") },
        placeholder = { Text("confirm") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
    ) { Text("Submit") }
}

private fun actionLabel(action: PendingAction): String = when (action) {
    is PendingAction.ToggleOff -> "Turn off ${action.app.appName}"
    is PendingAction.Delete -> "Delete ${action.app.appName}"
    PendingAction.MasterToggleOff -> "Turn off protection"
}

private fun targetLabel(action: PendingAction): String = when (action) {
    is PendingAction.ToggleOff -> action.app.appName
    is PendingAction.Delete -> action.app.appName
    PendingAction.MasterToggleOff -> "all apps on your list"
}
