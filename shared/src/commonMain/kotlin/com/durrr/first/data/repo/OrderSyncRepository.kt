package com.durrr.first.data.repo

import com.durrr.first.domain.model.OrderHeader
import com.durrr.first.domain.model.OrderItem
import com.durrr.first.domain.model.OrderStatus
import com.durrr.first.network.ServerApiClient
import com.durrr.first.network.dto.ServerOrderDto
import com.durrr.first.network.dto.ServerOrderStatus
import com.durrr.first.network.dto.ServerReservationStatus
import kotlin.math.max

class OrderSyncRepository(
    private val orderCacheRepository: OrderCacheRepository,
    private val apiClient: ServerApiClient,
    private val transaksiRepository: TransaksiRepository? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val nowIso: () -> String = { "" },
) {
    data class UpdateStatusResult(
        val localSaved: Boolean,
        val remoteSynced: Boolean,
        val warning: String? = null,
    )

    data class ReservationSyncSummary(
        val total: Int,
        val pending: Int,
    )

    suspend fun pullOrders(baseUrl: String, outletId: String? = null): Int {
        val scopedOutletId = outletId.orEmpty().ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
        val bearerToken = settingsRepository?.getActiveUserServerApiBearerToken(scopedOutletId)
            ?.trim()
            .orEmpty()
        if (settingsRepository != null && bearerToken.isBlank()) {
            error("Session server belum aktif. Pairing server dulu lalu login ulang.")
        }
        val remoteOrders = apiClient.fetchOrders(
            baseUrl = baseUrl,
            statuses = listOf(
                ServerOrderStatus.NEW,
                ServerOrderStatus.ACCEPTED,
                ServerOrderStatus.PREPARING,
                ServerOrderStatus.SERVED,
                ServerOrderStatus.DONE,
            ),
            outletId = scopedOutletId,
            bearerToken = bearerToken.ifBlank { null },
        )
        remoteOrders.forEach(::cacheRemoteOrder)
        return remoteOrders.size
    }

    suspend fun updateStatus(
        baseUrl: String?,
        orderId: String,
        targetStatus: ServerOrderStatus,
        outletId: String? = null,
    ): UpdateStatusResult {
        val scopedOutletId = outletId.orEmpty().ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
        var localSaved = false

        val localOrder = orderCacheRepository.getOrderById(orderId, scopedOutletId)
        if (localOrder != null) {
            val localStatus = mapStatus(targetStatus.name)
            val updatedHeader = localOrder.header.copy(
                status = localStatus,
                updatedAt = nowIso().ifBlank { localOrder.header.updatedAt },
                outletId = scopedOutletId,
            )
            orderCacheRepository.upsertOrder(
                order = updatedHeader,
                items = localOrder.items,
                outletId = scopedOutletId,
            )
            if (localStatus == OrderStatus.DONE) {
                transaksiRepository?.ensureOrderRecordedAsTransaksi(
                    order = updatedHeader,
                    items = localOrder.items,
                    nowIso = nowIso,
                    outletId = scopedOutletId,
                )
            }
            localSaved = true
        }

        val normalizedBaseUrl = baseUrl?.trim().orEmpty().ifBlank { null }
        if (normalizedBaseUrl == null) {
            return UpdateStatusResult(
                localSaved = localSaved,
                remoteSynced = false,
                warning = if (localSaved) {
                    "Status tersimpan lokal. Nanti sync setelah server tersedia."
                } else {
                    "Status belum bisa disimpan karena order lokal tidak ditemukan."
                },
            )
        }

        val bearerToken = settingsRepository?.getActiveUserServerApiBearerToken(scopedOutletId)
        if (bearerToken.isNullOrBlank()) {
            return UpdateStatusResult(
                localSaved = localSaved,
                remoteSynced = false,
                warning = if (localSaved) {
                    "Status tersimpan lokal. Pairing server lalu login ulang untuk sync server."
                } else {
                    "Aksi order butuh auth. Pairing server dulu lalu login ulang dengan PIN aktif."
                },
            )
        }

        return runCatching {
            apiClient.updateOrderStatus(
                baseUrl = normalizedBaseUrl,
                orderId = orderId,
                status = targetStatus,
                outletId = scopedOutletId,
                bearerToken = bearerToken,
            )
        }.fold(
            onSuccess = { updated ->
                cacheRemoteOrder(updated)
                UpdateStatusResult(
                    localSaved = true,
                    remoteSynced = true,
                    warning = null,
                )
            },
            onFailure = { error ->
                UpdateStatusResult(
                    localSaved = localSaved,
                    remoteSynced = false,
                    warning = buildString {
                        if (localSaved) {
                            append("Status tersimpan lokal, sync server gagal")
                        } else {
                            append("Gagal update status")
                        }
                        val message = error.message?.takeIf { it.isNotBlank() }
                        if (message != null) {
                            append(": ")
                            append(message)
                        }
                    },
                )
            },
        )
    }

    suspend fun fetchReservationSummary(
        baseUrl: String,
        outletId: String? = null,
    ): ReservationSyncSummary? {
        val scopedOutletId = outletId.orEmpty().ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
        val bearerToken = settingsRepository?.getActiveUserServerApiBearerToken(scopedOutletId)
            ?.trim()
            .orEmpty()
            .ifBlank { return null }
        val reservations = apiClient.fetchReservations(
            baseUrl = baseUrl,
            statuses = listOf(
                ServerReservationStatus.PENDING,
                ServerReservationStatus.CONFIRMED,
                ServerReservationStatus.SEATED,
            ),
            outletId = scopedOutletId,
            bearerToken = bearerToken,
        )
        val pendingCount = reservations.count { it.status.equals("PENDING", ignoreCase = true) }
        return ReservationSyncSummary(
            total = reservations.size,
            pending = pendingCount,
        )
    }

    private fun cacheRemoteOrder(order: ServerOrderDto) {
        val notes = listOfNotNull(
            order.customerName?.takeIf { it.isNotBlank() }?.let { "Customer: $it" },
            order.paymentConfirmation?.takeIf { it.isNotBlank() }?.let { raw ->
                if (raw.equals("CASHIER", ignoreCase = true)) "Payment: At Cashier" else "Payment: $raw"
            },
            order.note?.takeIf { it.isNotBlank() },
        ).joinToString(" | ").ifBlank { null }

        val header = OrderHeader(
            id = order.id,
            token = order.customerUuid,
            status = mapStatus(order.status),
            notes = notes,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
            outletId = order.outletId,
        )
        val items = order.items.map { item ->
            OrderItem(
                id = item.id,
                orderId = order.id,
                itemId = item.menuId,
                itemName = item.itemName,
                qty = max(1, item.qty),
                price = max(0, item.price),
                note = item.note,
            )
        }
        orderCacheRepository.upsertOrder(
            order = header,
            items = items,
            outletId = order.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
        )
        if (header.status == OrderStatus.DONE) {
            transaksiRepository?.ensureOrderRecordedAsTransaksi(
                order = header,
                items = items,
                nowIso = nowIso,
                outletId = order.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
            )
        }
    }

    private fun mapStatus(status: String): OrderStatus {
        return when (status.uppercase()) {
            "NEW" -> OrderStatus.NEW
            "ACCEPTED" -> OrderStatus.ACCEPTED
            "PREPARING" -> OrderStatus.COOKING
            "SERVED" -> OrderStatus.SERVED
            "DONE" -> OrderStatus.DONE
            "CANCELLED" -> OrderStatus.CANCELLED
            else -> OrderStatus.NEW
        }
    }
}
