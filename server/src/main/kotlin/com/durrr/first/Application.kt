package com.durrr.first

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.netty.*
import io.ktor.server.request.host
import io.ktor.server.request.path
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes
import com.durrr.first.network.dto.AssignProductModifiersRequest
import com.durrr.first.network.dto.ServerAuthSessionLoginRequest
import com.durrr.first.network.dto.ServerAuthPairingCodeCreateRequest
import com.durrr.first.network.dto.ServerAuthPairingCodeRedeemRequest
import com.durrr.first.network.dto.ServerAuthSessionRefreshRequest
import com.durrr.first.network.dto.ServerAuthUserStatusRequest
import com.durrr.first.network.dto.ServerAuthUserUpsertRequest
import com.durrr.first.network.dto.ServerOutletStatusRequest
import com.durrr.first.network.dto.ServerOutletUpsertRequest
import com.durrr.first.network.dto.MenuImageUploadResponse
import com.durrr.first.network.dto.UpsertMenuItemRequest
import com.durrr.first.network.dto.UpsertModifierGroupRequest
import com.durrr.first.network.dto.TransactionBatchRequest
import com.durrr.first.network.security.OpaqueBearerTokenCodec
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

fun main() {
    val serverPort = EnvConfig.getInt("SUCASH_SERVER_PORT", SERVER_PORT)
    val serverHost = EnvConfig.get("SUCASH_SERVER_HOST", "0.0.0.0").orEmpty()
    embeddedServer(Netty, port = serverPort, host = serverHost, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    ServerDatabase.init()
    log.info(BackblazeMenuImageStorage.configurationSummary())
    val reservationLimiter = reservationRateLimiter
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            if (call.request.path().startsWith("/api")) {
                call.respondApiError(
                    status = HttpStatusCode.BadRequest,
                    message = "Invalid API request payload",
                    error = cause.message ?: "Bad Request",
                )
            } else {
                call.respond(HttpStatusCode.BadRequest, cause.message ?: "Bad Request")
            }
        }
        status(HttpStatusCode.NotFound) { call, status ->
            if (call.request.path().startsWith("/api")) {
                call.respondApiError(
                    status = status,
                    message = "Route not found",
                    error = "Not Found",
                )
            } else {
                call.respond(status, "Not Found")
            }
        }
        exception<Throwable> { call, cause ->
            if (call.request.path().startsWith("/api")) {
                this@module.log.error(
                    "API request failed: ${call.request.path()}",
                    cause,
                )
                call.respondApiError(
                    status = HttpStatusCode.InternalServerError,
                    message = "Request failed",
                    error = cause.message ?: (cause::class.simpleName ?: "Unknown error"),
                )
            } else {
                this@module.log.error(
                    "Request failed: ${call.request.path()}",
                    cause,
                )
                call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            }
        }
    }
    if (reservationLimiter == null) {
        log.info("Reservation rate limiter disabled.")
    } else {
        log.info(
            "Reservation rate limiter enabled: maxRequests=${reservationLimiter.maxRequests}, " +
                "windowSeconds=${reservationLimiter.windowSeconds}"
        )
    }

    routing {
        val webDistDir = resolveWebDistDirectory()
        val mediaUploadDir = resolveMediaUploadDirectory().apply { mkdirs() }
        if (webDistDir != null) {
            log.info("Serving React webapp from: ${webDistDir.absolutePath}")
            staticFiles("/web", webDistDir)
        } else {
            log.warn("React webapp dist not found. Falling back to bundled web resources.")
        }
        staticFiles("/media", mediaUploadDir)

        get("/web") {
            call.respondWebIndex(webDistDir)
        }

        get("/web/{path...}") {
            val path = call.parameters.getAll("path")
                ?.joinToString("/")
                .orEmpty()
            if (webDistDir != null) {
                if (path.isBlank() || !path.contains('.')) {
                    call.respondWebIndex(webDistDir)
                    return@get
                }
                val targetFile = webDistDir.resolve(path)
                if (targetFile.exists() && targetFile.isFile) {
                    call.respondFile(targetFile)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Resource not found: $path")
                }
                return@get
            }
            if (path.isBlank()) {
                call.respond(HttpStatusCode.NotFound, "Missing resource path")
                return@get
            }
            call.respondWebResource("web/$path", contentTypeForFile(path))
        }

        get("/") {
            if (!call.request.isAllowedRootAccess()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    "Root page is only accessible from local/private network.",
                )
                return@get
            }
            val subdomainUuid = call.request.extractUuidSubdomain()
            if (subdomainUuid != null && ServerDatabase.findCustomer(subdomainUuid) != null) {
                call.respondRedirect("/t/$subdomainUuid", permanent = false)
                return@get
            }
            call.respondWebIndex(webDistDir)
        }

        get("/dashboard") {
            call.respondWebIndex(webDistDir)
        }

        get("/admin") {
            if (!call.request.isAllowedRootAccess()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    "Admin page is only accessible from local/private network.",
                )
                return@get
            }
            call.respondWebIndex(webDistDir)
        }

        get("/t/{tableUuid}") {
            call.respondWebIndex(webDistDir)
        }

        get("/scan/{tableUuid}") {
            val tableUuid = call.parameters["tableUuid"].orEmpty()
            if (tableUuid.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing table UUID")
                return@get
            }
            call.respondRedirect("/t/$tableUuid", permanent = false)
        }

        route("/api") {
            post("/auth/pairing/create") {
                val request = call.receiveApiRequest<ServerAuthPairingCodeCreateRequest>()
                val requestedRole = "OWNER"
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                val ownerAccess = call.resolveOwnerAccessForAdmin(scopedOutletId)
                if (!ownerAccess.allowed) return@post
                val pairingCode = ServerDatabase.createApiAuthPairingCode(
                    role = requestedRole,
                    outletId = scopedOutletId,
                    ttlSeconds = request.ttlSeconds,
                )
                if (pairingCode == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Pairing code create failed",
                        error = "Unable to create pairing code",
                    )
                } else {
                    call.respondApiSuccess(
                        data = pairingCode,
                        message = "Pairing code created",
                    )
                }
            }

            post("/auth/pairing/redeem") {
                val request = call.receiveApiRequest<ServerAuthPairingCodeRedeemRequest>()
                val requestedRole = request.role.trim().uppercase()
                if (requestedRole !in setOf("OWNER", "CASHIER")) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Pairing redeem failed",
                        error = "Role must be OWNER or CASHIER",
                    )
                    return@post
                }
                val consumed = ServerDatabase.consumeApiAuthPairingCode(
                    pairingCode = request.pairingCode,
                    role = requestedRole,
                    outletId = request.outletId,
                )
                if (consumed == null) {
                    call.respondApiError(
                        status = HttpStatusCode.Unauthorized,
                        message = "Pairing redeem failed",
                        error = "Pairing code is invalid or expired",
                    )
                    return@post
                }
                if (!call.ensureActiveOutlet(consumed.outletId)) return@post
                val session = ServerDatabase.issueApiAuthSession(
                    role = requestedRole,
                    userId = request.userId,
                    userName = request.userName,
                    outletId = consumed.outletId,
                    deviceId = request.deviceId,
                )
                if (session == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Pairing redeem failed",
                        error = "Unable to create session for role ${consumed.role}",
                    )
                } else {
                    call.respondApiSuccess(
                        data = session,
                        message = "Pairing redeem success",
                    )
                }
            }

            post("/auth/session/login") {
                val request = call.receiveApiRequest<ServerAuthSessionLoginRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                val requestedRole = request.role.trim().uppercase()
                if (requestedRole !in setOf("OWNER", "CASHIER")) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Session login failed",
                        error = "Role must be OWNER or CASHIER",
                    )
                    return@post
                }
                if (
                    call.resolveLegacyBootstrapPrincipal(
                        requestedOutletId = scopedOutletId,
                        requiredRole = requestedRole,
                    ) == null
                ) return@post
                val session = ServerDatabase.issueApiAuthSession(
                    role = requestedRole,
                    userId = request.userId,
                    userName = request.userName,
                    outletId = scopedOutletId,
                    deviceId = request.deviceId,
                )
                if (session == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Session login failed",
                        error = "Unable to create session for role $requestedRole",
                    )
                } else {
                    call.respondApiSuccess(
                        data = session,
                        message = "Session login success",
                    )
                }
            }

            post("/auth/session/refresh") {
                val request = call.receiveApiRequest<ServerAuthSessionRefreshRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                call.resolveApiPrincipal(
                    requestedOutletId = scopedOutletId,
                    allowedRoles = setOf("OWNER", "CASHIER"),
                ) ?: return@post

                val authHeader = call.request.headers[HttpHeaders.Authorization]
                    ?.trim()
                    .orEmpty()
                val token = authHeader.substringAfter(' ', missingDelimiterValue = "")
                    .trim()
                val refreshed = ServerDatabase.refreshApiAuthSession(
                    accessToken = token,
                    outletId = scopedOutletId,
                    deviceId = request.deviceId,
                )
                if (refreshed == null) {
                    call.respondApiError(
                        status = HttpStatusCode.Unauthorized,
                        message = "Session refresh failed",
                        error = "Session not found or expired",
                    )
                } else {
                    call.respondApiSuccess(
                        data = refreshed,
                        message = "Session refreshed",
                    )
                }
            }

            post("/auth/session/logout") {
                val requestedOutletId = call.request.queryParameters["outlet"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::normalizeApiOutletIdOrNull)
                val principal = call.resolveApiPrincipal(
                    requestedOutletId = requestedOutletId,
                    allowedRoles = setOf("OWNER", "CASHIER"),
                ) ?: return@post
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                    ?.trim()
                    .orEmpty()
                val token = authHeader.substringAfter(' ', missingDelimiterValue = "")
                    .trim()
                ServerDatabase.revokeApiAuthSession(token)
                call.recordAuthAudit(
                    principal = principal,
                    action = "SESSION_LOGOUT",
                    targetType = "SESSION",
                    targetId = principal.userId,
                )
                call.respondApiSuccess(
                    data = true,
                    message = "Session logout success",
                )
            }

            get("/auth/users") {
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                if (!call.requireApiRoleForOutlet(outletId, "OWNER")) return@get
                call.respondApiSuccess(
                    data = ServerDatabase.listAuthUsers(outletId),
                    message = "Auth users fetched",
                )
            }

            post("/auth/users/upsert") {
                val request = call.receiveApiRequest<ServerAuthUserUpsertRequest>()
                val outletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(outletId)) return@post
                val principal = call.resolveApiPrincipal(
                    requestedOutletId = outletId,
                    allowedRoles = setOf("OWNER"),
                ) ?: return@post
                val updated = ServerDatabase.upsertAuthUser(
                    outletId = outletId,
                    role = request.role,
                    userId = request.userId,
                    userName = request.userName,
                    active = request.active,
                )
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Auth user save failed",
                        error = "Invalid auth user payload",
                    )
                } else {
                    call.recordAuthAudit(
                        principal = principal,
                        action = "AUTH_USER_UPSERT",
                        targetType = "AUTH_USER",
                        targetId = "${updated.role}:${updated.userId}",
                        payloadJson = """{"active":${updated.active}}""",
                    )
                    call.respondApiSuccess(
                        data = updated,
                        message = "Auth user saved",
                    )
                }
            }

            post("/auth/users/{userId}/status") {
                val userId = call.parameters["userId"].orEmpty()
                if (userId.isBlank()) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Auth user status update failed",
                        error = "Missing user id",
                    )
                    return@post
                }
                val request = call.receiveApiRequest<ServerAuthUserStatusRequest>()
                val outletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(outletId)) return@post
                val principal = call.resolveApiPrincipal(
                    requestedOutletId = outletId,
                    allowedRoles = setOf("OWNER"),
                ) ?: return@post
                val updated = ServerDatabase.updateAuthUserStatus(
                    outletId = outletId,
                    role = request.role,
                    userId = userId,
                    active = request.active,
                )
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Auth user status update failed",
                        error = "Auth user not found",
                    )
                } else {
                    call.recordAuthAudit(
                        principal = principal,
                        action = "AUTH_USER_STATUS_UPDATE",
                        targetType = "AUTH_USER",
                        targetId = "${updated.role}:${updated.userId}",
                        payloadJson = """{"active":${updated.active}}""",
                    )
                    call.respondApiSuccess(
                        data = updated,
                        message = "Auth user status updated",
                    )
                }
            }

            get("/auth/sessions") {
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                if (!call.requireApiRoleForOutlet(outletId, "OWNER")) return@get
                val includeRevoked = call.request.queryParameters["include_revoked"]
                    ?.trim()
                    ?.let { it.equals("true", ignoreCase = true) || it == "1" }
                    ?: false
                val limit = call.request.queryParameters["limit"]
                    ?.toIntOrNull()
                    ?: 100
                call.respondApiSuccess(
                    data = ServerDatabase.listApiAuthSessions(
                        outletId = outletId,
                        includeRevoked = includeRevoked,
                        limit = limit,
                    ),
                    message = "Auth sessions fetched",
                )
            }

            post("/auth/sessions/{sessionId}/revoke") {
                val sessionId = call.parameters["sessionId"].orEmpty()
                if (sessionId.isBlank()) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Session revoke failed",
                        error = "Missing session id",
                    )
                    return@post
                }
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@post
                if (!call.ensureActiveOutlet(outletId)) return@post
                val principal = call.resolveApiPrincipal(
                    requestedOutletId = outletId,
                    allowedRoles = setOf("OWNER"),
                ) ?: return@post
                val revoked = ServerDatabase.revokeApiAuthSessionById(
                    sessionId = sessionId,
                    outletId = outletId,
                )
                if (!revoked) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Session revoke failed",
                        error = "Session not found",
                    )
                } else {
                    call.recordAuthAudit(
                        principal = principal,
                        action = "SESSION_REVOKE",
                        targetType = "SESSION",
                        targetId = sessionId,
                    )
                    call.respondApiSuccess(
                        data = true,
                        message = "Session revoked",
                    )
                }
            }

            get("/auth/audit") {
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                if (!call.requireApiRoleForOutlet(outletId, "OWNER")) return@get
                val limit = call.request.queryParameters["limit"]
                    ?.toIntOrNull()
                    ?: 100
                call.respondApiSuccess(
                    data = ServerDatabase.listApiAuthAuditLogs(
                        outletId = outletId,
                        limit = limit,
                    ),
                    message = "Auth audit logs fetched",
                )
            }

            get("/outlets") {
                val ownerAccess = call.resolveOwnerAccessForAdmin(requestedOutletId = null)
                if (!ownerAccess.allowed) {
                    return@get
                }
                call.respondApiSuccess(
                    data = ServerDatabase.listOutlets(),
                    message = "Outlets fetched",
                )
            }

            post("/outlets/upsert") {
                val ownerAccess = call.resolveOwnerAccessForAdmin(requestedOutletId = null)
                if (!ownerAccess.allowed) return@post
                val principal = ownerAccess.principal
                val request = call.receiveApiRequest<ServerOutletUpsertRequest>()
                val scopedOutletId = normalizeApiOutletIdOrNull(request.outletId)
                if (scopedOutletId == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Outlet save failed",
                        error = "outlet_id is required",
                    )
                    return@post
                }
                val updated = ServerDatabase.upsertOutlet(
                    outletId = scopedOutletId,
                    name = request.name,
                    active = request.active,
                )
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Outlet save failed",
                        error = "Invalid outlet payload",
                    )
                } else {
                    if (principal != null) {
                        call.recordAuthAudit(
                            principal = principal,
                            action = "OUTLET_UPSERT",
                            targetType = "OUTLET",
                            targetId = updated.outletId,
                            payloadJson = """{"active":${updated.active}}""",
                        )
                    }
                    call.respondApiSuccess(data = updated, message = "Outlet saved")
                }
            }

            post("/outlets/{id}/status") {
                val ownerAccess = call.resolveOwnerAccessForAdmin(requestedOutletId = null)
                if (!ownerAccess.allowed) return@post
                val principal = ownerAccess.principal
                val outletIdParam = call.parameters["id"].orEmpty()
                if (outletIdParam.isBlank()) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Outlet status update failed",
                        error = "Missing outlet id",
                    )
                    return@post
                }
                val request = call.receiveApiRequest<ServerOutletStatusRequest>()
                val scopedOutletId = normalizeApiOutletIdOrNull(request.outletId ?: outletIdParam)
                if (scopedOutletId == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Outlet status update failed",
                        error = "outlet_id is required",
                    )
                    return@post
                }
                val updated = ServerDatabase.updateOutletStatus(
                    outletId = scopedOutletId,
                    active = request.active,
                )
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Outlet status update failed",
                        error = "Outlet not found",
                    )
                } else {
                    if (principal != null) {
                        call.recordAuthAudit(
                            principal = principal,
                            action = "OUTLET_STATUS_UPDATE",
                            targetType = "OUTLET",
                            targetId = updated.outletId,
                            payloadJson = """{"active":${updated.active}}""",
                        )
                    }
                    call.respondApiSuccess(data = updated, message = "Outlet status updated")
                }
            }

            post("/media/menu-image/upload") {
                var outletIdFromForm: String? = null
                var originalFileName: String? = null
                var fileBytes: ByteArray? = null
                var fileContentType: String? = null

                val multipart = call.receiveMultipart()
                while (true) {
                    val part = multipart.readPart() ?: break
                    when (part) {
                        is PartData.FormItem -> if (part.name == "outlet_id") {
                            outletIdFromForm = part.value.trim()
                        }
                        is PartData.FileItem -> if (part.name == "file") {
                            originalFileName = part.originalFileName
                            fileContentType = part.contentType?.toString()
                            fileBytes = part.provider().readRemaining().readBytes()
                        }
                        else -> Unit
                    }
                    part.dispose()
                }

                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = outletIdFromForm,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                if (!call.requireApiRoleForOutlet(scopedOutletId, "OWNER")) return@post
                val bytes = fileBytes
                if (bytes == null || bytes.isEmpty()) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Image upload failed",
                        error = "Missing image file",
                    )
                    return@post
                }
                if (bytes.size > MAX_MENU_IMAGE_BYTES) {
                    call.respondApiError(
                        status = HttpStatusCode.PayloadTooLarge,
                        message = "Image upload failed",
                        error = "Max file size is 5 MB",
                    )
                    return@post
                }
                val imageUrl = saveMenuImageFile(
                    outletId = scopedOutletId,
                    originalFileName = originalFileName,
                    contentType = fileContentType,
                    bytes = bytes,
                )
                call.respondApiSuccess(
                    data = MenuImageUploadResponse(imageUrl = imageUrl),
                    message = "Image uploaded",
                )
            }

            get("/menu") {
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                call.respondApiSuccess(
                    data = ServerDatabase.listMenu(outletId),
                    message = "Menu fetched",
                )
            }

            get("/menu/catalog") {
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                call.respondApiSuccess(
                    data = ServerDatabase.menuCatalog(outletId),
                    message = "Menu catalog fetched",
                )
            }

            post("/menu/upsert") {
                val request = call.receiveApiRequest<UpsertMenuItemRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                if (!call.requireApiRoleForOutlet(scopedOutletId, "OWNER")) return@post
                val updated = ServerDatabase.upsertMenu(request)
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Menu upsert failed",
                        error = "Invalid menu payload",
                    )
                } else {
                    call.respondApiSuccess(data = updated, message = "Menu item saved")
                }
            }

            post("/menu/{id}/delete") {
                val id = call.parameters["id"].orEmpty()
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@post
                if (!call.ensureActiveOutlet(outletId)) return@post
                if (!call.requireApiRoleForOutlet(outletId, "OWNER")) return@post
                if (id.isBlank()) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Menu delete failed",
                        error = "Missing menu id",
                    )
                    return@post
                }
                val deleted = ServerDatabase.deleteMenu(id, outletId)
                if (!deleted) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Menu delete failed",
                        error = "Menu not found",
                    )
                } else {
                    call.respondApiSuccess(data = true, message = "Menu deleted")
                }
            }

            post("/menu/modifiers/upsert") {
                val request = call.receiveApiRequest<UpsertModifierGroupRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                if (!call.requireApiRoleForOutlet(scopedOutletId, "OWNER")) return@post
                val ok = ServerDatabase.upsertModifierGroup(request)
                if (!ok) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Modifier group save failed",
                        error = "Invalid modifier group payload",
                    )
                } else {
                    call.respondApiSuccess(data = true, message = "Modifier group saved")
                }
            }

            post("/menu/{id}/modifiers/assign") {
                val itemId = call.parameters["id"].orEmpty()
                val request = call.receiveApiRequest<AssignProductModifiersRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                if (!call.requireApiRoleForOutlet(scopedOutletId, "OWNER")) return@post
                if (itemId.isBlank()) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Modifier assignment failed",
                        error = "Missing item id",
                    )
                    return@post
                }
                val ok = ServerDatabase.assignProductModifiers(itemId, request)
                if (!ok) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Modifier assignment failed",
                        error = "Invalid modifier assignment payload",
                    )
                } else {
                    call.respondApiSuccess(data = true, message = "Modifier assignment saved")
                }
            }

            get("/customers") {
                call.respondApiSuccess(data = ServerDatabase.listCustomers(), message = "Customers fetched")
            }

            post("/customers/seed-tables") {
                val request = call.receiveApiRequest<SeedTablesRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                val ownerAccess = call.resolveOwnerAccessForAdmin(scopedOutletId)
                if (!ownerAccess.allowed) return@post
                val principal = ownerAccess.principal
                val seeded = ServerDatabase.seedTableCustomers(
                    count = request.count,
                    outletId = scopedOutletId,
                )
                if (principal != null) {
                    call.recordAuthAudit(
                        principal = principal,
                        action = "TABLES_SEED",
                        targetType = "OUTLET",
                        targetId = scopedOutletId,
                        payloadJson = """{"count":${seeded.size}}""",
                    )
                }
                call.respondApiSuccess(
                    data = seeded,
                    message = "Table customers seeded",
                )
            }

            get("/tables") {
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                call.respondApiSuccess(
                    data = ServerDatabase.listTablesByOutlet(outletId = outletId),
                    message = "Tables fetched",
                )
            }

            post("/tables/seed") {
                val request = call.receiveApiRequest<SeedTablesRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                val ownerAccess = call.resolveOwnerAccessForAdmin(scopedOutletId)
                if (!ownerAccess.allowed) return@post
                val principal = ownerAccess.principal
                val seeded = ServerDatabase.seedTableCustomers(
                    count = request.count,
                    outletId = scopedOutletId,
                )
                if (principal != null) {
                    call.recordAuthAudit(
                        principal = principal,
                        action = "TABLES_SEED",
                        targetType = "OUTLET",
                        targetId = scopedOutletId,
                        payloadJson = """{"count":${seeded.size}}""",
                    )
                }
                call.respondApiSuccess(
                    data = seeded,
                    message = "Tables seeded",
                )
            }

            get("/tables/{uuid}") {
                val uuid = call.parameters["uuid"].orEmpty()
                val requestedOutletId = call.request.queryParameters["outlet"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::normalizeApiOutletIdOrNull)
                val table = if (requestedOutletId != null) {
                    if (!call.ensureActiveOutlet(requestedOutletId)) return@get
                    ServerDatabase.findTableByUuidInOutlet(
                        uuid = uuid,
                        outletId = requestedOutletId,
                    )
                } else {
                    ServerDatabase.resolveTableOutlet(uuid)
                }
                if (table == null) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Table fetch failed",
                        error = "Table not found",
                    )
                } else {
                    call.respondApiSuccess(data = table, message = "Table fetched")
                }
            }

            get("/tables/{uuid}/outlet") {
                val uuid = call.parameters["uuid"].orEmpty()
                val resolved = ServerDatabase.resolveTableOutlet(uuid)
                if (resolved == null) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Table outlet resolve failed",
                        error = "Table not found or outlet cannot be resolved",
                    )
                } else {
                    call.respondApiSuccess(
                        data = mapOf(
                            "uuid" to resolved.uuid,
                            "name" to resolved.name,
                            "outlet_id" to resolved.outletId,
                            "table_index" to resolved.tableIndex,
                        ),
                        message = "Table outlet resolved",
                    )
                }
            }

            get("/customers/{uuid}") {
                val uuid = call.parameters["uuid"].orEmpty()
                val customer = ServerDatabase.findCustomer(uuid)
                if (customer == null) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Customer fetch failed",
                        error = "Customer not found",
                    )
                } else {
                    call.respondApiSuccess(data = customer, message = "Customer fetched")
                }
            }

            get("/orders") {
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                if (!call.requireApiRoleForOutlet(outletId, "OWNER", "CASHIER")) return@get
                val statuses = call.request.queryParameters["status"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: emptySet()
                call.respondApiSuccess(
                    data = ServerDatabase.listOrders(statuses, outletId),
                    message = "Orders fetched",
                )
            }

            post("/orders") {
                val request = call.receiveApiRequest<CreateOrderRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                val order = ServerDatabase.createOrder(
                    request.copy(outletId = scopedOutletId),
                )
                if (order == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Order creation failed",
                        error = "Invalid order request",
                    )
                } else {
                    call.respondApiSuccess(
                        data = order,
                        message = "Order created",
                        status = HttpStatusCode.Created,
                    )
                }
            }

            post("/reservations") {
                reservationLimiter?.let { limiter ->
                    val decision = limiter.tryAcquire(key = call.rateLimitKeyForReservation())
                    if (!decision.allowed) {
                        call.response.headers.append(
                            HttpHeaders.RetryAfter,
                            decision.retryAfterSeconds.toString(),
                        )
                        call.respondApiError(
                            status = HttpStatusCode.TooManyRequests,
                            message = "Too many reservation requests",
                            error = "Please retry in ${decision.retryAfterSeconds} second(s).",
                        )
                        return@post
                    }
                }
                val request = call.receiveApiRequest<CreateReservationRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.resolvedOutletId(),
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                val reservation = ServerDatabase.createReservation(
                    request.copy(
                        outletId = scopedOutletId,
                        outletIdCamel = scopedOutletId,
                    )
                )
                if (reservation == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Reservation creation failed",
                        error = "Invalid reservation request",
                    )
                } else {
                    call.respondApiSuccess(
                        data = reservation,
                        message = "Reservation created",
                        status = HttpStatusCode.Created,
                    )
                }
            }

            get("/reservations") {
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                if (!call.requireApiRoleForOutlet(outletId, "OWNER", "CASHIER")) return@get
                val statuses = call.request.queryParameters["status"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: emptySet()
                call.respondApiSuccess(
                    data = ServerDatabase.listReservations(statuses, outletId),
                    message = "Reservations fetched",
                )
            }

            post("/reservations/{id}/status") {
                val reservationId = call.parameters["id"].orEmpty()
                if (reservationId.isBlank()) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Reservation status update failed",
                        error = "Missing reservation id",
                    )
                    return@post
                }
                val request = call.receiveApiRequest<UpdateReservationStatusRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                if (!call.requireApiRoleForOutlet(scopedOutletId, "OWNER", "CASHIER")) return@post
                val updated = ServerDatabase.updateReservationStatus(
                    reservationId = reservationId,
                    status = request.status,
                    outletId = scopedOutletId,
                )
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Reservation status update failed",
                        error = "Invalid reservation status or reservation not found",
                    )
                } else {
                    call.respondApiSuccess(data = updated, message = "Reservation status updated")
                }
            }

            post("/orders/{id}/status") {
                val orderId = call.parameters["id"].orEmpty()
                val body = call.receiveApiRequest<UpdateOrderStatusRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = body.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                if (!call.requireApiRoleForOutlet(scopedOutletId, "OWNER", "CASHIER")) return@post
                val updated = ServerDatabase.updateOrderStatus(
                    orderId = orderId,
                    status = body.status,
                    outletId = scopedOutletId,
                )
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Order status update failed",
                        error = "Cannot update order status",
                    )
                } else {
                    call.respondApiSuccess(data = updated, message = "Order status updated")
                }
            }

            post("/orders/{id}/accept") {
                val orderId = call.parameters["id"].orEmpty()
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@post
                if (!call.ensureActiveOutlet(outletId)) return@post
                if (!call.requireApiRoleForOutlet(outletId, "OWNER", "CASHIER")) return@post
                val updated = ServerDatabase.updateOrderStatus(orderId, "ACCEPTED", outletId)
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Order accept failed",
                        error = "Cannot accept order",
                    )
                } else {
                    call.respondApiSuccess(data = updated, message = "Order accepted")
                }
            }

            post("/sync/transactions/batch") {
                val request = call.receiveApiRequest<TransactionBatchRequest>()
                val scopedOutletId = call.requireScopedOutletId(
                    rawOutletId = request.outletId,
                    source = "outlet_id",
                ) ?: return@post
                if (!call.ensureActiveOutlet(scopedOutletId)) return@post
                if (!call.requireApiRoleForOutlet(scopedOutletId, "OWNER", "CASHIER")) return@post
                call.respondApiSuccess(
                    data = ServerDatabase.syncTransactionsBatch(request),
                    message = "Transaction sync processed",
                )
            }

            get("/recap/daily") {
                val date = call.request.queryParameters["date"]
                    ?.takeIf { it.isNotBlank() }
                    ?: LocalDate.now().toString()
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                if (!call.requireApiRoleForOutlet(outletId, "OWNER", "CASHIER")) return@get
                call.respondApiSuccess(
                    data = ServerDatabase.dailyRecap(date, outletId),
                    message = "Daily recap fetched",
                )
            }

            get("/recap/summary") {
                val date = call.request.queryParameters["date"]
                    ?.takeIf { it.isNotBlank() }
                    ?: LocalDate.now().toString()
                val range = call.request.queryParameters["range"]
                    ?.takeIf { it.isNotBlank() }
                    ?: "TODAY"
                val fromDate = call.request.queryParameters["from"]
                    ?.takeIf { it.isNotBlank() }
                val toDate = call.request.queryParameters["to"]
                    ?.takeIf { it.isNotBlank() }
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@get
                if (!call.ensureActiveOutlet(outletId)) return@get
                if (!call.requireApiRoleForOutlet(outletId, "OWNER", "CASHIER")) return@get
                call.respondApiSuccess(
                    data = ServerDatabase.recapSummary(
                        range = range,
                        date = date,
                        fromDate = fromDate,
                        toDate = toDate,
                        outletId = outletId,
                    ),
                    message = "Recap summary fetched",
                )
            }

            post("/admin/reset-all") {
                val outletId = call.requireScopedOutletId(
                    rawOutletId = call.request.queryParameters["outlet"],
                    source = "outlet query parameter",
                ) ?: return@post
                if (!call.ensureActiveOutlet(outletId)) return@post
                val principal = call.resolveApiPrincipal(
                    requestedOutletId = outletId,
                    allowedRoles = setOf("OWNER"),
                ) ?: return@post
                val ok = ServerDatabase.resetOutletData(outletId)
                if (ok) {
                    call.recordAuthAudit(
                        principal = principal,
                        action = "OUTLET_RESET_ALL",
                        targetType = "OUTLET",
                        targetId = outletId,
                    )
                    call.respondApiSuccess(
                        data = true,
                        message = "Reset done for outlet=$outletId",
                    )
                } else {
                    call.respondApiError(
                        status = HttpStatusCode.InternalServerError,
                        message = "Reset failed",
                        error = "Failed to reset outlet=$outletId",
                    )
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondWebIndex(webDistDir: File?) {
    val distIndex = webDistDir?.resolve("index.html")
    if (distIndex?.exists() == true) {
        respondFile(distIndex)
        return
    }
    respondWebResource("web/index.html", ContentType.Text.Html)
}

private suspend fun ApplicationCall.respondWebResource(path: String, contentType: ContentType) {
    val body = this::class.java.classLoader
        .getResource(path)
        ?.readText()
    if (body == null) {
        respond(HttpStatusCode.NotFound, "Resource not found: $path")
        return
    }
    respondText(body, contentType = contentType)
}

private fun resolveWebDistDirectory(): File? {
    val configuredPath = EnvConfig.get("SUCASH_WEBAPP_DIST", "")
        .orEmpty()
        .trim()
    if (configuredPath.isBlank()) {
        return null
    }
    val configuredFile = File(configuredPath)
    val resolved = if (configuredFile.isAbsolute) {
        configuredFile
    } else {
        File(System.getProperty("user.dir"), configuredPath)
    }
    return resolved.takeIf { it.exists() && it.isDirectory }
}

private const val MAX_MENU_IMAGE_BYTES = 5 * 1024 * 1024
private val mediaLogger = LoggerFactory.getLogger("MenuImageStorage")

private fun resolveMediaUploadDirectory(): File {
    val configuredPath = EnvConfig.get("SUCASH_MEDIA_UPLOAD_DIR", "data/uploads").orEmpty()
        .ifBlank { "data/uploads" }
    val configuredFile = File(configuredPath)
    return if (configuredFile.isAbsolute) {
        configuredFile
    } else {
        File(System.getProperty("user.dir"), configuredPath)
    }
}

private fun saveMenuImageFile(
    outletId: String,
    originalFileName: String?,
    contentType: String?,
    bytes: ByteArray,
): String {
    if (BackblazeMenuImageStorage.isConfigured()) {
        runCatching {
            mediaLogger.info("Uploading menu image to Backblaze for outlet={}", outletId)
            return BackblazeMenuImageStorage.uploadMenuImage(
                outletId = outletId,
                originalFileName = originalFileName,
                contentType = contentType,
                bytes = bytes,
            )
        }.onFailure { error ->
            // Fallback to local storage if Backblaze fails to avoid blocking owner workflows.
            mediaLogger.warn("Backblaze upload failed, falling back to local storage: {}", error.message)
        }
    }

    val extension = inferImageExtension(originalFileName, contentType)
    val fileName = "${System.currentTimeMillis()}-${UUID.randomUUID()}.$extension"
    val outletSafe = outletId.trim().ifBlank { DEFAULT_API_OUTLET_ID }
        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val relativePath = "menu/$outletSafe/$fileName"
    val targetFile = resolveMediaUploadDirectory().resolve(relativePath)
    targetFile.parentFile?.mkdirs()
    targetFile.writeBytes(bytes)
    mediaLogger.info("Stored menu image in local media dir: {}", relativePath)
    return "/media/$relativePath"
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

private fun contentTypeForFile(path: String): ContentType {
    return when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "js", "mjs" -> ContentType.Application.JavaScript
        "css" -> ContentType.Text.CSS
        "html" -> ContentType.Text.Html
        "json" -> ContentType.Application.Json
        "svg" -> ContentType.Image.SVG
        "png" -> ContentType.Image.PNG
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "webp" -> ContentType.parse("image/webp")
        "ico" -> ContentType.parse("image/x-icon")
        else -> ContentType.Application.OctetStream
    }
}

private val apiJsonParser = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}
private const val AUTH_BEARER_PREFIX = "Bearer "
private const val DEFAULT_API_OUTLET_ID = "default"

private enum class ApiAuthSource {
    SESSION,
    LEGACY,
}

private data class ApiAuthPrincipal(
    val role: String,
    val outletId: String,
    val userId: String,
    val userName: String,
    val source: ApiAuthSource,
)

private data class OwnerAccessResult(
    val allowed: Boolean,
    val principal: ApiAuthPrincipal? = null,
)

@Serializable
data class SeedTablesRequest(
    val count: Int = 10,
    @SerialName("outlet_id")
    val outletId: String? = null,
)

private suspend inline fun <reified T> ApplicationCall.respondApiSuccess(
    data: T? = null,
    message: String = "OK",
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val envelope = buildJsonObject {
        put("data", data?.let { apiJsonParser.encodeToJsonElement(it) } ?: JsonNull)
        put("message", JsonPrimitive(message))
        put("error", JsonNull)
    }
    respondText(
        text = apiJsonParser.encodeToString(envelope),
        contentType = ContentType.Application.Json,
        status = status,
    )
}

private suspend fun ApplicationCall.respondApiError(
    status: HttpStatusCode,
    message: String,
    error: String? = null,
) {
    val envelope = buildJsonObject {
        put("data", JsonNull)
        put("message", JsonPrimitive(message))
        put("error", error?.let { JsonPrimitive(it) } ?: JsonNull)
    }
    respondText(
        text = apiJsonParser.encodeToString(envelope),
        contentType = ContentType.Application.Json,
        status = status,
    )
}

private suspend inline fun <reified T> ApplicationCall.receiveApiRequest(): T {
    val rawBody = receiveText()
    if (rawBody.isBlank()) {
        throw IllegalArgumentException("Request body cannot be empty")
    }
    val envelope = try {
        apiJsonParser.decodeFromString(JsonObject.serializer(), rawBody)
    } catch (_: SerializationException) {
        throw IllegalArgumentException("Request body must use envelope format {data,message,error}")
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Request body must use envelope format {data,message,error}")
    }
    val payload = envelope["data"]
    if (payload == null || payload is JsonNull) {
        throw IllegalArgumentException("Envelope data is required")
    }
    val error = envelope["error"]?.jsonPrimitive?.contentOrNull
    if (!error.isNullOrBlank()) {
        throw IllegalArgumentException("Request envelope error must be null")
    }
    return try {
        apiJsonParser.decodeFromJsonElement<T>(payload)
    } catch (_: SerializationException) {
        throw IllegalArgumentException("Invalid envelope data payload")
    }
}

private fun ApplicationCall.recordAuthAudit(
    principal: ApiAuthPrincipal,
    action: String,
    targetType: String? = null,
    targetId: String? = null,
    payloadJson: String? = null,
) {
    runCatching {
        ServerDatabase.appendApiAuthAuditLog(
            outletId = principal.outletId,
            actorRole = principal.role,
            actorUserId = principal.userId,
            action = action,
            targetType = targetType,
            targetId = targetId,
            payloadJson = payloadJson,
        )
    }
}

private suspend fun ApplicationCall.resolveOwnerAccessForAdmin(
    requestedOutletId: String?,
): OwnerAccessResult {
    val authHeader = request.headers[HttpHeaders.Authorization]
        ?.trim()
        .orEmpty()
    val hasBearerToken = authHeader.startsWith(AUTH_BEARER_PREFIX, ignoreCase = true) &&
        authHeader.substringAfter(' ', missingDelimiterValue = "").trim().isNotEmpty()

    if (hasBearerToken) {
        val principal = resolveApiPrincipal(
            requestedOutletId = requestedOutletId,
            allowedRoles = setOf("OWNER"),
        ) ?: return OwnerAccessResult(allowed = false)
        return OwnerAccessResult(allowed = true, principal = principal)
    }

    if (!configuredAllowLocalPairingBootstrap()) {
        respondApiError(
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized request",
            error = "Owner bearer token is required",
        )
        return OwnerAccessResult(allowed = false)
    }

    if (!request.isAllowedRootAccess()) {
        respondApiError(
            status = HttpStatusCode.Forbidden,
            message = "Forbidden request",
            error = "This admin action is allowed only from local/private network",
        )
        return OwnerAccessResult(allowed = false)
    }

    return OwnerAccessResult(allowed = true, principal = null)
}

private suspend fun ApplicationCall.requireApiRole(vararg allowedRoles: String): Boolean {
    return requireApiRoleForOutlet(
        requestedOutletId = request.queryParameters["outlet"]
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeApiOutletIdOrNull),
        allowedRoles = allowedRoles.map { it.uppercase() }.toSet(),
    )
}

