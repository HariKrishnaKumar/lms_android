package com.exam.lms.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exam.lms.data.remote.dto.*
import com.exam.lms.data.repo.LmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val isLoading: Boolean = false,
    val stats: AdminStatsDto? = null,
    val colleges: List<CollegeDto> = emptyList(),
    val courses: List<CourseDto> = emptyList(),
    val batches: List<BatchDto> = emptyList(),
    val students: List<StudentDto> = emptyList(),
    val unverifiedStudents: List<UnverifiedStudentDto> = emptyList(),
    val tests: List<TestDto> = emptyList(),
    val notifications: List<NotificationDto> = emptyList(),
    val failedLogins: List<FailedLoginDto> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: LmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun loadAdminDashboard() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val statsRes = repository.getAdminStats()
                val collegesRes = repository.getColleges()
                val testsRes = repository.getTests()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        stats = statsRes.data,
                        colleges = collegesRes.data ?: emptyList(),
                        tests = testsRes.data ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load admin stats") }
            }
        }
    }

    fun loadColleges() {
        viewModelScope.launch {
            val res = repository.getColleges()
            _uiState.update { it.copy(colleges = res.data ?: emptyList()) }
        }
    }

    fun createCollege(name: String, code: String, city: String) {
        viewModelScope.launch {
            repository.createCollege(CollegeDto(id = 0, name = name, code = code, city = city))
            loadColleges()
        }
    }

    fun archiveCollege(id: Int) {
        viewModelScope.launch {
            repository.archiveCollege(id)
            loadColleges()
        }
    }

    fun permanentDeleteCollege(id: Int) {
        viewModelScope.launch {
            repository.permanentDeleteCollege(id)
            loadColleges()
        }
    }

    fun loadCourses() {
        viewModelScope.launch {
            val res = repository.getCourses()
            _uiState.update { it.copy(courses = res.data ?: emptyList()) }
        }
    }

    fun loadBatches() {
        viewModelScope.launch {
            val res = repository.getBatches()
            _uiState.update { it.copy(batches = res.data ?: emptyList()) }
        }
    }

    fun promoteBatch(batchId: Int) {
        viewModelScope.launch {
            repository.promoteBatch(batchId, preview = false)
            loadBatches()
        }
    }

    fun loadStudents() {
        viewModelScope.launch {
            val res = repository.getStudents()
            val unverifiedRes = repository.getUnverifiedStudents()
            _uiState.update {
                it.copy(
                    students = res.data ?: emptyList(),
                    unverifiedStudents = unverifiedRes.data ?: emptyList()
                )
            }
        }
    }

    fun createTest(title: String, duration: Int) {
        viewModelScope.launch {
            repository.createTest(
                TestCreateRequest(
                    title = title,
                    testType = "mcq",
                    durationMinutes = duration
                )
            )
            loadAdminDashboard()
        }
    }

    fun loadFailedLogins() {
        viewModelScope.launch {
            val res = repository.getFailedLogins()
            _uiState.update { it.copy(failedLogins = res.data ?: emptyList()) }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onComplete()
        }
    }
}
