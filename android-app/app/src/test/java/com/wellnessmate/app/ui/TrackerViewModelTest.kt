package com.wellnessmate.app.ui

import com.wellnessmate.app.data.TrackerEntryRequest
import com.wellnessmate.app.data.TrackerEntryResponse
import com.wellnessmate.app.data.TrackerRepository
import com.wellnessmate.app.data.TrackerTypeResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refreshAndCreateUpdateState() = runTest {
        val repository = FakeTrackerRepository()
        val viewModel = TrackerViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.types.size)
        var saved = false
        viewModel.save(null, TrackerEntryRequest("WATER", "2026-06-28T00:00:00Z", 500.0, null, null)) {
            saved = true
        }
        advanceUntilIdle()

        assertTrue(saved)
        assertEquals(1, viewModel.state.value.entries.size)
    }

    @Test
    fun saveAllCreatesEveryWorkoutDraft() = runTest {
        val repository = FakeTrackerRepository()
        val viewModel = TrackerViewModel(repository)
        advanceUntilIdle()
        var saved = false

        viewModel.saveAll(
            listOf(
                TrackerEntryRequest("WORKOUT", "2026-07-01T00:00:00Z", 30.0, "RUNNING", null),
                TrackerEntryRequest("WORKOUT", "2026-07-01T00:00:00Z", 20.0, "YOGA", null),
            ),
        ) { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        assertEquals(2, viewModel.state.value.entries.size)
    }
}

private class FakeTrackerRepository : TrackerRepository {
    private val stored = mutableListOf<TrackerEntryResponse>()
    override suspend fun types() = Result.success(
        listOf(TrackerTypeResponse("WATER", "ml", "Volume", null, false, 0.0, 20000.0, false)),
    )
    override suspend fun entries(type: String?, from: String?, to: String?) = Result.success(stored.toList())
    override suspend fun create(request: TrackerEntryRequest): Result<TrackerEntryResponse> {
        val entry = TrackerEntryResponse(1, request.type, request.recordedAt, request.amount, "ml", null, null, "MANUAL", 0)
        stored += entry
        return Result.success(entry)
    }
    override suspend fun update(id: Long, request: TrackerEntryRequest) = create(request)
    override suspend fun delete(id: Long): Result<Unit> {
        stored.removeAll { it.id == id }
        return Result.success(Unit)
    }
}
