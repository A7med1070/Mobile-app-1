package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskPriority(val labelAr: String) {
    URGENT("عاجل"),
    HIGH("مرتفع"),
    MEDIUM("متوسط"),
    LOW("منخفض");

    companion object {
        fun fromString(value: String?): TaskPriority {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }
}

@IgnoreExtraProperties
data class SubTask(
    val id: String = "",
    val title: String = "",
    val isDone: Boolean = false
)

@IgnoreExtraProperties
data class TaskItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String? = null,
    val priority: String = TaskPriority.MEDIUM.name,
    val isCompleted: Boolean = false,
    val dueDate: Long? = null,
    val hasReminder: Boolean = false,
    val audioPath: String? = null,
    val audioDurationSeconds: Int = 0,
    val imageUris: List<String> = emptyList(),
    val subtasks: List<SubTask> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun getTypedPriority(): TaskPriority = TaskPriority.fromString(priority)
}
