package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ActivityLevel(val labelAr: String, val factor: Double) {
    SEDENTARY("قليل الحركة (مكتبي)", 1.2),
    LIGHTLY_ACTIVE("نشاط خفيف (1-3 أيام أسبوعياً)", 1.375),
    MODERATELY_ACTIVE("نشاط متوسط (3-5 أيام أسبوعياً)", 1.55),
    VERY_ACTIVE("نشاط عالي (تمارين مكثفة)", 1.725);

    companion object {
        fun fromString(value: String?): ActivityLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MODERATELY_ACTIVE
    }
}

enum class DietGoal(val labelAr: String) {
    LOSE_WEIGHT("خسارة الوزن وتخفيف الدهون"),
    MAINTAIN_WEIGHT("الحفاظ على الوزن الحالي"),
    GAIN_WEIGHT("زيادة الوزن وبناء العضلات");

    companion object {
        fun fromString(value: String?): DietGoal =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOSE_WEIGHT
    }
}

@IgnoreExtraProperties
data class UserDietProfile(
    val age: Int = 25,
    val heightCm: Float = 175f,
    val weightKg: Float = 75f,
    val gender: String = "MALE", // MALE or FEMALE
    val activityLevel: String = ActivityLevel.MODERATELY_ACTIVE.name,
    val goal: String = DietGoal.LOSE_WEIGHT.name,
    val mealsCount: Int = 3,
    val preferences: String = "",
    val allergies: String = "",
    val targetDailyCalories: Int = 2000
) {
    fun calculateBmr(): Int {
        // Mifflin-St Jeor formula
        val s = if (gender == "MALE") 5 else -161
        val bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) + s
        val factor = ActivityLevel.fromString(activityLevel).factor
        val tdee = bmr * factor
        return when (DietGoal.fromString(goal)) {
            DietGoal.LOSE_WEIGHT -> (tdee - 450).toInt().coerceAtLeast(1200)
            DietGoal.GAIN_WEIGHT -> (tdee + 400).toInt()
            DietGoal.MAINTAIN_WEIGHT -> tdee.toInt()
        }
    }
}

@IgnoreExtraProperties
data class DietMacroInfo(
    val proteinGrams: Int = 0,
    val carbsGrams: Int = 0,
    val fatGrams: Int = 0
)

@IgnoreExtraProperties
data class DietMeal(
    val id: String = "",
    val mealType: String = "وجبة", // فطور، غداء، عشاء، سناك
    val name: String = "",
    val description: String = "",
    val ingredients: List<String> = emptyList(),
    val portionSize: String = "",
    val estimatedCalories: Int = 0,
    val proteinGrams: Int = 0,
    val carbsGrams: Int = 0,
    val fatGrams: Int = 0,
    val healthyAlternatives: List<String> = emptyList()
)

@IgnoreExtraProperties
data class DietPlan(
    val id: String = "",
    val userId: String = "",
    val targetCalories: Int = 2000,
    val totalProtein: Int = 120,
    val totalCarbs: Int = 220,
    val totalFat: Int = 60,
    val generalAdvice: String = "",
    val meals: List<DietMeal> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class DailyFoodEntry(
    val id: String = "",
    val name: String = "",
    val mealType: String = "وجبة",
    val calories: Int = 0,
    val proteinGrams: Int = 0,
    val carbsGrams: Int = 0,
    val fatGrams: Int = 0,
    val portionDescription: String = "1 حصة",
    val portionMultiplier: Float = 1.0f,
    val timestampMillis: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class DailyNutritionLog(
    val dateKey: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val targetCalories: Int = 2000,
    val entries: List<DailyFoodEntry> = emptyList()
) {
    val totalCalories: Int get() = entries.sumOf { it.calories }
    val totalProtein: Int get() = entries.sumOf { it.proteinGrams }
    val totalCarbs: Int get() = entries.sumOf { it.carbsGrams }
    val totalFat: Int get() = entries.sumOf { it.fatGrams }
    val remainingCalories: Int get() = (targetCalories - totalCalories).coerceAtLeast(0)
    val progressRatio: Float get() = if (targetCalories > 0) (totalCalories.toFloat() / targetCalories).coerceIn(0f, 1f) else 0f
}

data class FoodScanResult(
    val productName: String,
    val baseCalories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val standardPortion: String,
    val notes: String = ""
)

enum class RoutineCategory(val labelAr: String) {
    MEAL("وجبة غذائية"),
    EXERCISE("رياضة ونشاط"),
    WORK("عمل ومهام"),
    STUDY("دراسة وقراءة"),
    PRAYER("عبادة وروحانيات"),
    REST("راحة ونوم"),
    OTHER("أخرى");

    companion object {
        fun fromString(name: String?): RoutineCategory =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: OTHER
    }
}

@IgnoreExtraProperties
data class DailyRoutineItem(
    val id: String = "",
    val title: String = "",
    val timeLabel: String = "08:00 ص",
    val timeMinutesFromMidnight: Int = 480, // for chronological sorting
    val category: String = RoutineCategory.WORK.name,
    val isCompleted: Boolean = false,
    val notes: String = "",
    val linkedMealName: String? = null
) {
    fun getTypedCategory(): RoutineCategory = RoutineCategory.fromString(category)
}
