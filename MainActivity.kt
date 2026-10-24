package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.diet.DietViewModel
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.routine.RoutineViewModel
import com.example.ui.splash.SplashScreen
import com.example.ui.task.TaskViewModel
import com.example.ui.theme.NithamiTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

private enum class RootNavigationState {
    SPLASH,
    AUTH,
    MAIN
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var userDarkThemeOverride by remember { mutableStateOf<Boolean?>(true) }
            val activeDarkTheme = userDarkThemeOverride ?: true

            NithamiTheme(darkTheme = activeDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRootContent(
                        isDarkTheme = activeDarkTheme,
                        onToggleDarkTheme = {
                            userDarkThemeOverride = !activeDarkTheme
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppRootContent(
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val rootState = when {
        showSplash -> RootNavigationState.SPLASH
        currentUser == null -> RootNavigationState.AUTH
        else -> RootNavigationState.MAIN
    }

    Crossfade(
        targetState = rootState,
        animationSpec = tween(durationMillis = 350),
        label = "root_navigation_crossfade"
    ) { state ->
        when (state) {
            RootNavigationState.SPLASH -> {
                SplashScreen(
                    onSplashFinished = { showSplash = false }
                )
            }
            RootNavigationState.AUTH -> {
                AuthScreen(
                    onAuthSuccess = {
                        currentUser = Firebase.auth.currentUser
                    }
                )
            }
            RootNavigationState.MAIN -> {
                val taskViewModel: TaskViewModel = viewModel()
                val dietViewModel: DietViewModel = viewModel()
                val routineViewModel: RoutineViewModel = viewModel()

                MainAppNavigation(
                    taskViewModel = taskViewModel,
                    dietViewModel = dietViewModel,
                    routineViewModel = routineViewModel,
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onSignOut = {
                        currentUser = null
                    }
                )
            }
        }
    }
}
