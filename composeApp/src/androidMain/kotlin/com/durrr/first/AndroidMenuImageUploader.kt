package com.durrr.first

import android.content.Context
import android.net.Uri
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.network.ServerApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun uploadMenuImageFromUri(
    context: Context,
    apiClient: ServerApiClient,
    settingsRepository: SettingsRepository,
    baseUrl: String,
    localUri: String,
    outletId: String,
): String? {
    val normalizedUri = localUri.trim()
    if (normalizedUri.isBlank()) return null
    if (
        normalizedUri.startsWith("http://", ignoreCase = true) ||
            normalizedUri.startsWith("https://", ignoreCase = true) ||
            normalizedUri.startsWith("/media/", ignoreCase = true)
    ) {
        return normalizedUri
    }

    val parsedUri = runCatching { Uri.parse(normalizedUri) }.getOrNull() ?: return null
    val bytes = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(parsedUri)?.use { it.readBytes() }
        }.getOrNull()
    } ?: return null
    if (bytes.isEmpty()) return null

    val mimeType = context.contentResolver.getType(parsedUri)?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?: "image/jpeg"
    val fileName = parsedUri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() }
        ?: "menu-image.jpg"

    val bearerToken = settingsRepository.getActiveUserServerApiBearerToken(outletId)
    val uploadedImagePath = apiClient.uploadMenuImage(
        baseUrl = baseUrl,
        imageBytes = bytes,
        fileName = fileName,
        contentType = mimeType,
        outletId = outletId,
        bearerToken = bearerToken,
    ).imageUrl.trim()
    if (uploadedImagePath.isBlank()) return null
    if (
        uploadedImagePath.startsWith("http://", ignoreCase = true) ||
            uploadedImagePath.startsWith("https://", ignoreCase = true)
    ) {
        return uploadedImagePath
    }
    val base = baseUrl.trim().removeSuffix("/")
    val suffix = if (uploadedImagePath.startsWith("/")) uploadedImagePath else "/$uploadedImagePath"
    return "$base$suffix"
}
