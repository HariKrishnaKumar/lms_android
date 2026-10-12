package com.exam.lms.ui.nav

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.exam.lms.ui.admin.*
import com.exam.lms.ui.auth.*
import com.exam.lms.ui.faculty.FacultyDashboardScreen
import com.exam.lms.ui.faculty.FacultyViewModel
import com.exam.lms.ui.student.*

@Composable
fun LmsNavGraph() {
    val navController = rememberNavController()

    val authViewModel: AuthViewModel = hiltViewModel()
    val studentViewModel: StudentViewModel = hiltViewModel()
    val facultyViewModel: FacultyViewModel = hiltViewModel()
    val adminViewModel: AdminViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                viewModel = authViewModel,
                onNavigateToGraph = { dest ->
                    navController.navigate(dest) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = { role ->
                    val dest = when (role) {
                        "student" -> "student_dashboard"
                        "faculty" -> "faculty_dashboard"
                        else -> "admin_dashboard"
                    }
                    navController.navigate(dest) {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToFacultyLogin = { navController.navigate("faculty_login") },
                onNavigateToSignup = { navController.navigate("signup") },
                onNavigateToForgotPassword = { navController.navigate("forgot_password") }
            )
        }

        composable("faculty_login") {
            FacultyLoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate("faculty_dashboard") {
                        popUpTo("faculty_login") { inclusive = true }
                    }
                },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        composable("signup") {
            SignupScreen(
                viewModel = authViewModel,
                onNavigateToVerifyOtp = { studentId ->
                    navController.navigate("verify_otp/$studentId")
                },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        composable(
            route = "verify_otp/{studentId}",
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: 0
            VerifyOtpScreen(
                studentId = studentId,
                viewModel = authViewModel,
                onOtpVerified = {
                    navController.navigate("student_dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("forgot_password") {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onBackToLogin = { navController.popBackStack() }
            )
        }

        composable(
            route = "guest_entry/{testId}",
            arguments = listOf(navArgument("testId") { type = NavType.IntType })
        ) { backStackEntry ->
            val testId = backStackEntry.arguments?.getInt("testId") ?: 501
            GuestEntryScreen(
                testId = testId,
                viewModel = authViewModel,
                onGuestCreated = {
                    navController.navigate("test_taking/$testId")
                }
            )
        }

        // --- Student Destinations ---

        composable("student_dashboard") {
            StudentDashboardScreen(
                viewModel = studentViewModel,
                onStartTest = { testId -> navController.navigate("test_taking/$testId") },
                onNavigateToAnalytics = { navController.navigate("student_analytics") },
                onNavigateToProfile = { navController.navigate("student_profile") },
                onLogout = {
                    authViewModel.logout {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(
            route = "test_taking/{testId}",
            arguments = listOf(navArgument("testId") { type = NavType.IntType })
        ) { backStackEntry ->
            val testId = backStackEntry.arguments?.getInt("testId") ?: 501
            TestTakingScreen(
                testId = testId,
                viewModel = studentViewModel,
                onSubmitted = {
                    navController.navigate("result") {
                        popUpTo("test_taking/$testId") { inclusive = true }
                    }
                }
            )
        }

        composable("result") {
            ResultScreen(
                viewModel = studentViewModel,
                onBackToDashboard = {
                    navController.navigate("student_dashboard") {
                        popUpTo("student_dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable("student_analytics") {
            StudentAnalyticsScreen(
                viewModel = studentViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("student_profile") {
            StudentProfileScreen(
                viewModel = studentViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --- Faculty Destinations ---

        composable("faculty_dashboard") {
            FacultyDashboardScreen(
                viewModel = facultyViewModel,
                onLogout = {
                    authViewModel.logout {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        // --- Admin Destinations ---

        composable("admin_dashboard") {
            AdminDashboardScreen(
                viewModel = adminViewModel,
                onNavigate = { route -> navController.navigate(route) },
                onLogout = {
                    authViewModel.logout {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("colleges") {
            CollegesScreen(
                viewModel = adminViewModel,
                onNavigateCreate = { navController.navigate("college_create") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("college_create") {
            CollegeCreateScreen(
                viewModel = adminViewModel,
                onCreated = { navController.popBackStack() }
            )
        }

        composable("courses") {
            CoursesScreen(viewModel = adminViewModel, onBack = { navController.popBackStack() })
        }

        composable("batches") {
            BatchesScreen(viewModel = adminViewModel, onBack = { navController.popBackStack() })
        }

        composable("students") {
            StudentsScreen(viewModel = adminViewModel, onBack = { navController.popBackStack() })
        }

        composable("pending_verifications") {
            PendingVerificationsScreen(viewModel = adminViewModel, onBack = { navController.popBackStack() })
        }

        composable("test_builder") {
            TestBuilderScreen(
                viewModel = adminViewModel,
                onCreated = { navController.popBackStack() }
            )
        }

        composable("failed_logins") {
            FailedLoginsScreen(viewModel = adminViewModel, onBack = { navController.popBackStack() })
        }

        composable("settings") {
            SettingsPlaceholderScreen(onBack = { navController.popBackStack() })
        }
    }
}
