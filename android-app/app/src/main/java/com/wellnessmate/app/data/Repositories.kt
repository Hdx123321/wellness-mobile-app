package com.wellnessmate.app.data

import retrofit2.HttpException

/** User-facing API failure with a stable fallback message. @author TODO(team member) */
class ApiFailure(message: String) : RuntimeException(message)

interface AuthRepository {
    fun restoredSession(): SessionUser?
    suspend fun login(identifier: String, password: String): Result<SessionUser>
    suspend fun register(username: String, email: String, password: String, displayName: String?): Result<SessionUser>
    suspend fun logout()
    fun markOnboardingComplete(): SessionUser?
}

class NetworkAuthRepository(
    private val api: WellnessApi,
    private val tokenStore: TokenStore,
) : AuthRepository {
    override fun restoredSession(): SessionUser? = tokenStore.session()

    override suspend fun login(identifier: String, password: String): Result<SessionUser> = apiResult {
        api.login(LoginRequest(identifier.trim(), password)).also(tokenStore::save).toSession()
    }

    override suspend fun register(
        username: String,
        email: String,
        password: String,
        displayName: String?,
    ): Result<SessionUser> = apiResult {
        api.register(RegisterRequest(username.trim(), email.trim(), password, displayName?.trim()))
            .also(tokenStore::save)
            .toSession()
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        tokenStore.clear()
    }

    override fun markOnboardingComplete(): SessionUser? {
        tokenStore.markOnboardingComplete()
        return tokenStore.session()
    }

    private fun AuthResponse.toSession() = SessionUser(
        userId, username, displayName, role, onboardingRequired,
    )
}

interface OnboardingRepository {
    suspend fun questions(): Result<List<OnboardingQuestion>>
    suspend fun save(request: OnboardingRequest): Result<Unit>
}

class NetworkOnboardingRepository(private val api: WellnessApi) : OnboardingRepository {
    override suspend fun questions() = apiResult { api.onboardingQuestions() }
    override suspend fun save(request: OnboardingRequest) = apiResult { api.saveProfile(request); Unit }
}

interface TrackerRepository {
    suspend fun types(): Result<List<TrackerTypeResponse>>
    suspend fun entries(type: String? = null): Result<List<TrackerEntryResponse>>
    suspend fun create(request: TrackerEntryRequest): Result<TrackerEntryResponse>
    suspend fun update(id: Long, request: TrackerEntryRequest): Result<TrackerEntryResponse>
    suspend fun delete(id: Long): Result<Unit>
}

class NetworkTrackerRepository(private val api: WellnessApi) : TrackerRepository {
    override suspend fun types() = apiResult { api.trackerTypes() }
    override suspend fun entries(type: String?) = apiResult { api.trackerEntries(type).content }
    override suspend fun create(request: TrackerEntryRequest) = apiResult { api.createTrackerEntry(request) }
    override suspend fun update(id: Long, request: TrackerEntryRequest) = apiResult {
        api.updateTrackerEntry(id, request)
    }
    override suspend fun delete(id: Long) = apiResult { api.deleteTrackerEntry(id); Unit }
}

private suspend fun <T> apiResult(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: Throwable) {
        val message = when (error) {
            is HttpException -> when (error.code()) {
                400 -> "Please check the entered values."
                401 -> "Your session has expired. Please sign in again."
                409 -> "This account information is already in use."
                else -> "Server request failed (${error.code()})."
            }
            else -> "Cannot reach the WellnessMate service."
        }
        Result.failure(ApiFailure(message))
    }
}
