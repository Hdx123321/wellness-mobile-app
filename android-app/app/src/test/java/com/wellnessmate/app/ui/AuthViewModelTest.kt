package com.wellnessmate.app.ui

import com.wellnessmate.app.data.AuthRepository
import com.wellnessmate.app.data.SessionUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loginMovesToSignedInSession() = runTest {
        val user = SessionUser(1, "alice", "Alice", "CLIENT", true)
        val viewModel = AuthViewModel(FakeAuthRepository(loginResult = Result.success(user)))

        viewModel.login("alice", "StrongPass123")
        advanceUntilIdle()

        assertEquals(user, (viewModel.session.value as SessionState.SignedIn).user)
        assertEquals(false, viewModel.uiState.value.submitting)
    }

    @Test
    fun invalidRegistrationStaysSignedOut() = runTest {
        val viewModel = AuthViewModel(FakeAuthRepository())

        viewModel.register("x", "bad", "short", "")

        assertTrue(viewModel.session.value is SessionState.SignedOut)
        assertTrue(viewModel.uiState.value.error != null)
    }
}

private class FakeAuthRepository(
    private val loginResult: Result<SessionUser> = Result.failure(IllegalStateException()),
) : AuthRepository {
    override fun restoredSession(): SessionUser? = null
    override suspend fun login(identifier: String, password: String) = loginResult
    override suspend fun register(username: String, email: String, password: String, displayName: String?) = loginResult
    override suspend fun logout() = Unit
    override fun markOnboardingComplete(): SessionUser? = null
}
