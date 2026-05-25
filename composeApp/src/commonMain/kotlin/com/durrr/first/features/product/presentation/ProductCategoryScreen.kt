package com.durrr.first.features.product.presentation

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.CatalogNameRules
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.ui.design.AppTheme

private val FigmaBlue = Color(0xFF273BBF)
private val FigmaBorder = Color(0xFFD5D9E2)

@Composable
fun ProductCategoryScreen(
    repo: MenuRepository,
    settingsRepository: SettingsRepository,
) {
    var groups by remember { mutableStateOf(emptyList<GroupItem>()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var categoryName by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun currentOutletId(): String {
        return settingsRepository.resolveOutletId()
    }

    fun refresh() {
        groups = repo.getGroups(currentOutletId()).sortedBy { it.order }
    }

    fun normalizeGroupOrder() {
        repo.getGroups(currentOutletId())
            .sortedBy { it.order }
            .forEachIndexed { index, group ->
                val expectedOrder = index + 1
                if (group.order != expectedOrder) {
                    repo.upsertGroup(group.copy(order = expectedOrder), currentOutletId())
                }
            }
    }

    fun startNew() {
        editingId = null
        categoryName = ""
    }

    fun startEdit(group: GroupItem) {
        editingId = group.id
        categoryName = group.name
    }

    fun save() {
        val name = CatalogNameRules.normalize(categoryName)
        if (name.isBlank()) {
            statusMessage = "Nama kategori wajib ASCII terbaca (maks ${CatalogNameRules.MAX_LENGTH} karakter)."
            return
        }
        val normalizedName = normalizeCategoryNameKey(name)
        val duplicate = groups.firstOrNull {
            it.id != editingId && normalizeCategoryNameKey(it.name) == normalizedName
        }
        if (duplicate != null) {
            statusMessage = "Kategori \"$name\" sudah ada. Pakai nama lain."
            return
        }

        val existingGroup = groups.firstOrNull { it.id == editingId }
        val nextOrder = (groups.maxOfOrNull { it.order } ?: 0) + 1
        val resolvedGroupId = editingId ?: IdGenerator.newCategoryId(
            categoryName = name,
            existingGroupIds = groups.map { it.id },
        )
        repo.upsertGroup(
            GroupItem(
                id = resolvedGroupId,
                name = name,
                order = existingGroup?.order ?: nextOrder,
                outletId = currentOutletId(),
            ),
            currentOutletId(),
        )
        normalizeGroupOrder()
        refresh()
        statusMessage = if (editingId == null) "Kategori ditambahkan." else "Kategori diperbarui."
        startNew()
    }

    fun delete(group: GroupItem) {
        runCatching {
            repo.deleteGroup(group.id, currentOutletId())
        }.onSuccess {
            normalizeGroupOrder()
            refresh()
            if (editingId == group.id) startNew()
            statusMessage = "Kategori dihapus."
        }.onFailure {
            statusMessage = "Gagal hapus kategori. Pastikan kategori tidak dipakai item aktif."
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        startNew()
    }

    ProductCategoryContent(
        groups = groups,
        editingId = editingId,
        categoryName = categoryName,
        onCategoryNameChange = { categoryName = it },
        statusMessage = statusMessage,
        onSave = ::save,
        onReset = ::startNew,
        onEdit = ::startEdit,
        onDelete = ::delete,
    )
}

private fun normalizeCategoryNameKey(name: String): String {
    return CatalogNameRules.normalize(name)
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
}

@Composable
private fun ProductCategoryContent(
    groups: List<GroupItem>,
    editingId: String?,
    categoryName: String,
    onCategoryNameChange: (String) -> Unit,
    statusMessage: String?,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onEdit: (GroupItem) -> Unit,
    onDelete: (GroupItem) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FigmaBorder, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (editingId == null) "Tambah Kategori" else "Edit Kategori",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = onCategoryNameChange,
                    label = { Text("Nama kategori") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue, contentColor = Color.White),
                    ) {
                        Text("Simpan")
                    }
                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF374151)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBorder),
                    ) {
                        Text("Reset")
                    }
                }
                if (!statusMessage.isNullOrBlank()) {
                    Text(statusMessage.orEmpty(), color = Color(0xFF4B5563))
                }
            }

            Text("Daftar Kategori", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, FigmaBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onEdit(group) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5668CA), contentColor = Color.White),
                            ) {
                                Text("Edit")
                            }
                            Button(
                                onClick = { onDelete(group) },
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
}

@Preview
@Composable
private fun ProductCategoryScreenPreview() {
    AppTheme {
        var groups by remember {
            mutableStateOf(
                listOf(
                    GroupItem("grp1", "Minuman", 1),
                    GroupItem("grp2", "Makanan", 2),
                    GroupItem("grp3", "Snack", 3),
                ),
            )
        }
        var editingId by remember { mutableStateOf<String?>(null) }
        var categoryName by remember { mutableStateOf("Dessert") }
        var statusMessage by remember { mutableStateOf<String?>(null) }

        ProductCategoryContent(
            groups = groups,
            editingId = editingId,
            categoryName = categoryName,
            onCategoryNameChange = { categoryName = it },
            statusMessage = statusMessage,
            onSave = { statusMessage = "Preview save clicked." },
            onReset = {
                editingId = null
                categoryName = ""
            },
            onEdit = { group ->
                editingId = group.id
                categoryName = group.name
            },
            onDelete = { group ->
                groups = groups.filterNot { it.id == group.id }
                statusMessage = "Preview delete ${group.name}"
            },
        )
    }
}
