package com.swiftq.staff.data.remote

import android.content.Context
import com.swiftq.staff.data.remote.model.LoginResponse

class StaffLoginHandler(context: Context) {

    private val repository = AuthRepository()
    private val tokenManager = TokenManager(context.applicationContext)

    suspend fun login(
        email: String,
        password: String
    ): Result<LoginResponse> {

        val result = repository.login(email, password)

        return result.fold(
            onSuccess = { response ->
                if (StaffRoleValidator.isAllowed(response.user.role)) {
                    tokenManager.clearSession()
                    tokenManager.saveToken(response.accessToken)
                    tokenManager.saveStaffProfile(
                        fullName = response.user.fullName,
                        clinicId = response.user.clinicId
                    )
                    Result.success(response)
                } else {
                    tokenManager.clearToken()
                    Result.failure(
                        Exception("This account does not have staff access.")
                    )
                }
            },
            onFailure = { error ->
                tokenManager.clearToken()
                Result.failure(error)
            }
        )
    }

    fun logout() {
        tokenManager.clearSession()
    }
}
