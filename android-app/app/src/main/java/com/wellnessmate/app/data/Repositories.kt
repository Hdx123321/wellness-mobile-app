package com.wellnessmate.app.data

import retrofit2.HttpException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    suspend fun createAnalyzedPhoto(
        request: AnalyzedFoodEntryRequest,
        thumbnail: ByteArray,
    ): Result<FoodEntryResponse>
    suspend fun analyze(image: ByteArray): Result<FoodAnalysisResponse>
    suspend fun thumbnail(entryId: Long): Result<ByteArray>
    suspend fun delete(id: Long): Result<Unit>
}

class NetworkFoodRepository(private val api: WellnessApi) : FoodRepository {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val analyzedFoodAdapter = moshi.adapter(AnalyzedFoodEntryRequest::class.java)

    override suspend fun catalog(query: String, categoryId: Long?) =
        apiResult { api.foodCatalog(query.trim(), categoryId) }

    override suspend fun categories() = apiResult { api.foodCategories() }

    override suspend fun foodDetail(id: Long) = apiResult { api.foodDetail(id) }
    override suspend fun entries(from: String, to: String) = apiResult { api.foodEntries(from, to) }
    override suspend fun create(request: FoodEntryRequest) = apiResult { api.createFoodEntry(request) }
    override suspend fun createAnalyzed(request: AnalyzedFoodEntryRequest) = apiResult {
        api.createAnalyzedFoodEntry(request)
    }
    override suspend fun createAnalyzedPhoto(
        request: AnalyzedFoodEntryRequest,
        thumbnail: ByteArray,
    ) = apiResult {
        val entry = analyzedFoodAdapter.toJson(request).toRequestBody("application/json".toMediaType())
        val body = thumbnail.toRequestBody("image/jpeg".toMediaType())
        api.createAnalyzedFoodPhotoEntry(
            entry,
            MultipartBody.Part.createFormData("thumbnail", "meal-thumbnail.jpg", body),
        )
    }
    override suspend fun analyze(image: ByteArray) = apiResult {
        val body = image.toRequestBody("image/jpeg".toMediaType())
        api.analyzeFoodPhoto(MultipartBody.Part.createFormData("image", "meal.jpg", body))
    }
    override suspend fun thumbnail(entryId: Long) = apiResult {
        api.foodEntryThumbnail(entryId).bytes()
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
    suspend fun sendStream(content: String, onToken: (String) -> Unit): Result<AiAdvisorMessageResponse>
}

class NetworkAiAdvisorRepository(
    private val api: WellnessApi,
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String,
    private val tokenStore: TokenStore,
) : AiAdvisorRepository {
    override suspend fun messages() = apiResult { api.aiAdvisorMessages() }
    override suspend fun send(content: String) = apiResult {
        api.sendAiAdvisorMessage(AiAdvisorMessageRequest(content.trim()))
    }

    override suspend fun sendStream(content: String, onToken: (String) -> Unit): Result<AiAdvisorMessageResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val jsonBody = moshi.adapter(AiAdvisorMessageRequest::class.java)
                    .toJson(AiAdvisorMessageRequest(content.trim()))
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val requestBuilder = okhttp3.Request.Builder()
                    .url("${baseUrl}api/ai-advisor/messages/stream")
                    .post(requestBody)
                tokenStore.token()?.let { requestBuilder.header("Authorization", "Bearer $it") }
                val response = okHttpClient.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    val message = when (code) {
                        503 -> "AI service is not configured on the server."
                        502 -> "AI advisor is temporarily unavailable."
                        else -> "Server request failed ($code)."
                    }
                    return@withContext Result.failure(ApiFailure(message))
                }

                val source = response.body?.source() ?: run {
                    response.close()
                    return@withContext Result.failure(ApiFailure("Empty response"))
                }

                var messageId = -1L
                var createdAt = ""
                val fullText = StringBuilder()

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    // SSE format: "data:value" or "data: value" — strip prefix
                    val data = when {
                        line.startsWith("data:") -> line.substring(5).trimStart()
                        else -> continue
                    }
                    if (data.isEmpty()) continue
                    // Try JSON parse — done event has {"type":"done",...}
                    val done = try {
                        moshi.adapter(Map::class.java).fromJson(data) as? Map<*, *>
                    } catch (_: Exception) { null }
                    if (done != null && done["type"] == "done") {
                        messageId = (done["messageId"] as? Number)?.toLong() ?: -1L
                        createdAt = done["createdAt"]?.toString() ?: ""
                        break
                    }
                    // Plain token
                    fullText.append(data)
                    onToken(data)
                }
                response.close()

                if (messageId == -1L) {
                    // Stream completed but no done event — create a synthetic response
                    messageId = -System.currentTimeMillis()
                    createdAt = ""
                }
                Result.success(AiAdvisorMessageResponse(messageId, "ASSISTANT", fullText.toString(), createdAt))
            } catch (e: Exception) {
                Result.failure(ApiFailure("Stream failed: ${e.message}"))
            }
        }
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
