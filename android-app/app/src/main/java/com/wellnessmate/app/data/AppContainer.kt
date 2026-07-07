package com.alpinefitness.app.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.alpinefitness.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/** Application-scoped network and repository dependencies. @author TODO(team member) */
class AppContainer(context: Context) {
    private val tokenStore = TokenStore(context.applicationContext)
    val okHttpClient: OkHttpClient
    val baseUrl: String = BuildConfig.API_BASE_URL
    private val api: WellnessApi

    init {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    tokenStore.token()?.let { header("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(request)
            }
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WellnessApi::class.java)
    }

    val authRepository: AuthRepository = NetworkAuthRepository(api, tokenStore)
    val onboardingRepository: OnboardingRepository = NetworkOnboardingRepository(api)
    val healthProfileRepository: HealthProfileRepository = NetworkHealthProfileRepository(api)
    val trackerRepository: TrackerRepository = NetworkTrackerRepository(api)
    val foodRepository: FoodRepository = NetworkFoodRepository(api)
    val coachChatRepository: CoachChatRepository = NetworkCoachChatRepository(api)
    val trainingPlanRepository: TrainingPlanRepository = NetworkTrainingPlanRepository(api)
    val aiAdvisorRepository: AiAdvisorRepository =
        NetworkAiAdvisorRepository(api, okHttpClient, baseUrl, tokenStore)
}
