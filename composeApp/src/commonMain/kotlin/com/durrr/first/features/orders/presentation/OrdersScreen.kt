package com.durrr.first.features.orders.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.durrr.first.core.utils.formatReadableDateTime
import com.durrr.first.core.utils.formatNumber
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.data.repo.OrderCacheRepository
import com.durrr.first.data.repo.OrderSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.OrderHeader
import com.durrr.first.domain.model.OrderItem
import com.durrr.first.domain.model.OrderStatus
import com.durrr.first.domain.model.OrderWithItems
import com.durrr.first.network.dto.ServerOrderStatus
import com.durrr.first.ui.notification.AppNotificationLevel
import com.durrr.first.ui.design.AppTheme
import kotlinx.coroutines.launch

private enum class OrderTab(val title: String) {
    PROCESS("Dalam Proses"),
    DONE("Selesai"),
}

private val FigmaBorder = Color(0xFFB7B7B7)
private val FigmaBlue = Color(0xFF273BBF)
private val FigmaLightPanel = Color(0xFFF5F8FF)

@Composable
fun OrdersScreen(
    orderRepository: OrderCacheRepository,
    orderSyncRepository: OrderSyncRepository,
    settingsRepository: SettingsRepository,
    onNotify: (title: String, message: String, level: AppNotificationLevel) -> Unit = { _, _, _ -> },
    onCreateWalkInOrder: () -> Unit = {},
) {
    var orders by remember { mutableStateOf(emptyList<OrderWithItems>()) }
    var isPullingOrders by remember { mutableStateOf(false) }
    var processingOrderId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(OrderTab.PROCESS) }
    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isBusy = isPullingOrders || processingOrderId != null

    fun serverBaseUrl(): String? = settingsRepository.getOptionalServerBaseUrl()

    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    fun reloadLocalOrders() {
        orders = orderRepository.getAllOrders(currentOutletId())
        if (selectedOrderId == null && orders.isNotEmpty()) selectedOrderId = orders.first().header.id
    }

    suspend fun showNotification(message: String?) {
        val text = message.orEmpty().trim()
        if (text.isBlank()) return
        onNotify("Orders", text, notificationLevelForMessage(text))
        snackbarHostState.showSnackbar(text)
    }

    suspend fun pullOrders() {
        val baseUrl = serverBaseUrl() ?: run {
            showNotification("Server belum dipasang. Orders yang tampil hanya cache lokal.")
            return
        }
        isPullingOrders = true
        try {
            val pulled = orderSyncRepository.pullOrders(baseUrl, currentOutletId())
            val reservationSummary = runCatching {
                orderSyncRepository.fetchReservationSummary(baseUrl, currentOutletId())
            }.getOrNull()
            reloadLocalOrders()
            val message = buildString {
                append("Synced $pulled order(s)")
                reservationSummary?.let {
                    append(" • Reservations: ${it.total}")
                    if (it.pending > 0) {
                        append(" (pending ${it.pending})")
                    }
                }
            }
            showNotification(message)
        } catch (t: Throwable) {
            showNotification(t.message ?: "Sync failed")
        } finally {
            isPullingOrders = false
        }
    }

    suspend fun advanceStatus(order: OrderWithItems) {
        val next = nextServerStatus(order.header.status) ?: return
        processingOrderId = order.header.id
        try {
            val result = orderSyncRepository.updateStatus(
                baseUrl = serverBaseUrl(),
                orderId = order.header.id,
                targetStatus = next,
                outletId = currentOutletId(),
            )
            reloadLocalOrders()
            if (next == ServerOrderStatus.DONE) {
                selectedTab = OrderTab.DONE
                selectedOrderId = order.header.id
            }
            val baseMessage = "Order ${friendlyOrderNumber(order)} -> ${nextActionResultLabel(next)}"
            val notification = when {
                result.remoteSynced -> "$baseMessage (server synced)"
                !result.warning.isNullOrBlank() -> "$baseMessage (${result.warning})"
                result.localSaved -> "$baseMessage (local saved)"
                else -> "Gagal update status order."
            }
            showNotification(notification)
        } catch (t: Throwable) {
            showNotification(t.message ?: "Gagal update status order")
        } finally {
            processingOrderId = null
        }
    }

    LaunchedEffect(Unit) {
        reloadLocalOrders()
        if (serverBaseUrl() != null) {
            pullOrders()
        }
    }

    val filteredOrders = orders.filter { order ->
        val inSelectedTab = when (selectedTab) {
            OrderTab.PROCESS -> order.header.status == OrderStatus.NEW ||
                order.header.status == OrderStatus.ACCEPTED ||
                order.header.status == OrderStatus.COOKING ||
                order.header.status == OrderStatus.SERVED
            OrderTab.DONE -> order.header.status == OrderStatus.DONE
        }
        val matchesSearch = searchQuery.isBlank() ||
            order.header.id.contains(searchQuery, true) ||
            order.header.token.orEmpty().contains(searchQuery, true)
        inSelectedTab && matchesSearch
    }
    val selectedOrder = filteredOrders.firstOrNull { it.header.id == selectedOrderId } ?: filteredOrders.firstOrNull()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
    ) {
        val wide = maxWidth >= 1100.dp
        val compact = maxWidth < 760.dp
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FigmaSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Cari Pesanan",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FigmaOutlineButton(
                        label = "Filter",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onCreateWalkInOrder,
                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Kasir") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FigmaSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Cari Pesanan",
                        modifier = Modifier.weight(1f),
                    )
                    FigmaOutlineButton(
                        label = "Filter",
                        onClick = {},
                        modifier = Modifier.heightIn(min = 72.dp),
                    )
                    if (!wide) {
                        Button(
                            onClick = onCreateWalkInOrder,
                            colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
                        ) { Text("Kasir") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OrderTab.values().forEach { tab ->
                    FigmaTab(
                        title = tab.title,
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                    )
                }
            }
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1.7f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onCreateWalkInOrder,
                                colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
                            ) { Text("Buat Pesanan Baru") }
                            FigmaOutlineButton(
                                label = "Pull Orders",
                                onClick = {
                                scope.launch { pullOrders() }
                                },
                                enabled = !isBusy,
                                loading = isPullingOrders,
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(filteredOrders) { order ->
                                OrderListCard(
                                    order = order,
                                    selected = selectedOrder?.header?.id == order.header.id,
                                    onClick = { selectedOrderId = order.header.id },
                                    actionLabel = if (selectedTab == OrderTab.PROCESS) nextActionLabel(order.header.status) else null,
                                    onActionClick = { scope.launch { advanceStatus(order) } },
                                    actionEnabled = !isBusy,
                                    actionLoading = processingOrderId == order.header.id,
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .background(FigmaLightPanel, RoundedCornerShape(20.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (selectedOrder == null) {
                            Text("Belum ada order", color = Color.Gray)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("No.Pesanan", color = Color.Gray)
                                    Text(friendlyOrderNumber(selectedOrder), fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("No.Meja", color = Color.Gray)
                                    Text(friendlyTableLabel(selectedOrder), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, FigmaBlue, RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                selectedOrder.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column {
                                            Text(item.itemName, fontWeight = FontWeight.SemiBold)
                                            Text("${formatRupiah(item.price)} x ${formatNumber(item.qty)}", color = Color.Gray)
                                        }
                                        Text(formatRupiah(item.price * item.qty), color = FigmaBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            val totalQty = selectedOrder.items.sumOf { it.qty }
                            val totalAmount = selectedOrder.items.sumOf { it.price * it.qty }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Ringkasan Pembayaran", fontWeight = FontWeight.SemiBold)
                                SummaryLine("Item (${formatNumber(totalQty)})", formatRupiah(totalAmount))
                                SummaryLine("Diskon", formatRupiah(0L))
                                SummaryLine("Total", formatRupiah(totalAmount))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Metode Pembayaran", fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FigmaOutlineButton(label = "Tunai", onClick = {})
                                    Button(
                                        onClick = {},
                                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
                                    ) { Text("QRIS") }
                                }
                            }
                            Button(
                                onClick = { scope.launch { advanceStatus(selectedOrder) } },
                                colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy && nextActionLabel(selectedOrder.header.status) != null,
                            ) {
                                if (processingOrderId == selectedOrder.header.id) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White,
                                        )
                                        Text("Processing...")
                                    }
                                } else {
                                    Text(nextActionLabel(selectedOrder.header.status) ?: "Selesai")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onCreateWalkInOrder,
                                colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Buat Pesanan Baru") }
                            FigmaOutlineButton(
                                label = "Pull Orders",
                                onClick = { scope.launch { pullOrders() } },
                                enabled = !isBusy,
                                loading = isPullingOrders,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(filteredOrders) { order ->
                        OrderListCard(
                            order = order,
                            selected = false,
                            onClick = { selectedOrderId = order.header.id },
                            actionLabel = if (selectedTab == OrderTab.PROCESS) nextActionLabel(order.header.status) else null,
                            onActionClick = { scope.launch { advanceStatus(order) } },
                            actionEnabled = !isBusy,
                            actionLoading = processingOrderId == order.header.id,
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

private fun notificationLevelForMessage(message: String): AppNotificationLevel {
    val value = message.lowercase()
    return when {
        "failed" in value || "error" in value || "gagal" in value -> AppNotificationLevel.ERROR
        "warning" in value || "offline" in value || "timeout" in value || "local saved" in value -> AppNotificationLevel.WARNING
        else -> AppNotificationLevel.INFO
    }
}

@Composable
private fun OrderListCard(
    order: OrderWithItems,
    selected: Boolean,
    onClick: () -> Unit,
    actionLabel: String?,
    onActionClick: () -> Unit,
    actionEnabled: Boolean = true,
    actionLoading: Boolean = false,
) {
    val total = order.items.sumOf { it.price * it.qty }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) FigmaBlue else FigmaBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val orderLabel = friendlyOrderNumber(order)
        val tableLabel = friendlyTableLabel(order)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pesanan $orderLabel", color = Color(0xFF5D5D5D), fontWeight = FontWeight.SemiBold)
                Text("No. Meja : $tableLabel", color = Color(0xFF5D5D5D), fontWeight = FontWeight.SemiBold)
                Text("Status : ${statusLabel(order.header.status)}", color = Color(0xFF5D5D5D), fontWeight = FontWeight.SemiBold)
                Text("qty : ${formatNumber(order.items.sumOf { it.qty })}", color = Color(0xFF5D5D5D), fontWeight = FontWeight.SemiBold)
                order.header.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(notes, color = Color(0xFF7A7A7A), style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(40.dp), horizontalAlignment = Alignment.End) {
                Text(friendlyCreatedAt(order.header.createdAt), color = Color(0xFFB0B0B0))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(formatRupiah(total), color = FigmaBlue, fontWeight = FontWeight.Bold)
                    Text("QR Code", color = Color(0xFF5D5D5D))
                }
            }
        }
        if (actionLabel != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onActionClick,
                    enabled = actionEnabled && !actionLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (actionLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Text("Loading...")
                        }
                    } else {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: OrderStatus): String {
    return when (status) {
        OrderStatus.NEW -> "Baru"
        OrderStatus.ACCEPTED -> "Diterima"
        OrderStatus.COOKING -> "Diproses"
        OrderStatus.SERVED -> "Disajikan"
        OrderStatus.DONE -> "Selesai"
        OrderStatus.CANCELLED -> "Dibatalkan"
    }
}

private fun friendlyOrderNumber(order: OrderWithItems): String {
    val createdAt = order.header.createdAt
    val dateDigits = createdAt.substringBefore('T').filter { it.isDigit() }
    val timeDigits = createdAt.substringAfter('T', "").filter { it.isDigit() }
    if (dateDigits.length >= 8 && timeDigits.length >= 4) {
        val shortDate = dateDigits.takeLast(6)
        val shortTime = timeDigits.take(4)
        return "ORD-$shortDate-$shortTime"
    }
    val shortId = order.header.id.substringBefore('-').ifBlank { order.header.id.take(8) }
    return "ORD-$shortId"
}

private fun friendlyTableLabel(order: OrderWithItems): String {
    val customerFromNotes = order.header.notes
        ?.split("|")
        ?.firstOrNull { it.contains("Customer:", ignoreCase = true) }
        ?.substringAfter(":", "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    if (customerFromNotes != null) return customerFromNotes

    val token = order.header.token.orEmpty().trim()
    if (token.isBlank()) return "-"
    return if (token.length > 8) token.take(8) else token
}

private fun friendlyCreatedAt(createdAt: String): String {
    return formatReadableDateTime(createdAt)
}

@Composable
private fun FigmaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0xFFB0B0B0)) },
        leadingIcon = { Text("\uD83D\uDD0D", color = Color(0xFFB0B0B0)) },
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
    )
}

@Composable
private fun FigmaOutlineButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF8A8A8A),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBorder),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        if (loading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = FigmaBlue,
                )
                Text("Sync...")
            }
        } else {
            Text(label)
        }
    }
}

