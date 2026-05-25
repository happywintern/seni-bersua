package com.durrr.first.features.product.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.durrr.first.core.utils.formatNumber
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.CatalogNameRules
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.model.ModifierGroupBundle
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.media.ProductImageBanner
import kotlinx.coroutines.launch

private val FigmaBlue = Color(0xFF273BBF)
private val FigmaBorder = Color(0xFFD5D9E2)
private val HiddenProductCategoryIds = setOf(
    "grp-rekomendasi-bundle",
    "grp-rekomendasi-promo",
)

private fun isLocalDeviceImageUri(value: String?): Boolean {
    val normalized = value?.trim().orEmpty()
    return normalized.startsWith("content://", ignoreCase = true) ||
        normalized.startsWith("file://", ignoreCase = true)
}

private const val PRICE_MAX_DIGITS = 12
private const val PRICE_MAX_VALUE = 999_999_999_999L

private fun normalizeCurrencyInput(value: String, maxDigits: Int = PRICE_MAX_DIGITS): String {
    return value.filter(Char::isDigit).take(maxDigits)
}

private fun parseCurrencyInput(raw: String): Long? {
    val digits = raw.filter(Char::isDigit)
    if (digits.isBlank()) return null
    if (digits.length > PRICE_MAX_DIGITS) return null
    return digits.toLongOrNull()
}

private fun formatCurrencyInput(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.isBlank()) return ""
    return formatNumber(digits.toLongOrNull() ?: 0L)
}

private fun dedupeGroupsByName(
    groups: List<GroupItem>,
    selectedGroupId: String?,
): List<GroupItem> {
    val mapByName = linkedMapOf<String, GroupItem>()
    groups.sortedBy { it.order }.forEach { group ->
        val key = group.name.trim().lowercase()
        val existing = mapByName[key]
        if (existing == null || group.id == selectedGroupId) {
            mapByName[key] = group
        }
    }
    return mapByName.values.toList()
}

