package com.durrr.first.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerMenuItemDto(
    val id: String,
    val name: String,
    val price: Long,
    @SerialName("groupId") val groupId: String? = null,
    @SerialName("groupName") val groupName: String? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("outletId") val outletId: String? = null,
)

@Serializable
data class ServerModifierOptionDto(
    val id: String,
    val name: String,
    @SerialName("priceDelta") val priceDelta: Long = 0,
    @SerialName("order") val order: Int = 0,
    @SerialName("isDefault") val isDefault: Boolean = false,
)

@Serializable
data class ServerModifierGroupDto(
    val id: String,
    val name: String,
    @SerialName("selectionType") val selectionType: String = "SINGLE",
    @SerialName("isRequired") val isRequired: Boolean = false,
    @SerialName("maxSelection") val maxSelection: Int = 1,
    val options: List<ServerModifierOptionDto> = emptyList(),
)

@Serializable
data class ServerProductModifierLinkDto(
    @SerialName("itemId") val itemId: String,
    @SerialName("modifierGroupIds") val modifierGroupIds: List<String> = emptyList(),
)

@Serializable
data class ServerMenuCatalogDto(
    val items: List<ServerMenuItemDto> = emptyList(),
    @SerialName("modifierGroups") val modifierGroups: List<ServerModifierGroupDto> = emptyList(),
    @SerialName("productModifierLinks") val productModifierLinks: List<ServerProductModifierLinkDto> = emptyList(),
)

@Serializable
data class UpsertModifierGroupRequest(
    val id: String,
    val name: String,
    @SerialName("selection_type") val selectionType: String = "SINGLE",
    @SerialName("is_required") val isRequired: Boolean = false,
    @SerialName("max_selection") val maxSelection: Int = 1,
    val options: List<ServerModifierOptionDto> = emptyList(),
    @SerialName("outlet_id") val outletId: String? = null,
)

@Serializable
data class AssignProductModifiersRequest(
    @SerialName("modifier_group_ids") val modifierGroupIds: List<String> = emptyList(),
    @SerialName("outlet_id") val outletId: String? = null,
)

@Serializable
data class ServerOrderItemDto(
    val id: String,
    val menuId: String? = null,
    val itemName: String,
    val qty: Long,
    val price: Long,
    val lineTotal: Long,
    val note: String? = null,
)

@Serializable
data class ServerOrderDto(
    val id: String,
    val customerUuid: String? = null,
    val customerName: String? = null,
    val status: String,
    val note: String? = null,
    val paymentConfirmation: String? = null,
    @SerialName("outletId") val outletId: String? = null,
    val createdAt: String,
    val updatedAt: String? = null,
    val total: Long = 0L,
    val items: List<ServerOrderItemDto> = emptyList(),
)

@Serializable
data class ServerOrderStatusRequest(
    val status: String,
    @SerialName("outlet_id") val outletId: String? = null,
)

enum class ServerOrderStatus {
    NEW,
    ACCEPTED,
    PREPARING,
    SERVED,
    DONE,
    CANCELLED,
}

@Serializable
data class ServerReservationDto(
    val id: String,
    @SerialName("customer_name") val customerName: String,
    val phone: String? = null,
    @SerialName("party_size") val partySize: Int,
    @SerialName("reservation_at") val reservationAt: String,
    @SerialName("reservation_date") val reservationDate: String? = null,
    @SerialName("reservation_time") val reservationTime: String? = null,
    val status: String,
    val note: String? = null,
    @SerialName("outlet_id") val outletId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ServerReservationStatusRequest(
    val status: String,
    @SerialName("outlet_id") val outletId: String? = null,
)

enum class ServerReservationStatus {
    PENDING,
    CONFIRMED,
    SEATED,
    COMPLETED,
    CANCELLED,
}

@Serializable
data class ServerAuthSessionLoginRequest(
    val role: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("outlet_id") val outletId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class ServerAuthSessionRefreshRequest(
    @SerialName("outlet_id") val outletId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class ServerAuthSessionDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("access_token") val accessToken: String,
    val role: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    @SerialName("outlet_id") val outletId: String,
    @SerialName("issued_at") val issuedAt: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class ServerAuthPairingCodeCreateRequest(
    val role: String = "OWNER",
    @SerialName("outlet_id") val outletId: String? = null,
    @SerialName("ttl_seconds") val ttlSeconds: Long? = null,
)

@Serializable
data class ServerAuthPairingCodeDto(
    @SerialName("pairing_code") val pairingCode: String,
    val role: String,
    @SerialName("outlet_id") val outletId: String,
    @SerialName("issued_at") val issuedAt: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class ServerAuthPairingCodeRedeemRequest(
    @SerialName("pairing_code") val pairingCode: String,
    val role: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("outlet_id") val outletId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
)

@Serializable
data class ServerAuthUserDto(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    val role: String,
    @SerialName("outlet_id") val outletId: String,
    val active: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("last_login_at") val lastLoginAt: String? = null,
)

@Serializable
data class ServerAuthUserUpsertRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    val role: String,
    @SerialName("outlet_id") val outletId: String? = null,
    val active: Boolean = true,
)

@Serializable
data class ServerAuthUserStatusRequest(
    val role: String,
    val active: Boolean,
    @SerialName("outlet_id") val outletId: String? = null,
)

@Serializable
data class ServerAuthSessionViewDto(
    @SerialName("session_id") val sessionId: String,
    val role: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    @SerialName("outlet_id") val outletId: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("issued_at") val issuedAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
)

@Serializable
data class ServerAuthAuditLogDto(
    val id: String,
    @SerialName("outlet_id") val outletId: String,
    @SerialName("actor_role") val actorRole: String? = null,
    @SerialName("actor_user_id") val actorUserId: String? = null,
    val action: String,
    @SerialName("target_type") val targetType: String? = null,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("payload_json") val payloadJson: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ServerOutletDto(
    @SerialName("outlet_id") val outletId: String,
    val name: String,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ServerOutletUpsertRequest(
    @SerialName("outlet_id") val outletId: String,
    val name: String,
    val active: Boolean = true,
)

@Serializable
data class ServerOutletStatusRequest(
    val active: Boolean,
    @SerialName("outlet_id") val outletId: String? = null,
)
