package com.exam.lms.data.repo

import com.exam.lms.data.remote.ApiService
import com.exam.lms.data.remote.dto.*
import com.exam.lms.data.token.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LmsRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenStore: TokenStore
) {
    fun getStoredToken(): String? = tokenStore.getAccessToken()
    fun getStoredRole(): String? = tokenStore.getRole()
    fun clearStoredToken() = tokenStore.clear()

    suspend fun login(email: String, pass: String): ApiResponse<AuthTokenData> {
        val res = apiService.login(LoginRequest(email, pass))
        res.data?.let {
            tokenStore.saveTokens(it.accessToken, it.refreshToken, it.role, it.subRole)
        }
        return res
    }

    suspend fun facultyLogin(collegeId: Int, email: String, pass: String): ApiResponse<AuthTokenData> {
        val res = apiService.facultyLogin(FacultyLoginRequest(collegeId, email, pass))
        res.data?.let {
            tokenStore.saveTokens(it.accessToken, it.refreshToken, it.role, it.subRole)
        }
        return res
    }

    suspend fun register(req: RegisterRequest): ApiResponse<AuthTokenData> = apiService.register(req)

    suspend fun verifyOtp(studentId: Int, otp: String): ApiResponse<AuthTokenData> {
        val res = apiService.verifyOtp(VerifyOtpRequest(studentId, otp))
        res.data?.let {
            tokenStore.saveTokens(it.accessToken, it.refreshToken, it.role, it.subRole)
        }
        return res
    }

    suspend fun resendOtp(studentId: Int) = apiService.resendOtp(ResendOtpRequest(studentId))
    suspend fun forgotPassword(email: String) = apiService.forgotPassword(ForgotPasswordRequest(email))
    suspend fun verifyResetOtp(email: String, otp: String) = apiService.verifyResetOtp(VerifyResetOtpRequest(email, otp))
    suspend fun resetPassword(grant: String, newPass: String) = apiService.resetPassword(ResetPasswordRequest(grant, newPass))
    suspend fun logout() {
        try { apiService.logout() } catch (_: Exception) {}
        tokenStore.clear()
    }

    suspend fun getSession(): ApiResponse<UserSession> = apiService.getSession()

    // Hierarchy
    suspend fun getColleges(status: String? = null, hasFaculty: Int? = null) = apiService.getColleges(status, hasFaculty)
    suspend fun createCollege(college: CollegeDto) = apiService.createCollege(college)
    suspend fun getCollegeDetails(id: Int) = apiService.getCollegeDetails(id)
    suspend fun archiveCollege(id: Int) = apiService.archiveCollege(id)
    suspend fun permanentDeleteCollege(id: Int) = apiService.permanentDeleteCollege(id)
    suspend fun getCourses(collegeId: Int? = null) = apiService.getCourses(collegeId)
    suspend fun createCourse(course: CourseDto) = apiService.createCourse(course)
    suspend fun getBatches(courseId: Int? = null) = apiService.getBatches(courseId)
    suspend fun createBatch(batch: BatchDto) = apiService.createBatch(batch)
    suspend fun promoteBatch(batchId: Int, preview: Boolean) = apiService.promoteBatch(batchId, PromoteStudentsRequest(batchId, preview))
    suspend fun getSections(batchId: Int? = null) = apiService.getSections(batchId)

    // Students & Guests
    suspend fun getStudents(collegeId: Int? = null, courseId: Int? = null, batchId: Int? = null, sectionId: Int? = null) =
        apiService.getStudents(collegeId, courseId, batchId, sectionId)
    suspend fun getUnverifiedStudents() = apiService.getUnverifiedStudents()
    suspend fun createGuestEntry(testId: Int, name: String, email: String) =
        apiService.createGuestEntry(GuestEntryRequest(testId, name, email))

    // Tests & Submissions
    suspend fun getTests(status: String? = null) = apiService.getTests(status)
    suspend fun createTest(req: TestCreateRequest) = apiService.createTest(req)
    suspend fun getTestDetails(id: Int) = apiService.getTestDetails(id)
    suspend fun publishTest(id: Int) = apiService.publishTest(id)
    suspend fun startSubmission(testId: Int) = apiService.startSubmission(mapOf("test_id" to testId))
    suspend fun getSubmission(id: String) = apiService.getSubmission(id)
    suspend fun saveAnswers(id: String, answers: Map<String, kotlinx.serialization.json.JsonElement>, autoSave: Boolean = true) =
        apiService.saveAnswers(id, SaveAnswersRequest(answers, autoSave))
    suspend fun submitTest(id: String) = apiService.submitTest(id)
    suspend fun recordTabSwitch(submissionId: String, count: Int = 1) =
        apiService.recordTabSwitch(TabSwitchRequest(submissionId, count))

    // Analytics & Notifications
    suspend fun getStudentAnalytics() = apiService.getStudentAnalytics()
    suspend fun getFacultyOverview() = apiService.getFacultyOverview()
    suspend fun getAdminStats() = apiService.getAdminStats()
    suspend fun getTestResults(id: Int) = apiService.getTestResults(id)
    suspend fun getNotifications() = apiService.getNotifications()
    suspend fun getAuditLogs() = apiService.getAuditLogs()
    suspend fun getFailedLogins() = apiService.getFailedLogins()
}
