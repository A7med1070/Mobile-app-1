package com.example.ui.task

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SubTask
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.ui.theme.PriorityHigh
import com.example.ui.theme.PriorityLow
import com.example.ui.theme.PriorityMedium
import com.example.ui.theme.PriorityUrgent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditDialog(
    initialTask: TaskItem? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String?,
        priority: TaskPriority,
        dueDate: Long?,
        hasReminder: Boolean,
        subtasks: List<SubTask>
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var selectedPriority by remember {
        mutableStateOf(initialTask?.getTypedPriority() ?: TaskPriority.MEDIUM)
    }
    var dueDate by remember { mutableStateOf(initialTask?.dueDate) }
    var hasReminder by remember { mutableStateOf(initialTask?.hasReminder ?: false) }

    val subtasks = remember {
        mutableStateListOf<SubTask>().apply {
            initialTask?.subtasks?.let { addAll(it) }
        }
    }
    var newSubtaskText by remember { mutableStateOf("") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasReminder = true
        }
    }

    // Date & Time Picker Dialog State
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dueDate ?: System.currentTimeMillis()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedDate = datePickerState.selectedDateMillis ?: (dueDate ?: System.currentTimeMillis())
                        showDatePicker = false

                        val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = pickedDate
                        }
                        val year = utcCal.get(java.util.Calendar.YEAR)
                        val month = utcCal.get(java.util.Calendar.MONTH)
                        val day = utcCal.get(java.util.Calendar.DAY_OF_MONTH)

                        val currentCal = java.util.Calendar.getInstance().apply {
                            if (dueDate != null) timeInMillis = dueDate!!
                        }

                        android.app.TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val finalCal = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.YEAR, year)
                                    set(java.util.Calendar.MONTH, month)
                                    set(java.util.Calendar.DAY_OF_MONTH, day)
                                    set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(java.util.Calendar.MINUTE, minute)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }
                                dueDate = finalCal.timeInMillis
                                hasReminder = true
                            },
                            currentCal.get(java.util.Calendar.HOUR_OF_DAY),
                            currentCal.get(java.util.Calendar.MINUTE),
                            false
                        ).show()
                    }
                ) {
                    Text("التالي (الوقت)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialTask == null) "إضافة مهمة جديدة" else "تعديل المهمة",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان المهمة *") },
                placeholder = { Text("مثال: مراجعة التقرير، شراء المستلزمات...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input")
            )

            // Description input
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("ملاحظات وتفاصيل إضافية") },
                placeholder = { Text("أضف أي تفاصيل أو ملاحظات مهمة...") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_desc_input")
            )

            // Priority Selection
            Text(
                text = "درجة الأولوية",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TaskPriority.entries.forEach { priority ->
                    val isSelected = selectedPriority == priority
                    val priorityColor = when (priority) {
                        TaskPriority.URGENT -> PriorityUrgent
                        TaskPriority.HIGH -> PriorityHigh
                        TaskPriority.MEDIUM -> PriorityMedium
                        TaskPriority.LOW -> PriorityLow
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPriority = priority },
                        label = {
                            Text(
                                text = priority.labelAr,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Surface(
                                shape = CircleShape,
                                color = priorityColor,
                                modifier = Modifier.size(10.dp)
                            ) {}
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = priorityColor.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("priority_chip_${priority.name.lowercase()}")
                    )
                }
            }

            // Due Date & Reminders Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (dueDate != null) {
                                    SimpleDateFormat("EEEE، dd MMMM yyyy - hh:mm a", Locale("ar")).format(Date(dueDate!!))
                                } else {
                                    "تحديد التاريخ والوقت"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }

                        Row {
                            if (dueDate != null) {
                                TextButton(onClick = { dueDate = null }) {
                                    Text("مسح", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            TextButton(onClick = { showDatePicker = true }) {
                                Text(if (dueDate == null) "اختيار" else "تغيير")
                            }
                        }
                    }

                    if (dueDate != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = if (hasReminder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "تفعيل إشعار التذكير",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = hasReminder,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val isGranted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (!isGranted) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                hasReminder = true
                                            }
                                        } else {
                                            hasReminder = true
                                        }
                                    } else {
                                        hasReminder = false
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Subtasks / Checklist builder
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "قائمة المهام الفرعية (${subtasks.count { it.isDone }}/${subtasks.size})",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                subtasks.forEachIndexed { index, subtask ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = subtask.isDone,
                            onCheckedChange = { checked ->
                                subtasks[index] = subtask.copy(isDone = checked)
                            }
                        )
                        Text(
                            text = subtask.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { subtasks.removeAt(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "حذف النقطة",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubtaskText,
                        onValueChange = { newSubtaskText = it },
                        placeholder = { Text("أضف خطوة فرعية...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newSubtaskText.isNotBlank()) {
                                subtasks.add(
                                    SubTask(
                                        id = UUID.randomUUID().toString(),
                                        title = newSubtaskText.trim(),
                                        isDone = false
                                    )
                                )
                                newSubtaskText = ""
                            }
                        },
                        enabled = newSubtaskText.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "إضافة خطوة"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("إلغاء")
                }

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(
                                title.trim(),
                                description.trim().ifEmpty { null },
                                selectedPriority,
                                dueDate,
                                hasReminder,
                                subtasks.toList()
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_task_button")
                ) {
                    Text(if (initialTask == null) "حفظ المهمة" else "تحديث")
                }
            }
        }
    }
}
