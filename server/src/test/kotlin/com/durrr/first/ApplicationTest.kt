package com.durrr.first

import com.durrr.first.network.security.OpaqueBearerTokenCodec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.UUID
import kotlin.test.*

class ApplicationTest {
    init {
        System.setProperty("SUCASH_API_SHARED_SECRET", TEST_SHARED_SECRET)
        System.setProperty("SUCASH_ALLOW_LEGACY_TOKEN_AUTH", "true")
        System.setProperty("SUCASH_ALLOW_LOCAL_PAIRING_BOOTSTRAP", "true")
    }

    private val configuredSecret: String by lazy {
        EnvConfig.get("SUCASH_API_SHARED_SECRET", "")
            .orEmpty()
            .trim()
            .ifBlank { TEST_SHARED_SECRET }
    }

    private fun envelope(
        dataJson: String,
        message: String = "test request",
    ): String = """{"data":$dataJson,"message":"$message","error":null}"""

    private fun HttpRequestBuilder.authorizeAs(
        role: String,
        outletId: String = "default",
    ) {
        val session = ServerDatabase.issueApiAuthSession(
            role = role,
            userId = role.lowercase(),
            userName = role.lowercase().replaceFirstChar(Char::uppercaseChar),
            outletId = outletId,
            deviceId = "test-device-$role",
        ) ?: error("Unable to issue test session for role=$role outlet=$outletId")
        header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
    }

    private fun HttpRequestBuilder.authorizeLegacyBootstrapAs(
        role: String,
        outletId: String = "default",
    ) {
        val token = OpaqueBearerTokenCodec.issue(
            secret = configuredSecret,
            role = role,
            pin = "1234",
            outletId = outletId,
        ) ?: error("Unable to issue legacy bootstrap token for tests")
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend fun seedMenuItem(
        client: io.ktor.client.HttpClient,
        id: String,
        name: String,
        price: Long,
        outletId: String = "default",
    ) {
        val response = client.post("/api/menu/upsert") {
            contentType(ContentType.Application.Json)
            authorizeAs("OWNER", outletId)
            setBody(
                envelope(
                    """{"id":"$id","name":"$name","price":$price,"outlet_id":"$outletId"}""",
                    "Menu upsert request",
                )
            )
        }
        assertEquals(
            HttpStatusCode.OK,
            response.status,
            "Failed to seed menu item for test: ${response.bodyAsText()}",
        )
    }

    private suspend fun ensureOutlet(
        client: io.ktor.client.HttpClient,
        outletId: String,
        name: String = outletId,
        active: Boolean = true,
    ) {
        val response = client.post("/api/outlets/upsert") {
            contentType(ContentType.Application.Json)
            authorizeAs("OWNER", "default")
            setBody(
                envelope(
                    """{"outlet_id":"$outletId","name":"$name","active":$active}""",
                    "Outlet upsert request",
                )
            )
        }
        assertEquals(
            HttpStatusCode.OK,
            response.status,
            "Failed to ensure outlet for test: ${response.bodyAsText()}",
        )
    }

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(
            body.contains("sucash", ignoreCase = true) ||
                body.contains("<!doctype html>", ignoreCase = true),
        )
    }

