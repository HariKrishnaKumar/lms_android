package com.exam.lms.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse<T>(
    @SerialName("data") val data: T? = null,
    @SerialName("meta") val meta: ApiMeta? = null
)

@Serializable
data class ApiMeta(
    @SerialName("server_time") val serverTime: String? = null,
    @SerialName("page") val page: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    @SerialName("total") val total: Int? = null,
    @SerialName("python_api_available") val pythonApiAvailable: Boolean? = null
)

@Serializable
data class ApiErrorResponse(
    @SerialName("error") val error: ApiError
)

@Serializable
data class ApiError(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    @SerialName("details") val details: ApiErrorDetails? = null,
    @SerialName("retry_after") val retryAfter: Int? = null
)

@Serializable
data class ApiErrorDetails(
    @SerialName("field") val field: String? = null
)

// --- Auth DTOs ---

@Serializable
data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("role_hint") val roleHint: String? = null
)

@Serializable
data class FacultyLoginRequest(
    @SerialName("college_id") val collegeId: Int,
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null
)

@Serializable
data class RegisterRequest(
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("phone") val phone: String? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("college_id") val collegeId: Int,
    @SerialName("course_id") val courseId: Int,
    @SerialName("batch_id") val batchId: Int,
    @SerialName("section_id") val sectionId: Int,
    @SerialName("roll_number") val rollNumber: String,
    @SerialName("year_of_joining") val yearOfJoining: Int,
    @SerialName("password") val password: String
)

@Serializable
data class VerifyOtpRequest(
    @SerialName("student_id") val studentId: Int,
    @SerialName("otp") val otp: String
)

@Serializable
data class ResendOtpRequest(
    @SerialName("student_id") val studentId: Int
)

@Serializable
data class ForgotPasswordRequest(
    @SerialName("email") val email: String
)

@Serializable
data class VerifyResetOtpRequest(
    @SerialName("email") val email: String,
    @SerialName("otp") val otp: String
)

@Serializable
data class ResetPasswordRequest(
    @SerialName("reset_grant") val resetGrant: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class AuthTokenData(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("role") val role: String,
    @SerialName("sub_role") val subRole: String? = null,
    @SerialName("user") val user: UserSession? = null,
    @SerialName("student_id") val studentId: Int? = null,
    @SerialName("reset_grant") val resetGrant: String? = null
)

@Serializable
data class UserSession(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("role") val role: String,
    @SerialName("sub_role") val subRole: String? = null,
    @SerialName("college_id") val collegeId: Int? = null,
    @SerialName("college_name") val collegeName: String? = null
)

// --- Hierarchy DTOs ---

@Serializable
data class CollegeDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("code") val code: String,
    @SerialName("status") val status: String = "active",
    @SerialName("city") val city: String? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("courses_count") val coursesCount: Int = 0,
    @SerialName("students_count") val studentsCount: Int = 0
)

@Serializable
data class CourseDto(
    @SerialName("id") val id: Int,
    @SerialName("college_id") val collegeId: Int,
    @SerialName("name") val name: String,
    @SerialName("code") val code: String,
    @SerialName("status") val status: String = "active",
    @SerialName("college_name") val collegeName: String? = null,
    @SerialName("batches_count") val batchesCount: Int = 0
)

@Serializable
data class BatchDto(
    @SerialName("id") val id: Int,
    @SerialName("course_id") val courseId: Int,
    @SerialName("name") val name: String,
    @SerialName("academic_year") val academicYear: String,
    @SerialName("duration_years") val durationYears: Int = 4,
    @SerialName("current_semester") val currentSemester: Int = 1,
    @SerialName("batch_nick_name") val batchNickName: String? = null,
    @SerialName("status") val status: String = "active",
    @SerialName("students_count") val studentsCount: Int = 0
)

@Serializable
data class SectionDto(
    @SerialName("id") val id: Int,
    @SerialName("batch_id") val batchId: Int,
    @SerialName("name") val name: String,
    @SerialName("status") val status: String = "active"
)

@Serializable
data class CollegeStreamDto(
    @SerialName("id") val id: Int,
    @SerialName("college_id") val collegeId: Int,
    @SerialName("name") val name: String,
    @SerialName("category") val category: String = "UGC"
)

