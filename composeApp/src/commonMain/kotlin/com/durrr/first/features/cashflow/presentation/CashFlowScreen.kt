package com.durrr.first.features.cashflow.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.durrr.first.core.utils.formatReadableDateTime
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.data.repo.CashFlowRepository
import com.durrr.first.data.repo.CashSessionRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.CashFlowSummary
import com.durrr.first.domain.model.CashSessionSummary
import com.durrr.first.domain.model.RecapRange
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppErrorBanner
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

@Composable
fun CashFlowScreen(
    repository: CashFlowRepository,
    cashSessionRepository: CashSessionRepository,
    settingsRepository: SettingsRepository,
    todayDate: () -> String,
    nowIso: () -> String,
    onOpenDashboard: () -> Unit = {},
) {
    var selectedRange by remember { mutableStateOf(RecapRange.TODAY) }
    var summary by remember { mutableStateOf<CashFlowSummary?>(null) }
    var activeSessionSummary by remember { mutableStateOf<CashSessionSummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val defaultCashier = remember { settingsRepository.resolveCurrentCashierName() }
    var userIdText by remember { mutableStateOf(defaultCashier) }
    var openingCashText by remember { mutableStateOf("0") }
    var moveAmountText by remember { mutableStateOf("0") }
    var moveNoteText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    fun load() {
        loading = true
        runCatching {
            val outletId = currentOutletId()
            val anchorDate = todayDate()
            summary = repository.getSummary(selectedRange, anchorDate, outletId)
            val activeSession = cashSessionRepository.getActiveSession(outletId)
            activeSessionSummary = activeSession?.let {
                cashSessionRepository.getSessionSummary(it.sessionId)
            }
            message = null
        }.onFailure {
            summary = null
            activeSessionSummary = null
            message = it.message ?: "Failed to load cash flow data."
        }
        loading = false
    }

    LaunchedEffect(selectedRange) {
        load()
    }

    LazyColumn(
        modifier = Modifier.padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader("Arus Kas", "Flow kas harian + input saldo awal kasir")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                RangeTab("Hari Ini", selectedRange == RecapRange.TODAY) { selectedRange = RecapRange.TODAY }
                RangeTab("Minggu", selectedRange == RecapRange.WEEK) { selectedRange = RecapRange.WEEK }
                RangeTab("Bulan", selectedRange == RecapRange.MONTH) { selectedRange = RecapRange.MONTH }
                RangeTab("Semua", selectedRange == RecapRange.ALL) { selectedRange = RecapRange.ALL }
            }
        }

        if (!message.isNullOrBlank()) {
            item { AppErrorBanner(message = message.orEmpty()) }
        }

        val active = activeSessionSummary
        if (active == null) {
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Buka Shift Kasir", "Isi saldo awal kas")
                        OutlinedTextField(
                            value = userIdText,
                            onValueChange = { userIdText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nama Kasir") },
                        )
                        OutlinedTextField(
                            value = openingCashText,
                            onValueChange = { value ->
                                openingCashText = value.filter { it.isDigit() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Saldo Awal Kas") },
                            placeholder = { Text("Contoh: 500000") },
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val openingCash = openingCashText.toLongOrNull() ?: error("Saldo awal tidak valid")
                                        cashSessionRepository.openSession(
                                            outletId = currentOutletId(),
                                            openingCash = openingCash,
                                            userId = userIdText.ifBlank { "cashier" },
                                            openedAt = nowIso(),
                                        )
                                        "Shift kasir dibuka."
                                    }.onSuccess {
                                        message = it
                                        load()
                                    }.onFailure {
                                        message = it.message ?: "Gagal buka shift."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Buka Shift")
                        }
                    }
                }
            }
        } else {
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Shift Aktif")
                        Text("Kasir: ${active.session.openedBy}")
                        Text("Buka: ${active.session.openedAt}")
                        Text("Saldo Awal: ${formatRupiah(active.session.openingCash)}")
                        Text("Cash Sales: ${formatRupiah(active.cashSales)}")
                        Text("Cash In: ${formatRupiah(active.cashIn)}")
                        Text("Cash Out: ${formatRupiah(active.cashOut)}")
                        Text(
                            "Saldo Kas Saat Ini: ${formatRupiah(active.expectedCashNow)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Input Kas Masuk / Keluar")
                        OutlinedTextField(
                            value = moveAmountText,
                            onValueChange = { value ->
                                moveAmountText = value.filter { it.isDigit() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nominal") },
                            placeholder = { Text("Contoh: 100000") },
                        )
                        OutlinedTextField(
                            value = moveNoteText,
                            onValueChange = { moveNoteText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Keterangan") },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            val amount = moveAmountText.toLongOrNull() ?: error("Nominal tidak valid")
                                            if (amount <= 0L) error("Nominal harus lebih dari 0")
                                            cashSessionRepository.addCashIn(
                                                sessionId = active.session.sessionId,
                                                amount = amount,
                                                note = moveNoteText,
                                                userId = userIdText.ifBlank { "cashier" },
                                                createdAt = nowIso(),
                                            )
                                            "Kas masuk tercatat."
                                        }.onSuccess {
                                            moveAmountText = "0"
                                            moveNoteText = ""
                                            message = it
                                            load()
                                        }.onFailure {
                                            message = it.message ?: "Gagal simpan kas masuk."
                                        }
                                    }
                                },
                            ) { Text("Kas Masuk") }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            val amount = moveAmountText.toLongOrNull() ?: error("Nominal tidak valid")
                                            if (amount <= 0L) error("Nominal harus lebih dari 0")
                                            cashSessionRepository.addCashOut(
                                                sessionId = active.session.sessionId,
                                                amount = amount,
                                                note = moveNoteText,
                                                userId = userIdText.ifBlank { "cashier" },
                                                createdAt = nowIso(),
                                            )
                                            "Kas keluar tercatat."
                                        }.onSuccess {
                                            moveAmountText = "0"
                                            moveNoteText = ""
                                            message = it
                                            load()
                                        }.onFailure {
                                            message = it.message ?: "Gagal simpan kas keluar."
                                        }
                                    }
                                },
                            ) { Text("Kas Keluar") }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                Button(onClick = { load() }) { Text(if (loading) "Memuat..." else "Refresh") }
                Button(onClick = onOpenDashboard) { Text("Buka Dashboard") }
            }
        }

        val data = summary
        if (data == null) {
            item {
                AppEmptyState(
                    title = "Belum ada data arus kas",
                    message = "Lakukan transaksi atau refresh sinkronisasi.",
                )
            }
        } else {
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Ringkasan Arus Kas")
                        Text("Total Pemasukan (Semua Metode): ${formatRupiah(data.totalCashIn)}")
                        Text("Refund / Batal: ${formatRupiah(data.totalRefundOrCancelled)}")
                        Text("Penjualan Tunai Bersih: ${formatRupiah(data.cashSalesNet)}")
                        Text("Saldo Awal Sesi: ${formatRupiah(data.openingCashTotal)}")
                        Text("Kas Masuk Manual: ${formatRupiah(data.manualCashIn)}")
                        Text("Kas Keluar Manual: ${formatRupiah(data.manualCashOut)}")
                        Text(
                            "Posisi Kas Estimasi: ${formatRupiah(data.estimatedCashPosition)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Metode Pembayaran")
                        if (data.byMethod.isEmpty()) {
                            Text("Belum ada pembayaran.")
                        } else {
                            data.byMethod.forEach { method ->
                                Text("${method.methodName}: ${formatRupiah(method.total)}")
                            }
                        }
                    }
                }
            }
            item {
                AppSectionHeader("Riwayat Kas Terbaru")
            }
            if (data.recentEntries.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "Belum ada riwayat",
                        message = "Riwayat pembayaran akan muncul di sini.",
                    )
                }
            } else {
                items(data.recentEntries) { entry ->
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                            Text(entry.transaksiId ?: "-", fontWeight = FontWeight.SemiBold)
                            Text("Tanggal: ${formatReadableDateTime(entry.dateTime)}")
                            Text("Metode: ${entry.methodName}")
                            Text("Nominal: ${formatRupiah(entry.amount)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick) {
        Text(if (selected) "• $title" else title)
    }
}

@Preview
@Composable
private fun CashFlowScreenPreview() {
    AppTheme {
        LazyColumn(
            modifier = Modifier.padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            item { AppSectionHeader("Arus Kas", "Flow kas harian + input saldo awal kasir") }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Buka Shift Kasir", "Isi saldo awal kas")
                        OutlinedTextField(
                            value = "cashier",
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nama Kasir") },
                        )
                        OutlinedTextField(
                            value = "500000",
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Saldo Awal Kas") },
                        )
                        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Text("Buka Shift")
                        }
                    }
                }
            }
        }
    }
}
