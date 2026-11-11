package lvluplife.record

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.net.URI
import java.net.http.HttpClient as JavaHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse as JavaHttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.fixtures.FaultPoint
import lvluplife.fixtures.InjectedFault
import lvluplife.model.*
import lvluplife.persistence.*

/** Recovery HTTP contracts with actual local Auth verification and Firestore commits. No production fault switches. */
class CreateRecoveryTest {
    private val json = ObjectMapper()
    private class Identity(val uid: Uid, val token: String) { override fun toString() = "Identity(redacted)" }
    private fun identity(): Identity {
        val response = JavaHttpClient.newHttpClient().send(HttpRequest.newBuilder(URI(
            "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key"))
            .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"returnSecureToken":true}""")).build(), JavaHttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        val body = json.readTree(response.body())
        return Identity(Uid(body.path("localId").textValue()), body.path("idToken").textValue())
    }
    private class Fixture(val resources: ServerResources, val a: Identity, val b: Identity) {
        @Volatile var now: Instant = Instant.parse("2026-11-11T15:00:00Z")
        val failBefore = AtomicBoolean(); val loseAfter = AtomicBoolean()
        @Volatile var gate: CompletableDeferred<Unit>? = null
        val arrivals = AtomicInteger()
        fun root(identity: Identity = a) = resources.documents.read(identity.uid, emptyList())!!
        fun cycle(identity: Identity = a) = root(identity)["currentCycleId"] as String
        fun records(identity: Identity = a) = resources.firestore.collection("users/${identity.uid.value}/records").get().get(10, TimeUnit.SECONDS).documents
        fun receipt(id: OperationId, identity: Identity = a) = OperationStore(resources.firestore).find(identity.uid, id)
        fun synchronizeTwoRequests() { gate = CompletableDeferred(); arrivals.set(0) }
    }
    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080", resources.firestore.options.host)
            assertEquals("127.0.0.1:8080", resources.firestore.options.emulatorHost)
            val f = Fixture(resources, identity(), identity())
            testApplication {
                application {
                    val base = accountHandlers(resources.firestore, AuthoritativeClock { f.now })
                    serverModule(base.copy(command = { uid, command ->
                        if (command.type == CommandType.CREATE_RECORD) {
                            f.gate?.let { gate ->
                                if (f.arrivals.incrementAndGet() == 2) gate.complete(Unit)
                                withTimeout(10_000) { gate.await() }
                            }
                            if (f.failBefore.compareAndSet(true, false)) throw InjectedFault(FaultPoint.BEFORE_COMMIT)
                        }
                        val result = base.command(uid, command)
                        if (command.type == CommandType.CREATE_RECORD && result.status == HttpStatusCode.OK &&
                            result.body.path("outcome").textValue() == "CONFIRMED" && f.loseAfter.compareAndSet(true, false))
                            throw InjectedFault(FaultPoint.AFTER_COMMIT)
                        result
                    }), verifyToken = { resources.identityVerifier.verify(it)?.value })
                }
                for (owner in listOf(f.a, f.b)) {
                    val response = client.post("/v1/commands") {
                        bearerAuth(owner.token); contentType(ContentType.Application.Json)
                        setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value, "type" to "COMPLETE_PROFILE",
                            "payload" to mapOf("username" to ("R" + UUID.randomUUID().toString().replace("-", "").take(12))))))
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                }
                block(f)
            }
        }
    }
    private suspend fun HttpClient.create(f: Fixture, id: OperationId, minutes: Int = 30, owner: Identity = f.a) = post("/v1/commands") {
        bearerAuth(owner.token); contentType(ContentType.Application.Json)
        setBody(json.writeValueAsString(mapOf("operationId" to id.value, "type" to "CREATE_RECORD", "cycleId" to f.cycle(owner),
            "payload" to mapOf("activityId" to "strength", "activityDate" to "2026-11-11", "hours" to minutes / 60, "minutes" to minutes % 60))))
    }
    private suspend fun HttpClient.lookup(owner: Identity, id: OperationId) = get("/v1/operations/${id.value}") { bearerAuth(owner.token) }
    private suspend fun body(response: HttpResponse): JsonNode = json.readTree(response.bodyAsText())
    private suspend fun confirmed(response: HttpResponse): JsonNode {
        assertEquals(HttpStatusCode.OK, response.status)
        return body(response).also { assertEquals("CONFIRMED", it.path("outcome").textValue()) }
    }
    private suspend fun HttpClient.progress(owner: Identity): JsonNode {
        val response = get("/v1/progress") { bearerAuth(owner.token) }
        assertEquals(HttpStatusCode.OK, response.status); return body(response)
    }

    @Test fun failureBeforeCommitIsUnknownAndSameEnvelopeCanBeAppliedOnce() = scenario { f ->
        val id = OperationId.create(); val before = f.root(); f.failBefore.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.create(f, id).status)
        assertFalse(f.failBefore.get()); assertNull(f.receipt(id)); assertTrue(f.records().isEmpty()); assertEquals(before, f.root())
        assertEquals("UNKNOWN", body(client.lookup(f.a, id)).path("outcome").textValue())
        val receipt = confirmed(client.create(f, id))
        assertEquals(receipt, confirmed(client.create(f, id))); assertEquals(1, f.records().size)
        assertEquals(6, client.progress(f.a).path("attributes").path("STR").path("xp").intValue())
    }
    @Test fun lostResponseAfterCommitReturnsExactReceiptWithoutAnotherWrite() = scenario { f ->
        val id = OperationId.create(); f.loseAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.create(f, id).status)
        assertFalse(f.loseAfter.get()); assertEquals(ReceiptOutcome.CONFIRMED, f.receipt(id)?.outcome)
        val before = f.root(); val receipt = body(client.lookup(f.a, id))
        assertEquals(receipt, confirmed(client.create(f, id))); assertEquals(before, f.root()); assertEquals(1, f.records().size)
    }
    @Test fun simultaneousFirstAttemptsWithSameUuidProduceOneRecordAndOneReceipt() = scenario { f ->
        val id = OperationId.create(); f.synchronizeTwoRequests()
        val responses = coroutineScope { listOf(async { client.create(f, id) }, async { client.create(f, id) }).map { it.await() } }
        val first = confirmed(responses[0]); assertEquals(first, confirmed(responses[1]))
        assertEquals(1, f.records().size); assertEquals(first.path("result").path("recordId").textValue(), f.records().single().id)
        assertEquals((f.root()["revision"] as Number).toLong(), f.receipt(id)!!.revision.value)
    }
    @Test fun concurrentRetriesAfterLostResponseAndDateExpiryDoNotReapply() = scenario { f ->
        val id = OperationId.create(); f.loseAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.create(f, id).status)
        val original = body(client.lookup(f.a, id)); val before = f.root()
        f.now = Instant.parse("2026-11-15T15:00:00Z"); f.synchronizeTwoRequests()
        val responses = coroutineScope { listOf(async { client.create(f, id) }, async { client.create(f, id) }).map { it.await() } }
        for (response in responses) assertEquals(original, confirmed(response))
        assertEquals(before, f.root()); assertEquals(1, f.records().size)
        assertEquals(6, client.progress(f.a).path("attributes").path("STR").path("xp").intValue())
    }
    @Test fun concurrentUuidCollisionKeepsOnlyTheWinningPayload() = scenario { f ->
        val id = OperationId.create(); f.synchronizeTwoRequests()
        val responses = coroutineScope { listOf(async { client.create(f, id, 30) }, async { client.create(f, id, 35) }).map { it.await() } }
        assertEquals(1, responses.count { it.status == HttpStatusCode.OK })
        val rejected = responses.single { it.status == HttpStatusCode.Conflict }
        assertEquals("OPERATION_ID_REUSED", body(rejected).path("error").path("code").textValue())
        val winner = confirmed(responses.single { it.status == HttpStatusCode.OK })
        assertEquals(winner, body(client.lookup(f.a, id))); assertEquals(1, f.records().size)
        val minutes = f.records().single().getLong("durationMinutes")!!
        val expectedXp = mapOf(30L to 6L, 35L to 7L).getValue(minutes)
        assertEquals(expectedXp, winner.path("result").path("progression").path("attributes").path("STR").path("xp").longValue())
    }
    @Test fun independentClientReadsSavedProgressWithoutImportingPendingIdentity() = scenario { f ->
        val id = OperationId.create(); f.loseAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.create(f, id).status)
        val second = createClient { }
        try {
            val p = second.progress(f.a)
            assertEquals(6, p.path("attributes").path("STR").path("xp").intValue())
            assertFalse(p.has("pending")); assertFalse(p.has("operations")); assertFalse(p.toString().contains(id.value))
            val unknown = body(second.lookup(f.a, OperationId.create()))
            assertEquals("UNKNOWN", unknown.path("outcome").textValue())
            val original = body(client.lookup(f.a, id))
            assertEquals(original, confirmed(client.create(f, id))); assertEquals(1, f.records().size)
        } finally { second.close() }
    }
    @Test fun sameUuidBelongsToVerifiedUidAndCannotExposeAnotherAccountsResult() = scenario { f ->
        val id = OperationId.create(); val a = confirmed(client.create(f, id))
        val foreign = body(client.lookup(f.b, id))
        assertEquals("UNKNOWN", foreign.path("outcome").textValue()); assertFalse(foreign.has("result"))
        val b = confirmed(client.create(f, id, 5, f.b))
        assertNotEquals(a.path("result").path("recordId"), b.path("result").path("recordId"))
        assertEquals(6, client.progress(f.a).path("attributes").path("STR").path("xp").intValue())
        assertEquals(1, client.progress(f.b).path("attributes").path("STR").path("xp").intValue())
        assertEquals(1, f.records(f.a).size); assertEquals(1, f.records(f.b).size)
    }
    @Test fun oldReceiptDoesNotReplaceNewerProgressAfterItsDateExpires() = scenario { f ->
        val id = OperationId.create(); val original = confirmed(client.create(f, id))
        confirmed(client.create(f, OperationId.create()))
        val before = f.root(); f.now = Instant.parse("2026-11-15T15:00:00Z")
        assertEquals(original, confirmed(client.create(f, id))); assertEquals(before, f.root())
        val p = client.progress(f.a)
        assertEquals(12, p.path("attributes").path("STR").path("xp").intValue())
        assertEquals("2026-11-15", p.path("businessDate").textValue())
        assertTrue(p.path("revision").longValue() > original.path("result").path("revision").longValue())
        assertEquals(2, f.records().size)
    }
}
