package com.wellnessmate.app.ui

import com.wellnessmate.app.data.AnalyzedFoodEntryRequest
import com.wellnessmate.app.data.FoodAnalysisItemResponse
import com.wellnessmate.app.data.FoodAnalysisResponse
import com.wellnessmate.app.data.FoodCatalogItemResponse
import com.wellnessmate.app.data.FoodEntryRequest
import com.wellnessmate.app.data.FoodEntryResponse
import com.wellnessmate.app.data.FoodNutrients
import com.wellnessmate.app.data.FoodRepository
import com.wellnessmate.app.data.CatalogFoodItemRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun loadsCatalogAndSavesSelectedFoods() = runTest {
        val repository = FakeFoodRepository()
        val viewModel = FoodViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertEquals("Chicken breast", viewModel.state.value.catalog.single().name)

        viewModel.saveCatalog(
            java.time.LocalDate.of(2026, 6, 28),
            "LUNCH",
            listOf(CatalogFoodItemRequest(1, 200.0)),
            "Lunch",
        ) {}
        advanceUntilIdle()

        assertEquals(200.0, repository.catalogRequest?.items?.single()?.grams ?: 0.0, 0.0)
        assertEquals("LUNCH", repository.catalogRequest?.mealType)
    }

    @Test
    fun analyzesThenConfirmsPhotoEstimate() = runTest {
        val repository = FakeFoodRepository()
        val viewModel = FoodViewModel(repository)
        advanceUntilIdle()

        viewModel.analyze(byteArrayOf(1, 2, 3), java.time.LocalDate.of(2026, 6, 28), "DINNER") {}
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.analysis)

        viewModel.confirmAnalysis {}
        advanceUntilIdle()
        assertEquals(2, repository.photoAnalysisRequest?.items?.size)
        assertEquals("Rice bowl", repository.photoAnalysisRequest?.items?.first()?.name)
        assertEquals("DINNER", repository.photoAnalysisRequest?.mealType)
        assertEquals(null, viewModel.state.value.analysis)
    }

    @Test
    fun editsAndDeletesPhotoEstimateBeforeConfirmingWithThumbnail() = runTest {
        val repository = FakeFoodRepository()
        val viewModel = FoodViewModel(repository)
        advanceUntilIdle()

        viewModel.analyze(byteArrayOf(1, 2, 3), java.time.LocalDate.of(2026, 6, 28), "DINNER") {}
        advanceUntilIdle()

        viewModel.updateAnalysisItem(
            index = 0,
            name = "Edited rice bowl",
            grams = "325",
            calories = "520",
            proteinGrams = "24",
            carbohydrateGrams = "70",
            fatGrams = "14",
            fiberGrams = "6",
        )
        viewModel.removeAnalysisItem(1)
        viewModel.confirmAnalysis {}
        advanceUntilIdle()

        assertEquals("Edited rice bowl", repository.photoAnalysisRequest?.items?.single()?.name)
        assertEquals(325.0, repository.photoAnalysisRequest?.items?.single()?.grams ?: 0.0, 0.0)
        assertTrue(repository.photoThumbnail?.contentEquals(byteArrayOf(1, 2, 3)) == true)
        assertEquals(null, viewModel.state.value.analysis)
    }
}

private class FakeFoodRepository : FoodRepository {
    var catalogRequest: FoodEntryRequest? = null
    var analysisRequest: AnalyzedFoodEntryRequest? = null
    var photoAnalysisRequest: AnalyzedFoodEntryRequest? = null
    var photoThumbnail: ByteArray? = null

    override suspend fun catalog(query: String) = Result.success(listOf(
        FoodCatalogItemResponse(1, "Chicken breast", 165.0, 31.0, 0.0, 3.6, 0.0),
    ))

    override suspend fun entries(from: String, to: String) = Result.success(emptyList<FoodEntryResponse>())

    override suspend fun create(request: FoodEntryRequest): Result<FoodEntryResponse> {
        catalogRequest = request
        return Result.success(entry())
    }

    override suspend fun createAnalyzed(request: AnalyzedFoodEntryRequest): Result<FoodEntryResponse> {
        analysisRequest = request
        return Result.success(entry())
    }

    override suspend fun analyze(image: ByteArray) = Result.success(
        FoodAnalysisResponse(
            summary = "Rice bowl",
            items = listOf(
                FoodAnalysisItemResponse(
                    "Rice bowl", 300.0, 450.0, 20.0, 65.0, 12.0, 5.0, 0.8,
                ),
                FoodAnalysisItemResponse(
                    "Sauce", 30.0, 80.0, 1.0, 8.0, 5.0, 0.0, 0.35,
                ),
            ),
            disclaimer = "Review before saving",
        ),
    )

    override suspend fun createAnalyzedPhoto(
        request: AnalyzedFoodEntryRequest,
        thumbnail: ByteArray,
    ): Result<FoodEntryResponse> {
        photoAnalysisRequest = request
        photoThumbnail = thumbnail
        return Result.success(entry())
    }

    override suspend fun thumbnail(entryId: Long) = Result.success(byteArrayOf(9, 8, 7))

    override suspend fun delete(id: Long) = Result.success(Unit)

    private fun entry() = FoodEntryResponse(
        id = 1,
        trackerEntryId = 1,
        recordedAt = "2026-06-28T00:00:00Z",
        mealType = "LUNCH",
        source = "MANUAL",
        notes = null,
        items = emptyList(),
        totals = FoodNutrients(0.0, 0.0, 0.0, 0.0, 0.0),
    )
}
