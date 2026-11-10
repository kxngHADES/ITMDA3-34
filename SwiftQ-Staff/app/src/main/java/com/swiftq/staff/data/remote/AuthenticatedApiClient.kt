package com.swiftq.staff.data.remote

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object AuthenticatedApiClient {

    fun create(context: Context): SwiftQApiService {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context))
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json; charset=UTF-8".toMediaType()
                )
            )
            .build()
            .create(SwiftQApiService::class.java)
    }
}