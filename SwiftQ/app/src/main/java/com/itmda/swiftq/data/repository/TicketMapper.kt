package com.itmda.swiftq.data.repository

import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.remote.TicketDto

/** Copies the server's view of a ticket onto the local row, keeping what only the device knows (phone_enc, local_ref, ...). */
internal fun TicketEntity.withServerTicket(dto: TicketDto, nowMs: Long): TicketEntity = copy(
    serverId = dto.id,
    queueId = dto.queueId,
    servicePointId = dto.servicePointId,
    clinicId = dto.clinicId,
    displayNumber = dto.displayNumber,
    sequenceNo = dto.sequenceNo,
    status = dto.status,
    channel = dto.channel,
    position = dto.position,
    peopleAhead = dto.peopleAhead,
    waitMinMinutes = dto.estimatedWait?.minMinutes,
    waitMaxMinutes = dto.estimatedWait?.maxMinutes,
    waitColdStart = dto.estimatedWait?.coldStart,
    counterLabel = dto.counterLabel,
    priorityRequested = dto.priority.requestedCategory,
    priorityConfirmed = dto.priority.staffConfirmed,
    lateFlag = dto.lateFlag,
    displayName = dto.displayName ?: displayName,
    phoneLast4 = dto.phoneLast4 ?: phoneLast4,
    notificationChannel = dto.notificationChannel ?: notificationChannel,
    language = dto.language ?: language,
    createdAt = dto.createdAt,
    updatedAt = dto.updatedAt,
    calledAt = dto.calledAt,
    closedAt = dto.closedAt,
    lastSyncedAt = nowMs,
)
