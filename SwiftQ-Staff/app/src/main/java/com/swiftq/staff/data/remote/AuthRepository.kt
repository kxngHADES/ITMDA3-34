package com.swiftq.staff.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val apiService: SwiftQApiService = RetrofitClient.api
) {
    suspend fun login(
        email: String,
        password: String
    ): Result<com.swiftq.staff.data.remote.model.LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(
                    LoginRequest(
                        email = email.trim(),
                        password = password
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()

                    if (body != null) {
                        Result.success(body)
                    } else {
                        Result.failure(
                            Exception("The server returned an empty response.")
                        )
                    }
                } else {
                    // Surface the API's own message, e.g.
                    // "Incorrect email or password."
                    Result.failure(
                        Exception(
                            ApiErrors.from(response, "Login failed.").message
                        )
                    )
                }
            } catch (exception: Exception) {
                Result.failure(
                    Exception("Unable to connect to the SwiftQ API.")
                )
            }
        }
    }
}