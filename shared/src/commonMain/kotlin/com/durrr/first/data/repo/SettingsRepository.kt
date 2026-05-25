package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.ReceiptConfig
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.network.dto.ServerAuthSessionDto

class SettingsRepository(private val db: TokoDatabase) {
    data class LocalAccountSession(
        val role: String,
        val userId: String,
        val userName: String,
    )

    fun loadReceiptConfig(): ReceiptConfig {
        return runCatching {
            ensureSettingsTable()
            val values = db.tokoQueries
                .selectAllAppSettings()
                .executeAsList()
                .associate { it.setting_key to (it.setting_value ?: "") }
            ReceiptConfig(
                storeName = values[KEY_STORE_NAME].orEmpty().ifBlank { "SuCash" },
                storeAddressOrPhone = values[KEY_STORE_ADDRESS].orEmpty(),
                headerLogoPath = values[KEY_STORE_LOGO].orEmpty(),
                watermarkLogoPath = values[KEY_WATERMARK_LOGO].orEmpty(),
                footerText = values[KEY_FOOTER_TEXT].orEmpty().ifBlank { "Thank you" },
            )
        }.getOrElse {
            defaultConfig()
        }
    }

    fun saveReceiptConfig(config: ReceiptConfig): Boolean {
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                db.tokoQueries.upsertAppSetting(KEY_STORE_NAME, config.storeName)
                db.tokoQueries.upsertAppSetting(KEY_STORE_ADDRESS, config.storeAddressOrPhone)
                db.tokoQueries.upsertAppSetting(KEY_STORE_LOGO, config.headerLogoPath)
                db.tokoQueries.upsertAppSetting(KEY_WATERMARK_LOGO, config.watermarkLogoPath)
                db.tokoQueries.upsertAppSetting(KEY_FOOTER_TEXT, config.footerText)
            }
            true
        }.getOrDefault(false)
    }

    fun upsert(key: String, value: String): Boolean {
        return runCatching {
            ensureSettingsTable()
            db.tokoQueries.upsertAppSetting(key, value)
            true
        }.getOrDefault(false)
    }

    fun getValue(key: String): String {
        return runCatching {
            ensureSettingsTable()
            val row = db.tokoQueries.selectAppSetting(key).executeAsOneOrNull()
            row?.setting_value.orEmpty()
        }.getOrDefault("")
    }

    fun getOptionalValue(key: String): String? {
        return getValue(key).trim().ifBlank { null }
    }

    fun getOptionalServerBaseUrl(): String? = getOptionalValue(KEY_SERVER_BASE_URL)

    fun getDefaultCashierId(): String? = getOptionalValue(KEY_DEFAULT_CASHIER_ID)

    fun getDefaultCashierName(): String? = getOptionalValue(KEY_DEFAULT_CASHIER_NAME)

    fun getOwnerName(): String? = getOptionalValue(KEY_OWNER_NAME)

    fun ensureDefaultCashierId(existingName: String? = null): String {
        val existing = getDefaultCashierId()
        if (existing != null) return existing
        val generated = buildDefaultCashierId(existingName)
        upsert(KEY_DEFAULT_CASHIER_ID, generated)
        return generated
    }

    fun hasOwnerPinConfigured(): Boolean = isValidPin(getValue(KEY_OWNER_PIN))

    fun hasCashierPinConfigured(): Boolean = isValidPin(getValue(KEY_DEFAULT_CASHIER_PIN))

    fun hasAnyLoginPinConfigured(): Boolean = hasOwnerPinConfigured() || hasCashierPinConfigured()

    fun verifyOwnerPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        return getValue(KEY_OWNER_PIN) == pin
    }

    fun verifyCashierPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        return getValue(KEY_DEFAULT_CASHIER_PIN) == pin
    }

    fun setActiveUserOwner(): Boolean {
        val ownerName = getOwnerName()?.ifBlank { null } ?: "Owner"
        return setActiveUser(
            role = ROLE_OWNER,
            userId = "owner",
            userName = ownerName,
        )
    }

    fun setActiveUserCashier(): Boolean {
        val cashierName = getDefaultCashierName()?.ifBlank { null } ?: "Cashier"
        val cashierId = ensureDefaultCashierId(cashierName)
        return setActiveUser(
            role = ROLE_CASHIER,
            userId = cashierId,
            userName = cashierName,
        )
    }

    fun setActiveUser(role: String, userId: String, userName: String): Boolean {
        if (role != ROLE_OWNER && role != ROLE_CASHIER) return false
        if (userId.isBlank() || userName.isBlank()) return false
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_ROLE, role)
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_ID, userId)
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_NAME, userName)
            }
            true
        }.getOrDefault(false)
    }

    fun clearActiveUser(): Boolean {
        val active = getActiveUserSession()
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_ROLE, "")
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_ID, "")
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_NAME, "")
                clearLegacyServerSessionSlots()
            }
            if (active != null) {
                clearScopedServerSessionSlots(
                    outletId = null,
                    role = active.role,
                    userId = active.userId,
                )
            }
            true
        }.getOrDefault(false)
    }

    fun getActiveUserSession(): LocalAccountSession? {
        val role = getOptionalValue(KEY_ACTIVE_USER_ROLE) ?: return null
        val userId = getOptionalValue(KEY_ACTIVE_USER_ID) ?: return null
        val userName = getOptionalValue(KEY_ACTIVE_USER_NAME) ?: return null
        if (role != ROLE_OWNER && role != ROLE_CASHIER) return null
        return LocalAccountSession(
            role = role,
            userId = userId,
            userName = userName,
        )
    }

    fun getActiveUserServerApiBearerToken(outletId: String? = null): String? {
        val active = getActiveUserSession() ?: return null
        val normalizedOutletId = resolveOutletId(outletId)
        return getStoredServerSessionToken(
            outletId = normalizedOutletId,
            role = active.role,
            userId = active.userId,
        )
    }

    fun saveServerAuthSession(session: ServerAuthSessionDto): Boolean {
        val normalizedOutletId = resolveOutletId(session.outletId)
        val normalizedRole = session.role.trim().uppercase()
        val normalizedUserId = session.userId.trim()
        if (normalizedRole != ROLE_OWNER && normalizedRole != ROLE_CASHIER) return false
        if (normalizedUserId.isBlank()) return false
        val scope = buildServerSessionScope(
            outletId = normalizedOutletId,
            role = normalizedRole,
            userId = normalizedUserId,
        )
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                // Keep backward-compatible single-slot keys for old flows.
                db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_TOKEN, session.accessToken.trim())
                db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_ID, session.sessionId.trim())
                db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_ROLE, normalizedRole)
                db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_USER_ID, normalizedUserId)
                db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_USER_NAME, session.userName.trim())
                db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_OUTLET_ID, normalizedOutletId)
                db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_ISSUED_AT, session.issuedAt.trim())
                db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_EXPIRES_AT, session.expiresAt.trim())
                // Keep active local scope aligned with paired server outlet.
                db.tokoQueries.upsertAppSetting(KEY_OUTLET_ID, normalizedOutletId)

                // Multi-outlet/user scoped slots.
                db.tokoQueries.upsertAppSetting(scope.tokenKey, session.accessToken.trim())
                db.tokoQueries.upsertAppSetting(scope.sessionIdKey, session.sessionId.trim())
                db.tokoQueries.upsertAppSetting(scope.userNameKey, session.userName.trim())
                db.tokoQueries.upsertAppSetting(scope.issuedAtKey, session.issuedAt.trim())
                db.tokoQueries.upsertAppSetting(scope.expiresAtKey, session.expiresAt.trim())
            }
            true
        }.getOrDefault(false)
    }

    fun clearServerAuthSession(
        outletId: String? = null,
        role: String? = null,
        userId: String? = null,
    ): Boolean {
        val active = getActiveUserSession()
        val normalizedRole = role?.trim()?.uppercase()
            ?: active?.role
            ?: ""
        val normalizedUserId = userId?.trim()
            ?: active?.userId
            ?: ""
        val normalizedOutletId = outletId?.let(::resolveOutletId)
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                clearLegacyServerSessionSlots()
            }
            clearScopedServerSessionSlots(
                outletId = normalizedOutletId,
                role = normalizedRole,
                userId = normalizedUserId,
            )
            true
        }.getOrDefault(false)
    }

    fun getOrCreateDeviceId(): String {
        val existing = getOptionalValue(KEY_DEVICE_ID)
        if (existing != null) return existing
        val generated = IdGenerator.newId("dev_")
        upsert(KEY_DEVICE_ID, generated)
        return generated
    }

    fun resolveOutletId(outletId: String? = null): String {
        return normalizeOutletId(
            outletId
                ?.trim()
                ?.ifBlank { null }
                ?: getOptionalValue(KEY_OUTLET_ID),
        )
    }

    fun resolveCurrentCashierId(): String {
        return getActiveUserSession()?.userId
            ?: ensureDefaultCashierId(getDefaultCashierName())
    }

    fun resolveCurrentCashierName(): String {
        return getActiveUserSession()?.userName
            ?: getDefaultCashierName()
            ?.ifBlank { null }
            ?: "Cashier"
    }

    fun isLocalSetupComplete(): Boolean {
        return getValue(KEY_LOCAL_SETUP_COMPLETED).equals("true", ignoreCase = true)
    }

    fun markLocalSetupCompleted(completed: Boolean): Boolean {
        return upsert(KEY_LOCAL_SETUP_COMPLETED, completed.toString())
    }

    fun resetAllLocal(outletId: String = DEFAULT_OUTLET_ID): Boolean {
        val scopedOutletId = normalizeOutletId(outletId)
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                db.tokoQueries.deleteOrderItemsByOutlet(scopedOutletId)
                db.tokoQueries.deleteOrderHeadersByOutlet(scopedOutletId)
                db.tokoQueries.deleteTransaksiDetailsByOutlet(scopedOutletId)
                db.tokoQueries.deletePembayaranByOutlet(scopedOutletId)
                db.tokoQueries.deleteTransaksiByOutlet(scopedOutletId)
                db.tokoQueries.deleteProductModifierLinksByOutlet(scopedOutletId)
                db.tokoQueries.deleteModifierOptionsByOutlet(scopedOutletId)
                db.tokoQueries.deleteModifierGroupsByOutlet(scopedOutletId)
                db.tokoQueries.deleteItemsByOutlet(scopedOutletId)
                db.tokoQueries.deleteGroupsByOutlet(scopedOutletId)
                db.tokoQueries.deleteOutboxByOutlet(scopedOutletId)
                db.tokoQueries.deleteStockLedgerByOutlet(scopedOutletId)
                db.tokoQueries.deleteStockThresholdByOutlet(scopedOutletId)
                db.tokoQueries.deleteStockBalanceByOutlet(scopedOutletId)
                db.tokoQueries.deleteCashMovementsByOutlet(scopedOutletId)
                db.tokoQueries.deleteCashSessionsByOutlet(scopedOutletId)
                db.tokoQueries.deleteAllAppSettings()
            }
            true
        }.getOrDefault(false)
    }

    private fun ensureSettingsTable() {
        db.tokoQueries.createAppSettingsTable()
    }

    companion object {
        const val KEY_STORE_NAME = "store_name"
        const val KEY_STORE_ADDRESS = "store_address"
        const val KEY_STORE_LOGO = "store_logo_path"
        const val KEY_WATERMARK_LOGO = "watermark_logo_path"
        const val KEY_FOOTER_TEXT = "footer_text"
        const val KEY_SERVER_BASE_URL = "server_base_url"
        const val KEY_OUTLET_ID = "outlet_id"
        const val KEY_ALLOW_NEGATIVE_STOCK = "allow_negative_stock"
        const val KEY_AUTO_TAX_PERCENT = "auto_tax_percent"
        const val KEY_AUTO_SERVICE_PERCENT = "auto_service_percent"
        const val KEY_AUTO_ROUNDING = "auto_rounding"
        const val KEY_LOCAL_SETUP_COMPLETED = "local_setup_completed"
        const val KEY_SETUP_MODE = "setup_mode"
        const val KEY_OWNER_NAME = "owner_name"
        const val KEY_OWNER_PIN = "owner_pin"
        const val KEY_DEFAULT_CASHIER_ID = "default_cashier_id"
        const val KEY_DEFAULT_CASHIER_NAME = "default_cashier_name"
        const val KEY_DEFAULT_CASHIER_PIN = "default_cashier_pin"
        const val KEY_ACTIVE_USER_ROLE = "active_user_role"
        const val KEY_ACTIVE_USER_ID = "active_user_id"
        const val KEY_ACTIVE_USER_NAME = "active_user_name"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SERVER_SESSION_TOKEN = "server_session_token"
        const val KEY_SERVER_SESSION_ID = "server_session_id"
        const val KEY_SERVER_SESSION_ROLE = "server_session_role"
        const val KEY_SERVER_SESSION_USER_ID = "server_session_user_id"
        const val KEY_SERVER_SESSION_USER_NAME = "server_session_user_name"
        const val KEY_SERVER_SESSION_OUTLET_ID = "server_session_outlet_id"
        const val KEY_SERVER_SESSION_ISSUED_AT = "server_session_issued_at"
        const val KEY_SERVER_SESSION_EXPIRES_AT = "server_session_expires_at"
        const val KEY_SERVER_SESSION_TOKEN_PREFIX = "server_session_token."
        const val KEY_SERVER_SESSION_ID_PREFIX = "server_session_id."
        const val KEY_SERVER_SESSION_USER_NAME_PREFIX = "server_session_user_name."
        const val KEY_SERVER_SESSION_ISSUED_AT_PREFIX = "server_session_issued_at."
        const val KEY_SERVER_SESSION_EXPIRES_AT_PREFIX = "server_session_expires_at."
        const val DEFAULT_OUTLET_ID = "default"
        const val SETUP_MODE_LOCAL_FIRST = "LOCAL_FIRST"
        const val ROLE_OWNER = "OWNER"
        const val ROLE_CASHIER = "CASHIER"

        fun normalizeOutletId(value: String?): String {
            return value.orEmpty()
                .trim()
                .replace(Regex("\\s+"), "-")
                .ifBlank { DEFAULT_OUTLET_ID }
                .take(64)
        }

        private fun defaultConfig(): ReceiptConfig = ReceiptConfig(
            storeName = "SuCash",
            storeAddressOrPhone = "",
            headerLogoPath = "",
            watermarkLogoPath = "",
            footerText = "Thank you",
        )

        private fun buildDefaultCashierId(name: String?): String {
            val normalized = name
                ?.trim()
                ?.lowercase()
                ?.replace(Regex("[^a-z0-9]+"), "_")
                ?.trim('_')
                .orEmpty()
            return if (normalized.isNotBlank()) {
                "cashier_$normalized"
            } else {
                IdGenerator.newId("cashier_")
            }
        }

        private fun isValidPin(pin: String): Boolean {
            return pin.length in 4..6 && pin.all(Char::isDigit)
        }
    }

    private fun getStoredServerSessionToken(
        outletId: String,
        role: String,
        userId: String,
    ): String? {
        val scope = buildServerSessionScope(
            outletId = outletId,
            role = role,
            userId = userId,
        )
        val scopedToken = getOptionalValue(scope.tokenKey)
        if (scopedToken != null) return scopedToken

        // Fallback for pre-scope single-slot storage.
        val token = getOptionalValue(KEY_SERVER_SESSION_TOKEN) ?: return null
        val storedOutletId = resolveOutletId(getOptionalValue(KEY_SERVER_SESSION_OUTLET_ID))
        val storedRole = getOptionalValue(KEY_SERVER_SESSION_ROLE)?.uppercase() ?: return null
        val storedUserId = getOptionalValue(KEY_SERVER_SESSION_USER_ID) ?: return null
        if (storedOutletId != outletId) return null
        if (storedRole != role.uppercase()) return null
        if (storedUserId != userId) return null
        return token
    }

    private fun clearLegacyServerSessionSlots() {
        db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_TOKEN, "")
        db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_ID, "")
        db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_ROLE, "")
        db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_USER_ID, "")
        db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_USER_NAME, "")
        db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_OUTLET_ID, "")
        db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_ISSUED_AT, "")
        db.tokoQueries.upsertAppSetting(KEY_SERVER_SESSION_EXPIRES_AT, "")
    }

    private fun clearScopedServerSessionSlots(
        outletId: String?,
        role: String,
        userId: String,
    ) {
        if (role.isBlank() || userId.isBlank()) return
        val allRows = db.tokoQueries.selectAllAppSettings().executeAsList()
        val roleToken = sanitizeSettingTokenSegment(role)
        val userToken = sanitizeSettingTokenSegment(userId)
        val outletToken = outletId?.let(::sanitizeSettingTokenSegment)
        val prefixes = listOf(
            KEY_SERVER_SESSION_TOKEN_PREFIX,
            KEY_SERVER_SESSION_ID_PREFIX,
            KEY_SERVER_SESSION_USER_NAME_PREFIX,
            KEY_SERVER_SESSION_ISSUED_AT_PREFIX,
            KEY_SERVER_SESSION_EXPIRES_AT_PREFIX,
        )
        allRows.forEach { row ->
            val key = row.setting_key
            val matchedPrefix = prefixes.firstOrNull { key.startsWith(it) } ?: return@forEach
            val suffix = key.removePrefix(matchedPrefix)
            val segments = suffix.split('.')
            if (segments.size != 3) return@forEach
            val matchOutlet = outletToken == null || segments[0] == outletToken
            val matchRole = segments[1] == roleToken
            val matchUser = segments[2] == userToken
            if (matchOutlet && matchRole && matchUser) {
                db.tokoQueries.upsertAppSetting(key, "")
            }
        }
    }

    private data class ServerSessionScope(
        val tokenKey: String,
        val sessionIdKey: String,
        val userNameKey: String,
        val issuedAtKey: String,
        val expiresAtKey: String,
    )

    private fun buildServerSessionScope(
        outletId: String,
        role: String,
        userId: String,
    ): ServerSessionScope {
        val outletToken = sanitizeSettingTokenSegment(outletId)
        val roleToken = sanitizeSettingTokenSegment(role)
        val userToken = sanitizeSettingTokenSegment(userId)
        val suffix = "$outletToken.$roleToken.$userToken"
        return ServerSessionScope(
            tokenKey = "$KEY_SERVER_SESSION_TOKEN_PREFIX$suffix",
            sessionIdKey = "$KEY_SERVER_SESSION_ID_PREFIX$suffix",
            userNameKey = "$KEY_SERVER_SESSION_USER_NAME_PREFIX$suffix",
            issuedAtKey = "$KEY_SERVER_SESSION_ISSUED_AT_PREFIX$suffix",
            expiresAtKey = "$KEY_SERVER_SESSION_EXPIRES_AT_PREFIX$suffix",
        )
    }

    private fun sanitizeSettingTokenSegment(value: String): String {
        return value.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "na" }
            .take(64)
    }
}
