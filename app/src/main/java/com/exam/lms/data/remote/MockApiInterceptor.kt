package com.exam.lms.data.remote

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockApiInterceptor @Inject constructor() : Interceptor {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // If enabled or server unreachable, intercept `/api/v1/` calls with realistic mock data
        if (path.contains("/api/v1/")) {
            val responseJson = generateMockResponse(path, request)
            if (responseJson != null) {
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.trimIndent().toResponseBody(jsonMediaType))
                    .build()
            }
        }

        return try {
            chain.proceed(request)
        } catch (e: Exception) {
            val fallbackJson = generateMockResponse(path, request) ?: """{"error":{"code":"server_error","message":"Server unavailable"}}"""
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(fallbackJson.trimIndent().toResponseBody(jsonMediaType))
                .build()
        }
    }

    private fun generateMockResponse(path: String, request: Request): String? {
        return when {
            path.endsWith("/auth/login") || path.endsWith("/auth/faculty-login") -> """
                {
                  "data": {
                    "access_token": "mock_access_token_12345",
                    "refresh_token": "mock_refresh_token_67890",
                    "role": "${if (path.contains("faculty")) "faculty" else "student"}",
                    "sub_role": null,
                    "student_id": 101,
                    "user": {
                      "id": 101,
                      "name": "Alex Student",
                      "email": "alex@university.edu",
                      "role": "${if (path.contains("faculty")) "faculty" else "student"}",
                      "college_id": 1,
                      "college_name": "College of Engineering"
                    }
                  }
                }
            """
            path.endsWith("/auth/session") -> """
                {
                  "data": {
                    "id": 101,
                    "name": "Alex Student",
                    "email": "alex@university.edu",
                    "role": "student",
                    "college_id": 1,
                    "college_name": "College of Engineering"
                  }
                }
            """
            path.endsWith("/auth/refresh") -> """
                {
                  "data": {
                    "access_token": "refreshed_access_token_999",
                    "refresh_token": "refreshed_refresh_token_888",
                    "role": "student"
                  }
                }
            """
            path.endsWith("/colleges") -> """
                {
                  "data": [
                    {"id": 1, "name": "College of Engineering", "code": "ENG01", "status": "active", "city": "Metropolis", "courses_count": 4, "students_count": 450},
                    {"id": 2, "name": "School of Management", "code": "MGMT02", "status": "active", "city": "Metropolis", "courses_count": 2, "students_count": 210}
                  ]
                }
            """
            path.endsWith("/courses") -> """
                {
                  "data": [
                    {"id": 10, "college_id": 1, "name": "Computer Science & Engineering", "code": "CSE", "status": "active", "college_name": "College of Engineering", "batches_count": 4},
                    {"id": 11, "college_id": 1, "name": "Electrical Engineering", "code": "EE", "status": "active", "college_name": "College of Engineering", "batches_count": 3}
                  ]
                }
            """
            path.endsWith("/batches") -> """
                {
                  "data": [
                    {"id": 100, "course_id": 10, "name": "2023-2027 CSE-A", "academic_year": "2023-2027", "duration_years": 4, "current_semester": 3, "batch_nick_name": "CSE_2023_A", "status": "active", "students_count": 60}
                  ]
                }
            """
            path.endsWith("/sections") -> """
                {
                  "data": [
                    {"id": 1001, "batch_id": 100, "name": "Section A", "status": "active"},
                    {"id": 1002, "batch_id": 100, "name": "Section B", "status": "active"}
                  ]
                }
            """
            path.endsWith("/students") -> """
                {
                  "data": [
                    {"id": 101, "name": "Alex Student", "email": "alex@university.edu", "roll_number": "CSE2301", "status": "active", "college_name": "College of Engineering", "course_name": "Computer Science", "batch_name": "2023-2027 CSE-A", "section_name": "Section A"}
                  ]
                }
            """
            path.endsWith("/unverified-students") -> """
                {
                  "data": [
                    {"id": 202, "name": "Sam Unverified", "email": "sam@university.edu", "roll_number": "CSE2399", "created_at": "2026-10-10 09:15:00"}
                  ]
                }
            """
            path.endsWith("/tests") -> """
                {
                  "data": [
                    {
                      "id": 501,
                      "title": "Data Structures Midterm Exam",
                      "description": "Comprehensive assessment on Arrays, Linked Lists, Trees, and Sorting Algorithms.",
                      "test_type": "mcq",
                      "status": "active",
                      "duration_minutes": 60,
                      "total_marks": 50.0,
                      "pass_percentage": 40.0,
                      "start_time": "2026-10-12 00:00:00",
                      "end_time": "2026-10-15 23:59:59",
                      "max_tab_switches": 3,
                      "questions_count": 5
                    },
                    {
                      "id": 502,
                      "title": "Algorithms & Complexity Quiz",
                      "description": "Big-O notation, recursion, and graph traversal basics.",
                      "test_type": "mcq",
                      "status": "active",
                      "duration_minutes": 30,
                      "total_marks": 20.0,
                      "pass_percentage": 40.0,
                      "start_time": "2026-10-10 00:00:00",
                      "end_time": "2026-10-20 23:59:59",
                      "max_tab_switches": 2,
                      "questions_count": 3
                    }
                  ]
                }
            """
            path.contains("/tests/") && !path.endsWith("/results") && !path.endsWith("/publish") -> """
                {
                  "data": {
                    "id": 501,
                    "title": "Data Structures Midterm Exam",
                    "description": "Comprehensive assessment on Arrays, Linked Lists, Trees, and Sorting Algorithms.",
                    "test_type": "mcq",
                    "status": "active",
                    "duration_minutes": 60,
                    "total_marks": 50.0,
                    "pass_percentage": 40.0,
                    "start_time": "2026-10-12 00:00:00",
                    "end_time": "2026-10-15 23:59:59",
                    "max_tab_switches": 3,
                    "questions_count": 3,
                    "sections": [
                      {
                        "id": 1,
                        "test_id": 501,
                        "title": "Section 1: Data Structures Basics",
                        "section_order": 1,
                        "questions": [
                          {
                            "id": 1001,
                            "section_id": 1,
                            "question_text": "What is the average time complexity of searching in a Balanced Binary Search Tree (AVL/Red-Black)?",
                            "type": "mcq",
                            "marks": 2.0,
                            "options": ["O(1)", "O(log n)", "O(n)", "O(n log n)"],
                            "correct_option": "B",
                            "explanation": "Balanced BSTs guarantee maximum tree height of O(log n)."
                          },
                          {
                            "id": 1002,
                            "section_id": 1,
                            "question_text": "Which data structure follows the LIFO (Last In First Out) principle?",
                            "type": "mcq",
                            "marks": 2.0,
                            "options": ["Queue", "Stack", "Array", "Graph"],
                            "correct_option": "B",
                            "explanation": "Stack operates on LIFO order."
                          },
                          {
                            "id": 1003,
                            "section_id": 1,
                            "question_text": "Write a brief explanation of how HashMap handles collisions.",
                            "type": "explanation",
                            "marks": 5.0,
                            "options": [],
                            "explanation": "HashMaps handle collision using chaining (LinkedList / Red-Black Tree) or open addressing."
                          }
                        ]
                      }
                    ]
                  }
                }
            """
            path.endsWith("/submissions") -> """
                {
                  "data": {
                    "id": "sub_1001",
                    "test_id": 501,
                    "student_id": 101,
                    "status": "in_progress",
                    "started_at": "2026-10-12 10:00:00",
                    "effective_deadline": "2026-10-12 11:00:00",
                    "remaining_seconds": 3600,
                    "evaluation_status": "pending_manual_review",
                    "answers": []
                  }
                }
            """
            path.contains("/submissions/") && path.endsWith("/answers") -> """
                {
                  "data": {
                    "id": "sub_1001",
                    "test_id": 501,
                    "student_id": 101,
                    "status": "in_progress",
                    "started_at": "2026-10-12 10:00:00",
                    "effective_deadline": "2026-10-12 11:00:00",
                    "remaining_seconds": 3500,
                    "evaluation_status": "pending_manual_review"
                  }
                }
            """
            path.contains("/submissions/") && path.endsWith("/submit") -> """
                {
                  "data": {
                    "submission_id": "sub_1001",
                    "status": "submitted",
                    "submitted_at": "2026-10-12 10:45:00",
                    "duplicate": false,
                    "total_score": 42.0,
                    "evaluation_status": "evaluated"
                  }
                }
            """
            path.endsWith("/tab-switches") -> """
                {
                  "data": {
                    "id": 1,
                    "submission_id": "sub_1001",
                    "switch_count": 1,
                    "ip_address": "127.0.0.1",
                    "user_agent": "LMS Android App/1.0",
                    "occurred_at": "2026-10-12 10:15:22.123"
                  }
                }
            """
            path.endsWith("/student/analytics") -> """
                {
                  "data": {
                    "tests_taken": 8,
                    "avg_score": 84.5,
                    "pass_rate": 100.0,
                    "category_scores": {
                      "Data Structures": 90.0,
                      "Algorithms": 82.0,
                      "Database Management": 81.5
                    }
                  }
                }
            """
            path.endsWith("/faculty/overview") -> """
                {
                  "data": {
                    "total_tests": 12,
                    "active_submissions": 45,
                    "pending_gradings": 6,
                    "avg_class_score": 78.2
                  }
                }
            """
            path.endsWith("/admin/stats") -> """
                {
                  "data": {
                    "colleges_count": 5,
                    "students_count": 1250,
                    "faculty_count": 85,
                    "tests_count": 42,
                    "active_now": 38
                  }
                }
            """
            path.endsWith("/notifications") -> """
                {
                  "data": [
                    {"id": 1, "title": "Midterm Exam Scheduled", "message": "Data Structures Midterm exam is live now.", "type": "info", "created_at": "2026-10-12 08:00:00"}
                  ]
                }
            """
            else -> """
                {
                  "data": {},
                  "meta": {"server_time": "2026-10-12T10:30:00+05:30"}
                }
            """
        }
    }
}
