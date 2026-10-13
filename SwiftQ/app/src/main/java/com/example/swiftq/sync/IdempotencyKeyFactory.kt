package com.example.swiftq.sync

import java.util.UUID

/**
 * The one place identifiers are minted.
 *
 * Idempotency-key lifecycle (Chapter 4.4.1):
 *  1. A key is created exactly once, when the patient taps Join, inside the same transaction that writes
 *     the `tickets` row and the `outbox` row.
 *  2. It is stored in `outbox.idempotency_key` (UNIQUE) and never regenerated.
 *  3. Every retry (network retry, process death, reboot, later flush) reads it back from that row.
 * [UUID.randomUUID] is a version-4 (random) UUID, which is what the API requires.
 */
interface IdempotencyKeyFactory {
    fun newKey(): String
}

class UuidV4KeyFactory : IdempotencyKeyFactory {
    override fun newKey(): String = UUID.randomUUID().toString()
}
