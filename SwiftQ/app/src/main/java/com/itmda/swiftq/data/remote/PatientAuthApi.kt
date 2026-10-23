package com.itmda.swiftq.data.remote

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Register, login and refresh. Built on a client WITHOUT [AuthInterceptor]: refresh runs while
 * [PatientSessionAuth] holds its lock, and going through the interceptor would ask for a token again and deadlock.
 */
interface PatientAuthApi {
    @POST("v1/auth/patients/register")
    suspend fun register(@Body body: RequestBody): Response<ResponseBody>

    @POST("v1/auth/patients/login")
    suspend fun login(@Body body: RequestBody): Response<ResponseBody>

    @POST("v1/auth/patients/refresh")
    suspend fun refresh(@Header("Authorization") bearer: String): Response<ResponseBody>

    companion object {
        fun create(baseUrl: String, bareClient: OkHttpClient): PatientAuthApi = Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(bareClient)
            .build()
            .create(PatientAuthApi::class.java)
    }
}
