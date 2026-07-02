package com.wellnessmate.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Query
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody

/** Retrofit contract for the implemented backend core flow. @author TODO(team member) */
interface WellnessApi {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout()

    @GET("api/onboarding/questions")
    suspend fun onboardingQuestions(): List<OnboardingQuestion>

    @PUT("api/onboarding/profile")
    suspend fun saveProfile(@Body request: OnboardingRequest): ProfileResponse

    @GET("api/onboarding/profile")
    suspend fun profile(): ProfileResponse

    @GET("api/trackers/types")
    suspend fun trackerTypes(): List<TrackerTypeResponse>

    @GET("api/tracker-entries")
    suspend fun trackerEntries(
        @Query("type") type: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
    ): PageResponse<TrackerEntryResponse>

    @GET("api/tracker-entries/{id}")
    suspend fun trackerEntry(@Path("id") id: Long): TrackerEntryResponse

    @POST("api/tracker-entries")
    suspend fun createTrackerEntry(@Body request: TrackerEntryRequest): TrackerEntryResponse

    @PUT("api/tracker-entries/{id}")
    suspend fun updateTrackerEntry(
        @Path("id") id: Long,
        @Body request: TrackerEntryRequest,
    ): TrackerEntryResponse

    @DELETE("api/tracker-entries/{id}")
    suspend fun deleteTrackerEntry(@Path("id") id: Long)

    @GET("api/food/catalog")
    suspend fun foodCatalog(
        @Query("query") query: String,
        @Query("categoryId") categoryId: Long? = null,
        @Query("limit") limit: Int = 50,
    ): List<FoodCatalogItemResponse>

    @GET("api/food/categories")
    suspend fun foodCategories(): List<FoodCategoryResponse>

    @GET("api/food/catalog/{id}")
    suspend fun foodDetail(@Path("id") id: Long): FoodDetailResponse

    @GET("api/food/entries")
    suspend fun foodEntries(
        @Query("from") from: String,
        @Query("to") to: String,
    ): List<FoodEntryResponse>

    @POST("api/food/entries")
    suspend fun createFoodEntry(@Body request: FoodEntryRequest): FoodEntryResponse

    @POST("api/food/entries/analyzed")
    suspend fun createAnalyzedFoodEntry(@Body request: AnalyzedFoodEntryRequest): FoodEntryResponse

    @Multipart
    @POST("api/food/entries/analyzed/photo")
    suspend fun createAnalyzedFoodPhotoEntry(
        @Part("entry") entry: RequestBody,
        @Part thumbnail: MultipartBody.Part,
    ): FoodEntryResponse

    @DELETE("api/food/entries/{id}")
    suspend fun deleteFoodEntry(@Path("id") id: Long)

    @GET("api/food/entries/{id}/thumbnail")
    suspend fun foodEntryThumbnail(@Path("id") id: Long): ResponseBody

    @Multipart
    @POST("api/food/analyze")
    suspend fun analyzeFoodPhoto(@Part image: MultipartBody.Part): FoodAnalysisResponse

    @GET("api/coach-chat/conversations")
    suspend fun coachConversations(): List<CoachConversationResponse>

    @GET("api/coach-chat/conversations/{id}/messages")
    suspend fun coachMessages(
        @Path("id") conversationId: Long,
        @Query("afterId") afterId: Long = 0,
    ): List<CoachMessageResponse>

    @POST("api/coach-chat/conversations/{id}/messages")
    suspend fun sendCoachMessage(
        @Path("id") conversationId: Long,
        @Body request: CoachMessageRequest,
    ): CoachMessageResponse

    @GET("api/training-plans")
    suspend fun trainingPlans(): List<TrainingPlanResponse>

    @POST("api/training-plans")
    suspend fun createTrainingPlan(@Body request: TrainingPlanRequest): TrainingPlanResponse

    @POST("api/training-plans/{id}/check-ins")
    suspend fun checkInTrainingPlan(@Path("id") id: Long): TrainingPlanResponse

    @GET("api/ai-advisor/messages")
    suspend fun aiAdvisorMessages(): List<AiAdvisorMessageResponse>

    @POST("api/ai-advisor/messages")
    suspend fun sendAiAdvisorMessage(@Body request: AiAdvisorMessageRequest): AiAdvisorMessageResponse
}
