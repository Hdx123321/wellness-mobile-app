package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wellnessmate.app.data.OnboardingQuestion
import com.wellnessmate.app.data.OnboardingRepository
import com.wellnessmate.app.data.OnboardingRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val questions: List<OnboardingQuestion> = emptyList(),
    val error: String? = null,
)

/** Loads the questionnaire contract and submits the private profile. @author TODO(team member) */
class OnboardingViewModel(private val repository: OnboardingRepository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = OnboardingUiState(loading = true)
        viewModelScope.launch {
            repository.questions().fold(
                onSuccess = { _state.value = OnboardingUiState(loading = false, questions = it) },
                onFailure = { _state.value = OnboardingUiState(loading = false, error = it.message) },
            )
        }
    }

    fun save(request: OnboardingRequest, onSaved: () -> Unit) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            repository.save(request).fold(
                onSuccess = {
                    _state.value = _state.value.copy(saving = false)
                    onSaved()
                },
                onFailure = { _state.value = _state.value.copy(saving = false, error = it.message) },
            )
        }
    }

    companion object {
        fun factory(repository: OnboardingRepository): ViewModelProvider.Factory = factoryOf {
            OnboardingViewModel(repository)
        }
    }
}
