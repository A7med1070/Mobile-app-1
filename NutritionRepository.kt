package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DailyFoodEntry
import com.example.data.model.DailyNutritionLog
import com.example.data.model.DietMeal
import com.example.data.model.DietPlan
import com.example.data.model.UserDietProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NutritionRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول للوصول إلى النظام الغذائي.")
    }

    fun observeDietPlan(): Flow<DietPlan?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("diet_plans").document("current")
        emitAll(
            docRef.snapshots().map { snapshot ->
                if (!snapshot.exists()) return@map null
                try {
                    val mealsList = (snapshot.get("meals") as? List<*>)?.mapNotNull { item ->
                        if (item is Map<*, *>) {
                            DietMeal(
                                id = item["id"]?.toString() ?: "",
                                mealType = item["mealType"]?.toString() ?: "وجبة",
                                name = item["name"]?.toString() ?: "",
                                description = item["description"]?.toString() ?: "",
                                ingredients = (item["ingredients"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                portionSize = item["portionSize"]?.toString() ?: "1 حصة",
                                estimatedCalories = (item["estimatedCalories"] as? Number)?.toInt() ?: 0,
                                proteinGrams = (item["proteinGrams"] as? Number)?.toInt() ?: 0,
                                carbsGrams = (item["carbsGrams"] as? Number)?.toInt() ?: 0,
                                fatGrams = (item["fatGrams"] as? Number)?.toInt() ?: 0,
                                healthyAlternatives = (item["healthyAlternatives"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            )
                        } else null
                    } ?: emptyList()

                    DietPlan(
                        id = snapshot.id,
                        userId = uid,
                        targetCalories = (snapshot.get("targetCalories") as? Number)?.toInt() ?: 2000,
                        totalProtein = (snapshot.get("totalProtein") as? Number)?.toInt() ?: 120,
                        totalCarbs = (snapshot.get("totalCarbs") as? Number)?.toInt() ?: 220,
                        totalFat = (snapshot.get("totalFat") as? Number)?.toInt() ?: 60,
                        generalAdvice = snapshot.getString("generalAdvice") ?: "",
                        meals = mealsList,
                        createdAtMillis = (snapshot.get("createdAtMillis") as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    null
                }
            }
        )
    }

    suspend fun saveDietPlan(plan: DietPlan) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("diet_plans").document("current")

        val payload = mapOf(
            "id" to "current",
            "userId" to uid,
            "targetCalories" to plan.targetCalories,
            "totalProtein" to plan.totalProtein,
            "totalCarbs" to plan.totalCarbs,
            "totalFat" to plan.totalFat,
            "generalAdvice" to plan.generalAdvice,
            "meals" to plan.meals.map { m ->
                mapOf(
                    "id" to m.id,
                    "mealType" to m.mealType,
                    "name" to m.name,
                    "description" to m.description,
                    "ingredients" to m.ingredients,
                    "portionSize" to m.portionSize,
                    "estimatedCalories" to m.estimatedCalories,
                    "proteinGrams" to m.proteinGrams,
                    "carbsGrams" to m.carbsGrams,
                    "fatGrams" to m.fatGrams,
                    "healthyAlternatives" to m.healthyAlternatives
                )
            },
            "createdAtMillis" to plan.createdAtMillis
        )

        docRef.set(payload, SetOptions.merge()).await()
    }

    fun observeDietProfile(): Flow<UserDietProfile> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("diet_profile").document("settings")
        emitAll(
            docRef.snapshots().map { snapshot ->
                if (!snapshot.exists()) {
                    UserDietProfile()
                } else {
                    UserDietProfile(
                        age = (snapshot.get("age") as? Number)?.toInt() ?: 25,
                        heightCm = (snapshot.get("heightCm") as? Number)?.toFloat() ?: 175f,
                        weightKg = (snapshot.get("weightKg") as? Number)?.toFloat() ?: 75f,
                        gender = snapshot.getString("gender") ?: "MALE",
                        activityLevel = snapshot.getString("activityLevel") ?: "MODERATELY_ACTIVE",
                        goal = snapshot.getString("goal") ?: "LOSE_WEIGHT",
                        mealsCount = (snapshot.get("mealsCount") as? Number)?.toInt() ?: 3,
                        preferences = snapshot.getString("preferences") ?: "",
                        allergies = snapshot.getString("allergies") ?: "",
                        targetDailyCalories = (snapshot.get("targetDailyCalories") as? Number)?.toInt() ?: 2000
                    )
                }
            }
        )
    }

    suspend fun saveDietProfile(profile: UserDietProfile) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("diet_profile").document("settings")
        val payload = mapOf(
            "age" to profile.age,
            "heightCm" to profile.heightCm,
            "weightKg" to profile.weightKg,
            "gender" to profile.gender,
            "activityLevel" to profile.activityLevel,
            "goal" to profile.goal,
            "mealsCount" to profile.mealsCount,
            "preferences" to profile.preferences,
            "allergies" to profile.allergies,
            "targetDailyCalories" to profile.targetDailyCalories
        )
        docRef.set(payload, SetOptions.merge()).await()
    }

    fun observeDailyLog(dateKey: String): Flow<DailyNutritionLog> = flow {
        val uid = requireUserId()
        val entriesRef = db.collection("users")
            .document(uid)
            .collection("daily_nutrition")
            .document(dateKey)
            .collection("entries")

        val docRef = db.collection("users")
            .document(uid)
            .collection("daily_nutrition")
            .document(dateKey)

        emitAll(
            entriesRef.snapshots().map { snapshot ->
                val entries = snapshot.documents.mapNotNull { doc ->
                    try {
                        DailyFoodEntry(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            mealType = doc.getString("mealType") ?: "وجبة",
                            calories = (doc.get("calories") as? Number)?.toInt() ?: 0,
                            proteinGrams = (doc.get("proteinGrams") as? Number)?.toInt() ?: 0,
                            carbsGrams = (doc.get("carbsGrams") as? Number)?.toInt() ?: 0,
                            fatGrams = (doc.get("fatGrams") as? Number)?.toInt() ?: 0,
                            portionDescription = doc.getString("portionDescription") ?: "1 حصة",
                            portionMultiplier = (doc.get("portionMultiplier") as? Number)?.toFloat() ?: 1.0f,
                            timestampMillis = (doc.get("timestampMillis") as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.timestampMillis }

                // Fetch target calories if available
                var targetCal = 2000
                try {
                    val logDoc = docRef.get().await()
                    if (logDoc.exists()) {
                        targetCal = (logDoc.get("targetCalories") as? Number)?.toInt() ?: 2000
                    }
                } catch (e: Exception) {
                    // Fallback to default
                }

                DailyNutritionLog(
                    dateKey = dateKey,
                    targetCalories = targetCal,
                    entries = entries
                )
            }
        )
    }

    suspend fun addFoodEntry(dateKey: String, entry: DailyFoodEntry, targetCalories: Int) {
        val uid = requireUserId()
        val dateDocRef = db.collection("users")
            .document(uid)
            .collection("daily_nutrition")
            .document(dateKey)

        // Ensure parent document exists
        dateDocRef.set(
            mapOf("dateKey" to dateKey, "targetCalories" to targetCalories),
            SetOptions.merge()
        ).await()

        val entryRef = dateDocRef.collection("entries").document()
        val payload = mapOf(
            "name" to entry.name,
            "mealType" to entry.mealType,
            "calories" to entry.calories,
            "proteinGrams" to entry.proteinGrams,
            "carbsGrams" to entry.carbsGrams,
            "fatGrams" to entry.fatGrams,
            "portionDescription" to entry.portionDescription,
            "portionMultiplier" to entry.portionMultiplier,
            "timestampMillis" to entry.timestampMillis
        )
        entryRef.set(payload).await()
    }

    suspend fun deleteFoodEntry(dateKey: String, entryId: String) {
        val uid = requireUserId()
        db.collection("users")
            .document(uid)
            .collection("daily_nutrition")
            .document(dateKey)
            .collection("entries")
            .document(entryId)
            .delete()
            .await()
    }

    suspend fun updateDailyTargetCalories(dateKey: String, target: Int) {
        val uid = requireUserId()
        val dateDocRef = db.collection("users")
            .document(uid)
            .collection("daily_nutrition")
            .document(dateKey)
        dateDocRef.set(mapOf("targetCalories" to target), SetOptions.merge()).await()
    }

    suspend fun deleteAllUserData() {
        val uid = requireUserId()
        try {
            db.collection("users").document(uid).collection("diet_plans").document("current").delete().await()
        } catch (_: Exception) {}

        try {
            db.collection("users").document(uid).collection("diet_profile").document("settings").delete().await()
        } catch (_: Exception) {}

        try {
            val dailyLogs = db.collection("users").document(uid).collection("daily_nutrition").get().await()
            for (dayDoc in dailyLogs.documents) {
                try {
                    val entries = dayDoc.reference.collection("entries").get().await()
                    val batch = db.batch()
                    for (entry in entries.documents) {
                        batch.delete(entry.reference)
                    }
                    batch.delete(dayDoc.reference)
                    batch.commit().await()
                } catch (_: Exception) {
                    dayDoc.reference.delete().await()
                }
            }
        } catch (_: Exception) {}
    }
}