private suspend fun ApplicationCall.requireApiRoleForOutlet(
    requestedOutletId: String?,
    vararg allowedRoles: String,
): Boolean {
    return requireApiRoleForOutlet(
        requestedOutletId = requestedOutletId,
        allowedRoles = allowedRoles.map { it.uppercase() }.toSet(),
    )
}

private suspend fun ApplicationCall.requireApiRoleForOutlet(
    requestedOutletId: String?,
    allowedRoles: Set<String>,
): Boolean {
    val principal = resolveApiPrincipal(
        requestedOutletId = requestedOutletId,
        allowedRoles = allowedRoles,
    ) ?: return false
    return principal.role in allowedRoles
}

private suspend fun ApplicationCall.resolveApiPrincipal(
    requestedOutletId: String?,
    allowedRoles: Set<String>,
): ApiAuthPrincipal? {
    val authHeader = request.headers[HttpHeaders.Authorization]
        ?.trim()
        .orEmpty()
    val hasBearerToken = authHeader.startsWith(AUTH_BEARER_PREFIX, ignoreCase = true)
    val token = if (hasBearerToken) {
        authHeader.substringAfter(' ', missingDelimiterValue = "").trim()
    } else {
        ""
    }
    if (hasBearerToken && token.isBlank()) {
        respondApiError(
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized request",
            error = "Empty bearer token",
        )
        return null
    }
    if (!hasBearerToken) {
        respondApiError(
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized request",
            error = "Missing Authorization Bearer token",
        )
        return null
    }

    val session = ServerDatabase.findValidApiAuthSession(token)
    if (session == null) {
        respondApiError(
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized request",
            error = "Session token invalid or expired. Login again.",
        )
        return null
    }
    val effectiveOutletId = requestedOutletId ?: session.outletId
    val sessionRole = session.role.trim().uppercase()
    if (sessionRole !in allowedRoles) {
        respondApiError(
            status = HttpStatusCode.Forbidden,
            message = "Forbidden request",
            error = "Role $sessionRole cannot access this endpoint",
        )
        return null
    }
    if (session.outletId != effectiveOutletId) {
        respondApiError(
            status = HttpStatusCode.Forbidden,
            message = "Forbidden request",
            error = "Token outlet mismatch. Expected $effectiveOutletId, got ${session.outletId}",
        )
        return null
    }
    return ApiAuthPrincipal(
        role = sessionRole,
        outletId = session.outletId,
        userId = session.userId,
        userName = session.userName,
        source = ApiAuthSource.SESSION,
    )
}

