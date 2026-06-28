package com.wellnessmate.app.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wellnessmate.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** Application-scoped network and repository dependencies. @author TODO(team member) */
class AppContainer(context: Context) {
    private val tokenStore = TokenStore(context.applicationContext)
    private val api: WellnessApi

    init {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    tokenStore.token()?.let { header("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(request)
            }
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WellnessApi::class.java)
    }

    val authRepository: AuthRepository = NetworkAuthRepository(api, tokenStore)
    val onboardingRepository: OnboardingRepository = NetworkOnboardingRepository(api)
    val trackerRepository: TrackerRepository = NetworkTrackerRepository(api)
}
