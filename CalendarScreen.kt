package com.example.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TaskItem
import com.example.ui.task.TaskCard
import com.example.ui.task.TaskDetailDialog
import com.example.ui.task.TaskEditDialog
import com.example.ui.task.TaskViewModel
import com.example.ui.task.TasksUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarDay(
    val dateMillis: Long,
    val dayName: String,
    val dayNumber: String,
    val monthName: String,
    val isToday: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val tasksState by viewModel.tasksUiState.collectAsStateWithLifecycle()

    val todayCalendar = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    var selectedDayStartMillis by remember {
        mutableLongStateOf(todayCalendar.timeInMillis)
    }

    // Generate 30 days (-7 to +23 days)
    val daysList = remember {
        (-7..23).map { offset ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = todayCalendar.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            CalendarDay(
                dateMillis = cal.timeInMillis,
                dayName = SimpleDateFormat("EEE", Locale("ar")).format(cal.time),
                dayNumber = SimpleDateFormat("dd", Locale("ar")).format(cal.time),
                monthName = SimpleDateFormat("MMM", Locale("ar")).format(cal.time),
                isToday = offset == 0
            )
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        // Scroll to today (index 7)
        listState.scrollToItem(7)
    }

    var taskToViewDetails by remember { mutableStateOf<TaskItem?>(null) }
    var taskToEdit by remember { mutableStateOf<TaskItem?>(null) }

    if (taskToEdit != null) {
        TaskEditDialog(
            initialTask = taskToEdit,
            onDismiss = { taskToEdit = null },
            onSave = { title, desc, priority, dueDate, hasReminder, subtasks ->
                viewModel.updateTask(
                    taskToEdit!!.copy(
                        title = title,
                        description = desc,
                        priority = priority.name,
                        dueDate = dueDate,
                        hasReminder = hasReminder,
                        subtasks = subtasks
                    )
                )
                taskToEdit = null
            }
        )
    }

    if (taskToViewDetails != null) {
        TaskDetailDialog(
            task = taskToViewDetails!!,
            onDismiss = { taskToViewDetails = null },
            onToggleCompleted = {
                val current = taskToViewDetails!!
                viewModel.toggleTask(current)
                taskToViewDetails = current.copy(isCompleted = !current.isCompleted)
            },
            onEdit = {
                taskToEdit = taskToViewDetails
                taskToViewDetails = null
            },
            onDelete = {
                viewModel.deleteTask(taskToViewDetails!!.id)
                taskToViewDetails = null
            },
            onSubtaskToggle = { subtaskId, isDone ->
                val current = taskToViewDetails!!
                val updatedSubtasks = current.subtasks.map { subtask ->
                    if (subtask.id == subtaskId) subtask.copy(isDone = isDone) else subtask
                }
                val updatedTask = current.copy(subtasks = updatedSubtasks)
                taskToViewDetails = updatedTask
                viewModel.updateTask(updatedTask)
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "التقويم والجدول الزمني",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal Days Strip
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(daysList) { day ->
                    val isSelected = selectedDayStartMillis == day.dateMillis
                    val primaryColor = MaterialTheme.colorScheme.primary

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp),
                        modifier = Modifier
                            .width(62.dp)
                            .clickable { selectedDayStartMillis = day.dateMillis }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = day.dayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = day.dayNumber,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = day.monthName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (day.isToday) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else primaryColor,
                                    modifier = Modifier.size(5.dp)
                                ) {}
                            }
                        }
                    }
                }
            }

            // Selected Date Summary Bar
            val selectedDateFormatted = remember(selectedDayStartMillis) {
                SimpleDateFormat("EEEE، dd MMMM yyyy", Locale("ar")).format(Date(selectedDayStartMillis))
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedDateFormatted,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Tasks for selected day
            when (val state = tasksState) {
                is TasksUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is TasksUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is TasksUiState.Success -> {
                    val endOfDayMillis = selectedDayStartMillis + 24 * 60 * 60 * 1000
                    val dayTasks = state.tasks.filter { task ->
                        task.dueDate != null && task.dueDate in selectedDayStartMillis until endOfDayMillis
                    }.sortedBy { it.dueDate ?: 0L }

                    if (dayTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "لا توجد مهام مجدولة في هذا اليوم",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "يمكنك إضافة موعد وتوقيت لأي مهمة لتظهر في جدول هذا اليوم",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(dayTasks, key = { it.id }) { task ->
                                val timeLabel = if (task.dueDate != null) {
                                    SimpleDateFormat("hh:mm a", Locale("ar")).format(Date(task.dueDate))
                                } else ""

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = timeLabel,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                    TaskCard(
                                        task = task,
                                        onToggleCompleted = { viewModel.toggleTask(task) },
                                        onClick = { taskToViewDetails = task },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
