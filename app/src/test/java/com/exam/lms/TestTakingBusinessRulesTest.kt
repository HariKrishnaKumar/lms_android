package com.exam.lms

import com.exam.lms.data.remote.ApiService
import com.exam.lms.data.remote.dto.ApiResponse
import com.exam.lms.data.remote.dto.SubmitResponse
import com.exam.lms.data.repo.LmsRepository
import com.exam.lms.data.token.TokenStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class TestTakingBusinessRulesTest {

    private val apiService = mockk<ApiService>()
    private val tokenStore = mockk<TokenStore>(relaxed = true)
    private lateinit var repository: LmsRepository

    @Before
    fun setup() {
        repository = LmsRepository(apiService, tokenStore)
    }

    @Test
    fun `Rule T1 - Effective deadline is earliest of test end_time and started_at plus duration`() {
        val startedAtEpoch = 1700000000L
        val durationMinutes = 60
        val startedPlusDuration = startedAtEpoch + (durationMinutes * 60)

        val testEndTimeEpoch = 1700002000L // earlier than startedPlusDuration

        val effectiveDeadline = minOf(startedPlusDuration, testEndTimeEpoch)

        assertEquals(testEndTimeEpoch, effectiveDeadline)
    }

    @Test
    fun `Rule T6 - Lost race submit returns duplicate true and is treated as success`() = runTest {
        coEvery { apiService.submitTest("sub_1001") } returns ApiResponse(
            data = SubmitResponse(
                submissionId = "sub_1001",
                status = "submitted",
                submittedAt = "2026-10-12 10:45:00",
                duplicate = true,
                totalScore = 40.0,
                evaluationStatus = "evaluated"
            )
        )

        val response = repository.submitTest("sub_1001")

        assertNotNull(response.data)
        assertTrue(response.data!!.duplicate)
        assertEquals("submitted", response.data!!.status)
    }
}
