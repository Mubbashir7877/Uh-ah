package com.example.uh_ah.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uh_ah.data.DuplicateProtectedAppException
import com.example.uh_ah.data.InstallProtectionRepository
import com.example.uh_ah.data.ProtectedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val appName: String)

sealed class InstallPendingAction {
    data class ToggleOff(val app: ProtectedAppEntity) : InstallPendingAction()
    data class Delete(val app: ProtectedAppEntity) : InstallPendingAction()
    object MasterToggleOff : InstallPendingAction()
}

data class InstallSurveyState(
    val action: InstallPendingAction,
    val questionIndex: Int = 0,
    val awaitingConfirmText: Boolean = false,
    val confirmText: String = ""
)

class InstallProtectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = InstallProtectionRepository.getInstance(application)

    val apps: StateFlow<List<ProtectedAppEntity>> = repo.apps.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val masterEnabled: StateFlow<Boolean> = repo.masterEnabled

    private val _showAddSheet = MutableStateFlow(false)
    val showAddSheet: StateFlow<Boolean> = _showAddSheet
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    val filteredInstalledApps: StateFlow<List<InstalledApp>> = combine(_installedApps, _query) { apps, query ->
        if (query.isBlank()) apps else apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _surveyState = MutableStateFlow<InstallSurveyState?>(null)
    val surveyState: StateFlow<InstallSurveyState?> = _surveyState
    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun openAddSheet() {
        _query.value = ""
        _showAddSheet.value = true
        scanInstalledApps()
    }

    fun dismissAddSheet() { _showAddSheet.value = false }
    fun updateQuery(value: String) { _query.value = value }

    /** Reads launcher-visible packages from the device rather than searching an online catalog. */
    fun scanInstalledApps() {
        viewModelScope.launch {
            _isScanning.value = true
            _installedApps.value = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                    .asSequence()
                    .filter { it.packageName != getApplication<Application>().packageName }
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                    .sortedBy { it.appName.lowercase() }
                    .toList()
            }
            _isScanning.value = false
        }
    }

    fun addApp(app: InstalledApp) {
        viewModelScope.launch {
            try {
                repo.addApp(app.packageName, app.appName)
                _showAddSheet.value = false
                emitEvent("${app.appName} added to the keep-installed list")
            } catch (_: DuplicateProtectedAppException) {
                emitEvent("${app.appName} is already protected")
            }
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        if (enabled) repo.setMasterEnabled(true)
        else _surveyState.value = InstallSurveyState(InstallPendingAction.MasterToggleOff)
    }

    fun onAppToggleRequested(app: ProtectedAppEntity, enabled: Boolean) {
        if (enabled) viewModelScope.launch { repo.setAppEnabled(app.packageName, true) }
        else _surveyState.value = InstallSurveyState(InstallPendingAction.ToggleOff(app))
    }

    fun onDeleteRequested(app: ProtectedAppEntity) {
        _surveyState.value = InstallSurveyState(InstallPendingAction.Delete(app))
    }

    fun answerSurveyQuestion(yes: Boolean) {
        val current = _surveyState.value ?: return
        if (!yes) {
            _surveyState.value = null
            emitEvent("Cancelled")
            return
        }
        val next = current.questionIndex + 1
        _surveyState.value = if (next >= SURVEY_QUESTIONS.size) {
            current.copy(awaitingConfirmText = true)
        } else current.copy(questionIndex = next)
    }

    fun updateConfirmText(text: String) {
        _surveyState.value = _surveyState.value?.copy(confirmText = text)
    }

    fun submitConfirmText() {
        val current = _surveyState.value ?: return
        if (!current.confirmText.trim().equals("confirm", ignoreCase = true)) {
            emitEvent("Type \"confirm\" exactly to proceed")
            return
        }
        viewModelScope.launch {
            when (val action = current.action) {
                is InstallPendingAction.ToggleOff -> repo.setAppEnabled(action.app.packageName, false)
                is InstallPendingAction.Delete -> repo.deleteApp(action.app)
                InstallPendingAction.MasterToggleOff -> repo.setMasterEnabled(false)
            }
            emitEvent("Keep-installed protection updated")
            _surveyState.value = null
        }
    }

    fun cancelSurvey() { _surveyState.value = null }
    private fun emitEvent(message: String) { _events.trySend(message) }
}
