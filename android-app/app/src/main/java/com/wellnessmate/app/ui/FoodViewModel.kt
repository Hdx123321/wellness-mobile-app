package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wellnessmate.app.data.AnalyzedFoodEntryRequest
import com.wellnessmate.app.data.AnalyzedFoodItemRequest
import com.wellnessmate.app.data.CatalogFoodItemRequest
import com.wellnessmate.app.data.FoodAnalysisResponse
import com.wellnessmate.app.data.FoodCatalogItemResponse
import com.wellnessmate.app.data.FoodCategoryResponse
import com.wellnessmate.app.data.FoodDetailResponse
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
    val categories: List<FoodCategoryResponse> = emptyList(),
    val selectedCategoryId: Long? = null,
    val foodDetail: FoodDetailResponse? = null,
    val detailLoading: Boolean = false,
    val entries: List<FoodEntryResponse> = emptyList(),
    val analysis: FoodAnalysisResponse? = null,
    val analysisDate: LocalDate? = null,
    val analysisMealType: String? = null,
    val analysisThumbnail: ByteArray? = null,
    val thumbnails: Map<Long, ByteArray> = emptyMap(),
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
            val categories = repository.categories()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val from = today.atStartOfDay(zone).toInstant().toString()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant().toString()
            val entries = repository.entries(from, to)
            _state.value = _state.value.copy(
                loading = false,
                catalog = catalog.getOrNull() ?: _state.value.catalog,
                categories = categories.getOrNull() ?: _state.value.categories,
                entries = entries.getOrNull() ?: _state.value.entries,
                error = catalog.exceptionOrNull()?.message
                    ?: categories.exceptionOrNull()?.message
                    ?: entries.exceptionOrNull()?.message,
            )
        }
    }

    fun selectCategory(categoryId: Long?) {
        _state.value = _state.value.copy(selectedCategoryId = categoryId, loading = true, error = null)
        viewModelScope.launch {
            repository.catalog("", categoryId).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, catalog = it) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            val catId = _state.value.selectedCategoryId
            repository.catalog(query, catId).fold(
                onSuccess = { _state.value = _state.value.copy(catalog = it, error = null) },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
        }
    }

    fun loadFoodDetail(id: Long) {
        _state.value = _state.value.copy(detailLoading = true, error = null)
        viewModelScope.launch {
            repository.foodDetail(id).fold(
                onSuccess = { _state.value = _state.value.copy(detailLoading = false, foodDetail = it) },
                onFailure = { _state.value = _state.value.copy(detailLoading = false, error = it.message) },
            )
        }
    }

    fun clearDetail() {
        _state.value = _state.value.copy(foodDetail = null)
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

    fun saveCatalog(
        date: LocalDate,
        mealType: String,
        items: List<CatalogFoodItemRequest>,
        notes: String?,
        onSaved: () -> Unit,
    ) {
        if (_state.value.saving || items.isEmpty()) return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            repository.create(FoodEntryRequest(recordedAt(date), mealType, items, notes)).fold(
                onSuccess = {
                    _state.value = _state.value.copy(saving = false)
                    loadDate(date)
                    onSaved()
                },
                onFailure = { _state.value = _state.value.copy(saving = false, error = it.message) },
            )
        }
    }

    fun analyze(image: ByteArray, date: LocalDate, mealType: String, onComplete: () -> Unit) {
        if (_state.value.analyzing) return
        _state.value = _state.value.copy(
            analyzing = true,
            analysis = null,
            analysisDate = date,
            analysisMealType = mealType,
            analysisThumbnail = image,
            error = null,
        )
        viewModelScope.launch {
            repository.analyze(image).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        analyzing = false,
                        analysis = it,
                        analysisDate = date,
                        analysisMealType = mealType,
                        analysisThumbnail = image,
                    )
                },
                onFailure = { _state.value = _state.value.copy(analyzing = false, error = it.message) },
            )
            onComplete()
        }
    }

    fun confirmAnalysis(onSaved: () -> Unit) {
        val analysis = _state.value.analysis ?: return
        val date = _state.value.analysisDate ?: LocalDate.now()
        val mealType = _state.value.analysisMealType ?: "SNACK"
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
            val request = AnalyzedFoodEntryRequest(
                recordedAt(date), mealType, items, "Confirmed photo estimate",
            )
            val thumbnail = _state.value.analysisThumbnail
            val result = if (thumbnail == null) repository.createAnalyzed(request)
            else repository.createAnalyzedPhoto(request, thumbnail)
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        saving = false,
                        analysis = null,
                        analysisDate = null,
                        analysisMealType = null,
                        analysisThumbnail = null,
                    )
                    loadDate(date)
                    onSaved()
                },
                onFailure = { _state.value = _state.value.copy(saving = false, error = it.message) },
            )
        }
    }

    fun discardAnalysis() {
        _state.value = _state.value.copy(
            analysis = null,
            analysisDate = null,
            analysisMealType = null,
            analysisThumbnail = null,
        )
    }

    fun updateAnalysisItem(
        index: Int,
        name: String,
        grams: String,
        calories: String,
        proteinGrams: String,
        carbohydrateGrams: String,
        fatGrams: String,
        fiberGrams: String,
    ) {
        val current = _state.value.analysis ?: return
        val item = current.items.getOrNull(index) ?: return
        val updated = item.copy(
            name = name.trim(),
            estimatedGrams = grams.toDoubleOrNull() ?: item.estimatedGrams,
            calories = calories.toDoubleOrNull() ?: item.calories,
            proteinGrams = proteinGrams.toDoubleOrNull() ?: item.proteinGrams,
            carbohydrateGrams = carbohydrateGrams.toDoubleOrNull() ?: item.carbohydrateGrams,
            fatGrams = fatGrams.toDoubleOrNull() ?: item.fatGrams,
            fiberGrams = fiberGrams.toDoubleOrNull() ?: item.fiberGrams,
        )
        _state.value = _state.value.copy(
            analysis = current.copy(items = current.items.toMutableList().also { it[index] = updated }),
        )
    }

    fun removeAnalysisItem(index: Int) {
        val current = _state.value.analysis ?: return
        if (index !in current.items.indices) return
        _state.value = _state.value.copy(
            analysis = current.copy(items = current.items.filterIndexed { itemIndex, _ -> itemIndex != index }),
        )
    }

    fun delete(id: Long, onDeleted: () -> Unit) {
        val date = _state.value.entries.firstOrNull { it.id == id }?.let {
            runCatching { Instant.parse(it.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        }
        viewModelScope.launch {
            repository.delete(id).fold(
                onSuccess = {
                    if (date == null) refresh() else loadDate(date)
                    onDeleted()
                },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
        }
    }

    fun loadThumbnail(entryId: Long) {
        if (_state.value.thumbnails.containsKey(entryId)) return
        viewModelScope.launch {
            repository.thumbnail(entryId).fold(
                onSuccess = { bytes ->
                    _state.value = _state.value.copy(
                        thumbnails = _state.value.thumbnails + (entryId to bytes),
                    )
                },
                onFailure = { /* Thumbnail is optional; keep the meal card usable. */ },
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

    private fun recordedAt(date: LocalDate): String {
        val zone = ZoneId.systemDefault()
        return if (date == LocalDate.now(zone)) Instant.now().toString()
        else date.atTime(12, 0).atZone(zone).toInstant().toString()
    }

    companion object {
        fun factory(repository: FoodRepository): ViewModelProvider.Factory = factoryOf {
            FoodViewModel(repository)
        }
    }
}
