package com.durrr.first.features.transaction.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.TransaksiRepository
import com.durrr.first.data.repo.TransaksiSyncRepository
import com.durrr.first.domain.model.ModifierGroupBundle
import com.durrr.first.domain.model.Pembayaran
import com.durrr.first.domain.model.Transaksi
import com.durrr.first.domain.model.TransaksiDetail
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.domain.service.TotalsCalculator
import com.durrr.first.features.cart.domain.OrderDraftLine
import com.durrr.first.features.cart.domain.OrderDraftModifierSelection
import com.durrr.first.features.cart.domain.OrderBuilderCartStore
import com.durrr.first.features.cart.domain.OrderDraftStore
import com.durrr.first.features.product.domain.BundleDraft
import com.durrr.first.features.product.domain.BundleItemInput
import com.durrr.first.features.recommendation.domain.PromoDraft
import com.durrr.first.features.transaction.domain.ReceiptDraftStore
import com.durrr.first.network.dto.PembayaranDto
import com.durrr.first.network.dto.TransaksiDetailDto
import com.durrr.first.network.dto.TransaksiDto
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppInfoLine
import com.durrr.first.ui.design.AppPageContainer
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.AppStatusPill
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch
import kotlin.math.min

private const val KEY_RECOMMENDATION_BUNDLES = "recommendation_bundles_v1"
private const val KEY_RECOMMENDATION_PROMOS = "recommendation_promos_v1"
private const val RECORD_SEPARATOR = "\u001E"
private const val FIELD_SEPARATOR = "\u001F"
private const val ITEM_SEPARATOR = "\u001D"
private const val ITEM_FIELD_SEPARATOR = "\u001C"

private enum class CheckoutPaymentMethod(
    val paymentTypeId: String,
    val label: String,
) {
    TUNAI(paymentTypeId = "CASH", label = "Tunai"),
    QRIS(paymentTypeId = "QRIS", label = "QRIS"),
}

