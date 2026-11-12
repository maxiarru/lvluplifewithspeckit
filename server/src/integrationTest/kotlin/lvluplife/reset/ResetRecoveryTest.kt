package lvluplife.reset

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
import kotlinx.coroutines.*
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.fixtures.FaultPoint
import lvluplife.fixtures.InjectedFault
import lvluplife.model.*
import lvluplife.persistence.*

/** Recovery HTTP contracts with actual local Auth verification and Firestore commits. No production fault switches. */
class ResetRecoveryTest {
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
        @Volatile var now: Instant = Instant.parse("2026-11-12T18:00:00Z")
        val failBefore = AtomicBoolean(); val loseAfter = AtomicBoolean()
        fun projection() = resources.documents.read(a.uid, listOf("projections", "current"))!!
        fun root(identity: Identity = a) = resources.documents.read(identity.uid, emptyList())!!
        fun cycle(identity: Identity = a) = root(identity)["currentCycleId"] as String
        fun documents(collection: String, identity: Identity = a) = resources.firestore.collection("users/${identity.uid.value}/$collection").get().get(10, TimeUnit.SECONDS).documents.associate { it.id to it.data }
        fun snapshot(identity: Identity = a) = listOf(root(identity), documents("cycles",identity), documents("projections",identity), documents("records",identity), documents("activities",identity), documents("operations",identity))
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
                    val base = accountHandlers(resources.firestore, AuthoritativeClock { f.now })
                    serverModule(base.copy(command = { uid, command ->
                        if (command.type in setOf(CommandType.RESET_PROGRESSION)) {
                            if (f.failBefore.compareAndSet(true, false)) throw InjectedFault(FaultPoint.BEFORE_COMMIT)
                        }
                        val result = base.command(uid, command)
                        if (command.type in setOf(CommandType.RESET_PROGRESSION) && result.body.path("outcome").textValue() in setOf("CONFIRMED", "REJECTED") && f.loseAfter.compareAndSet(true, false))
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

    /** Test installation journal, not the Android adapter: real Room coverage lives in ResetFlowTest. */
    private inner class Installation(val directory: java.nio.file.Path) {
        val file = directory.resolve("pending.json")
        val traffic = mutableListOf<String>()
        fun prepare(owner: Identity, cycle: String, id: OperationId = OperationId.create()): String {
            check(!java.nio.file.Files.exists(file))
            val envelope = json.writeValueAsString(mapOf("operationId" to id.value, "type" to "RESET_PROGRESSION",
                "cycleId" to cycle, "payload" to mapOf("confirmed" to true)))
            java.nio.file.Files.writeString(file,json.writeValueAsString(mapOf("uid" to owner.uid.value,"envelope" to envelope)))
            return envelope
        }
        fun pending(owner: Identity): String? {
            if (!java.nio.file.Files.exists(file)) return null
            val saved = json.readTree(java.nio.file.Files.readString(file))
            return saved.path("envelope").textValue().takeIf { saved.path("uid").textValue() == owner.uid.value }
        }
        suspend fun send(client: HttpClient, owner: Identity): HttpResponse {
            val envelope = requireNotNull(pending(owner))
            traffic += "POST:$envelope"
            return client.post("/v1/commands") { bearerAuth(owner.token);contentType(ContentType.Application.Json);setBody(envelope) }
        }
        suspend fun recover(client: HttpClient, owner: Identity, resendUnknown: Boolean = false): JsonNode? {
            val envelope = pending(owner) ?: return null
            val id = json.readTree(envelope).path("operationId").textValue()
            traffic += "GET:$id"
            val response = client.get("/v1/operations/$id") { bearerAuth(owner.token) }
            if (response.status != HttpStatusCode.OK) return null
            var result = body(response)
            if (result.path("outcome").textValue() == "UNKNOWN" && resendUnknown) result=body(send(client,owner))
            if (result.path("outcome").textValue() in setOf("CONFIRMED","REJECTED")) java.nio.file.Files.deleteIfExists(file)
            return result
        }
    }
    private suspend fun installations(block: suspend (Installation, Installation) -> Unit) {
        val first=java.nio.file.Files.createTempDirectory("reset-installation-a-")
        val second=java.nio.file.Files.createTempDirectory("reset-installation-b-")
        try { block(Installation(first),Installation(second)) }
        finally {
            for(dir in listOf(first,second)) { java.nio.file.Files.deleteIfExists(dir.resolve("pending.json"));java.nio.file.Files.deleteIfExists(dir) }
        }
    }
    private suspend fun body(response: HttpResponse): JsonNode = json.readTree(response.bodyAsText())
    private suspend fun confirmed(response: HttpResponse): JsonNode {
        assertEquals(HttpStatusCode.OK,response.status)
        return body(response).also { assertEquals("CONFIRMED",it.path("outcome").textValue()) }
    }
    private suspend fun HttpClient.reset(f: Fixture, id: OperationId=OperationId.create(), cycle: String=f.cycle()): HttpResponse =
        post("/v1/commands") { bearerAuth(f.a.token);contentType(ContentType.Application.Json)
            setBody(json.writeValueAsString(mapOf("operationId" to id.value,"type" to "RESET_PROGRESSION","cycleId" to cycle,"payload" to mapOf("confirmed" to true)))) }
    private suspend fun HttpClient.lookup(owner: Identity,id: String): JsonNode {
        val response=get("/v1/operations/$id") { bearerAuth(owner.token) };assertEquals(HttpStatusCode.OK,response.status);return body(response)
    }
    private suspend fun HttpClient.query(owner: Identity,path: String): JsonNode {
        val response=get("/v1/$path") { bearerAuth(owner.token) };assertEquals(HttpStatusCode.OK,response.status);return body(response)
    }
    private suspend fun HttpClient.seed(f: Fixture) {
        confirmed(post("/v1/commands") { bearerAuth(f.a.token);contentType(ContentType.Application.Json)
            setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value,"type" to "CREATE_RECORD","cycleId" to f.cycle(),
                "payload" to mapOf("activityId" to "strength","activityDate" to "2026-11-12","hours" to 0,"minutes" to 30)))) })
        confirmed(post("/v1/commands") { bearerAuth(f.a.token);contentType(ContentType.Application.Json)
            setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value,"type" to "CREATE_CUSTOM_ACTIVITY",
                "payload" to mapOf("name" to "Propia","category" to "Rutina","primary" to "STR","secondary" to "DEX")))) })
    }
    private fun id(envelope: String)=json.readTree(envelope).path("operationId").textValue()
    private fun revision(f: Fixture)=(f.root()["revision"] as Number).toLong()
    private fun assertOnlyProgressWasRepublished(before: List<Map<String,Any?>>, f: Fixture) {
        val next=(before[0].getValue("revision") as Number).toLong()+1
        assertEquals(before[0]+("revision" to next),f.root())
        @Suppress("UNCHECKED_CAST")
        val projection=before[2].getValue("current") as Map<String,Any?>
        assertEquals(projection+("revision" to next),f.projection())
        val after=f.snapshot()
        for(index in listOf(1,3,4,5)) assertEquals(before[index],after[index])
    }

    @Test fun beforeCommitFailureLeavesEverythingUnchangedThenReopenedJournalResendsExactEnvelopeOnce() = scenario { f -> installations { first,_ ->
        client.seed(f);val before=f.snapshot();val previousRevision=revision(f)
        assertEquals(1,f.documents("activities").size);assertEquals(1,f.documents("records").size)
        val envelope=first.prepare(f.a,f.cycle());val disk=java.nio.file.Files.readString(first.file)
        assertEquals(setOf("uid","envelope"),json.readTree(disk).fieldNames().asSequence().toSet());assertFalse(disk.contains(f.a.token))
        f.failBefore.set(true);assertEquals(HttpStatusCode.ServiceUnavailable,first.send(client,f.a).status)
        assertFalse(f.failBefore.get());assertEquals(before,f.snapshot());assertNull(f.receipt(OperationId.parse(id(envelope))))
        assertEquals("UNKNOWN",client.lookup(f.a,id(envelope)).path("outcome").textValue());assertEquals(disk,java.nio.file.Files.readString(first.file))
        val reopened=Installation(first.directory);val result=reopened.recover(client,f.a,resendUnknown=true)!!
        assertEquals("CONFIRMED",result.path("outcome").textValue());assertEquals(listOf("GET:${id(envelope)}","POST:$envelope"),reopened.traffic)
        assertNull(reopened.pending(f.a));assertEquals(2,f.documents("cycles").size);assertEquals(previousRevision+1,revision(f))
        assertEquals(before[3],f.documents("records"));assertEquals(before[4],f.documents("activities"))
        val settled=f.snapshot();assertEquals(result,confirmed(client.reset(f,OperationId.parse(id(envelope)),json.readTree(envelope).path("cycleId").textValue())))
        assertEquals(settled,f.snapshot())
    } }
    @Test fun lostResponseAfterCommitIsRecoveredByLookupOnlyFromReopenedOriginalInstallation() = scenario { f -> installations { first,_ ->
        client.seed(f);val cycle=f.cycle();val before=revision(f);val envelope=first.prepare(f.a,cycle)
        f.loseAfter.set(true);assertEquals(HttpStatusCode.ServiceUnavailable,first.send(client,f.a).status);assertFalse(f.loseAfter.get())
        assertNotNull(first.pending(f.a));assertEquals(before+1,revision(f));assertEquals(2,f.documents("cycles").size)
        assertNotNull(f.documents("cycles").getValue(cycle)["closedAt"])
        val committed=f.snapshot();val original=client.lookup(f.a,id(envelope));val reopened=Installation(first.directory)
        assertEquals(original,reopened.recover(client,f.a,resendUnknown=true));assertEquals(listOf("GET:${id(envelope)}"),reopened.traffic)
        assertNull(reopened.pending(f.a));assertEquals(committed,f.snapshot())
    } }
    @Test fun anotherInstallationReadsConfirmedProgressAndPreservedDataWithoutImportingPending() = scenario { f -> installations { first,second ->
        client.seed(f);val history=client.query(f.a,"history?period=day&date=2026-11-12");val me=client.query(f.a,"me")
        val definitions=f.documents("activities");assertEquals(1,definitions.size)
        assertTrue(me.path("profile").path("username").textValue().isNotBlank())
        first.prepare(f.a,f.cycle());f.loseAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable,first.send(client,f.a).status);val committed=f.snapshot()
        val otherDevice=createClient { }
        try {
            assertNull(second.pending(f.a));assertNull(second.recover(otherDevice,f.a,true));assertTrue(second.traffic.isEmpty())
            val progress=otherDevice.query(f.a,"progress")
            assertEquals(f.cycle(),progress.path("cycleId").textValue());assertEquals("novato",progress.path("classId").textValue())
            assertEquals(1,progress.path("generalLevel").intValue());assertEquals(1,progress.path("stage").intValue())
            for(attribute in progress.path("attributes")) { assertEquals(1,attribute.path("level").intValue());assertEquals(0,attribute.path("xp").intValue()) }
            assertEquals(me.path("profile").path("username"),otherDevice.query(f.a,"me").path("profile").path("username"))
            val custom=otherDevice.query(f.a,"custom-activities")
            assertEquals(1,custom.path("items").size());assertEquals("Propia",custom.path("items").single().path("name").textValue())
            val afterHistory=otherDevice.query(f.a,"history?period=day&date=2026-11-12")
            assertEquals(history.path("items").map { it.path("recordId").textValue() },afterHistory.path("items").map { it.path("recordId").textValue() })
            assertEquals(1,afterHistory.path("items").size())
            assertEquals(definitions,f.documents("activities"));assertNotNull(first.pending(f.a));assertOnlyProgressWasRepublished(committed,f)
        } finally { otherDevice.close() }
    } }
    @Test fun foreignAccountCannotReadOrRecoverOriginalOwnersJournalOrReceipt() = scenario { f -> installations { first,_ ->
        val envelope=first.prepare(f.a,f.cycle());f.loseAfter.set(true);assertEquals(HttpStatusCode.ServiceUnavailable,first.send(client,f.a).status)
        val a=f.snapshot();val b=f.snapshot(f.b);val disk=java.nio.file.Files.readString(first.file)
        val reopened=Installation(first.directory);assertNull(reopened.pending(f.b));assertNull(reopened.recover(client,f.b,true));assertTrue(reopened.traffic.isEmpty())
        val foreign=client.lookup(f.b,id(envelope));assertEquals("UNKNOWN",foreign.path("outcome").textValue());assertFalse(foreign.has("result"))
        assertEquals(disk,java.nio.file.Files.readString(first.file));assertEquals(a,f.snapshot());assertEquals(b,f.snapshot(f.b))
        assertEquals("CONFIRMED",reopened.recover(client,f.a)!!.path("outcome").textValue());assertEquals(a,f.snapshot())
    } }
    @Test fun missingLocalPendingDoesNotDiscoverOrRecreateAnotherInstallationsOperation() = scenario { f -> installations { first,second ->
        first.prepare(f.a,f.cycle());f.loseAfter.set(true);assertEquals(HttpStatusCode.ServiceUnavailable,first.send(client,f.a).status)
        java.nio.file.Files.delete(first.file);val committed=f.snapshot();val reopened=Installation(first.directory)
        assertNull(reopened.recover(client,f.a,true));assertNull(second.recover(client,f.a,true))
        assertTrue(reopened.traffic.isEmpty());assertTrue(second.traffic.isEmpty())
        assertEquals(f.cycle(),client.query(f.a,"progress").path("cycleId").textValue());assertOnlyProgressWasRepublished(committed,f)
    } }
    @Test fun retryAfterAnotherResetRejectsOriginalCycleWithoutRetargetingIt() = scenario { f -> installations { first,_ ->
        val oldCycle=f.cycle();val envelope=first.prepare(f.a,oldCycle);f.failBefore.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable,first.send(client,f.a).status);confirmed(client.reset(f))
        val current=f.cycle();val cycles=f.documents("cycles");val projection=f.projection();val revision=revision(f)
        val reopened=Installation(first.directory);val result=reopened.recover(client,f.a,true)!!
        assertEquals("REJECTED",result.path("outcome").textValue());assertEquals("CYCLE_CLOSED",result.path("error").path("code").textValue())
        assertEquals(listOf("GET:${id(envelope)}","POST:$envelope"),reopened.traffic);assertNull(reopened.pending(f.a))
        assertEquals(current,f.cycle());assertEquals(cycles,f.documents("cycles"));assertEquals(projection,f.projection());assertEquals(revision+1,revision(f))
    } }
    @Test fun lateLookupAndRepeatedOldEnvelopeNeverRollbackASecondReset() = scenario { f -> installations { first,_ ->
        val cycle=f.cycle();val envelope=first.prepare(f.a,cycle);f.loseAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable,first.send(client,f.a).status);val original=client.lookup(f.a,id(envelope))
        f.now=f.now.plusSeconds(86400);confirmed(client.reset(f));val latest=f.snapshot()
        val reopened=Installation(first.directory);assertEquals(original,reopened.recover(client,f.a,true))
        val replies=coroutineScope { listOf(async { client.reset(f,OperationId.parse(id(envelope)),cycle) },async { client.reset(f,OperationId.parse(id(envelope)),cycle) }).map { it.await() } }
        for(reply in replies)assertEquals(original,confirmed(reply))
        assertEquals(3,f.documents("cycles").size);assertEquals(latest,f.snapshot());assertEquals(listOf("GET:${id(envelope)}"),reopened.traffic)
    } }
    @Test fun lostDefinitiveRejectionIsRecoveredWithoutCreatingAnotherCycleOrReceipt() = scenario { f -> installations { first,_ ->
        val old=f.cycle();confirmed(client.reset(f));val envelope=first.prepare(f.a,old);f.loseAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable,first.send(client,f.a).status);assertFalse(f.loseAfter.get())
        val settled=f.snapshot();val receipt=client.lookup(f.a,id(envelope));assertEquals("REJECTED",receipt.path("outcome").textValue())
        assertEquals("CYCLE_CLOSED",receipt.path("error").path("code").textValue())
        val reopened=Installation(first.directory);assertEquals(receipt,reopened.recover(client,f.a,true))
        assertEquals(listOf("GET:${id(envelope)}"),reopened.traffic);assertEquals(2,f.documents("cycles").size);assertEquals(settled,f.snapshot())
    } }
    @Test fun invalidAuthenticationRetainsPendingUntilOriginalAccountCanRecover() = scenario { f -> installations { first,_ ->
        val envelope=first.prepare(f.a,f.cycle());f.loseAfter.set(true);assertEquals(HttpStatusCode.ServiceUnavailable,first.send(client,f.a).status)
        val committed=f.snapshot();val disk=java.nio.file.Files.readString(first.file);val reopened=Installation(first.directory)
        assertNull(reopened.recover(client,Identity(f.a.uid,"synthetic-invalid-token"),true))
        assertEquals(disk,java.nio.file.Files.readString(first.file));assertEquals(committed,f.snapshot())
        assertEquals("CONFIRMED",reopened.recover(client,f.a,true)!!.path("outcome").textValue())
        assertEquals(listOf("GET:${id(envelope)}","GET:${id(envelope)}"),reopened.traffic);assertEquals(committed,f.snapshot())
    } }
}
