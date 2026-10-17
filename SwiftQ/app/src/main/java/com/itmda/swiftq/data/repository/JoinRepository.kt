package com.itmda.swiftq.data.repository

import com.itmda.swiftq.data.local.OutboxDao
import com.itmda.swiftq.data.local.OutboxEntity
import com.itmda.swiftq.data.local.OutboxOp
import com.itmda.swiftq.data.local.OutboxState
import com.itmda.swiftq.data.local.TicketDao
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.local.TicketState
import com.itmda.swiftq.data.local.Transactor
import com.itmda.swiftq.data.remote.ApiJson
import com.itmda.swiftq.data.remote.JoinPayload
import com.itmda.swiftq.data.remote.PatientPriorityDto
import com.itmda.swiftq.data.remote.PatientProfileDto
import com.itmda.swiftq.security.PayloadCipher
import com.itmda.swiftq.security.encryptText
import com.itmda.swiftq.sync.IdempotencyKeyFactory
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the patient typed, before validation. */
data class JoinInput(
    val clinicId: String,
    val servicePointId: String,
    val queueId: String,
    val displayName: String,
    val phone: String,
    val notificationChannel: String,
    val priorityRequest: String,
    val language: String,
    val email: String = "",
)

/** What the join form needs to know about the signed-in patient (null when anonymous). */
data class AccountDefaults(
    val displayName: String,
    val email: String,
    val language: String,
    val notificationChannel: String,
    val phoneLast4: String?,
    val priority: PatientPriorityDto,
) {
    val hasSavedPhone get() = phoneLast4 != null

    companion object {
        fun from(p: PatientProfileDto) = AccountDefaults(p.displayName, p.email, p.language, p.notificationChannel, p.phoneLast4, p.priority)
    }
}

sealed interface JoinResult {
    /** A local ticket and its outbox entry now exist. The caller should trigger a flush. */
    data class Created(val localRef: String) : JoinResult

    /** The patient already holds a ticket at this clinic (T-03.2), so nothing new was written. */
    data class AlreadyInQueue(val localRef: String) : JoinResult

    data class Invalid(
        val nameError: JoinValidation.NameError?,
        val phoneInvalid: Boolean,
        val phoneRequired: Boolean,
        val emailInvalid: Boolean = false,
        val emailRequired: Boolean = false,
    ) : JoinResult
}

