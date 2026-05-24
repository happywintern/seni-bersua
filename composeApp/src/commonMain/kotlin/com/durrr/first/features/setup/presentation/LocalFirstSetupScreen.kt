package com.durrr.first.features.setup.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.durrr.first.data.repo.CashSessionRepository
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.ReceiptConfig
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppInfoLine
import com.durrr.first.ui.design.AppPageContainer
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.AppStatusPill
import com.durrr.first.ui.design.Dimens

private data class SetupCategoryDraft(
    val name: String = "",
)

private enum class SetupWizardPath {
    LOCAL_ONLY,
    CONNECT_SERVER,
}

private enum class SetupWizardStep {
    PATH,
    DETAILS,
    SERVER,
    REVIEW,
}

@Composable
fun LocalFirstSetupScreen(
    settingsRepository: SettingsRepository,
    menuRepository: MenuRepository,
    cashSessionRepository: CashSessionRepository,
    nowIso: () -> String,
    onComplete: () -> Unit,
) {
    var storeName by remember { mutableStateOf("") }
    var storeAddress by remember { mutableStateOf("") }
    var outletId by remember { mutableStateOf(SettingsRepository.DEFAULT_OUTLET_ID) }
    var ownerName by remember { mutableStateOf("") }
    var ownerPin by remember { mutableStateOf("") }
    var ownerPinConfirm by remember { mutableStateOf("") }
    var cashierId by remember { mutableStateOf("") }
    var cashierName by remember { mutableStateOf("") }
    var cashierPin by remember { mutableStateOf("") }
    var cashierPinConfirm by remember { mutableStateOf("") }
    var ownerPinVisible by remember { mutableStateOf(false) }
    var ownerPinConfirmVisible by remember { mutableStateOf(false) }
    var cashierPinVisible by remember { mutableStateOf(false) }
    var cashierPinConfirmVisible by remember { mutableStateOf(false) }
    var autoTaxPercent by remember { mutableStateOf("11") }
    var autoServicePercent by remember { mutableStateOf("10") }
    var autoRounding by remember { mutableStateOf("0") }
    var openingCashText by remember { mutableStateOf("0") }
    var wizardPath by remember { mutableStateOf(SetupWizardPath.LOCAL_ONLY) }
    var wizardStep by remember { mutableStateOf(SetupWizardStep.PATH) }
    var serverBaseUrl by remember { mutableStateOf("") }
    var starterCategoryDrafts by remember {
        mutableStateOf(defaultStarterCategoryNames().map { SetupCategoryDraft(it) })
    }
    var openCashSessionNow by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val config = settingsRepository.loadReceiptConfig()
        storeName = config.storeName.ifBlank { "SuCash" }
        storeAddress = config.storeAddressOrPhone
        outletId = settingsRepository.resolveOutletId()
        ownerName = settingsRepository.getValue(SettingsRepository.KEY_OWNER_NAME)
            .ifBlank { "Owner" }
        cashierId = settingsRepository.getValue(SettingsRepository.KEY_DEFAULT_CASHIER_ID)
        cashierName = settingsRepository.getValue(SettingsRepository.KEY_DEFAULT_CASHIER_NAME)
            .ifBlank { "Cashier 1" }
        autoTaxPercent = settingsRepository.getValue(SettingsRepository.KEY_AUTO_TAX_PERCENT)
            .ifBlank { "11" }
        autoServicePercent = settingsRepository.getValue(SettingsRepository.KEY_AUTO_SERVICE_PERCENT)
            .ifBlank { "10" }
        autoRounding = settingsRepository.getValue(SettingsRepository.KEY_AUTO_ROUNDING)
            .ifBlank { "0" }
        serverBaseUrl = settingsRepository.getValue(SettingsRepository.KEY_SERVER_BASE_URL)
        ownerPin = settingsRepository.getValue(SettingsRepository.KEY_OWNER_PIN)
            .filter(Char::isDigit)
            .take(6)
        ownerPinConfirm = ownerPin
        cashierPin = settingsRepository.getValue(SettingsRepository.KEY_DEFAULT_CASHIER_PIN)
            .filter(Char::isDigit)
            .take(6)
        cashierPinConfirm = cashierPin
    }

    val validationError = setupValidationError(
        storeName = storeName,
        ownerName = ownerName,
        ownerPin = ownerPin,
        ownerPinConfirm = ownerPinConfirm,
        cashierName = cashierName,
        cashierPin = cashierPin,
        cashierPinConfirm = cashierPinConfirm,
    )
    val connectToServer = wizardPath == SetupWizardPath.CONNECT_SERVER
    val canContinueFromDetails = validationError == null

    AppPageContainer {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 980.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = Dimens.contentMaxWidth)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                        AppSectionHeader(
                            title = "Setup Wizard",
                            subtitle = "Ikuti langkah singkat ini supaya onboarding cepat dan jelas.",
                        )
                        AppStatusPill(
                            label = when (wizardStep) {
                                SetupWizardStep.PATH -> "Step 1/4 · Mode"
                                SetupWizardStep.DETAILS -> "Step 2/4 · Data Toko"
                                SetupWizardStep.SERVER -> "Step 3/4 · Koneksi Server"
                                SetupWizardStep.REVIEW -> "Step 4/4 · Review"
                            },
                        )
                        if (wide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                            ) {
                                SetupPathChoiceCard(
                                    title = "Local Only",
                                    subtitle = "Langsung transaksi offline dulu. Server bisa disambung nanti.",
                                    selected = wizardPath == SetupWizardPath.LOCAL_ONLY,
                                    onSelect = { wizardPath = SetupWizardPath.LOCAL_ONLY },
                                    modifier = Modifier.weight(1f),
                                )
                                SetupPathChoiceCard(
                                    title = "Connect to Server",
                                    subtitle = "Sekalian isi URL server saat setup, lalu pairing setelah login.",
                                    selected = wizardPath == SetupWizardPath.CONNECT_SERVER,
                                    onSelect = { wizardPath = SetupWizardPath.CONNECT_SERVER },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                                SetupPathChoiceCard(
                                    title = "Local Only",
                                    subtitle = "Transaksi offline dulu, pairing belakangan.",
                                    selected = wizardPath == SetupWizardPath.LOCAL_ONLY,
                                    onSelect = { wizardPath = SetupWizardPath.LOCAL_ONLY },
                                )
                                SetupPathChoiceCard(
                                    title = "Connect to Server",
                                    subtitle = "Isi URL server saat setup, pairing setelah login.",
                                    selected = wizardPath == SetupWizardPath.CONNECT_SERVER,
                                    onSelect = { wizardPath = SetupWizardPath.CONNECT_SERVER },
                                )
                            }
                        }
                        SetupQuickInfo(
                            title = "Panduan langkah",
                            lines = listOf(
                                "Step 1: pilih mode Local Only atau Connect to Server",
                                "Step 2: isi data toko + akun lokal",
                                "Step 3: (opsional) isi URL server",
                                "Step 4: review lalu simpan",
                            ),
                        )
                    }
                }

                if (wizardStep == SetupWizardStep.DETAILS) {
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                        ) {
                            SetupOutletCard(
                                storeName = storeName,
                                onStoreNameChange = { storeName = it },
                                storeAddress = storeAddress,
                                onStoreAddressChange = { storeAddress = it },
                                outletId = outletId,
                                onOutletIdChange = { outletId = it },
                                modifier = Modifier.weight(1f),
                            )
                            SetupDefaultsCard(
                                autoTaxPercent = autoTaxPercent,
                                onAutoTaxPercentChange = { autoTaxPercent = it.filter(Char::isDigit) },
                                autoServicePercent = autoServicePercent,
                                onAutoServicePercentChange = { autoServicePercent = it.filter(Char::isDigit) },
                                autoRounding = autoRounding,
                                onAutoRoundingChange = {
                                    autoRounding = it.filter { char -> char.isDigit() || char == '-' }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        SetupOutletCard(
                            storeName = storeName,
                            onStoreNameChange = { storeName = it },
                            storeAddress = storeAddress,
                            onStoreAddressChange = { storeAddress = it },
                            outletId = outletId,
                            onOutletIdChange = { outletId = it },
                        )
                        SetupDefaultsCard(
                            autoTaxPercent = autoTaxPercent,
                            onAutoTaxPercentChange = { autoTaxPercent = it.filter(Char::isDigit) },
                            autoServicePercent = autoServicePercent,
                            onAutoServicePercentChange = { autoServicePercent = it.filter(Char::isDigit) },
                            autoRounding = autoRounding,
                            onAutoRoundingChange = {
                                autoRounding = it.filter { char -> char.isDigit() || char == '-' }
                            },
                        )
                    }

                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                            AppSectionHeader("Akun Lokal", "Pisahkan akses owner dan staff agar transaksi lebih rapi.")
                            if (wide) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                                ) {
                                    AccountRoleCard(
                                        title = "Owner",
                                        subtitle = "Untuk pengaturan penting dan kontrol perangkat.",
                                        name = ownerName,
                                        onNameChange = { ownerName = it },
                                        nameLabel = "Owner Name",
                                        pin = ownerPin,
                                        onPinChange = { ownerPin = it.filter(Char::isDigit).take(6) },
                                        confirmPin = ownerPinConfirm,
                                        onConfirmPinChange = { ownerPinConfirm = it.filter(Char::isDigit).take(6) },
                                        pinLabel = "Owner PIN",
                                        confirmPinLabel = "Confirm Owner PIN",
                                        pinVisible = ownerPinVisible,
                                        onTogglePinVisibility = { ownerPinVisible = !ownerPinVisible },
                                        confirmPinVisible = ownerPinConfirmVisible,
                                        onToggleConfirmPinVisibility = {
                                            ownerPinConfirmVisible = !ownerPinConfirmVisible
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AccountRoleCard(
                                        title = "Cashier / Staff",
                                        subtitle = "Dipakai saat transaksi, cash session, dan audit kasir.",
                                        name = cashierName,
                                        onNameChange = { cashierName = it },
                                        nameLabel = "Cashier / Staff Name",
                                        pin = cashierPin,
                                        onPinChange = { cashierPin = it.filter(Char::isDigit).take(6) },
                                        confirmPin = cashierPinConfirm,
                                        onConfirmPinChange = { cashierPinConfirm = it.filter(Char::isDigit).take(6) },
                                        pinLabel = "Cashier / Staff PIN",
                                        confirmPinLabel = "Confirm Cashier / Staff PIN",
                                        pinVisible = cashierPinVisible,
                                        onTogglePinVisibility = { cashierPinVisible = !cashierPinVisible },
                                        confirmPinVisible = cashierPinConfirmVisible,
                                        onToggleConfirmPinVisibility = {
                                            cashierPinConfirmVisible = !cashierPinConfirmVisible
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            } else {
                                AccountRoleCard(
                                    title = "Owner",
                                    subtitle = "Untuk pengaturan penting dan kontrol perangkat.",
                                    name = ownerName,
                                    onNameChange = { ownerName = it },
                                    nameLabel = "Owner Name",
                                    pin = ownerPin,
                                    onPinChange = { ownerPin = it.filter(Char::isDigit).take(6) },
                                    confirmPin = ownerPinConfirm,
                                    onConfirmPinChange = { ownerPinConfirm = it.filter(Char::isDigit).take(6) },
                                    pinLabel = "Owner PIN",
                                    confirmPinLabel = "Confirm Owner PIN",
                                    pinVisible = ownerPinVisible,
                                    onTogglePinVisibility = { ownerPinVisible = !ownerPinVisible },
                                    confirmPinVisible = ownerPinConfirmVisible,
                                    onToggleConfirmPinVisibility = {
                                        ownerPinConfirmVisible = !ownerPinConfirmVisible
                                    },
                                )
                                AccountRoleCard(
                                    title = "Cashier / Staff",
                                    subtitle = "Dipakai saat transaksi, cash session, dan audit kasir.",
                                    name = cashierName,
                                    onNameChange = { cashierName = it },
                                    nameLabel = "Cashier / Staff Name",
                                    pin = cashierPin,
                                    onPinChange = { cashierPin = it.filter(Char::isDigit).take(6) },
                                    confirmPin = cashierPinConfirm,
                                    onConfirmPinChange = { cashierPinConfirm = it.filter(Char::isDigit).take(6) },
                                    pinLabel = "Cashier / Staff PIN",
                                    confirmPinLabel = "Confirm Cashier / Staff PIN",
                                    pinVisible = cashierPinVisible,
                                    onTogglePinVisibility = { cashierPinVisible = !cashierPinVisible },
                                    confirmPinVisible = cashierPinConfirmVisible,
                                    onToggleConfirmPinVisibility = {
                                        cashierPinConfirmVisible = !cashierPinConfirmVisible
                                    },
                                )
                            }
                            SetupSupportNote("PIN lokal dipakai untuk owner dan kasir di tablet ini. Gunakan 4 sampai 6 digit angka.")
                        }
                    }

                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                        ) {
                            SetupOpeningCashCard(
                                openingCashText = openingCashText,
                                onOpeningCashChange = { openingCashText = it.filter(Char::isDigit) },
                                openCashSessionNow = openCashSessionNow,
                                onOpenCashSessionChange = { openCashSessionNow = it },
                                modifier = Modifier.weight(0.85f),
                            )
                            SetupStarterCategoriesCard(
                                starterCategoryDrafts = starterCategoryDrafts,
                                onCategoryChange = { index, value ->
                                    starterCategoryDrafts = starterCategoryDrafts.toMutableList().apply {
                                        set(index, starterCategoryDrafts[index].copy(name = value))
                                    }
                                },
                                onAddCategory = {
                                    starterCategoryDrafts = starterCategoryDrafts + SetupCategoryDraft()
                                },
                                onRemoveCategory = { index ->
                                    starterCategoryDrafts = starterCategoryDrafts.toMutableList().apply {
                                        removeAt(index)
                                    }
                                },
                                modifier = Modifier.weight(1.15f),
                            )
                        }
                    } else {
                        SetupOpeningCashCard(
                            openingCashText = openingCashText,
                            onOpeningCashChange = { openingCashText = it.filter(Char::isDigit) },
                            openCashSessionNow = openCashSessionNow,
                            onOpenCashSessionChange = { openCashSessionNow = it },
                        )
                        SetupStarterCategoriesCard(
                            starterCategoryDrafts = starterCategoryDrafts,
                            onCategoryChange = { index, value ->
                                starterCategoryDrafts = starterCategoryDrafts.toMutableList().apply {
                                    set(index, starterCategoryDrafts[index].copy(name = value))
                                }
                            },
                            onAddCategory = {
                                starterCategoryDrafts = starterCategoryDrafts + SetupCategoryDraft()
                            },
                            onRemoveCategory = { index ->
                                starterCategoryDrafts = starterCategoryDrafts.toMutableList().apply {
                                    removeAt(index)
                                }
                            },
                        )
                    }
                }

                if (wizardStep == SetupWizardStep.SERVER && connectToServer) {
                    SetupServerConnectCard(
                        serverBaseUrl = serverBaseUrl,
                        onServerBaseUrlChange = { serverBaseUrl = it.trim() },
                    )
                }

                if (wizardStep == SetupWizardStep.REVIEW) {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            AppSectionHeader(
                                "Siap Dipakai",
                                "Begitu disimpan, device ini langsung masuk ke mode kasir lokal.",
                            )
                            AppInfoLine(
                                "Mode",
                                if (connectToServer) "Local First + Connect Server" else "Local First",
                            )
                            AppInfoLine(
                                "Server",
                                if (connectToServer) {
                                    serverBaseUrl.ifBlank { "Belum diisi, nanti atur dari Pengaturan" }
                                } else {
                                    "Tidak dipakai saat setup"
                                },
                            )
                            AppInfoLine("Kategori starter", starterCategoryDrafts.count { it.name.isNotBlank() }.toString())
                            if (!validationError.isNullOrBlank()) {
                                Text(
                                    text = validationError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFB42318),
                                )
                            } else {
                                Text(
                                    text = "Semua data valid. Tinggal simpan untuk mulai pakai app.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF046C4E),
                                )
                            }
                            if (!message.isNullOrBlank()) {
                                Text(
                                    text = message.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (saving) return@Button
                            if (!validationError.isNullOrBlank()) {
                                message = validationError
                                return@Button
                            }
                            val finalStoreName = storeName.trim()
                            val finalOutletId = settingsRepository.resolveOutletId(outletId)
                            val finalOwnerName = ownerName.trim()
                            val finalCashierName = cashierName.trim()
                            val openingCash = openingCashText.toLongOrNull() ?: 0L
                            val starterCategories = starterCategoryDrafts
                                .map { it.name.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                            val finalCashierId = cashierId.ifBlank {
                                settingsRepository.ensureDefaultCashierId(finalCashierName)
                            }

                            saving = true
                            message = null
                            runCatching {
                                settingsRepository.saveReceiptConfig(
                                    ReceiptConfig(
                                        storeName = finalStoreName,
                                        storeAddressOrPhone = storeAddress.trim(),
                                        headerLogoPath = settingsRepository.getValue(SettingsRepository.KEY_STORE_LOGO),
                                        watermarkLogoPath = settingsRepository.getValue(SettingsRepository.KEY_WATERMARK_LOGO),
                                        footerText = settingsRepository.getValue(SettingsRepository.KEY_FOOTER_TEXT)
                                            .ifBlank { "Thank you" },
                                    ),
                                )
                                settingsRepository.upsert(SettingsRepository.KEY_OUTLET_ID, finalOutletId)
                                settingsRepository.upsert(SettingsRepository.KEY_OWNER_NAME, finalOwnerName)
                                settingsRepository.upsert(SettingsRepository.KEY_OWNER_PIN, ownerPin)
                                settingsRepository.upsert(SettingsRepository.KEY_DEFAULT_CASHIER_ID, finalCashierId)
                                settingsRepository.upsert(SettingsRepository.KEY_DEFAULT_CASHIER_NAME, finalCashierName)
                                settingsRepository.upsert(SettingsRepository.KEY_DEFAULT_CASHIER_PIN, cashierPin)
                                settingsRepository.upsert(SettingsRepository.KEY_AUTO_TAX_PERCENT, autoTaxPercent.ifBlank { "11" })
                                settingsRepository.upsert(SettingsRepository.KEY_AUTO_SERVICE_PERCENT, autoServicePercent.ifBlank { "10" })
                                settingsRepository.upsert(SettingsRepository.KEY_AUTO_ROUNDING, autoRounding.ifBlank { "0" })
                                settingsRepository.upsert(
                                    SettingsRepository.KEY_SETUP_MODE,
                                    SettingsRepository.SETUP_MODE_LOCAL_FIRST,
                                )
                                settingsRepository.upsert(
                                    SettingsRepository.KEY_SERVER_BASE_URL,
                                    if (connectToServer) serverBaseUrl.trim() else "",
                                )

                                if (starterCategories.isNotEmpty() && menuRepository.getGroups(finalOutletId).isEmpty()) {
                                    starterGroups(finalOutletId, starterCategories).forEach {
                                        menuRepository.upsertGroup(it, finalOutletId)
                                    }
                                }

                                if (openCashSessionNow && cashSessionRepository.getActiveSession(finalOutletId) == null) {
                                    cashSessionRepository.openSession(
                                        outletId = finalOutletId,
                                        openingCash = openingCash,
                                        userId = finalCashierName,
                                        openedAt = nowIso(),
                                    )
                                }

                                settingsRepository.markLocalSetupCompleted(true)
                            }.onFailure {
                                message = "Setup failed: ${it.message ?: "Unknown error"}"
                            }.onSuccess {
                                message = "Tablet siap dipakai."
                                onComplete()
                            }
                            saving = false
                        },
                        enabled = !saving && validationError == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (saving) "Saving..." else "Save and Start Using App")
                    }
                }

                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (wizardStep != SetupWizardStep.PATH) {
                                TextButton(
                                    onClick = {
                                        wizardStep = when (wizardStep) {
                                            SetupWizardStep.DETAILS -> SetupWizardStep.PATH
                                            SetupWizardStep.SERVER -> SetupWizardStep.DETAILS
                                            SetupWizardStep.REVIEW -> if (connectToServer) SetupWizardStep.SERVER else SetupWizardStep.DETAILS
                                            SetupWizardStep.PATH -> SetupWizardStep.PATH
                                        }
                                    },
                                ) {
                                    Text("Back")
                                }
                            }
                            Button(
                                onClick = {
                                    wizardStep = when (wizardStep) {
                                        SetupWizardStep.PATH -> SetupWizardStep.DETAILS
                                        SetupWizardStep.DETAILS -> {
                                            if (!canContinueFromDetails) {
                                                message = validationError
                                                SetupWizardStep.DETAILS
                                            } else if (connectToServer) {
                                                SetupWizardStep.SERVER
                                            } else {
                                                SetupWizardStep.REVIEW
                                            }
                                        }
                                        SetupWizardStep.SERVER -> SetupWizardStep.REVIEW
                                        SetupWizardStep.REVIEW -> SetupWizardStep.REVIEW
                                    }
                                },
                                enabled = wizardStep != SetupWizardStep.REVIEW,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    when (wizardStep) {
                                        SetupWizardStep.PATH -> "Continue"
                                        SetupWizardStep.DETAILS -> if (connectToServer) "Continue to Server" else "Continue to Review"
                                        SetupWizardStep.SERVER -> "Continue to Review"
                                        SetupWizardStep.REVIEW -> "Ready to Save"
                                    }
                                )
                            }
                        }
                        if (!message.isNullOrBlank()) {
                            Text(
                                text = message.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupOutletCard(
    storeName: String,
    onStoreNameChange: (String) -> Unit,
    storeAddress: String,
    onStoreAddressChange: (String) -> Unit,
    outletId: String,
    onOutletIdChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            AppSectionHeader("Outlet", "Identitas toko untuk perangkat ini")
            OutlinedTextField(
                value = storeName,
                onValueChange = onStoreNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Store Name") },
                singleLine = true,
            )
            OutlinedTextField(
                value = storeAddress,
                onValueChange = onStoreAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Address / Phone") },
            )
            OutlinedTextField(
                value = outletId,
                onValueChange = onOutletIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Outlet ID") },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun SetupDefaultsCard(
    autoTaxPercent: String,
    onAutoTaxPercentChange: (String) -> Unit,
    autoServicePercent: String,
    onAutoServicePercentChange: (String) -> Unit,
    autoRounding: String,
    onAutoRoundingChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            AppSectionHeader("Defaults", "Pajak dan service default untuk checkout")
            if (autoTaxPercent.isNotBlank() || autoServicePercent.isNotBlank() || autoRounding.isNotBlank()) {
                SetupQuickInfo(
                    title = "Snapshot",
                    lines = listOf(
                        "Tax ${autoTaxPercent.ifBlank { "0" }}%",
                        "Service ${autoServicePercent.ifBlank { "0" }}%",
                        "Rounding Rp ${autoRounding.ifBlank { "0" }}",
                    ),
                )
            }
            OutlinedTextField(
                value = autoTaxPercent,
                onValueChange = onAutoTaxPercentChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tax %") },
                singleLine = true,
            )
            OutlinedTextField(
                value = autoServicePercent,
                onValueChange = onAutoServicePercentChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Service %") },
                singleLine = true,
            )
            OutlinedTextField(
                value = autoRounding,
                onValueChange = onAutoRoundingChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Rounding (Rp)") },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun SetupOpeningCashCard(
    openingCashText: String,
    onOpeningCashChange: (String) -> Unit,
    openCashSessionNow: Boolean,
    onOpenCashSessionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            AppSectionHeader("Saldo Awal", "Opsional kalau mau langsung buka sesi kas lokal")
            SetupQuickInfo(
                title = "Tip",
                lines = listOf(
                    "Kalau belum ada modal kasir, isi 0 dulu.",
                    "Saldo awal tetap bisa dibuka lagi setelah setup selesai.",
                ),
            )
            OutlinedTextField(
                value = openingCashText,
                onValueChange = onOpeningCashChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Opening Cash") },
                singleLine = true,
            )
            SetupSwitchRow(
                title = "Open cash session now",
                subtitle = "Langsung buka sesi kas lokal setelah setup selesai.",
                checked = openCashSessionNow,
                onCheckedChange = onOpenCashSessionChange,
            )
        }
    }
}

@Composable
private fun SetupStarterCategoriesCard(
    starterCategoryDrafts: List<SetupCategoryDraft>,
    onCategoryChange: (Int, String) -> Unit,
    onAddCategory: () -> Unit,
    onRemoveCategory: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            AppSectionHeader("Starter Categories", "Custom kategori awal seperti input add modifier.")
            SetupQuickInfo(
                title = "Contoh penggunaan",
                lines = listOf(
                    "Pisahkan kategori minuman, makanan, dan dessert",
                    "Kosongkan bila ingin setup menu sepenuhnya manual",
                ),
            )
            starterCategoryDrafts.forEachIndexed { index, draft ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { value -> onCategoryChange(index, value) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Category ${index + 1}") },
                        singleLine = true,
                    )
                    if (starterCategoryDrafts.size > 1) {
                        IconButton(onClick = { onRemoveCategory(index) }) {
                            Text("X", color = Color(0xFFB42318))
                        }
                    }
                }
            }
            Button(
                onClick = onAddCategory,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("+ Tambah Kategori")
            }
            SetupSupportNote("Kosongkan kalau mau mulai tanpa kategori bawaan.")
        }
    }
}

