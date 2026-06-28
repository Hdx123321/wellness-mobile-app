package com.wellnessmate.app.data

/** API request and response models for the Android core flow. @author TODO(team member) */
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String?,
)

data class LoginRequest(val identifier: String, val password: String)

data class AuthResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val userId: Long,
    val username: String,
    val displayName: String?,
    val role: String,
    val onboardingRequired: Boolean,
)

data class SessionUser(
    val userId: Long,
    val username: String,
    val displayName: String?,
    val role: String,
    val onboardingRequired: Boolean,
)

data class OnboardingQuestion(
    val id: String,
    val type: String,
    val required: Boolean,
    val prompt: String,
    val options: List<String>,
)

data class OnboardingRequest(
    val dateOfBirth: String,
    val heightCm: Double,
    val currentWeightKg: Double,
    val sex: String,
    val ethnicity: String?,
    val targetWeightKg: Double?,
    val goalDurationWeeks: Int?,
    val dailyRoutine: String,
    val activityLevel: String,
    val exercisePreferences: Set<String>,
    val coreNeeds: Set<String>,
)

data class ProfileResponse(val userId: Long)

data class TrackerTypeResponse(
    val type: String,
    val unit: String,
    val amountLabel: String,
    val detailLabel: String?,
    val detailRequired: Boolean,
    val minimum: Double,
    val maximum: Double,
    val integerOnly: Boolean,
)

data class TrackerEntryRequest(
    val type: String,
    val recordedAt: String,
    val amount: Double,
    val detail: String?,
    val notes: String?,
)

data class TrackerEntryResponse(
    val id: Long,
    val type: String,
    val recordedAt: String,
    val amount: Double,
    val unit: String,
    val detail: String?,
    val notes: String?,
    val source: String,
    val version: Long,
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
