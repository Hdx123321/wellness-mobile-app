package com.wellnessmate.app.ui

import com.wellnessmate.app.data.AiAdvisorMessageResponse
import com.wellnessmate.app.data.AiAdvisorRepository
import com.wellnessmate.app.data.AiAdvisorSessionResponse
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
    override suspend fun sessions() = Result.success(listOf(AiAdvisorSessionResponse(1, "Test", "", "")))
    override suspend fun createSession() = Result.success(AiAdvisorSessionResponse(2, "New", "", ""))
    override suspend fun deleteSession(id: Long) = Result.success(Unit)
    override suspend fun renameSession(id: Long, title: String) = Result.success(AiAdvisorSessionResponse(1, title, "", ""))
    override suspend fun messagesForSession(sessionId: Long) = Result.success(emptyList())
    override suspend fun sendStreamToSession(sessionId: Long, content: String, onThinkingToken: (String) -> Unit, onToken: (String) -> Unit): Result<AiAdvisorMessageResponse> {
        onToken("Take a short walk.")
        return Result.success(
            AiAdvisorMessageResponse(1, "ASSISTANT", "Take a short walk.", "2026-06-29T00:00:00Z"),
        )
    }
}
