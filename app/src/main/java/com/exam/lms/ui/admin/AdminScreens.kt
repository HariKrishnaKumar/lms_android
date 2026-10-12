package com.exam.lms.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exam.lms.ui.components.DataTable
import com.exam.lms.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAdminDashboard()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Platform Administration") },
                actions = {
                    IconButton(onClick = { viewModel.logout(onLogout) }) {
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
            val stats = state.stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminMetricCard(label = "Colleges", value = "${stats?.collegesCount ?: 0}", modifier = Modifier.weight(1f))
                AdminMetricCard(label = "Students", value = "${stats?.studentsCount ?: 0}", modifier = Modifier.weight(1f))
                AdminMetricCard(label = "Tests", value = "${stats?.testsCount ?: 0}", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Admin Navigation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onNavigate("colleges") }, modifier = Modifier.weight(1f)) { Text("Colleges") }
                    Button(onClick = { onNavigate("courses") }, modifier = Modifier.weight(1f)) { Text("Courses") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onNavigate("batches") }, modifier = Modifier.weight(1f)) { Text("Batches") }
                    Button(onClick = { onNavigate("students") }, modifier = Modifier.weight(1f)) { Text("Students") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onNavigate("pending_verifications") }, modifier = Modifier.weight(1f)) { Text("Pending OTP") }
                    Button(onClick = { onNavigate("test_builder") }, modifier = Modifier.weight(1f)) { Text("Test Builder") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigate("failed_logins") }, modifier = Modifier.weight(1f)) { Text("Audit Logs") }
                    OutlinedButton(onClick = { onNavigate("settings") }, modifier = Modifier.weight(1f)) { Text("Settings") }
                }
            }
        }
    }
}

@Composable
fun AdminMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollegesScreen(
    viewModel: AdminViewModel,
    onNavigateCreate: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadColleges()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("College Management") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateCreate) {
                Icon(Icons.Default.Add, contentDescription = "Add College")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            DataTable(
                headers = listOf("Code", "Name", "City", "Action"),
                items = state.colleges
            ) { college ->
                Text(college.code, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Text(college.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Text(college.city ?: "N/A", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Row(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { viewModel.archiveCollege(college.id) }) { Text("Archive", fontSize = 10.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollegeCreateScreen(
    viewModel: AdminViewModel,
    onCreated: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Create College") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("College Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("College Code") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.createCollege(name, code, city)
                    onCreated()
                },
                enabled = name.isNotBlank() && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create College")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCourses()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Courses Management") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            DataTable(
                headers = listOf("Code", "Course Name", "College"),
                items = state.courses
            ) { course ->
                Text(course.code, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Text(course.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Text(course.collegeName ?: "N/A", modifier = Modifier.weight(1f), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchesScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadBatches()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Batches & Promotion") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            DataTable(
                headers = listOf("Batch", "Sem", "Students", "Promote"),
                items = state.batches
            ) { batch ->
                Text(batch.name, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text("Sem ${batch.currentSemester}", modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text("${batch.studentsCount}", modifier = Modifier.weight(1f), fontSize = 12.sp)
                Button(
                    onClick = { viewModel.promoteBatch(batch.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Promote", fontSize = 10.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadStudents()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Students Directory") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            DataTable(
                headers = listOf("Roll No", "Name", "Email", "Status"),
                items = state.students
            ) { student ->
                Text(student.rollNumber, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(student.name, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(student.email, modifier = Modifier.weight(1f), fontSize = 12.sp)
                StatusBadge(status = student.status, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingVerificationsScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadStudents()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pending OTP Verifications") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            DataTable(
                headers = listOf("Roll No", "Name", "Registered At"),
                items = state.unverifiedStudents
            ) { unverified ->
                Text(unverified.rollNumber, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(unverified.name, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(unverified.createdAt, modifier = Modifier.weight(1f), fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestBuilderScreen(
    viewModel: AdminViewModel,
    onCreated: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Test Builder") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Assessment Title") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration (minutes)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.createTest(title, duration.toIntOrNull() ?: 60)
                    onCreated()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Assessment")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailedLoginsScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFailedLogins()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Failed Login Audit Log") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            DataTable(
                headers = listOf("Email", "IP Address", "Timestamp"),
                items = state.failedLogins
            ) { log ->
                Text(log.email, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(log.ipAddress, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(log.failedAt, modifier = Modifier.weight(1f), fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPlaceholderScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Settings: Coming Soon", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
