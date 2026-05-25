package com.durrr.first.features.recap.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.durrr.first.data.repo.RecapRepository
import com.durrr.first.data.repo.RecapSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.CashFlowRepository
import com.durrr.first.core.utils.formatNumber
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.domain.model.CashFlowSummary
import com.durrr.first.domain.model.ProductRecap
import com.durrr.first.domain.model.RecapDashboard
import com.durrr.first.domain.model.RecapMetrics
import com.durrr.first.domain.model.RecapRange
import com.durrr.first.ui.design.AppBarChart
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppErrorBanner
import com.durrr.first.ui.design.AppInfoLine
import com.durrr.first.ui.design.AppLoading
import com.durrr.first.ui.design.AppStatusPill
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.design.Dimens
import com.durrr.first.ui.notification.AppNotificationLevel

private val RecapBlue = Color(0xFF273BBF)
private val RecapBorder = Color(0xFFD5D9E2)
@Composable
fun RecapScreen(
    recapRepository: RecapRepository,
    cashFlowRepository: CashFlowRepository,
    recapSyncRepository: RecapSyncRepository? = null,
    settingsRepository: SettingsRepository,
    todayDate: () -> String,
    onNotify: (title: String, message: String, level: AppNotificationLevel) -> Unit = { _, _, _ -> },
    onOpenCashFlow: () -> Unit = {},
    // onOpenStock: () -> Unit = {},
    onOpenCashClosing: () -> Unit = {},
    onOpenTransactionHistory: () -> Unit = {},
) {
    var selectedRange by remember { mutableStateOf(RecapRange.ALL) }
    var dashboard by remember { mutableStateOf<RecapDashboard?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastSyncWarning by remember { mutableStateOf<String?>(null) }
    var dataSource by remember { mutableStateOf("Local") }
    var cashFlowSummary by remember { mutableStateOf<CashFlowSummary?>(null) }

    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    suspend fun load() {
        loading = true
        error = null
        val anchorDate = todayDate()
        val outletId = currentOutletId()
        val baseUrl = settingsRepository
            .getValue(SettingsRepository.KEY_SERVER_BASE_URL)
            .trim()
        val syncRepository = recapSyncRepository
        val remoteCapable = syncRepository != null && baseUrl.isNotBlank()

        if (remoteCapable) {
            runCatching {
                syncRepository.getDashboardSafe(
                    baseUrl = baseUrl,
                    range = selectedRange,
                    anchorDate = anchorDate,
                    outletId = outletId,
                )
            }.onSuccess {
                dashboard = it.dashboard
                dataSource = if (it.usedFallback) {
                    "Local"
                } else {
                    "Server + Local (Arus Kas tetap dari local session)"
                }
                if (!it.warning.isNullOrBlank()) {
                    val simplified = simplifyRecapSyncMessage(it.warning)
                    if (lastSyncWarning != simplified) {
                        onNotify("Recap", simplified, AppNotificationLevel.WARNING)
                        lastSyncWarning = simplified
                    }
                } else {
                    lastSyncWarning = null
                }
                error = null
            }.onFailure {
                runCatching {
                    recapRepository.getRecap(selectedRange, anchorDate, outletId)
                }.onSuccess { local ->
                    dashboard = local
                    dataSource = "Local"
                    val simplified = "Recap server tidak tersedia. Menampilkan data lokal."
                    if (lastSyncWarning != simplified) {
                        onNotify("Recap", simplified, AppNotificationLevel.WARNING)
                        lastSyncWarning = simplified
                    }
                    error = null
                }.onFailure { localError ->
                    val simplified = simplifyRecapLoadError(localError.message)
                    onNotify("Recap", simplified, AppNotificationLevel.ERROR)
                    error = simplified
                    dashboard = null
                }
            }
        } else {
            runCatching {
                recapRepository.getRecap(selectedRange, anchorDate, outletId)
            }.onSuccess {
                dashboard = it
                dataSource = "Local"
                error = null
            }.onFailure {
                val simplified = simplifyRecapLoadError(it.message)
                onNotify("Recap", simplified, AppNotificationLevel.ERROR)
                error = simplified
                dashboard = null
            }
        }
        runCatching {
            cashFlowRepository.getSummary(selectedRange, anchorDate, outletId)
        }.onSuccess {
            cashFlowSummary = it
        }.onFailure {
            cashFlowSummary = null
        }
        loading = false
    }

    LaunchedEffect(selectedRange) {
        load()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
    ) {
        val wide = maxWidth >= 1100.dp
        val snapshot = dashboard

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RangeSwitcher(selected = selectedRange, onSelect = { selectedRange = it })
                            Text(
                                text = "Data source: $dataSource",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            UtilityActionButton("Arus Kas", onOpenCashFlow)
                            // UtilityActionButton("Stok", onOpenStock)
                            UtilityActionButton("Kas Closing", onOpenCashClosing)
                            UtilityActionButton("Riwayat Trx", onOpenTransactionHistory)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RangeSwitcher(selected = selectedRange, onSelect = { selectedRange = it })
                        Text(
                            text = "Data source: $dataSource",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B7280),
                        )
                        UtilityActionButton("Arus Kas", onOpenCashFlow)
                        // UtilityActionButton("Stok", onOpenStock)
                        UtilityActionButton("Kas Closing", onOpenCashClosing)
                        UtilityActionButton("Riwayat Trx", onOpenTransactionHistory)
                    }
                }
            }
            if (error != null) {
                item { AppErrorBanner(message = error.orEmpty()) }
            }
            if (loading) {
                item { AppLoading("Loading recap...") }
            }
            if (!loading && snapshot != null) {
                item { MetricsGrid(metrics = snapshot.metrics, wide = wide) }
                cashFlowSummary?.let { flow ->
                    item {
                        AppCard {
                            Text(
                                "Ringkasan Arus Kas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Arus kas memakai cash session + pembayaran lokal device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280),
                            )
                            AppInfoLine("Pemasukan", formatRupiah(flow.totalCashIn))
                            AppInfoLine("Refund/Batal", formatRupiah(flow.totalRefundOrCancelled))
                            AppInfoLine("Penjualan Tunai Bersih", formatRupiah(flow.cashSalesNet))
                            AppInfoLine("Posisi Kas Estimasi", formatRupiah(flow.estimatedCashPosition))
                        }
                    }
                }
                item {
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            ChartPanel(
                                title = "Grafik Perbandingan",
                                modifier = Modifier.weight(2.2f),
                            ) { AppBarChart(points = snapshot.chart, modifier = Modifier.fillMaxWidth()) }
                            RecommendationPanel(
                                bestProduct = snapshot.bestProduct,
                                lowestProduct = snapshot.lowestProduct,
                                productInsight = snapshot.productInsight,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            ChartPanel(title = "Grafik Perbandingan") {
                                AppBarChart(points = snapshot.chart, modifier = Modifier.fillMaxWidth())
                            }
                            RecommendationPanel(
                                bestProduct = snapshot.bestProduct,
                                lowestProduct = snapshot.lowestProduct,
                                productInsight = snapshot.productInsight,
                            )
                        }
                    }
                }
                item {
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            ProductListSummary(
                                title = "Items Performance",
                                products = snapshot.topProducts,
                                modifier = Modifier.weight(1.35f),
                            )
                            PaymentMethodSummary(
                                methods = snapshot.paymentBreakdown,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            ProductListSummary(title = "Items Performance", products = snapshot.topProducts)
                            PaymentMethodSummary(methods = snapshot.paymentBreakdown)
                        }
                    }
                }
                item {
                    ProductListSummary(
                        title = "Slow Movers",
                        products = snapshot.slowProducts,
                    )
                }
            }
            if (!loading && snapshot == null && error == null) {
                item {
                    AppEmptyState(
                        title = "No recap data",
                        message = "No transaction data for selected period.",
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeSwitcher(
    selected: RecapRange,
    onSelect: (RecapRange) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        listOf(
            RecapRange.TODAY to "Today",
            RecapRange.WEEK to "This Week",
            RecapRange.MONTH to "This Month",
            RecapRange.ALL to "All Time",
        ).forEach { (range, label) ->
            if (selected == range) {
                Button(
                    onClick = { onSelect(range) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = RecapBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RecapBlue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) { Text(label) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(range) },
                    border = androidx.compose.foundation.BorderStroke(1.dp, RecapBorder),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun MetricsGrid(metrics: RecapMetrics, wide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                RecapMetricCard("Total Pendapatan", formatRupiah(metrics.totalSales), Modifier.weight(1f))
                RecapMetricCard("Total Transaksi", formatNumber(metrics.totalTransactions.toLong()), Modifier.weight(1f))
                RecapMetricCard("Rata-rata / Transaksi", formatRupiah(metrics.averagePerTransaction), Modifier.weight(1f))
            }
        } else {
            RecapMetricCard("Total Pendapatan", formatRupiah(metrics.totalSales))
            RecapMetricCard("Total Transaksi", formatNumber(metrics.totalTransactions.toLong()))
            RecapMetricCard("Rata-rata / Transaksi", formatRupiah(metrics.averagePerTransaction))
        }
        val netSales = (metrics.totalSales - metrics.totalDiscounts).coerceAtLeast(0L)
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                RecapMetricCard("Diskon", formatRupiah(metrics.totalDiscounts), Modifier.weight(1f))
                RecapMetricCard("Penjualan Bersih", formatRupiah(netSales), Modifier.weight(1f))
            }
        } else {
            RecapMetricCard("Diskon", formatRupiah(metrics.totalDiscounts))
            RecapMetricCard("Penjualan Bersih", formatRupiah(netSales))
        }
    }
}

@Composable
private fun RecapMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Text(
            title,
            color = Color(0xFF6B7280),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = RecapBlue,
        )
    }
}

