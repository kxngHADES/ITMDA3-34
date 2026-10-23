package com.swiftq.staff.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

// Works out which clinic the signed-in staff member works at.
class ClinicResolver(
    private val api: SwiftQApiService,
    private val tokenManager: TokenManager
) {

    suspend fun resolve(): Result<String> = withContext(Dispatchers.IO) {
        // 1. A hard-coded clinic overrides everything (useful for demos).
        ClinicConfig.CLINIC_ID.takeIf { it.isNotBlank() }?.let {
            return@withContext Result.success(it)
        }

        // 2. The clinic returned with the login response.
        tokenManager.getClinicId()?.takeIf { it.isNotBlank() }?.let {
            return@withContext Result.success(it)
        }

        try {
            // 3. The clinic attached to the staff profile.
            val me = api.getMe()

            if (!me.isSuccessful) {
                return@withContext Result.failure(
                    ApiErrors.from(me, "Unable to load your staff profile.")
                )
            }

            me.body()?.clinicId?.takeIf { it.isNotBlank() }?.let {
                tokenManager.saveClinicId(it)
                return@withContext Result.success(it)
            }

            // 4. Fall back to the first active clinic the account can see.
            val clinics = api.getClinics(active = true)

            if (!clinics.isSuccessful) {
                return@withContext Result.failure(
                    ApiErrors.from(clinics, "Unable to load clinics.")
                )
            }

            val clinic = clinics.body()?.items?.firstOrNull()
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        "Your account is not linked to a clinic yet."
                    )
                )

            tokenManager.saveClinicId(clinic.id)
            Result.success(clinic.id)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            Result.failure(
                NetworkUnavailableException(
                    "Unable to reach SwiftQ. Check your connection.",
                    exception
                )
            )
        } catch (exception: Exception) {
            Result.failure(
                Exception("Unable to work out your clinic.", exception)
            )
        }
    }
}
