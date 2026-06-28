package com.wellnessmate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wellnessmate.app.data.AppContainer
import com.wellnessmate.app.ui.AuthViewModel
import com.wellnessmate.app.ui.OnboardingViewModel
import com.wellnessmate.app.ui.SessionState
import com.wellnessmate.app.ui.TrackerViewModel
import com.wellnessmate.app.ui.auth.LoginRegisterScreen
import com.wellnessmate.app.ui.onboarding.OnboardingScreen
import com.wellnessmate.app.ui.tracker.MainTrackerNav

/** Hosts the complete Android authentication, onboarding, and tracker flow. @author TODO(team member) */
class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
            if (current.user.onboardingRequired) {
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
                MainTrackerNav(
                    user = current.user,
                    viewModel = trackerViewModel,
                    onLogout = authViewModel::logout,
                )
            }
        }
    }
}
