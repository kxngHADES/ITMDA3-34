package com.example.swiftq.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiFactory {
    /** Connect 10 s, read 15 s. A timeout is a retryable failure, never a crash. */
    fun httpClient(tokens: AuthTokenProvider?): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply {
            if (tokens != null) {
                addInterceptor(AuthInterceptor(tokens))
                authenticator(TokenRefreshAuthenticator(tokens))
            }
        }
        .build()

    fun create(baseUrl: String, client: OkHttpClient): SwiftQApi = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SwiftQApi::class.java)
}
