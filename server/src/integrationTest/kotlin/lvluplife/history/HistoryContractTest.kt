package lvluplife.history

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.time.Instant
import java.time.LocalDate
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

/** Contracts over local Firestore. Identity, clock, historical snapshots and faults are fixture-only. */
class HistoryContractTest {
    private val json = ObjectMapper()
    private class Fixture(val resources: ServerResources) {
        val a = Uid("synthetic-history-${UUID.randomUUID()}"); val b = Uid("synthetic-history-${UUID.randomUUID()}")
        val tokenA = "synthetic-history-a"; val tokenB = "synthetic-history-b"
        @Volatile var now = Instant.parse("2026-11-01T15:00:00Z")
        val loseAfter = AtomicBoolean(); val failBefore = AtomicBoolean()
        fun verify(token: String) = when(token) { tokenA -> a.value; tokenB -> b.value; else -> null }
        fun root(uid: Uid = a) = resources.documents.read(uid, emptyList())!!
        fun cycle(uid: Uid = a) = root(uid)["currentCycleId"] as String
        fun records(uid: Uid = a) = resources.firestore.collection("users/${uid.value}/records").get().get(10, TimeUnit.SECONDS).documents
        fun projection(uid: Uid = a) = resources.documents.read(uid, listOf("projections", "current"))
        fun receipt(id: OperationId, uid: Uid = a) = OperationStore(resources.firestore).find(uid,id)
        fun stored(id: String, uid: Uid = a) = resources.documents.read(uid,listOf("records",id))!!
        fun seed(id: String, date: String, born: Instant = LocalDate.parse(date).atTime(12,0).toInstant(java.time.ZoneOffset.UTC),
            recordCycle: String = cycle(), deleted: Boolean = false, minutes: Int = 1) {
            var record = ActivityRecord.create(RecordId(id), CycleId(recordCycle), ActivityCatalog.find(ActivityId("strength"))!!, LocalDate.parse(date), minutes, born)
            if (deleted) record = record.markDeleted(born.plusMillis(1))
            resources.documents.write(a,listOf("records",id),RecordStore(resources.firestore).fields(record))
        }
        fun newCycle(): String {
            val old = cycle(); val next = "synthetic-next-${UUID.randomUUID()}"
            resources.documents.write(a,listOf("cycles",old),resources.documents.read(a,listOf("cycles",old))!! + ("closedAt" to now.toString()))
            resources.documents.write(a,listOf("cycles",next),mapOf("startedAt" to now.toString(),"rulesVersion" to 1L))
            resources.documents.write(a,emptyList(),root() + mapOf("currentCycleId" to next,"revision" to ((root()["revision"] as Long)+1)))
            return next
        }
        fun seedPage(count: Int): List<String> {
            val ids = (0 until count).map { "synthetic-page-" + it.toString().padStart(4,'0') }
            val store = RecordStore(resources.firestore)
            val currentCycle = CycleId(cycle())
            for (chunk in ids.chunked(400)) {
                val batch = resources.firestore.batch()
                for(id in chunk) {
                    val record = ActivityRecord.create(RecordId(id),currentCycle,ActivityCatalog.find(ActivityId("strength"))!!,
                        LocalDate.parse("2026-11-11"),1,now.minusSeconds(1))
                    batch.set(resources.firestore.document("users/${a.value}/records/$id"),store.fields(record))
                }
                batch.commit().get(15,TimeUnit.SECONDS)
            }
            return ids
        }
    }
    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080",resources.firestore.options.host); assertEquals("127.0.0.1:8080",resources.firestore.options.emulatorHost)
            val f = Fixture(resources)
            testApplication {
                application {
                    val base = accountHandlers(resources.firestore,AuthoritativeClock { f.now })
                    serverModule(base.copy(command = { uid,command ->
                        val correction = command.type in setOf(CommandType.EDIT_RECORD,CommandType.DELETE_RECORD)
                        if(correction && f.failBefore.compareAndSet(true,false)) throw InjectedFault(FaultPoint.BEFORE_COMMIT)
                        val response = base.command(uid,command)
                        if(correction && response.status == HttpStatusCode.OK && response.body.path("outcome").textValue()=="CONFIRMED" && f.loseAfter.compareAndSet(true,false))
                            throw InjectedFault(FaultPoint.AFTER_COMMIT)
                        response
                    }),f::verify)
                }
                for(token in listOf(f.tokenA,f.tokenB)) {
                    val response=client.post("/v1/commands") { bearerAuth(token); contentType(ContentType.Application.Json)
                        setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value,"type" to "COMPLETE_PROFILE",
                            "payload" to mapOf("username" to ("H"+UUID.randomUUID().toString().replace("-","").take(12)))))) }
                    assertEquals(HttpStatusCode.OK,response.status)
                }
                f.now=Instant.parse("2026-11-11T15:00:00Z"); block(f)
            }
        }
    }
    private suspend fun body(response: HttpResponse): JsonNode = json.readTree(response.bodyAsText())
    private suspend fun confirmed(response: HttpResponse): JsonNode {
        assertEquals(HttpStatusCode.OK,response.status)
        return body(response).also { assertEquals("CONFIRMED",it.path("outcome").textValue()) }
    }
    private suspend fun rejected(response: HttpResponse, code: String): JsonNode {
        val expected=if(code in setOf("RECORD_STALE","RECORD_DELETED","CYCLE_CLOSED","OPERATION_ID_REUSED")) HttpStatusCode.Conflict else HttpStatusCode.UnprocessableEntity
        assertEquals(expected,response.status)
        return body(response).also { assertEquals("REJECTED",it.path("outcome").textValue()); assertEquals(code,it.path("error").path("code").textValue()) }
    }
    private suspend fun HttpClient.command(f: Fixture,type: String,data: Map<String,Any?>,id: OperationId = OperationId.create(),
        token: String = f.tokenA,cycle: String = f.cycle()) = post("/v1/commands") {
        bearerAuth(token);contentType(ContentType.Application.Json)
        setBody(json.writeValueAsString(mapOf("operationId" to id.value,"type" to type,"cycleId" to cycle,"payload" to data)))
    }
    private suspend fun HttpClient.create(f: Fixture,activity: String="strength", minutes: Int=30, date: String="2026-11-11"): String {
        val data=linkedMapOf<String,Any?>("activityId" to activity,"activityDate" to date)
        if(activity!="meal") { data["hours"]=minutes/60;data["minutes"]=minutes%60 }
        return confirmed(command(f,"CREATE_RECORD",data)).path("result").path("recordId").textValue()
    }
    private fun editData(id: String,version: Long=1,date: String="2026-11-11",activity: String="strength",minutes: Int=60) =
        mapOf<String,Any?>("recordId" to id,"expectedVersion" to version,"activityDate" to date,"activityId" to activity,"hours" to minutes/60,"minutes" to minutes%60)
    private fun deleteData(id: String,version: Long=1) = mapOf<String,Any?>("recordId" to id,"expectedVersion" to version)
    private suspend fun HttpClient.history(f: Fixture,period: String="day",date: String="2026-11-11",token: String=f.tokenA): JsonNode {
        val response=get("/v1/history?period=$period&date=$date") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK,response.status)
        return body(response).also { assertTrue(it.path("complete").isBoolean && it.path("complete").booleanValue()); assertTrue(it.path("items").isArray); assertTrue(it.path("revision").isIntegralNumber) }
    }
    private fun ids(history: JsonNode) = history.path("items").map { it.path("recordId").textValue() }

    @Test fun emptySuccessfulDayHasExplicitBoundsAndDoesNotMutateRevisionOrReceipts() = scenario { f ->
        val root=f.root();val receipts=f.resources.firestore.collection("users/${f.a.value}/operations").get().get().size()
        val h=client.history(f)
        assertEquals(f.now.toString(),h.path("observedAt").textValue())
        assertEquals("2026-11-11",h.path("periodStart").textValue());assertEquals("2026-11-12",h.path("periodEndExclusive").textValue())
        assertEquals(emptyList(),ids(h));assertEquals(root["revision"],h.path("revision").longValue());assertEquals(root,f.root())
        assertEquals(receipts,f.resources.firestore.collection("users/${f.a.value}/operations").get().get().size())
    }
    @Test fun dayUsesActivityDateInsteadOfCreationDateAndExcludesTombstones() = scenario { f ->
        f.seed("previous","2026-11-10");f.seed("today","2026-11-11");f.seed("removed","2026-11-11",deleted=true)
        assertEquals(listOf("today"),ids(client.history(f)));assertEquals(listOf("previous"),ids(client.history(f,date="2026-11-10")))
    }
    @Test fun weekRunsMondayThroughSundayAcrossMonthBoundary() = scenario { f ->
        f.now=Instant.parse("2026-12-07T15:00:00Z")
        for(date in listOf("2026-11-29","2026-11-30","2026-12-06","2026-12-07"))f.seed(date,date)
        val h=client.history(f,"week","2026-12-06")
        assertEquals("2026-11-30",h.path("periodStart").textValue());assertEquals("2026-12-07",h.path("periodEndExclusive").textValue())
        assertEquals(listOf("2026-11-30","2026-12-06"),ids(h))
    }
    @Test fun monthUsesCalendarBoundsAcrossYearAndLeapFebruary() = scenario { f ->
        f.now=Instant.parse("2028-03-01T15:00:00Z")
        for(date in listOf("2027-12-31","2028-01-01","2028-02-01","2028-02-29","2028-03-01"))f.seed(date,date)
        val leap=client.history(f,"month","2028-02-15")
        assertEquals("2028-02-01",leap.path("periodStart").textValue());assertEquals("2028-03-01",leap.path("periodEndExclusive").textValue())
        assertEquals(listOf("2028-02-01","2028-02-29"),ids(leap))
        val december=client.history(f,"month","2027-12-01");assertEquals("2028-01-01",december.path("periodEndExclusive").textValue())
        assertEquals(listOf("2027-12-31"),ids(december))
    }
    @Test fun stableOrderIsActivityDateThenOriginalCreationThenOrdinalId() = scenario { f ->
        f.seed("z","2026-11-11",f.now.minusSeconds(2));f.seed("a","2026-11-11",f.now.minusSeconds(2))
        f.seed("later","2026-11-11",f.now.minusSeconds(1));f.seed("previous-day","2026-11-10",f.now)
        val root=f.root();assertEquals(listOf("previous-day","a","z","later"),ids(client.history(f,"week")));assertEquals(root,f.root())
    }
    @Test fun historyKeepsOldCycleRecordsWithoutAwardingThemAgain() = scenario { f ->
        val id=client.create(f);val originalCycle=f.cycle();f.newCycle();val before=f.root();val projection=f.projection()
        val item=client.history(f).path("items").single()
        assertEquals(id,item.path("recordId").textValue());assertEquals(originalCycle,item.path("cycleId").textValue())
        assertEquals(before,f.root());assertEquals(projection,f.projection())
    }
    @Test fun historyReturnsAuthoritativeDefinitionVersionDurationAndCreationWithoutPrivateReceiptFields() = scenario { f ->
        val id=client.create(f);val item=client.history(f).path("items").single()
        assertEquals(id,item.path("recordId").textValue());assertEquals("strength",item.path("activityId").textValue())
        assertEquals(30,item.path("durationMinutes").intValue());assertEquals(1,item.path("version").intValue())
        assertEquals(f.now.toString(),item.path("createdAt").textValue());assertEquals("STR",item.path("activityDefinition").path("primary").textValue())
        assertFalse(item.has("uid"));assertFalse(item.has("requestHash"));assertFalse(item.has("operationId"))
    }
    @Test fun moreThanOneThousandZeroXpRecordsAreReturnedAsOneCompletePeriod() = scenario { f ->
        val expected=f.seedPage(1005);val h=client.history(f)
        assertEquals(expected,ids(h));assertEquals(1005,h.path("items").size());assertEquals(1005,f.records().size)
    }
    @Test fun concurrentWriteCannotProduceMixedRevisionOrPartialPeriod() = scenario { f ->
        f.seedPage(1005);val before=(f.root()["revision"] as Long)
        val pair=coroutineScope { val read=async { client.get("/v1/history?period=day&date=2026-11-11") { bearerAuth(f.tokenA) } };val write=async { client.create(f,minutes=1) };read.await() to write.await() }
        assertEquals(HttpStatusCode.OK,pair.first.status);val h=body(pair.first);val revision=h.path("revision").longValue()
        assertTrue(revision in before..before+1);assertEquals(if(revision==before)1005 else 1006,h.path("items").size());assertTrue(h.path("complete").booleanValue())
        assertEquals(1006,client.history(f).path("items").size())
    }
    @Test fun otherOwnerHistoryIsEmptyAndCannotBeSelectedThroughQueryParameters() = scenario { f ->
        client.create(f);assertTrue(ids(client.history(f,token=f.tokenB)).isEmpty())
        assertEquals(HttpStatusCode.BadRequest,client.get("/v1/history?period=day&date=2026-11-11&uid=${f.a.value}") { bearerAuth(f.tokenB) }.status)
    }
    @Test fun corruptStoredRecordFailsWholePeriodInsteadOfReturningPartialSuccess() = scenario { f ->
        client.create(f);f.resources.documents.write(f.a,listOf("records","synthetic-corrupt"),mapOf("activityDate" to "2026-11-11","cycleId" to f.cycle()))
        val before=f.root();val response=client.get("/v1/history?period=day&date=2026-11-11") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.ServiceUnavailable,response.status);val h=body(response)
        assertFalse(h.has("items"));assertFalse(h.has("complete"));assertEquals(before,f.root())
    }
    @Test fun historyRequiresAuthenticationAndStrictPeriodAndDateParameters() = scenario { f ->
        assertEquals(HttpStatusCode.Unauthorized,client.get("/v1/history?period=day&date=2026-11-11").status)
        for(query in listOf("period=year&date=2026-11-11","period=day&date=2026-02-30","period=week","date=2026-11-11","period=day&period=week&date=2026-11-11"))
            assertEquals(HttpStatusCode.BadRequest,client.get("/v1/history?$query") { bearerAuth(f.tokenA) }.status)
    }
    @Test fun durationEditPreservesIdentityCreationAndAtomicallyPublishesReplayAndReceipt() = scenario { f ->
        val id=client.create(f);val original=f.stored(id);val before=(f.root()["revision"] as Long);val op=OperationId.create()
        val result=confirmed(client.command(f,"EDIT_RECORD",editData(id,activity="aerobic"),op)).path("result")
        val updated=f.stored(id);assertEquals(id,result.path("recordId").textValue());assertEquals(original["createdAt"],updated["createdAt"])
        assertEquals(2L,updated["version"]);assertEquals("aerobic",updated["activityId"]);assertEquals(60L,updated["durationMinutes"])
        assertEquals(before+1,f.root()["revision"]);assertEquals(f.root()["revision"],f.projection()!!["revision"])
        assertEquals(0,result.path("progression").path("attributes").path("STR").path("xp").intValue())
        assertEquals(12,result.path("progression").path("attributes").path("DEX").path("xp").intValue())
        assertEquals(ReceiptOutcome.CONFIRMED,f.receipt(op)?.outcome)
    }
    @Test fun deletionPersistsTombstoneAndExcludesRecordFromHistoryAndReplay() = scenario { f ->
        val id=client.create(f);val original=f.stored(id);val op=OperationId.create()
        val result=confirmed(client.command(f,"DELETE_RECORD",deleteData(id),op)).path("result")
        assertEquals(f.now.toString(),f.stored(id)["deletedAt"]);assertEquals(original["createdAt"],f.stored(id)["createdAt"])
        assertEquals(2L,f.stored(id)["version"]);assertEquals(0,result.path("progression").path("attributes").path("STR").path("xp").intValue())
        assertTrue(ids(client.history(f)).isEmpty());assertEquals(1,f.records().size);assertEquals(ReceiptOutcome.CONFIRMED,f.receipt(op)?.outcome)
    }
    @Test fun bothCorrectionsAccept299SecondsAndRejectExactly300OnOriginalCreationClock() = scenario { f ->
        val edit=client.create(f);val delete=client.create(f);val born=f.now
        f.now=born.plusSeconds(299);confirmed(client.command(f,"EDIT_RECORD",editData(edit)));confirmed(client.command(f,"DELETE_RECORD",deleteData(delete)))
        f.now=born.plusSeconds(300);rejected(client.command(f,"EDIT_RECORD",editData(edit,version=2)),"EDIT_WINDOW_EXPIRED")
        val untouched=client.create(f);f.now=f.now.plusSeconds(300)
        rejected(client.command(f,"DELETE_RECORD",deleteData(untouched)),"EDIT_WINDOW_EXPIRED");assertNull(f.stored(untouched)["deletedAt"])
    }
    @Test fun staleExpectedVersionCannotOverwriteNewerStateAndFreshHistoryShowsWinner() = scenario { f ->
        val id=client.create(f);confirmed(client.command(f,"EDIT_RECORD",editData(id,minutes=35)))
        val before=f.stored(id);val p=f.projection()
        rejected(client.command(f,"EDIT_RECORD",editData(id,minutes=40)),"RECORD_STALE")
        rejected(client.command(f,"DELETE_RECORD",deleteData(id)),"RECORD_STALE")
        assertEquals(before,f.stored(id));assertEquals(p,f.projection());val item=client.history(f).path("items").single()
        assertEquals(2,item.path("version").intValue());assertEquals(35,item.path("durationMinutes").intValue())
    }
    @Test fun tombstoneRejectsEditOrSecondDeleteWithoutResurrectingRecord() = scenario { f ->
        val id=client.create(f);confirmed(client.command(f,"DELETE_RECORD",deleteData(id)));val before=f.stored(id)
        rejected(client.command(f,"EDIT_RECORD",editData(id,version=2)),"RECORD_DELETED")
        rejected(client.command(f,"DELETE_RECORD",deleteData(id,version=2)),"RECORD_DELETED")
        assertEquals(before,f.stored(id));assertTrue(ids(client.history(f)).isEmpty())
    }
    @Test fun recentOldCycleCannotBeEditedOrDeletedAfterCurrentCycleChanges() = scenario { f ->
        val id=client.create(f);val old=f.cycle();val current=f.newCycle();val original=f.stored(id)
        rejected(client.command(f,"EDIT_RECORD",editData(id),cycle=old),"CYCLE_CLOSED")
        rejected(client.command(f,"DELETE_RECORD",deleteData(id),cycle=old),"CYCLE_CLOSED")
        rejected(client.command(f,"EDIT_RECORD",editData(id),cycle=current),"CYCLE_CLOSED")
        rejected(client.command(f,"DELETE_RECORD",deleteData(id),cycle=current),"CYCLE_CLOSED")
        assertEquals(original,f.stored(id))
    }
    @Test fun otherOwnerCannotEditOrDeleteKnownRecordId() = scenario { f ->
        val id=client.create(f);val original=f.stored(id)
        rejected(client.command(f,"EDIT_RECORD",editData(id),token=f.tokenB,cycle=f.cycle(f.b)),"RECORD_DELETED")
        rejected(client.command(f,"DELETE_RECORD",deleteData(id),token=f.tokenB,cycle=f.cycle(f.b)),"RECORD_DELETED")
        assertEquals(original,f.stored(id));assertTrue(f.records(f.b).isEmpty())
    }
    @Test fun mealDateOnlyEditRecalculatesConstitutionWithoutAskingForDuration() = scenario { f ->
        val id=client.create(f,"meal");val data=mapOf<String,Any?>("recordId" to id,"expectedVersion" to 1,"activityDate" to "2026-11-10")
        confirmed(client.command(f,"EDIT_RECORD",data));assertEquals("2026-11-10",f.stored(id)["activityDate"])
        assertFalse(f.stored(id).containsKey("durationMinutes"));assertEquals(2L,f.stored(id)["version"])
    }
    @Test fun modalityConversionsAndMealDurationAreFunctionalRejections() = scenario { f ->
        val duration=client.create(f);val meal=client.create(f,"meal")
        rejected(client.command(f,"EDIT_RECORD",editData(duration,activity="meal")),"INVALID_INPUT")
        rejected(client.command(f,"EDIT_RECORD",editData(meal)),"INVALID_INPUT")
        rejected(client.command(f,"EDIT_RECORD",editData(meal,activity="meal",minutes=0)),"INVALID_INPUT")
        assertEquals(1L,f.stored(duration)["version"]);assertEquals(1L,f.stored(meal)["version"])
    }
    @Test fun durationDestinationBudgetAndDateAreValidatedWithoutChangingOriginal() = scenario { f ->
        val id=client.create(f,minutes=180);client.create(f,minutes=1260);val before=f.stored(id)
        rejected(client.command(f,"EDIT_RECORD",editData(id,minutes=181)),"DAILY_DURATION_EXCEEDED")
        rejected(client.command(f,"EDIT_RECORD",editData(id,date="2026-11-08")),"DATE_NOT_ALLOWED")
        assertEquals(before,f.stored(id));confirmed(client.command(f,"EDIT_RECORD",editData(id,minutes=120)))
        client.create(f,minutes=60);assertEquals(3,f.records().size)
    }
    @Test fun sameUuidReturnsOriginalEditReceiptEvenAfterVersionAndWindowChange() = scenario { f ->
        val id=client.create(f);val op=OperationId.create();val data=editData(id)
        val original=confirmed(client.command(f,"EDIT_RECORD",data,op));f.now=f.now.plusSeconds(3600);val root=f.root();val record=f.stored(id)
        assertEquals(original,confirmed(client.command(f,"EDIT_RECORD",data,op)));assertEquals(root,f.root());assertEquals(record,f.stored(id))
    }
    @Test fun sameUuidReturnsOriginalDeleteReceiptAfterTombstoneAndCycleChange() = scenario { f ->
        val id=client.create(f);val cycle=f.cycle();val op=OperationId.create();val data=deleteData(id)
        val original=confirmed(client.command(f,"DELETE_RECORD",data,op));f.newCycle();f.now=f.now.plusSeconds(3600);val root=f.root()
        assertEquals(original,confirmed(client.command(f,"DELETE_RECORD",data,op,cycle=cycle)));assertEquals(root,f.root());assertEquals(2L,f.stored(id)["version"])
    }
    @Test fun uuidCollisionCannotAlterAlreadyConfirmedCorrection() = scenario { f ->
        val id=client.create(f);val op=OperationId.create();val data=editData(id)
        confirmed(client.command(f,"EDIT_RECORD",data,op));val before=f.stored(id)
        val collision=client.command(f,"EDIT_RECORD",editData(id,minutes=90),op)
        assertEquals(HttpStatusCode.Conflict,collision.status);assertEquals("OPERATION_ID_REUSED",body(collision).path("error").path("code").textValue())
        assertEquals(before,f.stored(id))
    }
    @Test fun functionalRejectionIsImmutableAndCorrectionRequiresNewUuid() = scenario { f ->
        val id=client.create(f);val op=OperationId.create();val invalid=editData(id,date="2026-11-08")
        val original=rejected(client.command(f,"EDIT_RECORD",invalid,op),"DATE_NOT_ALLOWED")
        assertEquals(original,rejected(client.command(f,"EDIT_RECORD",invalid,op),"DATE_NOT_ALLOWED"))
        assertEquals(HttpStatusCode.Conflict,client.command(f,"EDIT_RECORD",editData(id),op).status)
        confirmed(client.command(f,"EDIT_RECORD",editData(id)));assertEquals(2L,f.stored(id)["version"])
    }
    @Test fun lostEditReplyRecoversReceiptWithoutSecondVersionIncrement() = scenario { f ->
        val id=client.create(f);val op=OperationId.create();val data=editData(id);f.loseAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable,client.command(f,"EDIT_RECORD",data,op).status);assertFalse(f.loseAfter.get())
        val before=f.root();val record=f.stored(id);val lookup=client.get("/v1/operations/${op.value}") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.OK,lookup.status);assertEquals(body(lookup),confirmed(client.command(f,"EDIT_RECORD",data,op)))
        assertEquals(before,f.root());assertEquals(record,f.stored(id));assertEquals(2L,record["version"])
    }
    @Test fun lostDeleteReplyRecoversOriginalTombstoneWithoutAnotherMutation() = scenario { f ->
        val id=client.create(f);val op=OperationId.create();val data=deleteData(id);f.loseAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable,client.command(f,"DELETE_RECORD",data,op).status);assertFalse(f.loseAfter.get())
        val before=f.root();val record=f.stored(id)
        confirmed(client.command(f,"DELETE_RECORD",data,op));assertEquals(before,f.root());assertEquals(record,f.stored(id));assertNotNull(record["deletedAt"])
    }
    @Test fun failureBeforeCorrectionDoesNotProduceDefinitiveReceiptOrChangeProjection() = scenario { f ->
        val id=client.create(f);val op=OperationId.create();val before=f.stored(id);val p=f.projection();f.failBefore.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable,client.command(f,"EDIT_RECORD",editData(id),op).status)
        assertNull(f.receipt(op));assertEquals(before,f.stored(id));assertEquals(p,f.projection())
        confirmed(client.command(f,"EDIT_RECORD",editData(id),op));assertEquals(2L,f.stored(id)["version"])
    }
    @Test fun mutationSchemaRejectsOwnerXpCreationAndMalformedVersionBeforeWrites() = scenario { f ->
        val id=client.create(f);val original=f.stored(id);val root=f.root()
        for(data in listOf(editData(id)+( "uid" to f.b.value),editData(id)+( "xp" to 999),editData(id)+( "createdAt" to f.now.toString()),
            editData(id)+( "expectedVersion" to "1"),editData(id)+( "expectedVersion" to 1.5),deleteData(id)+( "activityId" to "meal"))) {
            val op=OperationId.create();val type=if(data.containsKey("activityDate"))"EDIT_RECORD" else "DELETE_RECORD"
            assertEquals(HttpStatusCode.BadRequest,client.command(f,type,data,op).status);assertNull(f.receipt(op))
        }
        assertEquals(original,f.stored(id));assertEquals(root,f.root())
    }
}