private suspend fun ApplicationCall.resolveLegacyBootstrapPrincipal(
    requestedOutletId: String,
    requiredRole: String,
): ApiAuthPrincipal? {
    val authHeader = request.headers[HttpHeaders.Authorization]
        ?.trim()
        .orEmpty()
    val hasBearerToken = authHeader.startsWith(AUTH_BEARER_PREFIX, ignoreCase = true)
    val token = if (hasBearerToken) {
        authHeader.substringAfter(' ', missingDelimiterValue = "").trim()
    } else {
        ""
    }
    if (!hasBearerToken || token.isBlank()) {
        respondApiError(
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized request",
            error = "Bootstrap bearer token is required",
        )
        return null
    }
    if (!configuredAllowLegacyTokenAuth()) {
        respondApiError(
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized request",
            error = "Legacy bootstrap login is disabled. Use pairing flow.",
        )
        return null
    }
    val sharedSecret = configuredApiSharedSecret()
    if (sharedSecret == null) {
        respondApiError(
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized request",
            error = "Server API shared secret is not configured",
        )
        return null
    }
    val claims = OpaqueBearerTokenCodec.decode(secret = sharedSecret, token = token)
    if (claims == null) {
        respondApiError(
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized request",
            error = "Invalid bootstrap bearer token",
        )
        return null
    }
    val providedRole = claims.role.trim().uppercase()
    if (providedRole != requiredRole) {
        respondApiError(
            status = HttpStatusCode.Forbidden,
            message = "Forbidden request",
            error = "Role $providedRole cannot bootstrap $requiredRole session",
        )
        return null
    }
    val tokenOutletId = normalizeApiOutletIdOrNull(claims.outletId)
    if (tokenOutletId == null) {
        respondApiError(
            status = HttpStatusCode.Unauthorized,
            message = "Unauthorized request",
            error = "Legacy bootstrap token must include explicit outlet_id",
        )
        return null
    }
    if (tokenOutletId != requestedOutletId) {
        respondApiError(
            status = HttpStatusCode.Forbidden,
            message = "Forbidden request",
            error = "Token outlet mismatch. Expected $requestedOutletId, got $tokenOutletId",
        )
        return null
    }
    return ApiAuthPrincipal(
        role = providedRole,
        outletId = tokenOutletId,
        userId = if (providedRole == "OWNER") "owner" else "cashier",
        userName = if (providedRole == "OWNER") "Owner" else "Cashier",
        source = ApiAuthSource.LEGACY,
    )
}

