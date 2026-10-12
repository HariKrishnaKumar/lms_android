package com.exam.lms

import org.junit.Assert.*
import org.junit.Test

data class MockSubmission(
    val id: String,
    val totalScore: Double?,
    val evaluationStatus: String
)

class GradingAndRankingBusinessRulesTest {

    @Test
    fun `Rule R1 - Awarded marks clamped to question total marks`() {
        val questionMarks = 5.0
        val rawAwardedMarks = 7.5

        val clampedMarks = minOf(rawAwardedMarks, questionMarks)

        assertEquals(5.0, clampedMarks, 0.001)
    }

    @Test
    fun `Rule R4 - Competition ranking 1, 1, 3 excludes pending submissions`() {
        val submissions = listOf(
            MockSubmission("sub_1", 90.0, "evaluated"),
            MockSubmission("sub_2", 90.0, "evaluated"),
            MockSubmission("sub_3", null, "pending_manual_review"), // Excluded from ranking
            MockSubmission("sub_4", 75.0, "evaluated")
        )

        val evaluatedSubmissions = submissions
            .filter { it.evaluationStatus == "evaluated" && it.totalScore != null }
            .sortedByDescending { it.totalScore }

        val ranks = mutableMapOf<String, Int>()
        var currentRank = 1
        for (i in evaluatedSubmissions.indices) {
            if (i > 0 && evaluatedSubmissions[i].totalScore == evaluatedSubmissions[i - 1].totalScore) {
                ranks[evaluatedSubmissions[i].id] = ranks[evaluatedSubmissions[i - 1].id]!!
            } else {
                ranks[evaluatedSubmissions[i].id] = i + 1
            }
        }

        assertEquals(1, ranks["sub_1"])
        assertEquals(1, ranks["sub_2"])
        assertNull(ranks["sub_3"]) // Pending is not ranked
        assertEquals(3, ranks["sub_4"]) // Competition rank: 1, 1, 3
    }

    @Test
    fun `Rule R5 - Pass percentage calculation threshold at 40 percent`() {
        val totalMarks = 50.0
        val passThresholdPercent = 40.0
        val passScore = totalMarks * (passThresholdPercent / 100.0) // 20.0

        val score1 = 20.0
        val score2 = 19.5

        assertTrue(score1 >= passScore)
        assertFalse(score2 >= passScore)
    }
}
