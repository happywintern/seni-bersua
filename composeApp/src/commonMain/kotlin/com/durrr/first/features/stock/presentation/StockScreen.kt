package com.durrr.first.features.stock.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.tooling.preview.Preview
import com.durrr.first.core.utils.formatReadableDateTime
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.StockRepository
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.model.LowStockItem
import com.durrr.first.domain.model.StockLedgerEntry
import com.durrr.first.domain.model.StockMovementType
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppErrorBanner
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

@Composable
fun StockScreen(
    stockRepository: StockRepository,
    menuRepository: MenuRepository,
    settingsRepository: SettingsRepository,
    nowIso: () -> String,
) {
    var items by remember { mutableStateOf(emptyList<Item>()) }
    var lowStock by remember { mutableStateOf(emptyList<LowStockItem>()) }
    var history by remember { mutableStateOf(emptyList<StockLedgerEntry>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var showItemPicker by remember { mutableStateOf(false) }
    var qtyDeltaText by remember { mutableStateOf("") }
    var thresholdText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    fun allowNegativeStock(): Boolean {
        return settingsRepository
            .getValue(SettingsRepository.KEY_ALLOW_NEGATIVE_STOCK)
            .ifBlank { "true" }
            .equals("true", ignoreCase = true)
    }

    fun refresh() {
        val outletId = currentOutletId()
        items = menuRepository.getItems(outletId).filter { it.isActive }
        lowStock = stockRepository.getLowStockItems(outletId)
        history = stockRepository.getStockHistory(outletId = outletId, limit = 50)
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val selectedItem = items.firstOrNull { it.id == selectedItemId }
    LazyColumn(
        modifier = Modifier.padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader("Stock", "Low-stock alerts, manual adjustment, and movement history")
        }
        item {
            if (!message.isNullOrBlank()) {
                AppErrorBanner(message = message.orEmpty())
            }
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    AppSectionHeader("Adjust Stock")
                    OutlinedTextField(
                        value = selectedItem?.name ?: "",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        label = { Text("Item") },
                        placeholder = { Text("Select item") },
                    )
                    DropdownMenu(
                        expanded = showItemPicker,
                        onDismissRequest = { showItemPicker = false },
                    ) {
                        items.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.name} (Rp ${item.price})") },
                                onClick = {
                                    selectedItemId = item.id
                                    showItemPicker = false
                                },
                            )
                        }
                    }
                    Button(
                        onClick = { showItemPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (selectedItem == null) "Pick Item" else "Change Item")
                    }
                    OutlinedTextField(
                        value = qtyDeltaText,
                        onValueChange = { qtyDeltaText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Qty Delta (+/-)") },
                        placeholder = { Text("Example: +10 or -2") },
                    )
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reason") },
                    )
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Low-stock threshold") },
                        placeholder = { Text("Optional") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val itemId = selectedItemId ?: error("Please pick an item first")
                                        val qtyDelta = qtyDeltaText.replace("+", "").toLongOrNull()
                                            ?: error("Invalid qty delta")
                                        stockRepository.adjustStock(
                                            itemId = itemId,
                                            outletId = currentOutletId(),
                                            qtyDelta = qtyDelta,
                                            reason = reasonText.ifBlank { "Manual adjustment" },
                                            user = settingsRepository.resolveCurrentCashierName(),
                                            createdAt = nowIso(),
                                            allowNegativeStock = allowNegativeStock(),
                                        )
                                        "Stock adjusted for ${selectedItem?.name ?: itemId}"
                                    }.onSuccess {
                                        message = it
                                        qtyDeltaText = ""
                                        reasonText = ""
                                        refresh()
                                    }.onFailure {
                                        message = it.message ?: "Failed to adjust stock"
                                    }
                                }
                            },
                        ) {
                            Text("Apply")
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val itemId = selectedItemId ?: error("Please pick an item first")
                                        val threshold = thresholdText.toLongOrNull()
                                            ?: error("Invalid threshold")
                                        stockRepository.setThreshold(
                                            itemId = itemId,
                                            minQty = threshold,
                                            outletId = currentOutletId(),
                                        )
                                        "Threshold saved for ${selectedItem?.name ?: itemId}"
                                    }.onSuccess {
                                        message = it
                                        refresh()
                                    }.onFailure {
                                        message = it.message ?: "Failed to save threshold"
                                    }
                                }
                            },
                        ) {
                            Text("Set Threshold")
                        }
                    }
                    Text(
                        text = "Negative stock is ${if (allowNegativeStock()) "enabled" else "disabled"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            AppCard {
                AppSectionHeader("Low Stock")
                if (lowStock.isEmpty()) {
                    Text("No low-stock items.")
                } else {
                    lowStock.forEach { row ->
                        Text("${row.itemName}: ${row.qtyOnHand} (min ${row.minQty})")
                    }
                }
            }
        }
        item {
            AppSectionHeader("Stock Movement History")
        }
        if (history.isEmpty()) {
            item {
                AppEmptyState(
                    title = "No stock history",
                    message = "Run a sale or manual adjustment to create stock ledger entries.",
                )
            }
        } else {
            items(history) { entry ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                        Text(
                            "${entry.movementType.name} ${entry.qtyDelta}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Item: ${entry.itemId}")
                        Text("At: ${formatReadableDateTime(entry.createdAt)}")
                        if (!entry.referenceId.isNullOrBlank()) {
                            Text("Ref: ${entry.referenceType ?: "-"} / ${entry.referenceId}")
                        }
                        if (!entry.reason.isNullOrBlank()) {
                            Text("Reason: ${entry.reason}")
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun StockScreenPreview() {
    val lowStock = listOf(
        LowStockItem("item-1", "Es Teh", "default", 3, 5),
        LowStockItem("item-2", "Nasi Goreng", "default", 2, 4),
    )
    val history = listOf(
        StockLedgerEntry(
            ledgerId = "lg-1",
            outletId = "default",
            itemId = "item-1",
            movementType = StockMovementType.ADJUST_IN,
            qtyDelta = 10,
            referenceType = null,
            referenceId = null,
            reason = "Restock manual",
            createdBy = "cashier",
            createdAt = "2026-04-27T09:00:00",
        ),
    )

    AppTheme {
        LazyColumn(
            modifier = Modifier.padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            item { AppSectionHeader("Stock", "Low-stock alerts, manual adjustment, and movement history") }
            item {
                AppCard {
                    AppSectionHeader("Low Stock")
                    lowStock.forEach { row ->
                        Text("${row.itemName}: ${row.qtyOnHand} (min ${row.minQty})")
                    }
                }
            }
            item { AppSectionHeader("Stock Movement History") }
            items(history) { entry ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                        Text("${entry.movementType.name} ${entry.qtyDelta}", style = MaterialTheme.typography.titleMedium)
                        Text("Item: ${entry.itemId}")
                        Text("At: ${formatReadableDateTime(entry.createdAt)}")
                        Text("Reason: ${entry.reason ?: "-"}")
                    }
                }
            }
        }
    }
}
