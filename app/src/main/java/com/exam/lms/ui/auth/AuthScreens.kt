package com.exam.lms.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exam.lms.data.remote.dto.RegisterRequest
import com.exam.lms.ui.components.QrCodeImage

@Composable
fun SplashScreen(
    viewModel: AuthViewModel,
    onNavigateToGraph: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.checkExistingSession { role ->
            val destination = when (role) {
                "student" -> "student_dashboard"
                "faculty" -> "faculty_dashboard"
                "admin", "super_admin", "platform_admin" -> "admin_dashboard"
                else -> "login"
            }
            onNavigateToGraph(destination)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = "LMS Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "LMS Exam Platform",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: (String) -> Unit,
    onNavigateToFacultyLogin: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.successRole) {
        state.successRole?.let { role ->
            if (role != "login_reset_complete") {
                onLoginSuccess(role)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sign In") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Welcome to LMS", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            state.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.login(email, password) },
                enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Sign In")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNavigateToFacultyLogin) {
                Text("Faculty Sign In")
            }
            TextButton(onClick = onNavigateToForgotPassword) {
                Text("Forgot Password?")
            }
            TextButton(onClick = onNavigateToSignup) {
                Text("Don't have an account? Sign Up")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacultyLoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: (String) -> Unit,
    onBackToLogin: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var collegeId by remember { mutableStateOf(1) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadColleges()
    }

    LaunchedEffect(state.successRole) {
        state.successRole?.let { onLoginSuccess(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Faculty Sign In") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Faculty Login", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Faculty Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            state.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.facultyLogin(collegeId, email, password) },
                enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Faculty Sign In")
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBackToLogin) {
                Text("Back to Student/Admin Login")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    viewModel: AuthViewModel,
    onNavigateToVerifyOtp: (Int) -> Unit,
    onBackToLogin: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var rollNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.unverifiedStudentId) {
        state.unverifiedStudentId?.let { id ->
            onNavigateToVerifyOtp(id)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Student Registration") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = rollNumber, onValueChange = { rollNumber = it }, label = { Text("Roll Number") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            state.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    viewModel.register(
                        RegisterRequest(
                            name = name,
                            email = email,
                            phone = phone,
                            gender = "Male",
                            collegeId = 1,
                            courseId = 10,
                            batchId = 100,
                            sectionId = 1001,
                            rollNumber = rollNumber,
                            yearOfJoining = 2023,
                            password = password
                        )
                    )
                },
                enabled = !state.isLoading && name.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Create Account")
            }

            TextButton(onClick = onBackToLogin) {
                Text("Already have an account? Sign In")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyOtpScreen(
    studentId: Int,
    viewModel: AuthViewModel,
    onOtpVerified: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var otp by remember { mutableStateOf("") }

    LaunchedEffect(state.successRole) {
        state.successRole?.let { onOtpVerified() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Verify OTP") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Enter the 6-digit OTP sent to your registered email.", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6) otp = it },
                label = { Text("6-Digit OTP") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            state.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.verifyOtp(studentId, otp) },
                enabled = !state.isLoading && otp.length == 6,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Verify & Activate")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onBackToLogin: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Forgot Password") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
        ) {
            if (!state.otpSent) {
                Text("Enter your email address to receive a password reset OTP.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.forgotPassword(email) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Send Reset OTP")
                }
            } else if (state.resetGrant == null) {
                Text("Enter the OTP sent to $email.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = otp, onValueChange = { otp = it }, label = { Text("Reset OTP") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.verifyResetOtp(email, otp) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Verify Reset OTP")
                }
            } else {
                Text("Enter your new password.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.resetPassword(state.resetGrant!!, newPassword) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Password")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBackToLogin) {
                Text("Back to Login")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestEntryScreen(
    testId: Int,
    viewModel: AuthViewModel,
    onGuestCreated: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Guest Test Access") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.guestUrl == null) {
                Text("Enter your details to generate guest access link & QR code.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Guest Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Guest Email") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.createGuestEntry(testId, name, email) },
                    enabled = !state.isLoading && name.isNotBlank() && email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate Guest Access")
                }
            } else {
                Text("Guest access generated successfully!", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                QrCodeImage(payload = state.guestUrl!!)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = state.guestUrl!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { onGuestCreated(state.guestUrl!!) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Guest Test")
                }
            }
        }
    }
}
