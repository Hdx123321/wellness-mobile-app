package com.alpinefitness.app.data

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
    suspend fun markRead(conversationId: Long): Result<Unit>
    suspend fun createConversation(clientId: Long, subject: String?): Result<CoachConversationResponse>
    suspend fun clients(): Result<List<SubscriberResponse>>
}

class NetworkCoachChatRepository(private val api: WellnessApi) : CoachChatRepository {
    override suspend fun conversations() = apiResult { api.coachConversations() }
    override suspend fun messages(conversationId: Long, afterId: Long) = apiResult {
        api.coachMessages(conversationId, afterId)
    }
    override suspend fun send(conversationId: Long, content: String) = apiResult {
        api.sendCoachMessage(conversationId, CoachMessageRequest(content.trim()))
    }
    override suspend fun markRead(conversationId: Long) = apiResult {
        api.markCoachConversationRead(conversationId)
        Unit
    }
    override suspend fun createConversation(clientId: Long, subject: String?) = apiResult {
        api.createCoachConversation(CreateConversationRequest(clientId, subject))
    }
    override suspend fun clients() = apiResult { api.coachClients() }
}

interface TrainingPlanRepository {
    suspend fun plans(): Result<List<TrainingPlanResponse>>
    suspend fun create(request: TrainingPlanRequest): Result<TrainingPlanResponse>
    suspend fun update(id: Long, request: TrainingPlanRequest): Result<TrainingPlanResponse>
    suspend fun delete(id: Long): Result<Unit>
    suspend fun checkIn(id: Long): Result<TrainingPlanResponse>
    suspend fun subscribe(id: Long): Result<TrainingPlanResponse>
    suspend fun unsubscribe(id: Long): Result<Unit>
    suspend fun subscribers(planId: Long): Result<List<SubscriberResponse>>
    suspend fun uploadFile(bytes: ByteArray, contentType: String, filename: String): Result<FileUploadResponse>
}

class NetworkTrainingPlanRepository(private val api: WellnessApi) : TrainingPlanRepository {
    override suspend fun plans() = apiResult { api.trainingPlans() }
    override suspend fun create(request: TrainingPlanRequest) = apiResult { api.createTrainingPlan(request) }
    override suspend fun update(id: Long, request: TrainingPlanRequest) = apiResult { api.updateTrainingPlan(id, request) }
    override suspend fun delete(id: Long) = apiResult { api.deleteTrainingPlan(id) }
    override suspend fun checkIn(id: Long) = apiResult { api.checkInTrainingPlan(id) }
    override suspend fun subscribe(id: Long) = apiResult { api.subscribeTrainingPlan(id) }
    override suspend fun unsubscribe(id: Long) = apiResult { api.unsubscribeTrainingPlan(id) }
    override suspend fun subscribers(planId: Long) = apiResult { api.trainingPlanSubscribers(planId) }
    override suspend fun uploadFile(bytes: ByteArray, contentType: String, filename: String) = apiResult {
        val mediaType = try { contentType.toMediaType() } catch (_: Exception) { "application/octet-stream".toMediaType() }
        val body = bytes.toRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("file", filename, body)
        api.uploadFile(part)
    }
}

interface AiAdvisorRepository {
    suspend fun sessions(): Result<List<AiAdvisorSessionResponse>>
    suspend fun createSession(): Result<AiAdvisorSessionResponse>
    suspend fun deleteSession(id: Long): Result<Unit>
    suspend fun renameSession(id: Long, title: String): Result<AiAdvisorSessionResponse>
    suspend fun messagesForSession(sessionId: Long): Result<List<AiAdvisorMessageResponse>>
    suspend fun sendStreamToSession(sessionId: Long, content: String, onThinkingToken: (String) -> Unit, onToken: (String) -> Unit): Result<AiAdvisorMessageResponse>
}

class NetworkAiAdvisorRepository(
    private val api: WellnessApi,
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String,
    private val tokenStore: TokenStore,
) : AiAdvisorRepository {
    override suspend fun sessions() = apiResult { api.aiAdvisorSessions() }
    override suspend fun createSession() = apiResult { api.createAiAdvisorSession() }
    override suspend fun deleteSession(id: Long) = apiResult { api.deleteAiAdvisorSession(id); Unit }
    override suspend fun renameSession(id: Long, title: String) = apiResult {
        api.renameAiAdvisorSession(id, RenameSessionRequest(title.trim()))
    }
    override suspend fun messagesForSession(sessionId: Long) = apiResult {
        api.aiAdvisorSessionMessages(sessionId)
    }

    override suspend fun sendStreamToSession(sessionId: Long, content: String, onThinkingToken: (String) -> Unit, onToken: (String) -> Unit): Result<AiAdvisorMessageResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val jsonBody = moshi.adapter(AiAdvisorMessageRequest::class.java)
                    .toJson(AiAdvisorMessageRequest(content.trim()))
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val requestBuilder = okhttp3.Request.Builder()
                    .url("${baseUrl}api/ai-advisor/sessions/$sessionId/messages/stream")
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
                var isThinking = false

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val data = line.removePrefix("data:")
                    if (data.isEmpty()) continue

                    // Try structured event first (JSON with "type" field)
                    val event = try {
                        moshi.adapter(Map::class.java).fromJson(data) as? Map<*, *>
                    } catch (_: Exception) { null }

                    if (event != null) {
                        when (event["type"]) {
                            "done" -> {
                                messageId = (event["messageId"] as? Number)?.toLong() ?: -1L
                                createdAt = event["createdAt"]?.toString() ?: ""
                                break
                            }
                            "error" -> {
                                val msg = event["message"]?.toString() ?: "Something went wrong."
                                return@withContext Result.failure(ApiFailure(msg))
                            }
                            "thinking_start" -> {
                                isThinking = true
                                continue
                            }
                            "thinking_end" -> {
                                isThinking = false
                                continue
                            }
                            "tool_call", "tool_result" -> {
                                // Transient status events — skip
                                continue
                            }
                            else -> {
                                // Unknown structured event — treat as text
                                if (isThinking) onThinkingToken(data)
                                else {
                                    fullText.append(data)
                                    onToken(data)
                                }
                            }
                        }
                    } else {
                        // Plain text token — route based on thinking state
                        if (isThinking) onThinkingToken(data)
                        else {
                            fullText.append(data)
                            onToken(data)
                        }
                    }
                }
                response.close()

                if (messageId == -1L) {
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
