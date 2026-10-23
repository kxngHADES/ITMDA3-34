package com.swiftq.staff.data.remote

import com.swiftq.staff.data.remote.model.ClinicsResponse
import com.swiftq.staff.data.remote.model.ConfirmPriorityRequest
import com.swiftq.staff.data.remote.model.LoginResponse
import com.swiftq.staff.data.remote.model.MeResponse
import com.swiftq.staff.data.remote.model.PushTokenRequest
import com.swiftq.staff.data.remote.model.QueueResponse
import com.swiftq.staff.data.remote.model.ServicePointsResponse
import com.swiftq.staff.data.remote.model.TicketResponse
import com.swiftq.staff.data.remote.model.WalkInRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class CallNextRequest(
    val counter_label: String? = null
)

interface SwiftQApiService {

    @POST("v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @GET("v1/me")
    suspend fun getMe(): Response<MeResponse>

    @PUT("v1/me/push-token")
    suspend fun registerPushToken(
        @Body request: PushTokenRequest
    ): Response<Unit>

    @DELETE("v1/me/push-token")
    suspend fun removePushToken(
        @Query("device_id") deviceId: String
    ): Response<Unit>

    @GET("v1/clinics")
    suspend fun getClinics(
        @Query("active") active: Boolean = true
    ): Response<ClinicsResponse>

    @GET("v1/clinics/{clinic_id}/service-points")
    suspend fun getServicePoints(
        @Path("clinic_id") clinicId: String,
        @Query("active") active: Boolean = true
    ): Response<ServicePointsResponse>

    // Sends the last ETag so an unchanged queue returns 304 with no body.
    @GET("v1/queues/{queue_id}")
    suspend fun getQueue(
        @Path("queue_id") queueId: String,
        @Header("If-None-Match") etag: String? = null
    ): Response<QueueResponse>

    @POST("v1/queues/{queue_id}/call-next")
    suspend fun callNext(
        @Path("queue_id") queueId: String,
        @Body request: CallNextRequest = CallNextRequest()
    ): Response<TicketResponse>

    @POST("v1/queues/{queue_id}/walk-ins")
    suspend fun registerWalkIn(
        @Path("queue_id") queueId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: WalkInRequest
    ): Response<TicketResponse>

    @GET("v1/tickets/{ticket_id}")
    suspend fun getTicket(
        @Path("ticket_id") ticketId: String
    ): Response<TicketResponse>

    @POST("v1/tickets/{ticket_id}/no-show")
    suspend fun markNoShow(
        @Path("ticket_id") ticketId: String
    ): Response<TicketResponse>

    @POST("v1/tickets/{ticket_id}/complete")
    suspend fun completeTicket(
        @Path("ticket_id") ticketId: String
    ): Response<TicketResponse>

    @POST("v1/tickets/{ticket_id}/confirm-priority")
    suspend fun confirmPriority(
        @Path("ticket_id") ticketId: String,
        @Body request: ConfirmPriorityRequest
    ): Response<TicketResponse>
}
