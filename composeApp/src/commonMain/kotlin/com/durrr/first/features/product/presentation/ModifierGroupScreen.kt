package com.durrr.first.features.product.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.CatalogNameRules
import com.durrr.first.domain.model.ModifierGroup
import com.durrr.first.domain.model.ModifierGroupBundle
import com.durrr.first.domain.model.ModifierOption
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.ui.design.AppTheme

private val ModifierBlue = Color(0xFF273BBF)
private val ModifierBorder = Color(0xFFD5D9E2)
private const val MODIFIER_PRICE_MAX_DIGITS = 12
private const val MODIFIER_PRICE_MAX_VALUE = 999_999_999_999L

private fun normalizeModifierPriceInput(value: String): String {
    return value.filter(Char::isDigit).take(MODIFIER_PRICE_MAX_DIGITS)
}

private fun formatModifierPriceInput(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.isBlank()) return ""
    return formatNumber(digits.toLongOrNull() ?: 0L)
}

private fun parseModifierPriceInput(raw: String): Long? {
    val digits = raw.filter(Char::isDigit)
    if (digits.length > MODIFIER_PRICE_MAX_DIGITS) return null
    if (digits.isBlank()) return 0L
    return digits.toLongOrNull()
}

private data class ModifierOptionDraft(
    val name: String = "",
    val price: String = "0",
)

