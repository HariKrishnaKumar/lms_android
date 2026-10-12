package com.exam.lms.data.token

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshTokenHandlerProvider: Provider<RefreshTokenHandler>
) : Interceptor {

    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accessToken = tokenStore.getAccessToken()

        val request = if (accessToken != null && !originalRequest.headers.names().contains("Authorization")) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        if (response.code == 401 && accessToken != null && !request.url.encodedPath.contains("/auth/refresh")) {
            response.close()

            val newAccessToken = runBlocking {
                refreshMutex.withLock {
                    val currentToken = tokenStore.getAccessToken()
                    if (currentToken != accessToken && currentToken != null) {
                        // Already refreshed by another concurrent request
                        currentToken
                    } else {
                        val refreshToken = tokenStore.getRefreshToken()
                        if (refreshToken != null) {
                            val handler = refreshTokenHandlerProvider.get()
                            val refreshed = handler.refreshTokens(refreshToken)
                            if (refreshed != null) {
                                tokenStore.saveTokens(
                                    accessToken = refreshed.accessToken,
                                    refreshToken = refreshed.refreshToken,
                                    role = refreshed.role,
                                    subRole = refreshed.subRole
                                )
                                refreshed.accessToken
                            } else {
                                tokenStore.clear()
                                null
                            }
                        } else {
                            tokenStore.clear()
                            null
                        }
                    }
                }
            }

            if (newAccessToken != null) {
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
                return chain.proceed(newRequest)
            }
        }

        return response
    }
}

interface RefreshTokenHandler {
    suspend fun refreshTokens(refreshToken: String): RefreshResult?
}

data class RefreshResult(
    val accessToken: String,
    val refreshToken: String,
    val role: String,
    val subRole: String? = null
)