private fun configuredApiSharedSecret(): String? {
    return EnvConfig.get("SUCASH_API_SHARED_SECRET", "")
        .orEmpty()
        .trim()
        .ifBlank { null }
}

private fun configuredAllowLocalPairingBootstrap(): Boolean {
    val raw = EnvConfig.get("SUCASH_ALLOW_LOCAL_PAIRING_BOOTSTRAP", "true")
        .orEmpty()
        .trim()
    return raw.equals("true", ignoreCase = true) || raw == "1"
}

private fun configuredAllowLegacyTokenAuth(): Boolean {
    val raw = EnvConfig.get("SUCASH_ALLOW_LEGACY_TOKEN_AUTH", "false")
        .orEmpty()
        .trim()
    return raw.equals("true", ignoreCase = true) || raw == "1"
}

private fun configuredRequireExplicitOutletId(): Boolean {
    val raw = EnvConfig.get("SUCASH_REQUIRE_EXPLICIT_OUTLET_ID", "true")
        .orEmpty()
        .trim()
    return raw.equals("true", ignoreCase = true) || raw == "1"
}

private suspend fun ApplicationCall.requireScopedOutletId(
    rawOutletId: String?,
    source: String,
): String? {
    val normalized = rawOutletId
        ?.trim()
        ?.replace(Regex("\\s+"), "-")
        ?.take(64)
        .orEmpty()
    if (normalized.isBlank()) {
        if (configuredRequireExplicitOutletId()) {
            respondApiError(
                status = HttpStatusCode.BadRequest,
                message = "Outlet is required",
                error = "Missing outlet id from $source",
            )
            return null
        }
        return DEFAULT_API_OUTLET_ID
    }
    return normalized
}

