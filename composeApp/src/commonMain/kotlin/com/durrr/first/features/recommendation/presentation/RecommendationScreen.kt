package com.durrr.first.features.recommendation.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.durrr.first.core.utils.formatNumber
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.CatalogNameRules
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.features.product.data.BundleStore
import com.durrr.first.features.product.domain.BundleDraft
import com.durrr.first.features.product.domain.BundleItemInput
import com.durrr.first.features.recommendation.data.PromoStore
import com.durrr.first.features.recommendation.domain.PromoDraft
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppInfoLine
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.AppStatusPill
import com.durrr.first.ui.media.ProductImageBanner
import kotlinx.coroutines.launch

private enum class RecommendationTab(val title: String) {
    BUNDLE("Bundle"),
    PROMO("Promo"),
}

private val ActionBlue = Color(0xFF273BBF)
private const val BUNDLE_GROUP_ID = "grp-rekomendasi-bundle"
private const val BUNDLE_GROUP_NAME = "Bundle"
private const val PROMO_GROUP_ID = "grp-rekomendasi-promo"
private const val PROMO_GROUP_NAME = "Promo"
private const val KEY_RECOMMENDATION_BUNDLES = "recommendation_bundles_v1"
private const val KEY_RECOMMENDATION_PROMOS = "recommendation_promos_v1"
private const val RECORD_SEPARATOR = "\u001E"
private const val FIELD_SEPARATOR = "\u001F"
private const val ITEM_SEPARATOR = "\u001D"
private const val ITEM_FIELD_SEPARATOR = "\u001C"

