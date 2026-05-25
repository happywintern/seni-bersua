package com.durrr.first

import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object BackblazeMenuImageStorage {
    private val parser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    private val config: Config? by lazy { loadConfig() }
    private val downloadTokenCache = ConcurrentHashMap<String, CachedDownloadToken>()

    fun isConfigured(): Boolean = config != null

    fun configurationSummary(): String {
        val cfg = config ?: return "Backblaze disabled (missing key/appkey/bucket)."
        val keyPreview = cfg.keyId.take(4).padEnd(4, '*')
        return "Backblaze enabled bucket=${cfg.bucketName}, keyIdPrefix=$keyPreview, bucketId=${cfg.bucketId ?: "-"}, privateMode=${cfg.privateBucket}"
    }

    fun resolveReadableImageUrl(rawImageUrl: String?): String? {
        val raw = rawImageUrl?.trim()?.ifBlank { null } ?: return null
        val cfg = config ?: return raw
        if (!cfg.privateBucket) return raw
        val objectKey = extractObjectKeyFromBackblazeUrl(raw, cfg.bucketName) ?: return raw
        return runCatching { createSignedDownloadUrl(cfg, objectKey) }.getOrElse { raw }
    }

    fun uploadMenuImage(
        outletId: String,
        originalFileName: String?,
        contentType: String?,
        bytes: ByteArray,
    ): String {
        val cfg = config ?: error("Backblaze config is missing")
        val extension = inferImageExtension(originalFileName, contentType)
        val outletSafe = outletId.trim().ifBlank { "default" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val objectKey = "menu/$outletSafe/${System.currentTimeMillis()}-${UUID.randomUUID()}.$extension"
        val detectedContentType = contentType?.trim().takeUnless { it.isNullOrBlank() } ?: "image/jpeg"

        val auth = authorize(cfg)
        val bucketId = resolveBucketId(cfg, auth)
        val uploadTarget = getUploadUrl(auth.apiUrl, auth.authorizationToken, bucketId)
        uploadFile(
            uploadUrl = uploadTarget.uploadUrl,
            uploadAuthToken = uploadTarget.authorizationToken,
            objectKey = objectKey,
            contentType = detectedContentType,
            bytes = bytes,
        )

        val publicBase = cfg.publicBaseUrl?.trim()?.removeSuffix("/")
            ?: auth.downloadUrl.trim().removeSuffix("/")
        val encodedKey = encodePathForUrl(objectKey)
        return "$publicBase/file/${cfg.bucketName}/$encodedKey"
    }

    private fun loadConfig(): Config? {
        val keyId = EnvConfig.get("BACKBLAZE_BUCKET_KEYID")
            ?: EnvConfig.get("BACKBLAZE_KEY_ID")
            ?: EnvConfig.get("B2_KEY_ID")
        val applicationKey = EnvConfig.get("BACKBLAZE_BUCKET_APPKEY")
            ?: EnvConfig.get("BACKBLAZE_APPLICATION_KEY")
            ?: EnvConfig.get("B2_APPLICATION_KEY")
        val bucketName = EnvConfig.get("BACKBLAZE_BUCKET_NAME")
            ?: EnvConfig.get("B2_BUCKET_NAME")
        val bucketId = EnvConfig.get("BACKBLAZE_BUCKET_ID")
            ?: EnvConfig.get("B2_BUCKET_ID")
        val privateBucket = EnvConfig.get("BACKBLAZE_BUCKET_PRIVATE", "false")
            ?.equals("true", ignoreCase = true) == true
        val signedUrlTtlSeconds = EnvConfig.getInt("BACKBLAZE_SIGNED_URL_TTL_SECONDS", 3600)
            .coerceIn(60, 7 * 24 * 3600)
        val publicBaseUrl = EnvConfig.get("BACKBLAZE_PUBLIC_BASE_URL")
            ?: EnvConfig.get("B2_PUBLIC_BASE_URL")

        if (keyId.isNullOrBlank() || applicationKey.isNullOrBlank() || bucketName.isNullOrBlank()) {
            return null
        }
        return Config(
            keyId = keyId.trim(),
            applicationKey = applicationKey.trim(),
            bucketName = bucketName.trim(),
            bucketId = bucketId?.trim()?.ifBlank { null },
            privateBucket = privateBucket,
            signedUrlTtlSeconds = signedUrlTtlSeconds,
            publicBaseUrl = publicBaseUrl?.trim()?.ifBlank { null },
        )
    }

    private fun authorize(cfg: Config): AuthorizeResponse {
        val basicToken = Base64.getEncoder().encodeToString(
            "${cfg.keyId}:${cfg.applicationKey}".toByteArray(StandardCharsets.UTF_8)
        )
        val request = HttpRequest.newBuilder(URI.create(AUTH_URL))
            .header("Authorization", "Basic $basicToken")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        val json = parseJsonObject(response.body())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Backblaze authorize failed [${response.statusCode()}]: ${extractError(json)}")
        }
        return parser.decodeFromString(AuthorizeResponse.serializer(), response.body())
    }

    private fun resolveBucketId(cfg: Config, auth: AuthorizeResponse): String {
        val explicitBucketId = cfg.bucketId?.trim().orEmpty()
        if (explicitBucketId.isNotBlank()) return explicitBucketId
        val allowedBucketId = auth.allowed?.bucketId?.trim().orEmpty()
        if (allowedBucketId.isNotBlank()) return allowedBucketId

        val bodyJson = """{"accountId":"${escapeJson(auth.accountId)}"}"""
        val response = postJson(
            url = "${auth.apiUrl}/b2api/v2/b2_list_buckets",
            authorizationToken = auth.authorizationToken,
            jsonBody = bodyJson,
        )
        val buckets = response["buckets"] as? JsonArray ?: JsonArray(emptyList())
        val found = buckets.firstOrNull { element ->
            element.jsonObject["bucketName"]?.jsonPrimitive?.content == cfg.bucketName
        }?.jsonObject
        return found?.get("bucketId")?.jsonPrimitive?.content
            ?: error("Backblaze bucket not found: ${cfg.bucketName}")
    }

    private fun getUploadUrl(apiUrl: String, authToken: String, bucketId: String): UploadUrlResponse {
        val bodyJson = """{"bucketId":"${escapeJson(bucketId)}"}"""
        val response = postJson(
            url = "$apiUrl/b2api/v2/b2_get_upload_url",
            authorizationToken = authToken,
            jsonBody = bodyJson,
        )
        return UploadUrlResponse(
            uploadUrl = response["uploadUrl"]?.jsonPrimitive?.content
                ?: error("Backblaze upload URL missing"),
            authorizationToken = response["authorizationToken"]?.jsonPrimitive?.content
                ?: error("Backblaze upload token missing"),
        )
    }

    private fun uploadFile(
        uploadUrl: String,
        uploadAuthToken: String,
        objectKey: String,
        contentType: String,
        bytes: ByteArray,
    ) {
        val sha1 = sha1Hex(bytes)
        val encodedName = encodePathForHeader(objectKey)
        val request = HttpRequest.newBuilder(URI.create(uploadUrl))
            .header("Authorization", uploadAuthToken)
            .header("X-Bz-File-Name", encodedName)
            .header("Content-Type", contentType)
            .header("X-Bz-Content-Sha1", sha1)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val json = parseJsonObject(response.body())
            throw IllegalStateException("Backblaze upload failed [${response.statusCode()}]: ${extractError(json)}")
        }
    }

    private fun createSignedDownloadUrl(cfg: Config, objectKey: String): String {
        val cacheKey = "${cfg.bucketName}|$objectKey"
        val now = System.currentTimeMillis()
        downloadTokenCache[cacheKey]?.let { cached ->
            if (cached.expiresAtMillis > now + 10_000) {
                val encodedKey = encodePathForUrl(objectKey)
                val token = URLEncoder.encode(cached.token, StandardCharsets.UTF_8).replace("+", "%20")
                return "${cached.downloadUrl}/file/${cfg.bucketName}/$encodedKey?Authorization=$token"
            }
        }

        val auth = authorize(cfg)
        val bucketId = resolveBucketId(cfg, auth)
        val token = getDownloadAuthorization(
            apiUrl = auth.apiUrl,
            authorizationToken = auth.authorizationToken,
            bucketId = bucketId,
            fileNamePrefix = objectKey,
            validDurationSeconds = cfg.signedUrlTtlSeconds,
        )
        val expiresAt = now + (cfg.signedUrlTtlSeconds * 1000L)
        downloadTokenCache[cacheKey] = CachedDownloadToken(
            token = token.authorizationToken,
            expiresAtMillis = expiresAt,
            downloadUrl = auth.downloadUrl.trim().removeSuffix("/"),
        )
        val encodedKey = encodePathForUrl(objectKey)
        val encodedToken = URLEncoder.encode(token.authorizationToken, StandardCharsets.UTF_8).replace("+", "%20")
        return "${auth.downloadUrl.trim().removeSuffix("/")}/file/${cfg.bucketName}/$encodedKey?Authorization=$encodedToken"
    }

    private fun getDownloadAuthorization(
        apiUrl: String,
        authorizationToken: String,
        bucketId: String,
        fileNamePrefix: String,
        validDurationSeconds: Int,
    ): DownloadAuthorizationResponse {
        val bodyJson = """
            {
              "bucketId":"${escapeJson(bucketId)}",
              "fileNamePrefix":"${escapeJson(fileNamePrefix)}",
              "validDurationInSeconds":$validDurationSeconds
            }
        """.trimIndent()
        val response = postJson(
            url = "$apiUrl/b2api/v2/b2_get_download_authorization",
            authorizationToken = authorizationToken,
            jsonBody = bodyJson,
        )
        val token = response["authorizationToken"]?.jsonPrimitive?.content
            ?: error("Backblaze download authorization token missing")
        return DownloadAuthorizationResponse(token)
    }

    private fun postJson(
        url: String,
        authorizationToken: String,
        jsonBody: String,
    ): JsonObject {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", authorizationToken)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        val json = parseJsonObject(response.body())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Backblaze request failed [${response.statusCode()}]: ${extractError(json)}")
        }
        return json
    }

    private fun parseJsonObject(raw: String): JsonObject {
        return runCatching { parser.decodeFromString(JsonObject.serializer(), raw) }
            .getOrElse { JsonObject(emptyMap()) }
    }

    private fun extractError(json: JsonObject): String {
        val code = json["code"]?.jsonPrimitive?.contentOrNull
        val message = json["message"]?.jsonPrimitive?.contentOrNull
        return listOfNotNull(code, message).joinToString(": ").ifBlank { "Unknown error" }
    }

    private fun inferImageExtension(originalFileName: String?, contentType: String?): String {
        val fromName = originalFileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") }
        if (fromName != null) return fromName
        return when (contentType?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
    }

    private fun sha1Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return buildString(digest.size * 2) {
            digest.forEach { b ->
                append("%02x".format(b))
            }
        }
    }

    private fun encodePathForHeader(path: String): String {
        return path.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
        }
    }

    private fun encodePathForUrl(path: String): String {
        return encodePathForHeader(path)
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun extractObjectKeyFromBackblazeUrl(rawUrl: String, bucketName: String): String? {
        val parsed = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        val path = parsed.path.orEmpty()
        val prefix = "/file/$bucketName/"
        val index = path.indexOf(prefix)
        if (index < 0) return null
        val encoded = path.substring(index + prefix.length).trim()
        if (encoded.isBlank()) return null
        return encoded
            .split('/')
            .joinToString("/") { segment ->
                URLDecoder.decode(segment, StandardCharsets.UTF_8)
            }
    }

    private const val AUTH_URL = "https://api.backblazeb2.com/b2api/v2/b2_authorize_account"

    private data class Config(
        val keyId: String,
        val applicationKey: String,
        val bucketName: String,
        val bucketId: String?,
        val privateBucket: Boolean,
        val signedUrlTtlSeconds: Int,
        val publicBaseUrl: String?,
    )

    private data class CachedDownloadToken(
        val token: String,
        val expiresAtMillis: Long,
        val downloadUrl: String,
    )

    @Serializable
    private data class AuthorizeAllowed(
        val capabilities: List<String> = emptyList(),
        val bucketId: String? = null,
        val bucketName: String? = null,
    )

    @Serializable
    private data class AuthorizeResponse(
        val accountId: String,
        val apiUrl: String,
        val authorizationToken: String,
        val downloadUrl: String,
        val allowed: AuthorizeAllowed? = null,
    )

    @Serializable
    private data class UploadUrlResponse(
        val uploadUrl: String,
        val authorizationToken: String,
    )

    private data class DownloadAuthorizationResponse(
        val authorizationToken: String,
    )
}
