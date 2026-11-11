package lvluplife.data

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import lvluplife.application.*
import org.junit.Assert.*
import org.junit.Test

class RecordRepositoryTest {
    private class Store : PendingStore {
        var failDraft = false
        val rows = linkedMapOf<String, PendingOperation>(); val drafts = mutableMapOf<String, Draft>()
        override fun prepare(operation: PendingOperation) { rows.putIfAbsent(operation.operationId, operation) }
        override fun pendingFor(uid: String) = rows.values.filter { it.uid == uid }
        override fun updateState(uid: String, operationId: String, state: PendingState, safeError: String?) {
            rows[operationId] = rows.getValue(operationId).copy(state = state, safeError = safeError)
        }
        override fun resolveAbsent(uid: String, operationId: String) = updateState(uid, operationId, PendingState.UNKNOWN, null)
        override fun removeDefinitive(uid: String, operationId: String) { rows.remove(operationId) }
        override fun saveDraft(draft: Draft) { check(!failDraft); drafts[draft.uid] = draft }
        override fun draftFor(uid: String) = drafts[uid]
        override fun close() = Unit
    }
    private class Fixture {
        val json = ObjectMapper(); val store = Store(); var token: SessionToken? = SessionToken("owner-a", "synthetic-token")
        var online = true; var cycle = "cycle-original"; var unknown = false
        val requests = mutableListOf<TransportRequest>()
        val sessions = SessionTokenProvider { token }
        val api = ApiClient(ApiEndpoint.from("https://api.example.test", false), sessions, HttpTransport { request ->
            requests += request
            when {
                request.method == "POST" -> {
                    val body = json.readTree(request.body); val id = body.path("operationId").textValue()
                    assertEquals(PendingState.SENDING, store.rows.getValue(id).state)
                    assertEquals(body, json.readTree(store.rows.getValue(id).envelope))
                    if (unknown) TransportResponse(503, "{}") else TransportResponse(200,
                        """{"operationId":"$id","outcome":"CONFIRMED","validatedAt":"2026-11-11T15:00:00Z","result":{"recordId":"synthetic-record"}}""")
                }
                request.url.pathSegments.contains("operations") -> {
                    val id = request.url.pathSegments.last()
                    TransportResponse(200, """{"operationId":"$id","outcome":"UNKNOWN"}""")
                }
                request.url.pathSegments.contains("catalog") -> TransportResponse(200,
                    """{"activities":[{"id":"strength","name":"Fuerza","mode":"DURATION"},{"id":"meal","name":"Comida","mode":"OCCURRENCE"}]}""")
                else -> TransportResponse(200, """{"cycleId":"$cycle","businessDate":"2026-11-11"}""")
            }
        })
        val coordinator = OperationCoordinator(store, api, sessions, { online })
        val repository = RecordRepository(api, coordinator, store, sessions, { online })
        val draft = RecordDraft("strength", "2026-11-11", "0", "30")
    }
    @Test fun durableEnvelopePrecedesTransportAndKeepsObservedCycle() = runBlocking {
        val f = Fixture(); f.repository.catalog("owner-a"); f.cycle = "cycle-new"
        f.repository.submit("owner-a", "intent", f.draft)
        val post = f.json.readTree(f.requests.single { it.method == "POST" }.body)
        assertEquals("cycle-original", post.path("cycleId").textValue())
        assertEquals("CREATE_RECORD", post.path("type").textValue()); assertFalse(post.path("payload").has("xp"))
        assertEquals(30, post.path("payload").path("minutes").intValue())
    }
    @Test fun repeatedIntentDoesNotAllocateAnotherOperationAndNewIntentDoes() = runBlocking {
        val f = Fixture(); f.repository.catalog("owner-a")
        val first = f.repository.submit("owner-a", "one", f.draft)
        val second = f.repository.submit("owner-a", "one", f.draft)
        val third = f.repository.submit("owner-a", "two", f.draft)
        assertEquals(first.ref, second.ref); assertNotEquals(first.ref, third.ref)
        assertEquals(2, f.requests.count { it.method == "POST" })
    }
    @Test fun offlineAndForeignOwnerCannotSendOrCreatePending() = runBlocking {
        val f = Fixture(); f.repository.catalog("owner-a"); f.online = false
        try { f.repository.submit("owner-a", "one", f.draft); fail() } catch (_: OperationBlocked) { }
        f.online = true
        try { f.repository.submit("owner-b", "two", f.draft); fail() } catch (_: OperationBlocked) { }
        assertTrue(f.store.rows.isEmpty()); assertFalse(f.requests.any { it.method == "POST" })
    }
    @Test fun draftPreservesOtherDraftSectionsAndInvalidTextWithoutSending() = runBlocking {
        val f = Fixture(); f.store.saveDraft(Draft("owner-a", """{"profileUsername":"Player"}"""))
        val invalid = f.draft.copy(minutes = "1.5")
        f.repository.saveDraft("owner-a", invalid)
        assertEquals(invalid, f.repository.draft("owner-a"))
        assertEquals("Player", f.json.readTree(f.store.draftFor("owner-a")!!.content).path("profileUsername").textValue())
        assertTrue(f.requests.isEmpty())
    }
    @Test fun mealOmitsDurationAndInvalidComponentsNeverReachTransport() = runBlocking {
        val f = Fixture(); f.repository.catalog("owner-a")
        try { f.repository.submit("owner-a", "bad", f.draft.copy(minutes = "1.5")); fail() } catch (_: OperationBlocked) { }
        f.repository.submit("owner-a", "meal", f.draft.copy(activityId = "meal"))
        val payload = f.json.readTree(f.requests.single { it.method == "POST" }.body).path("payload")
        assertFalse(payload.has("hours")); assertFalse(payload.has("minutes"))
    }
    @Test fun retryLooksUpUnknownBeforeResendingOriginalBytes() = runBlocking {
        val f = Fixture(); f.repository.catalog("owner-a"); f.unknown = true
        val first = f.repository.submit("owner-a", "one", f.draft)
        val envelope = f.requests.single { it.method == "POST" }.body
        f.unknown = false; f.repository.retry(first.ref)
        assertEquals(2, f.requests.count { it.method == "POST" })
        assertEquals(envelope, f.requests.last().body)
        assertTrue(f.requests.dropWhile { it.method != "POST" }.drop(1).first().url.pathSegments.contains("operations"))
    }
    @Test fun localDraftFailureIsSafeAndDoesNotSendOrErasePreviousDraft() = runBlocking {
        val f = Fixture(); f.repository.saveDraft("owner-a", f.draft); f.store.failDraft = true
        try { f.repository.saveDraft("owner-a", f.draft.copy(minutes = "35")); fail() }
        catch (blocked: OperationBlocked) { assertEquals(ApiFailureCode.TEMPORARILY_UNAVAILABLE, blocked.code) }
        f.store.failDraft = false
        assertEquals(f.draft, f.repository.draft("owner-a")); assertTrue(f.requests.isEmpty()); assertTrue(f.store.rows.isEmpty())
    }
    @Test fun startupRecoveryAlreadyCompletedBySessionIsReportedOnceWithoutNewOperation() = runBlocking {
        val f = Fixture()
        val result = OperationSnapshot(OperationRef("owner-a", "123e4567-e89b-42d3-a456-426614174000"), PendingState.CONFIRMED)
        f.repository.rememberRecovered(listOf(result))
        assertEquals(listOf(result), f.repository.pending("owner-a"))
        assertTrue(f.repository.pending("owner-a").isEmpty()); assertTrue(f.requests.isEmpty()); assertTrue(f.store.rows.isEmpty())
    }
}