@Serializable
data class PromoteStudentsRequest(
    @SerialName("batch_id") val batchId: Int,
    @SerialName("preview") val preview: Boolean = false
)

@Serializable
data class PromotePreviewResponse(
    @SerialName("batch_id") val batchId: Int,
    @SerialName("current_semester") val currentSemester: Int,
    @SerialName("target_semester") val targetSemester: Int,
    @SerialName("eligible_students") val eligibleStudents: Int,
    @SerialName("will_graduate") val willGraduate: Boolean = false
)

// --- Student DTOs ---

@Serializable
data class StudentDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("roll_number") val rollNumber: String,
    @SerialName("status") val status: String = "active",
    @SerialName("college_id") val collegeId: Int? = null,
    @SerialName("college_name") val collegeName: String? = null,
    @SerialName("course_name") val courseName: String? = null,
    @SerialName("batch_name") val batchName: String? = null,
    @SerialName("section_name") val sectionName: String? = null
)

@Serializable
data class UnverifiedStudentDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("roll_number") val rollNumber: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class GuestEntryDto(
    @SerialName("id") val id: String,
    @SerialName("test_id") val testId: Int,
    @SerialName("guest_name") val guestName: String,
    @SerialName("guest_email") val guestEmail: String,
    @SerialName("guest_url") val guestUrl: String,
    @SerialName("status") val status: String = "active"
)

@Serializable
data class GuestEntryRequest(
    @SerialName("test_id") val testId: Int,
    @SerialName("guest_name") val guestName: String,
    @SerialName("guest_email") val guestEmail: String
)

// --- Test & Question DTOs ---

@Serializable
data class TestDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("test_type") val testType: String = "mcq", // mcq|coding|explanation|mixed
    @SerialName("status") val status: String = "draft", // draft|scheduled|active|paused|completed
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("total_marks") val totalMarks: Double = 100.0,
    @SerialName("pass_percentage") val passPercentage: Double = 40.0,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("max_tab_switches") val maxTabSwitches: Int? = null,
    @SerialName("questions_count") val questionsCount: Int = 0,
    @SerialName("sections") val sections: List<TestSectionDto> = emptyList()
)

@Serializable
data class TestSectionDto(
    @SerialName("id") val id: Int,
    @SerialName("test_id") val testId: Int,
    @SerialName("title") val title: String,
    @SerialName("section_order") val sectionOrder: Int = 0,
    @SerialName("questions") val questions: List<QuestionDto> = emptyList()
)

@Serializable
data class QuestionDto(
    @SerialName("id") val id: Int,
    @SerialName("section_id") val sectionId: Int? = null,
    @SerialName("question_text") val questionText: String,
    @SerialName("type") val type: String = "mcq", // mcq|coding|explanation
    @SerialName("marks") val marks: Double = 1.0,
    @SerialName("options") val options: List<String> = emptyList(), // for MCQ
    @SerialName("correct_option") val correctOption: String? = null, // A, B, C, D
    @SerialName("explanation") val explanation: String? = null,
    @SerialName("coding_starter") val codingStarter: String? = null
)

@Serializable
data class TestCreateRequest(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("test_type") val testType: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("total_marks") val totalMarks: Double = 100.0,
    @SerialName("pass_percentage") val passPercentage: Double = 40.0,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("max_tab_switches") val maxTabSwitches: Int? = null
)

@Serializable
data class CsvImportResult(
    @SerialName("imported") val imported: Int,
    @SerialName("failed") val failed: Int,
    @SerialName("errors") val errors: List<String> = emptyList()
)

// --- Submission DTOs ---

@Serializable
data class SubmissionDto(
    @SerialName("id") val id: String, // String to handle guest_<id> as well
    @SerialName("test_id") val testId: Int,
    @SerialName("student_id") val studentId: Int? = null,
    @SerialName("status") val status: String, // in_progress|submitted|evaluated
    @SerialName("started_at") val startedAt: String,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("effective_deadline") val effectiveDeadline: String,
    @SerialName("remaining_seconds") val remainingSeconds: Long,
    @SerialName("auto_score") val autoScore: Double? = null,
    @SerialName("manual_score") val manualScore: Double? = null,
    @SerialName("total_score") val totalScore: Double? = null,
    @SerialName("evaluation_status") val evaluationStatus: String = "pending_manual_review",
    @SerialName("rank") val rank: Int? = null,
    @SerialName("is_pass") val isPass: Boolean? = null,
    @SerialName("answers") val answers: List<AnswerDto> = emptyList()
)

