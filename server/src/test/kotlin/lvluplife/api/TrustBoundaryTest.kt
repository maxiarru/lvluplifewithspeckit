package lvluplife.api

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlin.test.*

class TrustBoundaryTest {
    private val validToken = "synthetic-valid-a"
    private val operation = "123e4567-e89b-42d3-a456-426614174000"
    private val command = """{"operationId":"$operation","type":"COMPLETE_PROFILE","payload":{"username":"SyntheticA"}}"""
    private fun verify(token: String): String? = when (token) {
        validToken -> "synthetic-account-a"
        "synthetic-forbidden" -> throw SecurityException("private-owner-b")
        "synthetic-provider-failure" -> error("secret-provider-detail")
        else -> null
    }

    @Test fun everyQueryRequiresIdentity() = testApplication {
        application { serverModule(::verify) }
        for (path in listOf("me", "catalog", "progress", "custom-activities", "history?period=day&date=2026-11-10", "operations/$operation")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/$path").status, path)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/commands") {
            contentType(ContentType.Application.Json); setBody(command)
        }.status)
    }

    @Test fun invalidExpiredRevokedAndDisabledTokensAreDenied() = testApplication {
        application { serverModule(::verify) }
        for (token in listOf("invalid", "expired", "revoked", "disabled")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/me") { bearerAuth(token) }.status)
        }
    }

    @Test fun clientCannotSelectAnotherOwnerOrProvideProgression() = testApplication {
        application { serverModule(::verify) }
        for (field in listOf("uid", "owner", "xp", "level", "classId", "createdAt")) {
            val body = command.dropLast(1) + ",\"$field\":\"synthetic-account-b\"}"
            val response = client.post("/v1/commands") {
                bearerAuth(validToken); contentType(ContentType.Application.Json); setBody(body)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, field)
            assertFalse(response.bodyAsText().contains("synthetic-account-b"))
        }
    }

    @Test fun unknownAndXpPayloadFieldsAreRejected() = testApplication {
        application { serverModule(::verify) }
        for (field in listOf("xp", "unexpected")) {
            val body = command.replace("\"SyntheticA\"", "\"SyntheticA\",\"$field\":12")
            assertEquals(HttpStatusCode.BadRequest, client.post("/v1/commands") {
                bearerAuth(validToken); contentType(ContentType.Application.Json); setBody(body)
            }.status)
        }
    }

    @Test fun malformedRequestHasSafeBadRequestResponse() = testApplication {
        application { serverModule(::verify) }
        val response = client.post("/v1/commands") {
            bearerAuth(validToken); contentType(ContentType.Application.Json); setBody("{broken")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("INVALID_REQUEST"))
        assertFalse(response.bodyAsText().contains(validToken))
    }

    @Test fun dependencyFailuresAndDenialsNeverExposePrivateDetails() = testApplication {
        application { serverModule(::verify) }
        for ((token, status, code) in listOf(
            Triple("synthetic-forbidden", HttpStatusCode.Forbidden, "FORBIDDEN"),
            Triple("synthetic-provider-failure", HttpStatusCode.ServiceUnavailable, "TEMPORARILY_UNAVAILABLE"),
        )) {
            val response = client.get("/v1/me") { bearerAuth(token) }
            assertEquals(status, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains(code))
            listOf(token, "secret-provider-detail", "private-owner-b", "Exception").forEach { assertFalse(body.contains(it)) }
        }
    }

    @Test fun oversizedBodiesAreRejectedWithoutEffects() = testApplication {
        application { serverModule(::verify) }
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/v1/commands") {
            bearerAuth(validToken); contentType(ContentType.Application.Json)
            setBody("x".repeat(100_000))
        }.status)
    }
}
