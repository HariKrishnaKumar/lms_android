package com.exam.lms.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exam.lms.data.remote.dto.*
import com.exam.lms.data.repo.LmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

data class StudentUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val activeTests: List<TestDto> = emptyList(),
    val currentTest: TestDto? = null,
    val activeSubmission: SubmissionDto? = null,
    val userAnswers: Map<Int, String> = emptyMap(), // questionId -> answer string
    val remainingSeconds: Long = 0,
    val tabSwitchCount: Int = 0,
    val showWarningBanner: Boolean = false,
    val autosaveStatus: String = "Saved", // "Saving...", "Saved", "Unable to save"
    val isSubmitted: Boolean = false,
    val submitResult: SubmitResponse? = null,
    val analytics: StudentAnalyticsDto? = null,
    val profile: UserSession? = null
)

@HiltViewModel
class StudentViewModel @Inject constructor(
    private val repository: LmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentUiState())
    val uiState: StateFlow<StudentUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var autosaveDebounceJob: Job? = null

    fun loadStudentData() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val testsRes = repository.getTests(status = "active")
                val analyticsRes = repository.getStudentAnalytics()
                val sessionRes = repository.getSession()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeTests = testsRes.data ?: emptyList(),
                        analytics = analyticsRes.data,
                        profile = sessionRes.data
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load dashboard") }
            }
        }
    }

    fun startTest(testId: Int) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val testDetails = repository.getTestDetails(testId).data
                val submissionRes = repository.startSubmission(testId).data

                if (testDetails != null && submissionRes != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentTest = testDetails,
                            activeSubmission = submissionRes,
                            remainingSeconds = submissionRes.remainingSeconds,
                            tabSwitchCount = 0,
                            isSubmitted = false
                        )
                    }
                    startTimer()
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to start test") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Error starting test") }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingSeconds > 0 && !_uiState.value.isSubmitted) {
                delay(1000)
                _uiState.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
            }
            if (_uiState.value.remainingSeconds <= 0 && !_uiState.value.isSubmitted) {
                // Auto-submit on timer expiry (Rule T10)
                submitTest()
            }
        }
    }

    fun onAnswerChanged(questionId: Int, answer: String) {
        val updatedAnswers = _uiState.value.userAnswers.toMutableMap()
        updatedAnswers[questionId] = answer
        _uiState.update { it.copy(userAnswers = updatedAnswers, autosaveStatus = "Saving...") }

        // Autosave queue §12: 2s debounce
        autosaveDebounceJob?.cancel()
        autosaveDebounceJob = viewModelScope.launch {
            delay(2000)
            performAutosave(questionId, answer)
        }
    }

    private suspend fun performAutosave(questionId: Int, answer: String) {
        val submissionId = _uiState.value.activeSubmission?.id ?: return
        val jsonPayload = mapOf(questionId.toString() to JsonPrimitive(answer))

        var attempts = 0
        var saved = false
        while (attempts < 3 && !saved) {
            try {
                val res = repository.saveAnswers(submissionId, jsonPayload, autoSave = true)
                if (res.data != null) {
                    saved = true
                    _uiState.update { it.copy(autosaveStatus = "Saved") }
                } else {
                    attempts++
                    delay(1000L * attempts)
                }
            } catch (e: Exception) {
                attempts++
                delay(1000L * attempts)
            }
        }

        if (!saved) {
            _uiState.update { it.copy(autosaveStatus = "Unable to save") }
        }
    }

    fun recordTabSwitch() {
        val submissionId = _uiState.value.activeSubmission?.id ?: return
        val newCount = _uiState.value.tabSwitchCount + 1

        _uiState.update {
            it.copy(
                tabSwitchCount = newCount,
                showWarningBanner = true
            )
        }

        viewModelScope.launch {
            try {
                repository.recordTabSwitch(submissionId, 1)
            } catch (_: Exception) {}

            delay(3000)
            _uiState.update { it.copy(showWarningBanner = false) }
        }
    }

    fun submitTest() {
        val submissionId = _uiState.value.activeSubmission?.id ?: return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val res = repository.submitTest(submissionId).data
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSubmitted = true,
                        submitResult = res
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Submit failed. Retrying...") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        autosaveDebounceJob?.cancel()
    }
}
