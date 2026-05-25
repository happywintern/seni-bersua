package com.durrr.first.features.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.OrderSyncRepository
import com.durrr.first.data.repo.ServerAuthRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.TransaksiSyncRepository
import com.durrr.first.domain.model.ReceiptConfig
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppInfoLine
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.design.Dimens
import com.durrr.first.ui.notification.AppNotificationLevel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    pickImage: ((String?) -> Unit) -> Unit,
    menuRepository: MenuRepository? = null,
    uploadMenuImage: suspend (baseUrl: String, localUri: String, outletId: String) -> String? = { _, _, _ -> null },
    menuSyncRepository: MenuSyncRepository? = null,
    orderSyncRepository: OrderSyncRepository? = null,
    transaksiSyncRepository: TransaksiSyncRepository? = null,
    serverAuthRepository: ServerAuthRepository? = null,
    isOwnerSession: Boolean = false,
    onNotify: (title: String, message: String, level: AppNotificationLevel) -> Unit = { _, _, _ -> },
    onRequireLocalSetup: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenCashFlow: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenCashClosing: () -> Unit = {},
) {
    var storeName by remember { mutableStateOf("") }
    var storeAddress by remember { mutableStateOf("") }
    var headerLogoPath by remember { mutableStateOf("") }
    var watermarkLogoPath by remember { mutableStateOf("") }
    var footerText by remember { mutableStateOf("") }
    var serverBaseUrl by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var autoTaxPercent by remember { mutableStateOf("11") }
    var autoServicePercent by remember { mutableStateOf("10") }
    var autoRounding by remember { mutableStateOf("0") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var syncBusyAction by remember { mutableStateOf<String?>(null) }
    val syncBusy = syncBusyAction != null
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val config = settingsRepository.loadReceiptConfig()
        storeName = config.storeName
        storeAddress = config.storeAddressOrPhone
        headerLogoPath = config.headerLogoPath
        watermarkLogoPath = config.watermarkLogoPath
        footerText = config.footerText
        serverBaseUrl = settingsRepository.getValue(SettingsRepository.KEY_SERVER_BASE_URL)
        autoTaxPercent = settingsRepository
            .getValue(SettingsRepository.KEY_AUTO_TAX_PERCENT)
            .ifBlank { "11" }
        autoServicePercent = settingsRepository
            .getValue(SettingsRepository.KEY_AUTO_SERVICE_PERCENT)
            .ifBlank { "10" }
        autoRounding = settingsRepository
            .getValue(SettingsRepository.KEY_AUTO_ROUNDING)
            .ifBlank { "0" }
    }

    suspend fun showSyncNotification(message: String?) {
        val text = message.orEmpty().trim()
        if (text.isNotBlank()) {
            onNotify("Settings", text, notificationLevelForMessage(text))
            snackbarHostState.showSnackbar(text)
        }
    }

    suspend fun runSyncAction(actionId: String, block: suspend () -> String): Boolean {
        syncBusyAction = actionId
        val result = runCatching { block() }
            .onSuccess { showSyncNotification(it) }
            .onFailure { showSyncNotification(it.message ?: "Unknown error") }
        syncBusyAction = null
        return result.isSuccess
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
        fun currentBaseUrlOrNull(): String? = serverBaseUrl.trim().ifBlank { null }
        fun requireBaseUrl(): String = currentBaseUrlOrNull() ?: error("Set Server Base URL first in Settings.")
        fun currentOutletId(): String = settingsRepository.resolveOutletId()
        val hasServerConfigured = currentBaseUrlOrNull() != null
        val activeSession = settingsRepository.getActiveUserSession()
        val ownerAccess = isOwnerSession || activeSession?.role == SettingsRepository.ROLE_OWNER
        fun requireOwnerAccessOrFail() {
            if (!ownerAccess) {
                error("Aksi ini khusus Owner.")
            }
        }
        suspend fun uploadPendingMenuImagesForSync(baseUrl: String, outletId: String): String {
            val repo = menuRepository ?: return "menu_image_upload:skipped(no_repo)"
            val candidates = repo.getItems(outletId)
                .filter { it.isActive }
                .filter { isLocalDeviceImageUri(it.imageUrl) }
            if (candidates.isEmpty()) return "menu_image_upload:0"

            var uploadedCount = 0
            val failures = mutableListOf<String>()
            candidates.forEach { item ->
                val localUri = item.imageUrl?.trim().orEmpty()
                val uploaded = runCatching {
                    uploadMenuImage(baseUrl, localUri, outletId)
                }.getOrElse { error ->
                    failures += "${item.name}: ${error.message ?: "upload error"}"
                    null
                }
                val finalUrl = uploaded?.trim().orEmpty()
                if (finalUrl.isNotBlank() && !isLocalDeviceImageUri(finalUrl)) {
                    repo.upsertItem(item.copy(imageUrl = finalUrl), outletId)
                    uploadedCount += 1
                } else if (failures.none { it.startsWith("${item.name}:") }) {
                    failures += "${item.name}: upload returned empty URL"
                }
            }
            if (failures.isNotEmpty()) {
                error(
                    "Upload foto produk belum tuntas ($uploadedCount/${candidates.size} berhasil). " +
                        "Contoh gagal: ${failures.first()}"
                )
            }
            return "menu_image_upload:$uploadedCount/${candidates.size}"
        }

        AppSectionHeader("Settings", "Receipt and local app configuration")

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                AppSectionHeader("Device Access", "Login session untuk tablet ini")
                AppInfoLine("Role", activeSession?.role ?: "-")
                AppInfoLine("User", activeSession?.userName ?: "-")
                AppInfoLine("User ID", activeSession?.userId ?: "-")
                Button(
                    onClick = {
                        settingsRepository.clearActiveUser()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Lock App (Logout)")
                }
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Store Name") },
                )
                OutlinedTextField(
                    value = storeAddress,
                    onValueChange = { storeAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Address / Phone") },
                )
                OutlinedTextField(
                    value = headerLogoPath,
                    onValueChange = { headerLogoPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Header Logo URI") },
                )
                Button(
                    onClick = {
                        pickImage { uri ->
                            if (!uri.isNullOrBlank()) headerLogoPath = uri
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pick Header Logo (Android)")
                }
                OutlinedTextField(
                    value = watermarkLogoPath,
                    onValueChange = { watermarkLogoPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Watermark Logo URI") },
                )
                Button(
                    onClick = {
                        pickImage { uri ->
                            if (!uri.isNullOrBlank()) watermarkLogoPath = uri
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pick Watermark Logo (Android)")
                }
                OutlinedTextField(
                    value = footerText,
                    onValueChange = { footerText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Footer Text") },
                )
                Button(
                    onClick = {
                        if (!ownerAccess) {
                            savedMessage = "Hanya Owner yang boleh ubah pengaturan struk."
                            return@Button
                        }
                        val saved = settingsRepository.saveReceiptConfig(
                            ReceiptConfig(
                                storeName = storeName,
                                storeAddressOrPhone = storeAddress,
                                headerLogoPath = headerLogoPath,
                                watermarkLogoPath = watermarkLogoPath,
                                footerText = footerText,
                            )
                        )
                        savedMessage = if (saved) {
                            "Saved"
                        } else {
                            "Failed to save settings"
                        }
                    },
                    enabled = ownerAccess,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Receipt Settings")
                }
                if (!savedMessage.isNullOrBlank()) {
                    Text(savedMessage.orEmpty())
                }
                if (!ownerAccess) {
                    Text(
                        text = "Mode kasir: pengaturan struk hanya bisa diubah owner.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                OutlinedTextField(
                    value = serverBaseUrl,
                    onValueChange = { serverBaseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server Base URL (Optional)") },
                    placeholder = { Text("http://10.0.2.2:8080") },
                )
                OutlinedTextField(
                    value = autoTaxPercent,
                    onValueChange = { autoTaxPercent = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tax % (Auto)") },
                    placeholder = { Text("11") },
                )
                OutlinedTextField(
                    value = autoServicePercent,
                    onValueChange = { autoServicePercent = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Service % (Auto)") },
                    placeholder = { Text("10") },
                )
                OutlinedTextField(
                    value = autoRounding,
                    onValueChange = { autoRounding = it.filter { c -> c.isDigit() || c == '-' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Rounding (Auto, Rp)") },
                    placeholder = { Text("0") },
                )
                Button(
                    onClick = {
                        if (!ownerAccess) {
                            savedMessage = "Hanya Owner yang boleh ubah koneksi server."
                            return@Button
                        }
                        val previousBaseUrl = settingsRepository.getOptionalServerBaseUrl().orEmpty()
                        val nextBaseUrl = serverBaseUrl.trim()
                        val baseUrlSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_SERVER_BASE_URL,
                            nextBaseUrl,
                        )
                        val taxSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_AUTO_TAX_PERCENT,
                            autoTaxPercent.ifBlank { "11" },
                        )
                        val serviceSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_AUTO_SERVICE_PERCENT,
                            autoServicePercent.ifBlank { "10" },
                        )
                        val roundingSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_AUTO_ROUNDING,
                            autoRounding.ifBlank { "0" },
                        )
                        if (previousBaseUrl != nextBaseUrl) {
                            settingsRepository.clearServerAuthSession()
                        }
                        savedMessage = if (
                            baseUrlSaved &&
                            taxSaved &&
                            serviceSaved &&
                            roundingSaved
                        ) {
                            "Saved server settings"
                        } else {
                            "Failed to save server settings"
                        }
                    },
                    enabled = ownerAccess,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Connectivity Settings")
                }
                if (serverAuthRepository != null) {
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it.uppercase().take(16) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Pairing Code (From Server Admin)") },
                        placeholder = { Text("Masukkan pairing code dari panel server") },
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                runSyncAction("pair_session") {
                                    val baseUrl = requireBaseUrl()
                                    val active = settingsRepository.getActiveUserSession()
                                        ?: error("Login dulu sebelum pairing server.")
                                    val existingBearer = settingsRepository
                                        .getActiveUserServerApiBearerToken(currentOutletId())
                                        ?.trim()
                                        .orEmpty()
                                    if (pairingCode.isBlank() && existingBearer.isBlank()) {
                                        error("Pairing code dari server admin wajib untuk aktivasi session pertama.")
                                    }
                                    val session = serverAuthRepository.bootstrapSessionForActiveUser(
                                        baseUrl = baseUrl,
                                        outletId = currentOutletId(),
                                        pairingCode = pairingCode.trim().ifBlank { null },
                                    ) ?: error("Gagal membuat session server.")
                                    pairingCode = ""
                                    "Server session aktif: ${session.role} • ${session.outletId}"
                                }
                            }
                        },
                        enabled = !syncBusy && hasServerConfigured,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ActionButtonLabel(
                            label = "Pair Active User to Server",
                            loadingLabel = "Pairing...",
                            loading = syncBusyAction == "pair_session",
                        )
                    }
                }
                Text(
                    text = "Leave base URL blank for local-only mode. Pairing code dibuat dari server admin, mobile hanya redeem.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (menuSyncRepository != null || orderSyncRepository != null || transaksiSyncRepository != null) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    AppSectionHeader("Manual Sync", "Sync mobile, server, and web data")
                    Button(
                        onClick = {
                            scope.launch {
                                runSyncAction("sync_all") {
                                    val baseUrl = requireBaseUrl()
                                    val results = mutableListOf<String>()
                                    transaksiSyncRepository?.let {
                                        val flushed = it.flushPending(baseUrl, currentOutletId())
                                        results += "transaksi_flush:$flushed"
                                    }
                                    menuSyncRepository?.let {
                                        if (ownerAccess) {
                                            results += uploadPendingMenuImagesForSync(baseUrl, currentOutletId())
                                            val pushedMenu = it.pushToServer(baseUrl, currentOutletId())
                                            results += "menu_push:$pushedMenu"
                                        } else {
                                            results += "menu_push:skipped(owner_only)"
                                        }
                                        val pulledMenu = it.pullFromServer(baseUrl, currentOutletId())
                                        results += "menu_pull:$pulledMenu"
                                    }
                                    orderSyncRepository?.let {
                                        val pulledOrders = it.pullOrders(baseUrl, currentOutletId())
                                        results += "orders_pull:$pulledOrders"
                                    }
                                    "Sync all done (${results.joinToString(", ")})"
                                }
                            }
                        },
                        enabled = !syncBusy && hasServerConfigured,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ActionButtonLabel(
                            label = "Sync All Now",
                            loadingLabel = "Syncing...",
                            loading = syncBusyAction == "sync_all",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        if (orderSyncRepository != null) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        runSyncAction("pull_orders") {
                                            val pulled = orderSyncRepository.pullOrders(requireBaseUrl(), currentOutletId())
                                            "Pulled $pulled order(s)"
                                        }
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured,
                            ) {
                                ActionButtonLabel(
                                    label = "Pull Orders",
                                    loadingLabel = "Pulling...",
                                    loading = syncBusyAction == "pull_orders",
                                )
                            }
                        }
                        if (transaksiSyncRepository != null) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        runSyncAction("flush_transaksi") {
                                            val flushed = transaksiSyncRepository.flushPending(requireBaseUrl(), currentOutletId())
                                            "Flushed $flushed transaksi event(s)"
                                        }
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured,
                            ) {
                                ActionButtonLabel(
                                    label = "Flush Transaksi",
                                    loadingLabel = "Flushing...",
                                    loading = syncBusyAction == "flush_transaksi",
                                )
                            }
                        }
                    }
                    if (menuSyncRepository != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        runSyncAction("pull_menu") {
                                            val pulled = menuSyncRepository.pullFromServer(requireBaseUrl(), currentOutletId())
                                            "Pulled $pulled menu item(s)"
                                        }
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured,
                            ) {
                                ActionButtonLabel(
                                    label = "Pull Menu",
                                    loadingLabel = "Pulling...",
                                    loading = syncBusyAction == "pull_menu",
                                )
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        runSyncAction("push_menu") {
                                            requireOwnerAccessOrFail()
                                            val baseUrl = requireBaseUrl()
                                            val outlet = currentOutletId()
                                            val imageResult = uploadPendingMenuImagesForSync(baseUrl, outlet)
                                            val pushed = menuSyncRepository.pushToServer(baseUrl, outlet)
                                            "Pushed $pushed menu item(s), $imageResult"
                                        }
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured && ownerAccess,
                            ) {
                                ActionButtonLabel(
                                    label = "Push Menu",
                                    loadingLabel = "Pushing...",
                                    loading = syncBusyAction == "push_menu",
                                )
                            }
                        }
                    }
                    if (menuSyncRepository != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        runSyncAction("force_align") {
                                            requireOwnerAccessOrFail()
                                            val baseUrl = requireBaseUrl()
                                            val outlet = currentOutletId()
                                            val flushed = transaksiSyncRepository?.flushPending(baseUrl, outlet) ?: 0
                                            val imageResult = uploadPendingMenuImagesForSync(baseUrl, outlet)
                                            val pushed = menuSyncRepository.pushToServer(baseUrl, outlet)
                                            val pulled = menuSyncRepository.pullFromServer(baseUrl, outlet)
                                            val pulledOrders = orderSyncRepository?.pullOrders(baseUrl, outlet) ?: 0
                                            "Aligned data ($imageResult, trx_flush:$flushed, menu_push:$pushed, menu_pull:$pulled, orders_pull:$pulledOrders)"
                                        }
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured && ownerAccess,
                                modifier = Modifier.weight(1f),
                            ) {
                                ActionButtonLabel(
                                    label = "Force Align Data",
                                    loadingLabel = "Aligning...",
                                    loading = syncBusyAction == "force_align",
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val success = runSyncAction("reset_local") {
                                            requireOwnerAccessOrFail()
                                            val outletBeforeReset = currentOutletId()
                                            settingsRepository.resetAllLocal(outletBeforeReset)
                                            "Local reset complete. Returning to setup."
                                        }
                                        if (success) {
                                            onRequireLocalSetup()
                                        }
                                    }
                                },
                                enabled = !syncBusy && ownerAccess,
                                modifier = Modifier.weight(1f),
                            ) {
                                ActionButtonLabel(
                                    label = "Reset Local Data",
                                    loadingLabel = "Resetting...",
                                    loading = syncBusyAction == "reset_local",
                                )
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        runSyncAction("reset_server") {
                                            requireOwnerAccessOrFail()
                                            val baseUrl = requireBaseUrl()
                                            val outletBeforeReset = currentOutletId()
                                            menuSyncRepository.resetServerAllData(baseUrl, outletBeforeReset)
                                            "Server reset complete."
                                        }
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured && ownerAccess,
                                modifier = Modifier.weight(1f),
                            ) {
                                ActionButtonLabel(
                                    label = "Reset Server Data",
                                    loadingLabel = "Resetting...",
                                    loading = syncBusyAction == "reset_server",
                                )
                            }
                        }
                    }
                    if (!hasServerConfigured) {
                        Text(
                            "Server belum dipasang di device ini. Sync dan reset server akan aktif setelah URL server disimpan. Reset local tetap akan mengembalikan tablet ke setup awal.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!ownerAccess) {
                        Text(
                            "Mode kasir: push menu, force align, reset data, dan ubah koneksi hanya untuk owner.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            Button(
                onClick = onOpenCashFlow,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cash Flow")
            }
            Button(
                onClick = onOpenStock,
                modifier = Modifier.weight(1f),
            ) {
                Text("Stock")
            }
            Button(
                onClick = onOpenCashClosing,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cash Closing")
            }
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Dimens.md),
        )
    }
}

