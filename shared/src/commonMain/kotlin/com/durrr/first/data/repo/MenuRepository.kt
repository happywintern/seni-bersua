package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.model.ModifierGroup
import com.durrr.first.domain.model.ModifierGroupBundle
import com.durrr.first.domain.model.ModifierOption

class MenuRepository(private val db: TokoDatabase) {
    fun getGroups(outletId: String = SettingsRepository.DEFAULT_OUTLET_ID): List<GroupItem> {
        return db.tokoQueries.selectAllGroups(outletId).executeAsList().map {
            GroupItem(
                id = it.id_group_item,
                name = CatalogNameRules.normalizeOrFallback(it.nama, fallback = "Kategori"),
                order = it.urutan.toInt(),
                outletId = it.outlet_id,
            )
        }
    }

    fun upsertGroup(
        group: GroupItem,
        outletId: String = group.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        val normalizedName = CatalogNameRules.normalizeOrFallback(group.name, fallback = "Kategori")
        db.tokoQueries.upsertGroupItem(group.id, normalizedName, group.order.toLong(), outletId)
    }

    fun deleteGroup(groupId: String, outletId: String = SettingsRepository.DEFAULT_OUTLET_ID) {
        db.tokoQueries.deleteGroupItem(groupId, outletId)
    }

    fun getModifierGroups(outletId: String = SettingsRepository.DEFAULT_OUTLET_ID): List<ModifierGroup> {
        return db.tokoQueries.selectAllModifierGroups(outletId).executeAsList().map {
            ModifierGroup(
                id = it.id_modifier_group,
                name = CatalogNameRules.normalizeOrFallback(it.nama, fallback = "Modifier"),
                selectionType = it.selection_type,
                isRequired = it.is_required.toLong() == 1L,
                maxSelection = it.max_selection.toInt(),
                outletId = it.outlet_id,
            )
        }
    }

    fun getModifierOptions(
        groupId: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): List<ModifierOption> {
        return db.tokoQueries.selectModifierOptionsByGroup(groupId, outletId).executeAsList().map {
            ModifierOption(
                id = it.id_modifier_option,
                groupId = it.id_modifier_group,
                name = CatalogNameRules.normalizeOrFallback(it.nama, fallback = "Option"),
                priceDelta = it.price_delta.toLong(),
                order = it.urutan.toInt(),
                isDefault = it.is_default.toLong() == 1L,
                outletId = it.outlet_id,
            )
        }
    }

    fun getModifierGroupBundles(outletId: String = SettingsRepository.DEFAULT_OUTLET_ID): List<ModifierGroupBundle> {
        return getModifierGroups(outletId).map { group ->
            ModifierGroupBundle(
                group = group,
                options = getModifierOptions(group.id, outletId),
            )
        }
    }

    fun upsertModifierGroup(
        group: ModifierGroup,
        options: List<ModifierOption>,
        outletId: String = group.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        val normalizedGroupName = CatalogNameRules.normalizeOrFallback(group.name, fallback = "Modifier")
        db.tokoQueries.upsertModifierGroup(
            group.id,
            normalizedGroupName,
            group.selectionType,
            if (group.isRequired) 1L else 0L,
            group.maxSelection.toLong(),
            outletId,
        )
        db.tokoQueries.deleteModifierOptionsByGroup(group.id, outletId)
        options.forEachIndexed { index, option ->
            db.tokoQueries.insertModifierOption(
                option.id,
                group.id,
                CatalogNameRules.normalizeOrFallback(option.name, fallback = "Option ${index + 1}"),
                option.priceDelta,
                option.order.toLong(),
                if (option.isDefault) 1L else 0L,
                outletId,
            )
        }
    }

    fun deleteModifierGroup(groupId: String, outletId: String = SettingsRepository.DEFAULT_OUTLET_ID) {
        db.tokoQueries.deleteProductModifierLinksByGroup(groupId, outletId)
        db.tokoQueries.deleteModifierOptionsByGroup(groupId, outletId)
        db.tokoQueries.deleteModifierGroup(groupId, outletId)
    }

    fun getModifierGroupIdsForItem(
        itemId: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): Set<String> {
        return db.tokoQueries.selectModifierGroupLinksByItem(itemId, outletId).executeAsList().toSet()
    }

    fun assignModifierGroupsToItem(
        itemId: String,
        groupIds: List<String>,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        db.tokoQueries.deleteProductModifierLinksByItem(itemId, outletId)
        groupIds.distinct().forEach { groupId ->
            db.tokoQueries.insertProductModifierLink(itemId, groupId, outletId)
        }
    }

    fun getItems(outletId: String = SettingsRepository.DEFAULT_OUTLET_ID): List<Item> {
        return db.tokoQueries.selectAllItems(outletId).executeAsList().map {
            Item(
                id = it.id_item,
                name = CatalogNameRules.normalizeOrFallback(it.nama, fallback = "Item"),
                price = parseLong(it.harga),
                groupId = it.id_group_item,
                code = it.kode,
                imageUrl = it.keterangan?.trim()?.ifBlank { null },
                isActive = it.is_delete == null || it.is_delete == "0",
                outletId = it.outlet_id,
            )
        }
    }

    fun upsertItem(
        item: Item,
        outletId: String = item.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        val normalizedName = CatalogNameRules.normalizeOrFallback(item.name, fallback = "Item")
        db.tokoQueries.upsertItem(
            id_item = item.id,
            nama = normalizedName,
            harga = item.price.toString(),
            id_group_item = item.groupId,
            is_delete = if (item.isActive) "0" else "1",
            kode = item.code,
            keterangan = item.imageUrl?.trim()?.ifBlank { null },
            jenis_item = null,
            outlet_id = outletId,
        )
    }

    fun setItemOrderable(
        itemId: String,
        isOrderable: Boolean,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        if (isOrderable) {
            db.tokoQueries.reactivateItem(itemId, outletId)
        } else {
            db.tokoQueries.softDeleteItem(itemId, outletId)
        }
    }

    fun deleteItem(itemId: String, outletId: String = SettingsRepository.DEFAULT_OUTLET_ID) {
        db.tokoQueries.softDeleteItem(itemId, outletId)
    }

    fun hardDeleteItem(itemId: String, outletId: String = SettingsRepository.DEFAULT_OUTLET_ID) {
        db.transaction {
            db.tokoQueries.deleteProductModifierLinksByItem(itemId, outletId)
            db.tokoQueries.deleteStockLedgerByItem(itemId, outletId)
            db.tokoQueries.deleteStockThresholdByItem(itemId, outletId)
            db.tokoQueries.deleteStockBalanceByItem(itemId, outletId)
            db.tokoQueries.clearTransaksiDetailItemReferenceByItem(itemId)
            db.tokoQueries.clearOrderItemReferenceByItem(itemId)
            db.tokoQueries.hardDeleteItem(itemId, outletId)
        }
    }

    private fun parseLong(value: String?): Long = value?.toLongOrNull() ?: 0L
}
