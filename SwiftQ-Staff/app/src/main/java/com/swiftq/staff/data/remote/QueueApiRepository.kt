package com.swiftq.staff.data.remote

import com.swiftq.staff.data.remote.model.ConfirmPriorityRequest
import com.swiftq.staff.data.remote.model.QueueEntryResponse
import com.swiftq.staff.data.remote.model.QueueResponse
import com.swiftq.staff.data.remote.model.ServicePoint
import com.swiftq.staff.data.remote.model.TicketResponse
import com.swiftq.staff.data.remote.model.WalkInRequest
import com.swiftq.staff.model.QueuePriority
import com.swiftq.staff.model.QueueStatus
import com.swiftq.staff.model.QueueTicket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class QueueApiRepository(
    private val api: SwiftQApiService,
    private val servicePointsRepository: ServicePointsRepository
) {

    // Result of a conditional queue request.
    sealed class QueueFetch {
        data class Updated(
            val queue: QueueResponse,
            val etag: String?
        ) : QueueFetch()

        object NotModified : QueueFetch()
    }

    // ---------------------------------------------------------
    // SERVICE POINTS
    // ---------------------------------------------------------

    suspend fun loadServicePoints(
        clinicId: String
    ): Result<List<ServicePoint>> {
        return servicePointsRepository.getActiveServicePoints(clinicId)
    }

    // ---------------------------------------------------------
    // LOAD QUEUE
    // ---------------------------------------------------------

    suspend fun loadQueue(
        queueId: String,
        etag: String? = null
    ): Result<QueueFetch> = withContext(Dispatchers.IO) {

        if (queueId.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    "This service point does not currently have a queue."
                )
            )
        }

        try {
            val response = api.getQueue(queueId, etag)

            if (response.code() == 304) {
                return@withContext Result.success(QueueFetch.NotModified)
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    ApiErrors.from(response, "Unable to load the queue.")
                )
            }

            val queue = response.body()
                ?: return@withContext Result.failure(
                    Exception("The API returned an empty queue response.")
                )

            Result.success(
                QueueFetch.Updated(
                    queue = queue,
                    etag = response.headers()["ETag"]
                )
            )
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
                Exception(
                    "Unable to load queue information from SwiftQ.",
                    exception
                )
            )
        }
    }

    // ---------------------------------------------------------
    // MAP API TICKETS TO DASHBOARD TICKETS
    // ---------------------------------------------------------

    fun mapTickets(queue: QueueResponse): List<QueueTicket> {
        return queue.entries.mapNotNull(::mapEntry)
    }

    private fun mapEntry(entry: QueueEntryResponse): QueueTicket? {
        val status = statusOf(entry.status) ?: return null

        return QueueTicket(
            id = entry.ticketId,
            ticketNumber = entry.displayNumber,
            status = status,
            priority = priorityOf(
                category = entry.priorityCategory,
                staffConfirmed = entry.staffConfirmed == true,
                isPriority = entry.isPriority
            ),
            position = entry.position,
            displayName = entry.displayName,
            priorityCategory = entry.priorityCategory
                ?.lowercase()
                ?.takeIf { it != "none" },
            lateFlag = entry.lateFlag,
            joinedAt = entry.joinedAt
        )
    }

    // ---------------------------------------------------------
    // CALL NEXT PATIENT
    // ---------------------------------------------------------

    suspend fun callNextPatient(
        queueId: String,
        counterLabel: String? = null
    ): Result<TicketResponse> {

        if (queueId.isBlank()) {
            return Result.failure(
                IllegalArgumentException("A queue ID is required.")
            )
        }

        return execute("Unable to call the next patient.") {
            api.callNext(
                queueId = queueId,
                request = CallNextRequest(
                    counter_label = counterLabel
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                )
            )
        }
    }

    // ---------------------------------------------------------
    // REGISTER WALK-IN PATIENT
    // ---------------------------------------------------------

    suspend fun registerWalkIn(
        queueId: String,
        idempotencyKey: String,
        displayName: String?,
        phone: String?,
        email: String?,
        language: String,
        notificationChannel: String,
        priorityCategory: String
    ): Result<TicketResponse> {

        if (queueId.isBlank()) {
            return Result.failure(
                IllegalArgumentException("A queue ID is required.")
            )
        }

        val cleanName = displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (cleanName != null && cleanName.length > 40) {
            return Result.failure(
                IllegalArgumentException(
                    "The patient's name cannot exceed 40 characters."
                )
            )
        }

        val cleanPhone = phone
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (
            cleanPhone != null &&
            !cleanPhone.matches(Regex("^\\+27[0-9]{9}$"))
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Enter a South African phone number in the format +27XXXXXXXXX."
                )
            )
        }

        val cleanEmail = email
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val cleanLanguage = language.trim().lowercase()

        if (!cleanLanguage.matches(Regex("^[a-z]{2,3}$"))) {
            return Result.failure(
                IllegalArgumentException(
                    "Enter a valid two- or three-letter language code."
                )
            )
        }

        val cleanChannel = notificationChannel.trim().lowercase()
        val allowedChannels = setOf(
            "push",
            "sms",
            "email",
            "none"
        )

        if (cleanChannel !in allowedChannels) {
            return Result.failure(
                IllegalArgumentException(
                    "Select a supported notification method."
                )
            )
        }

        if (cleanChannel == "sms" && cleanPhone == null) {
            return Result.failure(
                IllegalArgumentException(
                    "A phone number is required for SMS alerts."
                )
            )
        }

        if (cleanChannel == "email" && cleanEmail == null) {
            return Result.failure(
                IllegalArgumentException(
                    "An email address is required for email alerts."
                )
            )
        }

        val cleanPriority = priorityCategory.trim().lowercase()

        if (cleanPriority !in PRIORITY_CATEGORIES) {
            return Result.failure(
                IllegalArgumentException(
                    "Select a supported priority category."
                )
            )
        }

        // The caller keeps one idempotency key per walk-in form, so a
        // retry after a timeout cannot register the patient twice.
        return execute("Walk-in registration failed.") {
            api.registerWalkIn(
                queueId = queueId,
                idempotencyKey = idempotencyKey,
                request = WalkInRequest(
                    displayName = cleanName,
                    phone = cleanPhone,
                    email = cleanEmail,
                    language = cleanLanguage,
                    notificationChannel = cleanChannel,
                    priorityCategory = cleanPriority
                )
            )
        }
    }

    // ---------------------------------------------------------
    // TICKET DETAILS AND ACTIONS
    // ---------------------------------------------------------

    suspend fun getTicket(ticketId: String): Result<TicketResponse> {
        return requireTicketId(ticketId)
            ?: execute("Unable to load the ticket.") {
                api.getTicket(ticketId)
            }
    }

    suspend fun markTicketNoShow(ticketId: String): Result<TicketResponse> {
        return requireTicketId(ticketId)
            ?: execute("Unable to mark the ticket as a no-show.") {
                api.markNoShow(ticketId)
            }
    }

    suspend fun completeTicket(ticketId: String): Result<TicketResponse> {
        return requireTicketId(ticketId)
            ?: execute("Unable to mark the ticket as served.") {
                api.completeTicket(ticketId)
            }
    }

    suspend fun confirmPriority(
        ticketId: String,
        confirmed: Boolean,
        category: String? = null
    ): Result<TicketResponse> {
        return requireTicketId(ticketId)
            ?: execute("Unable to update the priority request.") {
                api.confirmPriority(
                    ticketId = ticketId,
                    request = ConfirmPriorityRequest(
                        confirmed = confirmed,
                        category = category?.takeIf { confirmed }
                    )
                )
            }
    }

    private fun requireTicketId(ticketId: String): Result<TicketResponse>? {
        return if (ticketId.isBlank()) {
            Result.failure(IllegalArgumentException("A ticket ID is required."))
        } else {
            null
        }
    }

    private suspend fun <T> execute(
        failureMessage: String,
        call: suspend () -> Response<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val response = call()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    ApiErrors.from(response, failureMessage)
                )
            }

            val body = response.body()
                ?: return@withContext Result.failure(
                    Exception("The API returned an empty response.")
                )

            Result.success(body)
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
            Result.failure(Exception(failureMessage, exception))
        }
    }

    companion object {
        val PRIORITY_CATEGORIES = listOf(
            "none",
            "elderly",
            "pregnant",
            "disability",
            "urgent"
        )

        fun statusOf(status: String?): QueueStatus? {
            return when (status?.lowercase()) {
                "waiting" -> QueueStatus.WAITING
                "called" -> QueueStatus.CALLED
                "served" -> QueueStatus.SERVED
                "no_show" -> QueueStatus.NO_SHOW
                else -> null
            }
        }

        // The API only treats a category as priority once staff confirm it.
        // Until then it is a request that staff need to review.
        fun priorityOf(
            category: String?,
            staffConfirmed: Boolean,
            isPriority: Boolean = false
        ): QueuePriority {
            val hasCategory = !category.isNullOrBlank() &&
                    !category.equals("none", ignoreCase = true)

            return when {
                isPriority -> QueuePriority.PRIORITY
                hasCategory && staffConfirmed -> QueuePriority.PRIORITY
                hasCategory -> QueuePriority.REQUESTED
                else -> QueuePriority.REGULAR
            }
        }
    }
}
