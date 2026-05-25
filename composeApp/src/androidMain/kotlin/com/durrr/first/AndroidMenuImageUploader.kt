package com.durrr.first

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.network.ServerApiClient
import java.io.ByteArrayOutputStream
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
    val uploadBytes = normalizeImageBytesForUpload(bytes)

    val mimeType = context.contentResolver.getType(parsedUri)?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?: "image/jpeg"
    val fileName = parsedUri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() }
        ?: "menu-image.jpg"

    val bearerToken = settingsRepository.getActiveUserServerApiBearerToken(outletId)
    if (bearerToken.isNullOrBlank()) {
        error("Owner bearer token belum ada. Login ulang sebagai owner lalu coba upload foto lagi.")
    }
    val uploadedImagePath = apiClient.uploadMenuImage(
        baseUrl = baseUrl,
        imageBytes = uploadBytes,
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

private fun normalizeImageBytesForUpload(
    sourceBytes: ByteArray,
    maxBytes: Int = 4_900_000,
): ByteArray {
    if (sourceBytes.size <= maxBytes) return sourceBytes

    val bitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return sourceBytes
    var working = bitmap
    if (working.width > MAX_DIMENSION_PX || working.height > MAX_DIMENSION_PX) {
        val ratio = minOf(
            MAX_DIMENSION_PX.toFloat() / working.width.toFloat(),
            MAX_DIMENSION_PX.toFloat() / working.height.toFloat(),
        )
        val targetW = (working.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (working.height * ratio).toInt().coerceAtLeast(1)
        working = Bitmap.createScaledBitmap(working, targetW, targetH, true)
    }

    var quality = 92
    var result = compressBitmapJpeg(working, quality)
    while (result.size > maxBytes && quality >= 50) {
        quality -= 7
        result = compressBitmapJpeg(working, quality)
    }
    return result
}

private fun compressBitmapJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
    return output.toByteArray()
}

private const val MAX_DIMENSION_PX = 1920
