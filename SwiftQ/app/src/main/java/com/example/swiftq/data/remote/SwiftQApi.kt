package com.example.swiftq.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The endpoints the patient app uses. Bodies come back as raw [ResponseBody] so [safeApiCall] can read the
 * error envelope on non-2xx too. The join body is a pre-built [RequestBody] on purpose: the outbox stores the
 * exact bytes and a retry must send those same bytes, never a re-serialised object.
 */
interface SwiftQApi {
    @GET("v1/clinics")
    suspend fun listClinics(@Query("cursor") cursor: String? = null): Response<ResponseBody>

    @GET("v1/clinics/{clinic_id}/service-points")
    suspend fun listServicePoints(@Path("clinic_id") clinicId: String): Response<ResponseBody>

    @POST("v1/queues/{queue_id}/tickets")
    suspend fun joinQueue(
        @Path("queue_id") queueId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @GET("v1/tickets/{ticket_id}")
    suspend fun getTicket(@Path("ticket_id") ticketId: String): Response<ResponseBody>

    @GET("v1/queues/{queue_id}")
    suspend fun getQueue(
        @Path("queue_id") queueId: String,
        @Header("If-None-Match") etag: String? = null,
    ): Response<ResponseBody>

    @POST("v1/tickets/{ticket_id}/cancel")
    suspend fun cancelTicket(
        @Path("ticket_id") ticketId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<ResponseBody>

    @GET("v1/tickets/mine")
    suspend fun myTickets(@Query("active") active: Boolean = true): Response<ResponseBody>

    @POST("v1/sync/push")
    suspend fun syncPush(@Body body: RequestBody): Response<ResponseBody>
}
