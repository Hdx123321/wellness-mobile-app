package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wellnessmate.app.data.AuthRepository
import com.wellnessmate.app.data.SessionManager
import com.wellnessmate.app.data.SessionUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object SignedOut : SessionState
    data class SignedIn(val user: SessionUser) : SessionState
}

data class AuthUiState(val submitting: Boolean = false, val error: String? = null)

/** Owns authentication, session restoration, and logout state. @author TODO(team member) */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _session = MutableStateFlow<SessionState>(
        repository.restoredSession()?.let(SessionState::SignedIn) ?: SessionState.SignedOut,
    )
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // 全局会话过期监听：任何 API 返回 401 时，自动跳转到登录页
        viewModelScope.launch {
            SessionManager.expired.collect { expired ->
                if (expired) {
                    SessionManager.reset()
                    logout()
                }
            }
        }
    }

    fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(error = "Username/email and password are required.")
            return
        }
        submit { repository.login(identifier, password) }
    }

    fun register(username: String, email: String, password: String, displayName: String) {
        if (username.length < 3 || !email.contains('@') || password.length < 8) {
            _uiState.value = AuthUiState(error = "Use a valid username, email, and password of at least 8 characters.")
            return
        }
        submit { repository.register(username, email, password, displayName.ifBlank { null }) }
    }

    fun markOnboardingComplete() {
        repository.markOnboardingComplete()?.let { _session.value = SessionState.SignedIn(it) }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _session.value = SessionState.SignedOut
            _uiState.value = AuthUiState()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun submit(block: suspend () -> Result<SessionUser>) {
        if (_uiState.value.submitting) return
        _uiState.value = AuthUiState(submitting = true)
        viewModelScope.launch {
            block().fold(
                onSuccess = {
                    _session.value = SessionState.SignedIn(it)
                    _uiState.value = AuthUiState()
                },
                onFailure = { _uiState.value = AuthUiState(error = it.message) },
            )
        }
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = factoryOf {
            AuthViewModel(repository)
        }
    }
}

internal fun <T : ViewModel> factoryOf(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
