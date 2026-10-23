package com.itmda.swiftq

import com.itmda.swiftq.data.local.TicketState
import com.itmda.swiftq.feedback.FeedbackPlayer
import com.itmda.swiftq.push.Cue
import com.itmda.swiftq.ui.status.spokenTicketNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushAndFeedbackTest {
    @Test fun apiPushKindsMapToCues() {
        assertEquals(Cue.YourTurn, Cue.fromPushKind("called"))
        assertEquals(Cue.YourTurn, Cue.fromPushKind("recalled"))
        assertEquals(Cue.AlmostTurn, Cue.fromPushKind("almost_your_turn"))
        // Kinds meant for staff or not for patients are ignored.
        assertNull(Cue.fromPushKind("priority_requested"))
        assertNull(Cue.fromPushKind(null))
    }

    @Test fun ticketTransitionsPickTheRightCue() {
        assertEquals(Cue.Confirmed, Cue.forTransition(TicketState.PENDING_SYNC, TicketState.WAITING))
        assertEquals(Cue.YourTurn, Cue.forTransition(TicketState.WAITING, TicketState.CALLED))
        assertEquals(Cue.YourTurn, Cue.forTransition(TicketState.PENDING_SYNC, TicketState.CALLED))
        // The first value after app start is not a change, and nothing else rings.
        assertNull(Cue.forTransition(null, TicketState.CALLED))
        assertNull(Cue.forTransition(TicketState.WAITING, TicketState.WAITING))
        assertNull(Cue.forTransition(TicketState.CALLED, TicketState.SERVED))
    }

    @Test fun yourTurnIsTheLongestPattern() {
        val turn = FeedbackPlayer.pattern(Cue.YourTurn).sum()
        assertTrue(turn > FeedbackPlayer.pattern(Cue.AlmostTurn).sum())
        assertTrue(FeedbackPlayer.pattern(Cue.AlmostTurn).sum() > FeedbackPlayer.pattern(Cue.Confirmed).sum())
    }

    @Test fun ticketNumbersAreSpokenCharacterByCharacter() {
        assertEquals("A, 0 1 4", spokenTicketNumber("A014"))
        assertEquals("B C, 1 2", spokenTicketNumber("BC12"))
        assertNull(spokenTicketNumber(null))
    }
}
