package com.wellnessmate.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("api/trackers/types")
    suspend fun trackerTypes(): List<TrackerTypeResponse>

    @GET("api/tracker-entries")
    suspend fun trackerEntries(
        @Query("type") type: String? = null,
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
}
