package com.example.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DailyRoutineItem
import com.example.data.model.DietPlan
import com.example.data.model.RoutineCategory
import com.example.data.repository.NutritionRepository
import com.example.data.repository.RoutineRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineStats(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val remainingCount: Int = 0,
    val progressRatio: Float = 0f
)

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val routineRepository = RoutineRepository(application)
    private val nutritionRepository = NutritionRepository(application)

    val routineItems: StateFlow<List<DailyRoutineItem>> = routineRepository.observeRoutineItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val routineStats: StateFlow<RoutineStats> = routineItems.combine(routineItems) { items, _ ->
        val total = items.size
        val completed = items.count { it.isCompleted }
        RoutineStats(
            totalCount = total,
            completedCount = completed,
            remainingCount = (total - completed).coerceAtLeast(0),
            progressRatio = if (total > 0) completed.toFloat() / total else 0f
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoutineStats()
    )

    init {
        // Automatically check if starter routine should be populated
        viewModelScope.launch {
            try {
                val currentPlan = nutritionRepository.observeDietPlan().firstOrNull()
                routineRepository.populateRoutineFromDiet(currentPlan)
            } catch (e: Exception) {
                // Ignore initialization error
            }
        }
    }

    fun toggleItem(item: DailyRoutineItem) {
        viewModelScope.launch {
            try {
                routineRepository.toggleRoutineItem(item.id, item.isCompleted)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun addItem(
        title: String,
        timeLabel: String,
        timeMinutes: Int,
        category: RoutineCategory,
        notes: String = "",
        linkedMealName: String? = null
    ) {
        viewModelScope.launch {
            try {
                val item = DailyRoutineItem(
                    title = title.trim(),
                    timeLabel = timeLabel,
                    timeMinutesFromMidnight = timeMinutes,
                    category = category.name,
                    notes = notes.trim(),
                    linkedMealName = linkedMealName
                )
                routineRepository.addRoutineItem(item)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            try {
                routineRepository.deleteRoutineItem(itemId)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun resetTodayProgress() {
        viewModelScope.launch {
            try {
                routineRepository.resetDailyCompletion()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun syncWithDietPlan(plan: DietPlan) {
        viewModelScope.launch {
            try {
                routineRepository.populateRoutineFromDiet(plan)
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}