private suspend fun ApplicationCall.ensureActiveOutlet(outletId: String): Boolean {
    if (ServerDatabase.isOutletActive(outletId)) return true
    respondApiError(
        status = HttpStatusCode.NotFound,
        message = "Outlet unavailable",
        error = "Outlet $outletId not found or inactive",
    )
    return false
}

private fun normalizeApiOutletIdOrNull(value: String?): String? {
    return value.orEmpty()
        .trim()
        .replace(Regex("\\s+"), "-")
        .ifBlank { return null }
        .take(64)
}

private fun io.ktor.server.request.ApplicationRequest.extractUuidSubdomain(): String? {
    val host = headers["Host"]
        ?.substringBefore(':')
        ?.lowercase()
        ?: return null
    val firstLabel = when {
        host.endsWith(".localhost") -> host.removeSuffix(".localhost").substringBefore(".")
        host.contains(".") -> host.substringBefore(".")
        else -> return null
    }
    return runCatching { UUID.fromString(firstLabel) }.getOrNull()?.toString()
}

private fun io.ktor.server.request.ApplicationRequest.isAllowedRootAccess(): Boolean {
    val requestHost = host()
        .substringBefore(':')
        .lowercase()
    val serverHost = EnvConfig.get("SUCASH_SERVER_HOST", "0.0.0.0")
        .orEmpty()
        .substringBefore(':')
        .lowercase()
        .trim()

    if (requestHost == "localhost" || requestHost == "127.0.0.1" || requestHost == "::1") {
        return true
    }
    if (serverHost.isNotBlank() && serverHost != "0.0.0.0" && requestHost == serverHost) {
        return true
    }
    return requestHost.isPrivateIpv4Address()
}

