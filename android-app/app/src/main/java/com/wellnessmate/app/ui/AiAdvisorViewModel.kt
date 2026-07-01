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
    val streamingContent: String = "",  // partial token text while AI is streaming
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
        _state.value = _state.value.copy(sending = true, error = null, streamingContent = "")

        val temporaryUser = AiAdvisorMessageResponse(
            id = -System.currentTimeMillis(), role = "USER", content = content.trim(),
            createdAt = "",
        )
        _state.value = _state.value.copy(
            messages = _state.value.messages + temporaryUser,
        )

        viewModelScope.launch {
            repository.sendStream(content.trim()) { token ->
                _state.value = _state.value.copy(streamingContent = _state.value.streamingContent + token)
            }.fold(
                onSuccess = { response ->
                    _state.value = _state.value.copy(
                        sending = false,
                        streamingContent = "",
                        messages = _state.value.messages + response,
                    )
                    onSent()
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        sending = false,
                        streamingContent = "",
                        error = error.message,
                    )
                },
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