@Composable
private fun SetupPathChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    SetupInsetCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            AppStatusPill(
                label = if (selected) "Selected" else "Tap to Choose",
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                border = BorderStroke(1.dp, borderColor),
                shape = MaterialTheme.shapes.medium,
            ) {
                TextButton(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selected) "Selected" else "Use This Path")
                }
            }
        }
    }
}

@Composable
private fun SetupServerConnectCard(
    serverBaseUrl: String,
    onServerBaseUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            AppSectionHeader(
                title = "Connect to Server",
                subtitle = "Opsional saat setup. Kalau kosong, tetap Local First.",
            )
            OutlinedTextField(
                value = serverBaseUrl,
                onValueChange = onServerBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server Base URL") },
                placeholder = { Text("http://10.0.2.2:8080") },
                singleLine = true,
            )
            SetupQuickInfo(
                title = "Setelah setup",
                lines = listOf(
                    "Login owner di mobile",
                    "Masukkan pairing code dari Server Admin",
                    "Session server aktif lalu sync bisa jalan",
                ),
            )
        }
    }
}

@Composable
private fun AccountRoleCard(
    title: String,
    subtitle: String,
    name: String,
    onNameChange: (String) -> Unit,
    nameLabel: String,
    pin: String,
    onPinChange: (String) -> Unit,
    confirmPin: String,
    onConfirmPinChange: (String) -> Unit,
    pinLabel: String,
    confirmPinLabel: String,
    pinVisible: Boolean,
    onTogglePinVisibility: () -> Unit,
    confirmPinVisible: Boolean,
    onToggleConfirmPinVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SetupInsetCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            AppSectionHeader(title, subtitle)
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(nameLabel) },
                singleLine = true,
            )
            PinField(
                value = pin,
                onValueChange = onPinChange,
                label = pinLabel,
                visible = pinVisible,
                onToggleVisibility = onTogglePinVisibility,
            )
            PinField(
                value = confirmPin,
                onValueChange = onConfirmPinChange,
                label = confirmPinLabel,
                visible = confirmPinVisible,
                onToggleVisibility = onToggleConfirmPinVisibility,
            )
        }
    }
}

