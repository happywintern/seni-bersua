package com.durrr.first.features.cart.domain

data class OrderBuilderCartSnapshot(
    val tableToken: String?,
    val lines: List<OrderDraftLine>,
)

object OrderBuilderCartStore {
    private val snapshots = linkedMapOf<String, OrderBuilderCartSnapshot>()

    fun putSnapshot(outletId: String, snapshot: OrderBuilderCartSnapshot) {
        snapshots[normalizeOutlet(outletId)] = snapshot
    }

    fun getSnapshot(outletId: String): OrderBuilderCartSnapshot? {
        return snapshots[normalizeOutlet(outletId)]
    }

    fun clearSnapshot(outletId: String) {
        snapshots.remove(normalizeOutlet(outletId))
    }

    private fun normalizeOutlet(outletId: String): String {
        return outletId.trim().ifBlank { "default" }
    }
}
