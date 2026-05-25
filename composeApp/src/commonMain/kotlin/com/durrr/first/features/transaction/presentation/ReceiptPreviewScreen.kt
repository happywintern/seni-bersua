package com.durrr.first.features.transaction.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.durrr.first.core.utils.formatReadableDateTime
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.data.repo.ReceiptRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.ReceiptData
import com.durrr.first.domain.model.Pembayaran
import com.durrr.first.domain.model.Transaksi
import com.durrr.first.domain.model.TransaksiDetail
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppLoading
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.design.Dimens
import com.durrr.first.features.transaction.domain.ReceiptDraftStore
import com.durrr.first.ui.platform.PlatformUriImage

@Composable
fun ReceiptPreviewScreen(
    transaksiId: String,
    receiptRepository: ReceiptRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit = {},
    onShareReceipt: (subject: String, textPayload: String) -> Unit = { _, _ -> },
    onPrintReceipt: (jobName: String, htmlPayload: String) -> Unit = { _, _ -> },
) {
    var loading by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<ReceiptData?>(null) }
    var storeName by remember { mutableStateOf("SuCash") }
    var storeAddress by remember { mutableStateOf("") }
    var headerLogoPath by remember { mutableStateOf("") }
    var watermarkLogoPath by remember { mutableStateOf("") }
    var footerText by remember { mutableStateOf("Thank you") }
    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    LaunchedEffect(transaksiId) {
        loading = true
        val config = settingsRepository.loadReceiptConfig()
        storeName = config.storeName
        storeAddress = config.storeAddressOrPhone
        headerLogoPath = config.headerLogoPath
        watermarkLogoPath = config.watermarkLogoPath
        footerText = config.footerText
        data = ReceiptDraftStore.getDraft(transaksiId)
            ?: receiptRepository.getReceiptData(transaksiId, currentOutletId())
        loading = false
    }

    if (loading) {
        AppLoading("Preparing receipt preview...")
        return
    }

    val receipt = data
    if (receipt == null) {
        AppEmptyState(
            title = "Receipt not found",
            message = "No receipt data for $transaksiId",
        )
        return
    }
    val receiptTitle = "Receipt ${receipt.transaksi.id.ifBlank { "Unknown" }}"
    val receiptShareText = remember(receipt, storeName, storeAddress, footerText) {
        buildReceiptShareText(
            receipt = receipt,
            storeName = storeName,
            storeAddress = storeAddress,
            footerText = footerText,
        )
    }
    val receiptPrintHtml = remember(receipt, storeName, storeAddress, footerText) {
        buildReceiptPrintHtml(
            receipt = receipt,
            storeName = storeName,
            storeAddress = storeAddress,
            footerText = footerText,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader(
                title = "Receipt Preview",
                subtitle = "Print-ready thermal layout",
            )
        }
        item {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack,
            ) {
                Text("Back")
            }
        }
        item {
            ReceiptPaper(
                receipt = receipt,
                storeName = storeName,
                storeAddress = storeAddress,
                headerLogoPath = headerLogoPath,
                watermarkLogoPath = watermarkLogoPath,
                footerText = footerText,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onPrintReceipt(receiptTitle, receiptPrintHtml) },
                ) {
                    Text("Print")
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onShareReceipt(receiptTitle, receiptShareText) },
                ) {
                    Text("Share / Save")
                }
            }
        }
    }
}