@Composable
private fun SetupQuickInfo(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    SetupInsetCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            lines.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetupSupportNote(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SetupInsetCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            content()
        }
    }
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggleVisibility) {
                Text(if (visible) "Hide" else "Show")
            }
        },
        supportingText = {
            Text("${value.length}/6 digit")
        },
    )
}

@Composable
private fun SetupSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(end = Dimens.sm),
            verticalArrangement = Arrangement.spacedBy(Dimens.xxs),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun starterGroups(outletId: String): List<GroupItem> {
    return starterGroups(outletId, defaultStarterCategoryNames())
}

private fun starterGroups(
    outletId: String,
    names: List<String>,
): List<GroupItem> {
    val usedIds = mutableListOf<String>()
    return names.mapIndexed { index, name ->
        val id = IdGenerator.newCategoryId(
            categoryName = name,
            existingGroupIds = usedIds,
        )
        usedIds += id
        GroupItem(
            id = id,
            name = name,
            order = index + 1,
            outletId = outletId,
        )
    }
}

private fun defaultStarterCategoryNames(): List<String> {
    return listOf("Kopi & Teh", "Non-Coffee", "Snack")
}

private fun isValidSetupPin(pin: String): Boolean {
    return pin.length in 4..6 && pin.all(Char::isDigit)
}

private fun setupValidationError(
    storeName: String,
    ownerName: String,
    ownerPin: String,
    ownerPinConfirm: String,
    cashierName: String,
    cashierPin: String,
    cashierPinConfirm: String,
): String? {
    if (storeName.trim().isBlank()) return "Store name wajib diisi."
    if (ownerName.trim().isBlank()) return "Owner name wajib diisi."
    if (!isValidSetupPin(ownerPin)) return "Owner PIN harus 4 sampai 6 digit angka."
    if (ownerPin != ownerPinConfirm) return "Konfirmasi Owner PIN belum sama."
    if (cashierName.trim().isBlank()) return "Cashier / staff name wajib diisi."
    if (!isValidSetupPin(cashierPin)) return "Cashier / staff PIN harus 4 sampai 6 digit angka."
    if (cashierPin != cashierPinConfirm) return "Konfirmasi Cashier / staff PIN belum sama."
    return null
}