class JoinRepository(
    private val tickets: TicketDao,
    private val outbox: OutboxDao,
    private val transactor: Transactor,
    private val cipher: PayloadCipher,
    private val keys: IdempotencyKeyFactory,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // One join at a time per process. A second tap waits here and then finds the ticket the first one wrote.
    private val joinLock = Mutex()

    /**
     * [account] is the signed-in patient's profile. The server fills in every field the body leaves out from it, so
     * unchanged contact details and an unchanged priority are left out instead of copied in.
     */
    suspend fun join(input: JoinInput, account: AccountDefaults? = null): JoinResult = joinLock.withLock {
        // --- Duplicate check (T-03.2): one active ticket per patient per clinic -------------------------
        // "Active" = pending_sync, waiting or called. This also covers a double tap and a tap right after a
        // restart, because the first tap's ticket is already in SQLite. The server enforces the same rule
        // (it answers 200 with the existing ticket), but checking here means we never even queue a second join.
        tickets.findActiveAtClinic(input.clinicId)?.let { return JoinResult.AlreadyInQueue(it.localRef) }

        val name = input.displayName.trim().ifEmpty { null }
        val phone = input.phone.trim().takeIf { it.isNotEmpty() }?.let { JoinValidation.normalisePhone(it) ?: return invalidPhone(input) }
        val rawEmail = input.email.trim().takeIf { it.isNotEmpty() }
        val email = rawEmail?.let { JoinValidation.normaliseEmail(it) ?: return JoinResult.Invalid(null, phoneInvalid = false, phoneRequired = false, emailInvalid = true) }
        val nameError = name?.let { JoinValidation.nameError(it) }
        // Same method as on the account and nothing new typed: the server applies the saved phone or account email.
        val usingSavedContact = account != null && input.notificationChannel == account.notificationChannel &&
            phone == null && (email == null || email == account.email.lowercase())
        // The saved number counts, so SMS no longer needs one typed in.
        val phoneRequired = NotificationChoice.needsPhone(input.notificationChannel) && phone == null &&
            !(usingSavedContact && account!!.hasSavedPhone)
        val emailRequired = NotificationChoice.needsEmail(input.notificationChannel) && email == null && !usingSavedContact
        if (nameError != null || phoneRequired || emailRequired) {
            return JoinResult.Invalid(nameError, phoneInvalid = false, phoneRequired = phoneRequired, emailRequired = emailRequired)
        }
        // The address only matters when Email was chosen; a leftover from switching tabs is dropped.
        val keptEmail = email.takeIf { NotificationChoice.needsEmail(input.notificationChannel) }
        val keptPhone = phone.takeIf { NotificationChoice.needsPhone(input.notificationChannel) || input.notificationChannel == NotificationChoice.PUSH }

        // --- Identity of this intent, minted once -------------------------------------------------------
        val now = clock()
        val nowIso = Instant.ofEpochMilli(now).toString()
        val localRef = keys.newKey()
        val idempotencyKey = keys.newKey() // created here and nowhere else; every retry reads it from the outbox row

        // The exact body, serialised once. It is stored encrypted and replayed byte for byte on every retry.
        val payload = JoinPayload(
            displayName = name ?: account?.displayName,
            phone = if (usingSavedContact) null else keptPhone,
            email = if (usingSavedContact) null else keptEmail,
            language = input.language,
            notificationChannel = if (usingSavedContact) null else input.notificationChannel,
            priorityRequest = when {
                account == null -> input.priorityRequest                                  // anonymous: as before
                input.priorityRequest == account.priority.category -> null                // the account's own
                input.priorityRequest == PriorityChoice.NONE &&
                    account.priority.category != PriorityChoice.NONE -> null              // "No, I am fine" keeps it
                else -> input.priorityRequest                                             // something new this visit
            },
            clientCreatedAt = nowIso,
        )
        // The ticket screen shows the effective values offline: the account's priority and the saved number's last 4.
        val effectivePriority = payload.priorityRequest ?: account?.priority?.category ?: input.priorityRequest
        val last4 = if (usingSavedContact) account?.phoneLast4 else keptPhone?.takeLast(4)
        val payloadJson = ApiJson.encodeToString(JoinPayload.serializer(), payload)

        // --- One transaction: the ticket and its outbox row exist together or not at all -----------------
        transactor.run {
            tickets.insert(
                TicketEntity(
                    localRef = localRef,
                    queueId = input.queueId,
                    servicePointId = input.servicePointId,
                    clinicId = input.clinicId,
                    status = TicketState.PENDING_SYNC,
                    priorityRequested = effectivePriority,
                    priorityConfirmed = payload.priorityRequest == null && account?.priority?.active == true,
                    displayName = payload.displayName,
                    phoneEnc = keptPhone?.let { cipher.encryptText(it) }, // POPIA: never plain text on disk
                    phoneLast4 = last4,
                    emailEnc = keptEmail?.let { cipher.encryptText(it) },
                    notificationChannel = input.notificationChannel,
                    language = input.language,
                    clientCreatedAt = nowIso,
                    updatedAt = nowIso,
                ),
            )
            outbox.insert(
                OutboxEntity(
                    idempotencyKey = idempotencyKey,
                    op = OutboxOp.JOIN,
                    queueId = input.queueId,
                    ticketId = localRef,
                    localRef = localRef,
                    payloadEnc = cipher.encryptText(payloadJson),
                    clientUpdatedAt = nowIso,
                    state = OutboxState.PENDING,
                    attempt = 0,
                    nextAttemptAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        JoinResult.Created(localRef)
    }

    private fun invalidPhone(input: JoinInput) =
        JoinResult.Invalid(nameError = null, phoneInvalid = true, phoneRequired = false)
}