@Composable
fun RecommendationScreen(
    menuRepository: MenuRepository,
    settingsRepository: SettingsRepository,
    menuSyncRepository: MenuSyncRepository,
    pickDate: (initialIso: String?, onPicked: (String) -> Unit) -> Unit = { _, _ -> },
    pickImage: ((String?) -> Unit) -> Unit = { onPicked -> onPicked(null) },
) {
    val bundles = BundleStore.bundles
    val promos = PromoStore.promos
    var menuItems by remember { mutableStateOf(emptyList<Item>()) }
    var groupNameById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedTab by remember { mutableStateOf(RecommendationTab.BUNDLE) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var bundleName by remember { mutableStateOf("") }
    var bundlePrice by remember { mutableStateOf("") }
    var bundleImageUrl by remember { mutableStateOf("") }
    var bundleStartDate by remember { mutableStateOf("") }
    var bundleEndDate by remember { mutableStateOf("") }
    var editingBundleId by remember { mutableStateOf<String?>(null) }
    val selectedBundleQty = remember { mutableStateMapOf<String, Int>() }

    var promoName by remember { mutableStateOf("") }
    var promoProductId by remember { mutableStateOf<String?>(null) }
    var promoQty by remember { mutableStateOf("1") }
    var promoPrice by remember { mutableStateOf("") }
    var promoDiscountPercent by remember { mutableStateOf("") }
    var promoStartDate by remember { mutableStateOf("") }
    var promoEndDate by remember { mutableStateOf("") }

    fun currentOutletId(): String = settingsRepository.resolveOutletId()

    fun persistRecommendationDrafts() {
        settingsRepository.upsert(KEY_RECOMMENDATION_BUNDLES, encodeBundles(bundles))
        settingsRepository.upsert(KEY_RECOMMENDATION_PROMOS, encodePromos(promos))
    }

    fun hydrateRecommendationDraftsFromSettings() {
        if (bundles.isEmpty()) {
            val storedBundles = decodeBundles(settingsRepository.getValue(KEY_RECOMMENDATION_BUNDLES))
            if (storedBundles.isNotEmpty()) bundles.addAll(storedBundles)
        }
        if (promos.isEmpty()) {
            val storedPromos = decodePromos(settingsRepository.getValue(KEY_RECOMMENDATION_PROMOS))
            if (storedPromos.isNotEmpty()) promos.addAll(storedPromos)
        }
    }

    fun syncDraftsWithMenu() {
        val bundleNameKeys = bundles.map { normalizeNameKey(it.name) }.toSet()
        val promoNameKeys = promos.map { normalizeNameKey(it.name) }.toSet()
        val bundleItems = menuItems.filter { item ->
            val groupName = item.groupId?.let(groupNameById::get)
            isBundleMenuItem(item, groupName, bundleNameKeys)
        }
        val promoItems = menuItems.filter { item ->
            val groupName = item.groupId?.let(groupNameById::get)
            isPromoMenuItem(item, groupName, promoNameKeys)
        }

        val existingBundleById = bundles.associateBy { it.id }
        val existingBundleByName = bundles.associateBy { normalizeNameKey(it.name) }
        val syncedBundles = bundleItems.sortedBy { it.name.lowercase() }.map { item ->
            val bundleId = extractBundleId(item.id)
            val existing = existingBundleById[bundleId]
                ?: existingBundleByName[normalizeNameKey(item.name)]
            if (existing != null) {
                existing.copy(
                    name = item.name,
                    price = item.price,
                    imageUrl = item.imageUrl ?: existing.imageUrl,
                )
            } else {
                BundleDraft(
                    id = bundleId,
                    name = item.name,
                    startDate = "",
                    endDate = "",
                    price = item.price,
                    items = emptyList(),
                    imageUrl = item.imageUrl,
                )
            }
        }
        if (syncedBundles.isNotEmpty() || bundles.isEmpty()) {
            bundles.clear()
            bundles.addAll(syncedBundles)
        }

        val existingPromoById = promos.associateBy { it.id }
        val existingPromoByName = promos.associateBy { normalizeNameKey(it.name) }
        val syncedPromos = promoItems.sortedBy { it.name.lowercase() }.map { item ->
            val promoId = extractPromoId(item.id)
            val existing = existingPromoById[promoId]
                ?: existingPromoByName[normalizeNameKey(item.name)]
            if (existing != null) {
                existing.copy(name = item.name, promoPrice = item.price, qty = existing.qty.coerceAtLeast(1))
            } else {
                PromoDraft(
                    id = promoId,
                    name = item.name,
                    itemId = item.id,
                    itemName = item.name,
                    discountPercent = existing?.discountPercent ?: 0,
                    qty = existing?.qty ?: 1,
                    promoPrice = item.price,
                    startDate = existing?.startDate.orEmpty(),
                    endDate = existing?.endDate.orEmpty(),
                )
            }
        }
        if (syncedPromos.isNotEmpty() || promos.isEmpty()) {
            promos.clear()
            promos.addAll(syncedPromos)
        }
    }

    fun syncMenuAfterChange(successPrefix: String) {
        val baseUrl = settingsRepository.getOptionalServerBaseUrl()
        if (baseUrl.isNullOrBlank()) {
            message = "$successPrefix Tersimpan lokal. Push menu di Settings untuk kirim ke server."
            return
        }
        scope.launch {
            runCatching {
                val pushed = menuSyncRepository.pushToServer(baseUrl, currentOutletId())
                val pulled = menuSyncRepository.pullFromServer(baseUrl, currentOutletId())
                menuItems = menuRepository
                    .getItems(currentOutletId())
                    .filter { it.isActive }
                    .sortedBy { it.name.lowercase() }
                syncDraftsWithMenu()
                persistRecommendationDrafts()
                "Sync server sukses (push $pushed item, pull $pulled item)."
            }.onSuccess { syncMsg ->
                message = "$successPrefix $syncMsg"
            }.onFailure { error ->
                message = "$successPrefix Sync server gagal: ${error.message ?: "Unknown error"}"
            }
        }
    }

    fun upsertRecommendationGroup(groupId: String, groupName: String) {
        menuRepository.upsertGroup(
            GroupItem(
                id = groupId,
                name = groupName,
                order = 99,
                outletId = currentOutletId(),
            ),
            outletId = currentOutletId(),
        )
    }

    fun refreshMenu() {
        menuItems = menuRepository
            .getItems(currentOutletId())
            .filter { it.isActive }
            .sortedBy { it.name.lowercase() }
        groupNameById = menuRepository
            .getGroups(currentOutletId())
            .associate { it.id to it.name }
    }

    fun resetBundleEditor() {
        editingBundleId = null
        bundleName = ""
        bundlePrice = ""
        bundleImageUrl = ""
        bundleStartDate = ""
        bundleEndDate = ""
        selectedBundleQty.clear()
    }

    fun startEditBundle(bundle: BundleDraft) {
        editingBundleId = bundle.id
        bundleName = bundle.name
        bundlePrice = bundle.price.toString()
        bundleImageUrl = bundle.imageUrl.orEmpty()
        bundleStartDate = bundle.startDate
        bundleEndDate = bundle.endDate
        selectedBundleQty.clear()
        bundle.items.forEach { item ->
            selectedBundleQty[item.itemId] = item.qty.coerceAtLeast(1)
        }
    }

    fun deleteBundle(bundle: BundleDraft) {
        val outletId = currentOutletId()
        val deletedMenuItemId = bundleMenuItemId(bundle.id)
        bundles.remove(bundle)
        menuRepository.hardDeleteItem(
            itemId = deletedMenuItemId,
            outletId = outletId,
        )
        if (editingBundleId == bundle.id) {
            resetBundleEditor()
        }
        refreshMenu()
        syncDraftsWithMenu()
        persistRecommendationDrafts()
        val baseUrl = settingsRepository.getOptionalServerBaseUrl()
        if (baseUrl.isNullOrBlank()) {
            message = "Bundle dihapus permanen di device ini."
            return
        }
        scope.launch {
            runCatching {
                menuSyncRepository.deleteItemFromServer(
                    baseUrl = baseUrl,
                    itemId = deletedMenuItemId,
                    outletId = outletId,
                )
                val pulled = menuSyncRepository.pullFromServer(baseUrl, outletId)
                refreshMenu()
                syncDraftsWithMenu()
                persistRecommendationDrafts()
                "Bundle dihapus permanen. Sync server sukses (pull $pulled item)."
            }.onSuccess { syncMessage ->
                message = syncMessage
            }.onFailure { error ->
                message = "Bundle dihapus lokal, tapi hapus server gagal: ${error.message ?: "Unknown error"}"
            }
        }
    }

    fun deletePromo(promo: PromoDraft) {
        val outletId = currentOutletId()
        val deletedMenuItemId = promoMenuItemId(promo.id)
        promos.remove(promo)
        menuRepository.hardDeleteItem(
            itemId = deletedMenuItemId,
            outletId = outletId,
        )
        refreshMenu()
        syncDraftsWithMenu()
        persistRecommendationDrafts()
        val baseUrl = settingsRepository.getOptionalServerBaseUrl()
        if (baseUrl.isNullOrBlank()) {
            message = "Promo dihapus permanen di device ini."
            return
        }
        scope.launch {
            runCatching {
                menuSyncRepository.deleteItemFromServer(
                    baseUrl = baseUrl,
                    itemId = deletedMenuItemId,
                    outletId = outletId,
                )
                val pulled = menuSyncRepository.pullFromServer(baseUrl, outletId)
                refreshMenu()
                syncDraftsWithMenu()
                persistRecommendationDrafts()
                "Promo dihapus permanen. Sync server sukses (pull $pulled item)."
            }.onSuccess { syncMessage ->
                message = syncMessage
            }.onFailure { error ->
                message = "Promo dihapus lokal, tapi hapus server gagal: ${error.message ?: "Unknown error"}"
            }
        }
    }

    LaunchedEffect(Unit) {
        hydrateRecommendationDraftsFromSettings()
        refreshMenu()
        syncDraftsWithMenu()
        persistRecommendationDrafts()
    }

    val itemById = remember(menuItems) { menuItems.associateBy { it.id } }
    val selectableMenuItems = remember(menuItems, groupNameById) {
        menuItems.filterNot { item ->
            val groupName = item.groupId?.let(groupNameById::get)
            isBundleMenuItem(item, groupName, emptySet()) ||
                isPromoMenuItem(item, groupName, emptySet())
        }
    }
    val recommendationHighlights = remember(
        bundles.toList(),
        promos.toList(),
        selectableMenuItems,
    ) {
        buildRecommendationHighlights(
            bundles = bundles,
            promos = promos,
            menuItems = selectableMenuItems,
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
    ) {
        val wide = maxWidth >= 1080.dp
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                AppSectionHeader(
                    title = "Rekomendasi",
                    subtitle = "Kelola bundle produk dan promo temporer untuk kasir + dashboard",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecommendationTabButton(
                        title = RecommendationTab.BUNDLE.title,
                        selected = selectedTab == RecommendationTab.BUNDLE,
                        onClick = { selectedTab = RecommendationTab.BUNDLE },
                    )
                    RecommendationTabButton(
                        title = RecommendationTab.PROMO.title,
                        selected = selectedTab == RecommendationTab.PROMO,
                        onClick = { selectedTab = RecommendationTab.PROMO },
                    )
                }
            }
            item {
                RecommendationHighlightsCard(
                    recommendations = recommendationHighlights,
                )
            }
            if (!message.isNullOrBlank()) {
                item {
                    AppCard {
                        Text(message.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                if (selectedTab == RecommendationTab.BUNDLE) {
                    BundleEditorCard(
                        menuItems = selectableMenuItems,
                        selectedQty = selectedBundleQty,
                        bundleName = bundleName,
                        bundlePrice = bundlePrice,
                        bundleImageUrl = bundleImageUrl,
                        bundleStartDate = bundleStartDate,
                        bundleEndDate = bundleEndDate,
                        isEditing = editingBundleId != null,
                        onBundleNameChange = { bundleName = it },
                        onBundlePriceChange = { bundlePrice = normalizeCurrencyInput(it) },
                        onPickBundleImage = {
                            pickImage { picked ->
                                if (!picked.isNullOrBlank()) {
                                    bundleImageUrl = picked
                                }
                            }
                        },
                        onClearBundleImage = { bundleImageUrl = "" },
                        onPickBundleStartDate = {
                            pickDate(bundleStartDate.takeIf { it.isNotBlank() }) { picked ->
                                bundleStartDate = picked
                            }
                        },
                        onPickBundleEndDate = {
                            pickDate(bundleEndDate.takeIf { it.isNotBlank() }) { picked ->
                                bundleEndDate = picked
                            }
                        },
                        onToggleItem = { itemId, checked ->
                            if (checked) {
                                selectedBundleQty[itemId] = selectedBundleQty[itemId] ?: 1
                            } else {
                                selectedBundleQty.remove(itemId)
                            }
                        },
                        onDecreaseQty = { itemId ->
                            val current = selectedBundleQty[itemId] ?: return@BundleEditorCard
                            if (current <= 1) {
                                selectedBundleQty.remove(itemId)
                            } else {
                                selectedBundleQty[itemId] = current - 1
                            }
                        },
                        onIncreaseQty = { itemId ->
                            val current = selectedBundleQty[itemId] ?: 0
                            selectedBundleQty[itemId] = (current + 1).coerceAtMost(99)
                        },
                        onSave = {
                            val name = CatalogNameRules.normalize(bundleName)
                            val price = parseCurrencyInput(bundlePrice)
                            val selected = selectedBundleQty.filterValues { it > 0 }
                            if (name.isBlank()) {
                                message = "Nama bundle wajib ASCII terbaca (maks ${CatalogNameRules.MAX_LENGTH} karakter)."
                                return@BundleEditorCard
                            }
                            if (price <= 0L) {
                                message = "Harga bundle harus lebih dari 0."
                                return@BundleEditorCard
                            }
                            if (selected.size < 2) {
                                message = "Pilih minimal 2 produk untuk bundle."
                                return@BundleEditorCard
                            }

                            val bundleItems = selected.mapNotNull { (itemId, qty) ->
                                val item = itemById[itemId] ?: return@mapNotNull null
                                BundleItemInput(
                                    itemId = item.id,
                                    itemName = item.name,
                                    qty = qty,
                                )
                            }

                            val existingBundleId = editingBundleId
                            val resolvedBundleId = existingBundleId ?: IdGenerator.newId("bundle_")
                            val draft = BundleDraft(
                                id = resolvedBundleId,
                                name = name,
                                startDate = bundleStartDate.trim(),
                                endDate = bundleEndDate.trim(),
                                price = price,
                                items = bundleItems,
                                imageUrl = bundleImageUrl.trim().ifBlank { null },
                            )
                            val existingIndex = bundles.indexOfFirst { it.id == resolvedBundleId }
                            if (existingIndex >= 0) {
                                bundles[existingIndex] = draft
                            } else {
                                bundles.add(draft)
                            }
                            upsertRecommendationGroup(BUNDLE_GROUP_ID, BUNDLE_GROUP_NAME)
                            menuRepository.upsertItem(
                                Item(
                                    id = bundleMenuItemId(draft.id),
                                    name = draft.name,
                                    price = draft.price,
                                    groupId = BUNDLE_GROUP_ID,
                                    code = "BND-${draft.id.takeLast(6).uppercase()}",
                                    imageUrl = draft.imageUrl,
                                    isActive = true,
                                    outletId = currentOutletId(),
                                ),
                                outletId = currentOutletId(),
                            )
                            refreshMenu()
                            syncDraftsWithMenu()
                            persistRecommendationDrafts()
                            val successPrefix = if (existingBundleId == null) {
                                "Bundle tersimpan (${bundles.size} total)."
                            } else {
                                "Bundle diperbarui."
                            }
                            resetBundleEditor()
                            syncMenuAfterChange(successPrefix)
                        },
                        onCancelEdit = ::resetBundleEditor,
                    )
                } else {
                    PromoEditorCard(
                        menuItems = menuItems,
                        selectedProductId = promoProductId,
                        promoName = promoName,
                        promoQty = promoQty,
                        promoPrice = promoPrice,
                        promoDiscountPercent = promoDiscountPercent,
                        promoStartDate = promoStartDate,
                        promoEndDate = promoEndDate,
                        onPromoNameChange = { promoName = it },
                        onPromoQtyChange = { value ->
                            val digits = value.filter(Char::isDigit).take(2)
                            promoQty = when {
                                digits.isBlank() -> ""
                                digits.toIntOrNull() == null -> "1"
                                digits.toInt() < 1 -> "1"
                                digits.toInt() > 99 -> "99"
                                else -> digits
                            }
                        },
                        onDecreasePromoQty = {
                            val current = promoQty.toIntOrNull() ?: 1
                            promoQty = (current - 1).coerceAtLeast(1).toString()
                        },
                        onIncreasePromoQty = {
                            val current = promoQty.toIntOrNull() ?: 1
                            promoQty = (current + 1).coerceAtMost(99).toString()
                        },
                        onPromoPriceChange = { promoPrice = normalizeCurrencyInput(it) },
                        onPromoDiscountPercentChange = {
                            promoDiscountPercent = it.filter(Char::isDigit).take(2)
                        },
                        onPickPromoStartDate = {
                            pickDate(promoStartDate.takeIf { it.isNotBlank() }) { picked ->
                                promoStartDate = picked
                            }
                        },
                        onPickPromoEndDate = {
                            pickDate(promoEndDate.takeIf { it.isNotBlank() }) { picked ->
                                promoEndDate = picked
                            }
                        },
                        onSelectProduct = { promoProductId = it },
                        onSave = {
                            val item = itemById[promoProductId] ?: run {
                                message = "Pilih produk untuk promo."
                                return@PromoEditorCard
                            }

                            val qty = promoQty.toIntOrNull()?.coerceIn(1, 99) ?: 1
                            val normalTotal = item.price * qty

                            val normalizedPromoName = CatalogNameRules.normalize(promoName)
                            val percent = promoDiscountPercent.toIntOrNull()?.coerceIn(0, 95) ?: 0
                            val customPromoPrice = parseCurrencyInputOrNull(promoPrice)

                            val resolvedPrice = when {
                                customPromoPrice != null && customPromoPrice > 0L -> customPromoPrice
                                percent > 0 -> (normalTotal - (normalTotal * percent / 100L)).coerceAtLeast(0L)
                                else -> 0L
                            }

                            if (normalizedPromoName.isBlank()) {
                                message = "Nama promo wajib ASCII terbaca (maks ${CatalogNameRules.MAX_LENGTH} karakter)."
                                return@PromoEditorCard
                            }

                            if (qty < 1) {
                                message = "Jumlah promo minimal 1."
                                return@PromoEditorCard
                            }

                            if (resolvedPrice <= 0L) {
                                message = "Isi harga promo atau diskon persen yang valid."
                                return@PromoEditorCard
                            }

                            promos.add(
                                PromoDraft(
                                    id = IdGenerator.newId("promo_"),
                                    name = normalizedPromoName,
                                    itemId = item.id,
                                    itemName = item.name,
                                    qty = qty,
                                    discountPercent = percent,
                                    promoPrice = resolvedPrice,
                                    startDate = promoStartDate.trim(),
                                    endDate = promoEndDate.trim(),
                                )
                            )

                            val savedPromo = promos.last()

                            upsertRecommendationGroup(PROMO_GROUP_ID, PROMO_GROUP_NAME)

                            menuRepository.upsertItem(
                                Item(
                                    id = promoMenuItemId(savedPromo.id),
                                    name = "${savedPromo.name} (${savedPromo.itemName} x${savedPromo.qty})",
                                    price = savedPromo.promoPrice,
                                    groupId = PROMO_GROUP_ID,
                                    code = "PRM-${savedPromo.id.takeLast(6).uppercase()}",
                                    isActive = true,
                                    outletId = currentOutletId(),
                                ),
                                outletId = currentOutletId(),
                            )

                            refreshMenu()
                            syncDraftsWithMenu()
                            persistRecommendationDrafts()

                            promoName = ""
                            promoProductId = null
                            promoQty = "1"
                            promoPrice = ""
                            promoDiscountPercent = ""
                            promoStartDate = ""
                            promoEndDate = ""

                            syncMenuAfterChange("Promo tersimpan (${promos.size} total).")
                        },
                    )
                }
            }
            item {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        RecommendationListCard(
                            title = "Daftar Bundle",
                            emptyMessage = "Belum ada bundle tersimpan.",
                            modifier = Modifier.weight(1f),
                        ) {
                            if (bundles.isEmpty()) {
                                AppEmptyState(title = "Bundle kosong", message = "Tambahkan bundle untuk mulai promosi paket.")
                            } else {
                                bundles.forEach { bundle ->
                                    RecommendationBundleRow(
                                        bundle = bundle,
                                        onEdit = { startEditBundle(bundle) },
                                        onDelete = { deleteBundle(bundle) },
                                    )
                                }
                            }
                        }
                        RecommendationListCard(
                            title = "Daftar Promo",
                            emptyMessage = "Belum ada promo tersimpan.",
                            modifier = Modifier.weight(1f),
                        ) {
                            if (promos.isEmpty()) {
                                AppEmptyState(title = "Promo kosong", message = "Tambahkan promo temporer untuk item yang perlu didorong.")
                            } else {
                                promos.forEach { promo ->
                                    RecommendationPromoRow(
                                        promo = promo,
                                        onDelete = { deletePromo(promo) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        RecommendationListCard(
                            title = "Daftar Bundle",
                            emptyMessage = "Belum ada bundle tersimpan.",
                        ) {
                            if (bundles.isEmpty()) {
                                AppEmptyState(title = "Bundle kosong", message = "Tambahkan bundle untuk mulai promosi paket.")
                            } else {
                                bundles.forEach { bundle ->
                                    RecommendationBundleRow(
                                        bundle = bundle,
                                        onEdit = { startEditBundle(bundle) },
                                        onDelete = { deleteBundle(bundle) },
                                    )
                                }
                            }
                        }
                        RecommendationListCard(
                            title = "Daftar Promo",
                            emptyMessage = "Belum ada promo tersimpan.",
                        ) {
                            if (promos.isEmpty()) {
                                AppEmptyState(title = "Promo kosong", message = "Tambahkan promo temporer untuk item yang perlu didorong.")
                            } else {
                                promos.forEach { promo ->
                                    RecommendationPromoRow(
                                        promo = promo,
                                        onDelete = { deletePromo(promo) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bundleMenuItemId(bundleId: String): String = "menu_bundle_$bundleId"

private fun promoMenuItemId(promoId: String): String = "menu_promo_$promoId"

private fun extractBundleId(menuItemId: String): String {
    return menuItemId.removePrefix("menu_bundle_").ifBlank { menuItemId }
}

private fun extractPromoId(menuItemId: String): String {
    return menuItemId.removePrefix("menu_promo_").ifBlank { menuItemId }
}

private fun normalizeNameKey(value: String): String {
    return value.trim().lowercase().replace(Regex("\\s+"), " ")
}

private fun isBundleMenuItem(
    item: Item,
    groupName: String?,
    knownBundleNameKeys: Set<String>,
): Boolean {
    return item.groupId == BUNDLE_GROUP_ID ||
        item.id.startsWith("menu_bundle_") ||
        groupName.equals(BUNDLE_GROUP_NAME, ignoreCase = true) ||
        normalizeNameKey(item.name) in knownBundleNameKeys
}

private fun isPromoMenuItem(
    item: Item,
    groupName: String?,
    knownPromoNameKeys: Set<String>,
): Boolean {
    return item.groupId == PROMO_GROUP_ID ||
        item.id.startsWith("menu_promo_") ||
        groupName.equals(PROMO_GROUP_NAME, ignoreCase = true) ||
        normalizeNameKey(item.name) in knownPromoNameKeys
}

private fun encodeBundles(bundles: List<BundleDraft>): String {
    if (bundles.isEmpty()) return ""
    return bundles.joinToString(RECORD_SEPARATOR) { bundle ->
        val itemsEncoded = bundle.items.joinToString(ITEM_SEPARATOR) { item ->
            listOf(item.itemId, item.itemName, item.qty.toString()).joinToString(ITEM_FIELD_SEPARATOR)
        }
        listOf(
            bundle.id,
            bundle.name,
            bundle.startDate,
            bundle.endDate,
            bundle.price.toString(),
            itemsEncoded,
            bundle.imageUrl.orEmpty(),
        ).joinToString(FIELD_SEPARATOR)
    }
}

private fun decodeBundles(raw: String): List<BundleDraft> {
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

private fun encodePromos(promos: List<PromoDraft>): String {
    if (promos.isEmpty()) return ""
    return promos.joinToString(RECORD_SEPARATOR) { promo ->
        listOf(
            promo.id,
            promo.name,
            promo.itemId,
            promo.itemName,
            promo.qty.coerceAtLeast(1).toString(),
            promo.discountPercent.toString(),
            promo.promoPrice.toString(),
            promo.startDate,
            promo.endDate,
        ).joinToString(FIELD_SEPARATOR)
    }
}

private fun decodePromos(raw: String): List<PromoDraft> {
    if (raw.isBlank()) return emptyList()
    return raw.split(RECORD_SEPARATOR)
        .mapNotNull { row ->
            val cols = row.split(FIELD_SEPARATOR)

            if (cols.size >= 9) {
                PromoDraft(
                    id = cols[0],
                    name = cols[1],
                    itemId = cols[2],
                    itemName = cols[3],
                    qty = cols[4].toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    discountPercent = cols[5].toIntOrNull() ?: 0,
                    promoPrice = cols[6].toLongOrNull() ?: 0L,
                    startDate = cols[7],
                    endDate = cols[8],
                )
            } else if (cols.size >= 8) {
                PromoDraft(
                    id = cols[0],
                    name = cols[1],
                    itemId = cols[2],
                    itemName = cols[3],
                    qty = 1,
                    discountPercent = cols[4].toIntOrNull() ?: 0,
                    promoPrice = cols[5].toLongOrNull() ?: 0L,
                    startDate = cols[6],
                    endDate = cols[7],
                )
            } else {
                null
            }
        }
}
private fun normalizeCurrencyInput(value: String, maxDigits: Int = 12): String {
    return value.filter(Char::isDigit).take(maxDigits)
}

private fun parseCurrencyInput(raw: String): Long {
    return raw.filter(Char::isDigit).toLongOrNull() ?: 0L
}

private fun parseCurrencyInputOrNull(raw: String): Long? {
    val digits = raw.filter(Char::isDigit)
    if (digits.isBlank()) return null
    return digits.toLongOrNull()
}

private fun formatCurrencyInput(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.isBlank()) return ""
    return formatNumber(digits.toLongOrNull() ?: 0L)
}

private data class RecommendationHighlight(
    val badge: String,
    val title: String,
    val detail: String,
)

private fun buildRecommendationHighlights(
    bundles: List<BundleDraft>,
    promos: List<PromoDraft>,
    menuItems: List<Item>,
): List<RecommendationHighlight> {
    val fallbackBest = menuItems.maxByOrNull { it.price }
    val fallbackLowest = menuItems.minByOrNull { it.price }

    val bundleTitle = bundles.firstOrNull()?.name
        ?.takeIf { it.isNotBlank() }
        ?.let { "Bundle $it" }
        ?: run {
            val first = fallbackBest?.name
            val second = fallbackLowest?.name
            when {
                !first.isNullOrBlank() && !second.isNullOrBlank() && first != second -> "Bundle $first + $second"
                !first.isNullOrBlank() -> "Bundle $first + item pendamping"
                else -> "Bundle item terlaris + slow mover"
            }
        }

    val promoTarget = promos.firstOrNull()?.itemName
        ?.takeIf { it.isNotBlank() }
        ?: fallbackLowest?.name
        ?: "item slow mover"
    val upsellTarget = fallbackBest?.name ?: bundles.firstOrNull()?.name ?: "item unggulan"

    return listOf(
        RecommendationHighlight(
            badge = "Bundle",
            title = bundleTitle,
            detail = "Paketkan item terlaris dengan slow mover untuk dorong penjualan item yang lambat.",
        ),
        RecommendationHighlight(
            badge = "Promo",
            title = "Promo terbatas untuk $promoTarget",
            detail = "Jadikan menu sementara 7 hari dengan diskon kecil atau bonus topping agar qty naik.",
        ),
        RecommendationHighlight(
            badge = "Upsell",
            title = "Jadikan $upsellTarget sebagai add-on utama",
            detail = "Taruh di urutan atas kasir/QR order untuk menjaga momentum item paling laku.",
        ),
    )
}

@Composable
private fun RecommendationHighlightsCard(
    recommendations: List<RecommendationHighlight>,
) {
    AppCard {
        Text(
            "Rekomendasi Bundle & Promo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        recommendations.forEach { recommendation ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppStatusPill(label = recommendation.badge)
                    Text(
                        recommendation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        recommendation.detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
        ) {
            Text(title)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(title)
        }
    }
}

@Composable
private fun BundleEditorCard(
    menuItems: List<Item>,
    selectedQty: Map<String, Int>,
    bundleName: String,
    bundlePrice: String,
    bundleImageUrl: String,
    bundleStartDate: String,
    bundleEndDate: String,
    isEditing: Boolean,
    onBundleNameChange: (String) -> Unit,
    onBundlePriceChange: (String) -> Unit,
    onPickBundleImage: () -> Unit,
    onClearBundleImage: () -> Unit,
    onPickBundleStartDate: () -> Unit,
    onPickBundleEndDate: () -> Unit,
    onToggleItem: (itemId: String, checked: Boolean) -> Unit,
    onDecreaseQty: (itemId: String) -> Unit,
    onIncreaseQty: (itemId: String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val selectedCount = selectedQty.values.count { it > 0 }
    val itemById = remember(menuItems) { menuItems.associateBy { it.id } }
    val baselineTotal = selectedQty.entries.sumOf { (itemId, qty) ->
        val item = itemById[itemId] ?: return@sumOf 0L
        item.price * qty
    }
    val bundlePriceValue = parseCurrencyInput(bundlePrice)
    val savings = (baselineTotal - bundlePriceValue).coerceAtLeast(0L)

    AppCard {
        AppSectionHeader(
            if (isEditing) "Edit Bundle Produk" else "Buat Bundle Produk",
            "Contoh: Coffee + Snack jadi paket promo yang langsung muncul di kasir",
        )
        OutlinedTextField(
            value = bundleName,
            onValueChange = onBundleNameChange,
            label = { Text("Nama Bundle") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = formatCurrencyInput(bundlePrice),
            onValueChange = onBundlePriceChange,
            label = { Text("Harga Bundle") },
            prefix = { Text("Rp") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text("Foto Bundle", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Pilih foto dari galeri supaya bundle tampil lebih jelas di kasir/menu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onPickBundleImage,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = ActionBlue,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text(if (bundleImageUrl.isBlank()) "Pilih Foto dari Galeri" else "Ganti Foto")
            }
            if (bundleImageUrl.isNotBlank()) {
                Button(
                    onClick = onClearBundleImage,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFB42318),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Text("Hapus Foto")
                }
            }
        }
        ProductImageBanner(
            imageUrl = bundleImageUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Harga tersimpan: ${formatRupiah(bundlePriceValue)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (selectedCount > 0) {
                Text(
                    "Pilih: ${formatNumber(selectedCount)} item",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateActionField(
                label = "Mulai",
                value = bundleStartDate,
                placeholder = "Pilih tanggal mulai",
                onClick = onPickBundleStartDate,
                modifier = Modifier.weight(1f),
            )
            DateActionField(
                label = "Selesai",
                value = bundleEndDate,
                placeholder = "Pilih tanggal selesai",
                onClick = onPickBundleEndDate,
                modifier = Modifier.weight(1f),
            )
        }
        if (baselineTotal > 0L) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Harga normal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRupiah(baselineTotal), fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Harga bundle", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRupiah(bundlePriceValue), fontWeight = FontWeight.SemiBold, color = ActionBlue)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Potensi hemat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRupiah(savings), fontWeight = FontWeight.SemiBold)
            }
        }
        Text("Pilih item bundle", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (menuItems.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "Belum ada produk aktif",
                        message = "Tambahkan produk dulu di tab Produk, lalu kembali ke sini untuk bikin bundle.",
                    )
                }
            }
            items(menuItems) { item ->
                val qty = selectedQty[item.id] ?: 0
                val checked = qty > 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(formatRupiah(item.price), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!checked) {
                        OutlinedButton(onClick = { onToggleItem(item.id, true) }) {
                            Text("Pilih")
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = { onDecreaseQty(item.id) }) { Text("−") }
                            Text(
                                formatNumber(qty),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.width(28.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedButton(onClick = { onIncreaseQty(item.id) }) { Text("+") }
                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedButton(onClick = { onToggleItem(item.id, false) }) { Text("Hapus") }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isEditing) "Update Bundle" else "Simpan Bundle")
        }
        if (isEditing) {
            OutlinedButton(
                onClick = onCancelEdit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Batal Edit")
            }
        }
    }
}

@Composable
private fun PromoEditorCard(
    menuItems: List<Item>,
    selectedProductId: String?,
    promoName: String,
    promoQty: String,
    promoPrice: String,
    promoDiscountPercent: String,
    promoStartDate: String,
    promoEndDate: String,
    onPromoNameChange: (String) -> Unit,
    onPromoQtyChange: (String) -> Unit,
    onDecreasePromoQty: () -> Unit,
    onIncreasePromoQty: () -> Unit,
    onPromoPriceChange: (String) -> Unit,
    onPromoDiscountPercentChange: (String) -> Unit,
    onPickPromoStartDate: () -> Unit,
    onPickPromoEndDate: () -> Unit,
    onSelectProduct: (String?) -> Unit,
    onSave: () -> Unit,
) {
    val promoQtyValue = promoQty.toIntOrNull()?.coerceIn(1, 99) ?: 1
    val promoPriceValue = parseCurrencyInput(promoPrice)
    val selectedItem = menuItems.firstOrNull { it.id == selectedProductId }
    val normalTotal = selectedItem?.let { it.price * promoQtyValue } ?: 0L

    AppCard {
        AppSectionHeader("Buat Promo Produk", "Promo sementara untuk item tertentu")

        OutlinedTextField(
            value = promoName,
            onValueChange = onPromoNameChange,
            label = { Text("Nama Promo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text("Pilih produk", style = MaterialTheme.typography.titleSmall)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            menuItems.take(24).forEach { item ->
                val selected = selectedProductId == item.id
                if (selected) {
                    Button(
                        onClick = { onSelectProduct(item.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                    ) {
                        Text(item.name)
                    }
                } else {
                    OutlinedButton(onClick = { onSelectProduct(item.id) }) {
                        Text(item.name)
                    }
                }
            }
        }

        Text("Jumlah produk promo", style = MaterialTheme.typography.titleSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDecreasePromoQty,
                enabled = promoQtyValue > 1,
            ) {
                Text("−")
            }

            OutlinedTextField(
                value = promoQty,
                onValueChange = onPromoQtyChange,
                label = { Text("Qty") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )

            OutlinedButton(
                onClick = onIncreasePromoQty,
                enabled = promoQtyValue < 99,
            ) {
                Text("+")
            }
        }

        if (selectedItem != null) {
            Text(
                text = "Harga normal ${selectedItem.name} x$promoQtyValue: ${formatRupiah(normalTotal)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = formatCurrencyInput(promoPrice),
                onValueChange = onPromoPriceChange,
                label = { Text("Harga Promo Total (opsional)") },
                prefix = { Text("Rp") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )

            OutlinedTextField(
                value = promoDiscountPercent,
                onValueChange = onPromoDiscountPercentChange,
                label = { Text("Diskon %") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateActionField(
                label = "Mulai",
                value = promoStartDate,
                placeholder = "Pilih tanggal mulai",
                onClick = onPickPromoStartDate,
                modifier = Modifier.weight(1f),
            )

            DateActionField(
                label = "Selesai",
                value = promoEndDate,
                placeholder = "Pilih tanggal selesai",
                onClick = onPickPromoEndDate,
                modifier = Modifier.weight(1f),
            )
        }

        if (promoPrice.isNotBlank()) {
            Text(
                text = "Harga promo tersimpan: ${formatRupiah(promoPriceValue)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (promoDiscountPercent.isNotBlank() && selectedItem != null) {
            val percent = promoDiscountPercent.toIntOrNull()?.coerceIn(0, 95) ?: 0
            val calculatedPrice = (normalTotal - (normalTotal * percent / 100L)).coerceAtLeast(0L)

            Text(
                text = "Estimasi harga setelah diskon: ${formatRupiah(calculatedPrice)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Simpan Promo")
        }
    }
}

@Composable
private fun DateActionField(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (value.isBlank()) placeholder else value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RecommendationListCard(
    title: String,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
        Text(emptyMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecommendationBundleRow(
    bundle: BundleDraft,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard {
        ProductImageBanner(
            imageUrl = bundle.imageUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
        AppInfoLine("Bundle", bundle.name, emphasized = true)
        AppInfoLine("Harga", formatRupiah(bundle.price))
        AppInfoLine(
            "Periode",
            "${bundle.startDate.ifBlank { "-" }} s/d ${bundle.endDate.ifBlank { "-" }}",
        )
        Text(
            "Items: ${bundle.items.joinToString { "${it.itemName} x${it.qty}" }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Text("Edit Bundle")
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text("Hapus Bundle")
            }
        }
    }
}

@Composable
private fun RecommendationPromoRow(
    promo: PromoDraft,
    onDelete: () -> Unit,
) {
    val qty = promo.qty.coerceAtLeast(1)

    AppCard {
        AppInfoLine("Promo", promo.name, emphasized = true)
        AppInfoLine("Produk", "${promo.itemName} x$qty")
        AppInfoLine("Harga Promo Total", formatRupiah(promo.promoPrice))
        AppInfoLine("Diskon", "${promo.discountPercent}%")
        AppInfoLine(
            "Periode",
            "${promo.startDate.ifBlank { "-" }} s/d ${promo.endDate.ifBlank { "-" }}",
        )

        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("Hapus Promo")
        }
    }
}