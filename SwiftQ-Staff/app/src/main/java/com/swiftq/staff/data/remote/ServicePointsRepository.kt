package com.swiftq.staff.data.remote

import com.swiftq.staff.data.remote.model.ServicePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ServicePointsRepository(
    private val api: SwiftQApiService
) {
    suspend fun getActiveServicePoints(
        clinicId: String
    ): Result<List<ServicePoint>> = withContext(Dispatchers.IO) {
        try {
            if (clinicId.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "A clinic ID is required to load service points."
                    )
                )
            }

            val response = api.getServicePoints(
                clinicId = clinicId,
                active = true
            )

            if (response.isSuccessful) {
                val servicePoints = response.body()?.items
                    ?: return@withContext Result.failure(
                        Exception("The API returned an empty response.")
                    )

                Result.success(
                    servicePoints.filter { it.isActive }
                )
            } else {
                Result.failure(
                    ApiErrors.from(response, "Unable to load service points.")
                )
            }
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
                Exception("Unable to connect to the SwiftQ API.")
            )
        }
    }
}
