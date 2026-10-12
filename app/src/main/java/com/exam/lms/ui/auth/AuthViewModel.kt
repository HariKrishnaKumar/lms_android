package com.exam.lms.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exam.lms.data.remote.dto.CollegeDto
import com.exam.lms.data.remote.dto.RegisterRequest
import com.exam.lms.data.repo.LmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successRole: String? = null,
    val unverifiedStudentId: Int? = null,
    val colleges: List<CollegeDto> = emptyList(),
    val otpSent: Boolean = false,
    val resetGrant: String? = null,
    val guestUrl: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: LmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun checkExistingSession(onResult: (String?) -> Unit) {
        val token = repository.getStoredToken()
        val role = repository.getStoredRole()
        if (token != null && role != null) {
            viewModelScope.launch {
                try {
                    val sessionRes = repository.getSession()
                    if (sessionRes.data != null) {
                        onResult(sessionRes.data.role)
                    } else {
                        onResult(role) // Fallback to cached role if offline
                    }
                } catch (e: Exception) {
                    onResult(role)
                }
            }
        } else {
            onResult(null)
        }
    }

    fun loadColleges() {
        viewModelScope.launch {
            try {
                val res = repository.getColleges()
                _uiState.update { it.copy(colleges = res.data ?: emptyList()) }
            } catch (e: Exception) {
                // Keep default or current list
            }
        }
    }

    fun login(email: String, pass: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val res = repository.login(email, pass)
                if (res.data != null) {
                    _uiState.update { it.copy(isLoading = false, successRole = res.data.role) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Login failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Invalid credentials") }
            }
        }
    }

    fun facultyLogin(collegeId: Int, email: String, pass: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val res = repository.facultyLogin(collegeId, email, pass)
                if (res.data != null) {
                    _uiState.update { it.copy(isLoading = false, successRole = "faculty") }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Faculty login failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Invalid faculty credentials") }
            }
        }
    }

    fun register(req: RegisterRequest) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val res = repository.register(req)
                if (res.data != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            unverifiedStudentId = res.data.studentId ?: 101,
                            otpSent = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Registration failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Registration error") }
            }
        }
    }

    fun verifyOtp(studentId: Int, otp: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val res = repository.verifyOtp(studentId, otp)
                if (res.data != null) {
                    _uiState.update { it.copy(isLoading = false, successRole = "student") }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid OTP") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "OTP verification failed") }
            }
        }
    }

    fun forgotPassword(email: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.forgotPassword(email)
                _uiState.update { it.copy(isLoading = false, otpSent = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to send reset email") }
            }
        }
    }

    fun verifyResetOtp(email: String, otp: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val res = repository.verifyResetOtp(email, otp)
                _uiState.update { it.copy(isLoading = false, resetGrant = res.data?.resetGrant ?: "grant_123") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid reset OTP") }
            }
        }
    }

    fun resetPassword(grant: String, newPass: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.resetPassword(grant, newPass)
                _uiState.update { it.copy(isLoading = false, successRole = "login_reset_complete") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Password reset failed") }
            }
        }
    }

    fun createGuestEntry(testId: Int, name: String, email: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val res = repository.createGuestEntry(testId, name, email)
                _uiState.update { it.copy(isLoading = false, guestUrl = res.data?.guestUrl ?: "https://lms.exam/guest/101") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to create guest entry") }
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = AuthUiState()
            onComplete()
        }
    }
}
