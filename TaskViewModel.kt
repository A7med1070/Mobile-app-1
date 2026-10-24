package com.example.ui.task

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SubTask
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.preference.UserProfileManager
import com.example.data.repository.NutritionRepository
import com.example.data.repository.RoutineRepository
import com.example.data.repository.TaskRepository
import com.example.reminder.ReminderScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

sealed interface TasksUiState {
    data object Loading : TasksUiState
    data class Success(val tasks: List<TaskItem>) : TasksUiState
    data class Error(val message: String) : TasksUiState
}

enum class TaskFilter(val titleAr: String) {
    ALL("الكل"),
    TODAY("اليوم"),
    URGENT("عاجل"),
    HIGH("مرتفع"),
    MEDIUM("متوسط"),
    LOW("منخفض"),
    COMPLETED("المكتملة")
}

data class TaskStats(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val urgentCount: Int = 0,
    val dueTodayCount: Int = 0
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TaskRepository(application)

    private val _selectedFilter = MutableStateFlow(TaskFilter.ALL)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDarkMode = MutableStateFlow<Boolean?>(null)
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    private val rawTasksFlow = repository.observeTasks()

    val statsState: StateFlow<TaskStats> = rawTasksFlow
        .map { tasks ->
            val calendar = Calendar.getInstance()
            val startOfToday = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val endOfToday = startOfToday + 24 * 60 * 60 * 1000

            TaskStats(
                totalCount = tasks.size,
                completedCount = tasks.count { it.isCompleted },
                urgentCount = tasks.count { it.priority == TaskPriority.URGENT.name && !it.isCompleted },
                dueTodayCount = tasks.count {
                    it.dueDate != null && it.dueDate in startOfToday until endOfToday && !it.isCompleted
                }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TaskStats()
        )

    val tasksUiState: StateFlow<TasksUiState> = combine(
        rawTasksFlow,
        _selectedFilter,
        _searchQuery
    ) { tasks, filter, query ->
        val filtered = tasks.filter { task ->
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                task.title.contains(query, ignoreCase = true) ||
                        (task.description?.contains(query, ignoreCase = true) == true)
            }

            if (!matchesQuery) return@filter false

            when (filter) {
                TaskFilter.ALL -> true
                TaskFilter.COMPLETED -> task.isCompleted
                TaskFilter.URGENT -> task.priority == TaskPriority.URGENT.name && !task.isCompleted
                TaskFilter.HIGH -> task.priority == TaskPriority.HIGH.name && !task.isCompleted
                TaskFilter.MEDIUM -> task.priority == TaskPriority.MEDIUM.name && !task.isCompleted
                TaskFilter.LOW -> task.priority == TaskPriority.LOW.name && !task.isCompleted
                TaskFilter.TODAY -> {
                    if (task.dueDate == null || task.isCompleted) false
                    else {
                        val cal = Calendar.getInstance()
                        val start = cal.apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val end = start + 24 * 60 * 60 * 1000
                        task.dueDate in start until end
                    }
                }
            }
        }
        TasksUiState.Success(filtered) as TasksUiState
    }
        .catch { error ->
            emit(TasksUiState.Error(error.localizedMessage ?: "حدث خطأ أثناء تحميل المهام"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TasksUiState.Loading
        )

    fun setFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleDarkMode(currentThemeDark: Boolean) {
        _isDarkMode.value = if (_isDarkMode.value == null) {
            !currentThemeDark
        } else {
            !(_isDarkMode.value!!)
        }
    }

    fun addTask(
        title: String,
        description: String?,
        priority: TaskPriority,
        dueDate: Long?,
        hasReminder: Boolean,
        subtasks: List<SubTask> = emptyList(),
        audioPath: String? = null,
        audioDurationSeconds: Int = 0,
        imageUris: List<String> = emptyList(),
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val taskId = repository.addTask(
                    title = title,
                    description = description,
                    priority = priority.name,
                    dueDate = dueDate,
                    hasReminder = hasReminder,
                    audioPath = audioPath,
                    audioDurationSeconds = audioDurationSeconds,
                    imageUris = imageUris,
                    subtasks = subtasks
                )

                if (hasReminder && dueDate != null) {
                    ReminderScheduler.scheduleReminder(
                        context = getApplication(),
                        taskId = taskId,
                        title = title,
                        description = description,
                        priority = priority.name,
                        triggerAtMillis = dueDate
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "فشل إضافة المهمة")
            }
        }
    }

    fun updateTask(
        task: TaskItem,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.updateTask(task)
                if (task.hasReminder && task.dueDate != null && !task.isCompleted) {
                    ReminderScheduler.scheduleReminder(
                        context = getApplication(),
                        taskId = task.id,
                        title = task.title,
                        description = task.description,
                        priority = task.priority,
                        triggerAtMillis = task.dueDate
                    )
                } else {
                    ReminderScheduler.cancelReminder(getApplication(), task.id)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "فشل تعديل المهمة")
            }
        }
    }

    fun toggleTask(task: TaskItem) {
        viewModelScope.launch {
            try {
                repository.toggleTaskCompleted(task)
                if (!task.isCompleted) {
                    ReminderScheduler.cancelReminder(getApplication(), task.id)
                }
            } catch (e: Exception) {
                Log.e("TaskViewModel", "فشل تبديل حالة المهمة", e)
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                ReminderScheduler.cancelReminder(getApplication(), taskId)
                repository.deleteTask(taskId)
            } catch (e: Exception) {
                Log.e("TaskViewModel", "فشل حذف المهمة", e)
            }
        }
    }

    fun signOut(context: Context, credentialManager: CredentialManager, onComplete: () -> Unit) {
        Firebase.auth.signOut()
        viewModelScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e("Auth", "فشل مسح جلسة CredentialManager", e)
            } finally {
                onComplete()
            }
        }
    }

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    fun deleteAccount(
        context: Context,
        credentialManager: CredentialManager,
        onReauthRequired: () -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            onError("لا يوجد حساب مسجل حالياً")
            return
        }
        val uid = user.uid

        viewModelScope.launch {
            _isDeletingAccount.value = true
            try {
                // 1. Cancel reminders for current user's tasks
                val currentTasks = (tasksUiState.value as? TasksUiState.Success)?.tasks ?: emptyList()
                currentTasks.forEach { task ->
                    ReminderScheduler.cancelReminder(context, task.id)
                }

                // 2. Wipe Firestore subcollections
                val routineRepo = RoutineRepository(context)
                val nutritionRepo = NutritionRepository(context)

                repository.deleteAllUserData()
                routineRepo.deleteAllUserData()
                nutritionRepo.deleteAllUserData()

                // 3. Clear local preferences
                UserProfileManager(context).clear()

                // 4. Delete Firebase Auth user
                user.delete().await()

                // 5. Clear credentials
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.e("TaskViewModel", "فشل مسح CredentialState", e)
                }

                _isDeletingAccount.value = false
                onSuccess()
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                _isDeletingAccount.value = false
                Log.w("TaskViewModel", "إعادة تسجيل الدخول مطلوبة لحذف الحساب", e)
                onReauthRequired()
            } catch (e: Exception) {
                _isDeletingAccount.value = false
                Log.e("TaskViewModel", "فشل حذف الحساب", e)
                onError(e.localizedMessage ?: "حدث خطأ أثناء حذف الحساب والبيانات")
            }
        }
    }
}

