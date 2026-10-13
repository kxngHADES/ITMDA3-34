package com.example.swiftq.sync

import com.example.swiftq.data.remote.TicketDto

/**
 * What one outbox entry's send came to, whichever endpoint carried it. [OutboxProcessor] turns each
 * outcome into a ticket state and an outbox state, so REST and sync/push share one set of rules.
 */
sealed interface SendOutcome {
    /** Server accepted it: 201, 200 (replay or existing ticket), or sync outcome applied/duplicate/conflict_resolved. */
    data class Applied(val ticket: TicketDto) : SendOutcome

    /** sync outcome conflict_pending: the IT admin resolves it. The patient keeps seeing their saved place. */
    data object ConflictPending : SendOutcome

    /** The server will never accept it. [message] is the server's plain-language text, or null to use our own. */
    data class Rejected(val code: String?, val message: String?, val keyReused: Boolean = false) : SendOutcome

    /** Try again later with the same key and bytes. */
    data class Retry(
        val code: String? = null,
        val message: String? = null,
        val retryAfterSeconds: Int? = null,
    ) : SendOutcome

    /** An earlier entry failed, so this one was left alone to keep write order. */
    data object NotAttempted : SendOutcome
}
