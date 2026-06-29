package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wellnessmate.app.data.AiAdvisorMessageResponse
import com.wellnessmate.app.data.AiAdvisorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiAdvisorUiState(
    val loading: Boolean = true,
    val sending: Boolean = false,
    val messages: List<AiAdvisorMessageResponse> = emptyList(),
    val error: String? = null,
)

class AiAdvisorViewModel(private val repository: AiAdvisorRepository) : ViewModel() {
    private val _state = MutableStateFlow(AiAdvisorUiState())
    val state: StateFlow<AiAdvisorUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            repository.messages().fold(
                onSuccess = { _state.value = AiAdvisorUiState(loading = false, messages = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun send(content: String, onSent: () -> Unit) {
        if (content.isBlank() || _state.value.sending) return
        _state.value = _state.value.copy(sending = true, error = null)
        viewModelScope.launch {
            repository.send(content).fold(
                onSuccess = { response ->
                    val temporaryUser = AiAdvisorMessageResponse(
                        id = -System.currentTimeMillis(), role = "USER", content = content.trim(),
                        createdAt = response.createdAt,
                    )
                    _state.value = _state.value.copy(
                        sending = false,
                        messages = _state.value.messages + temporaryUser + response,
                    )
                    onSent()
                },
                onFailure = { _state.value = _state.value.copy(sending = false, error = it.message) },
            )
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    companion object {
        fun factory(repository: AiAdvisorRepository): ViewModelProvider.Factory = factoryOf {
            AiAdvisorViewModel(repository)
        }
    }
}