@Composable
private fun UtilityActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF555555)),
        border = androidx.compose.foundation.BorderStroke(1.dp, RecapBorder),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) { Text(label) }
}

@Composable
private fun ChartPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun RecommendationPanel(
    bestProduct: ProductRecap?,
    lowestProduct: ProductRecap?,
    productInsight: String,
    modifier: Modifier = Modifier,
) {
    val recommendations = buildPromoRecommendations(bestProduct, lowestProduct)
    AppCard(modifier = modifier) {
        Text("Rekomendasi Bundle & Promo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (recommendations.isEmpty()) {
            Text(
                "Belum cukup data untuk rekomendasi promo. Tambah transaksi dulu ya.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recommendations.forEach { recommendation ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.md),
                        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                    ) {
                        AppStatusPill(label = recommendation.badge)
                        Text(recommendation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(recommendation.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        RecommendationCallout(
            productInsight = productInsight,
            hasSignal = bestProduct != null || lowestProduct != null,
        )
    }
}

private data class PromoRecommendation(
    val badge: String,
    val title: String,
    val detail: String,
)

private fun buildPromoRecommendations(
    bestProduct: ProductRecap?,
    lowestProduct: ProductRecap?,
): List<PromoRecommendation> {
    val recommendations = mutableListOf<PromoRecommendation>()

    if (bestProduct != null && lowestProduct != null) {
        recommendations += PromoRecommendation(
            badge = "Bundle",
            title = "Bundle ${bestProduct.itemName} + ${lowestProduct.itemName}",
            detail = "Paketkan item terlaris dengan slow mover untuk dorong penjualan item yang lambat.",
        )
    }
    if (lowestProduct != null) {
        recommendations += PromoRecommendation(
            badge = "Promo",
            title = "Promo terbatas untuk ${lowestProduct.itemName}",
            detail = "Jadikan menu sementara 7 hari dengan diskon kecil atau bonus topping agar qty naik.",
        )
    }
    if (bestProduct != null) {
        recommendations += PromoRecommendation(
            badge = "Upsell",
            title = "Jadikan ${bestProduct.itemName} sebagai add-on utama",
            detail = "Taruh di urutan atas kasir/QR order untuk menjaga momentum item paling laku.",
        )
    }

    return recommendations.distinctBy { it.title }.take(3)
}

private fun simplifyRecapSyncMessage(raw: String?): String {
    val message = raw.orEmpty()
    val lower = message.lowercase()
    return when {
        "timeout" in lower -> "Koneksi ke server timeout. Menampilkan data lokal."
        "failed to connect" in lower || "connection refused" in lower || "unreachable" in lower ->
            "Tidak bisa terhubung ke server. Menampilkan data lokal."
        "unexpected end of stream" in lower -> "Koneksi terputus saat sync recap. Menampilkan data lokal."
        "server recap response error" in lower || "http" in lower || "api" in lower ->
            "Respons server recap bermasalah. Menampilkan data lokal."
        message.isBlank() -> "Sinkronisasi recap gagal. Menampilkan data lokal."
        else -> "Sinkronisasi recap gagal. Menampilkan data lokal."
    }
}

private fun simplifyRecapLoadError(raw: String?): String {
    val lower = raw.orEmpty().lowercase()
    return when {
        "timeout" in lower -> "Gagal memuat recap: koneksi timeout."
        "failed to connect" in lower || "connection refused" in lower || "unreachable" in lower ->
            "Gagal memuat recap: tidak bisa terhubung ke data."
        "no such table" in lower -> "Gagal memuat recap: struktur data belum siap."
        else -> "Gagal memuat recap. Coba lagi."
    }
}

@Composable
private fun RecommendationCallout(
    productInsight: String,
    hasSignal: Boolean,
) {
    Surface(
        color = if (hasSignal) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        },
        contentColor = if (hasSignal) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        ) {
            AppStatusPill(
                label = if (hasSignal) "Suggested Action" else "Waiting for More Data",
                containerColor = if (hasSignal) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (hasSignal) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
            Text(
                text = productInsight,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Standar rekomendasi: minimal 3 transaksi, top seller qty >= 5, needs attention qty <= 2.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProductListSummary(
    title: String,
    products: List<ProductRecap>,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (products.isEmpty()) {
            Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        products.forEach { product ->
            AppInfoLine(
                label = "${product.itemName} • Qty ${formatNumber(product.qty)}",
                value = formatRupiah(product.revenue),
            )
        }
    }
}

@Composable
private fun PaymentMethodSummary(
    methods: List<com.durrr.first.domain.model.PaymentMethodRecap>,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Text("Payment Breakdown", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (methods.isEmpty()) {
            Text("No payments yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        methods.forEach { method ->
            AppInfoLine(label = method.methodName, value = formatRupiah(method.total))
        }
    }
}

@Preview
@Composable
fun RecapScreenPreview() {
    var selectedRange by remember { mutableStateOf(RecapRange.TODAY) }
    val sample = RecapDashboard(
        range = selectedRange,
        metrics = RecapMetrics(
            totalSales = 1_850_000,
            totalTransactions = 74,
            averagePerTransaction = 25_000,
            totalDiscounts = 120_000,
        ),
        chart = listOf(
            com.durrr.first.domain.model.RecapChartPoint("Mon", 240_000),
            com.durrr.first.domain.model.RecapChartPoint("Tue", 310_000),
            com.durrr.first.domain.model.RecapChartPoint("Wed", 260_000),
            com.durrr.first.domain.model.RecapChartPoint("Thu", 280_000),
            com.durrr.first.domain.model.RecapChartPoint("Fri", 330_000),
        ),
        bestProduct = ProductRecap("item-1", "Es Teh", 40, 320_000),
        lowestProduct = ProductRecap("item-9", "Brownies", 4, 120_000),
        productInsight = "Bundle Brownies with Es Teh to lift low-performing sales.",
        paymentBreakdown = listOf(
            com.durrr.first.domain.model.PaymentMethodRecap("cash", "Cash", 1_000_000),
            com.durrr.first.domain.model.PaymentMethodRecap("qris", "QRIS", 850_000),
        ),
        topProducts = listOf(
            ProductRecap("item-1", "Es Teh", 40, 320_000),
            ProductRecap("item-2", "Nasi Goreng", 24, 600_000),
        ),
        slowProducts = listOf(
            ProductRecap("item-9", "Brownies", 4, 120_000),
            ProductRecap("item-10", "Kentang", 5, 95_000),
        ),
    )

    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RangeSwitcher(selected = selectedRange, onSelect = { selectedRange = it })
            Text(
                text = "Data source: Preview",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
            )
            MetricsGrid(metrics = sample.metrics, wide = false)
            RecommendationPanel(
                bestProduct = sample.bestProduct,
                lowestProduct = sample.lowestProduct,
                productInsight = sample.productInsight,
            )
            ProductListSummary(title = "Items Performance", products = sample.topProducts)
            PaymentMethodSummary(methods = sample.paymentBreakdown)
        }
    }
}
