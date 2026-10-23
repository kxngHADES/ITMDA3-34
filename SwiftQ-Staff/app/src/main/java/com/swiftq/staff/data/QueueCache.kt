package com.swiftq.staff.data

import android.content.Context
import com.swiftq.staff.data.remote.model.QueueResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Keeps the last queue the staff member saw so it can be shown offline.
class QueueCache(context: Context) {

    @Serializable
    data class Snapshot(
        val clinicId: String,
        val servicePointId: String,
        val servicePointName: String,
        val queue: QueueResponse,
        val syncedAtMillis: Long
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        "swiftq_queue_cache",
        Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun save(snapshot: Snapshot) {
        preferences.edit()
            .putString(KEY_SNAPSHOT, json.encodeToString(snapshot))
            .apply()
    }

    fun load(): Snapshot? {
        val stored = preferences.getString(KEY_SNAPSHOT, null) ?: return null

        return runCatching {
            json.decodeFromString<Snapshot>(stored)
        }.getOrNull()
    }

    // Records that the server confirmed the cached queue is still current.
    fun markSynced(syncedAtMillis: Long) {
        val snapshot = load() ?: return
        save(snapshot.copy(syncedAtMillis = syncedAtMillis))
    }

    fun saveCounterLabel(label: String) {
        preferences.edit()
            .putString(KEY_COUNTER_LABEL, label)
            .apply()
    }

    fun getCounterLabel(): String? {
        return preferences.getString(KEY_COUNTER_LABEL, null)
    }

    fun clear() {
        preferences.edit()
            .clear()
            .apply()
    }

    companion object {
        private const val KEY_SNAPSHOT = "last_queue_snapshot"
        private const val KEY_COUNTER_LABEL = "counter_label"
    }
}