@Composable
fun ProductEditorScreen(
    repo: MenuRepository,
    settingsRepository: SettingsRepository,
    menuSyncRepository: MenuSyncRepository,
    pickImage: ((String?) -> Unit) -> Unit = { onPicked -> onPicked(null) },
    uploadMenuImage: suspend (baseUrl: String, localUri: String, outletId: String) -> String? = { _, _, _ -> null },
    itemId: String?,
    onManageModifiers: () -> Unit = {},
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf(emptyList<GroupItem>()) }
    var modifierBundles by remember { mutableStateOf(emptyList<ModifierGroupBundle>()) }
    var itemName by remember { mutableStateOf("") }
    var itemCode by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var itemImageUrl by remember { mutableStateOf("") }
    var itemActive by remember { mutableStateOf(true) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var selectedModifierGroupIds by remember { mutableStateOf(setOf<String>()) }
    var showGroupDropdown by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    fun serverBaseUrl(): String? = settingsRepository.getOptionalServerBaseUrl()

    LaunchedEffect(itemId) {
        val allGroups = repo.getGroups(currentOutletId())
            .filterNot { it.id in HiddenProductCategoryIds }
        modifierBundles = repo.getModifierGroupBundles(currentOutletId())
        if (itemId.isNullOrBlank()) {
            groups = dedupeGroupsByName(allGroups, selectedGroupId = null)
            itemName = ""
            itemCode = ""
            itemPrice = ""
            itemImageUrl = ""
            itemActive = true
            selectedGroupId = null
            selectedModifierGroupIds = emptySet()
            return@LaunchedEffect
        }

        val item = repo.getItems(currentOutletId()).firstOrNull { it.id == itemId } ?: return@LaunchedEffect
        groups = dedupeGroupsByName(allGroups, selectedGroupId = item.groupId)
        itemName = item.name
        itemCode = item.code.orEmpty()
        itemPrice = item.price.toString()
        itemImageUrl = item.imageUrl.orEmpty()
        itemActive = item.isActive
        selectedGroupId = item.groupId
        selectedModifierGroupIds = repo.getModifierGroupIdsForItem(item.id, currentOutletId())
    }

    fun save() {
        if (itemName.length > CatalogNameRules.MAX_LENGTH) {
            statusMessage = "Nama barang maksimal ${CatalogNameRules.MAX_LENGTH} karakter."
            return
        }
        val name = CatalogNameRules.normalize(itemName)
        val price = parseCurrencyInput(itemPrice)
        val priceDigitsCount = itemPrice.filter(Char::isDigit).length
        if (name.isBlank()) {
            statusMessage = "Nama barang wajib ASCII terbaca (maks ${CatalogNameRules.MAX_LENGTH} karakter)."
            return
        }
        if (priceDigitsCount > PRICE_MAX_DIGITS) {
            statusMessage = "Harga jual maksimal 12 digit."
            return
        }
        if (price == null || price < 0L) {
            statusMessage = "Harga jual harus angka valid."
            return
        }
        if (price > PRICE_MAX_VALUE) {
            statusMessage = "Harga jual maksimal ${formatNumber(PRICE_MAX_VALUE)} (12 digit)."
            return
        }

        val outletId = currentOutletId()
        val existingItems = repo.getItems(outletId)
        val selectedGroupName = groups.firstOrNull { it.id == selectedGroupId }?.name
        val resolvedItemId = itemId ?: IdGenerator.newCatalogItemId(
            itemName = name,
            categoryName = selectedGroupName,
            existingItemIds = existingItems.map { it.id },
            existingIdsInCategory = existingItems
                .filter { it.groupId == selectedGroupId }
                .map { it.id },
        )

        scope.launch {
            var resolvedImageUrl = itemImageUrl.trim().ifBlank { null }
            val baseUrl = serverBaseUrl()
            var localImagePendingServerUpload = false
            var uploadErrorMessage: String? = null
            if (
                !baseUrl.isNullOrBlank() &&
                !resolvedImageUrl.isNullOrBlank() &&
                isLocalDeviceImageUri(resolvedImageUrl)
            ) {
                val uploadResult = runCatching {
                    uploadMenuImage(baseUrl, resolvedImageUrl, outletId)
                }
                val uploadedImageUrl = uploadResult.getOrNull()
                if (uploadedImageUrl.isNullOrBlank()) {
                    uploadErrorMessage = uploadResult.exceptionOrNull()?.message
                }
                if (!uploadedImageUrl.isNullOrBlank()) {
                    resolvedImageUrl = uploadedImageUrl
                    itemImageUrl = uploadedImageUrl
                } else {
                    localImagePendingServerUpload = true
                }
            }

            val model = Item(
                id = resolvedItemId,
                name = name,
                price = price,
                groupId = selectedGroupId,
                code = itemCode.trim().ifBlank { null },
                imageUrl = resolvedImageUrl,
                isActive = itemActive,
                outletId = outletId,
            )
            repo.upsertItem(model, outletId)
            repo.assignModifierGroupsToItem(model.id, selectedModifierGroupIds.toList(), outletId)

            if (!baseUrl.isNullOrBlank() && !localImagePendingServerUpload) runCatching {
                menuSyncRepository.pushToServer(baseUrl, outletId)
            }
            if (localImagePendingServerUpload) {
                val reason = uploadErrorMessage?.takeIf { it.isNotBlank() } ?: "Koneksi/server upload belum siap."
                statusMessage = "Produk tersimpan lokal. Foto belum terupload ke server. Detail: $reason"
                return@launch
            }
            onSaved()
        }
    }

    ProductEditorContent(
        title = if (itemId == null) "Tambah Barang" else "Edit Barang",
        groups = groups,
        modifierBundles = modifierBundles,
        selectedGroupId = selectedGroupId,
        showGroupDropdown = showGroupDropdown,
        onOpenGroupDropdown = { showGroupDropdown = true },
        onDismissGroupDropdown = { showGroupDropdown = false },
        onSelectGroup = {
            selectedGroupId = it
            showGroupDropdown = false
        },
        itemName = itemName,
        onItemNameChange = { itemName = it },
        itemCode = itemCode,
        onItemCodeChange = { itemCode = it },
        itemPrice = itemPrice,
        onItemPriceChange = { itemPrice = normalizeCurrencyInput(it) },
        itemImageUrl = itemImageUrl,
        onPickImage = {
            pickImage { uri ->
                if (!uri.isNullOrBlank()) {
                    itemImageUrl = uri
                }
            }
        },
        onClearImage = { itemImageUrl = "" },
        itemActive = itemActive,
        onItemActiveChange = { itemActive = it },
        selectedModifierGroupIds = selectedModifierGroupIds,
        onToggleModifierGroup = { modifierGroupId ->
            selectedModifierGroupIds = if (selectedModifierGroupIds.contains(modifierGroupId)) {
                selectedModifierGroupIds - modifierGroupId
            } else {
                selectedModifierGroupIds + modifierGroupId
            }
        },
        onManageModifiers = onManageModifiers,
        statusMessage = statusMessage,
        onSave = ::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorContent(
    title: String,
    groups: List<GroupItem>,
    modifierBundles: List<ModifierGroupBundle>,
    selectedGroupId: String?,
    showGroupDropdown: Boolean,
    onOpenGroupDropdown: () -> Unit,
    onDismissGroupDropdown: () -> Unit,
    onSelectGroup: (String) -> Unit,
    itemName: String,
    onItemNameChange: (String) -> Unit,
    itemCode: String,
    onItemCodeChange: (String) -> Unit,
    itemPrice: String,
    onItemPriceChange: (String) -> Unit,
    itemImageUrl: String,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    itemActive: Boolean,
    onItemActiveChange: (Boolean) -> Unit,
    selectedModifierGroupIds: Set<String>,
    onToggleModifierGroup: (String) -> Unit,
    onManageModifiers: () -> Unit,
    statusMessage: String?,
    onSave: () -> Unit,
) {
    val selectedModifierCount = selectedModifierGroupIds.size
    val itemNameTooLong = itemName.length > CatalogNameRules.MAX_LENGTH
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 920.dp)
                .align(Alignment.TopCenter)
                .border(1.dp, FigmaBorder, RoundedCornerShape(18.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (title == "Tambah Barang") {
                    "Isi detail produk baru lalu pilih modifier group bila diperlukan."
                } else {
                    "Perbarui detail produk dan pengaturan modifier group."
                },
                color = Color(0xFF666666),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = itemName,
                onValueChange = onItemNameChange,
                label = { Text("Nama barang") },
                modifier = Modifier.fillMaxWidth(),
                isError = itemNameTooLong,
                supportingText = {
                    Text(
                        text = if (itemNameTooLong) {
                            "Nama barang maksimal ${CatalogNameRules.MAX_LENGTH} karakter. Saat ini ${itemName.length} karakter."
                        } else {
                            "${itemName.length}/${CatalogNameRules.MAX_LENGTH} karakter"
                        },
                        color = if (itemNameTooLong) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
            OutlinedTextField(
                value = itemCode,
                onValueChange = onItemCodeChange,
                label = { Text("Kode barang") },
                supportingText = { Text("Opsional. Gunakan kode unik untuk pencarian cepat.") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = formatCurrencyInput(itemPrice),
                onValueChange = onItemPriceChange,
                label = { Text("Harga jual") },
                supportingText = { Text("Format otomatis rupiah. Maksimal 12 digit (contoh: 32000 jadi 32.000).") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Foto Produk",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Gunakan gambar dari galeri (lebih cepat daripada URL).",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPickImage,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FigmaBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBorder),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Text(if (itemImageUrl.isBlank()) "Pilih Foto dari Galeri" else "Ganti Foto")
                }
                if (itemImageUrl.isNotBlank()) {
                    Button(
                        onClick = onClearImage,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFFB42318),
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBorder),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        Text("Hapus Foto")
                    }
                }
            }
            if (itemImageUrl.isNotBlank()) {
                Text(
                    text = if (itemImageUrl.startsWith("http", ignoreCase = true)) {
                        "Sumber: URL"
                    } else {
                        "Sumber: Galeri device"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                )
            }
            ProductImageBanner(
                imageUrl = itemImageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            ExposedDropdownMenuBox(
                expanded = showGroupDropdown,
                onExpandedChange = { expanded ->
                    if (groups.isNotEmpty()) {
                        if (expanded) onOpenGroupDropdown() else onDismissGroupDropdown()
                    }
                },
            ) {
                OutlinedTextField(
                    value = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Kategori") },
                    placeholder = { Text("Pilih kategori") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showGroupDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true,
                        ),
                )
                ExposedDropdownMenu(
                    expanded = showGroupDropdown,
                    onDismissRequest = onDismissGroupDropdown,
                ) {
                    if (groups.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Belum ada kategori") },
                            onClick = onDismissGroupDropdown,
                        )
                    } else {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = { onSelectGroup(group.id) },
                            )
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Status aktif")
                Switch(checked = itemActive, onCheckedChange = onItemActiveChange)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Modifier groups ($selectedModifierCount dipilih)",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(
                        onClick = onManageModifiers,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FigmaBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBorder),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        Text("Kelola modifier")
                    }
                }
                if (modifierBundles.isEmpty()) {
                    Text("Belum ada modifier group. Tambahkan dulu seperti Sugar Level, Ice Level, Size, atau Add-ons.")
                } else {
                    modifierBundles.forEach { bundle ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, FigmaBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Checkbox(
                                    checked = selectedModifierGroupIds.contains(bundle.group.id),
                                    onCheckedChange = { onToggleModifierGroup(bundle.group.id) },
                                )
                                Column {
                                    Text(bundle.group.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (bundle.group.selectionType == "MULTIPLE") {
                                            "Bisa pilih sampai ${bundle.group.maxSelection}"
                                        } else {
                                            "Pilih satu"
                                        },
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Text(
                                bundle.options.joinToString(", ") { option ->
                                    if (option.priceDelta == 0L) option.name else "${option.name} (+${option.priceDelta})"
                                },
                                color = Color(0xFF555555),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (selectedModifierCount == 0) {
                        Text(
                            "Tidak ada modifier group dipilih. Produk ini akan dijual tanpa opsi tambahan.",
                            color = Color(0xFF666666),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (!statusMessage.isNullOrBlank()) {
                Text(statusMessage.orEmpty(), color = Color(0xFFB91C1C))
            }
            Button(
                onClick = onSave,
                enabled = !itemNameTooLong,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue, contentColor = Color.White),
            ) {
                Text("Simpan")
            }
        }
    }
}

@Preview
@Composable
private fun ProductEditorScreenPreview() {
    AppTheme {
        var selectedGroupId by remember { mutableStateOf<String?>(null) }
        var showGroupDropdown by remember { mutableStateOf(false) }
        var itemName by remember { mutableStateOf("Americano") }
        var itemCode by remember { mutableStateOf("MN003") }
        var itemPrice by remember { mutableStateOf("22000") }
        var itemImageUrl by remember { mutableStateOf("") }
        var itemActive by remember { mutableStateOf(true) }

        ProductEditorContent(
            title = "Tambah Barang",
            groups = listOf(
                GroupItem("grp1", "Minuman", 1),
                GroupItem("grp2", "Makanan", 2),
            ),
            modifierBundles = listOf(
                ModifierGroupBundle(
                    group = com.durrr.first.domain.model.ModifierGroup("mod1", "Sugar Level", "SINGLE", true, 1),
                    options = listOf(
                        com.durrr.first.domain.model.ModifierOption("opt1", "mod1", "0%", 0, 1, false),
                        com.durrr.first.domain.model.ModifierOption("opt2", "mod1", "50%", 0, 2, true),
                    ),
                ),
            ),
            selectedGroupId = selectedGroupId,
            showGroupDropdown = showGroupDropdown,
            onOpenGroupDropdown = { showGroupDropdown = true },
            onDismissGroupDropdown = { showGroupDropdown = false },
            onSelectGroup = {
                selectedGroupId = it
                showGroupDropdown = false
            },
            itemName = itemName,
            onItemNameChange = { itemName = it },
            itemCode = itemCode,
            onItemCodeChange = { itemCode = it },
            itemPrice = itemPrice,
            onItemPriceChange = { itemPrice = it },
            itemImageUrl = itemImageUrl,
            onPickImage = {},
            onClearImage = { itemImageUrl = "" },
            itemActive = itemActive,
            onItemActiveChange = { itemActive = it },
            selectedModifierGroupIds = setOf("mod1"),
            onToggleModifierGroup = {},
            onManageModifiers = {},
            statusMessage = null,
            onSave = {},
        )
    }
}
