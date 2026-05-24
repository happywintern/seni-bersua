package com.durrr.first.data.repo

import com.durrr.first.domain.model.PaymentMethodRecap
import com.durrr.first.domain.model.ProductRecap
import com.durrr.first.domain.model.RecapChartPoint
import com.durrr.first.domain.model.RecapDashboard
import com.durrr.first.domain.model.RecapMetrics
import com.durrr.first.domain.model.RecapProductRecommendationPolicy
import com.durrr.first.domain.model.RecapRange
import com.durrr.first.network.ServerApiClient
import com.durrr.first.network.ServerApiException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class RecapSyncRepository(
    private val recapRepository: RecapRepository,
    private val apiClient: ServerApiClient,
    private val settingsRepository: SettingsRepository? = null,
) {
    data class DashboardLoadResult(
        val dashboard: RecapDashboard,
        val usedFallback: Boolean,
        val warning: String? = null,
    )

    class RecapSyncException(
        val kind: Kind,
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause) {
        enum class Kind {
            LOCAL,
            NETWORK,
            SERVER,
            UNKNOWN,
        }
    }

    suspend fun getDashboard(
        baseUrl: String,
        range: RecapRange,
        anchorDate: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): RecapDashboard {
        val bearerToken = resolveBearerTokenOrNull(
            outletId = outletId,
            requireActiveSession = true,
        )
        val local = runCatching {
            recapRepository.getRecap(range, anchorDate, outletId)
        }.getOrElse { cause ->
            throw toRecapSyncException(cause, source = "Local recap load")
        }
        val remote = runCatching {
            withNetworkRetry {
                apiClient.getRecapSummary(
                    baseUrl = baseUrl,
                    range = range,
                    date = anchorDate,
                    outletId = outletId,
                    bearerToken = bearerToken,
                )
            }
        }.getOrElse { cause ->
            throw toRecapSyncException(cause, source = "Server recap sync")
        }
        return mergeDashboard(
            local = local,
            range = range,
            anchorDate = anchorDate,
            remote = remote,
        )
    }

    suspend fun getDashboardSafe(
        baseUrl: String,
        range: RecapRange,
        anchorDate: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): DashboardLoadResult {
        val bearerToken = resolveBearerTokenOrNull(
            outletId = outletId,
            requireActiveSession = false,
        )
        val local = runCatching {
            recapRepository.getRecap(range, anchorDate, outletId)
        }.getOrElse { cause ->
            throw toRecapSyncException(cause, source = "Local recap load")
        }

        val remoteResult = runCatching {
            withNetworkRetry {
                apiClient.getRecapSummary(
                    baseUrl = baseUrl,
                    range = range,
                    date = anchorDate,
                    outletId = outletId,
                    bearerToken = bearerToken,
                )
            }
        }
        val remote = remoteResult.getOrNull()
        if (remote == null) {
            val reason = remoteResult.exceptionOrNull()?.let { error ->
                toRecapSyncException(error, source = "Server recap sync").message
            } ?: "Server recap unavailable."
            return DashboardLoadResult(
                dashboard = local,
                usedFallback = true,
                warning = "$reason Showing local data.",
            )
        }

        return DashboardLoadResult(
            dashboard = mergeDashboard(
                local = local,
                range = range,
                anchorDate = anchorDate,
                remote = remote,
            ),
            usedFallback = false,
            warning = null,
        )
    }

    private fun mergeDashboard(
        local: RecapDashboard,
        range: RecapRange,
        anchorDate: String,
        remote: com.durrr.first.network.dto.RecapSummaryResponse,
    ): RecapDashboard {
        val remoteMetrics = RecapMetrics(
            totalSales = remote.grossTotal,
            totalTransactions = remote.transaksiCount,
            averagePerTransaction = remote.averageTicket,
            totalDiscounts = remote.totalDiscount,
        )
        val preferLocalMetrics = local.metrics.totalTransactions > remoteMetrics.totalTransactions
        val mergedMetrics = if (preferLocalMetrics) local.metrics else remoteMetrics

        val remoteTop = remote.topItems.map {
            ProductRecap(
                itemId = it.itemId,
                itemName = it.itemName,
                qty = it.qtySold,
                revenue = it.revenue,
            )
        }
        val remoteSlow = remote.slowItems.map {
            ProductRecap(
                itemId = it.itemId,
                itemName = it.itemName,
                qty = it.qtySold,
                revenue = it.revenue,
            )
        }
        val mergedChart = if (
            local.chart.isNotEmpty() &&
            local.metrics.totalTransactions >= remote.transaksiCount
        ) {
            local.chart
        } else {
            listOf(
                RecapChartPoint(
                    label = when (range) {
                        RecapRange.TODAY -> anchorDate
                        RecapRange.WEEK -> "Last 7 days"
                        RecapRange.MONTH -> anchorDate.take(7)
                        RecapRange.ALL -> "All Time"
                    },
                    total = remote.grossTotal,
                )
            )
        }

        val mergedTopProducts = if (remoteTop.isNotEmpty()) remoteTop else local.topProducts
        val mergedSlowProducts = if (remoteSlow.isNotEmpty()) remoteSlow else local.slowProducts
        val bestProduct = RecapProductRecommendationPolicy.selectTopSeller(
            mergedMetrics.totalTransactions,
            mergedTopProducts,
        )
        val lowestProduct = RecapProductRecommendationPolicy.selectNeedsAttention(
            mergedMetrics.totalTransactions,
            mergedSlowProducts,
        )

        return RecapDashboard(
            range = range,
            metrics = mergedMetrics,
            chart = mergedChart,
            bestProduct = bestProduct,
            lowestProduct = lowestProduct,
            productInsight = RecapProductRecommendationPolicy.buildInsight(
                totalTransactions = mergedMetrics.totalTransactions,
                topSeller = bestProduct,
                needsAttention = lowestProduct,
            ),
            paymentBreakdown = remote.paymentBreakdown.map {
                PaymentMethodRecap(
                    methodId = it.methodId,
                    methodName = it.methodName,
                    total = it.total,
                )
            },
            topProducts = mergedTopProducts,
            slowProducts = mergedSlowProducts,
        )
    }

    private suspend fun <T> withNetworkRetry(
        maxAttempts: Int = 3,
        initialBackoffMs: Long = 400L,
        block: suspend () -> T,
    ): T {
        var attempt = 1
        var backoff = initialBackoffMs
        var lastError: Throwable? = null
        while (attempt <= maxAttempts) {
            try {
                return block()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (!isRetryableNetworkError(error) || attempt == maxAttempts) {
                    throw error
                }
                lastError = error
                delay(backoff)
                backoff *= 2
                attempt += 1
            }
        }
        throw lastError ?: IllegalStateException("Recap network retry failed without error detail")
    }

    private fun isRetryableNetworkError(error: Throwable): Boolean {
        return error is IOException ||
            error is SocketTimeoutException ||
            error is ConnectTimeoutException ||
            error is HttpRequestTimeoutException ||
            (error.message?.contains("unexpected end of stream", ignoreCase = true) == true)
    }

    private fun toRecapSyncException(cause: Throwable, source: String): RecapSyncException {
        if (cause is RecapSyncException) return cause
        val messageText = cause.message.orEmpty()
        val isNetwork = cause is IOException ||
            cause is SocketTimeoutException ||
            cause is ConnectTimeoutException ||
            cause is HttpRequestTimeoutException ||
            messageText.contains("unexpected end of stream", ignoreCase = true) ||
            messageText.contains("timeout", ignoreCase = true) ||
            messageText.contains("failed to connect", ignoreCase = true)
        val isServer = !isNetwork && (
            cause is ServerApiException ||
            messageText.contains("server", ignoreCase = true) ||
                messageText.contains("http", ignoreCase = true) ||
                messageText.contains("api", ignoreCase = true)
            )

        val kind = when {
            source.startsWith("Local", ignoreCase = true) -> RecapSyncException.Kind.LOCAL
            isNetwork -> RecapSyncException.Kind.NETWORK
            isServer -> RecapSyncException.Kind.SERVER
            else -> RecapSyncException.Kind.UNKNOWN
        }

        val reason = when (kind) {
            RecapSyncException.Kind.LOCAL -> "Local recap load failed"
            RecapSyncException.Kind.NETWORK -> "Network issue while syncing recap"
            RecapSyncException.Kind.SERVER -> "Server recap response error"
            RecapSyncException.Kind.UNKNOWN -> "$source failed"
        }
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: "unknown error"
        return RecapSyncException(
            kind = kind,
            message = "$reason: $detail",
            cause = cause,
        )
    }

    private fun resolveBearerTokenOrNull(
        outletId: String,
        requireActiveSession: Boolean,
    ): String? {
        val token = settingsRepository?.getActiveUserServerApiBearerToken(outletId)
            ?.trim()
            .orEmpty()
        if (settingsRepository != null && requireActiveSession && token.isBlank()) {
            error("Session server belum aktif. Pairing server dulu lalu login ulang.")
        }
        return token.ifBlank { null }
    }
}