@Composable
private fun ReceiptPaper(
    receipt: ReceiptData,
    storeName: String,
    storeAddress: String,
    headerLogoPath: String,
    watermarkLogoPath: String,
    footerText: String,
) {
    val subtotal = receipt.details.sumOf { it.total }
    val payment = receipt.pembayaran

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (watermarkLogoPath.isNotBlank()) {
                PlatformUriImage(
                    uri = watermarkLogoPath,
                    contentDescription = "Receipt watermark logo",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.6f)
                        .height(120.dp),
                    alpha = 0.5f,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.xxs),
            ) {
                if (headerLogoPath.isNotBlank()) {
                    PlatformUriImage(
                        uri = headerLogoPath,
                        contentDescription = "Receipt header logo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        alpha = 1f,
                    )
                }
                Text(
                    text = storeName.ifBlank { "SuCash" },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                if (storeAddress.isNotBlank()) {
                    Text(
                        text = storeAddress,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black,
                    )
                }
                ReceiptDivider()
                ReceiptValueRow("Transaction", receipt.transaksi.id)
                ReceiptValueRow("Date", formatReadableDateTime(receipt.transaksi.createdAt))
                ReceiptValueRow("Table", receipt.transaksi.meja ?: "-")
                ReceiptDivider()

                Text(
                    text = "ITEMS",
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                if (receipt.details.isEmpty()) {
                    Text(
                        text = "-",
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black,
                    )
                } else {
                    receipt.details.forEach { detail ->
                        Text(
                            text = detail.itemName,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                        )
                        ReceiptValueRow(
                            label = "${detail.qty} x ${formatRupiah(detail.price)}",
                            value = formatRupiah(detail.total),
                        )
                    }
                }

                ReceiptDivider()
                ReceiptValueRow("Subtotal", formatRupiah(subtotal))
                ReceiptValueRow("Discount", formatRupiah(receipt.transaksi.discountPlus))
                ReceiptValueRow("Tax", formatRupiah(receipt.transaksi.tax))
                ReceiptValueRow("Service", formatRupiah(receipt.transaksi.serviceCharge))
                ReceiptValueRow("Rounding", formatRupiah(receipt.transaksi.rounding))
                ReceiptDivider()
                ReceiptValueRow("TOTAL", formatRupiah(receipt.transaksi.total), emphasized = true)
                ReceiptValueRow("Paid", formatRupiah(payment?.amountPaid ?: 0L))
                ReceiptValueRow("Change", formatRupiah(payment?.change ?: 0L), emphasized = true)
                ReceiptDivider()
                Text(
                    text = footerText.ifBlank { "Thank you" },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun ReceiptValueRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
        )
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
        )
    }
}

@Composable
private fun ReceiptDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = Dimens.xxs),
        color = Color.Black.copy(alpha = 0.3f),
    )
}

private fun buildReceiptShareText(
    receipt: ReceiptData,
    storeName: String,
    storeAddress: String,
    footerText: String,
): String {
    val subtotal = receipt.details.sumOf { it.total }
    val payment = receipt.pembayaran
    return buildString {
        appendLine(storeName.ifBlank { "SuCash" })
        if (storeAddress.isNotBlank()) appendLine(storeAddress)
        appendLine("--------------------------------------------------")
        appendLine("Transaction: ${receipt.transaksi.id}")
        appendLine("Date: ${formatReadableDateTime(receipt.transaksi.createdAt)}")
        appendLine("Table: ${receipt.transaksi.meja ?: "-"}")
        appendLine("--------------------------------------------------")
        if (receipt.details.isEmpty()) {
            appendLine("(No items)")
        } else {
            receipt.details.forEach { detail ->
                appendLine(detail.itemName)
                appendLine("${detail.qty} x ${formatRupiah(detail.price)} = ${formatRupiah(detail.total)}")
            }
        }
        appendLine("--------------------------------------------------")
        appendLine("Subtotal: ${formatRupiah(subtotal)}")
        appendLine("Discount: ${formatRupiah(receipt.transaksi.discountPlus)}")
        appendLine("Tax: ${formatRupiah(receipt.transaksi.tax)}")
        appendLine("Service: ${formatRupiah(receipt.transaksi.serviceCharge)}")
        appendLine("Rounding: ${formatRupiah(receipt.transaksi.rounding)}")
        appendLine("TOTAL: ${formatRupiah(receipt.transaksi.total)}")
        appendLine("Paid: ${formatRupiah(payment?.amountPaid ?: 0L)}")
        appendLine("Change: ${formatRupiah(payment?.change ?: 0L)}")
        appendLine("Payment: ${payment?.paymentTypeId ?: "-"}")
        appendLine("--------------------------------------------------")
        appendLine(footerText.ifBlank { "Thank you" })
    }
}