@Composable
private fun FigmaTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = if (selected) FigmaBlue else Color(0xFF6B6B6B),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) FigmaBlue else FigmaBorder),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(title)
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, color = Color(0xFF5D5D5D))
    }
}

private fun nextServerStatus(status: OrderStatus): ServerOrderStatus? {
    return when (status) {
        OrderStatus.NEW -> ServerOrderStatus.ACCEPTED
        OrderStatus.ACCEPTED -> ServerOrderStatus.DONE
        OrderStatus.COOKING -> ServerOrderStatus.DONE
        OrderStatus.SERVED -> ServerOrderStatus.DONE
        OrderStatus.DONE, OrderStatus.CANCELLED -> null
    }
}

private fun nextActionLabel(status: OrderStatus): String? {
    return when (status) {
        OrderStatus.NEW -> "Accept"
        OrderStatus.ACCEPTED -> "Done"
        OrderStatus.COOKING -> "Done"
        OrderStatus.SERVED -> "Done"
        OrderStatus.DONE, OrderStatus.CANCELLED -> null
    }
}

private fun nextActionResultLabel(status: ServerOrderStatus): String {
    return when (status) {
        ServerOrderStatus.NEW -> "Baru"
        ServerOrderStatus.ACCEPTED -> "Accepted"
        ServerOrderStatus.PREPARING -> "Diproses"
        ServerOrderStatus.SERVED -> "Disajikan"
        ServerOrderStatus.DONE -> "Done"
        ServerOrderStatus.CANCELLED -> "Dibatalkan"
    }
}

@Preview
@Composable
fun OrdersScreenPreviewCard() {
    val sample = OrderWithItems(
        header = OrderHeader(
            id = "ORD-1001",
            token = "T-07",
            status = OrderStatus.ACCEPTED,
            notes = "Customer: Adi | Payment: At Cashier",
            createdAt = "2026-04-27T09:12:00",
            updatedAt = "2026-04-27T09:13:00",
        ),
        items = listOf(
            OrderItem(
                id = "oi-1",
                orderId = "ORD-1001",
                itemId = "item-1",
                itemName = "Es Teh",
                qty = 2,
                price = 8000,
                note = null,
            ),
            OrderItem(
                id = "oi-2",
                orderId = "ORD-1001",
                itemId = "item-2",
                itemName = "Nasi Goreng",
                qty = 1,
                price = 25000,
                note = "Pedas",
            ),
        ),
    )
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FigmaTab(title = "Dalam Proses", selected = true, onClick = {})
            OrderListCard(
                order = sample,
                selected = true,
                onClick = {},
                actionLabel = "Done",
                onActionClick = {},
            )
            FigmaOutlineButton(label = "Pull Orders", onClick = {})
        }
    }
}