private fun String.isPrivateIpv4Address(): Boolean {
    val parts = split('.')
    if (parts.size != 4) return false
    val numbers = parts.map { it.toIntOrNull() ?: return false }
    val a = numbers[0]
    val b = numbers[1]
    return when {
        a == 10 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 168 -> true
        else -> false
    }
}

private data class RateLimitDecision(
    val allowed: Boolean,
    val retryAfterSeconds: Int = 0,
)

private class FixedWindowIpRateLimiter(
    val maxRequests: Int,
    val windowSeconds: Int,
) {
    private data class WindowCounter(
        var windowStartMs: Long,
        var count: Int,
    )

    private val windowMillis = windowSeconds * 1000L
    private val counters = ConcurrentHashMap<String, WindowCounter>()

    fun tryAcquire(key: String, nowMs: Long = System.currentTimeMillis()): RateLimitDecision {
        val state = counters.compute(key) { _, existing ->
            if (existing == null || nowMs - existing.windowStartMs >= windowMillis) {
                WindowCounter(windowStartMs = nowMs, count = 1)
            } else {
                existing.count += 1
                existing
            }
        } ?: WindowCounter(windowStartMs = nowMs, count = 1)

        if (state.count <= maxRequests) {
            return RateLimitDecision(allowed = true)
        }
        val remainingMs = (windowMillis - (nowMs - state.windowStartMs)).coerceAtLeast(0L)
        val retryAfter = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
        return RateLimitDecision(allowed = false, retryAfterSeconds = retryAfter)
    }
}

