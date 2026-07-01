package com.wellnessmate.app.data

import retrofit2.HttpException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

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

    override suspend fun login(identifier: String, password: String): Result<SessionUser> = apiResult(
        expireSessionOnUnauthorized = false,
    ) {
        api.login(LoginRequest(identifier.trim(), password)).also(tokenStore::save).toSession()
    }

    override suspend fun register(
        username: String,
        email: String,
        password: String,
        displayName: String?,
    ): Result<SessionUser> = apiResult(expireSessionOnUnauthorized = false) {
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

interface HealthProfileRepository {
    suspend fun profile(): Result<ProfileResponse>
    suspend fun updateHeight(profile: ProfileResponse, heightCm: Double): Result<ProfileResponse>
    suspend fun updateGoal(
        profile: ProfileResponse,
        targetWeightKg: Double?,
        goalDurationWeeks: Int?,
    ): Result<ProfileResponse>
}

class NetworkHealthProfileRepository(private val api: WellnessApi) : HealthProfileRepository {
    override suspend fun profile() = apiResult { api.profile() }
    override suspend fun updateHeight(profile: ProfileResponse, heightCm: Double) = apiResult {
        api.saveProfile(profile.toUpdate(heightCm))
    }
    override suspend fun updateGoal(
        profile: ProfileResponse,
        targetWeightKg: Double?,
        goalDurationWeeks: Int?,
    ) = apiResult {
        api.saveProfile(
            profile.toUpdate().copy(
                targetWeightKg = targetWeightKg,
                goalDurationWeeks = goalDurationWeeks,
            )
        )
    }
}

interface TrackerRepository {
    suspend fun types(): Result<List<TrackerTypeResponse>>
    suspend fun entries(
        type: String? = null,
        from: String? = null,
        to: String? = null,
    ): Result<List<TrackerEntryResponse>>
    suspend fun create(request: TrackerEntryRequest): Result<TrackerEntryResponse>
    suspend fun update(id: Long, request: TrackerEntryRequest): Result<TrackerEntryResponse>
    suspend fun delete(id: Long): Result<Unit>
}

class NetworkTrackerRepository(private val api: WellnessApi) : TrackerRepository {
    override suspend fun types() = apiResult { api.trackerTypes() }
    override suspend fun entries(type: String?, from: String?, to: String?) = apiResult {
        api.trackerEntries(type, from, to).content
    }
    override suspend fun create(request: TrackerEntryRequest) = apiResult { api.createTrackerEntry(request) }
    override suspend fun update(id: Long, request: TrackerEntryRequest) = apiResult {
        api.updateTrackerEntry(id, request)
    }
    override suspend fun delete(id: Long) = apiResult { api.deleteTrackerEntry(id); Unit }
}

interface FoodRepository {
    suspend fun catalog(query: String, categoryId: Long? = null): Result<List<FoodCatalogItemResponse>>
    suspend fun categories(): Result<List<FoodCategoryResponse>>
    suspend fun foodDetail(id: Long): Result<FoodDetailResponse>
    suspend fun entries(from: String, to: String): Result<List<FoodEntryResponse>>
    suspend fun create(request: FoodEntryRequest): Result<FoodEntryResponse>
    suspend fun createAnalyzed(request: AnalyzedFoodEntryRequest): Result<FoodEntryResponse>
    suspend fun analyze(image: ByteArray): Result<FoodAnalysisResponse>
    suspend fun delete(id: Long): Result<Unit>
}

class NetworkFoodRepository(private val api: WellnessApi) : FoodRepository {
    override suspend fun catalog(query: String, categoryId: Long?) =
        apiResult { api.foodCatalog(query.trim(), categoryId) }

    override suspend fun categories() = apiResult { api.foodCategories() }

    override suspend fun foodDetail(id: Long) = apiResult { api.foodDetail(id) }

    override suspend fun entries(from: String, to: String) = apiResult { api.foodEntries(from, to) }
    override suspend fun create(request: FoodEntryRequest) = apiResult { api.createFoodEntry(request) }
    override suspend fun createAnalyzed(request: AnalyzedFoodEntryRequest) = apiResult {
        api.createAnalyzedFoodEntry(request)
    }
    override suspend fun analyze(image: ByteArray) = apiResult {
        val body = image.toRequestBody("image/jpeg".toMediaType())
        api.analyzeFoodPhoto(MultipartBody.Part.createFormData("image", "meal.jpg", body))
    }
    override suspend fun delete(id: Long) = apiResult { api.deleteFoodEntry(id); Unit }
}

interface CoachChatRepository {
    suspend fun conversations(): Result<List<CoachConversationResponse>>
    suspend fun messages(conversationId: Long, afterId: Long): Result<List<CoachMessageResponse>>
    suspend fun send(conversationId: Long, content: String): Result<CoachMessageResponse>
}

class NetworkCoachChatRepository(private val api: WellnessApi) : CoachChatRepository {
    override suspend fun conversations() = apiResult { api.coachConversations() }
    override suspend fun messages(conversationId: Long, afterId: Long) = apiResult {
        api.coachMessages(conversationId, afterId)
    }
    override suspend fun send(conversationId: Long, content: String) = apiResult {
        api.sendCoachMessage(conversationId, CoachMessageRequest(content.trim()))
    }
}

interface AiAdvisorRepository {
    suspend fun messages(): Result<List<AiAdvisorMessageResponse>>
    suspend fun send(content: String): Result<AiAdvisorMessageResponse>
}

class NetworkAiAdvisorRepository(private val api: WellnessApi) : AiAdvisorRepository {
    override suspend fun messages() = apiResult { api.aiAdvisorMessages() }
    override suspend fun send(content: String) = apiResult {
        api.sendAiAdvisorMessage(AiAdvisorMessageRequest(content.trim()))
    }
}

private suspend fun <T> apiResult(
    expireSessionOnUnauthorized: Boolean = true,
    block: suspend () -> T,
): Result<T> {
    return try {
        Result.success(block())
    } catch (error: Throwable) {
        val message = when (error) {
            is HttpException -> when (error.code()) {
                400 -> "Please check the entered values."
                401 -> if (expireSessionOnUnauthorized) {
                    SessionManager.expireSession()  // ← 触发全局登出，跳转登录页
                    "Your session has expired. Please sign in again."
                } else {
                    "Incorrect username/email or password."
                }
                409 -> "This account information is already in use."
                422 -> "No food could be recognized. Try a clearer photo."
                502 -> "Food photo analysis is temporarily unavailable."
                503 -> "AI service is not configured on the server."
                else -> "Server request failed (${error.code()})."
            }
            else -> "Cannot reach the WellnessMate service."
        }
        Result.failure(ApiFailure(message))
    }
}
