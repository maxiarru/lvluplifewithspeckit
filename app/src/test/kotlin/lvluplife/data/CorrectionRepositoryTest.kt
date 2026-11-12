package lvluplife.data

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import lvluplife.application.*
import org.junit.Assert.*
import org.junit.Test

class CorrectionRepositoryTest {
    private class Store:PendingStore {
        val rows=linkedMapOf<String,PendingOperation>();val drafts=mutableMapOf<String,Draft>()
        override fun prepare(operation:PendingOperation) { rows.putIfAbsent(operation.operationId,operation) }
        override fun pendingFor(uid:String)=rows.values.filter { it.uid==uid }
        override fun updateState(uid:String,operationId:String,state:PendingState,safeError:String?) {
            check(rows.getValue(operationId).uid==uid);rows[operationId]=rows.getValue(operationId).copy(state=state,safeError=safeError)
        }
        override fun resolveAbsent(uid:String,operationId:String)=updateState(uid,operationId,PendingState.UNKNOWN,null)
        override fun removeDefinitive(uid:String,operationId:String) { rows.remove(operationId) }
        override fun saveDraft(draft:Draft) { drafts[draft.uid]=draft }
        override fun draftFor(uid:String)=drafts[uid]
        override fun close()=Unit
    }
    private class Fixture {
        val store=Store();val json=ObjectMapper();var token:SessionToken?=SessionToken("owner-a","synthetic-token")
        var online=true;var unknown=false;var lookupConfirmed=false
        val requests=mutableListOf<TransportRequest>();val sessions=SessionTokenProvider { token }
        val api=ApiClient(ApiEndpoint.from("https://api.example.test",false),sessions,HttpTransport { request ->
            requests += request
            if(request.method=="POST") {
                val body=json.readTree(request.body);val id=body.path("operationId").textValue()
                assertEquals(PendingState.SENDING,store.rows.getValue(id).state);assertEquals(body,json.readTree(store.rows.getValue(id).envelope))
                if(unknown)TransportResponse(503,"{}") else TransportResponse(200,receipt(id))
            } else {
                val id=request.url.pathSegments.last()
                TransportResponse(200,if(lookupConfirmed)receipt(id) else """{"operationId":"$id","outcome":"UNKNOWN"}""")
            }
        })
        val coordinator=OperationCoordinator(store,api,sessions,{ online })
        val records=object:RecordFlowRecords {
            override val online get()=true
            override suspend fun catalog(uid:String)=listOf(RecordOption("strength","Fuerza",true),RecordOption("aerobic","Aeróbico",true),RecordOption("meal","Comida",false))
            override suspend fun draft(uid:String):RecordDraft?=null
            override suspend fun saveDraft(uid:String,draft:RecordDraft)=Unit
            override suspend fun pending(uid:String)=emptyList<OperationSnapshot>()
            override suspend fun submit(uid:String,intentId:String,draft:RecordDraft):OperationSnapshot=error("Not a record creation")
            override suspend fun retry(ref:OperationRef):OperationSnapshot=error("Not a record retry")
        }
        val repository=CorrectionRepository(api,coordinator,store,sessions,{ online },records)
        val draft=CorrectionDraft("record-original","cycle-observed",3,"strength","2026-11-11","0","45")
        val item=HistoryItem("record-original","cycle-observed","strength","Fuerza","2026-11-11",30,java.time.Instant.parse("2026-11-11T14:59:00Z"),3,true)
        fun receipt(id:String)="""{"operationId":"$id","outcome":"CONFIRMED","validatedAt":"2026-11-11T15:00:00Z","result":{"recordId":"record-original","version":4}}"""
    }
    private suspend fun blocked(code:ApiFailureCode,block:suspend ()->Unit) {
        try { block();fail("Expected rejection") } catch(failure:OperationBlocked) { assertEquals(code,failure.code) }
    }
    @Test fun editPersistsObservedCycleAndVersionBeforeTransportWithoutRefreshingThem()=runBlocking {
        val f=Fixture();f.repository.edit("owner-a","intent",f.draft)
        val post=f.json.readTree(f.requests.single().body)
        assertEquals("EDIT_RECORD",post.path("type").textValue());assertEquals("cycle-observed",post.path("cycleId").textValue())
        assertEquals(3,post.path("payload").path("expectedVersion").intValue());assertEquals("record-original",post.path("payload").path("recordId").textValue())
        assertFalse(post.path("payload").has("createdAt"));assertFalse(post.path("payload").has("xp"));assertTrue(f.requests.all { it.method=="POST" })
    }
    @Test fun mealEditOnlySendsDateWhileDeleteOnlySendsIdentityAndExpectedVersion()=runBlocking {
        val f=Fixture();f.repository.edit("owner-a","meal",f.draft.copy(activityId="meal",hours=null,minutes=null))
        val meal=f.json.readTree(f.requests.last().body).path("payload")
        assertEquals(setOf("recordId","expectedVersion","activityDate"),meal.fieldNames().asSequence().toSet())
        f.repository.delete("owner-a","delete",f.item)
        val delete=f.json.readTree(f.requests.last().body)
        assertEquals("DELETE_RECORD",delete.path("type").textValue());assertEquals(setOf("recordId","expectedVersion"),delete.path("payload").fieldNames().asSequence().toSet())
        assertEquals("cycle-observed",delete.path("cycleId").textValue())
    }
    @Test fun unknownEditAndDeleteLookupBeforeResendingExactOriginalBytes()=runBlocking {
        for(deleting in listOf(false,true)) {
            val f=Fixture();f.unknown=true
            val result=if(deleting)f.repository.delete("owner-a","intent",f.item) else f.repository.edit("owner-a","intent",f.draft)
            val envelope=f.requests.single().body;f.unknown=false;f.repository.retry(result.ref)
            assertEquals(2,f.requests.count { it.method=="POST" });assertTrue(f.requests[1].url.pathSegments.contains("operations"))
            assertEquals(envelope,f.requests.last().body)
        }
    }
    @Test fun invalidInputsAndOfflineNeverAllocateOrSendCorrections()=runBlocking {
        val f=Fixture()
        for(draft in listOf(f.draft.copy(minutes="1.5"),f.draft.copy(minutes="60"),f.draft.copy(hours="-1"),f.draft.copy(hours="24",minutes="1"),
            f.draft.copy(expectedVersion=0),f.draft.copy(date="bad-date"),f.draft.copy(activityId="meal")))
            blocked(ApiFailureCode.INVALID_INPUT) { f.repository.edit("owner-a","invalid",draft) }
        f.online=false;blocked(ApiFailureCode.NETWORK_ERROR) { f.repository.delete("owner-a","offline",f.item) }
        assertTrue(f.store.rows.isEmpty());assertTrue(f.requests.isEmpty())
    }
    @Test fun draftAndClearPreserveOtherNamespacesAndOwner()=runBlocking {
        val f=Fixture();f.store.saveDraft(Draft("owner-a","""{"profileUsername":"Player","recordDraft":{"minutes":"25"},"customActivity":{"name":"Propia"}}"""))
        f.store.saveDraft(Draft("owner-b","untouched"));f.repository.saveDraft("owner-a",f.draft)
        assertEquals(f.draft,f.repository.draft("owner-a"));f.repository.clearDraft("owner-a")
        val root=f.json.readTree(f.store.draftFor("owner-a")!!.content)
        assertFalse(root.has("correctionDraft"));assertEquals("Player",root.path("profileUsername").textValue())
        assertEquals("25",root.path("recordDraft").path("minutes").textValue());assertEquals("Propia",root.path("customActivity").path("name").textValue())
        assertEquals("untouched",f.store.draftFor("owner-b")!!.content);assertTrue(f.requests.isEmpty())
    }
    @Test fun coldRecoveryRetainsDeleteTypeAndDoesNotTransmitAnUnrelatedCreation()=runBlocking {
        val f=Fixture();f.unknown=true;val original=f.repository.delete("owner-a","delete",f.item)
        val creation=f.coordinator.prepare("owner-a","create",ApiCommandType.CREATE_RECORD,mapOf("activityId" to "strength","activityDate" to "2026-11-11","hours" to 0,"minutes" to 30),"cycle-observed")
        val cold=CorrectionRepository(f.api,OperationCoordinator(f.store,f.api,f.sessions,{ f.online }),f.store,f.sessions,{ f.online },f.records)
        val pending=cold.pending("owner-a")
        assertEquals(listOf(original.ref),pending.map { it.ref });assertTrue(cold.wasDeletion(original.ref))
        assertEquals(PendingState.PREPARED,f.store.rows.getValue(creation.operationId).state);assertEquals(1,f.requests.count { it.method=="POST" })
    }
    @Test fun startupDefinitiveDeleteIsDeliveredOnceWithOriginalKind()=runBlocking {
        val f=Fixture();f.unknown=true;val original=f.repository.delete("owner-a","delete",f.item)
        val ids=f.repository.recoveryIds("owner-a");f.lookupConfirmed=true
        val recovered=f.coordinator.recover().filter { it.ref.operationId in ids };f.repository.rememberRecovered(recovered)
        assertTrue(f.store.rows.isEmpty());val before=f.requests.size
        assertEquals(PendingState.CONFIRMED,f.repository.pending("owner-a").single().state);assertTrue(f.repository.wasDeletion(original.ref))
        assertTrue(f.repository.pending("owner-a").isEmpty());assertEquals(before,f.requests.size)
    }
    @Test fun anotherOwnerCannotRetryOrReplaceOriginalEnvelope()=runBlocking {
        val f=Fixture();f.unknown=true;val result=f.repository.edit("owner-a","edit",f.draft)
        val original=f.store.rows.getValue(result.ref.operationId);val before=f.requests.size
        f.token=SessionToken("owner-b","synthetic-other-token")
        blocked(ApiFailureCode.AUTH_REQUIRED) { f.repository.retry(result.ref) }
        blocked(ApiFailureCode.AUTH_REQUIRED) { f.repository.edit("owner-a","edit",f.draft.copy(minutes="50")) }
        assertEquals(original,f.store.rows.getValue(result.ref.operationId));assertEquals(before,f.requests.size)
    }
    @Test fun durationOptionsNeverOfferMealConversion()=runBlocking {
        val f=Fixture();assertEquals(listOf("strength","aerobic"),f.repository.options("owner-a").map { it.id });assertTrue(f.requests.isEmpty())
    }
}
