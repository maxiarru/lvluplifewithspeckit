package lvluplife.api

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlin.test.*

class RequestSchemaTest {
    private val id = "123e4567-e89b-42d3-a456-426614174000"
    private fun command(type: String, payload: String, cycle: Boolean = true) =
        """{"operationId":"$id","type":"$type",${if (cycle) "\"cycleId\":\"cycle-a\"," else ""}"payload":$payload}"""
    private val variants get() = listOf(
        command("COMPLETE_PROFILE", """{"username":"SyntheticA"}""", false),
        command("CREATE_CUSTOM_ACTIVITY", """{"name":"Exercise","category":"Fitness","primary":"STR"}""", false),
        command("CREATE_RECORD", """{"activityId":"strength","activityDate":"2026-11-10","hours":0,"minutes":30}"""),
        command("CREATE_RECORD", """{"activityId":"meal","activityDate":"2026-11-10"}"""),
        command("EDIT_RECORD", """{"recordId":"record-a","expectedVersion":1,"activityDate":"2026-11-10"}"""),
        command("EDIT_RECORD", """{"recordId":"record-a","expectedVersion":1,"activityId":"strength","activityDate":"2026-11-10","hours":0,"minutes":30}"""),
        command("DELETE_RECORD", """{"recordId":"record-a","expectedVersion":1}"""),
        command("RESET_PROGRESSION", """{"confirmed":true}"""),
    )

    @Test fun validVariantsReachOnlyVerifiedOwnerAndNeverPretendToBePersisted() = testApplication {
        val seen = mutableListOf<CommandEnvelope>()
        application {
            serverModule(verifyToken = { "owner-a" }, handlers = ApiHandlers(command = { uid, request ->
                assertEquals("owner-a", uid.value)
                seen += request
                throw ApiException(ApiError.TEMPORARILY_UNAVAILABLE)
            }))
        }
        for (body in variants) {
            val response = client.post("/v1/commands") {
                bearerAuth("synthetic-token"); contentType(ContentType.Application.Json); setBody(body)
            }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status, body)
            assertFalse(response.bodyAsText().contains("CONFIRMED"))
        }
        assertEquals(variants.size, seen.size)
    }

    @Test fun everyVariantRejectsUnknownPayloadFieldsBeforeDispatch() = testApplication {
        application { serverModule(verifyToken = { "owner-a" }, handlers = ApiHandlers(command = { _, _ ->
            error("must not dispatch malformed input")
        })) }
        for (body in variants) {
            val withXp = body.dropLast(2) + ",\"xp\":20}}"
            assertEquals(HttpStatusCode.BadRequest, client.post("/v1/commands") {
                bearerAuth("synthetic-token"); contentType(ContentType.Application.Json); setBody(withXp)
            }.status, body)
        }
    }

    @Test fun wrongTypesMissingFieldsDuplicateKeysAndTrailingJsonAreRejected() = testApplication {
        application { serverModule { "owner-a" } }
        val profile = variants.first()
        val invalid = listOf("null", "[]", "{}", "$profile {}",
            profile.replace("\"username\":\"SyntheticA\"", "\"username\":\"A\",\"username\":\"B\""),
            profile.replace("\"username\":\"SyntheticA\"", "\"username\":null"),
            profile.replace(id, "1-1-1-1-1"),
            variants[2].replace("\"hours\":0", "\"hours\":0.5"),
            variants[2].replace("\"hours\":0,", ""),
            variants[2].replace("\"cycleId\":\"cycle-a\",", ""),
            profile.replace("\"payload\":", "\"cycleId\":null,\"payload\":"),
            command("RESET_PROGRESSION", """{"confirmed":false}"""))
        for (body in invalid) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/v1/commands") {
                bearerAuth("synthetic-token"); contentType(ContentType.Application.Json); setBody(body)
            }.status, body)
        }
    }

    @Test fun chunkedOversizedBodyIsBoundedWithoutContentLength() = testApplication {
        application { serverModule { "owner-a" } }
        val response = client.post("/v1/commands") {
            bearerAuth("synthetic-token")
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.Application.Json
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeFully(ByteArray(100_000) { 'x'.code.toByte() })
                }
            })
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test fun queryShapeIsStrictAndUnavailableDataIsNeverReturnedAsEmpty() = testApplication {
        application { serverModule { "owner-a" } }
        for (path in listOf("me", "catalog", "progress", "custom-activities", "operations/$id", "history?period=week&date=2026-11-10")) {
            val response = client.get("/v1/$path") { bearerAuth("synthetic-token") }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status, path)
            assertFalse(response.bodyAsText().contains("\"items\":[]"))
        }
        for (path in listOf("me?uid=owner-b", "history?period=year&date=2026-11-10", "history?period=day&date=2026-02-30",
            "history?period=day&period=week&date=2026-11-10", "operations/not-uuid", "history?period=day")) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/v1/$path") { bearerAuth("synthetic-token") }.status, path)
        }
    }

    @Test fun onlyJsonUtf8IsAccepted() = testApplication {
        application { serverModule { "owner-a" } }
        for (type in listOf(ContentType.Text.Plain, ContentType.Application.Json.withParameter("charset", "utf-16"))) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/v1/commands") {
                bearerAuth("synthetic-token"); contentType(type); setBody(variants.first())
            }.status)
        }
    }

    @Test fun typedErrorsUseContractStatusAndUnexpectedExceptionsStayPrivate() = testApplication {
        var current: ApiError? = ApiError.RECORD_STALE
        application {
            serverModule(verifyToken = { "owner-a" }, handlers = ApiHandlers(command = { _, _ ->
                current?.let { throw ApiException(it) }
                error("private-provider-detail synthetic-secret-token owner-b")
            }))
        }
        for ((error, status) in listOf(ApiError.RECORD_STALE to HttpStatusCode.Conflict,
            ApiError.USERNAME_UNAVAILABLE to HttpStatusCode.Conflict,
            ApiError.DATE_NOT_ALLOWED to HttpStatusCode.UnprocessableEntity,
            ApiError.TEMPORARILY_UNAVAILABLE to HttpStatusCode.ServiceUnavailable,
            null to HttpStatusCode.ServiceUnavailable)) {
            current = error
            val response = client.post("/v1/commands") {
                bearerAuth("synthetic-secret-token"); contentType(ContentType.Application.Json); setBody(variants.first())
            }
            assertEquals(status, response.status)
            val text = response.bodyAsText()
            assertTrue(text.contains(error?.name ?: "TEMPORARILY_UNAVAILABLE"))
            listOf("private-provider-detail", "synthetic-secret-token", "owner-b", "Exception").forEach { assertFalse(text.contains(it)) }
        }
    }
}
