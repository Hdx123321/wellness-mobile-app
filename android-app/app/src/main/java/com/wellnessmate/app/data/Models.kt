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

data class ProfileResponse(
    val userId: Long,
    val dateOfBirth: String,
    val heightCm: Double,
    val currentWeightKg: Double,
    val sex: String,
    val ethnicity: String?,
    val targetWeightKg: Double?,
    val goalStartedAt: String?,
    val goalDurationWeeks: Int?,
    val dailyRoutine: String,
    val activityLevel: String,
    val exercisePreferences: Set<String>,
    val coreNeeds: Set<String>,
) {
    fun toUpdate(heightCm: Double = this.heightCm) = OnboardingRequest(
        dateOfBirth = dateOfBirth,
        heightCm = heightCm,
        currentWeightKg = currentWeightKg,
        sex = sex,
        ethnicity = ethnicity,
        targetWeightKg = targetWeightKg,
        goalDurationWeeks = goalDurationWeeks,
        dailyRoutine = dailyRoutine,
        activityLevel = activityLevel,
        exercisePreferences = exercisePreferences,
        coreNeeds = coreNeeds,
    )
}

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

data class FoodCatalogItemResponse(
    val id: Long,
    val name: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbohydratePer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double,
    val categoryId: Long? = null,
    val imageUrl: String? = null,
)

data class FoodCategoryResponse(
    val id: Long,
    val name: String,
    val nameCn: String,
)

data class ServingSizeResponse(
    val id: Long,
    val label: String,
    val labelCn: String,
    val grams: Double,
    val isDefault: Boolean,
)

data class FoodDetailResponse(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbohydratePer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double,
    val categoryId: Long?,
    val servingSizes: List<ServingSizeResponse>,
)

data class FoodNutrients(
    val calories: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double,
)

data class CatalogFoodItemRequest(val foodId: Long, val grams: Double)

data class FoodEntryRequest(
    val recordedAt: String,
    val mealType: String,
    val items: List<CatalogFoodItemRequest>,
    val notes: String?,
)

data class AnalyzedFoodItemRequest(
    val name: String,
    val grams: Double,
    val calories: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double,
)

data class AnalyzedFoodEntryRequest(
    val recordedAt: String,
    val mealType: String,
    val items: List<AnalyzedFoodItemRequest>,
    val notes: String?,
)

data class FoodEntryItemResponse(
    val id: Long,
    val catalogItemId: Long?,
    val name: String,
    val grams: Double,
    val nutrients: FoodNutrients,
)

data class FoodEntryResponse(
    val id: Long,
    val trackerEntryId: Long,
    val recordedAt: String,
    val mealType: String,
    val source: String,
    val notes: String?,
    val items: List<FoodEntryItemResponse>,
    val totals: FoodNutrients,
)

data class FoodAnalysisItemResponse(
    val name: String,
    val estimatedGrams: Double,
    val calories: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double,
    val confidence: Double,
)

data class FoodAnalysisResponse(
    val summary: String,
    val items: List<FoodAnalysisItemResponse>,
    val disclaimer: String,
)

data class CoachConversationResponse(
    val id: Long,
    val clientName: String,
    val coachName: String,
    val lastMessage: String?,
    val updatedAt: String,
)

data class CoachMessageRequest(val content: String)

data class CoachMessageResponse(
    val id: Long,
    val senderId: Long,
    val senderName: String,
    val senderRole: String,
    val content: String,
    val createdAt: String,
)

data class AiAdvisorMessageRequest(val content: String)

data class AiAdvisorMessageResponse(
    val id: Long,
    val role: String,
    val content: String,
    val createdAt: String,
)
