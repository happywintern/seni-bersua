package com.durrr.first.features.product.domain

data class BundleItemInput(
    val itemId: String,
    val itemName: String,
    val qty: Int,
)

data class BundleDraft(
    val id: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val price: Long,
    val items: List<BundleItemInput>,
    val imageUrl: String? = null,
)