@Serializable
data class AnswerDto(
    @SerialName("question_id") val questionId: Int,
    @SerialName("selected_option") val selectedOption: String? = null, // for MCQ
    @SerialName("code") val code: String? = null, // for coding
    @SerialName("text_answer") val textAnswer: String? = null, // for explanation
    @SerialName("marks_awarded") val marksAwarded: Double? = null,
    @SerialName("saved_at") val savedAt: String? = null
)

@Serializable
data class SaveAnswersRequest(
    @SerialName("answers") val answers: Map<String, JsonElement>,
    @SerialName("auto_save") val autoSave: Boolean = true
)

@Serializable
data class SubmitResponse(
    @SerialName("submission_id") val submissionId: String,
    @SerialName("status") val status: String,
    @SerialName("submitted_at") val submittedAt: String,
    @SerialName("duplicate") val duplicate: Boolean = false,
    @SerialName("total_score") val totalScore: Double? = null,
    @SerialName("evaluation_status") val evaluationStatus: String? = null
)

@Serializable
data class TabSwitchRequest(
    @SerialName("submission_id") val submissionId: String,
    @SerialName("switch_count") val switchCount: Int = 1
)

@Serializable
data class TabSwitchLogDto(
    @SerialName("id") val id: Int,
    @SerialName("submission_id") val submissionId: String,
    @SerialName("switch_count") val switchCount: Int,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("user_agent") val userAgent: String? = null,
    @SerialName("occurred_at") val occurredAt: String
)

@Serializable
data class PciRecordDto(
    @SerialName("id") val id: Int,
    @SerialName("student_id") val studentId: Int,
    @SerialName("student_name") val studentName: String? = null,
    @SerialName("pci_score") val pciScore: Double,
    @SerialName("recorded_at") val recordedAt: String
)

// --- Analytics, Notifications, Audit DTOs ---

@Serializable
data class StudentAnalyticsDto(
    @SerialName("tests_taken") val testsTaken: Int = 0,
    @SerialName("avg_score") val avgScore: Double = 0.0,
    @SerialName("pass_rate") val passRate: Double = 0.0,
    @SerialName("category_scores") val categoryScores: Map<String, Double> = emptyMap()
)

@Serializable
data class FacultyOverviewDto(
    @SerialName("total_tests") val totalTests: Int = 0,
    @SerialName("active_submissions") val activeSubmissions: Int = 0,
    @SerialName("pending_gradings") val pendingGradings: Int = 0,
    @SerialName("avg_class_score") val avgClassScore: Double = 0.0
)

@Serializable
data class AdminStatsDto(
    @SerialName("colleges_count") val collegesCount: Int = 0,
    @SerialName("students_count") val studentsCount: Int = 0,
    @SerialName("faculty_count") val facultyCount: Int = 0,
    @SerialName("tests_count") val testsCount: Int = 0,
    @SerialName("active_now") val activeNow: Int = 0
)

@Serializable
data class TestResultsDto(
    @SerialName("test_id") val testId: Int,
    @SerialName("title") val title: String,
    @SerialName("total_submissions") val totalSubmissions: Int = 0,
    @SerialName("pass_count") val passCount: Int = 0,
    @SerialName("fail_count") val failCount: Int = 0,
    @SerialName("avg_score") val avgScore: Double = 0.0,
    @SerialName("rankings") val rankings: List<SubmissionDto> = emptyList()
)

@Serializable
data class NotificationDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("message") val message: String,
    @SerialName("type") val type: String = "info",
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class AuditLogDto(
    @SerialName("id") val id: Int,
    @SerialName("user_id") val userId: Int,
    @SerialName("action") val action: String,
    @SerialName("details") val details: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class FailedLoginDto(
    @SerialName("id") val id: Int,
    @SerialName("email") val email: String,
    @SerialName("ip_address") val ipAddress: String,
    @SerialName("failed_at") val failedAt: String
)
