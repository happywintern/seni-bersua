package com.durrr.first.data.repo

import com.durrr.first.network.ServerApiClient
import com.durrr.first.network.ServerApiException
import com.durrr.first.network.dto.ServerAuthPairingCodeRedeemRequest
import com.durrr.first.network.dto.ServerAuthSessionDto
import com.durrr.first.network.dto.ServerAuthSessionLoginRequest
import com.durrr.first.network.dto.ServerAuthSessionRefreshRequest

class ServerAuthRepository(
    private val apiClient: ServerApiClient,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun bootstrapSessionForActiveUser(
        baseUrl: String,
        outletId: String = settingsRepository.resolveOutletId(),
        pairingCode: String? = null,
    ): ServerAuthSessionDto? {
        val active = settingsRepository.getActiveUserSession() ?: return null
        val scopedOutletId = settingsRepository.resolveOutletId(outletId)
        val sessionToken = settingsRepository
            .getActiveUserServerApiBearerToken(scopedOutletId)
            ?.trim()
            .orEmpty()

        suspend fun redeemViaPairing(): ServerAuthSessionDto {
            val normalizedPairingCode = pairingCode?.trim().orEmpty()
            check(normalizedPairingCode.isNotBlank()) {
                "Pairing code wajib dari server admin. Mobile hanya bisa redeem."
            }
            return try {
                apiClient.redeemPairingCode(
                    baseUrl = baseUrl,
                    request = ServerAuthPairingCodeRedeemRequest(
                        pairingCode = normalizedPairingCode,
                        role = active.role,
                        userId = active.userId,
                        userName = active.userName,
                        outletId = scopedOutletId,
                        deviceId = settingsRepository.getOrCreateDeviceId(),
                    ),
                )
            } catch (error: ServerApiException) {
                if (error.statusCode == 404) {
                    error(
                        "Endpoint pairing tidak ditemukan di server. Pastikan server terbaru berjalan di $baseUrl (route /api/auth/pairing/redeem)."
                    )
                }
                throw error
            }
        }


        val response = if (sessionToken.isNotBlank()) {
            runCatching {
                apiClient.refreshServerSession(
                    baseUrl = baseUrl,
                    request = ServerAuthSessionRefreshRequest(
                        outletId = scopedOutletId,
                        deviceId = settingsRepository.getOrCreateDeviceId(),
                    ),
                    bearerToken = sessionToken,
                )
            }.getOrElse {
                settingsRepository.clearServerAuthSession(outletId = scopedOutletId)
                redeemViaPairing()
            }
        } else {
            redeemViaPairing()
        }
        settingsRepository.saveServerAuthSession(response)
        return response
    }

    suspend fun bootstrapSessionWithLegacyBearerForActiveUser(
        baseUrl: String,
        outletId: String = settingsRepository.resolveOutletId(),
        bootstrapBearerToken: String,
    ): ServerAuthSessionDto? {
        val active = settingsRepository.getActiveUserSession() ?: return null
        val scopedOutletId = settingsRepository.resolveOutletId(outletId)
        val response = apiClient.loginServerSession(
            baseUrl = baseUrl,
            request = ServerAuthSessionLoginRequest(
                role = active.role,
                userId = active.userId,
                userName = active.userName,
                outletId = scopedOutletId,
                deviceId = settingsRepository.getOrCreateDeviceId(),
            ),
            bootstrapBearerToken = bootstrapBearerToken,
        )
        settingsRepository.saveServerAuthSession(response)
        return response
    }

    suspend fun refreshActiveUserSession(
        baseUrl: String,
        outletId: String = settingsRepository.resolveOutletId(),
    ): ServerAuthSessionDto? {
        val scopedOutletId = settingsRepository.resolveOutletId(outletId)
        val bearerToken = settingsRepository.getActiveUserServerApiBearerToken(scopedOutletId)
            ?.trim()
            .orEmpty()
        if (bearerToken.isBlank()) return null
        val response = apiClient.refreshServerSession(
            baseUrl = baseUrl,
            request = ServerAuthSessionRefreshRequest(
                outletId = scopedOutletId,
                deviceId = settingsRepository.getOrCreateDeviceId(),
            ),
            bearerToken = bearerToken,
        )
        settingsRepository.saveServerAuthSession(response)
        return response
    }

    suspend fun logoutActiveUserSession(baseUrl: String) {
        val scopedOutletId = settingsRepository.resolveOutletId()
        val bearerToken = settingsRepository.getActiveUserServerApiBearerToken(scopedOutletId)
            ?.trim()
            .orEmpty()
        if (bearerToken.isNotBlank()) {
            runCatching {
                apiClient.logoutServerSession(
                    baseUrl = baseUrl,
                    bearerToken = bearerToken,
                )
            }
        }
        settingsRepository.clearServerAuthSession(outletId = scopedOutletId)
    }
}
