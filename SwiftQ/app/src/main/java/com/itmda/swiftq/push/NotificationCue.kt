package com.itmda.swiftq.push

import com.itmda.swiftq.data.local.TicketState

/** What the patient is being told. Mirrors the API's `kind` for the two alerts it pushes to patients. */
enum class Cue {
    /** The server confirmed a place that was only saved on the phone. */
    Confirmed,
    /** `almost_your_turn`: a few people left ahead. */
    AlmostTurn,
    /** `called`: go in now. */
    YourTurn;

    companion object {
        /** The `kind` field of the FCM data payload (api/services/notification_service.py). Other kinds are not for patients. */
        fun fromPushKind(kind: String?): Cue? = when (kind) {
            "called", "recalled" -> YourTurn
            "almost_your_turn" -> AlmostTurn
            else -> null
        }

        /** A cue for a ticket moving between states while the app is open (polling or a push-triggered refresh). */
        fun forTransition(from: String?, to: String?): Cue? = when {
            from == null || from == to -> null
            to == TicketState.CALLED -> YourTurn
            from == TicketState.PENDING_SYNC && to == TicketState.WAITING -> Confirmed
            else -> null
        }
    }
}
