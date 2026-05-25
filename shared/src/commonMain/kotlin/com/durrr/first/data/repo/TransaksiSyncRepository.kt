package com.durrr.first.data.repo

import com.durrr.first.domain.model.OutboxEvent
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.network.ServerApiClient
import com.durrr.first.network.dto.TransactionBatchRequest
import com.durrr.first.network.dto.TransactionSyncEventDto
import com.durrr.first.network.dto.TransaksiDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TransaksiSyncRepository(
    private val syncRepository: SyncRepository,
    private val apiClient: ServerApiClient,
    private val settingsRepository: SettingsRepository? = null,
    private val nowIso: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun enqueueCheckout(
        transaksi: TransaksiDto,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): String {
        val eventId = IdGenerator.newId("evt_")
        syncRepository.enqueue(
            OutboxEvent(
                id = eventId,
                type = EVENT_TYPE_TRANSAKSI,
                payloadJson = json.encodeToString(transaksi),
                createdAt = nowIso(),
                sentAt = null,
                outletId = outletId,
            ),
            outletId = outletId,
        )
        return eventId
    }

    suspend fun flushPending(
        baseUrl: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): Int {
        val pending = syncRepository.getPending(outletId)
            .filter { it.type == EVENT_TYPE_TRANSAKSI }
            .filter { it.payloadJson.isNotBlank() }

        if (pending.isEmpty()) return 0

        val request = TransactionBatchRequest(
            events = pending.map { event ->
                TransactionSyncEventDto(
                    eventId = event.id,
                    entityType = EVENT_TYPE_TRANSAKSI,
                    op = "UPSERT",
                    payloadJson = event.payloadJson,
                    createdAt = event.createdAt,
                )
            },
            outletId = outletId,
        )
        val bearerToken = settingsRepository?.getActiveUserServerApiBearerToken(outletId)
            ?.trim()
            .orEmpty()
        if (settingsRepository != null && bearerToken.isBlank()) {
            error("Session server belum aktif. Pairing server dulu lalu login ulang.")
        }
        val response = apiClient.syncTransactions(
            baseUrl = baseUrl,
            request = request,
            bearerToken = bearerToken.ifBlank { null },
        )
        val sentAt = nowIso()
        val acceptedIds = if (response.acks.isNotEmpty()) {
            response.acks.filter { it.status.equals("ACCEPTED", ignoreCase = true) }.map { it.eventId }
        } else {
            response.accepted
        }
        acceptedIds.forEach { eventId -> syncRepository.markSent(eventId, sentAt, outletId) }
        return acceptedIds.size
    }

    companion object {
        private const val EVENT_TYPE_TRANSAKSI = "TRANSAKSI_CHECKOUT"
    }
}
