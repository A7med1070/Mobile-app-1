package com.example

import com.example.data.model.ActivityLevel
import com.example.data.model.DailyFoodEntry
import com.example.data.model.DailyNutritionLog
import com.example.data.model.DietGoal
import com.example.data.model.RoutineCategory
import com.example.data.model.UserDietProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testUserDietProfileBmrCalculation() {
        val profile = UserDietProfile(
            age = 25,
            heightCm = 180f,
            weightKg = 80f,
            gender = "MALE",
            activityLevel = ActivityLevel.MODERATELY_ACTIVE.name,
            goal = DietGoal.LOSE_WEIGHT.name
        )
        val calculatedTarget = profile.calculateBmr()
        // Expected BMR: (10*80) + (6.25*180) - (5*25) + 5 = 800 + 1125 - 125 + 5 = 1805
        // TDEE: 1805 * 1.55 = 2797.75
        // Lose weight: 2797 - 450 = ~2347
        assertTrue("Target calories should be reasonably calculated", calculatedTarget in 2000..2600)
    }

    @Test
    fun testDailyNutritionLogCalculations() {
        val entries = listOf(
            DailyFoodEntry(id = "1", name = "إفطار صحي", calories = 450, proteinGrams = 30, carbsGrams = 50, fatGrams = 15),
            DailyFoodEntry(id = "2", name = "غداء متكامل", calories = 650, proteinGrams = 45, carbsGrams = 70, fatGrams = 20)
        )
        val log = DailyNutritionLog(
            dateKey = "2026-10-21",
            targetCalories = 2000,
            entries = entries
        )

        assertEquals(1100, log.totalCalories)
        assertEquals(75, log.totalProtein)
        assertEquals(120, log.totalCarbs)
        assertEquals(35, log.totalFat)
        assertEquals(900, log.remainingCalories)
        assertEquals(0.55f, log.progressRatio, 0.01f)
    }

    @Test
    fun testRoutineCategoryParsing() {
        assertEquals(RoutineCategory.MEAL, RoutineCategory.fromString("MEAL"))
        assertEquals(RoutineCategory.EXERCISE, RoutineCategory.fromString("EXERCISE"))
        assertEquals(RoutineCategory.WORK, RoutineCategory.fromString("WORK"))
        assertEquals(RoutineCategory.OTHER, RoutineCategory.fromString("UNKNOWN_XYZ"))
    }

    @Test
    fun testClinicalDietPlanGeneration() {
        val service = com.example.data.remote.GeminiDietService()
        val profile = UserDietProfile(
            age = 30,
            heightCm = 175f,
            weightKg = 75f,
            gender = "MALE",
            activityLevel = ActivityLevel.MODERATELY_ACTIVE.name,
            goal = DietGoal.LOSE_WEIGHT.name,
            mealsCount = 3
        )
        val plan = service.generateClinicalDietPlan(profile)
        assertTrue("Generated plan should have valid calories", plan.targetCalories > 1500)
        assertTrue("Generated plan should have at least 3 meals", plan.meals.size >= 3)
        assertTrue("General advice should not be empty", plan.generalAdvice.isNotBlank())
    }

    @Test
    fun testTaskPriorityOrdering() {
        val urgent = com.example.data.model.TaskPriority.URGENT
        val high = com.example.data.model.TaskPriority.HIGH
        val medium = com.example.data.model.TaskPriority.MEDIUM
        val low = com.example.data.model.TaskPriority.LOW

        assertEquals("عاجل", urgent.labelAr)
        assertEquals("مرتفع", high.labelAr)
        assertEquals("متوسط", medium.labelAr)
        assertEquals("منخفض", low.labelAr)
    }
}