@Composable
fun OrderCheckoutScreen(
    draftId: String,
    menuRepository: MenuRepository,
    transaksiRepository: TransaksiRepository,
    settingsRepository: SettingsRepository,
    transaksiSyncRepository: TransaksiSyncRepository,
    nowIso: () -> String,
    onBackToOrders: () -> Unit,
    onPreviewReceipt: (String) -> Unit,
) {
    var draft by remember(draftId) { mutableStateOf(OrderDraftStore.getDraft(draftId)) }
    var discountPlus by remember { mutableStateOf("0") }
    var taxPercent by remember { mutableStateOf(11L) }
    var servicePercent by remember { mutableStateOf(10L) }
    var roundingValue by remember { mutableStateOf(0L) }
    var paid by remember { mutableStateOf("0") }
    var paymentMethod by remember { mutableStateOf(CheckoutPaymentMethod.TUNAI) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }
    var editingBundles by remember { mutableStateOf(emptyList<ModifierGroupBundle>()) }
    var editingSelectionByGroup by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val totalsCalculator = remember { TotalsCalculator() }

    LaunchedEffect(Unit) {
        taxPercent = settingsRepository
            .getValue(SettingsRepository.KEY_AUTO_TAX_PERCENT)
            .toLongOrNull() ?: 11L
        servicePercent = settingsRepository
            .getValue(SettingsRepository.KEY_AUTO_SERVICE_PERCENT)
            .toLongOrNull() ?: 10L
        roundingValue = settingsRepository
            .getValue(SettingsRepository.KEY_AUTO_ROUNDING)
            .toLongOrNull() ?: 0L
    }

    val lines = draft?.lines.orEmpty()
    val subtotal = lines.sumOf { it.qty * it.price }
    val discountValue = discountPlus.toLongOrNull() ?: 0L
    val recommendationDiscount = remember(lines) {
        val bundles = decodeRecommendationBundles(
            settingsRepository.getValue(KEY_RECOMMENDATION_BUNDLES),
        )
        val promos = decodeRecommendationPromos(
            settingsRepository.getValue(KEY_RECOMMENDATION_PROMOS),
        )
        calculateRecommendationDiscount(
            lines = lines,
            bundles = bundles,
            promos = promos,
            referenceDate = nowIso().take(10),
        )
    }
    val effectiveDiscountValue = (discountValue + recommendationDiscount.totalDiscount).coerceAtLeast(0L)
    val pricedBase = (subtotal - effectiveDiscountValue).coerceAtLeast(0L)
    val taxValue = (pricedBase * taxPercent) / 100L
    val serviceChargeValue = (pricedBase * servicePercent) / 100L
    val manualPaidValue = paid.toLongOrNull() ?: 0L
    val baseTotals = totalsCalculator.calculate(
        lines = lines.map { TotalsCalculator.Line(it.qty, it.price, 0) },
        discountPlus = effectiveDiscountValue,
        tax = taxValue,
        serviceCharge = serviceChargeValue,
        rounding = roundingValue,
        paid = manualPaidValue,
    )
    val effectivePaidAmount = when (paymentMethod) {
        CheckoutPaymentMethod.TUNAI -> manualPaidValue
        CheckoutPaymentMethod.QRIS -> baseTotals.grandTotal
    }
    val totals = if (effectivePaidAmount == manualPaidValue) {
        baseTotals
    } else {
        totalsCalculator.calculate(
            lines = lines.map { TotalsCalculator.Line(it.qty, it.price, 0) },
            discountPlus = effectiveDiscountValue,
            tax = taxValue,
            serviceCharge = serviceChargeValue,
            rounding = roundingValue,
            paid = effectivePaidAmount,
        )
    }
    val isCashPaidTooLow = paymentMethod == CheckoutPaymentMethod.TUNAI &&
        effectivePaidAmount < totals.grandTotal

    fun serverBaseUrl(): String? = settingsRepository.getOptionalServerBaseUrl()

    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    fun currentCashierId(): String = settingsRepository.resolveCurrentCashierId()

    fun currentCashierName(): String = settingsRepository.resolveCurrentCashierName()

    fun lineDisplayName(line: OrderDraftLine): String {
        if (line.modifiers.isEmpty()) return line.itemName
        val summary = line.modifiers.joinToString(", ") { it.optionName }
        return "${line.itemName} [$summary]"
    }

    fun updateLineQty(index: Int, delta: Long) {
        val currentDraft = draft ?: return
        val currentLine = currentDraft.lines.getOrNull(index) ?: return
        val nextQty = currentLine.qty + delta
        val nextLines = currentDraft.lines.toMutableList()
        if (nextQty <= 0) {
            nextLines.removeAt(index)
        } else {
            nextLines[index] = currentLine.copy(qty = nextQty)
        }
        draft = currentDraft.copy(lines = nextLines)
    }

    fun openModifierEditor(index: Int) {
        val line = draft?.lines?.getOrNull(index) ?: return
        val itemId = line.itemId ?: run {
            syncMessage = "Item ini tidak punya itemId untuk modifier."
            return
        }
        val linkedGroupIds = menuRepository.getModifierGroupIdsForItem(itemId, currentOutletId())
        if (linkedGroupIds.isEmpty()) {
            syncMessage = "Produk ini belum punya modifier group."
            return
        }
        val bundles = menuRepository.getModifierGroupBundles(currentOutletId())
            .filter { linkedGroupIds.contains(it.group.id) }
        val currentSelections = line.modifiers.groupBy { it.groupId }
            .mapValues { entry -> entry.value.map { it.optionId }.toSet() }
        val initialized = bundles.associate { bundle ->
            val fromLine = currentSelections[bundle.group.id].orEmpty()
            val selection = if (fromLine.isNotEmpty()) {
                fromLine
            } else if (bundle.group.selectionType == "SINGLE") {
                bundle.options.firstOrNull { it.isDefault }?.let { setOf(it.id) } ?: emptySet()
            } else {
                emptySet()
            }
            bundle.group.id to selection
        }
        editingLineIndex = index
        editingBundles = bundles
        editingSelectionByGroup = initialized
    }

    fun applyModifierEditor() {
        val currentDraft = draft ?: return
        val index = editingLineIndex ?: return
        val currentLine = currentDraft.lines.getOrNull(index) ?: return
        val selectedModifiers = editingBundles.flatMap { bundle ->
            val selectedIds = editingSelectionByGroup[bundle.group.id].orEmpty()
            bundle.options.filter { selectedIds.contains(it.id) }.map { option ->
                OrderDraftModifierSelection(
                    groupId = bundle.group.id,
                    groupName = bundle.group.name,
                    optionId = option.id,
                    optionName = option.name,
                    priceDelta = option.priceDelta,
                )
            }
        }
        val nextPrice = currentLine.basePrice + selectedModifiers.sumOf { it.priceDelta }
        val nextLines = currentDraft.lines.toMutableList()
        nextLines[index] = currentLine.copy(
            price = nextPrice,
            modifiers = selectedModifiers,
        )
        draft = currentDraft.copy(lines = nextLines)
        editingLineIndex = null
        editingBundles = emptyList()
        editingSelectionByGroup = emptyMap()
    }

    if (draft == null) {
        AppPageContainer(modifier = Modifier.fillMaxSize()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                AppSectionHeader("Ringkasan Pembayaran")
                AppEmptyState(
                    title = "Order draft not found",
                    message = "Go back and create a new order first.",
                )
                Button(onClick = onBackToOrders, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Orders")
                }
            }
        }
        return
    }

    AppPageContainer(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints {
            val wideLayout = maxWidth >= 1180.dp
            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    CheckoutPreviewPane(
                        modifier = Modifier.weight(1.35f),
                        tableToken = draft?.tableToken,
                        lines = lines,
                        subtotal = totals.subtotal,
                        syncMessage = syncMessage,
                        onDecreaseQty = { updateLineQty(it, -1) },
                        onIncreaseQty = { updateLineQty(it, 1) },
                        onEditModifiers = ::openModifierEditor,
                        editingLineIndex = editingLineIndex,
                        editingBundles = editingBundles,
                        editingSelectionByGroup = editingSelectionByGroup,
                        onSelectionChange = { groupId, optionId, selected, selectionType, maxSelection ->
                            editingSelectionByGroup = editingSelectionByGroup.toMutableMap().also { map ->
                                val current = map[groupId].orEmpty()
                                map[groupId] = when {
                                    selectionType == "SINGLE" && selected -> setOf(optionId)
                                    selectionType == "SINGLE" && !selected -> emptySet()
                                    selected -> (current + optionId).take(maxSelection).toSet()
                                    else -> current - optionId
                                }
                            }
                        },
                        onCancelModifierEdit = {
                            editingLineIndex = null
                            editingBundles = emptyList()
                            editingSelectionByGroup = emptyMap()
                        },
                        onApplyModifierEdit = ::applyModifierEditor,
                    )
                    CheckoutPaymentPane(
                        modifier = Modifier.weight(0.95f),
                        discountPlus = discountPlus,
                        manualDiscountValue = discountValue,
                        autoRecommendationDiscount = recommendationDiscount.totalDiscount,
                        recommendationDiscountHint = recommendationDiscount.hint,
                        taxPercent = taxPercent,
                        taxValue = taxValue,
                        servicePercent = servicePercent,
                        serviceChargeValue = serviceChargeValue,
                        roundingValue = roundingValue,
                        paymentMethod = paymentMethod,
                        paid = paid,
                        effectivePaidAmount = effectivePaidAmount,
                        isCashPaidTooLow = isCashPaidTooLow,
                        minimumCashPaid = totals.grandTotal,
                        totals = totals,
                        processing = processing,
                        onPaymentMethodChange = { paymentMethod = it },
                        onDiscountChange = { discountPlus = it },
                        onPaidChange = { paid = it },
                        onPreview = {
                            val previewId = IdGenerator.newId("preview_")
                            val previewCreatedAt = nowIso()
                            val previewTransaksi = Transaksi(
                                id = previewId,
                                createdAt = previewCreatedAt,
                                meja = draft?.tableToken,
                                cashierId = currentCashierId(),
                                cashierName = currentCashierName(),
                                discountPlus = effectiveDiscountValue,
                                tax = taxValue,
                                serviceCharge = serviceChargeValue,
                                rounding = roundingValue,
                                total = totals.grandTotal,
                                outletId = currentOutletId(),
                            )
                            val previewDetails = lines.map { line ->
                                TransaksiDetail(
                                    id = IdGenerator.newId("trd_preview_"),
                                    transaksiId = previewId,
                                    itemId = line.itemId,
                                    itemName = lineDisplayName(line),
                                    qty = line.qty,
                                    price = line.price,
                                    discount = 0L,
                                    total = line.qty * line.price,
                                )
                            }
                            val previewPayment = Pembayaran(
                                id = IdGenerator.newId("pay_preview_"),
                                transaksiId = previewId,
                                paidAt = previewCreatedAt,
                                amountPaid = effectivePaidAmount,
                                change = totals.change,
                                paymentTypeId = paymentMethod.paymentTypeId,
                                outletId = currentOutletId(),
                            )
                            ReceiptDraftStore.putDraft(
                                draftId = previewId,
                                transaksi = previewTransaksi,
                                details = previewDetails,
                                pembayaran = previewPayment,
                            )
                            onPreviewReceipt(previewId)
                        },
                        onCheckout = {
                            if (processing) return@CheckoutPaymentPane
                            if (paymentMethod == CheckoutPaymentMethod.TUNAI && effectivePaidAmount < totals.grandTotal) {
                                syncMessage = "Tunai dibayar tidak boleh kurang dari Grand Total (${formatRupiah(totals.grandTotal)})."
                                return@CheckoutPaymentPane
                            }
                            val currentDraft = draft ?: return@CheckoutPaymentPane
                            scope.launch {
                                processing = true
                                syncMessage = null
                                runCatching {
                                    val transaksiId = IdGenerator.newId("trx_")
                                    val createdAt = nowIso()
                                    val header = Transaksi(
                                        id = transaksiId,
                                        createdAt = createdAt,
                                        meja = currentDraft.tableToken,
                                        cashierId = currentCashierId(),
                                        cashierName = currentCashierName(),
                                        discountPlus = effectiveDiscountValue,
                                        tax = taxValue,
                                        serviceCharge = serviceChargeValue,
                                        rounding = roundingValue,
                                        total = 0L,
                                        outletId = currentOutletId(),
                                    )
                                    transaksiRepository.createTransaksi(header)

                                    val details = currentDraft.lines.map { line ->
                                        val detail = TransaksiDetail(
                                            id = IdGenerator.newId("trd_"),
                                            transaksiId = transaksiId,
                                            itemId = line.itemId,
                                            itemName = lineDisplayName(line),
                                            qty = line.qty,
                                            price = line.price,
                                            discount = 0L,
                                            total = line.qty * line.price,
                                        )
                                        transaksiRepository.addDetail(detail)
                                        detail
                                    }

                                    val pembayaran = Pembayaran(
                                        id = IdGenerator.newId("pay_"),
                                        transaksiId = transaksiId,
                                        paidAt = createdAt,
                                        amountPaid = effectivePaidAmount,
                                        change = 0L,
                                        paymentTypeId = paymentMethod.paymentTypeId,
                                        outletId = currentOutletId(),
                                    )

                                    val checkoutTotals = transaksiRepository.checkoutCash(
                                        transaksiId = transaksiId,
                                        details = details,
                                        pembayaran = pembayaran,
                                        discountPlus = header.discountPlus,
                                        tax = header.tax,
                                        serviceCharge = header.serviceCharge,
                                        rounding = header.rounding,
                                        cashierId = header.cashierId,
                                        cashierName = header.cashierName,
                                    )

                                    val transaksiDto = TransaksiDto(
                                        id = transaksiId,
                                        createdAt = createdAt,
                                        meja = header.meja,
                                        cashierId = header.cashierId,
                                        cashierName = header.cashierName,
                                        discountPlus = header.discountPlus,
                                        tax = header.tax,
                                        serviceCharge = header.serviceCharge,
                                        rounding = header.rounding,
                                        total = checkoutTotals.grandTotal,
                                        details = details.map { detail ->
                                            TransaksiDetailDto(
                                                id = detail.id,
                                                itemId = detail.itemId,
                                                itemName = detail.itemName,
                                                qty = detail.qty,
                                                price = detail.price,
                                                discount = detail.discount,
                                                total = detail.total,
                                            )
                                        },
                                        pembayaran = PembayaranDto(
                                            id = pembayaran.id,
                                            paidAt = pembayaran.paidAt,
                                            amountPaid = pembayaran.amountPaid,
                                            change = checkoutTotals.change,
                                            paymentTypeId = pembayaran.paymentTypeId,
                                        ),
                                    )

                                    transaksiSyncRepository.enqueueCheckout(transaksiDto, currentOutletId())
                                    val baseUrl = serverBaseUrl()
                                    if (baseUrl == null) {
                                        syncMessage = "Checkout saved locally. Pair server later from Settings to sync."
                                    } else {
                                        runCatching {
                                            val synced = transaksiSyncRepository.flushPending(
                                                baseUrl = baseUrl,
                                                outletId = currentOutletId(),
                                            )
                                            "Checkout saved. Synced $synced event(s)."
                                        }.onFailure {
                                            syncMessage = "Checkout saved locally. Sync pending: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                    }

                                    OrderDraftStore.removeDraft(draftId)
                                    OrderBuilderCartStore.clearSnapshot(currentOutletId())
                                    draft = null
                                    onPreviewReceipt(transaksiId)
                                }.onFailure {
                                    syncMessage = "Checkout failed: ${it.message ?: "Unknown error"}"
                                }
                                processing = false
                            }
                        },
                        onBackToOrders = onBackToOrders,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    item {
                        CheckoutPreviewPane(
                            tableToken = draft?.tableToken,
                            lines = lines,
                            subtotal = totals.subtotal,
                            syncMessage = syncMessage,
                            onDecreaseQty = { updateLineQty(it, -1) },
                            onIncreaseQty = { updateLineQty(it, 1) },
                            onEditModifiers = ::openModifierEditor,
                            editingLineIndex = editingLineIndex,
                            editingBundles = editingBundles,
                            editingSelectionByGroup = editingSelectionByGroup,
                            onSelectionChange = { groupId, optionId, selected, selectionType, maxSelection ->
                                editingSelectionByGroup = editingSelectionByGroup.toMutableMap().also { map ->
                                    val current = map[groupId].orEmpty()
                                    map[groupId] = when {
                                        selectionType == "SINGLE" && selected -> setOf(optionId)
                                        selectionType == "SINGLE" && !selected -> emptySet()
                                        selected -> (current + optionId).take(maxSelection).toSet()
                                        else -> current - optionId
                                    }
                                }
                            },
                            onCancelModifierEdit = {
                                editingLineIndex = null
                                editingBundles = emptyList()
                                editingSelectionByGroup = emptyMap()
                            },
                            onApplyModifierEdit = ::applyModifierEditor,
                        )
                    }
                    item {
                        CheckoutPaymentPane(
                            discountPlus = discountPlus,
                            manualDiscountValue = discountValue,
                            autoRecommendationDiscount = recommendationDiscount.totalDiscount,
                            recommendationDiscountHint = recommendationDiscount.hint,
                            taxPercent = taxPercent,
                            taxValue = taxValue,
                            servicePercent = servicePercent,
                            serviceChargeValue = serviceChargeValue,
                            roundingValue = roundingValue,
                            paymentMethod = paymentMethod,
                            paid = paid,
                            effectivePaidAmount = effectivePaidAmount,
                            isCashPaidTooLow = isCashPaidTooLow,
                            minimumCashPaid = totals.grandTotal,
                            totals = totals,
                            processing = processing,
                            onPaymentMethodChange = { paymentMethod = it },
                            onDiscountChange = { discountPlus = it },
                            onPaidChange = { paid = it },
                            onPreview = {
                                val previewId = IdGenerator.newId("preview_")
                                val previewCreatedAt = nowIso()
                                val previewTransaksi = Transaksi(
                                    id = previewId,
                                    createdAt = previewCreatedAt,
                                    meja = draft?.tableToken,
                                    cashierId = currentCashierId(),
                                    cashierName = currentCashierName(),
                                    discountPlus = effectiveDiscountValue,
                                    tax = taxValue,
                                    serviceCharge = serviceChargeValue,
                                    rounding = roundingValue,
                                    total = totals.grandTotal,
                                    outletId = currentOutletId(),
                                )
                                val previewDetails = lines.map { line ->
                                    TransaksiDetail(
                                        id = IdGenerator.newId("trd_preview_"),
                                        transaksiId = previewId,
                                        itemId = line.itemId,
                                        itemName = lineDisplayName(line),
                                        qty = line.qty,
                                        price = line.price,
                                        discount = 0L,
                                        total = line.qty * line.price,
                                    )
                                }
                                val previewPayment = Pembayaran(
                                    id = IdGenerator.newId("pay_preview_"),
                                    transaksiId = previewId,
                                    paidAt = previewCreatedAt,
                                    amountPaid = effectivePaidAmount,
                                    change = totals.change,
                                    paymentTypeId = paymentMethod.paymentTypeId,
                                    outletId = currentOutletId(),
                                )
                                ReceiptDraftStore.putDraft(
                                    draftId = previewId,
                                    transaksi = previewTransaksi,
                                    details = previewDetails,
                                    pembayaran = previewPayment,
                                )
                                onPreviewReceipt(previewId)
                            },
                            onCheckout = {
                                if (processing) return@CheckoutPaymentPane
                                if (paymentMethod == CheckoutPaymentMethod.TUNAI && effectivePaidAmount < totals.grandTotal) {
                                    syncMessage = "Tunai dibayar tidak boleh kurang dari Grand Total (${formatRupiah(totals.grandTotal)})."
                                    return@CheckoutPaymentPane
                                }
                                val currentDraft = draft ?: return@CheckoutPaymentPane
                                scope.launch {
                                    processing = true
                                    syncMessage = null
                                    runCatching {
                                        val transaksiId = IdGenerator.newId("trx_")
                                        val createdAt = nowIso()
                                        val header = Transaksi(
                                            id = transaksiId,
                                            createdAt = createdAt,
                                            meja = currentDraft.tableToken,
                                            cashierId = currentCashierId(),
                                            cashierName = currentCashierName(),
                                            discountPlus = effectiveDiscountValue,
                                            tax = taxValue,
                                            serviceCharge = serviceChargeValue,
                                            rounding = roundingValue,
                                            total = 0L,
                                            outletId = currentOutletId(),
                                        )
                                        transaksiRepository.createTransaksi(header)

                                        val details = currentDraft.lines.map { line ->
                                            val detail = TransaksiDetail(
                                                id = IdGenerator.newId("trd_"),
                                                transaksiId = transaksiId,
                                                itemId = line.itemId,
                                                itemName = lineDisplayName(line),
                                                qty = line.qty,
                                                price = line.price,
                                                discount = 0L,
                                                total = line.qty * line.price,
                                            )
                                            transaksiRepository.addDetail(detail)
                                            detail
                                        }

                                        val pembayaran = Pembayaran(
                                            id = IdGenerator.newId("pay_"),
                                            transaksiId = transaksiId,
                                            paidAt = createdAt,
                                            amountPaid = effectivePaidAmount,
                                            change = 0L,
                                            paymentTypeId = paymentMethod.paymentTypeId,
                                            outletId = currentOutletId(),
                                        )

                                        val checkoutTotals = transaksiRepository.checkoutCash(
                                            transaksiId = transaksiId,
                                            details = details,
                                            pembayaran = pembayaran,
                                            discountPlus = header.discountPlus,
                                            tax = header.tax,
                                            serviceCharge = header.serviceCharge,
                                            rounding = header.rounding,
                                            cashierId = header.cashierId,
                                            cashierName = header.cashierName,
                                        )

                                        val transaksiDto = TransaksiDto(
                                            id = transaksiId,
                                            createdAt = createdAt,
                                            meja = header.meja,
                                            cashierId = header.cashierId,
                                            cashierName = header.cashierName,
                                            discountPlus = header.discountPlus,
                                            tax = header.tax,
                                            serviceCharge = header.serviceCharge,
                                            rounding = header.rounding,
                                            total = checkoutTotals.grandTotal,
                                            details = details.map { detail ->
                                                TransaksiDetailDto(
                                                    id = detail.id,
                                                    itemId = detail.itemId,
                                                    itemName = detail.itemName,
                                                    qty = detail.qty,
                                                    price = detail.price,
                                                    discount = detail.discount,
                                                    total = detail.total,
                                                )
                                            },
                                            pembayaran = PembayaranDto(
                                                id = pembayaran.id,
                                                paidAt = pembayaran.paidAt,
                                                amountPaid = pembayaran.amountPaid,
                                                change = checkoutTotals.change,
                                                paymentTypeId = pembayaran.paymentTypeId,
                                            ),
                                        )

                                        transaksiSyncRepository.enqueueCheckout(transaksiDto, currentOutletId())
                                        val baseUrl = serverBaseUrl()
                                        if (baseUrl == null) {
                                            syncMessage = "Checkout saved locally. Pair server later from Settings to sync."
                                        } else {
                                            runCatching {
                                                val synced = transaksiSyncRepository.flushPending(
                                                    baseUrl = baseUrl,
                                                    outletId = currentOutletId(),
                                                )
                                                "Checkout saved. Synced $synced event(s)."
                                            }.onFailure {
                                                syncMessage = "Checkout saved locally. Sync pending: ${it.message ?: "Unknown error"}"
                                            }.onSuccess {
                                                syncMessage = it
                                            }
                                        }

                                        OrderDraftStore.removeDraft(draftId)
                                        OrderBuilderCartStore.clearSnapshot(currentOutletId())
                                        draft = null
                                        onPreviewReceipt(transaksiId)
                                    }.onFailure {
                                        syncMessage = "Checkout failed: ${it.message ?: "Unknown error"}"
                                    }
                                    processing = false
                                }
                            },
                            onBackToOrders = onBackToOrders,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutPreviewPane(
    tableToken: String?,
    lines: List<OrderDraftLine>,
    subtotal: Long,
    syncMessage: String?,
    onDecreaseQty: (Int) -> Unit,
    onIncreaseQty: (Int) -> Unit,
    onEditModifiers: (Int) -> Unit,
    editingLineIndex: Int?,
    editingBundles: List<ModifierGroupBundle>,
    editingSelectionByGroup: Map<String, Set<String>>,
    onSelectionChange: (groupId: String, optionId: String, selected: Boolean, selectionType: String, maxSelection: Int) -> Unit,
    onCancelModifierEdit: () -> Unit,
    onApplyModifierEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.md),
    ) {
        AppCard {
            AppSectionHeader(
                title = "Ringkasan Pembayaran",
                subtitle = "Edit jumlah dan add-ons di sini sebelum bayar",
            )
            AppStatusPill(label = "Table: ${tableToken ?: "Walk-in"}")
            if (!syncMessage.isNullOrBlank()) {
                Text(
                    text = syncMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AppCard {
            AppSectionHeader("Items")
            if (lines.isEmpty()) {
                Text(
                    "No items in draft.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                lines.forEachIndexed { index, line ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(Dimens.sm),
                        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                line.itemName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                formatRupiah(line.qty * line.price),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (line.modifiers.isNotEmpty()) {
                            Text(
                                line.modifiers.joinToString(" | ") { it.optionName },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Unit Price: ${formatRupiah(line.price)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Qty: ${line.qty}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                        ) {
                            OutlinedButton(
                                onClick = { onDecreaseQty(index) },
                                modifier = Modifier.weight(0.8f),
                            ) { Text("-") }
                            OutlinedButton(
                                onClick = { onIncreaseQty(index) },
                                modifier = Modifier.weight(0.8f),
                            ) { Text("+") }
                            OutlinedButton(
                                onClick = { onEditModifiers(index) },
                                modifier = Modifier.weight(1.8f),
                            ) { Text("Modifier") }
                        }
                        if (editingLineIndex == index && editingBundles.isNotEmpty()) {
                            InlineModifierEditor(
                                bundles = editingBundles,
                                selectionByGroup = editingSelectionByGroup,
                                onSelectionChange = onSelectionChange,
                                onCancel = onCancelModifierEdit,
                                onApply = onApplyModifierEdit,
                            )
                        }
                    }
                }
            }
            AppInfoLine("Subtotal", formatRupiah(subtotal), emphasized = true)
        }
    }
}

@Composable
private fun InlineModifierEditor(
    bundles: List<ModifierGroupBundle>,
    selectionByGroup: Map<String, Set<String>>,
    onSelectionChange: (groupId: String, optionId: String, selected: Boolean, selectionType: String, maxSelection: Int) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(Dimens.xs),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
    ) {
        Text("Pilih Modifier", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        bundles.forEach { bundle ->
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                Text(bundle.group.name, fontWeight = FontWeight.SemiBold)
                bundle.options.forEach { option ->
                    val selected = selectionByGroup[bundle.group.id].orEmpty().contains(option.id)
                    OutlinedButton(
                        onClick = {
                            onSelectionChange(
                                bundle.group.id,
                                option.id,
                                !selected,
                                bundle.group.selectionType,
                                bundle.group.maxSelection,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val suffix = if (option.priceDelta == 0L) "" else " (+${formatRupiah(option.priceDelta)})"
                        Text("${if (selected) "[x]" else "[ ]"} ${option.name}$suffix")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = onApply, modifier = Modifier.weight(1f)) { Text("Apply") }
        }
    }
}

@Composable
private fun CheckoutPaymentPane(
    discountPlus: String,
    manualDiscountValue: Long,
    autoRecommendationDiscount: Long,
    recommendationDiscountHint: String?,
    taxPercent: Long,
    taxValue: Long,
    servicePercent: Long,
    serviceChargeValue: Long,
    roundingValue: Long,
    paymentMethod: CheckoutPaymentMethod,
    paid: String,
    effectivePaidAmount: Long,
    isCashPaidTooLow: Boolean,
    minimumCashPaid: Long,
    totals: TotalsCalculator.Result,
    processing: Boolean,
    onPaymentMethodChange: (CheckoutPaymentMethod) -> Unit,
    onDiscountChange: (String) -> Unit,
    onPaidChange: (String) -> Unit,
    onPreview: () -> Unit,
    onCheckout: () -> Unit,
    onBackToOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.md),
    ) {
        AppCard {
            AppSectionHeader("Payment Summary", "Pilih metode bayar: Tunai atau QRIS")
            AmountField("Discount Manual", discountPlus, onDiscountChange)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                OutlinedButton(
                    onClick = { onPaymentMethodChange(CheckoutPaymentMethod.TUNAI) },
                    enabled = !processing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "Tunai",
                        fontWeight = if (paymentMethod == CheckoutPaymentMethod.TUNAI) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                OutlinedButton(
                    onClick = { onPaymentMethodChange(CheckoutPaymentMethod.QRIS) },
                    enabled = !processing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "QRIS",
                        fontWeight = if (paymentMethod == CheckoutPaymentMethod.QRIS) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            if (autoRecommendationDiscount > 0L) {
                AppInfoLine("Auto Bundle + Promo", "-${formatRupiah(autoRecommendationDiscount)}")
                recommendationDiscountHint?.takeIf { it.isNotBlank() }?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppInfoLine(
                "Total Discount",
                formatRupiah((manualDiscountValue + autoRecommendationDiscount).coerceAtLeast(0L)),
            )
            AppInfoLine("Tax ($taxPercent%)", formatRupiah(taxValue))
            AppInfoLine("Service ($servicePercent%)", formatRupiah(serviceChargeValue))
            AppInfoLine("Rounding (Auto)", formatRupiah(roundingValue))
            if (paymentMethod == CheckoutPaymentMethod.TUNAI) {
                AmountField(
                    label = "Tunai Dibayar",
                    value = paid,
                    onValueChange = onPaidChange,
                    isError = isCashPaidTooLow,
                    errorText = if (isCashPaidTooLow) {
                        "Minimal ${formatRupiah(minimumCashPaid)}"
                    } else {
                        null
                    },
                )
            } else {
                AppInfoLine("Pembayaran QRIS", formatRupiah(effectivePaidAmount))
            }
            AppInfoLine("Subtotal", formatRupiah(totals.subtotal))
            AppInfoLine("Grand Total", formatRupiah(totals.grandTotal), emphasized = true)
            AppInfoLine("Change", formatRupiah(totals.change), emphasized = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = !processing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Preview Receipt")
                }
                Button(
                    onClick = onCheckout,
                    enabled = !processing && !isCashPaidTooLow,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (processing) {
                            "Processing..."
                        } else {
                            "Checkout ${paymentMethod.label}"
                        }
                    )
                }
            }
            OutlinedButton(
                onClick = onBackToOrders,
                enabled = !processing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to Orders")
            }
        }
    }
}

@Composable
private fun AmountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    errorText: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                onValueChange(raw.filter(Char::isDigit).take(12))
            },
            label = { Text(label) },
            prefix = { Text("Rp") },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        )
        if (!errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private data class RecommendationDiscountResult(
    val totalDiscount: Long,
    val hint: String?,
)

private data class BundleMatchResult(
    val discount: Long,
    val appliedBundleCount: Int,
    val remainingQtyByItemId: Map<String, Long>,
)

private fun calculateRecommendationDiscount(
    lines: List<OrderDraftLine>,
    bundles: List<BundleDraft>,
    promos: List<PromoDraft>,
    referenceDate: String,
): RecommendationDiscountResult {
    if (lines.isEmpty()) {
        return RecommendationDiscountResult(totalDiscount = 0L, hint = null)
    }
    val activeBundles = bundles.filter { isActiveRecommendationWindow(it.startDate, it.endDate, referenceDate) }
    val activePromos = promos.filter { isActiveRecommendationWindow(it.startDate, it.endDate, referenceDate) }
    if (activeBundles.isEmpty() && activePromos.isEmpty()) {
        return RecommendationDiscountResult(totalDiscount = 0L, hint = null)
    }

    val availableQtyByItemId = lines.groupBy { it.itemId.orEmpty() }
        .filterKeys { it.isNotBlank() }
        .mapValues { (_, groupedLines) -> groupedLines.sumOf { it.qty.coerceAtLeast(0L) } }
        .toMutableMap()
    val basePriceByItemId = lines.asSequence()
        .mapNotNull { line ->
            val id = line.itemId ?: return@mapNotNull null
            id to line.basePrice.coerceAtLeast(0L)
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, prices) -> prices.minOrNull() ?: 0L }

    val bundleMatch = applyBundleDiscounts(
        bundles = activeBundles,
        availableQtyByItemId = availableQtyByItemId,
        basePriceByItemId = basePriceByItemId,
    )

    val bestPromoPriceByItemId = activePromos
        .groupBy { it.itemId }
        .mapValues { (_, candidatePromos) ->
            candidatePromos.minOf { it.promoPrice.coerceAtLeast(0L) }
        }

    var appliedPromoCount = 0
    var promoDiscount = 0L
    bundleMatch.remainingQtyByItemId.forEach { (itemId, remainingQty) ->
        if (remainingQty <= 0L) return@forEach
        val promoPrice = bestPromoPriceByItemId[itemId] ?: return@forEach
        val basePrice = basePriceByItemId[itemId] ?: return@forEach
        if (basePrice <= promoPrice) return@forEach
        appliedPromoCount += 1
        promoDiscount += (basePrice - promoPrice) * remainingQty
    }

    val totalDiscount = (bundleMatch.discount + promoDiscount).coerceAtLeast(0L)
    if (totalDiscount <= 0L) {
        return RecommendationDiscountResult(totalDiscount = 0L, hint = null)
    }
    val hint = buildString {
        val parts = mutableListOf<String>()
        if (bundleMatch.appliedBundleCount > 0) {
            parts += "Bundle x${bundleMatch.appliedBundleCount}"
        }
        if (appliedPromoCount > 0) {
            parts += "Promo item $appliedPromoCount"
        }
        append(parts.joinToString(" • "))
    }.ifBlank { null }
    return RecommendationDiscountResult(
        totalDiscount = totalDiscount,
        hint = hint,
    )
}

private fun applyBundleDiscounts(
    bundles: List<BundleDraft>,
    availableQtyByItemId: MutableMap<String, Long>,
    basePriceByItemId: Map<String, Long>,
): BundleMatchResult {
    var totalDiscount = 0L
    var appliedBundleCount = 0
    val candidates = bundles.mapNotNull { bundle ->
        if (bundle.items.isEmpty()) return@mapNotNull null
        val basePerSet = bundle.items.sumOf { item ->
            val price = basePriceByItemId[item.itemId] ?: 0L
            price * item.qty.coerceAtLeast(1)
        }
        val discountPerSet = (basePerSet - bundle.price.coerceAtLeast(0L)).coerceAtLeast(0L)
        if (discountPerSet <= 0L) return@mapNotNull null
        bundle to discountPerSet
    }.sortedByDescending { (_, discountPerSet) -> discountPerSet }

    for ((bundle, discountPerSet) in candidates) {
        var maxSetCount = Long.MAX_VALUE
        for (item in bundle.items) {
            val requiredQty = item.qty.coerceAtLeast(1).toLong()
            val availableQty = availableQtyByItemId[item.itemId] ?: 0L
            maxSetCount = min(maxSetCount, availableQty / requiredQty)
        }
        if (maxSetCount <= 0L || maxSetCount == Long.MAX_VALUE) continue

        appliedBundleCount += maxSetCount.toInt()
        totalDiscount += discountPerSet * maxSetCount
        bundle.items.forEach { item ->
            val requiredQty = item.qty.coerceAtLeast(1).toLong() * maxSetCount
            val current = availableQtyByItemId[item.itemId] ?: 0L
            availableQtyByItemId[item.itemId] = (current - requiredQty).coerceAtLeast(0L)
        }
    }
    return BundleMatchResult(
        discount = totalDiscount,
        appliedBundleCount = appliedBundleCount,
        remainingQtyByItemId = availableQtyByItemId.toMap(),
    )
}

private fun isActiveRecommendationWindow(
    startDate: String,
    endDate: String,
    referenceDate: String,
): Boolean {
    val start = startDate.trim()
    val end = endDate.trim()
    val ref = referenceDate.trim()
    if (ref.length != 10) return true
    if (start.length == 10 && ref < start) return false
    if (end.length == 10 && ref > end) return false
    return true
}

private fun decodeRecommendationBundles(raw: String): List<BundleDraft> {
    if (raw.isBlank()) return emptyList()
    return raw.split(RECORD_SEPARATOR)
        .mapNotNull { row ->
            val cols = row.split(FIELD_SEPARATOR)
            if (cols.size < 5) return@mapNotNull null
            val price = cols[4].toLongOrNull() ?: 0L
            val items = cols.getOrNull(5).orEmpty()
                .takeIf { it.isNotBlank() }
                ?.split(ITEM_SEPARATOR)
                ?.mapNotNull { encodedItem ->
                    val itemCols = encodedItem.split(ITEM_FIELD_SEPARATOR)
                    if (itemCols.size < 3) return@mapNotNull null
                    BundleItemInput(
                        itemId = itemCols[0],
                        itemName = itemCols[1],
                        qty = itemCols[2].toIntOrNull() ?: 1,
                    )
                }
                .orEmpty()
            BundleDraft(
                id = cols[0],
                name = cols[1],
                startDate = cols[2],
                endDate = cols[3],
                price = price,
                items = items,
                imageUrl = cols.getOrNull(6)?.takeIf { it.isNotBlank() },
            )
        }
}

private fun decodeRecommendationPromos(raw: String): List<PromoDraft> {
    if (raw.isBlank()) return emptyList()
    return raw.split(RECORD_SEPARATOR)
        .mapNotNull { row ->
            val cols = row.split(FIELD_SEPARATOR)
            if (cols.size < 8) return@mapNotNull null
            PromoDraft(
                id = cols[0],
                name = cols[1],
                itemId = cols[2],
                itemName = cols[3],
                discountPercent = cols[4].toIntOrNull() ?: 0,
                promoPrice = cols[5].toLongOrNull() ?: 0L,
                startDate = cols[6],
                endDate = cols[7],
            )
        }
}

@Preview
@Composable
fun OrderCheckoutScreenPreview() {
    val lines = listOf(
        OrderDraftLine(
            itemId = "item-1",
            itemName = "Es Teh",
            qty = 2,
            basePrice = 8000,
            price = 8000,
            modifiers = listOf(
                OrderDraftModifierSelection("sugar", "Sugar", "s50", "50%", 0),
            ),
        ),
        OrderDraftLine(
            itemId = "item-2",
            itemName = "Nasi Goreng",
            qty = 1,
            basePrice = 25000,
            price = 25000,
        ),
    )
    val totals = TotalsCalculator().calculate(
        lines = lines.map { TotalsCalculator.Line(it.qty, it.price, 0) },
        discountPlus = 0,
        tax = 4070,
        serviceCharge = 3700,
        rounding = 0,
        paid = 50000,
    )

    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            CheckoutPreviewPane(
                tableToken = "T-07",
                lines = lines,
                subtotal = totals.subtotal,
                syncMessage = "Checkout saved locally. Sync pending.",
                onDecreaseQty = {},
                onIncreaseQty = {},
                onEditModifiers = {},
                editingLineIndex = null,
                editingBundles = emptyList(),
                editingSelectionByGroup = emptyMap(),
                onSelectionChange = { _, _, _, _, _ -> },
                onCancelModifierEdit = {},
                onApplyModifierEdit = {},
            )
            CheckoutPaymentPane(
                discountPlus = "0",
                manualDiscountValue = 0,
                autoRecommendationDiscount = 0,
                recommendationDiscountHint = null,
                taxPercent = 11,
                taxValue = 4070,
                servicePercent = 10,
                serviceChargeValue = 3700,
                roundingValue = 0,
                paymentMethod = CheckoutPaymentMethod.TUNAI,
                paid = "50000",
                effectivePaidAmount = 50000,
                isCashPaidTooLow = false,
                minimumCashPaid = totals.grandTotal,
                totals = totals,
                processing = false,
                onPaymentMethodChange = {},
                onDiscountChange = {},
                onPaidChange = {},
                onPreview = {},
                onCheckout = {},
                onBackToOrders = {},
            )
        }
    }
}
