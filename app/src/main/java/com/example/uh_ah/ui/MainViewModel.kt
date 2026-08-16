package com.example.uh_ah.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uh_ah.data.AppEntity
import com.example.uh_ah.data.AppRepository
import com.example.uh_ah.data.DuplicateAppException
import com.example.uh_ah.device.AccessibilityHelper
import com.example.uh_ah.device.DeviceAdminHelper
import com.example.uh_ah.device.DeviceOwnerHelper
import com.example.uh_ah.playstore.PlayStoreSearchClient
import com.example.uh_ah.playstore.PlayStoreSearchResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The five hardcoded confirmation questions, in order. */
val SURVEY_QUESTIONS = listOf(
    "Are you sure you want to proceed?",
    "This action cannot be easily reversed. Continue?",
    "Do you understand the consequences?",
    "Have you backed up your data?",
    "Are you absolutely certain?"
)

/** What a toggle-off/delete survey is gating. Master toggle-off has no single app. */
sealed class PendingAction {
    data class ToggleOff(val app: AppEntity) : PendingAction()
    data class Delete(val app: AppEntity) : PendingAction()
    object MasterToggleOff : PendingAction()
}

/** Progress through a [PendingAction]'s survey: the 5 yes/no pages, then the "type confirm" page. */
data class SurveyState(
    val action: PendingAction,
    val questionIndex: Int = 0,
    val awaitingConfirmText: Boolean = false,
    val confirmText: String = ""
)

sealed class AddAppState {
    object Idle : AddAppState()
    object Searching : AddAppState()
    data class Found(val result: com.example.uh_ah.playstore.PlayStoreResult, val wasCorrected: Boolean) : AddAppState()
    object NotFound : AddAppState()
    data class Duplicate(val appName: String) : AddAppState()
    data class Error(val message: String) : AddAppState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository.getInstance(application)

    val apps: StateFlow<List<AppEntity>> = repo.apps.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val masterEnabled: StateFlow<Boolean> = repo.masterEnabled

    private val _isDeviceAdminActive = MutableStateFlow(DeviceAdminHelper.isAdminActive(application))
    val isDeviceAdminActive: StateFlow<Boolean> = _isDeviceAdminActive

    private val _isDeviceOwner = MutableStateFlow(DeviceOwnerHelper.isDeviceOwner(application))
    val isDeviceOwner: StateFlow<Boolean> = _isDeviceOwner

    private val _isAccessibilityServiceActive =
        MutableStateFlow(AccessibilityHelper.isServiceEnabled(application))
    val isAccessibilityServiceActive: StateFlow<Boolean> = _isAccessibilityServiceActive

    /** Call from onResume: device admin / owner / accessibility state all change in Settings, not through this app. */
    fun refreshDeviceAdminStatus() {
        _isDeviceAdminActive.value = DeviceAdminHelper.isAdminActive(getApplication())
        _isDeviceOwner.value = DeviceOwnerHelper.isDeviceOwner(getApplication())
        _isAccessibilityServiceActive.value = AccessibilityHelper.isServiceEnabled(getApplication())
    }

    fun hasSeenDeviceAdminPrompt(): Boolean = repo.hasSeenDeviceAdminPrompt

    fun markDeviceAdminPromptSeen() {
        repo.hasSeenDeviceAdminPrompt = true
    }

    private val _showAddAppSheet = MutableStateFlow(false)
    val showAddAppSheet: StateFlow<Boolean> = _showAddAppSheet

    private val _addAppQuery = MutableStateFlow("")
    val addAppQuery: StateFlow<String> = _addAppQuery

    private val _addAppState = MutableStateFlow<AddAppState>(AddAppState.Idle)
    val addAppState: StateFlow<AddAppState> = _addAppState

    private val _surveyState = MutableStateFlow<SurveyState?>(null)
    val surveyState: StateFlow<SurveyState?> = _surveyState

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ---- Master toggle: same asymmetry as per-app — ON needs no survey, OFF does ----

    fun setMasterEnabled(enabled: Boolean) {
        if (enabled) {
            repo.setMasterEnabled(true)
        } else {
            startSurvey(PendingAction.MasterToggleOff)
        }
    }

    // ---- Per-app toggle / delete: ON needs no survey, OFF and delete do ----

    fun onAppToggleRequested(app: AppEntity, newValue: Boolean) {
        if (newValue) {
            viewModelScope.launch { repo.setAppEnabled(app.packageName, true) }
        } else {
            startSurvey(PendingAction.ToggleOff(app))
        }
    }

    fun onDeleteRequested(app: AppEntity) {
        startSurvey(PendingAction.Delete(app))
    }

    // ---- Survey state machine ----

    private fun startSurvey(action: PendingAction) {
        _surveyState.value = SurveyState(action = action)
    }

    /** Any "No" cancels immediately without applying the toggle/delete. */
    fun answerSurveyQuestion(yes: Boolean) {
        val current = _surveyState.value ?: return
        if (!yes) {
            _surveyState.value = null
            emitEvent("Cancelled")
            return
        }
        val nextIndex = current.questionIndex + 1
        _surveyState.value = if (nextIndex >= SURVEY_QUESTIONS.size) {
            current.copy(awaitingConfirmText = true)
        } else {
            current.copy(questionIndex = nextIndex)
        }
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
                is PendingAction.ToggleOff -> {
                    repo.setAppEnabled(action.app.packageName, false)
                    emitEvent("${action.app.appName} turned off")
                }
                is PendingAction.Delete -> {
                    repo.deleteApp(action.app)
                    emitEvent("${action.app.appName} removed from the list")
                }
                PendingAction.MasterToggleOff -> {
                    repo.setMasterEnabled(false)
                    emitEvent("Protection turned off")
                }
            }
            _surveyState.value = null
        }
    }

    /** If the user backs out or the app is killed mid-survey, nothing was ever committed — safe to just drop it. */
    fun cancelSurvey() {
        _surveyState.value = null
    }

    // ---- Add app flow ----

    fun openAddAppSheet() {
        _addAppQuery.value = ""
        _addAppState.value = AddAppState.Idle
        _showAddAppSheet.value = true
    }

    fun dismissAddAppSheet() {
        _showAddAppSheet.value = false
    }

    fun updateAddAppQuery(text: String) {
        _addAppQuery.value = text
        _addAppState.value = AddAppState.Idle
    }

    fun searchPlayStore() {
        val query = _addAppQuery.value.trim()
        if (query.isEmpty()) return
        _addAppState.value = AddAppState.Searching
        viewModelScope.launch {
            when (val result = PlayStoreSearchClient.search(query)) {
                is PlayStoreSearchResult.Found -> {
                    _addAppState.value = if (repo.findByPackageName(result.result.packageName) != null) {
                        AddAppState.Duplicate(result.result.title)
                    } else {
                        AddAppState.Found(result.result, result.wasCorrected)
                    }
                }
                PlayStoreSearchResult.NotFound -> _addAppState.value = AddAppState.NotFound
                is PlayStoreSearchResult.Error -> _addAppState.value = AddAppState.Error(result.message)
            }
        }
    }

    fun confirmAddApp() {
        val found = _addAppState.value as? AddAppState.Found ?: return
        viewModelScope.launch {
            try {
                repo.addApp(found.result.packageName, found.result.title, found.result.iconUrl)
                emitEvent("${found.result.title} added to the list")
                _showAddAppSheet.value = false
            } catch (e: DuplicateAppException) {
                _addAppState.value = AddAppState.Duplicate(e.appName)
            }
        }
    }

    private fun emitEvent(message: String) {
        _events.trySend(message)
    }
}
