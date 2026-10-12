package com.exam.lms

import com.exam.lms.data.remote.ApiService
import com.exam.lms.data.remote.dto.*
import com.exam.lms.data.repo.LmsRepository
import com.exam.lms.data.token.TokenStore
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthBusinessRulesTest {

    private val apiService = mockk<ApiService>()
    private val tokenStore = mockk<TokenStore>(relaxed = true)
    private lateinit var repository: LmsRepository

    @Before
    fun setup() {
        repository = LmsRepository(apiService, tokenStore)
    }

    @Test
    fun `Rule A2 - Faculty login passes college_id credential hint`() = runTest {
        coEvery { apiService.facultyLogin(any()) } returns ApiResponse(
            data = AuthTokenData("access_123", "refresh_123", "faculty")
        )

        val response = repository.facultyLogin(collegeId = 1, email = "prof@univ.edu", pass = "pass123")

        assertNotNull(response.data)
        assertEquals("faculty", response.data?.role)
        coVerify {
            apiService.facultyLogin(match {
                it.collegeId == 1 && it.email == "prof@univ.edu"
            })
        }
        verify { tokenStore.saveTokens("access_123", "refresh_123", "faculty", null) }
    }

    @Test
    fun `Rule A4 - Signup creates student and requires OTP verification before auth`() = runTest {
        val registerReq = RegisterRequest(
            name = "John Doe",
            email = "john@univ.edu",
            phone = "1234567890",
            gender = "Male",
            collegeId = 1,
            courseId = 10,
            batchId = 100,
            sectionId = 1001,
            rollNumber = "CSE2301",
            yearOfJoining = 2023,
            password = "password123"
        )

        coEvery { apiService.register(registerReq) } returns ApiResponse(
            data = AuthTokenData("", "", "unverified", studentId = 505)
        )

        val regResponse = repository.register(registerReq)
        assertEquals(505, regResponse.data?.studentId)

        coEvery { apiService.verifyOtp(VerifyOtpRequest(505, "123456")) } returns ApiResponse(
            data = AuthTokenData("verified_access", "verified_refresh", "student")
        )

        val otpResponse = repository.verifyOtp(505, "123456")
        assertEquals("student", otpResponse.data?.role)
        verify { tokenStore.saveTokens("verified_access", "verified_refresh", "student", null) }
    }

    @Test
    fun `Rule A6 - Resetting password clears existing tokens`() = runTest {
        coEvery { apiService.resetPassword(any()) } returns ApiResponse(
            data = mapOf("message" to "Password reset successful")
        )

        repository.resetPassword("grant_abc123", "newPassword123")

        coVerify {
            apiService.resetPassword(ResetPasswordRequest("grant_abc123", "newPassword123"))
        }
    }
}