private val reservationRateLimiter: FixedWindowIpRateLimiter? by lazy {
    val enabled = EnvConfig.get("SUCASH_RESERVATION_RATE_LIMIT_ENABLED", "true")
        .orEmpty()
        .trim()
        .let { raw -> raw.equals("true", ignoreCase = true) || raw == "1" }
    if (!enabled) return@lazy null

    val maxRequests = EnvConfig
        .getInt("SUCASH_RESERVATION_RATE_LIMIT_MAX_REQUESTS", 5)
        .coerceAtLeast(1)
    val windowSeconds = EnvConfig
        .getInt("SUCASH_RESERVATION_RATE_LIMIT_WINDOW_SECONDS", 60)
        .coerceAtLeast(1)

    FixedWindowIpRateLimiter(
        maxRequests = maxRequests,
        windowSeconds = windowSeconds,
    )
}

private fun ApplicationCall.rateLimitKeyForReservation(): String {
    val xForwardedFor = request.headers["X-Forwarded-For"]
        ?.substringBefore(',')
        ?.trim()
    val xRealIp = request.headers["X-Real-IP"]?.trim()
    val forwarded = request.headers["Forwarded"]
        ?.split(';', ',')
        ?.firstOrNull { part -> part.trim().startsWith("for=", ignoreCase = true) }
        ?.substringAfter('=', missingDelimiterValue = "")
        ?.trim()
        ?.trim('"')
        ?.removePrefix("[")
        ?.removeSuffix("]")
        ?.substringBefore(':')
    val requestHost = runCatching { request.host() }.getOrNull()
    val ip = xForwardedFor
        ?.takeIf { it.isNotBlank() }
        ?: xRealIp?.takeIf { it.isNotBlank() }
        ?: forwarded?.takeIf { it.isNotBlank() }
        ?: requestHost?.takeIf { it.isNotBlank() }
        ?: "unknown"
    return "reservation:$ip"
}
