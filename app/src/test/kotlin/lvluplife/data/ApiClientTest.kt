package lvluplife.data

import java.net.SocketTimeoutException
import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ApiClientTest {
    private val id = "123e4567-e89b-42d3-a456-426614174000"
    private val confirmed get() = """{"operationId":"$id","outcome":"CONFIRMED","validatedAt":"2026-11-10T15:00:00Z","result":{"recordId":"record-a","revision":12}}"""
    private val command get() = ApiClient.encode(ApiCommand(id, ApiCommandType.CREATE_RECORD,
        mapOf("activityId" to "strength", "activityDate" to "2026-11-10", "hours" to 0, "minutes" to 30), "cycle-a"))
    private class FakeTransport(var response: TransportResponse = TransportResponse(200, "{}")) : HttpTransport {
        val requests = mutableListOf<TransportRequest>()
        var failure: IOException? = null
        override fun execute(request: TransportRequest): TransportResponse {
            requests += request
            failure?.let { throw it }
            return response
        }
    }
    private fun client(transport: FakeTransport, session: SessionToken? = SessionToken("owner-a", "synthetic-secret-token")) =
        ApiClient(ApiEndpoint.from("https://api.example.test/", isDebug = false), SessionTokenProvider { session }, transport)

    @Test fun commandSerializationPreservesIdentityAndNeverPutsTokenOrUidInBodyOrUrl() {
        val transport = FakeTransport(TransportResponse(200, confirmed))
        val result = client(transport).send("owner-a", command) as ApiReply.Receipt
        assertEquals(RemoteOutcome.CONFIRMED, result.value.outcome)
        assertEquals(id, result.value.operationId)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/v1/commands", request.url.encodedPath)
        assertEquals("Bearer synthetic-secret-token", request.headers["Authorization"])
        assertEquals(command, request.body)
        assertFalse(request.body!!.contains("owner-a"))
        assertFalse(request.url.toString().contains("synthetic-secret-token"))
        assertFalse(request.toString().contains("synthetic-secret-token"))
        assertFalse(SessionToken("owner-a", "synthetic-secret-token").toString().contains("synthetic-secret-token"))
    }

    @Test fun explicitRetryUsesExactlyTheOriginalEnvelope() {
        val transport = FakeTransport(TransportResponse(200, confirmed))
        val api = client(transport)
        api.send("owner-a", command); api.send("owner-a", command)
        assertEquals(2, transport.requests.size)
        assertEquals(transport.requests[0].body, transport.requests[1].body)
        assertEquals(command, transport.requests[0].body)
    }

    @Test fun serializationEscapesTextAndRejectsCredentialsAndManualXp() {
        val body = ApiClient.encode(ApiCommand(id, ApiCommandType.CREATE_CUSTOM_ACTIVITY,
            mapOf("name" to "Texto \"con comillas\"", "category" to "Rutina", "primary" to "STR")))
        assertEquals("Texto \"con comillas\"", com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("payload").get("name").textValue())
        for (key in listOf("xp", "owner", "authorization", "idToken", "refresh_token")) {
            assertThrows(IllegalArgumentException::class.java) {
                ApiClient.encode(ApiCommand(id, ApiCommandType.COMPLETE_PROFILE, mapOf(key to "synthetic-secret")))
            }
        }
    }

    @Test fun definitiveRejectionRequiresOriginalUuidAndReceiptMetadata() {
        for (status in listOf(409, 422)) {
            val body = """{"operationId":"$id","outcome":"REJECTED","validatedAt":"2026-11-10T15:00:00Z","error":{"code":"INVALID_INPUT","message":"private-server-message"}}"""
            val transport = FakeTransport(TransportResponse(status, body))
            val reply = client(transport).send("owner-a", command) as ApiReply.Receipt
            assertEquals(RemoteOutcome.REJECTED, reply.value.outcome)
            assertEquals(ApiFailureCode.INVALID_INPUT, reply.value.errorCode)
            assertFalse(reply.toString().contains("private-server-message"))
        }
    }

    @Test fun absentReceiptIsUnknownAndNeverDefinitiveRejection() {
        val transport = FakeTransport(TransportResponse(200, """{"outcome":"UNKNOWN"}"""))
        assertEquals(ApiReply.ReceiptAbsent, client(transport).lookup("owner-a", id))
        assertEquals("/v1/operations/$id", transport.requests.single().url.encodedPath)
    }

    @Test fun malformedMismatchedOrAmbiguousResponsesKeepMutationUnknown() {
        for (body in listOf("<html>error</html>", "{}", "[]", confirmed.replace(id, "123e4567-e89b-42d3-a456-426614174001"),
            confirmed.replace("2026-11-10T15:00:00Z", "not-an-instant"),
            confirmed.replace("\"CONFIRMED\"", "\"CONFIRMED\",\"outcome\":\"REJECTED\""))) {
            val reply = client(FakeTransport(TransportResponse(200, body))).send("owner-a", command) as ApiReply.Failure
            assertEquals(ApiFailureCode.UNREADABLE_RESPONSE, reply.code)
            assertTrue(reply.mayHaveCommitted)
            assertEquals(FailureAction.RESOLVE_OPERATION, reply.action)
        }
    }

    @Test fun timeoutAndConnectionFailureNeverMeanMutationWasNotApplied() {
        for (error in listOf(SocketTimeoutException("synthetic-secret-token"), IOException("private-url-and-token"))) {
            val transport = FakeTransport().apply { failure = error }
            val reply = client(transport).send("owner-a", command) as ApiReply.Failure
            assertTrue(reply.mayHaveCommitted)
            assertFalse(reply.retryAutomatically)
            assertEquals(FailureAction.RESOLVE_OPERATION, reply.action)
            assertEquals(1, transport.requests.size)
            assertFalse(reply.toString().contains("synthetic-secret-token"))
            assertFalse(reply.toString().contains("private-url-and-token"))
        }
    }

    @Test fun unauthorizedSuspendsAndForbiddenStopsWithoutAutomaticRetries() {
        for ((status, action) in listOf(401 to FailureAction.SUSPEND_AUTH, 403 to FailureAction.STOP)) {
            val transport = FakeTransport(TransportResponse(status, "private-error-body"))
            val reply = client(transport).send("owner-a", command) as ApiReply.Failure
            assertEquals(action, reply.action)
            assertFalse(reply.retryAutomatically)
            assertEquals(1, transport.requests.size)
            assertFalse(reply.toString().contains("private-error-body"))
        }
    }

    @Test fun missingOrDifferentSessionCannotSendAnOwnersOperation() {
        for (session in listOf(null, SessionToken("owner-b", "synthetic-token-b"))) {
            val transport = FakeTransport()
            val reply = client(transport, session).send("owner-a", command) as ApiReply.Failure
            assertEquals(FailureAction.SUSPEND_AUTH, reply.action)
            assertTrue(transport.requests.isEmpty())
        }
    }

    @Test fun queriesEncodeOnlyAllowedParametersAndFailureIsNotEmptyData() {
        val transport = FakeTransport(TransportResponse(200, """{"complete":true,"items":[]}"""))
        assertTrue(client(transport).query("owner-a", ApiQuery.History(HistoryPeriod.week, LocalDate.of(2026, 11, 10))) is ApiReply.Data)
        assertEquals("period=week&date=2026-11-10", transport.requests.single().url.encodedQuery)
        transport.response = TransportResponse(503, "private-error-body")
        val failure = client(transport).query("owner-a", ApiQuery.Progress) as ApiReply.Failure
        assertFalse(failure.mayHaveCommitted)
        assertEquals(FailureAction.RETRY_QUERY, failure.action)
    }

    @Test fun nonJsonAndNonReceiptErrorsDoNotConfirmOrRejectMutations() {
        for (response in listOf(TransportResponse(200, confirmed, "text/plain"),
            TransportResponse(503, "{}"), TransportResponse(409, """{"error":{"code":"RECORD_STALE"}}"""),
            TransportResponse(302, "private-redirect-body"))) {
            val transport = FakeTransport(response)
            assertTrue(client(transport).send("owner-a", command) is ApiReply.Failure)
            assertEquals(1, transport.requests.size)
        }
    }

    @Test fun productionEndpointRejectsHttpEmulatorsAndCredentialBearingUrls() {
        for (url in listOf("http://api.example.test", "http://127.0.0.1:8081", "https://127.0.0.1:8081",
            "https://user:secret@api.example.test", "https://api.example.test?token=secret", "https://api.example.test/#secret")) {
            assertThrows(IllegalArgumentException::class.java) { ApiEndpoint.from(url, isDebug = false) }
        }
        assertEquals("http", ApiEndpoint.from("http://10.0.2.2:8081", isDebug = true).base.scheme)
    }

    @Test fun productionTransportAllowsOnlyModernTlsAndNeverReplaysOrRedirects() {
        val transport = OkHttpTransport(ApiEndpoint.from("https://api.example.test", isDebug = false))
        assertFalse(transport.client.retryOnConnectionFailure)
        assertFalse(transport.client.followRedirects)
        assertFalse(transport.client.followSslRedirects)
        assertTrue(transport.client.connectionSpecs.all { spec -> spec.isTls && spec.tlsVersions!!.all {
            it == okhttp3.TlsVersion.TLS_1_2 || it == okhttp3.TlsVersion.TLS_1_3
        } })
    }

    @Test fun serviceUnavailableWithImmediateRetryAfterDoesNotReplayPost() {
        mockwebserver3.MockWebServer().use { server ->
            server.enqueue(mockwebserver3.MockResponse.Builder().code(503).addHeader("Retry-After", "0").build())
            server.enqueue(mockwebserver3.MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(confirmed).build())
            server.start()
            val endpoint = ApiEndpoint.from(server.url("/").toString(), isDebug = true)
            val api = ApiClient(endpoint, SessionTokenProvider { SessionToken("owner-a", "synthetic-token") }, OkHttpTransport(endpoint))
            val result = api.send("owner-a", command)
            assertTrue("503 must preserve unknown outcome before receipt lookup", result is ApiReply.Failure)
            assertEquals(1, server.requestCount)
        }
    }
}
