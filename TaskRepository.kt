package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.SubTask
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TaskRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول أولاً للوصول إلى المهام.")
    }

    fun observeTasks(): Flow<List<TaskItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/tasks"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("tasks")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            parseTask(doc, uid)
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedWith(
                        compareByDescending<TaskItem> { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                            .thenByDescending { it.dueDate ?: 0L }
                    )
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addTask(
        title: String,
        description: String?,
        priority: String,
        dueDate: Long?,
        hasReminder: Boolean,
        subtasks: List<SubTask> = emptyList(),
        audioPath: String? = null,
        audioDurationSeconds: Int = 0,
        imageUris: List<String> = emptyList()
    ): String {
        val uid = requireUserId()
        val tasksRef = db.collection("users").document(uid).collection("tasks")
        val docRef = tasksRef.document()
        val taskId = docRef.id

        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "title" to title.trim(),
            "priority" to priority,
            "isCompleted" to false,
            "hasReminder" to hasReminder,
            "audioDurationSeconds" to audioDurationSeconds,
            "imageUris" to imageUris,
            "subtasks" to subtasks.map { mapOf("id" to it.id, "title" to it.title, "isDone" to it.isDone) },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (!description.isNullOrBlank()) {
            payload["description"] = description.trim()
        }
        if (dueDate != null) {
            payload["dueDate"] = dueDate
        }
        if (!audioPath.isNullOrBlank()) {
            payload["audioPath"] = audioPath
        }

        try {
            docRef.set(payload).await()
            return taskId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, tasksRef.path)
            throw e
        }
    }

    suspend fun updateTask(task: TaskItem) {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "معرف المهمة لا يمكن أن يكون فارغاً" }

        val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
        val updates = mutableMapOf<String, Any?>(
            "title" to task.title.trim(),
            "description" to task.description?.trim(),
            "priority" to task.priority,
            "isCompleted" to task.isCompleted,
            "dueDate" to task.dueDate,
            "hasReminder" to task.hasReminder,
            "audioPath" to task.audioPath,
            "audioDurationSeconds" to task.audioDurationSeconds,
            "imageUris" to task.imageUris,
            "subtasks" to task.subtasks.map { mapOf("id" to it.id, "title" to it.title, "isDone" to it.isDone) },
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleTaskCompleted(task: TaskItem) {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "معرف المهمة لا يمكن أن يكون فارغاً" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)

        try {
            docRef.update(
                mapOf(
                    "isCompleted" to !task.isCompleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTask(taskId: String) {
        val uid = requireUserId()
        require(taskId.isNotBlank()) { "معرف المهمة لا يمكن أن يكون فارغاً" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)

        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAllUserData() {
        val uid = requireUserId()
        val tasksRef = db.collection("users").document(uid).collection("tasks")
        val snapshot = tasksRef.get().await()
        if (!snapshot.isEmpty) {
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
        }
    }

    private fun parseTask(doc: com.google.firebase.firestore.DocumentSnapshot, fallbackUid: String): TaskItem {
        val resolvedId = if (doc.id.isNotBlank()) doc.id else (doc.getString("id") ?: "")
        return TaskItem(
            id = resolvedId,
            userId = doc.getString("userId") ?: fallbackUid,
            title = doc.getString("title") ?: "",
            description = doc.getString("description"),
            priority = doc.getString("priority") ?: TaskPriority.MEDIUM.name,
            isCompleted = doc.getBoolean("isCompleted") ?: false,
            dueDate = doc.getLong("dueDate"),
            hasReminder = doc.getBoolean("hasReminder") ?: false,
            audioPath = doc.getString("audioPath"),
            audioDurationSeconds = (doc.getLong("audioDurationSeconds") ?: 0L).toInt(),
            imageUris = (doc.get("imageUris") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            subtasks = ((doc.get("subtasks") as? List<*>)?.mapNotNull { mapObj ->
                if (mapObj is Map<*, *>) {
                    SubTask(
                        id = mapObj["id"]?.toString() ?: "",
                        title = mapObj["title"]?.toString() ?: "",
                        isDone = mapObj["isDone"] as? Boolean ?: false
                    )
                } else null
            }) ?: emptyList(),
            createdAt = doc.getTimestamp("createdAt"),
            updatedAt = doc.getTimestamp("updatedAt")
        )
    }
}