private fun buildReceiptPrintHtml(
    receipt: ReceiptData,
    storeName: String,
    storeAddress: String,
    footerText: String,
): String {
    val subtotal = receipt.details.sumOf { it.total }
    val payment = receipt.pembayaran
    val itemsHtml = if (receipt.details.isEmpty()) {
        "<tr><td colspan=\"2\">(No items)</td></tr>"
    } else {
        receipt.details.joinToString("") { detail ->
            "<tr><td><b>${escapeHtml(detail.itemName)}</b><br/>${detail.qty} x ${formatRupiah(detail.price)}</td><td style=\"text-align:right;\">${formatRupiah(detail.total)}</td></tr>"
        }
    }
    return """
        <html>
        <body style="font-family: monospace; padding: 12px;">
          <h2 style="text-align:center; margin:0;">${escapeHtml(storeName.ifBlank { "SuCash" })}</h2>
          ${if (storeAddress.isNotBlank()) "<div style=\"text-align:center; margin-bottom:8px;\">${escapeHtml(storeAddress)}</div>" else ""}
          <hr/>
          <div>Transaction: ${escapeHtml(receipt.transaksi.id)}</div>
          <div>Date: ${escapeHtml(formatReadableDateTime(receipt.transaksi.createdAt))}</div>
          <div>Table: ${escapeHtml(receipt.transaksi.meja ?: "-")}</div>
          <hr/>
          <table style="width:100%; border-collapse:collapse;">
            $itemsHtml
          </table>
          <hr/>
          <table style="width:100%;">
            <tr><td>Subtotal</td><td style="text-align:right;">${formatRupiah(subtotal)}</td></tr>
            <tr><td>Discount</td><td style="text-align:right;">${formatRupiah(receipt.transaksi.discountPlus)}</td></tr>
            <tr><td>Tax</td><td style="text-align:right;">${formatRupiah(receipt.transaksi.tax)}</td></tr>
            <tr><td>Service</td><td style="text-align:right;">${formatRupiah(receipt.transaksi.serviceCharge)}</td></tr>
            <tr><td>Rounding</td><td style="text-align:right;">${formatRupiah(receipt.transaksi.rounding)}</td></tr>
            <tr><td><b>TOTAL</b></td><td style="text-align:right;"><b>${formatRupiah(receipt.transaksi.total)}</b></td></tr>
            <tr><td>Paid</td><td style="text-align:right;">${formatRupiah(payment?.amountPaid ?: 0L)}</td></tr>
            <tr><td>Change</td><td style="text-align:right;">${formatRupiah(payment?.change ?: 0L)}</td></tr>
            <tr><td>Payment</td><td style="text-align:right;">${escapeHtml(payment?.paymentTypeId ?: "-")}</td></tr>
          </table>
          <hr/>
          <div style="text-align:center;">${escapeHtml(footerText.ifBlank { "Thank you" })}</div>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeHtml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

@Preview
@Composable
private fun ReceiptPreviewScreenPaperPreview() {
    val transaksi = Transaksi(
        id = "trx-preview",
        createdAt = "2026-04-27T10:30:00",
        meja = "T-07",
        discountPlus = 0,
        tax = 2000,
        serviceCharge = 1000,
        rounding = 0,
        total = 44000,
    )
    val details = listOf(
        TransaksiDetail(
            id = "d1",
            transaksiId = transaksi.id,
            itemId = "item-1",
            itemName = "Es Teh",
            qty = 2,
            price = 8000,
            discount = 0,
            total = 16000,
        ),
        TransaksiDetail(
            id = "d2",
            transaksiId = transaksi.id,
            itemId = "item-2",
            itemName = "Nasi Goreng",
            qty = 1,
            price = 25000,
            discount = 0,
            total = 25000,
        ),
    )
    val payment = Pembayaran(
        id = "pay-preview",
        transaksiId = transaksi.id,
        paidAt = transaksi.createdAt,
        amountPaid = 50000,
        change = 6000,
        paymentTypeId = "CASH",
    )

    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            ReceiptPaper(
                receipt = ReceiptData(transaksi = transaksi, details = details, pembayaran = payment),
                storeName = "SuCash",
                storeAddress = "Jl. Preview 123",
                headerLogoPath = "",
                watermarkLogoPath = "",
                footerText = "Terima kasih",
            )
        }
    }
}
