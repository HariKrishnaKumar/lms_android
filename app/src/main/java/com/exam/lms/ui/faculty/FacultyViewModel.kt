package com.exam.lms.ui.faculty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exam.lms.data.remote.dto.FacultyOverviewDto
import com.exam.lms.data.remote.dto.TestDto
import com.exam.lms.data.repo.LmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FacultyUiState(
    val isLoading: Boolean = false,
    val overview: FacultyOverviewDto? = null,
    val tests: List<TestDto> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class FacultyViewModel @Inject constructor(
    private val repository: LmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FacultyUiState())
    val uiState: StateFlow<FacultyUiState> = _uiState.asStateFlow()

    fun loadFacultyData() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val overviewRes = repository.getFacultyOverview()
                val testsRes = repository.getTests()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        overview = overviewRes.data,
                        tests = testsRes.data ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load faculty dashboard") }
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onComplete()
        }
    }
}
