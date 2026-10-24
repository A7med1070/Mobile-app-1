package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DailyRoutineItem
import com.example.data.model.DietPlan
import com.example.data.model.RoutineCategory
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RoutineRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول للوصول إلى النظام اليومي.")
    }

    fun observeRoutineItems(): Flow<List<DailyRoutineItem>> = flow {
        val uid = requireUserId()
        val routineRef = db.collection("users").document(uid).collection("daily_routine")
        emitAll(
            routineRef.snapshots().map { snapshot ->
                val items = snapshot.documents.mapNotNull { doc ->
                    try {
                        DailyRoutineItem(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            timeLabel = doc.getString("timeLabel") ?: "08:00 ص",
                            timeMinutesFromMidnight = (doc.get("timeMinutesFromMidnight") as? Number)?.toInt() ?: 480,
                            category = doc.getString("category") ?: RoutineCategory.WORK.name,
                            isCompleted = doc.getBoolean("isCompleted") ?: false,
                            notes = doc.getString("notes") ?: "",
                            linkedMealName = doc.getString("linkedMealName")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.timeMinutesFromMidnight }

                items
            }
        )
    }

    suspend fun addRoutineItem(item: DailyRoutineItem): String {
        val uid = requireUserId()
        val routineRef = db.collection("users").document(uid).collection("daily_routine")
        val docRef = routineRef.document()
        val payload = mapOf(
            "title" to item.title.trim(),
            "timeLabel" to item.timeLabel,
            "timeMinutesFromMidnight" to item.timeMinutesFromMidnight,
            "category" to item.category,
            "isCompleted" to item.isCompleted,
            "notes" to item.notes.trim(),
            "linkedMealName" to item.linkedMealName
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun toggleRoutineItem(itemId: String, currentStatus: Boolean) {
        val uid = requireUserId()
        db.collection("users")
            .document(uid)
            .collection("daily_routine")
            .document(itemId)
            .update("isCompleted", !currentStatus)
            .await()
    }

    suspend fun deleteRoutineItem(itemId: String) {
        val uid = requireUserId()
        db.collection("users")
            .document(uid)
            .collection("daily_routine")
            .document(itemId)
            .delete()
            .await()
    }

    suspend fun resetDailyCompletion() {
        val uid = requireUserId()
        val routineRef = db.collection("users").document(uid).collection("daily_routine")
        val snapshot = routineRef.get().await()
        for (doc in snapshot.documents) {
            doc.reference.update("isCompleted", false).await()
        }
    }

    suspend fun populateRoutineFromDiet(dietPlan: DietPlan?) {
        val uid = requireUserId()
        val routineRef = db.collection("users").document(uid).collection("daily_routine")

        val currentItems = routineRef.get().await()
        if (!currentItems.isEmpty) {
            // Already has routine items, don't overwrite blindly
            return
        }

        // Generate a balanced starter routine linking healthy meals with daily habits
        val starterList = mutableListOf(
            DailyRoutineItem(
                id = UUID.randomUUID().toString(),
                title = "الاستيقاظ وصلاة الفجر وشرب كوب ماء كبير",
                timeLabel = "05:30 ص",
                timeMinutesFromMidnight = 330,
                category = RoutineCategory.PRAYER.name
            ),
            DailyRoutineItem(
                id = UUID.randomUUID().toString(),
                title = "وجبة الإفطار الصحي المتوازن",
                timeLabel = "07:30 ص",
                timeMinutesFromMidnight = 450,
                category = RoutineCategory.MEAL.name,
                linkedMealName = dietPlan?.meals?.firstOrNull { it.mealType.contains("فطور") }?.name ?: "إفطار غني بالبروتين"
            ),
            DailyRoutineItem(
                id = UUID.randomUUID().toString(),
                title = "بداية العمل والمهام الأكثر أهمية (Deep Work)",
                timeLabel = "09:00 ص",
                timeMinutesFromMidnight = 540,
                category = RoutineCategory.WORK.name
            ),
            DailyRoutineItem(
                id = UUID.randomUUID().toString(),
                title = "سناك خفيف وتناول سوائل ومشي قصير",
                timeLabel = "11:30 ص",
                timeMinutesFromMidnight = 690,
                category = RoutineCategory.MEAL.name,
                linkedMealName = "مكسرات أو فاكهة طازجة"
            ),
            DailyRoutineItem(
                id = UUID.randomUUID().toString(),
                title = "وجبة الغداء الرئيسية الصحية",
                timeLabel = "02:00 م",
                timeMinutesFromMidnight = 840,
                category = RoutineCategory.MEAL.name,
                linkedMealName = dietPlan?.meals?.firstOrNull { it.mealType.contains("غداء") }?.name ?: "غداء صحي متكامل"
            ),
            DailyRoutineItem(
                id = UUID.randomUUID().toString(),
                title = "تمارين رياضية أو مشي 45 دقيقة",
                timeLabel = "05:30 م",
                timeMinutesFromMidnight = 1050,
                category = RoutineCategory.EXERCISE.name
            ),
            DailyRoutineItem(
                id = UUID.randomUUID().toString(),
                title = "وجبة العشاء الخفيفة",
                timeLabel = "08:00 م",
                timeMinutesFromMidnight = 1200,
                category = RoutineCategory.MEAL.name,
                linkedMealName = dietPlan?.meals?.firstOrNull { it.mealType.contains("عشاء") }?.name ?: "عشاء خفيف ومريح للهضم"
            ),
            DailyRoutineItem(
                id = UUID.randomUUID().toString(),
                title = "الاستعداد للنوم وإيقاف الشاشات",
                timeLabel = "10:30 م",
                timeMinutesFromMidnight = 1350,
                category = RoutineCategory.REST.name
            )
        )

        for (item in starterList) {
            addRoutineItem(item)
        }
    }

    suspend fun deleteAllUserData() {
        val uid = requireUserId()
        val routineRef = db.collection("users").document(uid).collection("daily_routine")
        val snapshot = routineRef.get().await()
        if (!snapshot.isEmpty) {
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
        }
    }
}
