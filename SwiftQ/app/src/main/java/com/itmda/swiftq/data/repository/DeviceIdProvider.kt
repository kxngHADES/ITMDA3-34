package com.itmda.swiftq.data.repository

import com.itmda.swiftq.data.local.SyncKeys
import com.itmda.swiftq.data.local.SyncStateDao
import com.itmda.swiftq.data.local.SyncStateEntity
import com.itmda.swiftq.sync.IdempotencyKeyFactory

/** A stable install id (a UUIDv4 made once and kept in `sync_state`), not the IMEI. Sent as `device_id` on sync. */
class DeviceIdProvider(
    private val syncState: SyncStateDao,
    private val keys: IdempotencyKeyFactory,
) {
    suspend fun get(): String =
        syncState.get(SyncKeys.DEVICE_ID) ?: keys.newKey().also { syncState.put(SyncStateEntity(SyncKeys.DEVICE_ID, it)) }
}
