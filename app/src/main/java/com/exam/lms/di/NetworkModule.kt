package com.exam.lms.di

import com.exam.lms.BuildConfig
import com.exam.lms.data.remote.ApiService
import com.exam.lms.data.remote.MockApiInterceptor
import com.exam.lms.data.remote.dto.RefreshTokenRequest
import com.exam.lms.data.token.AuthInterceptor
import com.exam.lms.data.token.RefreshResult
import com.exam.lms.data.token.RefreshTokenHandler
import com.exam.lms.data.token.TokenStore
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        mockApiInterceptor: MockApiInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)

        if (BuildConfig.USE_MOCK_DATA) {
            builder.addInterceptor(mockApiInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRefreshTokenHandler(apiService: javax.inject.Provider<ApiService>): RefreshTokenHandler {
        return object : RefreshTokenHandler {
            override suspend fun refreshTokens(refreshToken: String): RefreshResult? {
                return try {
                    val response = apiService.get().refreshToken(RefreshTokenRequest(refreshToken))
                    val data = response.data
                    if (data != null) {
                        RefreshResult(
                            accessToken = data.accessToken,
                            refreshToken = data.refreshToken,
                            role = data.role,
                            subRole = data.subRole
                        )
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
