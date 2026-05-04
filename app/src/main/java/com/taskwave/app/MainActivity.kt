package com.taskwave.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskwave.app.ui.screens.OnboardingScreen
import com.taskwave.app.ui.screens.TodoScreen
import com.taskwave.app.ui.theme.TaskWaveTheme
import com.taskwave.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()

            TaskWaveTheme(darkModeOverride = state.darkModeOverride) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!state.isLoading) {
                        AnimatedContent(
                            targetState = state.onboardingDone,
                            transitionSpec = {
                                fadeIn(tween(500)).togetherWith(fadeOut(tween(300)))
                            },
                            label = "nav"
                        ) { done ->
                            if (done) {
                                TodoScreen(vm = vm)
                            } else {
                                OnboardingScreen(onFinish = { vm.completeOnboarding() })
                            }
                        }
                    }
                }
            }
        }
    }
}
