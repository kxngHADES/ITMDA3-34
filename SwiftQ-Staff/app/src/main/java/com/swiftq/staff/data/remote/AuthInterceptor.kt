package com.swiftq.staff.data.remote

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(context: Context) : Interceptor {

    private val tokenManager = TokenManager(
        context.applicationContext
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // The login endpoint does not require an access token.
        if (originalRequest.url.encodedPath.endsWith("/v1/auth/login")) {
            return chain.proceed(originalRequest)
        }

        val token = tokenManager.getToken()

        val authenticatedRequest = originalRequest.newBuilder()
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        return chain.proceed(authenticatedRequest)
    }
}