package com.durrr.first.data.repo

import com.durrr.first.domain.model.Item
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.ModifierGroup
import com.durrr.first.domain.model.ModifierOption
import com.durrr.first.network.ServerApiClient
import com.durrr.first.network.dto.AssignProductModifiersRequest
import com.durrr.first.network.dto.ServerModifierOptionDto
import com.durrr.first.network.dto.UpsertModifierGroupRequest
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.delay

class MenuSyncRepository(
    private val menuRepository: MenuRepository,
    private val apiClient: ServerApiClient,
    private val settingsRepository: SettingsRepository? = null,
) {
    suspend fun resetServerAllData(
        baseUrl: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        val bearerToken = requireBearerTokenForServerWrite(outletId)
        apiClient.resetAllData(
            baseUrl = baseUrl,
            outletId = outletId,
            bearerToken = bearerToken,
        )
    }

    suspend fun pullFromServer(
        baseUrl: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): Int {
        val localGroups = menuRepository.getGroups(outletId)
        val localGroupNameById = localGroups.associate { it.id to it.name }.toMutableMap()
        val localGroupIdByName = localGroups
            .sortedBy { it.order }
            .associate { normalizeGroupKey(it.name) to it.id }
            .toMutableMap()

        val remoteCatalog = runCatching {
            apiClient.fetchMenuCatalog(baseUrl, outletId)
        }.getOrElse {
            val fallbackItems = apiClient.fetchMenu(baseUrl, outletId)
            return fallbackItems.also { remote ->
                val localById = menuRepository.getItems(outletId).associateBy { it.id }
                remote.forEach { remoteItem ->
                    val local = localById[remoteItem.id]
                    val normalizedGroupName = resolveGroupName(
                        explicitName = remoteItem.groupName,
                        existingName = local?.groupId?.let { localGroupNameById[it] },
                        itemName = remoteItem.name,
                    )
                    val canonicalGroupId = resolveCanonicalGroupId(
                        remoteGroupId = remoteItem.groupId,
                        localGroupId = local?.groupId,
                        groupName = normalizedGroupName,
                        localGroupIdByName = localGroupIdByName,
                    )
                    if (!canonicalGroupId.isNullOrBlank()) {
                        menuRepository.upsertGroup(
                            GroupItem(
                                id = canonicalGroupId,
                                name = normalizedGroupName,
                                order = 0,
                                outletId = remoteItem.outletId ?: outletId,
                            ),
                            outletId = remoteItem.outletId ?: outletId,
                        )
                        localGroupNameById[canonicalGroupId] = normalizedGroupName
                        val groupKey = normalizeGroupKey(normalizedGroupName)
                        if (!localGroupIdByName.containsKey(groupKey)) {
                            localGroupIdByName[groupKey] = canonicalGroupId
                        }
                    }
                    menuRepository.upsertItem(
                        Item(
                            id = remoteItem.id,
                            name = remoteItem.name,
                            price = remoteItem.price,
                            groupId = canonicalGroupId,
                            code = local?.code,
                            imageUrl = local?.imageUrl,
                            isActive = true,
                            outletId = remoteItem.outletId ?: outletId,
                        ),
                        outletId = remoteItem.outletId ?: outletId,
                    )
                }
                reconcileDuplicateGroupsByName(outletId)
            }.size
        }
        val remote = remoteCatalog.items
        val localById = menuRepository.getItems(outletId).associateBy { it.id }
        remote.forEach { remoteItem ->
            val local = localById[remoteItem.id]
            val normalizedGroupName = resolveGroupName(
                explicitName = remoteItem.groupName,
                existingName = local?.groupId?.let { localGroupNameById[it] },
                itemName = remoteItem.name,
            )
            val canonicalGroupId = resolveCanonicalGroupId(
                remoteGroupId = remoteItem.groupId,
                localGroupId = local?.groupId,
                groupName = normalizedGroupName,
                localGroupIdByName = localGroupIdByName,
            )
            if (!canonicalGroupId.isNullOrBlank()) {
                menuRepository.upsertGroup(
                    GroupItem(
                        id = canonicalGroupId,
                        name = normalizedGroupName,
                        order = 0,
                        outletId = remoteItem.outletId ?: outletId,
                    ),
                    outletId = remoteItem.outletId ?: outletId,
                )
                localGroupNameById[canonicalGroupId] = normalizedGroupName
                val groupKey = normalizeGroupKey(normalizedGroupName)
                if (!localGroupIdByName.containsKey(groupKey)) {
                    localGroupIdByName[groupKey] = canonicalGroupId
                }
            }
            menuRepository.upsertItem(
                Item(
                    id = remoteItem.id,
                    name = remoteItem.name,
                    price = remoteItem.price,
                    groupId = canonicalGroupId,
                    code = local?.code,
                    imageUrl = local?.imageUrl,
                    isActive = true,
                    outletId = remoteItem.outletId ?: outletId,
                ),
                outletId = remoteItem.outletId ?: outletId,
            )
        }

        remoteCatalog.modifierGroups.forEach { group ->
            val normalizedOptions = group.options.mapIndexed { index, option ->
                ModifierOption(
                    id = option.id,
                    groupId = group.id,
                    name = option.name,
                    priceDelta = option.priceDelta,
                    order = option.order.takeIf { it >= 0 } ?: index,
                    isDefault = option.isDefault,
                    outletId = outletId,
                )
            }
            menuRepository.upsertModifierGroup(
                group = ModifierGroup(
                    id = group.id,
                    name = group.name,
                    selectionType = group.selectionType,
                    isRequired = group.isRequired,
                    maxSelection = group.maxSelection,
                    outletId = outletId,
                ),
                options = normalizedOptions,
                outletId = outletId,
            )
        }
        remoteCatalog.productModifierLinks.forEach { link ->
            menuRepository.assignModifierGroupsToItem(
                itemId = link.itemId,
                groupIds = link.modifierGroupIds,
                outletId = outletId,
            )
        }
        reconcileDuplicateGroupsByName(outletId)
        return remote.size
    }

    suspend fun pushToServer(
        baseUrl: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): Int {
        val bearerToken = requireBearerTokenForServerWrite(outletId)
        val modifierBundles = menuRepository.getModifierGroupBundles(outletId)
        modifierBundles.forEach { bundle ->
            withNetworkRetry {
                apiClient.upsertModifierGroup(
                    baseUrl = baseUrl,
                    request = UpsertModifierGroupRequest(
                        id = bundle.group.id,
                        name = bundle.group.name,
                        selectionType = bundle.group.selectionType,
                        isRequired = bundle.group.isRequired,
                        maxSelection = bundle.group.maxSelection,
                        options = bundle.options.map { option ->
                            ServerModifierOptionDto(
                                id = option.id,
                                name = option.name,
                                priceDelta = option.priceDelta,
                                order = option.order,
                                isDefault = option.isDefault,
                            )
                        },
                        outletId = outletId,
                    ),
                    bearerToken = bearerToken,
                )
            }
        }

        val local = menuRepository.getItems(outletId)
        val groupNameById = menuRepository.getGroups(outletId).associate { it.id to it.name }
        local.forEach { item ->
            if (item.isActive) {
                val fallbackGroupName = inferGroupNameFromItemName(item.name)
                val resolvedGroupId = item.groupId?.takeIf { it.isNotBlank() } ?: inferGroupIdFromName(fallbackGroupName)
                val resolvedGroupName = groupNameById[resolvedGroupId]
                    ?.takeIf { it.isNotBlank() && !it.equals("Kategori", ignoreCase = true) }
                    ?: fallbackGroupName
                withNetworkRetry {
                    apiClient.upsertMenuItem(
                        baseUrl = baseUrl,
                        id = item.id,
                        name = item.name,
                        price = item.price,
                        groupId = resolvedGroupId,
                        groupName = resolvedGroupName,
                        outletId = outletId,
                        bearerToken = bearerToken,
                    )
                }
                withNetworkRetry {
                    apiClient.assignProductModifiers(
                        baseUrl = baseUrl,
                        itemId = item.id,
                        request = AssignProductModifiersRequest(
                            modifierGroupIds = menuRepository.getModifierGroupIdsForItem(item.id, outletId).toList(),
                            outletId = outletId,
                        ),
                        bearerToken = bearerToken,
                    )
                }
            } else {
                withNetworkRetry {
                    apiClient.deleteMenuItem(
                        baseUrl = baseUrl,
                        id = item.id,
                        outletId = outletId,
                        bearerToken = bearerToken,
                    )
                }
            }
        }
        return local.size
    }

    suspend fun deleteItemFromServer(
        baseUrl: String,
        itemId: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        val bearerToken = requireBearerTokenForServerWrite(outletId)
        withNetworkRetry {
            apiClient.deleteMenuItem(
                baseUrl = baseUrl,
                id = itemId,
                outletId = outletId,
                bearerToken = bearerToken,
            )
        }
    }

    private fun currentBearerToken(outletId: String): String? {
        return settingsRepository?.getActiveUserServerApiBearerToken(outletId)
    }

    private fun requireBearerTokenForServerWrite(outletId: String): String {
        val token = currentBearerToken(outletId)?.trim().orEmpty()
        if (token.isNotBlank()) return token
        error(
            "Owner auth diperlukan. Pairing server dulu lalu login ulang sebagai Owner."
        )
    }

    private suspend fun <T> withNetworkRetry(
        maxAttempts: Int = 3,
        initialBackoffMs: Long = 400L,
        block: suspend () -> T,
    ): T {
        var attempt = 1
        var backoff = initialBackoffMs
        var lastError: Throwable? = null
        while (attempt <= maxAttempts) {
            try {
                return block()
            } catch (error: Throwable) {
                if (!isRetryableNetworkError(error) || attempt == maxAttempts) {
                    throw error
                }
                lastError = error
                delay(backoff)
                backoff *= 2
                attempt += 1
            }
        }
        throw lastError ?: IllegalStateException("Network retry failed without error detail")
    }

    private fun isRetryableNetworkError(error: Throwable): Boolean {
        return error is IOException ||
            error is SocketTimeoutException ||
            error is ConnectTimeoutException ||
            error is HttpRequestTimeoutException ||
            (error.message?.contains("unexpected end of stream", ignoreCase = true) == true)
    }

    private fun resolveGroupName(
        explicitName: String?,
        existingName: String?,
        itemName: String,
    ): String {
        val explicit = explicitName?.trim().orEmpty()
        if (explicit.isNotBlank() && !explicit.equals("Kategori", ignoreCase = true)) return explicit

        val existing = existingName?.trim().orEmpty()
        if (existing.isNotBlank() && !existing.equals("Kategori", ignoreCase = true)) return existing

        return inferGroupNameFromItemName(itemName)
    }

    private fun resolveCanonicalGroupId(
        remoteGroupId: String?,
        localGroupId: String?,
        groupName: String,
        localGroupIdByName: Map<String, String>,
    ): String? {
        val existingByName = localGroupIdByName[normalizeGroupKey(groupName)]
        if (!existingByName.isNullOrBlank()) return existingByName
        return remoteGroupId?.trim()?.takeIf { it.isNotBlank() }
            ?: localGroupId?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun reconcileDuplicateGroupsByName(
        outletId: String,
    ) {
        val groups = menuRepository.getGroups(outletId)
        val items = menuRepository.getItems(outletId)
        val byName = groups.groupBy { normalizeGroupKey(it.name) }
        byName.values.forEach { sameNameGroups ->
            if (sameNameGroups.size <= 1) return@forEach
            val canonical = sameNameGroups.minWithOrNull(
                compareBy<GroupItem> { it.order }.thenBy { it.id },
            ) ?: return@forEach
            sameNameGroups
                .filter { it.id != canonical.id }
                .forEach { duplicate ->
                    items.filter { it.groupId == duplicate.id }.forEach { item ->
                        menuRepository.upsertItem(
                            item.copy(groupId = canonical.id),
                            outletId = outletId,
                        )
                    }
                    menuRepository.deleteGroup(duplicate.id, outletId)
                }
        }
    }

    private fun normalizeGroupKey(name: String): String {
        return CatalogNameRules.normalize(name)
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
    }

    private fun inferGroupNameFromItemName(itemName: String): String {
        val text = itemName.lowercase()
        return when {
            text.contains("coffee") || text.contains("kopi") || text.contains("latte") ||
                text.contains("espresso") || text.contains("cappuccino") || text.contains("macchiato") -> "Coffee"
            else -> "Non-Coffee"
        }
    }

    private fun inferGroupIdFromName(groupName: String): String {
        val normalized = groupName.trim().lowercase()
            .replace("&", " dan ")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return if (normalized.isBlank()) "grp-umum" else "grp-$normalized"
    }
}
