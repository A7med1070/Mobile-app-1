package com.example.ui.diet

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DailyFoodEntry
import com.example.data.model.DailyNutritionLog
import com.example.data.model.DietMeal
import com.example.data.model.DietPlan
import com.example.data.model.FoodScanResult
import com.example.data.model.UserDietProfile
import com.example.data.remote.GeminiDietService
import com.example.data.repository.NutritionRepository
import com.example.data.repository.RoutineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface DietPlanUiState {
    data object Idle : DietPlanUiState
    data object Loading : DietPlanUiState
    data class Success(val plan: DietPlan) : DietPlanUiState
    data class Error(val message: String) : DietPlanUiState
}

sealed interface FoodScanUiState {
    data object Idle : FoodScanUiState
    data object Scanning : FoodScanUiState
    data class Success(val result: FoodScanResult, val scannedBitmap: Bitmap? = null) : FoodScanUiState
    data class Error(val message: String) : FoodScanUiState
}

class DietViewModel(application: Application) : AndroidViewModel(application) {

    private val nutritionRepository = NutritionRepository(application)
    private val routineRepository = RoutineRepository(application)
    private val geminiService = GeminiDietService()

    private val todayKey: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    val userProfile: StateFlow<UserDietProfile> = nutritionRepository.observeDietProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserDietProfile()
        )

    val currentDietPlan: StateFlow<DietPlan?> = nutritionRepository.observeDietPlan()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val todayNutritionLog: StateFlow<DailyNutritionLog> = nutritionRepository.observeDailyLog(todayKey)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DailyNutritionLog(dateKey = todayKey)
        )

    private val _planGenerationState = MutableStateFlow<DietPlanUiState>(DietPlanUiState.Idle)
    val planGenerationState: StateFlow<DietPlanUiState> = _planGenerationState.asStateFlow()

    private val _foodScanState = MutableStateFlow<FoodScanUiState>(FoodScanUiState.Idle)
    val foodScanState: StateFlow<FoodScanUiState> = _foodScanState.asStateFlow()

    fun resetScanState() {
        _foodScanState.value = FoodScanUiState.Idle
    }

    fun saveProfile(profile: UserDietProfile, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                nutritionRepository.saveDietProfile(profile)
                onSuccess()
            } catch (e: Exception) {
                // profile save handled
            }
        }
    }

    fun generateDietPlan(profile: UserDietProfile) {
        _planGenerationState.value = DietPlanUiState.Loading
        viewModelScope.launch {
            try {
                try {
                    nutritionRepository.saveDietProfile(profile)
                } catch (e: Exception) {
                    Log.w("DietViewModel", "Failed to cache diet profile: ${e.message}")
                }

                val result = geminiService.generateDietPlan(profile)
                result.fold(
                    onSuccess = { plan ->
                        try {
                            nutritionRepository.saveDietPlan(plan)
                        } catch (e: Exception) {
                            Log.w("DietViewModel", "Failed to save diet plan to Firestore: ${e.message}")
                        }

                        try {
                            nutritionRepository.updateDailyTargetCalories(todayKey, plan.targetCalories)
                        } catch (e: Exception) {
                            Log.w("DietViewModel", "Failed to update daily target calories: ${e.message}")
                        }

                        try {
                            routineRepository.populateRoutineFromDiet(plan)
                        } catch (e: Exception) {
                            Log.w("DietViewModel", "Failed to populate routine from diet: ${e.message}")
                        }

                        _planGenerationState.value = DietPlanUiState.Success(plan)
                    },
                    onFailure = { error ->
                        _planGenerationState.value = DietPlanUiState.Error(
                            error.localizedMessage ?: "حدث خطأ أثناء إنشاء النظام الغذائي بالذكاء الاصطناعي."
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("DietViewModel", "Error in plan generation flow", e)
                _planGenerationState.value = DietPlanUiState.Error(
                    e.localizedMessage ?: "فشل الاتصال بالذكاء الاصطناعي."
                )
            }
        }
    }

    fun scanFoodImage(bitmap: Bitmap) {
        _foodScanState.value = FoodScanUiState.Scanning
        viewModelScope.launch {
            try {
                val result = geminiService.scanFoodImage(bitmap)
                result.fold(
                    onSuccess = { scanResult ->
                        _foodScanState.value = FoodScanUiState.Success(scanResult, bitmap)
                    },
                    onFailure = { error ->
                        _foodScanState.value = FoodScanUiState.Error(
                            error.localizedMessage ?: "تعذر تحليل صورة المنتج الغذائي."
                        )
                    }
                )
            } catch (e: Exception) {
                _foodScanState.value = FoodScanUiState.Error(
                    e.localizedMessage ?: "حدث خطأ غير متوقع أثناء الفحص."
                )
            }
        }
    }

    fun addScannedFoodToDailyLog(
        result: FoodScanResult,
        multiplier: Float,
        mealType: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val entry = DailyFoodEntry(
                id = UUID.randomUUID().toString(),
                name = result.productName,
                mealType = mealType,
                calories = (result.baseCalories * multiplier).toInt(),
                proteinGrams = (result.proteinGrams * multiplier).toInt(),
                carbsGrams = (result.carbsGrams * multiplier).toInt(),
                fatGrams = (result.fatGrams * multiplier).toInt(),
                portionDescription = "${result.standardPortion} (×$multiplier)",
                portionMultiplier = multiplier,
                timestampMillis = System.currentTimeMillis()
            )
            val currentTarget = todayNutritionLog.value.targetCalories
            nutritionRepository.addFoodEntry(todayKey, entry, currentTarget)
            _foodScanState.value = FoodScanUiState.Idle
            onComplete()
        }
    }

    fun addMealToDailyLog(meal: DietMeal, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val entry = DailyFoodEntry(
                id = UUID.randomUUID().toString(),
                name = meal.name,
                mealType = meal.mealType,
                calories = meal.estimatedCalories,
                proteinGrams = meal.proteinGrams,
                carbsGrams = meal.carbsGrams,
                fatGrams = meal.fatGrams,
                portionDescription = meal.portionSize,
                portionMultiplier = 1.0f,
                timestampMillis = System.currentTimeMillis()
            )
            val currentTarget = todayNutritionLog.value.targetCalories
            nutritionRepository.addFoodEntry(todayKey, entry, currentTarget)
            onComplete()
        }
    }

    fun addManualFoodEntry(
        name: String,
        calories: Int,
        protein: Int,
        carbs: Int,
        fat: Int,
        mealType: String,
        portion: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val entry = DailyFoodEntry(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                mealType = mealType,
                calories = calories,
                proteinGrams = protein,
                carbsGrams = carbs,
                fatGrams = fat,
                portionDescription = portion.trim().ifEmpty { "1 حصة" },
                portionMultiplier = 1.0f,
                timestampMillis = System.currentTimeMillis()
            )
            val currentTarget = todayNutritionLog.value.targetCalories
            nutritionRepository.addFoodEntry(todayKey, entry, currentTarget)
            onComplete()
        }
    }

    fun deleteFoodEntry(entryId: String) {
        viewModelScope.launch {
            try {
                nutritionRepository.deleteFoodEntry(todayKey, entryId)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun updateTargetCalories(newTarget: Int) {
        viewModelScope.launch {
            try {
                nutritionRepository.updateDailyTargetCalories(todayKey, newTarget)
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}
