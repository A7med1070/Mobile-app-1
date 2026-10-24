package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.example.ui.diet.DietScreen
import com.example.ui.diet.DietViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.routine.RoutineScreen
import com.example.ui.routine.RoutineViewModel
import com.example.ui.task.TaskDashboardScreen
import com.example.ui.task.TaskViewModel

enum class NavigationDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("الرئيسية", Icons.Filled.Home, Icons.Outlined.Home, "nav_item_home"),
    DIET("الغذاء و AI", Icons.Filled.Restaurant, Icons.Outlined.Restaurant, "nav_item_diet"),
    ROUTINE("الروتين", Icons.Filled.ViewTimeline, Icons.Outlined.ViewTimeline, "nav_item_routine"),
    TASKS("المهام", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt, "nav_item_tasks"),
    PROFILE("حسابي", Icons.Filled.Person, Icons.Outlined.Person, "nav_item_profile")
}

@Composable
fun MainAppNavigation(
    taskViewModel: TaskViewModel,
    dietViewModel: DietViewModel,
    routineViewModel: RoutineViewModel,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(NavigationDestination.HOME) }

    // Back handler to return to HOME if user presses system back
    if (currentDestination != NavigationDestination.HOME) {
        BackHandler {
            currentDestination = NavigationDestination.HOME
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationDestination.entries.forEach { destination ->
                    val isSelected = currentDestination == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.title
                            )
                        },
                        label = {
                            Text(text = destination.title, fontSize = 11.sp)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(destination.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "NavigationTransition"
            ) { destination ->
                when (destination) {
                    NavigationDestination.HOME -> {
                        HomeScreen(
                            taskViewModel = taskViewModel,
                            dietViewModel = dietViewModel,
                            routineViewModel = routineViewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = onToggleDarkTheme,
                            onNavigateToDiet = { currentDestination = NavigationDestination.DIET },
                            onNavigateToTasks = { currentDestination = NavigationDestination.TASKS },
                            onNavigateToRoutine = { currentDestination = NavigationDestination.ROUTINE },
                            onNavigateToProfile = { currentDestination = NavigationDestination.PROFILE }
                        )
                    }
                    NavigationDestination.DIET -> {
                        DietScreen(
                            viewModel = dietViewModel
                        )
                    }
                    NavigationDestination.ROUTINE -> {
                        RoutineScreen(
                            viewModel = routineViewModel
                        )
                    }
                    NavigationDestination.TASKS -> {
                        TaskDashboardScreen(
                            viewModel = taskViewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = onToggleDarkTheme,
                            onSignOut = onSignOut
                        )
                    }
                    NavigationDestination.PROFILE -> {
                        ProfileScreen(
                            viewModel = taskViewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = onToggleDarkTheme,
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
    }
}
