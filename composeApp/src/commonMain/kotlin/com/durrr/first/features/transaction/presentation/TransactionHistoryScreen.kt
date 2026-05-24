package com.durrr.first.features.transaction.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.durrr.first.core.utils.formatReadableDateTime
import com.durrr.first.core.utils.formatNumber
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.TransaksiRepository
import com.durrr.first.domain.model.Transaksi
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppLoading
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

@Composable
fun TransactionHistoryScreen(
    transaksiRepository: TransaksiRepository,
    settingsRepository: SettingsRepository,
    onOpenReceipt: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(emptyList<Transaksi>()) }
    val scope = rememberCoroutineScope()

    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    suspend fun loadHistory() {
        loading = true
        error = null
        runCatching {
            transaksiRepository.listHistory(
                outletId = currentOutletId(),
                query = query,
                limit = 300,
            )
        }.onSuccess {
            history = it
        }.onFailure {
            error = it.message ?: "Gagal memuat riwayat transaksi."
            history = emptyList()
        }
        loading = false
    }

    LaunchedEffect(query) {
        loadHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        AppSectionHeader(
            title = "Riwayat Transaksi",
            subtitle = "Cari transaksi berdasarkan ID, meja, atau kasir.",
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari transaksi...") },
            placeholder = { Text("Contoh: trx_, Customer 01, cashier") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Total ${formatNumber(history.size.toLong())} transaksi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { scope.launch { loadHistory() } }) {
                Text(if (loading) "Memuat..." else "Refresh")
            }
        }
        if (!error.isNullOrBlank()) {
            AppCard {
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (loading) {
            AppLoading("Memuat riwayat transaksi...")
        } else if (history.isEmpty()) {
            AppEmptyState(
                title = if (error.isNullOrBlank()) "Belum ada transaksi" else "Riwayat belum tersedia",
                message = if (error.isNullOrBlank()) {
                    "Transaksi yang selesai checkout akan muncul di sini."
                } else {
                    "Server sedang offline / data belum siap. Coba lagi nanti atau tap Refresh."
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs),
            ) {
                items(history) { transaksi ->
                    TransactionHistoryRow(
                        transaksi = transaksi,
                        onOpenReceipt = { onOpenReceipt(transaksi.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionHistoryRow(
    transaksi: Transaksi,
    onOpenReceipt: () -> Unit,
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.xxs),
            ) {
                Text(
                    "Transaksi ${transaksi.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatReadableDateTime(transaksi.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Meja: ${transaksi.meja?.ifBlank { "-" } ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Kasir: ${transaksi.cashierName?.ifBlank { "-" } ?: transaksi.cashierId ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                horizontalAlignment = androidx.compose.ui.Alignment.End,
            ) {
                Text(
                    formatRupiah(transaksi.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = onOpenReceipt) {
                    Text("Lihat Struk")
                }
            }
        }
    }
}
