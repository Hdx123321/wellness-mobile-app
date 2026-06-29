package com.wellnessmate.app.ui

import com.wellnessmate.app.data.AiAdvisorMessageResponse
import com.wellnessmate.app.data.AiAdvisorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiAdvisorViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun loadsAndAppendsUserAndAdvisorMessages() = runTest {
        val viewModel = AiAdvisorViewModel(FakeAiAdvisorRepository())
        advanceUntilIdle()

        viewModel.send("How can I move more?") {}
        advanceUntilIdle()

        assertEquals(listOf("USER", "ASSISTANT"), viewModel.state.value.messages.map { it.role })
    }
}

private class FakeAiAdvisorRepository : AiAdvisorRepository {
    override suspend fun messages() = Result.success(emptyList<AiAdvisorMessageResponse>())
    override suspend fun send(content: String) = Result.success(
        AiAdvisorMessageResponse(1, "ASSISTANT", "Take a short walk.", "2026-06-29T00:00:00Z"),
    )
}
