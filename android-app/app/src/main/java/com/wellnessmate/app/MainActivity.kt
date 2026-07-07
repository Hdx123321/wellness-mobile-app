package com.alpinefitness.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alpinefitness.app.data.AppContainer
import com.alpinefitness.app.ui.AuthViewModel
import com.alpinefitness.app.ui.OnboardingViewModel
import com.alpinefitness.app.ui.FoodViewModel
import com.alpinefitness.app.ui.SessionState
import com.alpinefitness.app.ui.TrackerViewModel
import com.alpinefitness.app.ui.CoachChatViewModel
import com.alpinefitness.app.ui.HealthProfileViewModel
import com.alpinefitness.app.ui.AiAdvisorViewModel
import com.alpinefitness.app.ui.TrainingPlanViewModel
import com.alpinefitness.app.ui.auth.LoginRegisterScreen
import com.alpinefitness.app.ui.onboarding.OnboardingScreen
import com.alpinefitness.app.ui.tracker.MainTrackerNav

/** Hosts the complete Android authentication, onboarding, and tracker flow. @author TODO(team member) */
class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val wellnessGreen = Color(0xFF5DB130)
            val wellnessColorScheme = lightColorScheme(
                primary = wellnessGreen,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDCE8D4),
                onPrimaryContainer = Color(0xFF1A3700),
                secondary = Color(0xFF586249),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFDCE8D4),
                onSecondaryContainer = Color(0xFF161E0A),
            )
            MaterialTheme(colorScheme = wellnessColorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WellnessMateApp(container)
                }
            }
        }
    }
}

@Composable
private fun WellnessMateApp(container: AppContainer) {
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(container.authRepository),
    )
    val session by authViewModel.session.collectAsState()

    when (val current = session) {
        SessionState.SignedOut -> LoginRegisterScreen(authViewModel)
        is SessionState.SignedIn -> {
            if (current.user.onboardingRequired && current.user.role != "COACH") {
                val onboardingViewModel: OnboardingViewModel = viewModel(
                    factory = OnboardingViewModel.factory(container.onboardingRepository),
                )
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onCompleted = authViewModel::markOnboardingComplete,
                    onLogout = authViewModel::logout,
                )
            } else {
                val trackerViewModel: TrackerViewModel = viewModel(
                    factory = TrackerViewModel.factory(container.trackerRepository),
                )
                val foodViewModel: FoodViewModel = viewModel(
                    factory = FoodViewModel.factory(container.foodRepository),
                )
                val coachChatViewModel: CoachChatViewModel = viewModel(
                    factory = CoachChatViewModel.factory(container.coachChatRepository),
                )
                val healthProfileViewModel: HealthProfileViewModel = viewModel(
                    factory = HealthProfileViewModel.factory(
                        container.healthProfileRepository,
                        container.trackerRepository,
                    ),
                )
                val aiAdvisorViewModel: AiAdvisorViewModel = viewModel(
                    factory = AiAdvisorViewModel.factory(container.aiAdvisorRepository),
                )
                val trainingPlanViewModel: TrainingPlanViewModel = viewModel(
                    factory = TrainingPlanViewModel.factory(container.trainingPlanRepository),
                )
                MainTrackerNav(
                    user = current.user,
                    viewModel = trackerViewModel,
                    foodViewModel = foodViewModel,
                    coachChatViewModel = coachChatViewModel,
                    healthProfileViewModel = healthProfileViewModel,
                    aiAdvisorViewModel = aiAdvisorViewModel,
                    trainingPlanViewModel = trainingPlanViewModel,
                    onLogout = authViewModel::logout,
                )
            }
        }
    }
}