    @Test
    fun testMenuUpsertRoute() = testApplication {
        application {
            module()
        }
        val response = client.post("/api/menu/upsert") {
            contentType(ContentType.Application.Json)
            authorizeAs("OWNER")
            setBody(
                envelope(
                    """{"id":"menu-test-case","name":"Menu Test Case","price":9900,"outlet_id":"default"}""",
                    "Menu upsert request",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("menu-test-case"))
    }

    @Test
    fun testTransactionBatchSyncAndRecapRoute() = testApplication {
        application {
            module()
        }
        val payload = """
            {
              "outlet_id": "default",
              "outbox_ids": ["evt-test-application"],
              "transaksi": [
                {
                  "id": "trx-test-application",
                  "created_at": "2026-03-14T08:00:00",
                  "meja": "table-1",
                  "discount_plus": 0,
                  "tax": 0,
                  "service_charge": 0,
                  "rounding": 0,
                  "total": 7777,
                  "details": [
                    {
                      "id": "det-test-application",
                      "item_id": "menu-test-case",
                      "item_name": "Menu Test Case",
                      "qty": 1,
                      "price": 7777,
                      "discount": 0,
                      "total": 7777
                    }
                  ],
                  "pembayaran": {
                    "id": "pay-test-application",
                    "paid_at": "2026-03-14T08:01:00",
                    "amount_paid": 10000,
                    "change": 2223,
                    "payment_type_id": "CASH"
                  }
                }
              ]
            }
        """.trimIndent()

        val syncResponse = client.post("/api/sync/transactions/batch") {
            contentType(ContentType.Application.Json)
            authorizeAs("CASHIER")
            setBody(envelope(payload, "Transaction sync batch request"))
        }
        assertEquals(HttpStatusCode.OK, syncResponse.status)
        assertTrue(syncResponse.bodyAsText().contains("evt-test-application"))

        val recapResponse = client.get("/api/recap/daily?date=2026-03-14&outlet=default") {
            authorizeAs("CASHIER")
        }
        assertEquals(HttpStatusCode.OK, recapResponse.status)
        assertTrue(recapResponse.bodyAsText().contains("\"date\": \"2026-03-14\""))
    }

    @Test
    fun testCreateOrderWithPaymentConfirmation() = testApplication {
        application {
            module()
        }
        val menuId = "menu-espresso-cashier-${UUID.randomUUID().toString().take(8)}"
        seedMenuItem(
            client = client,
            id = menuId,
            name = "Espresso",
            price = 18000,
        )
        val payload = """
            {
              "customerUuid": "9a8a7f0e-95d8-4b5b-83ec-2ef5dd4fe1d1",
              "outlet_id": "default",
              "paymentConfirmation": "CASHIER",
              "note": "test payment confirmation",
              "items": [
                {
                  "menuId": "$menuId",
                  "qty": 1
                }
              ]
            }
        """.trimIndent()
        val response = client.post("/api/orders") {
            contentType(ContentType.Application.Json)
            setBody(envelope(payload, "Create order request"))
        }
        val responseBody = response.bodyAsText()
        assertEquals(HttpStatusCode.Created, response.status, responseBody)
        assertTrue(responseBody.contains("\"paymentConfirmation\": \"CASHIER\""), responseBody)
    }

    @Test
    fun testTransactionBatchSyncWithEventEnvelopeAndIdempotency() = testApplication {
        application {
            module()
        }
        val payload = """
            {
              "events": [
                {
                  "event_id": "evt-envelope-1",
                  "entity_type": "TRANSAKSI_CHECKOUT",
                  "op": "UPSERT",
                  "payload_json": "{\"id\":\"trx-envelope-1\",\"created_at\":\"2026-03-14T09:00:00\",\"meja\":\"table-2\",\"discount_plus\":0,\"tax\":0,\"service_charge\":0,\"rounding\":0,\"total\":10000,\"details\":[{\"id\":\"det-envelope-1\",\"item_id\":\"menu-espresso\",\"item_name\":\"Espresso\",\"qty\":1,\"price\":10000,\"discount\":0,\"total\":10000}],\"pembayaran\":{\"id\":\"pay-envelope-1\",\"paid_at\":\"2026-03-14T09:01:00\",\"amount_paid\":12000,\"change\":2000,\"payment_type_id\":\"CASH\"}}",
                  "created_at": "2026-03-14T09:00:00"
                }
              ],
              "outlet_id": "default"
            }
        """.trimIndent()

        val firstSync = client.post("/api/sync/transactions/batch") {
            contentType(ContentType.Application.Json)
            authorizeAs("CASHIER")
            setBody(envelope(payload, "Transaction sync batch request"))
        }
        assertEquals(HttpStatusCode.OK, firstSync.status)
        assertTrue(firstSync.bodyAsText().contains("\"event_id\": \"evt-envelope-1\""))
        assertTrue(firstSync.bodyAsText().contains("\"status\": \"ACCEPTED\""))

        val secondSync = client.post("/api/sync/transactions/batch") {
            contentType(ContentType.Application.Json)
            authorizeAs("CASHIER")
            setBody(envelope(payload, "Transaction sync batch request"))
        }
        assertEquals(HttpStatusCode.OK, secondSync.status)
        assertTrue(secondSync.bodyAsText().contains("Already processed"))
    }

    @Test
    fun testMenuCatalogSupportsModifierCustomization() = testApplication {
        application {
            module()
        }
        seedMenuItem(
            client = client,
            id = "menu-espresso",
            name = "Espresso",
            price = 18000,
        )

        val upsertModifierPayload = """
            {
              "id": "mod-size",
              "name": "Drink Size",
              "selection_type": "SINGLE",
              "is_required": true,
              "max_selection": 1,
              "options": [
                { "id": "mod-size-normal", "name": "Normal", "priceDelta": 0, "order": 1, "isDefault": true },
                { "id": "mod-size-large", "name": "Large", "priceDelta": 3000, "order": 2, "isDefault": false }
              ],
              "outlet_id": "default"
            }
        """.trimIndent()
        val upsertModifierResponse = client.post("/api/menu/modifiers/upsert") {
            contentType(ContentType.Application.Json)
            authorizeAs("OWNER")
            setBody(envelope(upsertModifierPayload, "Upsert modifier group request"))
        }
        assertEquals(HttpStatusCode.OK, upsertModifierResponse.status)

        val assignPayload = """
            {
              "modifier_group_ids": ["mod-size"],
              "outlet_id": "default"
            }
        """.trimIndent()
        val assignResponse = client.post("/api/menu/menu-espresso/modifiers/assign") {
            contentType(ContentType.Application.Json)
            authorizeAs("OWNER")
            setBody(envelope(assignPayload, "Assign product modifiers request"))
        }
        assertEquals(HttpStatusCode.OK, assignResponse.status)

        val catalogResponse = client.get("/api/menu/catalog?outlet=default")
        assertEquals(HttpStatusCode.OK, catalogResponse.status)
        val body = catalogResponse.bodyAsText()
        assertTrue(body.contains("\"modifierGroups\""))
        assertTrue(body.contains("\"mod-size\""))
        assertTrue(body.contains("\"productModifierLinks\""))
        assertTrue(body.contains("\"menu-espresso\""))
    }

    @Test
    fun testCreateOrderWithLineModifiersAndLineNote() = testApplication {
        application {
            module()
        }
        seedMenuItem(
            client = client,
            id = "menu-espresso",
            name = "Espresso",
            price = 18000,
        )

        client.post("/api/menu/modifiers/upsert") {
            contentType(ContentType.Application.Json)
            authorizeAs("OWNER")
            setBody(
                envelope(
                    """
                {
                  "id": "mod-sugar",
                  "name": "Sugar Level",
                  "selection_type": "SINGLE",
                  "is_required": true,
                  "max_selection": 1,
                  "options": [
                    { "id": "mod-sugar-normal", "name": "Normal", "priceDelta": 0, "order": 1, "isDefault": true },
                    { "id": "mod-sugar-less", "name": "Less", "priceDelta": 0, "order": 2, "isDefault": false }
                  ],
                  "outlet_id": "default"
                }
                """.trimIndent(),
                    "Upsert modifier group request",
                )
            )
        }
        client.post("/api/menu/modifiers/upsert") {
            contentType(ContentType.Application.Json)
            authorizeAs("OWNER")
            setBody(
                envelope(
                    """
                {
                  "id": "mod-size",
                  "name": "Drink Size",
                  "selection_type": "SINGLE",
                  "is_required": true,
                  "max_selection": 1,
                  "options": [
                    { "id": "mod-size-normal", "name": "Normal", "priceDelta": 0, "order": 1, "isDefault": true },
                    { "id": "mod-size-large", "name": "Large", "priceDelta": 3000, "order": 2, "isDefault": false }
                  ],
                  "outlet_id": "default"
                }
                """.trimIndent(),
                    "Upsert modifier group request",
                )
            )
        }
        client.post("/api/menu/menu-espresso/modifiers/assign") {
            contentType(ContentType.Application.Json)
            authorizeAs("OWNER")
            setBody(
                envelope(
                    """
                {
                  "modifier_group_ids": ["mod-sugar", "mod-size"],
                  "outlet_id": "default"
                }
                """.trimIndent(),
                    "Assign product modifiers request",
                )
            )
        }

        val createOrderResponse = client.post("/api/orders") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """
                {
                  "customerUuid": "9a8a7f0e-95d8-4b5b-83ec-2ef5dd4fe1d1",
                  "outlet_id": "default",
                  "paymentConfirmation": "CASHIER",
                  "items": [
                    {
                      "menuId": "menu-espresso",
                      "qty": 1,
                      "note": "No stirrer",
                      "modifiers": [
                        { "optionId": "mod-sugar-normal" },
                        { "optionId": "mod-size-large" }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
                    "Create order request",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, createOrderResponse.status)
        val body = createOrderResponse.bodyAsText()
        assertTrue(body.contains("\"paymentConfirmation\": \"CASHIER\""))
        assertTrue(body.contains("\"lineTotal\": 21000"))
        assertTrue(body.contains("Sugar Level: Normal"))
        assertTrue(body.contains("Drink Size: Large"))
    }

    @Test
    fun testCreateReservationAndUpdateStatus() = testApplication {
        application {
            module()
        }

        val created = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """
                    {
                      "customer_name": "Andi",
                      "phone": "08123456789",
                      "party_size": 30,
                      "reservation_at": "2026-06-01T19:00:00",
                      "note": "Full cafe booking",
                      "outlet_id": "default"
                    }
                    """.trimIndent(),
                    "Create reservation request",
                )
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val createdBody = created.bodyAsText()
        assertTrue(createdBody.contains("\"status\": \"PENDING\""))
        assertTrue(createdBody.contains("\"reservation_date\": \"2026-06-01\""))
        assertTrue(createdBody.contains("\"reservation_time\": \"19:00\""))

        val createdFromWebForm = client.post("/api/reservations") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """
                    {
                      "customerName": "Budi",
                      "phone": "0811111111",
                      "partySize": 10,
                      "reservationDate": "2026-06-02",
                      "reservationTime": "17:30",
                      "note": "Web form style payload",
                      "outletId": "default"
                    }
                    """.trimIndent(),
                    "Create reservation request",
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createdFromWebForm.status)
        val webFormBody = createdFromWebForm.bodyAsText()
        assertTrue(webFormBody.contains("\"customer_name\": \"Budi\""))
        assertTrue(webFormBody.contains("\"reservation_date\": \"2026-06-02\""))
        assertTrue(webFormBody.contains("\"reservation_time\": \"17:30\""))

        val reservationId = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .find(createdBody)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        assertTrue(reservationId.isNotBlank())

        val listResponse = client.get("/api/reservations?status=PENDING&outlet=default") {
            authorizeAs("CASHIER")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertTrue(listResponse.bodyAsText().contains(reservationId))

        val update = client.post("/api/reservations/$reservationId/status") {
            contentType(ContentType.Application.Json)
            authorizeAs("OWNER")
            setBody(envelope("""{"status":"CONFIRMED","outlet_id":"default"}""", "Update reservation status request"))
        }
        assertEquals(HttpStatusCode.OK, update.status)
        assertTrue(update.bodyAsText().contains("\"status\": \"CONFIRMED\""))
    }

    @Test
    fun testProtectedWriteRejectsMismatchedTokenOutlet() = testApplication {
        application {
            module()
        }
        ensureOutlet(client = client, outletId = "outlet-b", name = "Outlet B")

        val response = client.post("/api/menu/upsert") {
            contentType(ContentType.Application.Json)
            authorizeAs(role = "OWNER", outletId = "outlet-a")
            setBody(
                envelope(
                    """{"id":"menu-outlet-mismatch","name":"Outlet Mismatch","price":12000,"outlet_id":"outlet-b"}""",
                    "Menu upsert request",
                )
            )
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("Token outlet mismatch"))
    }

    @Test
    fun testProtectedWriteAcceptsMatchingTokenOutlet() = testApplication {
        application {
            module()
        }
        ensureOutlet(client = client, outletId = "outlet-alpha", name = "Outlet Alpha")

        val response = client.post("/api/menu/upsert") {
            contentType(ContentType.Application.Json)
            authorizeAs(role = "OWNER", outletId = "outlet-alpha")
            setBody(
                envelope(
                    """{"id":"menu-outlet-match","name":"Outlet Match","price":15000,"outlet_id":"outlet-alpha"}""",
                    "Menu upsert request",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("menu-outlet-match"))
    }

    @Test
    fun testAuthSessionLoginAndUseSessionTokenForProtectedWrite() = testApplication {
        application {
            module()
        }

        val loginResponse = client.post("/api/auth/session/login") {
            contentType(ContentType.Application.Json)
            authorizeLegacyBootstrapAs(role = "OWNER", outletId = "default")
            setBody(
                envelope(
                    """
                    {
                      "role": "OWNER",
                      "user_id": "owner",
                      "user_name": "Owner",
                      "outlet_id": "default",
                      "device_id": "test-device-1"
                    }
                    """.trimIndent(),
                    "Auth session login request",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val loginBody = loginResponse.bodyAsText()
        val sessionToken = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"")
            .find(loginBody)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        assertTrue(sessionToken.isNotBlank())

        val upsertResponse = client.post("/api/menu/upsert") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $sessionToken")
            setBody(
                envelope(
                    """{"id":"menu-session-token","name":"Session Token Menu","price":21000,"outlet_id":"default"}""",
                    "Menu upsert request",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, upsertResponse.status)
        assertTrue(upsertResponse.bodyAsText().contains("menu-session-token"))
    }

    @Test
    fun testPairingCodeRedeemAndUseSessionTokenForProtectedWrite() = testApplication {
        application {
            module()
        }

        val createPairingResponse = client.post("/api/auth/pairing/create") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """{"role":"OWNER","outlet_id":"default"}""",
                    "Auth pairing create request",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, createPairingResponse.status)
        val pairingBody = createPairingResponse.bodyAsText()
        val pairingCode = Regex("\"pairing_code\"\\s*:\\s*\"([^\"]+)\"")
            .find(pairingBody)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        assertTrue(pairingCode.isNotBlank())

        val redeemResponse = client.post("/api/auth/pairing/redeem") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """
                    {
                      "pairing_code": "$pairingCode",
                      "role": "OWNER",
                      "user_id": "owner",
                      "user_name": "Owner",
                      "outlet_id": "default",
                      "device_id": "test-device-pairing"
                    }
                    """.trimIndent(),
                    "Auth pairing redeem request",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, redeemResponse.status)
        val redeemBody = redeemResponse.bodyAsText()
        val sessionToken = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"")
            .find(redeemBody)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        assertTrue(sessionToken.isNotBlank())

        val upsertResponse = client.post("/api/menu/upsert") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $sessionToken")
            setBody(
                envelope(
                    """{"id":"menu-pairing-token","name":"Pairing Token Menu","price":23000,"outlet_id":"default"}""",
                    "Menu upsert request",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, upsertResponse.status)
        assertTrue(upsertResponse.bodyAsText().contains("menu-pairing-token"))
    }

    companion object {
        private const val TEST_SHARED_SECRET = "test-shared-secret"
    }
}
