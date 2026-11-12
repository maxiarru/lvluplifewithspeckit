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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.model.*
import lvluplife.persistence.*

/** Recovery HTTP contracts with actual local Auth verification and Firestore commits. No production fault switches. */
class ResetContractTest {
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
        var now=Instant.parse("2026-11-12T18:00:00Z")
        var pauseType: CommandType?=null
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        var pair: CompletableDeferred<Unit>?=null
        val arrivals=AtomicInteger()
        fun raceResets() { pair=CompletableDeferred();arrivals.set(0) }
        fun root(owner: Identity=a)=resources.documents.read(owner.uid,emptyList())!!
        fun cycle(owner: Identity=a)=root(owner)["currentCycleId"] as String
        fun projection(owner: Identity=a)=resources.documents.read(owner.uid,listOf("projections","current"))!!
        fun documents(collection: String,owner: Identity=a)=resources.firestore.collection("users/${owner.uid.value}/$collection")
            .get().get(10,TimeUnit.SECONDS).documents.associate { it.id to it.data }
        fun cycles(owner: Identity=a)=documents("cycles",owner)
        fun receipt(id: OperationId,owner: Identity=a)=OperationStore(resources.firestore).find(owner.uid,id)
    }
    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) {
        val config=ServerConfig.fromEnvironment()
        check(config.mode==BackendMode.EMULATOR && config.projectId=="demo-lvluplife")
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080",resources.firestore.options.host)
            assertEquals("127.0.0.1:8080",resources.firestore.options.emulatorHost)
            val f=Fixture(resources,identity(),identity())
            testApplication {
                application {
                    val base=accountHandlers(resources.firestore,AuthoritativeClock { f.now })
                    serverModule(base.copy(command={ uid,command ->
                        if(command.type==f.pauseType) { f.entered.complete(Unit);withTimeout(10_000) { f.release.await() } }
                        if(command.type==CommandType.RESET_PROGRESSION) f.pair?.let { pair ->
                            if(f.arrivals.incrementAndGet()==2) pair.complete(Unit)
                            withTimeout(10_000) { pair.await() }
                        }
                        base.command(uid,command)
                    }),verifyToken={ resources.identityVerifier.verify(it)?.value })
                }
                for(owner in listOf(f.a,f.b)) confirmed(client.post("/v1/commands") {
                    bearerAuth(owner.token);contentType(ContentType.Application.Json)
                    setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value,"type" to "COMPLETE_PROFILE",
                        "payload" to mapOf("username" to ("Z"+UUID.randomUUID().toString().replace("-","").take(12))))))
                })
                try { block(f) } finally { f.release.complete(Unit) }
            }
        }
    }
    private suspend fun body(response: HttpResponse): JsonNode=json.readTree(response.bodyAsText())
    private suspend fun confirmed(response: HttpResponse): JsonNode {
        assertEquals(HttpStatusCode.OK,response.status)
        return body(response).also { assertEquals("CONFIRMED",it.path("outcome").textValue()) }
    }
    private suspend fun cycleClosed(response: HttpResponse): JsonNode {
        assertEquals(HttpStatusCode.Conflict,response.status)
        return body(response).also { assertEquals("REJECTED",it.path("outcome").textValue());assertEquals("CYCLE_CLOSED",it.path("error").path("code").textValue()) }
    }
    private suspend fun HttpClient.command(f: Fixture,type: String,payload: Map<String,Any?>,id: OperationId=OperationId.create(),
        cycle: String?=f.cycle(),owner: Identity=f.a)=post("/v1/commands") {
        bearerAuth(owner.token);contentType(ContentType.Application.Json)
        val value=linkedMapOf<String,Any?>("operationId" to id.value,"type" to type,"payload" to payload)
        if(cycle!=null)value["cycleId"]=cycle
        setBody(json.writeValueAsString(value))
    }
    private suspend fun HttpClient.reset(f: Fixture,id: OperationId=OperationId.create(),cycle: String=f.cycle(),owner: Identity=f.a)=
        command(f,"RESET_PROGRESSION",mapOf("confirmed" to true),id,cycle,owner)
    private suspend fun HttpClient.create(f: Fixture,cycle: String=f.cycle(),minutes: Int=30,date: String="2026-11-12",activity: String="strength")=
        confirmed(command(f,"CREATE_RECORD",mapOf("activityId" to activity,"activityDate" to date,"hours" to minutes/60,"minutes" to minutes%60),cycle=cycle))
            .path("result").path("recordId").textValue()
    private suspend fun HttpClient.lookup(owner: Identity,id: OperationId)=body(get("/v1/operations/${id.value}") { bearerAuth(owner.token) })
    private suspend fun HttpClient.progress(owner: Identity): JsonNode {
        val response=get("/v1/progress") { bearerAuth(owner.token) };assertEquals(HttpStatusCode.OK,response.status);return body(response)
    }
    private fun assertInitial(p: JsonNode) {
        assertEquals(1,p.path("generalLevel").intValue());assertEquals(0,p.path("generalRemainder").intValue())
        assertEquals("novato",p.path("classId").textValue());assertEquals(1,p.path("stage").intValue())
        assertEquals(setOf("STR","DEX","CON","INT","WIT","MEN"),p.path("attributes").fieldNames().asSequence().toSet())
        for(value in p.path("attributes")) {
            assertEquals(1,value.path("level").intValue());assertEquals(0,value.path("xp").intValue());assertEquals(0,value.path("inactiveDates").intValue())
        }
    }

    @Test fun resetRequiresLiteralBooleanTrueAndRejectsMissingFalseOrExtraFieldsWithoutWrites() = scenario { f ->
        val root=f.root();val cycles=f.cycles();val projection=f.projection();val receipts=f.documents("operations")
        val invalid=listOf(emptyMap(),mapOf("confirmed" to false),mapOf("confirmed" to "true"),mapOf("confirmed" to 1),
            mapOf("confirmed" to null),mapOf("confirmed" to true,"xp" to 100),mapOf("confirmed" to true,"username" to "Other"))
        for(payload in invalid) {
            val response=client.command(f,"RESET_PROGRESSION",payload)
            assertEquals(HttpStatusCode.BadRequest,response.status);assertEquals("INVALID_REQUEST",body(response).path("error").path("code").textValue())
        }
        assertEquals(root,f.root());assertEquals(cycles,f.cycles());assertEquals(projection,f.projection());assertEquals(receipts,f.documents("operations"))
    }
    @Test fun resetRequiresExpectedCycleAndCannotAcceptClientOwnerOrTargetCycle() = scenario { f ->
        val root=f.root();val receipts=f.documents("operations")
        for(cycle in listOf(null,"","../foreign")) assertEquals(HttpStatusCode.BadRequest,
            client.command(f,"RESET_PROGRESSION",mapOf("confirmed" to true),cycle=cycle).status)
        for(extra in listOf("uid","newCycleId","revision","rulesVersion")) assertEquals(HttpStatusCode.BadRequest,
            client.command(f,"RESET_PROGRESSION",mapOf("confirmed" to true,extra to "synthetic-forbidden")).status)
        assertEquals(root,f.root());assertEquals(receipts,f.documents("operations"))
    }
    @Test fun unauthenticatedResetCannotChangeAnyAccountOrCreateReceipt() = scenario { f ->
        val root=f.root();val cycles=f.cycles();val receipts=f.documents("operations")
        for(token in listOf<String?>(null,"synthetic-invalid-token")) {
            val response=client.post("/v1/commands") { if(token!=null)bearerAuth(token);contentType(ContentType.Application.Json)
                setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value,"type" to "RESET_PROGRESSION",
                    "cycleId" to f.cycle(),"payload" to mapOf("confirmed" to true)))) }
            assertEquals(HttpStatusCode.Unauthorized,response.status)
        }
        assertEquals(root,f.root());assertEquals(cycles,f.cycles());assertEquals(receipts,f.documents("operations"))
    }
    @Test fun readingProfileHistoryAndCatalogueNeverRequestsImplicitReset() = scenario { f ->
        val root=f.root();val cycles=f.cycles();val projection=f.projection();val receipts=f.documents("operations")
        for(path in listOf("me","catalog","history?period=day&date=2026-11-12"))
            assertEquals(HttpStatusCode.OK,client.get("/v1/$path") { bearerAuth(f.a.token) }.status)
        assertEquals(root,f.root());assertEquals(cycles,f.cycles());assertEquals(projection,f.projection());assertEquals(receipts,f.documents("operations"))
    }
    @Test fun confirmedResetClosesOldCycleCreatesNewAndPublishesInitialProjectionAtomically() = scenario { f ->
        client.create(f,minutes=240);val old=f.cycle();val root=f.root();val id=OperationId.create()
        val receipt=confirmed(client.reset(f,id,old));val result=receipt.path("result");val current=f.cycle()
        assertNotEquals(old,current);assertEquals(old,result.path("closedCycleId").textValue());assertEquals(current,result.path("cycleId").textValue())
        assertEquals((root["revision"] as Number).toLong()+1,(f.root()["revision"] as Number).toLong())
        assertEquals(f.root()["revision"],result.path("revision").longValue());assertInitial(result.path("progression"))
        assertEquals(f.now.toString(),f.cycles().getValue(old)["closedAt"]);assertEquals(f.now.toString(),f.cycles().getValue(current)["startedAt"])
        assertNull(f.cycles().getValue(current)["closedAt"]);assertEquals(2,f.cycles().size)
        assertEquals(current,f.projection()["cycleId"]);assertEquals(f.root()["revision"],f.projection()["revision"])
        assertEquals(ReceiptOutcome.CONFIRMED,f.receipt(id)!!.outcome);assertEquals(receipt,client.lookup(f.a,id))
    }
    @Test fun resetPreservesAccountUsernameCustomActivitiesAndEveryHistoricalSnapshot() = scenario { f ->
        val custom=confirmed(client.command(f,"CREATE_CUSTOM_ACTIVITY",mapOf("name" to "Propia","category" to "Rutina","primary" to "STR","secondary" to "DEX"),cycle=null))
            .path("result").path("activityId").textValue()
        client.create(f,activity=custom);val deleted=client.create(f,minutes=5)
        confirmed(client.command(f,"DELETE_RECORD",mapOf("recordId" to deleted,"expectedVersion" to 1)))
        val root=f.root();val records=f.documents("records");val activities=f.documents("activities")
        confirmed(client.reset(f))
        assertEquals(root-"currentCycleId"-"revision",f.root()-"currentCycleId"-"revision")
        assertEquals(records,f.documents("records"));assertEquals(activities,f.documents("activities"))
        assertNotNull(f.documents("records").getValue(deleted)["deletedAt"])
        val history=body(client.get("/v1/history?period=day&date=2026-11-12") { bearerAuth(f.a.token) })
        assertEquals(1,history.path("items").size());assertInitial(client.progress(f.a))
    }
    @Test fun sameUuidRecoversOriginalReceiptWithoutCreatingAnotherCycle() = scenario { f ->
        val id=OperationId.create();val old=f.cycle();val receipt=confirmed(client.reset(f,id,old));val root=f.root();val cycles=f.cycles();val projection=f.projection()
        assertEquals(receipt,confirmed(client.reset(f,id,old)));assertEquals(receipt,client.lookup(f.a,id))
        assertEquals(root,f.root());assertEquals(cycles,f.cycles());assertEquals(projection,f.projection())
    }
    @Test fun differentUuidAgainstAlreadyClosedExpectedCycleIsRejectedWithoutAnotherReset() = scenario { f ->
        val old=f.cycle();confirmed(client.reset(f,cycle=old));val current=f.cycle();val cycles=f.cycles();val projection=f.projection();val id=OperationId.create()
        cycleClosed(client.reset(f,id,old));assertEquals(current,f.cycle());assertEquals(cycles,f.cycles());assertEquals(projection,f.projection())
        assertEquals(ReceiptOutcome.REJECTED,f.receipt(id)!!.outcome)
    }
    @Test fun concurrentDifferentResetUuidsAgainstSameCycleHaveExactlyOneWinner() = scenario { f ->
        val old=f.cycle();val ids=listOf(OperationId.create(),OperationId.create());f.raceResets()
        val responses=coroutineScope { listOf(async { client.reset(f,ids[0],old) },async { client.reset(f,ids[1],old) }).map { it.await() } }
        assertEquals(1,responses.count { it.status==HttpStatusCode.OK });confirmed(responses.single { it.status==HttpStatusCode.OK })
        cycleClosed(responses.single { it.status==HttpStatusCode.Conflict });assertEquals(2,f.cycles().size)
        assertEquals(1,ids.count { f.receipt(it)?.outcome==ReceiptOutcome.CONFIRMED });assertEquals(1,ids.count { f.receipt(it)?.outcome==ReceiptOutcome.REJECTED })
    }
    @Test fun concurrentSameResetUuidHasOneTransitionAndIdenticalReceipts() = scenario { f ->
        val old=f.cycle();val before=(f.root()["revision"] as Number).toLong();val id=OperationId.create();f.raceResets()
        val responses=coroutineScope { listOf(async { client.reset(f,id,old) },async { client.reset(f,id,old) }).map { it.await() } }
        assertEquals(confirmed(responses[0]),confirmed(responses[1]));assertEquals(2,f.cycles().size)
        assertEquals(before+1,(f.root()["revision"] as Number).toLong())
    }
    @Test fun originalReceiptAfterAnotherResetDoesNotRollBackCurrentPointerOrProjection() = scenario { f ->
        val old=f.cycle();val id=OperationId.create();val receipt=confirmed(client.reset(f,id,old));confirmed(client.reset(f))
        val root=f.root();val cycles=f.cycles();val projection=f.projection()
        assertEquals(receipt,confirmed(client.reset(f,id,old)));assertEquals(root,f.root());assertEquals(cycles,f.cycles());assertEquals(projection,f.projection())
        assertEquals(3,f.cycles().size)
    }
    @Test fun sameUuidWithDifferentExpectedCycleRejectsCollisionWithoutChangingReceipt() = scenario { f ->
        val old=f.cycle();val id=OperationId.create();val receipt=confirmed(client.reset(f,id,old));val root=f.root();val cycles=f.cycles()
        val collision=client.reset(f,id,f.cycle());assertEquals(HttpStatusCode.Conflict,collision.status)
        assertEquals("OPERATION_ID_REUSED",body(collision).path("error").path("code").textValue())
        assertEquals(receipt,client.lookup(f.a,id));assertEquals(root,f.root());assertEquals(cycles,f.cycles())
    }
    @Test fun overlappingCreateThatCommitsBeforeResetRemainsOnlyInOldCycleHistory() = scenario { f ->
        val old=f.cycle();f.pauseType=CommandType.RESET_PROGRESSION
        coroutineScope {
            val reset=async { client.reset(f,cycle=old) };withTimeout(10_000) { f.entered.await() }
            val record=try { client.create(f,cycle=old) } finally { f.release.complete(Unit) }
            confirmed(reset.await());assertEquals(old,f.documents("records").getValue(record)["cycleId"])
            assertEquals(1,f.documents("records").size);assertInitial(client.progress(f.a))
            val history=body(client.get("/v1/history?period=day&date=2026-11-12") { bearerAuth(f.a.token) })
            assertEquals(record,history.path("items").single().path("recordId").textValue())
        }
    }
    @Test fun overlappingResetThatCommitsBeforeCreateRejectsOldEnvelopeWithoutTransferringIt() = scenario { f ->
        val old=f.cycle();val id=OperationId.create();f.pauseType=CommandType.CREATE_RECORD
        coroutineScope {
            val create=async { client.command(f,"CREATE_RECORD",mapOf("activityId" to "strength","activityDate" to "2026-11-12","hours" to 0,"minutes" to 30),id,old) }
            withTimeout(10_000) { f.entered.await() }
            try { confirmed(client.reset(f,cycle=old)) } finally { f.release.complete(Unit) }
            cycleClosed(create.await());assertTrue(f.documents("records").isEmpty());assertEquals(2,f.cycles().size)
            assertEquals(ReceiptOutcome.REJECTED,f.receipt(id)!!.outcome);assertInitial(client.progress(f.a))
        }
    }
    @Test fun foreignExpectedCycleCannotResetOrExposeAnotherOwnersAccount() = scenario { f ->
        val beforeA=f.root();val cyclesA=f.cycles();val projectionA=f.projection()
        val response=client.reset(f,cycle=f.cycle(),owner=f.b);cycleClosed(response)
        assertFalse(response.bodyAsText().contains(f.a.uid.value));assertEquals(beforeA,f.root());assertEquals(cyclesA,f.cycles());assertEquals(projectionA,f.projection())
        assertEquals(1,f.cycles(f.b).size)
    }
    @Test fun sameUuidIsScopedToVerifiedOwnerAndCanResetBothAccountsIndependently() = scenario { f ->
        val id=OperationId.create();val a=confirmed(client.reset(f,id));assertEquals("UNKNOWN",client.lookup(f.b,id).path("outcome").textValue())
        val b=confirmed(client.reset(f,id,f.cycle(f.b),f.b))
        assertNotEquals(a.path("result").path("cycleId"),b.path("result").path("cycleId"));assertEquals(2,f.cycles().size);assertEquals(2,f.cycles(f.b).size)
    }
    @Test fun recentOldCycleRecordCannotBeEditedOrDeletedAfterReset() = scenario { f ->
        val record=client.create(f);val old=f.cycle();confirmed(client.reset(f));val stored=f.documents("records")
        cycleClosed(client.command(f,"EDIT_RECORD",mapOf("recordId" to record,"expectedVersion" to 1,"activityId" to "strength","activityDate" to "2026-11-12","hours" to 0,"minutes" to 35),cycle=old))
        cycleClosed(client.command(f,"DELETE_RECORD",mapOf("recordId" to record,"expectedVersion" to 1),cycle=old))
        assertEquals(stored,f.documents("records"))
    }
    @Test fun backdatedRecordAfterResetBelongsToNewCycleAndAwardsOnlyNewProgress() = scenario { f ->
        client.create(f,minutes=240,date="2026-11-11");confirmed(client.reset(f));val current=f.cycle()
        val record=client.create(f,date="2026-11-11");assertEquals(current,f.documents("records").getValue(record)["cycleId"])
        val p=client.progress(f.a);assertEquals(6,p.path("attributes").path("STR").path("xp").intValue());assertEquals(1,p.path("generalLevel").intValue())
        assertEquals(2,f.documents("records").size)
    }
    @Test fun previous24HoursDoNotPreventOneHourInNewCycleOrEraseOldHistory() = scenario { f ->
        val old=client.create(f,minutes=1440);confirmed(client.reset(f));val record=client.create(f,minutes=60)
        assertNotEquals(f.documents("records").getValue(old)["cycleId"],f.documents("records").getValue(record)["cycleId"])
        assertEquals(1440L,f.documents("records").getValue(old)["durationMinutes"])
        assertEquals(60L,f.documents("records").getValue(record)["durationMinutes"]);assertEquals(2,f.documents("records").size)
        assertEquals(12,client.progress(f.a).path("attributes").path("STR").path("xp").intValue())
    }
    @Test fun consecutiveConfirmedResetsPreserveSchemaAndRulesWithoutReusingCycleIdentity() = scenario { f ->
        val old=f.cycle();val schema=f.root()["schemaVersion"];val rules=f.cycles().getValue(old)["rulesVersion"]
        confirmed(client.reset(f));val middle=f.cycle();confirmed(client.reset(f));val current=f.cycle()
        assertEquals(3,setOf(old,middle,current).size);assertEquals(schema,f.root()["schemaVersion"])
        assertEquals(setOf(rules),f.cycles().values.map { it["rulesVersion"] }.toSet())
        assertEquals(2,f.cycles().values.count { it["closedAt"]!=null });assertNull(f.cycles().getValue(current)["closedAt"])
    }
}
