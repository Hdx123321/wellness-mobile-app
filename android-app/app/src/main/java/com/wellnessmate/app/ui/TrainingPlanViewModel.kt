package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    fun select(plan: TrainingPlanResponse?) { _state.value = _state.value.copy(selected = plan) }
    fun checkIn() {
        val plan = _state.value.selected ?: return
        viewModelScope.launch { repository.checkIn(plan.id).fold(
            { updated -> _state.value = _state.value.copy(selected = updated,
                plans = _state.value.plans.map { if (it.id == updated.id) updated else it }) },
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
    fun clearError() { _state.value = _state.value.copy(error = null) }
    companion object { fun factory(repository: TrainingPlanRepository): ViewModelProvider.Factory = factoryOf { TrainingPlanViewModel(repository) } }
}
