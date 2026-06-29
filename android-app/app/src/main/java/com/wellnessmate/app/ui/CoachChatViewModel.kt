package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wellnessmate.app.data.CoachChatRepository
import com.wellnessmate.app.data.CoachConversationResponse
import com.wellnessmate.app.data.CoachMessageResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CoachChatUiState(
    val loading: Boolean = true,
    val sending: Boolean = false,
    val conversations: List<CoachConversationResponse> = emptyList(),
    val selectedConversationId: Long? = null,
    val messages: List<CoachMessageResponse> = emptyList(),
    val error: String? = null,
)

class CoachChatViewModel(private val repository: CoachChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(CoachChatUiState())
    val state: StateFlow<CoachChatUiState> = _state.asStateFlow()

    init {
        refreshConversations()
        viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                refreshMessages(silent = true)
            }
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            repository.conversations().fold(
                onSuccess = { conversations ->
                    val selected = _state.value.selectedConversationId
                        ?.takeIf { id -> conversations.any { it.id == id } }
                        ?: conversations.firstOrNull()?.id
                    _state.value = _state.value.copy(
                        loading = false,
                        conversations = conversations,
                        selectedConversationId = selected,
                        error = null,
                    )
                    refreshMessages(silent = true)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun selectConversation(id: Long) {
        _state.value = _state.value.copy(selectedConversationId = id, messages = emptyList())
        refreshMessages(silent = false)
    }

    fun refreshMessages(silent: Boolean = false) {
        val conversationId = _state.value.selectedConversationId ?: return
        val afterId = _state.value.messages.lastOrNull()?.id ?: 0
        viewModelScope.launch {
            repository.messages(conversationId, afterId).fold(
                onSuccess = { incoming ->
                    if (incoming.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            messages = (_state.value.messages + incoming).distinctBy { it.id },
                            error = null,
                        )
                    }
                },
                onFailure = { if (!silent) _state.value = _state.value.copy(error = it.message) },
            )
        }
    }

    fun send(content: String, onSent: () -> Unit) {
        val conversationId = _state.value.selectedConversationId ?: return
        if (content.isBlank() || _state.value.sending) return
        _state.value = _state.value.copy(sending = true, error = null)
        viewModelScope.launch {
            repository.send(conversationId, content).fold(
                onSuccess = { message ->
                    _state.value = _state.value.copy(
                        sending = false,
                        messages = (_state.value.messages + message).distinctBy { it.id },
                    )
                    onSent()
                },
                onFailure = { _state.value = _state.value.copy(sending = false, error = it.message) },
            )
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    companion object {
        fun factory(repository: CoachChatRepository): ViewModelProvider.Factory = factoryOf {
            CoachChatViewModel(repository)
        }
    }
}
