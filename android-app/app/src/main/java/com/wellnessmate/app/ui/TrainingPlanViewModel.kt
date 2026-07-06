package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wellnessmate.app.data.SubscriberResponse
import com.wellnessmate.app.data.TrainingPlanRepository
import com.wellnessmate.app.data.TrainingPlanRequest
import com.wellnessmate.app.data.TrainingPlanResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrainingPlanUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val plans: List<TrainingPlanResponse> = emptyList(),
    val selected: TrainingPlanResponse? = null,
    val subscribers: List<SubscriberResponse> = emptyList(),
    val uploadingBlockIndex: Int? = null,
    val error: String? = null,
)

class TrainingPlanViewModel(private val repository: TrainingPlanRepository) : ViewModel() {
    private val _state = MutableStateFlow(TrainingPlanUiState())
    val state = _state.asStateFlow()
    init { refresh() }
    fun refresh() = viewModelScope.launch {
        repository.plans().fold(
            { _state.value = _state.value.copy(loading = false, plans = it, error = null) },
            { _state.value = _state.value.copy(loading = false, error = it.message) },
        )
    }
    fun select(plan: TrainingPlanResponse?) {
        _state.value = _state.value.copy(selected = plan, subscribers = emptyList())
    }
    fun checkIn() {
        val plan = _state.value.selected ?: return
        viewModelScope.launch { repository.checkIn(plan.id).fold(
            { updated -> _state.value = _state.value.copy(selected = updated,
                plans = _state.value.plans.map { if (it.id == updated.id) updated else it }) },
            { _state.value = _state.value.copy(error = it.message) },
        ) }
    }
    fun subscribe() {
        val plan = _state.value.selected ?: return
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch { repository.subscribe(plan.id).fold(
            { updated -> _state.value = _state.value.copy(saving = false, selected = updated,
                plans = _state.value.plans.map { if (it.id == updated.id) updated else it }) },
            { _state.value = _state.value.copy(saving = false, error = it.message) },
        ) }
    }
    fun unsubscribe() {
        val plan = _state.value.selected ?: return
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch { repository.unsubscribe(plan.id).fold(
            {
                val updated = _state.value.selected?.copy(subscribed = false, checkInCount = 0, checkedInToday = false)
                _state.value = _state.value.copy(saving = false, selected = updated,
                    plans = _state.value.plans.map { if (it.id == plan.id) it.copy(subscribed = false) else it })
            },
            { _state.value = _state.value.copy(saving = false, error = it.message) },
        ) }
    }
    fun loadSubscribers(planId: Long) {
        viewModelScope.launch { repository.subscribers(planId).fold(
            { _state.value = _state.value.copy(subscribers = it) },
            { _state.value = _state.value.copy(error = it.message) },
        ) }
    }
    fun create(request: TrainingPlanRequest, done: () -> Unit) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch { repository.create(request).fold(
            { plan -> _state.value = _state.value.copy(saving = false, plans = listOf(plan) + _state.value.plans); done() },
            { _state.value = _state.value.copy(saving = false, error = it.message) },
        ) }
    }
    fun update(id: Long, request: TrainingPlanRequest, done: () -> Unit) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch { repository.update(id, request).fold(
            { updated -> _state.value = _state.value.copy(saving = false,
                selected = updated,
                plans = _state.value.plans.map { if (it.id == updated.id) updated else it }); done() },
            { _state.value = _state.value.copy(saving = false, error = it.message) },
        ) }
    }
    fun delete(id: Long, done: () -> Unit) {
        viewModelScope.launch { repository.delete(id).fold(
            { _state.value = _state.value.copy(selected = null,
                plans = _state.value.plans.filter { it.id != id }); done() },
            { _state.value = _state.value.copy(error = it.message) },
        ) }
    }
    fun uploadBlockFile(index: Int, bytes: ByteArray, contentType: String, filename: String, onUrl: (String) -> Unit) {
        _state.value = _state.value.copy(uploadingBlockIndex = index)
        viewModelScope.launch {
            repository.uploadFile(bytes, contentType, filename).fold(
                { fileResp ->
                    _state.value = _state.value.copy(uploadingBlockIndex = null)
                    onUrl(fileResp.url)
                },
                { _state.value = _state.value.copy(uploadingBlockIndex = null, error = it.message) },
            )
        }
    }

    fun setUploading(index: Int?) { _state.value = _state.value.copy(uploadingBlockIndex = index) }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    companion object { fun factory(repository: TrainingPlanRepository): ViewModelProvider.Factory = factoryOf { TrainingPlanViewModel(repository) } }
}
