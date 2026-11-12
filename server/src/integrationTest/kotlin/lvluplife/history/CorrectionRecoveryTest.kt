package lvluplife.history

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
class CorrectionRecoveryTest {
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
        val times = java.util.concurrent.ConcurrentLinkedQueue<Instant>()
        @Volatile var snapshotGate: java.util.concurrent.CountDownLatch? = null
        val snapshots = AtomicInteger()
        fun clockNow(): Instant {
            snapshotGate?.let { gate ->
                if (snapshots.incrementAndGet() <= 2) {
                    gate.countDown()
                    check(gate.await(10, TimeUnit.SECONDS)) { "Both snapshots must reach preparation" }
                }
            }
            return times.poll() ?: now
        }
        fun race() { snapshotGate = java.util.concurrent.CountDownLatch(2); snapshots.set(0) }
        fun stored(id: String) = resources.documents.read(a.uid, listOf("records", id))!!
        fun projection() = resources.documents.read(a.uid, listOf("projections", "current"))!!
        fun root(identity: Identity = a) = resources.documents.read(identity.uid, emptyList())!!
        fun cycle(identity: Identity = a) = root(identity)["currentCycleId"] as String
        fun records(identity: Identity = a) = resources.firestore.collection("users/${identity.uid.value}/records").get().get(10, TimeUnit.SECONDS).documents
        fun receipt(id: OperationId, identity: Identity = a) = OperationStore(resources.firestore).find(identity.uid, id)
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
                    val base = accountHandlers(resources.firestore, AuthoritativeClock { f.clockNow() })
                    serverModule(base.copy(command = { uid, command ->
                        if (command.type in setOf(CommandType.EDIT_RECORD, CommandType.DELETE_RECORD)) {
                            if (f.failBefore.compareAndSet(true, false)) throw InjectedFault(FaultPoint.BEFORE_COMMIT)
                        }
                        val result = base.command(uid, command)
                        if (command.type in setOf(CommandType.EDIT_RECORD, CommandType.DELETE_RECORD) && result.status == HttpStatusCode.OK &&
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

    private suspend fun HttpClient.correct(f: Fixture, record: String, id: OperationId = OperationId.create(),
        delete: Boolean = false, minutes: Int = 60, date: String = "2026-11-11") = post("/v1/commands") {
        bearerAuth(f.a.token); contentType(ContentType.Application.Json)
        val data = linkedMapOf<String, Any>("recordId" to record, "expectedVersion" to 1)
        if (!delete) data.putAll(mapOf("activityId" to "strength", "activityDate" to date, "hours" to minutes / 60, "minutes" to minutes % 60))
        setBody(json.writeValueAsString(mapOf("operationId" to id.value, "type" to if (delete) "DELETE_RECORD" else "EDIT_RECORD",
            "cycleId" to f.cycle(), "payload" to data)))
    }
    private suspend fun HttpClient.record(f: Fixture) = confirmed(create(f, OperationId.create())).path("result").path("recordId").textValue()
    private suspend fun rejected(response: HttpResponse, code: String): JsonNode {
        assertEquals(if (code in setOf("RECORD_STALE", "RECORD_DELETED")) HttpStatusCode.Conflict else HttpStatusCode.UnprocessableEntity, response.status)
        return body(response).also { assertEquals("REJECTED", it.path("outcome").textValue()); assertEquals(code, it.path("error").path("code").textValue()) }
    }
    private fun assertRejectedRoot(before: Map<String, Any?>, f: Fixture) {
        assertEquals(before + ("revision" to ((before["revision"] as Number).toLong()+1)), f.root())
    }
    @Test fun finalValidationCrossing299To300RejectsPreparedDelete() = scenario { f ->
        val record=client.record(f); val original=f.stored(record); val before=f.root(); val projection=f.projection()
        f.times.add(f.now.plusSeconds(299)); f.now=f.now.plusSeconds(300)
        rejected(client.correct(f,record,delete=true),"EDIT_WINDOW_EXPIRED")
        assertEquals(original,f.stored(record)); assertRejectedRoot(before,f); assertEquals(projection,f.projection())
    }
    @Test fun validEditAcrossMidnightRebuildsProjectionForNewBusinessDate() = scenario { f ->
        f.now=Instant.parse("2026-11-12T02:59:59Z"); val record=client.record(f); val original=f.stored(record)
        f.times.add(f.now); f.now=Instant.parse("2026-11-12T03:00:00Z")
        val receipt=confirmed(client.correct(f,record))
        assertEquals("2026-11-12",receipt.path("result").path("progression").path("businessDate").textValue())
        assertEquals(f.now.toString(),receipt.path("validatedAt").textValue())
        assertEquals(original["createdAt"],f.stored(record)["createdAt"]); assertEquals(2L,f.stored(record)["version"])
        assertEquals("2026-11-12",f.projection()["asOfDate"])
    }
    private fun revision(f: Fixture) = (f.root()["revision"] as Number).toLong()

    @Test fun editEditSameSnapshotCommitsOnlyWinnerAndRejectsStaleLoser() = scenario { f ->
        val record = client.record(f); val before = revision(f); val original = f.stored(record)
        val ids = listOf(OperationId.create(), OperationId.create()); f.race()
        val responses = coroutineScope { listOf(async { client.correct(f, record, ids[0], minutes=60) },
            async { client.correct(f, record, ids[1], minutes=90) }).map { it.await() } }
        val winnerIndex = responses.indexOfFirst { it.status == HttpStatusCode.OK }
        assertTrue(winnerIndex >= 0); val winner = confirmed(responses[winnerIndex])
        rejected(responses[1-winnerIndex], "RECORD_STALE")
        assertEquals(before+2, revision(f)); assertEquals(2L, f.stored(record)["version"])
        assertEquals(original["createdAt"], f.stored(record)["createdAt"])
        assertEquals(if (winnerIndex==0) 60L else 90L, f.stored(record)["durationMinutes"])
        assertEquals(winner.path("result").path("progression").path("attributes").path("STR").path("xp").longValue(),
            client.progress(f.a).path("attributes").path("STR").path("xp").longValue())
        for (id in ids) assertNotNull(f.receipt(id))
    }
    @Test fun editDeleteSameSnapshotNeverResurrectsOrOverwritesWinner() = scenario { f ->
        val record = client.record(f); val before = revision(f); f.race()
        val responses = coroutineScope { listOf(async { client.correct(f,record) }, async { client.correct(f,record,delete=true) }).map { it.await() } }
        assertEquals(1, responses.count { it.status==HttpStatusCode.OK })
        val deleted = f.stored(record)["deletedAt"] != null
        confirmed(responses[if(deleted) 1 else 0]); rejected(responses[if(deleted) 0 else 1], if(deleted) "RECORD_DELETED" else "RECORD_STALE")
        assertEquals(before+2, revision(f)); assertEquals(2L, f.stored(record)["version"]); assertEquals(1, f.records().size)
        val xp=client.progress(f.a).path("attributes").path("STR").path("xp").longValue()
        assertEquals(if(deleted) 0L else 12L,xp)
    }
    @Test fun sameUuidConcurrentEditsReturnIdenticalReceiptAndSingleVersion() = scenario { f ->
        val record=client.record(f); val id=OperationId.create(); val before=revision(f); f.race()
        val responses=coroutineScope { listOf(async { client.correct(f,record,id) },async { client.correct(f,record,id) }).map { it.await() } }
        assertEquals(confirmed(responses[0]),confirmed(responses[1])); assertEquals(before+1,revision(f)); assertEquals(2L,f.stored(record)["version"])
    }
    @Test fun editAcceptedAt299Seconds() = boundary(false,299)
    @Test fun deleteAcceptedAt299Seconds() = boundary(true,299)
    @Test fun editRejectedAt300Seconds() = boundary(false,300)
    @Test fun deleteRejectedAt300Seconds() = boundary(true,300)
    private fun boundary(delete: Boolean, seconds: Long) = scenario { f ->
        val record=client.record(f); val before=f.stored(record); val root=f.root(); val projection=f.projection()
        f.now=f.now.plusSeconds(seconds)
        val response=client.correct(f,record,delete=delete)
        if(seconds<300) { confirmed(response); assertEquals(2L,f.stored(record)["version"]) }
        else { rejected(response,"EDIT_WINDOW_EXPIRED"); assertEquals(before,f.stored(record)); assertRejectedRoot(root,f); assertEquals(projection,f.projection()) }
    }
    @Test fun finalValidationCrossing299To300RejectsPreparedEdit() = scenario { f ->
        val record=client.record(f); val original=f.stored(record); val before=f.root(); val projection=f.projection()
        f.times.add(f.now.plusSeconds(299)); f.now=f.now.plusSeconds(300)
        rejected(client.correct(f,record),"EDIT_WINDOW_EXPIRED")
        assertEquals(original,f.stored(record)); assertRejectedRoot(before,f); assertEquals(projection,f.projection())
    }
    @Test fun midnightBetweenPreparationAndCommitRevalidatesDateWithoutPartialEdit() = scenario { f ->
        f.now=Instant.parse("2026-11-12T02:59:59Z"); val record=client.record(f)
        val original=f.stored(record); val before=f.root(); val projection=f.projection()
        f.times.add(f.now); f.now=Instant.parse("2026-11-12T03:00:00Z")
        rejected(client.correct(f,record,date="2026-11-09"),"DATE_NOT_ALLOWED")
        assertEquals(original,f.stored(record)); assertRejectedRoot(before,f); assertEquals(projection,f.projection())
    }
    @Test fun editFailureBeforeCommitAtMidnightRemainsUnknownThenRevalidatesSameEnvelope() = beforeMidnight(false)
    @Test fun deleteFailureBeforeCommitAtMidnightRemainsUnknownThenCommitsOnce() = beforeMidnight(true)
    private fun beforeMidnight(delete: Boolean) = scenario { f ->
        f.now=Instant.parse("2026-11-12T02:59:59Z"); val record=client.record(f); val id=OperationId.create()
        val original=f.stored(record); val before=f.root(); val projection=f.projection(); f.failBefore.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable,client.correct(f,record,id,delete,date="2026-11-09").status)
        assertNull(f.receipt(id)); assertEquals("UNKNOWN",body(client.lookup(f.a,id)).path("outcome").textValue())
        assertEquals(original,f.stored(record)); assertEquals(before,f.root()); assertEquals(projection,f.projection())
        f.now=Instant.parse("2026-11-12T03:00:00Z")
        val response=client.correct(f,record,id,delete,date="2026-11-09")
        if(delete) confirmed(response) else rejected(response,"DATE_NOT_ALLOWED")
        val settled=f.stored(record); val settledRoot=f.root()
        assertEquals(body(client.lookup(f.a,id)),body(client.correct(f,record,id,delete,date="2026-11-09")))
        assertEquals(settled,f.stored(record)); assertEquals(settledRoot,f.root())
    }
    @Test fun editLostResponseAcrossMidnightAndWindowExpiryRecoversExactReceipt() = afterMidnight(false)
    @Test fun deleteLostResponseAcrossMidnightAndWindowExpiryRecoversExactReceipt() = afterMidnight(true)
    private fun afterMidnight(delete: Boolean) = scenario { f ->
        f.now=Instant.parse("2026-11-12T02:59:59Z"); val record=client.record(f); val id=OperationId.create(); f.loseAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable,client.correct(f,record,id,delete,date="2026-11-09").status)
        val receipt=body(client.lookup(f.a,id)); assertEquals("CONFIRMED",receipt.path("outcome").textValue())
        val root=f.root(); val stored=f.stored(record); val projection=f.projection(); assertEquals(2L,stored["version"])
        f.now=Instant.parse("2026-11-15T15:00:00Z")
        val responses=coroutineScope { listOf(async { client.correct(f,record,id,delete,date="2026-11-09") },
            async { client.correct(f,record,id,delete,date="2026-11-09") }).map { it.await() } }
        for(response in responses) assertEquals(receipt,confirmed(response))
        assertEquals(root,f.root()); assertEquals(stored,f.stored(record)); assertEquals(projection,f.projection())
        val foreign=body(client.lookup(f.b,id)); assertEquals("UNKNOWN",foreign.path("outcome").textValue()); assertFalse(foreign.has("result"))
    }
}
