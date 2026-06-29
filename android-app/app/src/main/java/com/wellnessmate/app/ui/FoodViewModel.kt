package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wellnessmate.app.data.AnalyzedFoodEntryRequest
import com.wellnessmate.app.data.AnalyzedFoodItemRequest
import com.wellnessmate.app.data.CatalogFoodItemRequest
import com.wellnessmate.app.data.FoodAnalysisResponse
import com.wellnessmate.app.data.FoodCatalogItemResponse
import com.wellnessmate.app.data.FoodEntryRequest
import com.wellnessmate.app.data.FoodEntryResponse
import com.wellnessmate.app.data.FoodRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FoodUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val analyzing: Boolean = false,
    val catalog: List<FoodCatalogItemResponse> = emptyList(),
    val entries: List<FoodEntryResponse> = emptyList(),
    val analysis: FoodAnalysisResponse? = null,
    val error: String? = null,
)

/** Owns the food calendar, confirmed meals, and photo analysis state. */
class FoodViewModel(private val repository: FoodRepository) : ViewModel() {
    private val _state = MutableStateFlow(FoodUiState())
    val state: StateFlow<FoodUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val catalog = repository.catalog("")
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val from = today.atStartOfDay(zone).toInstant().toString()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant().toString()
            val entries = repository.entries(from, to)
            if (catalog.isSuccess && entries.isSuccess) {
                _state.value = _state.value.copy(
                    loading = false,
                    catalog = catalog.getOrThrow(),
                    entries = entries.getOrThrow(),
                )
            } else {
                _state.value = _state.value.copy(
                    loading = false,
                    error = catalog.exceptionOrNull()?.message ?: entries.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun loadMonth(month: YearMonth) {
        val (from, to) = monthRange(month)
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repository.entries(from, to).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, entries = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun loadDate(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toString()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toString()
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repository.entries(from, to).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, entries = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            repository.catalog(query).fold(
                onSuccess = { _state.value = _state.value.copy(catalog = it, error = null) },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
        }
    }

    fun saveCatalog(
        items: List<CatalogFoodItemRequest>,
        notes: String?,
        onSaved: () -> Unit,
    ) {
        if (_state.value.saving || items.isEmpty()) return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            repository.create(FoodEntryRequest(Instant.now().toString(), items, notes)).fold(
                onSuccess = {
                    _state.value = _state.value.copy(saving = false)
                    refresh()
                    onSaved()
                },
                onFailure = { _state.value = _state.value.copy(saving = false, error = it.message) },
            )
        }
    }

    fun analyze(image: ByteArray, onComplete: () -> Unit) {
        if (_state.value.analyzing) return
        _state.value = _state.value.copy(analyzing = true, analysis = null, error = null)
        viewModelScope.launch {
            repository.analyze(image).fold(
                onSuccess = { _state.value = _state.value.copy(analyzing = false, analysis = it) },
                onFailure = { _state.value = _state.value.copy(analyzing = false, error = it.message) },
            )
            onComplete()
        }
    }

    fun confirmAnalysis(onSaved: () -> Unit) {
        val analysis = _state.value.analysis ?: return
        if (_state.value.saving) return
        val items = analysis.items.map {
            AnalyzedFoodItemRequest(
                name = it.name,
                grams = it.estimatedGrams,
                calories = it.calories,
                proteinGrams = it.proteinGrams,
                carbohydrateGrams = it.carbohydrateGrams,
                fatGrams = it.fatGrams,
                fiberGrams = it.fiberGrams,
            )
        }
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            repository.createAnalyzed(
                AnalyzedFoodEntryRequest(Instant.now().toString(), items, "Confirmed photo estimate"),
            ).fold(
                onSuccess = {
                    _state.value = _state.value.copy(saving = false, analysis = null)
                    refresh()
                    onSaved()
                },
                onFailure = { _state.value = _state.value.copy(saving = false, error = it.message) },
            )
        }
    }

    fun discardAnalysis() {
        _state.value = _state.value.copy(analysis = null)
    }

    fun delete(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(id).fold(
                onSuccess = { refresh(); onDeleted() },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun monthRange(month: YearMonth): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val start = month.atDay(1).minusDays(6).atStartOfDay(zone).toInstant()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        return start.toString() to end.toString()
    }

    companion object {
        fun factory(repository: FoodRepository): ViewModelProvider.Factory = factoryOf {
            FoodViewModel(repository)
        }
    }
}
