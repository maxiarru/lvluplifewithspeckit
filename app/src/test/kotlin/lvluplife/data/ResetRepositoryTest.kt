package lvluplife.data

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import lvluplife.application.*
import org.junit.Assert.*
import org.junit.Test

class ResetRepositoryTest {
    private class Store: PendingStore {
        val rows=linkedMapOf<String,PendingOperation>()
        val drafts=mutableMapOf<String,Draft>()
        var failPreparation=false
        var prepared: (() -> Unit)?=null
        val attempted=mutableListOf<PendingOperation>()
        override fun prepare(operation:PendingOperation) {
            attempted+=operation
            if(failPreparation) error("Synthetic disk failure")
            rows.putIfAbsent(operation.operationId,operation);prepared?.invoke()
        }
        override fun pendingFor(uid:String)=rows.values.filter { it.uid==uid }
        override fun updateState(uid:String,operationId:String,state:PendingState,safeError:String?) {
            check(rows.getValue(operationId).uid==uid)
            rows[operationId]=rows.getValue(operationId).copy(state=state,safeError=safeError)
        }
        override fun resolveAbsent(uid:String,operationId:String)=updateState(uid,operationId,PendingState.UNKNOWN,null)
        override fun removeDefinitive(uid:String,operationId:String) { check(rows.getValue(operationId).uid==uid);rows.remove(operationId) }
        override fun saveDraft(draft:Draft) { drafts[draft.uid]=draft }
        override fun draftFor(uid:String)=drafts[uid]
        override fun close()=Unit
    }
    private class Fixture {
        val store=Store();val json=ObjectMapper()
        var token:SessionToken?=SessionToken("owner-a","synthetic-token")
        var online=true;var unknown=false;var lookupConfirmed=false
        val requests=mutableListOf<TransportRequest>()
        val sessions=SessionTokenProvider { token }
        val api=ApiClient(ApiEndpoint.from("https://api.example.test",false),sessions,HttpTransport { request ->
            requests+=request
            if(request.method=="POST") {
                val body=json.readTree(request.body);val id=body.path("operationId").textValue()
                assertEquals(PendingState.SENDING,store.rows.getValue(id).state)
                assertEquals(request.body,store.rows.getValue(id).envelope)
                if(unknown) TransportResponse(503,"{}") else TransportResponse(200,receipt(id))
            } else {
                val id=request.url.pathSegments.last()
                TransportResponse(200,if(lookupConfirmed) receipt(id) else """{"operationId":"$id","outcome":"UNKNOWN"}""")
            }
        })
        val coordinator=OperationCoordinator(store,api,sessions,{ online })
        val repository=ResetRepository(api,coordinator,store,sessions,{ online })
        fun receipt(id:String)="""{"operationId":"$id","outcome":"CONFIRMED","validatedAt":"2026-11-12T15:00:00Z","result":{"cycleId":"new-cycle","closedCycleId":"observed-cycle","revision":11}}"""
        suspend fun submit()=repository.submit("owner-a","intent","observed-cycle",true)
    }
    private suspend fun blocked(code:ApiFailureCode,block:suspend ()->Unit) {
        try { block();fail("Expected rejection") } catch(failure:OperationBlocked) { assertEquals(code,failure.code) }
    }
    @Test fun persistsExplicitConfirmationAndObservedCycleBeforeTransportWithoutMetadataReads()=runBlocking {
        val f=Fixture();val result=f.submit();assertEquals(PendingState.CONFIRMED,result.state)
        val request=f.requests.single();assertEquals("POST",request.method)
        val body=f.json.readTree(request.body)
        assertEquals("RESET_PROGRESSION",body.path("type").textValue())
        assertEquals("observed-cycle",body.path("cycleId").textValue())
        assertEquals(setOf("confirmed"),body.path("payload").fieldNames().asSequence().toSet())
        assertTrue(body.path("payload").path("confirmed").booleanValue())
    }
    @Test fun preparationFailureRetainsSameUuidEnvelopeAndIntent()=runBlocking {
        val f=Fixture();f.store.failPreparation=true
        blocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) { f.submit() }
        assertTrue(f.requests.isEmpty());assertTrue(f.store.rows.isEmpty())
        val original=f.store.attempted.single();f.store.failPreparation=false;f.submit()
        assertEquals(listOf(original,original),f.store.attempted)
        assertEquals(original.envelope,f.requests.single().body)
    }
    @Test fun unknownLooksUpBeforeResendingExactOriginalEnvelope()=runBlocking {
        val f=Fixture();f.unknown=true;val result=f.submit();val envelope=f.requests.single().body
        f.unknown=false;f.repository.retry(result.ref)
        assertEquals(listOf("POST","GET","POST"),f.requests.map { it.method })
        assertEquals(envelope,f.requests.last().body)
    }
    @Test fun scopedRecoveryLeavesOtherCommandsAndOtherOwnersUntouched()=runBlocking {
        val f=Fixture();f.unknown=true;val original=f.submit()
        val create=f.coordinator.prepare("owner-a","create",ApiCommandType.CREATE_RECORD,mapOf("activityId" to "strength","activityDate" to "2026-11-12","hours" to 0,"minutes" to 30),"observed-cycle")
        val other=f.store.rows.getValue(original.ref.operationId).copy(uid="owner-b",operationId="foreign-reset")
        f.store.prepare(other)
        val cold=ResetRepository(f.api,OperationCoordinator(f.store,f.api,f.sessions,{ f.online }),f.store,f.sessions,{ f.online })
        assertEquals(listOf(original.ref),cold.pending("owner-a").map { it.ref })
        assertEquals(PendingState.PREPARED,f.store.rows.getValue(create.operationId).state)
        assertEquals(other,f.store.rows.getValue(other.operationId));assertEquals(listOf("POST","GET"),f.requests.map { it.method })
        blocked(ApiFailureCode.INVALID_INPUT) { cold.retry(create) }
    }
    @Test fun startupReceiptIsDeliveredOnceEvenAfterCoordinatorRemovesJournal()=runBlocking {
        val f=Fixture();f.unknown=true;val original=f.submit()
        val ids=f.repository.recoveryIds("owner-a");f.lookupConfirmed=true
        f.repository.rememberRecovered(f.coordinator.recover().filter { it.ref.operationId in ids })
        assertTrue(f.store.rows.isEmpty());val before=f.requests.size
        assertEquals(PendingState.CONFIRMED,f.repository.pending("owner-a").single().state)
        assertTrue(f.repository.pending("owner-a").isEmpty())
        assertEquals(PendingState.CONFIRMED,f.repository.retry(original.ref).state);assertEquals(before,f.requests.size)
    }
    @Test fun offlineUnconfirmedAndInvalidCycleNeverAllocateOrSend()=runBlocking {
        val f=Fixture()
        blocked(ApiFailureCode.INVALID_INPUT) { f.repository.submit("owner-a","intent","cycle",false) }
        for(cycle in listOf(""," ",".","..","a/b","a\n", "é".repeat(751)))
            blocked(ApiFailureCode.INVALID_INPUT) { f.repository.submit("owner-a","intent",cycle,true) }
        f.online=false;blocked(ApiFailureCode.NETWORK_ERROR) { f.submit() }
        assertTrue(f.store.attempted.isEmpty());assertTrue(f.requests.isEmpty())
    }
    @Test fun ownerChangeCannotRetryOrReassignOriginalEnvelopeAndDraftsStayIntact()=runBlocking {
        val f=Fixture();f.store.saveDraft(Draft("owner-a","all namespaces"));f.store.saveDraft(Draft("owner-b","foreign"))
        f.unknown=true;val original=f.submit();val row=f.store.rows.getValue(original.ref.operationId);val count=f.requests.size
        f.token=SessionToken("owner-b","other-token")
        blocked(ApiFailureCode.AUTH_REQUIRED) { f.repository.retry(original.ref) }
        blocked(ApiFailureCode.AUTH_REQUIRED) { f.submit() }
        assertEquals(row,f.store.rows.getValue(row.operationId));assertEquals(count,f.requests.size)
        assertEquals("all namespaces",f.store.draftFor("owner-a")!!.content);assertEquals("foreign",f.store.draftFor("owner-b")!!.content)
    }
    @Test fun sessionChangeAfterPreparationRetainsOwnerAndNeverSendsForNewSession()=runBlocking {
        val f=Fixture();f.store.prepared={ f.token=SessionToken("owner-b","other-token") }
        blocked(ApiFailureCode.AUTH_REQUIRED) { f.submit() }
        assertEquals("owner-a",f.store.rows.values.single().uid)
        assertEquals(PendingState.PREPARED,f.store.rows.values.single().state);assertTrue(f.requests.isEmpty())
    }
}
