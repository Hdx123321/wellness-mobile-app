package com.alpinefitness.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alpinefitness.app.data.AiAdvisorMessageResponse
import com.alpinefitness.app.data.AiAdvisorRepository
import com.alpinefitness.app.data.AiAdvisorSessionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiAdvisorUiState(
    val loading: Boolean = true,
    val sending: Boolean = false,
    val sessions: List<AiAdvisorSessionResponse> = emptyList(),
    val selectedSessionId: Long? = null,
    val messages: List<AiAdvisorMessageResponse> = emptyList(),
    val streamingContent: String = "",
    val thinkingContent: String = "",
    val isThinking: Boolean = false,
    val showDrawer: Boolean = false,
    val error: String? = null,
)

class AiAdvisorViewModel(private val repository: AiAdvisorRepository) : ViewModel() {
    private val _state = MutableStateFlow(AiAdvisorUiState())
    val state: StateFlow<AiAdvisorUiState> = _state.asStateFlow()
    private var streamJob: Job? = null

    init { refreshSessions() }

    // ── Sessions ──

    fun refreshSessions() {
        viewModelScope.launch {
            repository.sessions().fold(
                onSuccess = { sessions ->
                    val selected = _state.value.selectedSessionId
                        ?.takeIf { id -> sessions.any { it.id == id } }
                        ?: sessions.firstOrNull()?.id
                    _state.value = _state.value.copy(loading = false, sessions = sessions, selectedSessionId = selected)
                    if (selected != null) loadMessages(selected)
                    else _state.value = _state.value.copy(loading = false)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            repository.createSession().fold(
                onSuccess = { session ->
                    cancelStreaming()
                    _state.value = _state.value.copy(
                        sessions = listOf(session) + _state.value.sessions,
                        selectedSessionId = session.id, messages = emptyList(),
                        streamingContent = "", thinkingContent = "", isThinking = false, showDrawer = false,
                    )
                },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repository.deleteSession(id).fold(
                onSuccess = {
                    val sessions = _state.value.sessions.filter { it.id != id }
                    val newSelected = if (_state.value.selectedSessionId == id) sessions.firstOrNull()?.id else _state.value.selectedSessionId
                    if (_state.value.selectedSessionId == id) cancelStreaming()
                    _state.value = _state.value.copy(
                        sessions = sessions, selectedSessionId = newSelected,
                        messages = if (newSelected == null) emptyList() else _state.value.messages,
                        streamingContent = if (newSelected == null) "" else _state.value.streamingContent,
                        thinkingContent = if (newSelected == null) "" else _state.value.thinkingContent,
                        isThinking = if (newSelected == null) false else _state.value.isThinking,
                    )
                    if (newSelected != null) loadMessages(newSelected)
                },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
        }
    }

    fun renameSession(id: Long, title: String) {
        viewModelScope.launch {
            repository.renameSession(id, title).fold(
                onSuccess = { updated ->
                    _state.value = _state.value.copy(
                        sessions = _state.value.sessions.map { if (it.id == id) updated else it },
                    )
                },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
        }
    }

    fun selectSession(id: Long) {
        cancelStreaming()
        _state.value = _state.value.copy(selectedSessionId = id, messages = emptyList(), streamingContent = "", thinkingContent = "", isThinking = false, showDrawer = false)
        loadMessages(id)
    }

    // ── Messages ──

    private fun loadMessages(sessionId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repository.messagesForSession(sessionId).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, messages = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun send(content: String, onSent: () -> Unit, onCompleted: () -> Unit = {}) {
        if (content.isBlank() || _state.value.sending) return
        val existingId = _state.value.selectedSessionId
        _state.value = _state.value.copy(sending = true, error = null, streamingContent = "", thinkingContent = "", isThinking = false)

        val temporaryUser = AiAdvisorMessageResponse(
            id = -System.currentTimeMillis(), role = "USER", content = content.trim(), createdAt = "",
        )
        _state.value = _state.value.copy(messages = _state.value.messages + temporaryUser, thinkingContent = "", isThinking = false)
        onSent()

        streamJob = viewModelScope.launch {
            val sessionId = existingId ?: run {
                repository.createSession().fold(
                    onSuccess = { session ->
                        _state.value = _state.value.copy(
                            sessions = listOf(session) + _state.value.sessions,
                            selectedSessionId = session.id,
                        )
                        session.id
                    },
                    onFailure = {
                        _state.value = _state.value.copy(sending = false, error = it.message)
                        return@launch
                    },
                )
            }
            if (sessionId == null) return@launch
            repository.sendStreamToSession(sessionId, content.trim(),
                onThinkingToken = { token ->
                    if (_state.value.selectedSessionId == sessionId)
                        _state.value = _state.value.copy(
                            isThinking = true,
                            thinkingContent = _state.value.thinkingContent + token,
                        )
                },
                onToken = { token ->
                    if (_state.value.selectedSessionId == sessionId) {
                        _state.value = _state.value.copy(
                            isThinking = false,
                            streamingContent = _state.value.streamingContent + token,
                        )
                    }
                },
            ).fold(
                onSuccess = { response ->
                    if (_state.value.selectedSessionId == sessionId)
                        _state.value = _state.value.copy(
                            sending = false, streamingContent = "", thinkingContent = "", isThinking = false,
                            messages = _state.value.messages + response,
                        )
                    onCompleted()
                    refreshSessions()
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(sending = false, streamingContent = "", thinkingContent = "", isThinking = false, error = error.message)
                },
            )
        }
    }

    fun toggleDrawer() { _state.value = _state.value.copy(showDrawer = !_state.value.showDrawer) }
    fun closeDrawer() { _state.value = _state.value.copy(showDrawer = false) }
    fun cancelStreaming() { streamJob?.cancel() }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    companion object {
        fun factory(repository: AiAdvisorRepository): ViewModelProvider.Factory = factoryOf {
            AiAdvisorViewModel(repository)
        }
    }
}
