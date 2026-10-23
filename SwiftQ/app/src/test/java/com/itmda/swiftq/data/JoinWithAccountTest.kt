package com.itmda.swiftq.data

import com.itmda.swiftq.FakeStore
import com.itmda.swiftq.RandomKeys
import com.itmda.swiftq.TestCipher
import com.itmda.swiftq.data.remote.ApiJson
import com.itmda.swiftq.data.remote.PatientPriorityDto
import com.itmda.swiftq.data.repository.AccountDefaults
import com.itmda.swiftq.data.repository.JoinInput
import com.itmda.swiftq.data.repository.JoinRepository
import com.itmda.swiftq.data.repository.JoinResult
import com.itmda.swiftq.security.decryptText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section 5 of the patient-accounts guide: what a signed-in patient's join body contains. */
class JoinWithAccountTest {
    private val store = FakeStore()
    private val cipher = TestCipher()
    private val joins = JoinRepository(store.ticketDao, store.outboxDao, store.transactor, cipher, RandomKeys()) { 1_000_000L }

    private val confirmedElderly = PatientPriorityDto(category = "elderly", verified = true, active = true)
    private val account = AccountDefaults("Thandi", "thandi@example.com", "zu", "sms", "4567", confirmedElderly)

    private fun input(channel: String = "sms", priority: String = "elderly", phone: String = "", email: String = "", name: String = "Thandi") =
        JoinInput("c1", "sp1", "q1", name, phone, channel, priority, "zu", email)

    private fun sentBody(): JsonObject {
        val payload = cipher.decryptText(store.outbox.value.single().payloadEnc)
        return ApiJson.decodeFromString(JsonObject.serializer(), payload)
    }

    @Test fun keepingSavedContactAndPriorityLeavesThemOut() = runBlocking {
        assertTrue(joins.join(input(), account) is JoinResult.Created)
        val body = sentBody()
        assertFalse("notification_channel" in body)
        assertFalse("phone" in body)
        assertFalse("email" in body)
        assertFalse("priority_request" in body)
        assertEquals("zu", body["language"]!!.jsonPrimitive.content)
        val ticket = store.tickets.value.values.single()
        assertEquals("the ticket still records the effective values", "elderly", ticket.priorityRequested)
        assertTrue(ticket.priorityConfirmed)
        assertEquals("4567", ticket.phoneLast4)
        assertEquals("sms", ticket.notificationChannel)
    }

    @Test fun noIAmFineNeverSendsNoneOverASavedPriority() = runBlocking {
        joins.join(input(priority = "none"), account)
        assertFalse("priority_request" in sentBody())
        assertEquals("elderly", store.tickets.value.values.single().priorityRequested)
    }

    @Test fun pickingAnotherPriorityForThisVisitSendsIt() = runBlocking {
        joins.join(input(priority = "urgent"), account)
        assertEquals("urgent", sentBody()["priority_request"]!!.jsonPrimitive.content)
        assertFalse(store.tickets.value.values.single().priorityConfirmed)
    }

    @Test fun aNewNumberTypedForThisVisitIsSent() = runBlocking {
        joins.join(input(phone = "083 555 1234"), account)
        val body = sentBody()
        assertEquals("sms", body["notification_channel"]!!.jsonPrimitive.content)
        assertEquals("+27835551234", body["phone"]!!.jsonPrimitive.content)
    }

    @Test fun switchingToEmailWithTheAccountAddressIsSentForThisVisit() = runBlocking {
        joins.join(input(channel = "email", email = "thandi@example.com"), account)
        val body = sentBody()
        assertEquals("email", body["notification_channel"]!!.jsonPrimitive.content)
        assertEquals("thandi@example.com", body["email"]!!.jsonPrimitive.content)
    }

    @Test fun savedEmailChannelNeedsNothingTyped() = runBlocking {
        val emailAccount = account.copy(notificationChannel = "email", phoneLast4 = null)
        assertTrue(joins.join(input(channel = "email", email = "thandi@example.com"), emailAccount) is JoinResult.Created)
        assertFalse("email" in sentBody())
    }

    @Test fun smsWithoutASavedNumberStillNeedsOne() = runBlocking {
        val noPhone = account.copy(phoneLast4 = null)
        val result = joins.join(input(), noPhone)
        assertTrue(result is JoinResult.Invalid && result.phoneRequired)
    }

    @Test fun anonymousJoinIsUnchanged() = runBlocking {
        joins.join(input(priority = "none", phone = "082 123 4567"), account = null)
        val body = sentBody()
        assertEquals("none", body["priority_request"]!!.jsonPrimitive.content)
        assertEquals("sms", body["notification_channel"]!!.jsonPrimitive.content)
        assertEquals("+27821234567", body["phone"]!!.jsonPrimitive.content)
    }
}
