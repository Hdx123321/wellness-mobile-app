package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wellnessmate.app.data.TrackerEntryRequest
import com.wellnessmate.app.data.TrackerEntryResponse
import com.wellnessmate.app.data.TrackerRepository
import com.wellnessmate.app.data.TrackerTypeResponse
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrackerUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val types: List<TrackerTypeResponse> = emptyList(),
    val entries: List<TrackerEntryResponse> = emptyList(),
    val error: String? = null,
)

/** Owns tracker catalog, calendar data, create/edit, and delete state. @author TODO(team member) */
class TrackerViewModel(private val repository: TrackerRepository) : ViewModel() {
    private val _state = MutableStateFlow(TrackerUiState())
    val state: StateFlow<TrackerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val types = repository.types()
            val zone = ZoneId.systemDefault()
            val from = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant().toString()
            val to = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toString()
            val entries = repository.entries(from = from, to = to)
            if (types.isSuccess && entries.isSuccess) {
                _state.value = TrackerUiState(
                    loading = false,
                    types = types.getOrThrow(),
                    entries = entries.getOrThrow(),
                )
            } else {
                _state.value = _state.value.copy(
                    loading = false,
                    error = types.exceptionOrNull()?.message ?: entries.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun loadMonth(type: String, month: YearMonth) {
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).minusDays(6).atStartOfDay(zone).toInstant().toString()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toString()
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repository.entries(type, from, to).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, entries = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun loadDate(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val from = date.minusDays(6).atStartOfDay(zone).toInstant().toString()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toString()
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repository.entries(from = from, to = to).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, entries = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun save(id: Long?, request: TrackerEntryRequest, onSaved: () -> Unit) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            val result = if (id == null) repository.create(request) else repository.update(id, request)
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(saving = false)
                    loadDate(Instant.parse(request.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate())
                    onSaved()
                },
                onFailure = { _state.value = _state.value.copy(saving = false, error = it.message) },
            )
        }
    }

    fun delete(id: Long) {
        val date = _state.value.entries.firstOrNull { it.id == id }?.let {
            runCatching { Instant.parse(it.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        }
        viewModelScope.launch {
            repository.delete(id).fold(
                onSuccess = { if (date == null) refresh() else loadDate(date) },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    companion object {
        fun factory(repository: TrackerRepository): ViewModelProvider.Factory = factoryOf {
            TrackerViewModel(repository)
        }
    }
}
