package com.durrr.first.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpsertMenuItemRequest(
    val id: String? = null,
    val name: String,
    val price: Long,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("outlet_id") val outletId: String? = null,
)

@Serializable
data class MenuImageUploadResponse(
    @SerialName("image_url") val imageUrl: String,
)
