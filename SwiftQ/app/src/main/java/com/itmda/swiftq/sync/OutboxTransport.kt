package com.itmda.swiftq.sync

import com.itmda.swiftq.data.local.OutboxEntity
import com.itmda.swiftq.data.local.OutboxOp
import com.itmda.swiftq.data.remote.ApiJson
import com.itmda.swiftq.data.remote.ApiResult
import com.itmda.swiftq.data.remote.ErrorBody
import com.itmda.swiftq.data.remote.OutboxItemDto
import com.itmda.swiftq.data.remote.SwiftQApi
import com.itmda.swiftq.data.remote.SyncPushRequest
import com.itmda.swiftq.data.remote.SyncPushResponse
import com.itmda.swiftq.data.remote.TicketDto
import com.itmda.swiftq.data.remote.safeApiCall
import com.itmda.swiftq.security.PayloadCipher
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Sends a batch of outbox rows (already in write order) and returns one outcome per row, same order. */
interface OutboxTransport {
    suspend fun send(entries: List<OutboxEntity>): List<SendOutcome>
}

private val JSON_TYPE = "application/json".toMediaType()

/** Maps a failed HTTP answer to what the outbox should do about it (shared by REST and sync/push errors). */
internal fun classifyFailure(httpStatus: Int, error: ErrorBody?, retryAfterSeconds: Int?): SendOutcome {
    val retryable = error?.retryable ?: (httpStatus >= 500 || httpStatus == 429 || httpStatus == 401)
    return when {
        error?.code == "IDEMPOTENCY_KEY_REUSED" ->
            // A bug in our app (payload changed under the same key). Never retry with a new key.
            SendOutcome.Rejected(error.code, null, keyReused = true)
        retryable || httpStatus >= 500 -> SendOutcome.Retry(error?.code, error?.message, retryAfterSeconds)
        else -> SendOutcome.Rejected(error?.code, error?.message)
    }
}

/**
 * Option "REST": a join goes to POST /v1/queues/{id}/tickets with `Idempotency-Key`. The body is the stored
 * payload decrypted back to the exact original bytes. Entries are sent one at a time in order; on the first
 * transient failure the rest are left for the next flush so writes never overtake each other.
 */
class RestJoinTransport(
    private val api: SwiftQApi,
    private val cipher: PayloadCipher,
) : OutboxTransport {
    override suspend fun send(entries: List<OutboxEntity>): List<SendOutcome> {
        val outcomes = ArrayList<SendOutcome>(entries.size)
        var stop = false
        for (entry in entries) {
            if (stop) { outcomes += SendOutcome.NotAttempted; continue }
            val outcome = sendOne(entry)
            outcomes += outcome
            if (outcome is SendOutcome.Retry) stop = true
        }
        return outcomes
    }

    private suspend fun sendOne(entry: OutboxEntity): SendOutcome {
        // P7: update_ticket (PATCH) and cancel have no REST path wired here yet.
        val queueId = entry.queueId
        if (entry.op != OutboxOp.JOIN || queueId == null) return SendOutcome.Rejected("UNSUPPORTED_OP", null)
        val body = cipher.decrypt(entry.payloadEnc).toRequestBody(JSON_TYPE)
        val result = safeApiCall({ ApiJson.decodeFromString(TicketDto.serializer(), it) }) {
            api.joinQueue(queueId, entry.idempotencyKey, body)
        }
        return when (result) {
            is ApiResult.Success -> SendOutcome.Applied(result.value) // 201 new ticket, or 200 replay/existing
            is ApiResult.Failure -> classifyFailure(result.httpStatus, result.error, result.retryAfterSeconds)
            is ApiResult.NetworkError -> SendOutcome.Retry()
        }
    }
}

/**
 * Option "sync/push": the whole batch (max 50) goes to POST /v1/sync/push. Results are matched to rows by
 * `idempotency_key` / `local_ref`, never by array position alone.
 * Not used against the current dev API, which has no /v1/sync/push (see handback).
 */
class SyncPushTransport(
    private val api: SwiftQApi,
    private val cipher: PayloadCipher,
    private val deviceId: suspend () -> String,
) : OutboxTransport {
    override suspend fun send(entries: List<OutboxEntity>): List<SendOutcome> {
        val items = entries.map { e ->
            OutboxItemDto(
                idempotencyKey = e.idempotencyKey,
                op = e.op,
                queueId = e.queueId,
                ticketId = e.ticketId.takeIf { e.op != OutboxOp.JOIN },
                localRef = e.localRef,
                clientUpdatedAt = e.clientUpdatedAt,
                attempt = (e.attempt + 1).coerceAtLeast(1),
                payload = Json.parseToJsonElement(cipher.decrypt(e.payloadEnc).toString(Charsets.UTF_8)),
            )
        }
        val request = ApiJson.encodeToString(SyncPushRequest.serializer(), SyncPushRequest(deviceId(), items))
        val result = safeApiCall({ ApiJson.decodeFromString(SyncPushResponse.serializer(), it) }) {
            api.syncPush(request.toRequestBody(JSON_TYPE))
        }
        return when (result) {
            is ApiResult.Success -> mapResults(entries, result.value)
            is ApiResult.Failure -> entries.map { classifyFailure(result.httpStatus, result.error, result.retryAfterSeconds) }
            is ApiResult.NetworkError -> entries.map { SendOutcome.Retry() }
        }
    }

    internal fun mapResults(entries: List<OutboxEntity>, response: SyncPushResponse): List<SendOutcome> {
        val byKey = response.results.associateBy { it.idempotencyKey }
        val byRef = response.results.filter { it.localRef != null }.associateBy { it.localRef }
        return entries.map { entry ->
            val r = byKey[entry.idempotencyKey] ?: byRef[entry.localRef] ?: return@map SendOutcome.Retry()
            when (r.outcome) {
                "applied", "duplicate", "conflict_resolved" ->
                    // conflict_resolved: the server's last-write-wins result is final, so just apply it.
                    r.ticket?.let { SendOutcome.Applied(it) } ?: SendOutcome.Retry()
                "conflict_pending" -> SendOutcome.ConflictPending
                "rejected" -> r.error?.let { classifyRejected(it) } ?: SendOutcome.Rejected(null, null)
                else -> SendOutcome.Retry()
            }
        }
    }

    // A `rejected` item is final by definition, even if the error says retryable.
    private fun classifyRejected(e: ErrorBody): SendOutcome =
        SendOutcome.Rejected(e.code, e.message, keyReused = e.code == "IDEMPOTENCY_KEY_REUSED")
}