@Composable
fun ModifierGroupScreen(
    repo: MenuRepository,
    settingsRepository: SettingsRepository,
) {
    var bundles by remember { mutableStateOf(emptyList<ModifierGroupBundle>()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var groupName by remember { mutableStateOf("") }
    var selectionType by remember { mutableStateOf("SINGLE") }
    var required by remember { mutableStateOf(false) }
    var maxSelectionText by remember { mutableStateOf("1") }
    var optionsDrafts by remember { mutableStateOf(listOf(ModifierOptionDraft())) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    fun refresh() {
        bundles = repo.getModifierGroupBundles(currentOutletId())
    }

    fun startNew() {
        editingId = null
        groupName = ""
        selectionType = "SINGLE"
        required = false
        maxSelectionText = "1"
        optionsDrafts = listOf(ModifierOptionDraft())
    }

    fun startEdit(bundle: ModifierGroupBundle) {
        editingId = bundle.group.id
        groupName = bundle.group.name
        selectionType = bundle.group.selectionType
        required = bundle.group.isRequired
        maxSelectionText = bundle.group.maxSelection.toString()
        optionsDrafts = bundle.options.map { 
            ModifierOptionDraft(it.name, it.priceDelta.toString())
        }
    }

    fun save() {
        val name = CatalogNameRules.normalize(groupName)
        if (name.isBlank()) {
            statusMessage = "Nama modifier wajib ASCII terbaca (maks ${CatalogNameRules.MAX_LENGTH} karakter)."
            return
        }

        val maxSelection = when (selectionType) {
            "SINGLE" -> 1
            else -> maxSelectionText.toIntOrNull()?.coerceAtLeast(1) ?: run {
                statusMessage = "Maksimal pilihan harus angka valid."
                return
            }
        }

        val validOptions = optionsDrafts
            .map { it.copy(name = CatalogNameRules.normalize(it.name)) }
            .filter { it.name.isNotBlank() }
        if (validOptions.isEmpty()) {
            statusMessage = "Minimal isi satu opsi modifier dengan nama ASCII."
            return
        }

        val groupId = editingId ?: IdGenerator.newId("modgrp_")
        val options = validOptions.mapIndexed { index, draft ->
            val parsedPrice = parseModifierPriceInput(draft.price) ?: run {
                statusMessage = "Harga opsi modifier maksimal ${formatNumber(MODIFIER_PRICE_MAX_VALUE)} (12 digit)."
                return
            }
            ModifierOption(
                id = IdGenerator.newId("modopt_"),
                groupId = groupId,
                name = draft.name,
                priceDelta = parsedPrice,
                order = index + 1,
                isDefault = index == 0 && selectionType == "SINGLE",
                outletId = currentOutletId(),
            )
        }

        repo.upsertModifierGroup(
            group = ModifierGroup(
                id = groupId,
                name = name,
                selectionType = selectionType,
                isRequired = required,
                maxSelection = maxSelection,
                outletId = currentOutletId(),
            ),
            options = options,
            outletId = currentOutletId(),
        )
        refresh()
        statusMessage = if (editingId == null) "Modifier group ditambahkan." else "Modifier group diperbarui."
        startNew()
    }

    fun delete(bundle: ModifierGroupBundle) {
        repo.deleteModifierGroup(bundle.group.id, currentOutletId())
        refresh()
        if (editingId == bundle.group.id) startNew()
        statusMessage = "Modifier group dihapus."
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    ModifierGroupContent(
        bundles = bundles,
        editingId = editingId,
        groupName = groupName,
        onGroupNameChange = { groupName = it },
        selectionType = selectionType,
        onSelectionTypeChange = {
            selectionType = it
            if (it == "SINGLE") maxSelectionText = "1"
        },
        required = required,
        onRequiredChange = { required = it },
        maxSelectionText = maxSelectionText,
        onMaxSelectionTextChange = { maxSelectionText = it },
        optionsDrafts = optionsDrafts,
        onUpdateOption = { index, draft ->
            optionsDrafts = optionsDrafts.toMutableList().apply { set(index, draft) }
        },
        onAddOption = {
            optionsDrafts = optionsDrafts + ModifierOptionDraft()
        },
        onRemoveOption = { index ->
            if (optionsDrafts.size > 1) {
                optionsDrafts = optionsDrafts.toMutableList().apply { removeAt(index) }
            }
        },
        statusMessage = statusMessage,
        onSave = ::save,
        onReset = ::startNew,
        onEdit = ::startEdit,
        onDelete = ::delete,
    )
}

@Composable
private fun ModifierGroupContent(
    bundles: List<ModifierGroupBundle>,
    editingId: String?,
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    selectionType: String,
    onSelectionTypeChange: (String) -> Unit,
    required: Boolean,
    onRequiredChange: (Boolean) -> Unit,
    maxSelectionText: String,
    onMaxSelectionTextChange: (String) -> Unit,
    optionsDrafts: List<ModifierOptionDraft>,
    onUpdateOption: (Int, ModifierOptionDraft) -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (Int) -> Unit,
    statusMessage: String?,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onEdit: (ModifierGroupBundle) -> Unit,
    onDelete: (ModifierGroupBundle) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ModifierBorder, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (editingId == null) "Tambah Modifier Group" else "Edit Modifier Group",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = onGroupNameChange,
                        label = { Text("Nama group") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Tipe Pilihan",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF475569),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SelectionTypeChip(
                                label = "Pilih satu",
                                selected = selectionType == "SINGLE",
                                onClick = { onSelectionTypeChange("SINGLE") },
                                modifier = Modifier.weight(1f),
                            )
                            SelectionTypeChip(
                                label = "Bisa pilih banyak",
                                selected = selectionType == "MULTIPLE",
                                onClick = { onSelectionTypeChange("MULTIPLE") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (selectionType == "MULTIPLE") {
                        OutlinedTextField(
                            value = maxSelectionText,
                            onValueChange = onMaxSelectionTextChange,
                            label = { Text("Maksimal pilihan") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Wajib dipilih")
                        Switch(checked = required, onCheckedChange = onRequiredChange)
                    }

                    Text("Opsi Modifier", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        optionsDrafts.forEachIndexed { index, draft ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = draft.name,
                                    onValueChange = { onUpdateOption(index, draft.copy(name = it)) },
                                    label = { Text("Nama") },
                                    modifier = Modifier.weight(1.5f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = formatModifierPriceInput(draft.price),
                                    onValueChange = { onUpdateOption(index, draft.copy(price = normalizeModifierPriceInput(it))) },
                                    label = { Text("Harga") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    supportingText = { Text("Maks 12 digit") },
                                )
                                if (optionsDrafts.size > 1) {
                                    IconButton(onClick = { onRemoveOption(index) }) {
                                        Text("\uD83D\uDDD1", color = Color.Red)
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = onAddOption,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ModifierBlue),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ModifierBlue),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("+ Tambah Opsi")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(
                            onClick = onSave,
                            colors = ButtonDefaults.buttonColors(containerColor = ModifierBlue, contentColor = Color.White),
                        ) {
                            Text("Simpan")
                        }
                        Button(
                            onClick = onReset,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF374151)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ModifierBorder),
                        ) {
                            Text("Reset")
                        }
                    }
                    if (!statusMessage.isNullOrBlank()) {
                        Text(statusMessage, color = Color(0xFF4B5563))
                    }
                }
            }

            item {
                Text("Daftar Modifier Group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            items(bundles) { bundle ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ModifierBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(bundle.group.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${if (bundle.group.selectionType == "MULTIPLE") "Multiple" else "Single"} · ${bundle.options.size} opsi",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            bundle.options.joinToString(", ") { option ->
                                if (option.priceDelta == 0L) option.name else "${option.name} (+${option.priceDelta})"
                            },
                            color = Color(0xFF4B5563),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onEdit(bundle) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5668CA), contentColor = Color.White),
                        ) {
                            Text("Edit")
                        }
                        Button(
                            onClick = { onDelete(bundle) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                        ) {
                            Text("Hapus")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ModifierBlue else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) ModifierBlue else ModifierBorder,
        ),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (selected) Color.White else Color(0xFF334155),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Preview
@Composable
private fun ModifierGroupScreenPreview() {
    AppTheme {
        ModifierGroupContent(
            bundles = listOf(
                ModifierGroupBundle(
                    group = ModifierGroup("grp1", "Sugar Level", "SINGLE", true, 1),
                    options = listOf(
                        ModifierOption("opt1", "grp1", "0%", 0, 1, false),
                        ModifierOption("opt2", "grp1", "50%", 0, 2, true),
                        ModifierOption("opt3", "grp1", "100%", 0, 3, false),
                    ),
                ),
            ),
            editingId = null,
            groupName = "Sugar",
            onGroupNameChange = {},
            selectionType = "SINGLE",
            onSelectionTypeChange = {},
            required = true,
            onRequiredChange = {},
            maxSelectionText = "1",
            onMaxSelectionTextChange = {},
            optionsDrafts = listOf(ModifierOptionDraft("Normal", "0"), ModifierOptionDraft("Less", "0")),
            onUpdateOption = { _, _ -> },
            onAddOption = {},
            onRemoveOption = {},
            statusMessage = null,
            onSave = {},
            onReset = {},
            onEdit = {},
            onDelete = {},
        )
    }
}
