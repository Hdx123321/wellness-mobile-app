package com.wellnessmate.app.ui

import com.wellnessmate.app.data.OnboardingQuestion
import com.wellnessmate.app.data.OnboardingRepository
import com.wellnessmate.app.data.OnboardingRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsQuestionsAndCompletesProfile() = runTest {
        val repository = FakeOnboardingRepository()
        val viewModel = OnboardingViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.questions.size)
        var completed = false
        viewModel.save(sampleRequest()) { completed = true }
        advanceUntilIdle()

        assertTrue(repository.saved)
        assertTrue(completed)
    }

    private fun sampleRequest() = OnboardingRequest(
        "1995-05-12", 175.0, 75.0, "PREFER_NOT_TO_SAY", null,
        null, null, "MOSTLY_SITTING", "MODERATE", setOf("WALKING"), setOf("TRACK_EXERCISE"),
    )
}

private class FakeOnboardingRepository : OnboardingRepository {
    var saved = false
    override suspend fun questions() = Result.success(
        listOf(OnboardingQuestion("dateOfBirth", "DATE", true, "Date", emptyList())),
    )
    override suspend fun save(request: OnboardingRequest): Result<Unit> {
        saved = true
        return Result.success(Unit)
    }
}
