package com.itmda.swiftq.data.repository

import com.itmda.swiftq.data.local.ClinicDao
import com.itmda.swiftq.data.local.ClinicEntity
import com.itmda.swiftq.data.local.ServicePointDao
import com.itmda.swiftq.data.local.ServicePointEntity
import com.itmda.swiftq.data.local.Transactor
import com.itmda.swiftq.data.remote.ApiJson
import com.itmda.swiftq.data.remote.ApiResult
import com.itmda.swiftq.data.remote.ClinicPage
import com.itmda.swiftq.data.remote.ClinicDto
import com.itmda.swiftq.data.remote.ItemList
import com.itmda.swiftq.data.remote.ServicePointDto
import com.itmda.swiftq.data.remote.SwiftQApi
import com.itmda.swiftq.data.remote.safeApiCall
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer

/** Why a refresh did not bring new data. The screen picks plain-language wording from this. */
enum class RefreshResult { Updated, Offline, ServerError, NotAvailable }

/** Clinics and service points: Room is the source for the screens, the API only refreshes it. */
class ClinicRepository(
    private val clinics: ClinicDao,
    private val servicePoints: ServicePointDao,
    private val transactor: Transactor,
    private val api: SwiftQApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeClinics(): Flow<List<ClinicEntity>> = clinics.observeAll()

    fun observeServicePoints(clinicId: String): Flow<List<ServicePointEntity>> = servicePoints.observeForClinic(clinicId)

    suspend fun clinic(id: String): ClinicEntity? = clinics.find(id)

    suspend fun servicePoint(id: String): ServicePointEntity? = servicePoints.find(id)

    suspend fun refreshClinics(): RefreshResult {
        val all = ArrayList<ClinicDto>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            when (val r = safeApiCall({ ApiJson.decodeFromString(ClinicPage.serializer(), it) }) { api.listClinics(cursor) }) {
                is ApiResult.Success -> {
                    all += r.value.items
                    cursor = r.value.nextCursor
                    if (!r.value.hasMore || cursor == null) return save(all)
                }
                is ApiResult.NetworkError -> return RefreshResult.Offline
                is ApiResult.Failure -> return if (r.httpStatus >= 500) RefreshResult.ServerError else RefreshResult.NotAvailable
            }
        }
        return save(all)
    }

    private suspend fun save(items: List<ClinicDto>): RefreshResult {
        val now = clock()
        val rows = items.map {
            ClinicEntity(
                id = it.id,
                name = it.name,
                address = it.address,
                isOpen = it.isOpen,
                isActive = it.isActive ?: true,
                openingHoursJson = it.openingHours?.let { h -> ApiJson.encodeToString(ListSerializer(com.itmda.swiftq.data.remote.OpeningHoursSlotDto.serializer()), h) },
                updatedAt = it.updatedAt ?: Instant.ofEpochMilli(now).toString(),
                fetchedAt = now,
            )
        }
        transactor.run {
            clinics.upsertAll(rows)
            if (rows.isEmpty()) clinics.deleteAll() else clinics.deleteNotIn(rows.map { it.id })
        }
        return RefreshResult.Updated
    }

    suspend fun refreshServicePoints(clinicId: String): RefreshResult {
        val result = safeApiCall({ ApiJson.decodeFromString(ItemList.serializer(ServicePointDto.serializer()), it) }) {
            api.listServicePoints(clinicId)
        }
        return when (result) {
            is ApiResult.Success -> {
                val now = clock()
                val rows = result.value.items.map {
                    ServicePointEntity(
                        id = it.id,
                        clinicId = it.clinicId,
                        name = it.name,
                        icon = it.icon,
                        isActive = it.isActive,
                        queueId = it.queueId,
                        concurrentServers = it.concurrentServers,
                        updatedAt = it.updatedAt ?: Instant.ofEpochMilli(now).toString(),
                        fetchedAt = now,
                    )
                }
                transactor.run {
                    servicePoints.upsertAll(rows)
                    servicePoints.deleteForClinicNotIn(clinicId, rows.map { it.id }.ifEmpty { listOf("") })
                }
                RefreshResult.Updated
            }
            is ApiResult.NetworkError -> RefreshResult.Offline
            is ApiResult.Failure -> if (result.httpStatus >= 500) RefreshResult.ServerError else RefreshResult.NotAvailable
        }
    }

    private companion object { const val MAX_PAGES = 20 }
}