private fun isLocalDeviceImageUri(value: String?): Boolean {
    val normalized = value?.trim().orEmpty()
    return normalized.startsWith("content://", ignoreCase = true) ||
        normalized.startsWith("file://", ignoreCase = true)
}

private fun notificationLevelForMessage(message: String): AppNotificationLevel {
    val value = message.lowercase()
    return when {
        "failed" in value || "error" in value || "gagal" in value -> AppNotificationLevel.ERROR
        "warning" in value || "timeout" in value || "offline" in value || "local" in value -> AppNotificationLevel.WARNING
        else -> AppNotificationLevel.INFO
    }
}

@Composable
private fun ActionButtonLabel(
    label: String,
    loading: Boolean,
    loadingLabel: String = "Loading...",
) {
    if (loading) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
            Text(loadingLabel)
        }
    } else {
        Text(label)
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    var storeName by remember { mutableStateOf("SuCash") }
    var storeAddress by remember { mutableStateOf("Jl. Preview No. 12") }
    var serverBaseUrl by remember { mutableStateOf("http://10.0.2.2:8080") }

    AppTheme {
        Column(
            modifier = Modifier
                .padding(Dimens.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            AppSectionHeader("Settings", "Receipt and local app configuration")
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Store Name") },
                    )
                    OutlinedTextField(
                        value = storeAddress,
                        onValueChange = { storeAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Address / Phone") },
                    )
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("Save Receipt Settings")
                    }
                }
            }
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    OutlinedTextField(
                        value = serverBaseUrl,
                        onValueChange = { serverBaseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Server Base URL") },
                    )
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("Save Server Settings")
                    }
                }
            }
        }
    }
}
