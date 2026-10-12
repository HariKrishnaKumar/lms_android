package com.exam.lms.data.remote

import com.exam.lms.data.remote.dto.*
import retrofit2.http.*

interface ApiService {

    // --- Auth Endpoints ---

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthTokenData>

    @POST("auth/faculty-login")
    suspend fun facultyLogin(@Body request: FacultyLoginRequest): ApiResponse<AuthTokenData>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<AuthTokenData>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): ApiResponse<AuthTokenData>

    @POST("auth/resend-otp")
    suspend fun resendOtp(@Body request: ResendOtpRequest): ApiResponse<Map<String, String>>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): ApiResponse<Map<String, String>>

    @POST("auth/verify-reset-otp")
    suspend fun verifyResetOtp(@Body request: VerifyResetOtpRequest): ApiResponse<AuthTokenData>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): ApiResponse<Map<String, String>>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiResponse<AuthTokenData>

    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Map<String, String>>

    @GET("auth/session")
    suspend fun getSession(): ApiResponse<UserSession>

    // --- Hierarchy Endpoints ---

    @GET("colleges")
    suspend fun getColleges(
        @Query("status") status: String? = null,
        @Query("has_faculty") hasFaculty: Int? = null
    ): ApiResponse<List<CollegeDto>>

    @POST("colleges")
    suspend fun createCollege(@Body college: CollegeDto): ApiResponse<CollegeDto>

    @GET("colleges/{id}")
    suspend fun getCollegeDetails(@Path("id") id: Int): ApiResponse<CollegeDto>

    @DELETE("colleges/{id}")
    suspend fun archiveCollege(@Path("id") id: Int): ApiResponse<Map<String, String>>

    @DELETE("colleges/{id}/permanent")
    suspend fun permanentDeleteCollege(@Path("id") id: Int): ApiResponse<Map<String, String>>

    @GET("courses")
    suspend fun getCourses(
        @Query("college_id") collegeId: Int? = null,
        @Query("status") status: String? = null
    ): ApiResponse<List<CourseDto>>

    @POST("courses")
    suspend fun createCourse(@Body course: CourseDto): ApiResponse<CourseDto>

    @DELETE("courses/{id}")
    suspend fun archiveCourse(@Path("id") id: Int): ApiResponse<Map<String, String>>

    @GET("batches")
    suspend fun getBatches(
        @Query("course_id") courseId: Int? = null,
        @Query("active") active: Int? = null,
        @Query("status") status: String? = null
    ): ApiResponse<List<BatchDto>>

    @POST("batches")
    suspend fun createBatch(@Body batch: BatchDto): ApiResponse<BatchDto>

    @POST("batches/{id}/promote")
    suspend fun promoteBatch(
        @Path("id") id: Int,
        @Body request: PromoteStudentsRequest
    ): ApiResponse<PromotePreviewResponse>

    @GET("sections")
    suspend fun getSections(@Query("batch_id") batchId: Int? = null): ApiResponse<List<SectionDto>>

    @GET("geo/cities")
    suspend fun getGeoCities(): ApiResponse<List<String>>

    // --- Students & Guest Links ---

    @GET("students")
    suspend fun getStudents(
        @Query("college_id") collegeId: Int? = null,
        @Query("course_id") courseId: Int? = null,
        @Query("batch_id") batchId: Int? = null,
        @Query("section_id") sectionId: Int? = null
    ): ApiResponse<List<StudentDto>>

    @GET("unverified-students")
    suspend fun getUnverifiedStudents(): ApiResponse<List<UnverifiedStudentDto>>

    @POST("guests")
    suspend fun createGuestEntry(@Body request: GuestEntryRequest): ApiResponse<GuestEntryDto>

    @GET("guests/{id}")
    suspend fun getGuestEntry(@Path("id") id: String): ApiResponse<GuestEntryDto>

    // --- Tests & Questions ---

    @GET("tests")
    suspend fun getTests(
        @Query("status") status: String? = null,
        @Query("test_type") testType: String? = null
    ): ApiResponse<List<TestDto>>

    @POST("tests")
    suspend fun createTest(@Body request: TestCreateRequest): ApiResponse<TestDto>

    @GET("tests/{id}")
    suspend fun getTestDetails(@Path("id") id: Int): ApiResponse<TestDto>

    @POST("tests/{id}/publish")
    suspend fun publishTest(@Path("id") id: Int): ApiResponse<TestDto>

    @POST("tests/{id}/pause")
    suspend fun pauseTest(@Path("id") id: Int): ApiResponse<TestDto>

    @POST("tests/{id}/resume")
    suspend fun resumeTest(@Path("id") id: Int): ApiResponse<TestDto>

    @POST("tests/{id}/questions")
    suspend fun addQuestion(
        @Path("id") testId: Int,
        @Body question: QuestionDto
    ): ApiResponse<QuestionDto>

    // --- Submissions, Answers, Grading, Anti-Cheat ---

    @POST("submissions")
    suspend fun startSubmission(@Body body: Map<String, Int>): ApiResponse<SubmissionDto>

    @GET("submissions/{id}")
    suspend fun getSubmission(@Path("id") id: String): ApiResponse<SubmissionDto>

    @POST("submissions/{id}/answers")
    suspend fun saveAnswers(
        @Path("id") submissionId: String,
        @Body request: SaveAnswersRequest
    ): ApiResponse<SubmissionDto>

    @POST("submissions/{id}/submit")
    suspend fun submitTest(@Path("id") submissionId: String): ApiResponse<SubmitResponse>

    @POST("tab-switches")
    suspend fun recordTabSwitch(@Body request: TabSwitchRequest): ApiResponse<TabSwitchLogDto>

    @GET("pci-records")
    suspend fun getPciRecords(): ApiResponse<List<PciRecordDto>>

    // --- Analytics & Charts ---

    @GET("student/analytics")
    suspend fun getStudentAnalytics(): ApiResponse<StudentAnalyticsDto>

    @GET("faculty/overview")
    suspend fun getFacultyOverview(): ApiResponse<FacultyOverviewDto>

    @GET("admin/stats")
    suspend fun getAdminStats(): ApiResponse<AdminStatsDto>

    @GET("tests/{id}/results")
    suspend fun getTestResults(@Path("id") id: Int): ApiResponse<TestResultsDto>

    // --- Notifications, Audit, Failed Logins ---

    @GET("notifications")
    suspend fun getNotifications(): ApiResponse<List<NotificationDto>>

    @GET("audit-logs")
    suspend fun getAuditLogs(): ApiResponse<List<AuditLogDto>>

    @GET("failed-logins")
    suspend fun getFailedLogins(): ApiResponse<List<FailedLoginDto>>
}
