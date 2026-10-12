package com.exam.lms.ui.student

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.exam.lms.data.remote.dto.TestDto
import com.exam.lms.ui.components.StatusBadge
import com.exam.lms.ui.components.TimerCountdown
import com.exam.lms.ui.components.WarningBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    viewModel: StudentViewModel,
    onStartTest: (Int) -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadStudentData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Dashboard") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Welcome, ${state.profile?.name ?: "Student"}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNavigateToAnalytics, modifier = Modifier.weight(1f)) {
                    Text("Analytics")
                }
                OutlinedButton(onClick = onNavigateToProfile, modifier = Modifier.weight(1f)) {
                    Text("Profile")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Active & Upcoming Tests", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (state.activeTests.isEmpty()) {
                Text("No active tests available at this moment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.activeTests) { test ->
                        TestCard(test = test, onStart = { onStartTest(test.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun TestCard(test: TestDto, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(test.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                StatusBadge(status = test.status)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(test.description ?: "No description provided", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Duration: ${test.durationMinutes} mins | Marks: ${test.totalMarks}", fontSize = 12.sp)
                Button(onClick = onStart) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Test")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestTakingScreen(
    testId: Int,
    viewModel: StudentViewModel,
    onSubmitted: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Apply FLAG_SECURE on TestTaking Screen
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        viewModel.startTest(testId)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Anti-Cheat Lifecycle Mapping: count tab switches on pause / stop
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.recordTabSwitch()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.isSubmitted) {
        if (state.isSubmitted) {
            onSubmitted()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.currentTest?.title ?: "Assessment") },
                    actions = {
                        TimerCountdown(remainingSeconds = state.remainingSeconds)
                    }
                )
                WarningBanner(
                    message = "Warning: Tab switch detected! Count: ${state.tabSwitchCount}",
                    visible = state.showWarningBanner
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Autosave: ${state.autosaveStatus}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Switches: ${state.tabSwitchCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(16.dp))

            val questions = state.currentTest?.sections?.flatMap { it.questions } ?: emptyList()

            if (questions.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(questions) { question ->
                        QuestionItem(
                            question = question,
                            selectedAnswer = state.userAnswers[question.id] ?: "",
                            onAnswerSelected = { answer ->
                                viewModel.onAnswerChanged(question.id, answer)
                            }
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.submitTest() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Final Submit Assessment")
            }
        }
    }
}

@Composable
fun QuestionItem(
    question: com.exam.lms.data.remote.dto.QuestionDto,
    selectedAnswer: String,
    onAnswerSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Q: ${question.questionText}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Marks: ${question.marks}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            if (question.type == "mcq" && question.options.isNotEmpty()) {
                question.options.forEachIndexed { index, option ->
                    val optionLabel = listOf("A", "B", "C", "D").getOrElse(index) { "$index" }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedAnswer == optionLabel,
                            onClick = { onAnswerSelected(optionLabel) }
                        )
                        Text(text = "$optionLabel) $option", fontSize = 14.sp)
                    }
                }
            } else {
                OutlinedTextField(
                    value = selectedAnswer,
                    onValueChange = onAnswerSelected,
                    label = { Text("Write your response...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: StudentViewModel,
    onBackToDashboard: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val result = state.submitResult

    Scaffold(
        topBar = { TopAppBar(title = { Text("Assessment Result") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Submission Completed!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Submission ID: ${result?.submissionId ?: "N/A"}", fontSize = 14.sp)
            Text("Submitted At: ${result?.submittedAt ?: "N/A"}", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Score: ${result?.totalScore ?: 0.0}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            StatusBadge(status = result?.evaluationStatus ?: "evaluated")

            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBackToDashboard, modifier = Modifier.fillMaxWidth()) {
                Text("Return to Dashboard")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAnalyticsScreen(
    viewModel: StudentViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val analytics = state.analytics

    Scaffold(
        topBar = { TopAppBar(title = { Text("Performance Analytics") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Tests Taken: ${analytics?.testsTaken ?: 0}", fontSize = 16.sp)
            Text("Average Score: ${analytics?.avgScore ?: 0.0}%", fontSize = 16.sp)
            Text("Pass Rate: ${analytics?.passRate ?: 0.0}%", fontSize = 16.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(
    viewModel: StudentViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile

    Scaffold(
        topBar = { TopAppBar(title = { Text("Student Profile") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Name: ${profile?.name ?: "Alex Student"}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Email: ${profile?.email ?: "alex@university.edu"}", fontSize = 14.sp)
            Text("Role: ${profile?.role ?: "student"}", fontSize = 14.sp)
            Text("College: ${profile?.collegeName ?: "College of Engineering"}", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}
